package com.networkscanner.backend.integration.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProbeBootstrapPayloadMapperTest {

  private final ProbeBootstrapPayloadMapper mapper = new ProbeBootstrapPayloadMapper();

  @Test
  void mapParsesTemplateIdsAndFallsBackToTemplateId() {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(42L);
    entity.setIp("10.0.0.42");
    entity.setHostName("sw-42");
    entity.setName("Switch 42");
    entity.setSerialNumber("SN42");
    entity.setMacAddress("AA:BB:CC:DD:EE:42");
    entity.setVendor("Cisco");
    entity.setModel("C9300");
    entity.setFirmwareVersion("17.9");
    entity.setTemplateIds("tpl-a, tpl-b, tpl-a");
    entity.setTemplateId("fallback");
    entity.setEffectiveTemplateId("tpl-effective");
    entity.setTemplateVersion("3");
    entity.setPackVersion("1.2.0");
    entity.setSchemaVersion("2.0");
    entity.setUpdatedAt(OffsetDateTime.parse("2026-04-30T10:00:00Z"));

    ProbeBootstrapPayload payload = mapper.map(entity, "networkscanner");

    assertEquals(42L, payload.externalDeviceId());
    assertEquals("networkscanner", payload.sourceSystem());
    assertIterableEquals(List.of("tpl-a", "tpl-b"), payload.templateIds());
  }

  @Test
  void mapUsesFallbackTemplateWhenTemplateIdsCsvIsEmpty() {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(7L);
    entity.setTemplateIds(" ");
    entity.setTemplateId("tpl-default");

    ProbeBootstrapPayload payload = mapper.map(entity, "networkscanner");

    assertIterableEquals(List.of("tpl-default"), payload.templateIds());
  }
}
