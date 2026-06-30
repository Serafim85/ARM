package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
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
class MetricValuesUnitLabelIntegrationTest {

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
  private ZabbixRuntimeStateService runtimeStateService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void saveItemValuesPersistsUnitLabel() {
    long deviceId = 902L;
    String ip = "10.10.10.902";
    insertDevice(deviceId, ip);
    OffsetDateTime now = OffsetDateTime.now();

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(deviceId);
    device.setIp(ip);

    runtimeStateService.saveItemValues(
        device,
        "network-generic-device-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        List.of(new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "net.if.in[eth0]",
            "ifHCInOctets[eth0]",
            "eth0",
            null,
            "item-uuid-1",
            1_000_000.0,
            null,
            "bps",
            null,
            null,
            null
        )),
        now
    );

    String unitLabel = jdbcTemplate.queryForObject(
        "SELECT unit_label FROM metric_values WHERE device_ip = ? AND metric_name = ?",
        String.class,
        ip,
        "net.if.in[eth0]"
    );
    assertNotNull(unitLabel);
    assertEquals("bps", unitLabel);
  }

  private void insertDevice(long deviceId, String ip) {
    jdbcTemplate.update("DELETE FROM metric_values WHERE device_ip = ?", ip);
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
        "00:11:22:33:44:02",
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
}
