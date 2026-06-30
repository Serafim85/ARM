package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.config.WislaEventsProperties;
import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.event.WislaAvailabilityChangedEvent;
import com.networkscanner.backend.integration.impl.ProbeAvailabilityUpdateMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringAvailabilityBatchWriter {

  private static final Logger log = LoggerFactory.getLogger(MonitoringAvailabilityBatchWriter.class);

  private final JdbcTemplate jdbcTemplate;
  private final long stateHeartbeatMs;
  private final long downHistorySampleMs;
  private final SourceSystemProvider sourceSystemProvider;
  private final ProbeAvailabilityUpdateMapper availabilityUpdateMapper;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final boolean wislaHeartbeatEnabled;
  private final long wislaHeartbeatMs;

  @Autowired
  public MonitoringAvailabilityBatchWriter(
      JdbcTemplate jdbcTemplate,
      @Value("${monitoring.availability-refresh.state-heartbeat-ms:300000}") long stateHeartbeatMs,
      @Value("${monitoring.availability-refresh.history-down-sample-ms:180000}") long downHistorySampleMs,
      SourceSystemProvider sourceSystemProvider,
      ProbeAvailabilityUpdateMapper availabilityUpdateMapper,
      ApplicationEventPublisher applicationEventPublisher,
      WislaEventsProperties wislaEventsProperties
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.stateHeartbeatMs = Math.max(stateHeartbeatMs, 0L);
    this.downHistorySampleMs = Math.max(downHistorySampleMs, 0L);
    this.sourceSystemProvider = sourceSystemProvider;
    this.availabilityUpdateMapper = availabilityUpdateMapper;
    this.applicationEventPublisher = applicationEventPublisher;
    this.wislaHeartbeatEnabled = wislaEventsProperties.isAvailabilityHeartbeatEnabled();
    this.wislaHeartbeatMs = Math.max(wislaEventsProperties.getAvailabilityHeartbeatMs(), 0L);
  }

  MonitoringAvailabilityBatchWriter(
      JdbcTemplate jdbcTemplate,
      long stateHeartbeatMs,
      long downHistorySampleMs
  ) {
    this(
        jdbcTemplate,
        stateHeartbeatMs,
        downHistorySampleMs,
        () -> "networkscanner",
        new ProbeAvailabilityUpdateMapper(),
        null,
        defaultWislaEventsProperties()
    );
  }

  MonitoringAvailabilityBatchWriter(
      JdbcTemplate jdbcTemplate,
      long stateHeartbeatMs,
      long downHistorySampleMs,
      SourceSystemProvider sourceSystemProvider,
      ProbeAvailabilityUpdateMapper availabilityUpdateMapper,
      ApplicationEventPublisher applicationEventPublisher,
      boolean wislaHeartbeatEnabled,
      long wislaHeartbeatMs
  ) {
    this.jdbcTemplate = jdbcTemplate;
    this.stateHeartbeatMs = Math.max(stateHeartbeatMs, 0L);
    this.downHistorySampleMs = Math.max(downHistorySampleMs, 0L);
    this.sourceSystemProvider = sourceSystemProvider;
    this.availabilityUpdateMapper = availabilityUpdateMapper;
    this.applicationEventPublisher = applicationEventPublisher;
    this.wislaHeartbeatEnabled = wislaHeartbeatEnabled;
    this.wislaHeartbeatMs = Math.max(wislaHeartbeatMs, 0L);
  }

  private static WislaEventsProperties defaultWislaEventsProperties() {
    WislaEventsProperties properties = new WislaEventsProperties();
    properties.setAvailabilityHeartbeatEnabled(true);
    properties.setAvailabilityHeartbeatMs(300_000L);
    return properties;
  }

  @Transactional
  public void writeBatch(List<MonitoringAvailabilityRefreshResult> results) {
    if (results == null || results.isEmpty()) {
      return;
    }

    List<MonitoringAvailabilityRefreshResult> deviceUpdates = results.stream()
        .filter(this::shouldUpdateDeviceState)
        .toList();
    List<MonitoringAvailabilityRefreshResult> historyRows = results.stream()
        .filter(this::shouldPersistHistory)
        .toList();

    batchUpdateDevices(deviceUpdates);
    batchInsertAvailabilityHistory(historyRows);
    batchInsertTelemetryHistory(historyRows);
    publishWislaAvailabilityEvents(results);
  }

  private void publishWislaAvailabilityEvents(List<MonitoringAvailabilityRefreshResult> results) {
    if (applicationEventPublisher == null) {
      return;
    }
    for (MonitoringAvailabilityRefreshResult result : results) {
      if (!shouldPublishWislaAvailability(result)) {
        continue;
      }
      String reason = result.stateChanged() ? "state_changed" : "heartbeat";
      log.debug(
          "Publishing Wisla availability integrationId={} deviceId={} status={} reason={}",
          sourceSystemProvider.getSourceSystem(),
          result.deviceId(),
          result.status(),
          reason
      );
      applicationEventPublisher.publishEvent(
          new WislaAvailabilityChangedEvent(
              availabilityUpdateMapper.map(result, sourceSystemProvider.getSourceSystem())
          )
      );
    }
  }

  private boolean shouldPublishWislaAvailability(MonitoringAvailabilityRefreshResult result) {
    if (result.stateChanged()) {
      return true;
    }
    if (!wislaHeartbeatEnabled || wislaHeartbeatMs <= 0L) {
      return false;
    }
    if (result.previousUpdatedAt() == null) {
      return true;
    }
    return isElapsed(result.previousUpdatedAt(), result.recordedAt(), wislaHeartbeatMs);
  }

  private boolean shouldUpdateDeviceState(MonitoringAvailabilityRefreshResult result) {
    if (result.stateChanged()) {
      return true;
    }
    if (shouldPersistDownSample(result)) {
      return true;
    }
    if (stateHeartbeatMs <= 0 || result.previousUpdatedAt() == null) {
      return false;
    }
    return isElapsed(result.previousUpdatedAt(), result.recordedAt(), stateHeartbeatMs);
  }

  private boolean shouldPersistHistory(MonitoringAvailabilityRefreshResult result) {
    if (result.stateChanged()) {
      return true;
    }
    if (!isDown(result.status())) {
      return true;
    }
    return shouldPersistDownSample(result);
  }

  private boolean shouldPersistDownSample(MonitoringAvailabilityRefreshResult result) {
    if (!isDown(result.status()) || !Objects.equals(result.status(), result.previousStatus())) {
      return false;
    }
    if (downHistorySampleMs == 0L || result.previousUpdatedAt() == null) {
      return true;
    }
    return isElapsed(result.previousUpdatedAt(), result.recordedAt(), downHistorySampleMs);
  }

  private boolean isDown(String status) {
    return "Недоступно".equals(status);
  }

  private boolean isElapsed(OffsetDateTime from, OffsetDateTime to, long intervalMs) {
    return from.plusNanos(intervalMs * 1_000_000L).isBefore(to)
        || from.plusNanos(intervalMs * 1_000_000L).isEqual(to);
  }

  private void batchUpdateDevices(List<MonitoringAvailabilityRefreshResult> updates) {
    if (updates.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            UPDATE monitored_devices
            SET status = ?, availability_json = ?, updated_at = GREATEST(updated_at, ?)
            WHERE id = ?
            """,
        statementSetter(updates, (ps, result) -> {
          ps.setString(1, result.status());
          ps.setString(2, result.availabilityJson());
          ps.setObject(3, result.recordedAt());
          ps.setLong(4, result.deviceId());
        })
    );
  }

  private void batchInsertAvailabilityHistory(List<MonitoringAvailabilityRefreshResult> results) {
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO availability_history (
              recorded_at, device_ip, host_status, icmp_active, snmp_active, ssh_active
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
        statementSetter(results, (ps, result) -> {
          ps.setObject(1, result.recordedAt());
          ps.setString(2, result.deviceIp());
          ps.setString(3, result.status());
          ps.setBoolean(4, result.icmpReachable());
          ps.setBoolean(5, result.snmpReachable());
          ps.setBoolean(6, result.sshReachable());
        })
    );
  }

  private void batchInsertTelemetryHistory(List<MonitoringAvailabilityRefreshResult> results) {
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO telemetry_history (recorded_at, device_ip, cpu_usage, ram_usage, rom_usage)
            VALUES (?, ?, ?, ?, ?)
            """,
        statementSetter(results, (ps, result) -> {
          int seed = result.deviceIp().chars().sum();
          ps.setObject(1, result.recordedAt());
          ps.setString(2, result.deviceIp());
          ps.setBigDecimal(3, percentage(8 + (seed % 21)));
          ps.setBigDecimal(4, percentage(35 + (seed % 40)));
          ps.setBigDecimal(5, percentage(20 + (seed % 35)));
        })
    );
  }

  private BatchPreparedStatementSetter statementSetter(
      List<MonitoringAvailabilityRefreshResult> results,
      SqlConsumer consumer
  ) {
    List<MonitoringAvailabilityRefreshResult> snapshot = new ArrayList<>(results);
    return new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        consumer.accept(ps, snapshot.get(i));
      }

      @Override
      public int getBatchSize() {
        return snapshot.size();
      }
    };
  }

  private BigDecimal percentage(int value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }

  @FunctionalInterface
  private interface SqlConsumer {
    void accept(PreparedStatement ps, MonitoringAvailabilityRefreshResult result) throws SQLException;
  }
}
