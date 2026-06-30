package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricCollectorSnmpAvailabilityTest {

  @Test
  void snmpAvailableWhenWalkMasterReturnedEmptyArrayInRawMap() {
    ZabbixItemRuntime walkMaster = snmpWalkMaster("walk-uuid", "net.if.walk");
    MaterializedZabbixItem materialized = materialized(walkMaster, "net.if.walk", "walk[1.3.6.1.2.1.2.2.1.8]");

    assertTrue(MetricCollectorServiceImpl.resolveSnmpAgentAvailability(
        List.of(materialized),
        Map.of("net.if.walk", "[]"),
        List.of()
    ));
  }

  @Test
  void snmpAvailableWhenWalkMasterReturnedTextPayloadInValues() {
    ZabbixItemRuntime walkMaster = snmpWalkMaster("walk-uuid", "net.if.walk");
    MaterializedZabbixItem materialized = materialized(walkMaster, "net.if.walk", "walk[1.3.6.1.2.1.2.2.1.8]");
    ZabbixItemValue polled = new ZabbixItemValue(
        "tpl",
        "net.if.walk",
        "net.if.walk",
        "",
        null,
        "walk-uuid",
        null,
        "[{\"index\":\"2\"}]",
        null,
        null,
        "ok",
        null
    );

    assertTrue(MetricCollectorServiceImpl.resolveSnmpAgentAvailability(
        List.of(materialized),
        Map.of(),
        List.of(polled)
    ));
  }

  @Test
  void snmpUnavailableWhenNoRawResponseAndNoPollValues() {
    ZabbixItemRuntime walkMaster = snmpWalkMaster("walk-uuid", "net.if.walk");
    MaterializedZabbixItem materialized = materialized(walkMaster, "net.if.walk", "walk[1.3.6.1.2.1.2.2.1.8]");

    assertFalse(MetricCollectorServiceImpl.resolveSnmpAgentAvailability(
        List.of(materialized),
        Map.of(),
        List.of()
    ));
  }

  private static ZabbixItemRuntime snmpWalkMaster(String uuid, String key) {
    return new ZabbixItemRuntime(
        uuid,
        key,
        key,
        "SNMP_AGENT",
        "walk[1.3.6.1.2.1.2.2.1.8]",
        60,
        "TEXT",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null
    );
  }

  private static MaterializedZabbixItem materialized(ZabbixItemRuntime runtime, String key, String oid) {
    return new MaterializedZabbixItem(
        "tpl",
        runtime,
        key,
        key,
        "",
        null,
        oid,
        Map.of()
    );
  }
}
