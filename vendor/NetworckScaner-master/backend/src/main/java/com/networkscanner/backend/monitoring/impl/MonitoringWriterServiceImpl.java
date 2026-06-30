package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.event.WislaIncidentChangedEvent;
import com.networkscanner.backend.integration.impl.ExternalIncidentUpsertMapper;
import com.networkscanner.backend.monitoring.api.MonitoringWriterService;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringWriterServiceImpl implements MonitoringWriterService {

  private final ZabbixRuntimeStateService stateService;
  private final JdbcTemplate jdbcTemplate;
  private final MonitoringPipelineMessageStore messageStore;
  private final NotificationDispatchService notificationDispatchService;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;
  @Autowired
  private ExternalIncidentUpsertMapper externalIncidentUpsertMapper;
  @Autowired(required = false)
  private SourceSystemProvider sourceSystemProvider;

  public MonitoringWriterServiceImpl(
      ZabbixRuntimeStateService stateService,
      JdbcTemplate jdbcTemplate,
      MonitoringPipelineMessageStore messageStore,
      NotificationDispatchService notificationDispatchService,
      MonitoredDeviceRepository monitoredDeviceRepository
  ) {
    this.stateService = stateService;
    this.jdbcTemplate = jdbcTemplate;
    this.messageStore = messageStore;
    this.notificationDispatchService = notificationDispatchService;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
  }

  @Override
  public void apply(EvaluatedMonitoringEvent event) {
    applyBatch(List.of(event));
  }

  @Override
  @Transactional
  public void applyBatch(List<EvaluatedMonitoringEvent> events) {
    if (events == null || events.isEmpty()) {
      return;
    }

    Map<Long, List<EvaluatedMonitoringEvent>> groupedByDevice = new LinkedHashMap<>();
    for (EvaluatedMonitoringEvent event : events) {
      groupedByDevice.computeIfAbsent(event.deviceId(), ignored -> new ArrayList<>()).add(event);
    }

    for (List<EvaluatedMonitoringEvent> deviceEvents : groupedByDevice.values()) {
      applyDeviceBatch(deviceEvents);
    }
  }

  private void applyDeviceBatch(List<EvaluatedMonitoringEvent> deviceEvents) {
    EvaluatedMonitoringEvent lastApplied = null;
    List<MutationEnvelope> envelopes = new ArrayList<>();
    List<String> messageIds = deviceEvents.stream().map(EvaluatedMonitoringEvent::messageId).collect(Collectors.toList());
    boolean[] accepted = messageStore.markProcessedBatch(messageIds, "WRITER");
    if (accepted == null || accepted.length != deviceEvents.size()) {
      accepted = new boolean[deviceEvents.size()];
      for (int i = 0; i < deviceEvents.size(); i++) {
        accepted[i] = messageStore.markProcessed(deviceEvents.get(i).messageId(), "WRITER");
      }
    }
    for (int index = 0; index < deviceEvents.size(); index++) {
      if (!accepted[index]) {
        continue;
      }
      EvaluatedMonitoringEvent event = deviceEvents.get(index);
      applySingleEvent(event);
      for (MonitoringEventMutation mutation : event.eventMutations()) {
        envelopes.add(new MutationEnvelope(
            event.deviceId(),
            event.templateId(),
            event.templateVersion(),
            event.packVersion(),
            mutation
        ));
        dispatchOperatorNotification(event, mutation);
        publishIncidentEvent(event, mutation);
      }
      lastApplied = event;
    }

    applyEventMutations(coalesceMutations(envelopes));

    if (lastApplied != null) {
      jdbcTemplate.update(
          """
              UPDATE monitored_devices
              SET health_status = ?, updated_at = GREATEST(updated_at, ?)
              WHERE id = ?
              """,
          lastApplied.healthStatus().name(),
          lastApplied.collectedAt(),
          lastApplied.deviceId()
      );
    }
  }

  private void applySingleEvent(EvaluatedMonitoringEvent event) {
    if (!event.values().isEmpty()) {
      stateService.saveItemValues(
          deviceStub(event.deviceId(), event.deviceIp()),
          event.templateId(),
          event.templateVersion(),
          event.packVersion(),
          event.values(),
          event.collectedAt()
      );
    }
  }

  private void applyEventMutations(List<PersistMutation> mutations) {
    List<PersistMutation> openMutations = new ArrayList<>();
    List<PersistMutation> updateMutations = new ArrayList<>();
    List<PersistMutation> resolveMutations = new ArrayList<>();
    List<PersistMutation> resolvedInsertMutations = new ArrayList<>();

    for (PersistMutation mutation : mutations) {
      switch (mutation.action()) {
        case UPSERT_OPEN -> openMutations.add(mutation);
        case UPDATE_OPEN -> updateMutations.add(mutation);
        case RESOLVE_OPEN -> resolveMutations.add(mutation);
        case INSERT_RESOLVED -> resolvedInsertMutations.add(mutation);
      }
    }

    List<PersistMutation> openForInsert = applyOpenUpserts(openMutations);
    batchUpdateOpenEvents(updateMutations);
    batchResolveOpenEvents(resolveMutations);
    batchInsertEvents(openForInsert, "OPEN");
    batchInsertEvents(resolvedInsertMutations, "RESOLVED");
  }

  private List<PersistMutation> applyOpenUpserts(
      List<PersistMutation> mutations
  ) {
    if (mutations.isEmpty()) {
      return List.of();
    }
    int[] updated = jdbcTemplate.batchUpdate(
        """
            UPDATE monitoring_events
            SET template_id = ?, template_version = ?, pack_version = ?,
                trigger_uuid = ?, trigger_name = ?, trigger_expression = ?, recovery_expression = ?,
                threshold_level = ?, threshold_value = ?, actual_value = ?, severity = ?, recovery_path = NULL
            WHERE device_id = ?
              AND metric_name = ?
              AND COALESCE(instance_key, '') = ?
              AND status = 'OPEN'
            """,
        statementSetter(mutations, (ps, mutation) -> {
          ps.setString(1, mutation.templateId());
          ps.setString(2, mutation.templateVersion());
          ps.setString(3, mutation.packVersion());
          ps.setString(4, mutation.mutation().triggerUuid());
          ps.setString(5, mutation.mutation().triggerName());
          ps.setString(6, mutation.mutation().triggerExpression());
          ps.setString(7, mutation.mutation().recoveryExpression());
          ps.setString(8, mutation.mutation().thresholdLevel().name());
          ps.setDouble(9, mutation.mutation().thresholdValue());
          ps.setDouble(10, mutation.mutation().actualValue());
          ps.setString(11, mutation.mutation().severity());
          ps.setLong(12, mutation.deviceId());
          ps.setString(13, mutation.mutation().metricName());
          ps.setString(14, blankToEmpty(mutation.mutation().instanceKey()));
        })
    );

    List<PersistMutation> toInsert = new ArrayList<>();
    for (int i = 0; i < updated.length; i++) {
      if (updated[i] == 0) {
        toInsert.add(mutations.get(i));
      }
    }
    return toInsert;
  }

  private void batchUpdateOpenEvents(List<PersistMutation> mutations) {
    if (mutations.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            UPDATE monitoring_events
            SET template_id = ?, template_version = ?, pack_version = ?,
                trigger_uuid = ?, trigger_name = ?, trigger_expression = ?, recovery_expression = ?,
                threshold_level = ?, threshold_value = ?, actual_value = ?, severity = ?, recovery_path = NULL
            WHERE device_id = ?
              AND metric_name = ?
              AND COALESCE(instance_key, '') = ?
              AND status = 'OPEN'
            """,
        statementSetter(mutations, (ps, mutation) -> {
          ps.setString(1, mutation.templateId());
          ps.setString(2, mutation.templateVersion());
          ps.setString(3, mutation.packVersion());
          ps.setString(4, mutation.mutation().triggerUuid());
          ps.setString(5, mutation.mutation().triggerName());
          ps.setString(6, mutation.mutation().triggerExpression());
          ps.setString(7, mutation.mutation().recoveryExpression());
          ps.setString(8, mutation.mutation().thresholdLevel().name());
          ps.setDouble(9, mutation.mutation().thresholdValue());
          ps.setDouble(10, mutation.mutation().actualValue());
          ps.setString(11, mutation.mutation().severity());
          ps.setLong(12, mutation.deviceId());
          ps.setString(13, mutation.mutation().metricName());
          ps.setString(14, blankToEmpty(mutation.mutation().instanceKey()));
        })
    );
  }

  private void batchResolveOpenEvents(List<PersistMutation> mutations) {
    if (mutations.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            UPDATE monitoring_events
            SET normalized_at = ?, status = 'RESOLVED', severity = ?, recovery_path = ?
            WHERE device_id = ?
              AND metric_name = ?
              AND COALESCE(instance_key, '') = ?
              AND status = 'OPEN'
            """,
        statementSetter(mutations, (ps, mutation) -> {
          ps.setObject(1, mutation.mutation().normalizedAt());
          ps.setString(2, mutation.mutation().severity());
          ps.setString(3, mutation.mutation().recoveryPath());
          ps.setLong(4, mutation.deviceId());
          ps.setString(5, mutation.mutation().metricName());
          ps.setString(6, blankToEmpty(mutation.mutation().instanceKey()));
        })
    );
  }

  private void batchInsertEvents(
      List<PersistMutation> mutations,
      String status
  ) {
    if (mutations.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO monitoring_events (
              device_id, template_id, template_version, pack_version,
              metric_name, trigger_uuid, trigger_name, trigger_expression, recovery_expression, recovery_path, instance_key,
              threshold_level, threshold_value, actual_value,
              breach_started_at, normalized_at, status, severity
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        statementSetter(mutations, (ps, mutation) -> {
          ps.setLong(1, mutation.deviceId());
          ps.setString(2, mutation.templateId());
          ps.setString(3, mutation.templateVersion());
          ps.setString(4, mutation.packVersion());
          ps.setString(5, mutation.mutation().metricName());
          ps.setString(6, mutation.mutation().triggerUuid());
          ps.setString(7, mutation.mutation().triggerName());
          ps.setString(8, mutation.mutation().triggerExpression());
          ps.setString(9, mutation.mutation().recoveryExpression());
          ps.setString(10, mutation.mutation().recoveryPath());
          ps.setString(11, mutation.mutation().instanceKey());
          ps.setString(12, mutation.mutation().thresholdLevel().name());
          ps.setDouble(13, mutation.mutation().thresholdValue());
          ps.setDouble(14, mutation.mutation().actualValue());
          ps.setObject(15, mutation.mutation().breachStartedAt());
          ps.setObject(16, "RESOLVED".equals(status) ? mutation.mutation().normalizedAt() : null);
          ps.setString(17, status);
          ps.setString(18, mutation.mutation().severity());
        })
    );
  }

  private BatchPreparedStatementSetter statementSetter(
      List<PersistMutation> mutations,
      SqlConsumer consumer
  ) {
    return new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        consumer.accept(ps, mutations.get(i));
      }

      @Override
      public int getBatchSize() {
        return mutations.size();
      }
    };
  }

  private MonitoredDeviceEntity deviceStub(Long deviceId, String deviceIp) {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(deviceId);
    device.setIp(deviceIp);
    return device;
  }

  private void dispatchOperatorNotification(EvaluatedMonitoringEvent event, MonitoringEventMutation mutation) {
    if (event == null || mutation == null || mutation.action() == null) {
      return;
    }
    if (mutation.action() != MonitoringEventMutationAction.OPEN
        && mutation.action() != MonitoringEventMutationAction.RESOLVE
        && mutation.action() != MonitoringEventMutationAction.UPDATE) {
      return;
    }
    MonitoredDeviceEntity device = monitoredDeviceRepository.findById(event.deviceId()).orElse(null);
    String deviceName = device == null ? event.deviceIp() : device.getName();
    List<String> tags = extractTags(device);
    notificationDispatchService.notifyMonitoringEvent(
        event.deviceId(),
        event.deviceIp(),
        deviceName,
        tags,
        mutation
    );
  }

  private void publishIncidentEvent(EvaluatedMonitoringEvent event, MonitoringEventMutation mutation) {
    if (applicationEventPublisher == null || externalIncidentUpsertMapper == null) {
      return;
    }
    if (mutation.action() != MonitoringEventMutationAction.OPEN
        && mutation.action() != MonitoringEventMutationAction.UPDATE
        && mutation.action() != MonitoringEventMutationAction.RESOLVE) {
      return;
    }
    applicationEventPublisher.publishEvent(
        new WislaIncidentChangedEvent(externalIncidentUpsertMapper.fromMutation(event, mutation, currentSourceSystem()))
    );
  }

  private String currentSourceSystem() {
    return sourceSystemProvider != null ? sourceSystemProvider.getSourceSystem() : "networkscanner";
  }

  private static List<String> extractTags(MonitoredDeviceEntity device) {
    if (device == null || device.getTagsJson() == null || device.getTagsJson().isBlank()) {
      return List.of();
    }
    String raw = device.getTagsJson().trim();
    if (raw.length() < 2) {
      return List.of();
    }
    String inner = raw.substring(1, raw.length() - 1);
    if (inner.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(inner.split(","))
        .map(v -> v.trim().replace("\"", ""))
        .filter(v -> !v.isBlank())
        .distinct()
        .toList();
  }

  private String blankToEmpty(String value) {
    return value == null ? "" : value;
  }

  private List<PersistMutation> coalesceMutations(List<MutationEnvelope> envelopes) {
    if (envelopes.isEmpty()) {
      return List.of();
    }
    Map<EventKey, PersistMutation> pending = new LinkedHashMap<>();
    List<PersistMutation> result = new ArrayList<>();

    for (MutationEnvelope envelope : envelopes) {
      EventKey key = EventKey.from(envelope);
      PersistMutation next = PersistMutation.from(envelope);
      PersistMutation current = pending.get(key);
      if (current == null) {
        pending.put(key, next);
        continue;
      }

      PersistMutation merged = merge(current, next);
      if (merged == null) {
        result.add(current);
        pending.put(key, next);
      } else {
        pending.put(key, merged);
      }
    }

    result.addAll(pending.values());
    return result;
  }

  private PersistMutation merge(PersistMutation current, PersistMutation next) {
    return switch (current.action()) {
      case UPSERT_OPEN -> switch (next.action()) {
        case UPDATE_OPEN, UPSERT_OPEN -> current.withMutation(next.mutation());
        case RESOLVE_OPEN -> current.withAction(PersistAction.INSERT_RESOLVED)
            .withMutation(resolvedInsertMutation(current.mutation(), next.mutation()));
        case INSERT_RESOLVED -> null;
      };
      case UPDATE_OPEN -> switch (next.action()) {
        case UPDATE_OPEN -> current.withMutation(next.mutation());
        case RESOLVE_OPEN -> current.withAction(PersistAction.RESOLVE_OPEN).withMutation(next.mutation());
        default -> null;
      };
      case RESOLVE_OPEN, INSERT_RESOLVED -> null;
    };
  }

  private MonitoringEventMutation resolvedInsertMutation(
      MonitoringEventMutation opened,
      MonitoringEventMutation resolved
  ) {
    return new MonitoringEventMutation(
        MonitoringEventMutationAction.RESOLVE,
        resolved.metricName(),
        resolved.triggerUuid(),
        resolved.triggerName(),
        resolved.triggerExpression(),
        resolved.recoveryExpression(),
        resolved.recoveryPath(),
        resolved.instanceKey(),
        resolved.thresholdLevel(),
        resolved.thresholdValue(),
        resolved.actualValue(),
        opened.breachStartedAt(),
        resolved.normalizedAt(),
        resolved.severity()
    );
  }

  @FunctionalInterface
  private interface SqlConsumer {
    void accept(PreparedStatement ps, PersistMutation mutation) throws SQLException;
  }

  private enum PersistAction {
    UPSERT_OPEN,
    UPDATE_OPEN,
    RESOLVE_OPEN,
    INSERT_RESOLVED
  }

  private record MutationEnvelope(
      Long deviceId,
      String templateId,
      String templateVersion,
      String packVersion,
      MonitoringEventMutation mutation
  ) {
  }

  private record EventKey(
      String metricName,
      String instanceKey
  ) {
    private static EventKey from(MutationEnvelope envelope) {
      return new EventKey(
          envelope.mutation().metricName(),
          blankStatic(envelope.mutation().instanceKey())
      );
    }
  }

  private record PersistMutation(
      Long deviceId,
      String templateId,
      String templateVersion,
      String packVersion,
      PersistAction action,
      MonitoringEventMutation mutation
  ) {
    private static PersistMutation from(MutationEnvelope envelope) {
      PersistAction action = switch (envelope.mutation().action()) {
        case OPEN -> PersistAction.UPSERT_OPEN;
        case UPDATE -> PersistAction.UPDATE_OPEN;
        case RESOLVE -> PersistAction.RESOLVE_OPEN;
      };
      return new PersistMutation(
          envelope.deviceId(),
          envelope.templateId(),
          envelope.templateVersion(),
          envelope.packVersion(),
          action,
          envelope.mutation()
      );
    }

    private PersistMutation withAction(PersistAction nextAction) {
      return new PersistMutation(deviceId, templateId, templateVersion, packVersion, nextAction, mutation);
    }

    private PersistMutation withMutation(MonitoringEventMutation nextMutation) {
      return new PersistMutation(deviceId, templateId, templateVersion, packVersion, action, nextMutation);
    }
  }

  private static String blankStatic(String value) {
    return value == null ? "" : value;
  }
}
