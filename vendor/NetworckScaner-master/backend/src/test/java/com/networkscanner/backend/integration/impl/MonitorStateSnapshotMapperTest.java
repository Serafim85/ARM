package com.networkscanner.backend.integration.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.networkscanner.backend.integration.dto.MonitorState;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonitorStateSnapshotMapperTest {

  private final MonitorStateSnapshotMapper mapper = new MonitorStateSnapshotMapper();

  @Test
  void forMonitorOnBuildsNestedDeviceSnapshot() {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(101L);
    entity.setName("EdgeSwitch");
    entity.setIp("10.10.10.10");
    entity.setTemplateIds("tpl-1:5,tpl-2");
    entity.setTemplateVersion("5");

    List<String> templateIds = List.of("tpl-1:5", "tpl-2");
    MonitorStateSnapshot result = mapper.forMonitorOn(entity, "networkscanner", templateIds);

    assertEquals("networkscanner", result.sourceSystem());
    assertEquals(101L, result.externalDeviceId());
    assertNotNull(result.device());
    assertEquals(MonitorState.MONITOR_ON, result.device().state());
    assertEquals("EdgeSwitch", result.device().name());
    assertEquals("10.10.10.10", result.device().ipAddress());
    assertEquals(templateIds, result.device().templateIds());
    assertEquals("5", result.device().defaultTemplateVersion());
  }

  @Test
  void forMonitorOffIncludesOnlyDeviceState() {
    MonitorStateSnapshot result = mapper.forMonitorOff(200L, "networkscanner");

    assertEquals(MonitorState.MONITOR_OFF, result.device().state());
    assertEquals(200L, result.externalDeviceId());
    assertEquals("1.1", result.schemaVersion());
    assertNull(result.device().name());
    assertNull(result.device().ipAddress());
    assertNull(result.device().templateIds());
    assertNull(result.device().defaultTemplateVersion());
  }

  @Test
  void forDeletedIncludesOnlyDeviceState() {
    MonitorStateSnapshot result = mapper.forDeleted(201L, "networkscanner");

    assertEquals(MonitorState.DELETED, result.device().state());
    assertEquals(201L, result.externalDeviceId());
    assertEquals("1.1", result.schemaVersion());
    assertNull(result.device().name());
    assertNull(result.device().ipAddress());
    assertNull(result.device().templateIds());
    assertNull(result.device().defaultTemplateVersion());
  }
}
