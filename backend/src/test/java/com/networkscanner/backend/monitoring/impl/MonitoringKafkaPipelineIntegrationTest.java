package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MonitoringKafkaPipelineIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
      DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres")
  )
      .withDatabaseName("networkscanner")
      .withUsername("networkscanner")
      .withPassword("networkscanner");

  @Container
  static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("monitoring.collector.enabled", () -> "false");
    registry.add("monitoring.kafka.enabled", () -> "true");
    registry.add("monitoring.kafka.evaluator-enabled", () -> "true");
    registry.add("monitoring.kafka.writer-enabled", () -> "true");
    registry.add("monitoring.kafka.listener-concurrency", () -> "1");
  }

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void pipelineWritesMetricsEventsAndHealthStatus() throws Exception {
    long deviceId = 101L;
    insertDevice(deviceId, "10.10.10.101");

    kafkaTemplate.send("monitoring.polled", String.valueOf(deviceId), new PolledMetricsEvent(
        "pipeline-open-101",
        "1",
        deviceId,
        "10.10.10.101",
        "Unknown",
        "Generic",
        "network-generic-device-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        OffsetDateTime.now(),
        Map.of(),
        networkGenericAvailabilityValues(0.0),
        null,
        null,
        null
    )).get();

    awaitCondition(() -> count("SELECT COUNT(*) FROM metric_values WHERE device_ip = '10.10.10.101'") == 6L);
    awaitCondition(() -> count("SELECT COUNT(*) FROM monitoring_events WHERE device_id = 101 AND status = 'OPEN'") == 1L);
    awaitCondition(() -> "WARN".equals(singleString("SELECT health_status FROM monitored_devices WHERE id = 101")));

    kafkaTemplate.send("monitoring.polled", String.valueOf(deviceId), new PolledMetricsEvent(
        "pipeline-resolve-101",
        "1",
        deviceId,
        "10.10.10.101",
        "Unknown",
        "Generic",
        "network-generic-device-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        OffsetDateTime.now().plusSeconds(1),
        Map.of(),
        networkGenericAvailabilityValues(1.0),
        null,
        null,
        null
    )).get();

    awaitCondition(() -> count("SELECT COUNT(*) FROM monitoring_events WHERE device_id = 101 AND status = 'RESOLVED'") == 1L);
    awaitCondition(() -> "NORM".equals(singleString("SELECT health_status FROM monitored_devices WHERE id = 101")));
  }

  @Test
  void duplicateMessageIdIsProcessedOnlyOnce() throws Exception {
    long deviceId = 202L;
    insertDevice(deviceId, "10.10.10.202");
    PolledMetricsEvent event = new PolledMetricsEvent(
        "dedupe-202",
        "1",
        deviceId,
        "10.10.10.202",
        "Unknown",
        "Generic",
        "network-generic-device-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        OffsetDateTime.now(),
        Map.of(),
        networkGenericAvailabilityValues(0.0),
        null,
        null,
        null
    );

    kafkaTemplate.send("monitoring.polled", String.valueOf(deviceId), event).get();
    kafkaTemplate.send("monitoring.polled", String.valueOf(deviceId), event).get();

    awaitCondition(() -> count("SELECT COUNT(*) FROM metric_values WHERE device_ip = '10.10.10.202'") == 6L);
    assertEquals(1L, count("SELECT COUNT(*) FROM monitoring_pipeline_messages WHERE message_id = 'dedupe-202' AND stage = 'EVALUATOR'"));
    assertEquals(1L, count("SELECT COUNT(*) FROM monitoring_pipeline_messages WHERE message_id = 'dedupe-202' AND stage = 'WRITER'"));
    assertEquals(DeviceHealthStatus.WARN.name(), singleString("SELECT health_status FROM monitored_devices WHERE id = 202"));
  }

  @Test
  void writerBatchCoalescingScenarioKeepsSingleResolvedEvent() throws Exception {
    long deviceId = 303L;
    insertDevice(deviceId, "10.10.10.303");

    kafkaTemplate.send("monitoring.evaluated", String.valueOf(deviceId), evaluatedEvent(
        "writer-open-303",
        deviceId,
        OffsetDateTime.now(),
        MonitoringEventMutationAction.OPEN,
        DeviceHealthStatus.WARN
    )).get();
    kafkaTemplate.send("monitoring.evaluated", String.valueOf(deviceId), evaluatedEvent(
        "writer-update-303",
        deviceId,
        OffsetDateTime.now().plusSeconds(1),
        MonitoringEventMutationAction.UPDATE,
        DeviceHealthStatus.WARN
    )).get();
    kafkaTemplate.send("monitoring.evaluated", String.valueOf(deviceId), evaluatedEvent(
        "writer-resolve-303",
        deviceId,
        OffsetDateTime.now().plusSeconds(2),
        MonitoringEventMutationAction.RESOLVE,
        DeviceHealthStatus.NORM
    )).get();

    awaitCondition(() -> count("SELECT COUNT(*) FROM monitoring_events WHERE device_id = 303") == 1L);
    awaitCondition(() -> count("SELECT COUNT(*) FROM monitoring_events WHERE device_id = 303 AND status = 'RESOLVED'") == 1L);
    awaitCondition(() -> "NORM".equals(singleString("SELECT health_status FROM monitored_devices WHERE id = 303")));

    assertEquals(0L, count("SELECT COUNT(*) FROM monitoring_events WHERE device_id = 303 AND status = 'OPEN'"));
    assertEquals(3L, count("SELECT COUNT(*) FROM monitoring_pipeline_messages WHERE stage = 'WRITER' AND message_id IN ('writer-open-303', 'writer-update-303', 'writer-resolve-303')"));
    assertEquals("trigger-batch-303", singleString("SELECT trigger_uuid FROM monitoring_events WHERE device_id = 303"));
  }

  @Test
  void monitoredDeviceItemsAreDeletedByCascadeWhenDeviceRemoved() {
    long deviceId = 404L;
    insertDevice(deviceId, "10.10.10.404");
    jdbcTemplate.update(
        """
            INSERT INTO monitored_device_items (
              device_id, item_uuid, instance_key, item_key, name, item_type,
              discovery_prototype, discovery_rule_key, source_template_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        deviceId,
        "uuid-404",
        "",
        "sysUpTime",
        "Uptime",
        "SNMP_AGENT",
        false,
        null,
        "cisco-ios-by-snmp"
    );

    assertEquals(1L, count("SELECT COUNT(*) FROM monitored_device_items WHERE device_id = 404"));
    jdbcTemplate.update("DELETE FROM monitored_devices WHERE id = ?", deviceId);
    assertEquals(0L, count("SELECT COUNT(*) FROM monitored_device_items WHERE device_id = 404"));
  }

  private EvaluatedMonitoringEvent evaluatedEvent(
      String messageId,
      long deviceId,
      OffsetDateTime collectedAt,
      MonitoringEventMutationAction action,
      DeviceHealthStatus healthStatus
  ) {
    return new EvaluatedMonitoringEvent(
        messageId,
        "1",
        deviceId,
        "10.10.10.303",
        "cisco-ios-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        collectedAt,
        List.of(),
        List.of(new MonitoringEventMutation(
            action,
            "cpu",
            "trigger-batch-303",
            "CPU trigger batch 303",
            "last(/Template/cpu)>80",
            "last(/Template/cpu)<70",
            action == MonitoringEventMutationAction.RESOLVE ? "recovery_expression" : null,
            "",
            ThresholdLevel.WARNING,
            80.0,
            action == MonitoringEventMutationAction.RESOLVE ? 20.0 : 95.0,
            OffsetDateTime.parse("2026-04-03T12:00:00Z"),
            OffsetDateTime.parse("2026-04-03T12:05:00Z"),
            "HIGH"
        )),
        healthStatus
    );
  }

  private void insertDevice(long deviceId, String ip) {
    jdbcTemplate.update("DELETE FROM monitoring_pipeline_messages WHERE message_id LIKE ?", "%" + deviceId + "%");
    jdbcTemplate.update("DELETE FROM monitoring_events WHERE device_id = ?", deviceId);
    jdbcTemplate.update("DELETE FROM monitoring_item_state WHERE device_id = ?", deviceId);
    jdbcTemplate.update("DELETE FROM monitored_devices WHERE id = ?", deviceId);
    jdbcTemplate.update(
        """
            INSERT INTO monitored_devices (
              id, ip, host_name, name, serial_number, mac_address,
              vendor, model, firmware_version, polling_status, status,
              health_status, group_name, availability_json,
              template_id, effective_template_id, template_version, pack_version, schema_version,
              created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
        deviceId,
        ip,
        "host-" + deviceId,
        "device-" + deviceId,
        "serial-" + deviceId,
        "00:11:22:33:44:" + (deviceId % 100),
        "Cisco",
        "SG500X-48P",
        "1.0",
        "SNMP v2c",
        "Включено",
        DeviceHealthStatus.NORM.name(),
        "default",
        "[]",
        "cisco-ios-by-snmp",
        "cisco-ios-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        "1"
    );
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }

  private String singleString(String sql) {
    return jdbcTemplate.queryForObject(sql, String.class);
  }

  private List<ZabbixItemValue> networkGenericAvailabilityValues(double snmpAvailability) {
    return List.of(
        new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "icmpping",
            "icmpping",
            "",
            null,
            "icmp-ping-uuid",
            1.0,
            "1",
            null,
            null,
            "ok",
            null
        ),
        new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "icmppingloss",
            "icmppingloss",
            "",
            null,
            "icmp-loss-uuid",
            0.0,
            "0",
            "%",
            null,
            "ok",
            null
        ),
        new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "icmppingsec",
            "icmppingsec",
            "",
            null,
            "icmp-latency-uuid",
            0.01,
            "0.01",
            "s",
            null,
            "ok",
            null
        ),
        new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "system.hw.uptime[hrSystemUptime.0]",
            "system.hw.uptime[hrSystemUptime.0]",
            "",
            null,
            "hw-uptime-uuid",
            36000.0,
            "36000",
            null,
            null,
            "ok",
            null
        ),
        new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "system.net.uptime[sysUpTime.0]",
            "system.net.uptime[sysUpTime.0]",
            "",
            null,
            "net-uptime-uuid",
            36000.0,
            "36000",
            null,
            null,
            "ok",
            null
        ),
        new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "zabbix[host,snmp,available]",
            "zabbix[host,snmp,available]",
            "",
            null,
            "snmp-availability-uuid",
            snmpAvailability,
            snmpAvailability == 0.0 ? "0" : "1",
            null,
            null,
            "ok",
            null
        )
    );
  }

  private void awaitCondition(Check condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      if (condition.matches()) {
        return;
      }
      Thread.sleep(250L);
    }
    throw new AssertionError("Condition was not met within timeout.");
  }

  @FunctionalInterface
  private interface Check {
    boolean matches();
  }
}
