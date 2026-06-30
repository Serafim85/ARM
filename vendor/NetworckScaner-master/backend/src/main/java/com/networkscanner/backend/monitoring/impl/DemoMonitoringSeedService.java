package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.AvailabilityDto;
import com.networkscanner.backend.monitoring.dto.DemoMonitoringSeedResponseDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DemoMonitoringSeedService {

  private static final String DEFAULT_TEMPLATE_ID = "mib2-default";
  private static final String DEMO_NETWORK = "10.254.0.";
  private static final int DEVICE_COUNT = 7;

  private static final List<SeedMetric> NUMERIC_METRICS = List.of(
      new SeedMetric("cpu_current", "b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1", "%"),
      new SeedMetric("cpu_average", "b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2", "%"),
      new SeedMetric("cpu_peak", "b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3b3", "%"),
      new SeedMetric("ram_used_percent", "b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4b4", "%"),
      new SeedMetric("rom_used_percent", "b5b5b5b5b5b5b5b5b5b5b5b5b5b5b5b5", "%")
  );

  private final boolean seedEnabled;
  private final String seedTemplateId;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final MonitoringTemplateResolver templateResolver;
  private final ZabbixRuntimeStateService zabbixRuntimeStateService;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public DemoMonitoringSeedService(
      @Value("${app.demo-monitoring-seed-enabled:true}") boolean seedEnabled,
      @Value("${app.demo-monitoring-seed-template-id:" + DEFAULT_TEMPLATE_ID + "}") String seedTemplateId,
      MonitoredDeviceRepository monitoredDeviceRepository,
      MonitoringTemplateResolver templateResolver,
      ZabbixRuntimeStateService zabbixRuntimeStateService,
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper
  ) {
    this.seedEnabled = seedEnabled;
    this.seedTemplateId = seedTemplateId;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.templateResolver = templateResolver;
    this.zabbixRuntimeStateService = zabbixRuntimeStateService;
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public DemoMonitoringSeedResponseDto seedDemoMonitoringData() {
    if (!seedEnabled) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Функция недоступна.");
    }
    if (monitoredDeviceRepository.findByIp(DEMO_NETWORK + "1").isPresent()) {
      return new DemoMonitoringSeedResponseDto(true, 0, "Демо-данные уже загружены.");
    }

    ResolvedMonitoringTemplate template = templateResolver.resolveTemplateById(seedTemplateId);
    String availabilityJson = availabilityJson();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    OffsetDateTime historyStart = now.minusWeeks(2);

    List<MonitoredDeviceEntity> saved = new ArrayList<>();
    for (int i = 1; i <= DEVICE_COUNT; i++) {
      String ip = DEMO_NETWORK + i;
      MonitoredDeviceEntity entity = buildDevice(ip, i, availabilityJson, template, now);
      saved.add(monitoredDeviceRepository.save(entity));
    }

    List<MetricRow> metricBatch = new ArrayList<>();
    for (int i = 0; i < saved.size(); i++) {
      MonitoredDeviceEntity device = saved.get(i);
      int idx = i + 1;
      for (OffsetDateTime t = historyStart; t.isBefore(now); t = t.plusHours(2)) {
        double phase = t.toEpochSecond() / 7200.0 + idx;
        for (SeedMetric m : NUMERIC_METRICS) {
          double v = baseValue(m.key(), idx, phase);
          metricBatch.add(new MetricRow(t, device.getIp(), m, v, template));
        }
      }
      insertEvents(device.getId(), idx, template, now);
      List<ZabbixItemValue> lastValues = lastPointAsValues(idx, now);
      zabbixRuntimeStateService.saveItemValues(
          device,
          seedTemplateId,
          template.templateVersion(),
          template.packVersion(),
          lastValues,
          now
      );
    }

    batchInsertMetricValues(metricBatch);

    return new DemoMonitoringSeedResponseDto(
        false,
        DEVICE_COUNT,
        "Добавлено устройств: " + DEVICE_COUNT + ", шаблон " + seedTemplateId + ", история метрик за 2 недели."
    );
  }

  private String availabilityJson() {
    try {
      return objectMapper.writeValueAsString(List.of(
          new AvailabilityDto("ICMP", true, "success"),
          new AvailabilityDto("SNMP", true, "success")
      ));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private MonitoredDeviceEntity buildDevice(
      String ip,
      int index,
      String availabilityJson,
      ResolvedMonitoringTemplate template,
      OffsetDateTime now
  ) {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setIp(ip);
    entity.setHostName("demo-cisco-" + String.format("%02d", index));
    entity.setName("Демо Cisco " + index);
    entity.setSerialNumber("DEMO-CISCO-" + String.format("%04d", index));
    entity.setMacAddress(String.format("02:00:00:00:00:%02x", index));
    entity.setVendor("Cisco");
    entity.setModel("Catalyst 9300");
    entity.setFirmwareVersion("17.9.4");
    entity.setPollingStatus("SNMP v2c");
    entity.setStatus("Включено");
    entity.setHealthStatus(index % 3 == 0 ? DeviceHealthStatus.WARN : DeviceHealthStatus.NORM);
    entity.setGroupName("demo-seed");
    entity.setTagsJson("[]");
    entity.setAvailabilityJson(availabilityJson);
    entity.setTemplateId(seedTemplateId);
    entity.setTemplateIds(seedTemplateId);
    entity.setEffectiveTemplateId(template.id());
    entity.setTemplateVersion(template.templateVersion());
    entity.setPackVersion(template.packVersion());
    entity.setSchemaVersion(template.schemaVersion());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  private static double baseValue(String metricKey, int deviceIndex, double phase) {
    int base = 28 + (deviceIndex * 4) % 18;
    return switch (metricKey) {
      case "cpu_current" -> clamp(base + Math.sin(phase) * 12 + deviceIndex);
      case "cpu_average" -> clamp(base - 5 + Math.sin(phase * 0.9) * 10);
      case "cpu_peak" -> clamp(base + 15 + Math.abs(Math.sin(phase * 1.3)) * 12);
      case "ram_used_percent" -> clamp(42 + deviceIndex * 3 + Math.sin(phase * 0.7) * 8);
      case "rom_used_percent" -> clamp(35 + (deviceIndex * 2) % 20 + Math.sin(phase * 0.5) * 5);
      default -> base;
    };
  }

  private static double clamp(double v) {
    return Math.max(0.0, Math.min(100.0, Math.round(v * 10.0) / 10.0));
  }

  private List<ZabbixItemValue> lastPointAsValues(int deviceIndex, OffsetDateTime at) {
    double phase = at.toEpochSecond() / 7200.0 + deviceIndex;
    List<ZabbixItemValue> list = new ArrayList<>();
    for (SeedMetric m : NUMERIC_METRICS) {
      double v = baseValue(m.key(), deviceIndex, phase);
      list.add(new ZabbixItemValue(
          templateIdForMetric(m),
          m.key(),
          m.key(),
          "",
          null,
          m.itemUuid(),
          v,
          null,
          m.unit(),
          null,
          "ok",
          null
      ));
    }
    return list;
  }

  private String templateIdForMetric(SeedMetric metric) {
    return seedTemplateId;
  }

  private void insertEvents(
      long deviceId,
      int index,
      ResolvedMonitoringTemplate template,
      OffsetDateTime now
  ) {
    List<EventRow> rows = new ArrayList<>();
    rows.add(new EventRow(
        deviceId,
        template.id(),
        template.templateVersion(),
        template.packVersion(),
        "cpu_current",
        "c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1",
        "CPU usage warning",
        "",
        ThresholdLevel.WARNING,
        80.0,
        88.0 + index,
        now.minusHours(6 + index),
        null,
        MonitoringEventStatus.OPEN,
        "WARNING"
    ));
    rows.add(new EventRow(
        deviceId,
        template.id(),
        template.templateVersion(),
        template.packVersion(),
        "cpu_current",
        "c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2",
        "CPU usage high",
        "",
        ThresholdLevel.HIGH,
        95.0,
        97.0,
        now.minusDays(3),
        now.minusDays(1),
        MonitoringEventStatus.RESOLVED,
        "HIGH"
    ));
    if (index % 2 == 0) {
      rows.add(new EventRow(
          deviceId,
          template.id(),
          template.templateVersion(),
          template.packVersion(),
          "ram_used_percent",
          "d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0d0",
          "RAM pressure",
          "",
          ThresholdLevel.WARNING,
          90.0,
          92.0 + index * 0.1,
          now.minusDays(10),
          now.minusDays(8),
          MonitoringEventStatus.RESOLVED,
          "WARNING"
      ));
    }

    jdbcTemplate.batchUpdate(
        """
            INSERT INTO monitoring_events (
              device_id, template_id, template_version, pack_version,
              metric_name, trigger_uuid, trigger_name, instance_key,
              threshold_level, threshold_value, actual_value,
              breach_started_at, normalized_at, status, severity
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            EventRow r = rows.get(i);
            ps.setLong(1, r.deviceId());
            ps.setString(2, r.templateId());
            ps.setString(3, r.templateVersion());
            ps.setString(4, r.packVersion());
            ps.setString(5, r.metricName());
            ps.setString(6, blankToNull(r.triggerUuid()));
            ps.setString(7, r.triggerName());
            ps.setString(8, r.instanceKey() == null ? "" : r.instanceKey());
            ps.setString(9, r.thresholdLevel().name());
            ps.setDouble(10, r.thresholdValue());
            ps.setDouble(11, r.actualValue());
            ps.setObject(12, r.breachStartedAt());
            ps.setObject(13, r.normalizedAt());
            ps.setString(14, r.status().name());
            ps.setString(15, r.severity());
          }

          @Override
          public int getBatchSize() {
            return rows.size();
          }
        }
    );
  }

  private void batchInsertMetricValues(List<MetricRow> rows) {
    if (rows.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO metric_values (
              recorded_at, device_ip, metric_name, metric_value, item_key, instance_key, unit_label
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            MetricRow r = rows.get(i);
            ps.setObject(1, r.recordedAt());
            ps.setString(2, r.deviceIp());
            ps.setString(3, r.metric().key());
            ps.setDouble(4, r.value());
            ps.setString(5, r.metric().key());
            ps.setString(6, null);
            ps.setString(7, r.metric().unit());
          }

          @Override
          public int getBatchSize() {
            return rows.size();
          }
        }
    );
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private record SeedMetric(String key, String itemUuid, String unit) {
  }

  private record MetricRow(
      OffsetDateTime recordedAt,
      String deviceIp,
      SeedMetric metric,
      double value,
      String templateVersion,
      String packVersion
  ) {
    MetricRow(OffsetDateTime recordedAt, String deviceIp, SeedMetric metric, double value, ResolvedMonitoringTemplate t) {
      this(recordedAt, deviceIp, metric, value, t.templateVersion(), t.packVersion());
    }
  }

  private record EventRow(
      long deviceId,
      String templateId,
      String templateVersion,
      String packVersion,
      String metricName,
      String triggerUuid,
      String triggerName,
      String instanceKey,
      ThresholdLevel thresholdLevel,
      double thresholdValue,
      double actualValue,
      OffsetDateTime breachStartedAt,
      OffsetDateTime normalizedAt,
      MonitoringEventStatus status,
      String severity
  ) {
  }
}
