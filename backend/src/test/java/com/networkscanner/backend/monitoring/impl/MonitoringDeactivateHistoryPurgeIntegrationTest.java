package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MonitoringDeactivateHistoryPurgeIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
      DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres")
  )
      .withDatabaseName("networkscanner")
      .withUsername("networkscanner")
      .withPassword("networkscanner");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("monitoring.collector.enabled", () -> "false");
    registry.add("monitoring.kafka.enabled", () -> "false");
  }

  @Autowired
  private MonitoringService monitoringService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void deactivateRemovesMetricAndAvailabilityHistoryForDeviceIp() {
    long deviceId = 901L;
    String ip = "10.10.10.901";
    insertDevice(deviceId, ip);
    insertHistory(ip);

    assertEquals(2L, count("SELECT COUNT(*) FROM metric_values WHERE device_ip = ?", ip));
    assertEquals(1L, count("SELECT COUNT(*) FROM availability_history WHERE device_ip = ?", ip));
    assertEquals(1L, count("SELECT COUNT(*) FROM telemetry_history WHERE device_ip = ?", ip));

    refreshMetricValuesAggregate();
    assertEquals(2L, count("SELECT COUNT(*) FROM metric_values_1h WHERE device_ip = ?", ip));

    monitoringService.deactivate(List.of(ip), null);

    assertEquals(0L, count("SELECT COUNT(*) FROM monitored_devices WHERE ip = ?", ip));
    assertEquals(0L, count("SELECT COUNT(*) FROM metric_values WHERE device_ip = ?", ip));
    assertEquals(0L, count("SELECT COUNT(*) FROM metric_values_1h WHERE device_ip = ?", ip));
    assertEquals(0L, count("SELECT COUNT(*) FROM availability_history WHERE device_ip = ?", ip));
    assertEquals(0L, count("SELECT COUNT(*) FROM telemetry_history WHERE device_ip = ?", ip));
  }

  @Test
  void flywayAppliesTimescalePolicies() {
    Boolean compressionEnabled = jdbcTemplate.queryForObject(
        """
            SELECT compression_enabled
            FROM timescaledb_information.hypertables
            WHERE hypertable_name = 'metric_values'
            """,
        Boolean.class
    );
    assertEquals(Boolean.TRUE, compressionEnabled);
    assertEquals(
        0L,
        count(
            """
                SELECT COUNT(*)
                FROM timescaledb_information.continuous_aggregates
                WHERE view_name = 'metric_values_1m'
                """
        )
    );
    assertEquals(
        1L,
        count(
            """
                SELECT COUNT(*)
                FROM timescaledb_information.continuous_aggregates
                WHERE view_name = 'metric_values_1h'
                """
        )
    );
  }

  private void refreshMetricValuesAggregate() {
    jdbcTemplate.execute(
        "CALL refresh_continuous_aggregate('metric_values_1h', NULL, NULL)"
    );
  }

  private void insertHistory(String ip) {
    OffsetDateTime now = OffsetDateTime.now();
    jdbcTemplate.update(
        """
            INSERT INTO metric_values (recorded_at, device_ip, metric_name, metric_value)
            VALUES (?, ?, ?, ?), (?, ?, ?, ?)
            """,
        now, ip, "cpu.util", 42.0,
        now.minusMinutes(1), ip, "mem.used", 70.0
    );
    jdbcTemplate.update(
        """
            INSERT INTO availability_history (
              recorded_at, device_ip, host_status, icmp_active, snmp_active, ssh_active
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
        now, ip, "Включено", true, true, false
    );
    jdbcTemplate.update(
        """
            INSERT INTO telemetry_history (recorded_at, device_ip, cpu_usage, ram_usage, rom_usage)
            VALUES (?, ?, ?, ?, ?)
            """,
        now, ip, 10.0, 20.0, 30.0
    );
  }

  private void insertDevice(long deviceId, String ip) {
    jdbcTemplate.update("DELETE FROM metric_values WHERE device_ip = ?", ip);
    jdbcTemplate.update("DELETE FROM availability_history WHERE device_ip = ?", ip);
    jdbcTemplate.update("DELETE FROM telemetry_history WHERE device_ip = ?", ip);
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
        "00:11:22:33:44:01",
        "Cisco",
        "SG500X",
        "1.0",
        "SNMP v2c",
        "Включено",
        DeviceHealthStatus.NORM.name(),
        "default",
        "[]",
        "network-generic-device-by-snmp",
        "network-generic-device-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        "1"
    );
  }

  private long count(String sql, String ip) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class, ip);
    return value == null ? 0L : value;
  }

  private long count(String sql) {
    Long value = jdbcTemplate.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }
}
