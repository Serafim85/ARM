package com.networkscanner.backend.integration.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.integration.api.InstanceKeyHostProvider;
import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalIncidentUpsertMapperTest {

  @Test
  void fromMutationUsesRuntimeHostnameForInstanceKeyAndCorrelationKey() {
    InstanceKeyHostProvider hostProvider = org.mockito.Mockito.mock(InstanceKeyHostProvider.class);
    when(hostProvider.getHostName()).thenReturn("ns-host-01");
    ExternalIncidentUpsertMapper mapper = new ExternalIncidentUpsertMapper(hostProvider);

    MonitoringEventMutation mutation = new MonitoringEventMutation(
        MonitoringEventMutationAction.OPEN,
        "zabbix[host,snmp,available]",
        "tr-1",
        "No SNMP data collection",
        "max(/Linux SNMP/zabbix[host,snmp,available],5m)=0",
        null,
        null,
        null,
        ThresholdLevel.WARNING,
        0.0,
        0.0,
        OffsetDateTime.parse("2026-05-07T15:03:00.8476234Z"),
        null,
        "WARNING"
    );
    EvaluatedMonitoringEvent event = new EvaluatedMonitoringEvent(
        "msg-1",
        "1.0",
        5L,
        "10.0.0.5",
        "linux-snmp",
        "7.4",
        "filesystem-2026-05-07",
        OffsetDateTime.parse("2026-05-07T15:03:00.8476234Z"),
        List.of(),
        List.of(mutation),
        null
    );

    ExternalIncidentUpsert payload = mapper.fromMutation(event, mutation, "Cisco296");

    assertEquals("ns-host-01", payload.instanceKey());
    assertTrue(payload.correlationKey().contains("|ns-host-01|"));
  }

  @Test
  void fromEntityUsesRuntimeHostnameForInstanceKeyAndCorrelationKey() {
    InstanceKeyHostProvider hostProvider = org.mockito.Mockito.mock(InstanceKeyHostProvider.class);
    when(hostProvider.getHostName()).thenReturn("ns-host-02");
    ExternalIncidentUpsertMapper mapper = new ExternalIncidentUpsertMapper(hostProvider);

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(7L);
    MonitoringEventEntity entity = new MonitoringEventEntity();
    entity.setDevice(device);
    entity.setTemplateId("linux-snmp");
    entity.setTemplateVersion("7.4");
    entity.setStatus(MonitoringEventStatus.OPEN);
    entity.setTriggerUuid("tr-2");
    entity.setTriggerName("No SNMP data collection");
    entity.setMetricName("zabbix[host,snmp,available]");
    entity.setInstanceKey(null);
    entity.setThresholdLevel(ThresholdLevel.WARNING);
    entity.setThresholdValue(0.0);
    entity.setActualValue(0.0);
    entity.setBreachStartedAt(OffsetDateTime.parse("2026-05-07T15:03:00.8476234Z"));

    ExternalIncidentUpsert payload = mapper.fromEntity(entity, "Cisco296");

    assertEquals("ns-host-02", payload.instanceKey());
    assertTrue(payload.correlationKey().contains("|ns-host-02|"));
  }
}
