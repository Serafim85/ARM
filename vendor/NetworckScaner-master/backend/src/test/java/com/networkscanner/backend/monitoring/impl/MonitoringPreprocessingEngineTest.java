package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixPreprocessingStep;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MonitoringPreprocessingEngineTest {

  private final MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();

  @Test
  void jsonPathAndRangeStepsAreApplied() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("JSONPATH", List.of("$.value"), null, null),
        new ZabbixPreprocessingStep("IN_RANGE", List.of("10:20"), null, null)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "{\"value\":15}",
        null,
        OffsetDateTime.now()
    );

    assertEquals("ok", processed.status());
    assertFalse(processed.discarded());
    assertEquals("15", processed.textValue());
    assertEquals(15.0, processed.numericValue());
  }

  @Test
  void unsupportedStepReturnsFallbackStatus() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("SOME_UNKNOWN_STEP", List.of("return value"), null, null)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "42",
        null,
        OffsetDateTime.now()
    );

    assertEquals("fallback_applied", processed.status());
    assertTrue(processed.note().contains("Unsupported preprocessing step"));
    assertEquals(42.0, processed.numericValue());
  }

  @Test
  void javascriptStepConvertsReturnedValueToString() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("JAVASCRIPT", List.of("return Number(value) + 1;"), null, null)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "41",
        null,
        OffsetDateTime.now()
    );

    assertEquals("ok", processed.status());
    assertEquals("42", processed.textValue());
    assertEquals(42.0, processed.numericValue());
  }

  @Test
  void javascriptNullResultDiscardsValue() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("JAVASCRIPT", List.of("return null;"), null, null)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "41",
        null,
        OffsetDateTime.now()
    );

    assertEquals("discarded", processed.status());
    assertTrue(processed.discarded());
  }

  @Test
  void supportsFilteredJsonPathFirstExpression() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("JSONPATH", List.of("$[?(@.laName == 'Load-1')].laLoad.first()"), null, null)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "[{\"laName\":\"Load-1\",\"laLoad\":\"0.42\"},{\"laName\":\"Load-5\",\"laLoad\":\"0.35\"}]",
        null,
        OffsetDateTime.now()
    );

    assertEquals("ok", processed.status());
    assertEquals("0.42", processed.textValue());
    assertEquals(0.42, processed.numericValue());
  }

  @Test
  void snmpWalkValueExtractsCellFromMasterWalkJson() {
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m",
        "net.if.walk",
        "Walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.2.1.31.1.1.1.6,1.3.6.1.2.1.2.2.1.2]",
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
    ZabbixItemRuntime dependent = new ZabbixItemRuntime(
        "d",
        "net.if.in[ifHCInOctets.2]",
        "In",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        null,
        null,
        "net.if.walk",
        null,
        null,
        List.of(new ZabbixPreprocessingStep("SNMP_WALK_VALUE", List.of("1.3.6.1.2.1.31.1.1.1.6.{#SNMPINDEX}", "0"), null, null)),
        null,
        true,
        "if.discovery"
    );
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(master.key(), master);
    items.put(dependent.key(), dependent);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "t",
        "SNMP",
        "T",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "t",
        dependent,
        dependent.key(),
        dependent.key(),
        "2",
        "if.discovery",
        null,
        Map.of("{#SNMPINDEX}", "2")
    );
    String masterJson = "[{\"index\":\"2\",\"col1\":\"126237379\",\"col2\":\"ens18\"}]";

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        dependent,
        masterJson,
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );

    assertEquals("ok", processed.status());
    assertEquals("126237379", processed.textValue());
    assertEquals(126237379.0, processed.numericValue());
  }

  @Test
  void snmpWalkValueDiscardsIfaceTypeWhenMasterWalkIsEmpty() {
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m",
        "net.if.walk",
        "Walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.2.1.2.2.1.8,1.3.6.1.2.1.2.2.1.3]",
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
    ZabbixItemRuntime dependent = new ZabbixItemRuntime(
        "d",
        "net.if.type[ifType.2]",
        "IF type",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        null,
        null,
        "net.if.walk",
        null,
        null,
        List.of(new ZabbixPreprocessingStep("SNMP_WALK_VALUE", List.of("1.3.6.1.2.1.2.2.1.3.{#SNMPINDEX}", "0"), null, null)),
        null,
        true,
        "if.discovery"
    );
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(master.key(), master);
    items.put(dependent.key(), dependent);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "t",
        "SNMP",
        "T",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "t",
        dependent,
        dependent.key(),
        dependent.key(),
        "2",
        "if.discovery",
        null,
        Map.of("{#SNMPINDEX}", "2")
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        dependent,
        "[]",
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );

    assertEquals("discarded", processed.status());
    assertTrue(processed.discarded());
    assertNull(processed.numericValue());
  }

  @Test
  void snmpWalkValueScalarOidUsesFirstRowWhenWalkIndexDiffersFromScalarSuffix() {
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m",
        "system.cpu.walk",
        "Walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.2.1.25.3.3.1.1,1.3.6.1.4.1.2021.11.53.0]",
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
    ZabbixItemRuntime dependent = new ZabbixItemRuntime(
        "d",
        "system.cpu.idle[ssCpuRawIdle.0]",
        "Idle",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        null,
        null,
        "system.cpu.walk",
        null,
        null,
        List.of(new ZabbixPreprocessingStep("SNMP_WALK_VALUE", List.of("1.3.6.1.4.1.2021.11.53.0", "0"), null, null)),
        null,
        true,
        "cpu.discovery"
    );
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(master.key(), master);
    items.put(dependent.key(), dependent);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "t",
        "SNMP",
        "T",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "t",
        dependent,
        dependent.key(),
        dependent.key(),
        "0",
        "cpu.discovery",
        null,
        Map.of("{#SNMPINDEX}", "0")
    );
    String masterJson = "[{\"index\":\"196608\",\"col1\":\"0\",\"col2\":\"631068\"}]";

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        dependent,
        masterJson,
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );

    assertEquals("ok", processed.status());
    assertEquals("631068", processed.textValue());
    assertEquals(631068.0, processed.numericValue());
  }

  @Test
  void snmpWalkToJsonBuildsMacroRowsFromWalkPayload() {
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m",
        "vfs.fs.discovery.walk",
        "Walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.4.1.2021.9.1.2,1.3.6.1.4.1.2021.9.1.3]",
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
    ZabbixItemRuntime dependent = new ZabbixItemRuntime(
        "d2",
        "vfs.fs.discovery.json",
        "Discovery json",
        "DEPENDENT",
        null,
        60,
        "TEXT",
        null,
        null,
        "vfs.fs.discovery.walk",
        null,
        null,
        List.of(new ZabbixPreprocessingStep(
            "SNMP_WALK_TO_JSON",
            List.of(
                "{#FSNAME}", "1.3.6.1.4.1.2021.9.1.2", "",
                "{#FSDEVICE}", "1.3.6.1.4.1.2021.9.1.3", "unknown"
            ),
            null,
            null
        )),
        null,
        true,
        "fs.discovery"
    );
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(master.key(), master);
    items.put(dependent.key(), dependent);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "t",
        "SNMP",
        "T",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "t",
        dependent,
        dependent.key(),
        dependent.key(),
        "1",
        "fs.discovery",
        null,
        Map.of("{#SNMPINDEX}", "1")
    );
    String masterJson = "[{\"index\":\"1\",\"dskPath\":\"/\",\"dskDevice\":\"/dev/sda1\"},{\"index\":\"2\",\"dskPath\":\"/tmp\"}]";

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        dependent,
        masterJson,
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );

    assertEquals("ok", processed.status());
    assertEquals(
        "[{\"{#SNMPINDEX}\":\"1\",\"{#FSNAME}\":\"/\",\"{#FSDEVICE}\":\"/dev/sda1\"}," +
            "{\"{#SNMPINDEX}\":\"2\",\"{#FSNAME}\":\"/tmp\",\"{#FSDEVICE}\":\"unknown\"}]",
        processed.textValue()
    );
    assertNull(processed.numericValue());
  }

  @Test
  void snmpWalkToJsonOnSnmpWalkItemWithoutMasterItemUsesOwnWalkOid() {
    ZabbixItemRuntime walk = new ZabbixItemRuntime(
        "wl",
        "system.cpu.load.walk",
        "Load walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.4.1.2021.10.1.2,1.3.6.1.4.1.2021.10.1.3]",
        60,
        "TEXT",
        null,
        null,
        null,
        null,
        null,
        List.of(new ZabbixPreprocessingStep(
            "SNMP_WALK_TO_JSON",
            List.of(
                "laName", "1.3.6.1.4.1.2021.10.1.2", "0",
                "laLoad", "1.3.6.1.4.1.2021.10.1.3", "0"
            ),
            null,
            null
        )),
        null,
        false,
        null
    );
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(walk.key(), walk);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "t",
        "SNMP",
        "T",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "t",
        walk,
        walk.key(),
        walk.key(),
        "",
        null,
        walk.snmpOid(),
        Map.of()
    );
    String rawWalk = "[{\"index\":\"1\",\"laName\":\"Load-1\",\"laLoad\":\"0.29\"}]";

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        walk,
        rawWalk,
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );

    assertEquals("ok", processed.status());
    assertEquals("[{\"{#SNMPINDEX}\":\"1\",\"laName\":\"Load-1\",\"laLoad\":\"0.29\"}]", processed.textValue());
  }

  @Test
  void customErrorHandlerAppliesFallbackValue() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("CHECK_REGEX_ERROR", List.of("["), "CUSTOM_VALUE", "0")
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "abc",
        null,
        OffsetDateTime.now()
    );

    assertEquals("fallback_applied", processed.status());
    assertEquals("0", processed.textValue());
    assertEquals(0.0, processed.numericValue());
  }

  @Test
  void checkNotSupportedWithCustomValueReturnsFallback() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("CHECK_NOT_SUPPORTED", List.of("-1"), "CUSTOM_VALUE", "0")
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "noSuchObject",
        null,
        OffsetDateTime.now()
    );

    assertEquals("fallback_applied", processed.status());
    assertEquals("0", processed.textValue());
    assertEquals(0.0, processed.numericValue());
  }

  @Test
  void discardUnchangedHeartbeatKeepsValueAfterHeartbeatInterval() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("DISCARD_UNCHANGED_HEARTBEAT", List.of("1m"), null, null)
    );
    OffsetDateTime now = OffsetDateTime.now();
    ItemStateSnapshot previous = new ItemStateSnapshot(
        "t",
        "metric.key",
        null,
        10.0,
        "10",
        "%",
        null,
        "ok",
        null,
        now.minusMinutes(2)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "10",
        previous,
        now
    );

    assertEquals("ok", processed.status());
    assertFalse(processed.discarded());
    assertEquals(10.0, processed.numericValue());
  }

  @Test
  void discardUnchangedHeartbeatDiscardsWithinHeartbeatInterval() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("DISCARD_UNCHANGED_HEARTBEAT", List.of("5m"), null, null)
    );
    OffsetDateTime now = OffsetDateTime.now();
    ItemStateSnapshot previous = new ItemStateSnapshot(
        "t",
        "metric.key",
        null,
        10.0,
        "10",
        "%",
        null,
        "ok",
        null,
        now.minusMinutes(1)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "10",
        previous,
        now
    );

    assertEquals("discarded", processed.status());
    assertTrue(processed.discarded());
  }

  @Test
  void changePerSecondPersistsRawCounterInTextValueForNextDelta() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("CHANGE_PER_SECOND", List.of(), null, null)
    );
    OffsetDateTime t0 = OffsetDateTime.parse("2026-04-10T12:00:00Z");
    MonitoringPreprocessingEngine.ProcessedMonitoringValue first = engine.process(
        runtime,
        "1000",
        null,
        t0
    );
    assertEquals("ok", first.status());
    assertEquals(0.0d, first.numericValue(), 0.0001d);
    assertEquals("1000", first.textValue());

    ItemStateSnapshot previous = new ItemStateSnapshot(
        "t",
        "vfs.dev.read.rate[diskIOReads.9]",
        "9",
        0.0d,
        "1000",
        "r/s",
        null,
        "ok",
        null,
        t0
    );
    OffsetDateTime t1 = OffsetDateTime.parse("2026-04-10T12:00:30Z");
    MonitoringPreprocessingEngine.ProcessedMonitoringValue second = engine.process(
        runtime,
        "1060",
        previous,
        t1
    );
    assertEquals("ok", second.status());
    assertEquals(2.0d, second.numericValue(), 0.0001d);
    assertEquals("1060", second.textValue());
  }

  @Test
  void simpleChangeReseedsWhenCounterDecreases() {
    ZabbixItemRuntime runtime = runtimeWithSteps(
        new ZabbixPreprocessingStep("SIMPLE_CHANGE", List.of(), null, null)
    );
    OffsetDateTime now = OffsetDateTime.now();
    ItemStateSnapshot previous = new ItemStateSnapshot(
        "t",
        "metric.key",
        null,
        10.0,
        "10",
        "%",
        null,
        "ok",
        null,
        now.minusMinutes(1)
    );

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        runtime,
        "9",
        previous,
        now
    );

    assertEquals("ok", processed.status());
    assertFalse(processed.discarded());
    assertEquals(0.0d, processed.numericValue(), 0.0001d);
    assertEquals("9", processed.textValue());
  }

  @Test
  void netIfSpeedFallsBackToIfSpeedWhenIfHighSpeedIsZero() {
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m-if",
        "net.if.walk",
        "Walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.2.1.2.2.1.8,1.3.6.1.2.1.2.2.1.5,1.3.6.1.2.1.31.1.1.15]",
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
    ZabbixItemRuntime speed = new ZabbixItemRuntime(
        "d-speed",
        "net.if.speed[ifHighSpeed.2]",
        "Speed",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        "bps",
        null,
        "net.if.walk",
        null,
        null,
        List.of(
            new ZabbixPreprocessingStep("SNMP_WALK_VALUE", List.of("1.3.6.1.2.1.31.1.1.15.{#SNMPINDEX}", "0"), null, null),
            new ZabbixPreprocessingStep("MULTIPLIER", List.of("1000000"), null, null),
            new ZabbixPreprocessingStep("DISCARD_UNCHANGED_HEARTBEAT", List.of("1h"), null, null)
        ),
        null,
        true,
        "if.discovery"
    );
    Map<String, ZabbixItemRuntime> items = Map.of(master.key(), master, speed.key(), speed);
    ResolvedMonitoringTemplate template = templateWithItems(items);
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "t",
        speed,
        speed.key(),
        speed.key(),
        "2",
        "if.discovery",
        null,
        Map.of("{#SNMPINDEX}", "2")
    );
    String masterJson = "[{\"index\":\"2\",\"col1\":\"1\",\"col2\":\"1000000000\",\"col3\":\"0\"}]";

    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        speed,
        masterJson,
        new ItemStateSnapshot(
            "t",
            speed.key(),
            "2",
            1000000000.0,
            "1000000000",
            "bps",
            null,
            "ok",
            null,
            OffsetDateTime.now().minusMinutes(5)
        ),
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );

    assertEquals("ok", processed.status());
    assertFalse(processed.discarded());
    assertEquals(1000000000.0, processed.numericValue(), 0.0001d);
  }

  private ResolvedMonitoringTemplate templateWithItems(Map<String, ZabbixItemRuntime> items) {
    return new ResolvedMonitoringTemplate(
        "t",
        "SNMP",
        "T",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
  }

  private ZabbixItemRuntime runtimeWithSteps(ZabbixPreprocessingStep... steps) {
    return new ZabbixItemRuntime(
        "uuid",
        "metric.key",
        "Metric",
        "SNMP_AGENT",
        "1.3.6.1.2.1.1.3.0",
        60,
        "FLOAT",
        "%",
        "",
        null,
        "",
        "",
        List.of(steps),
        null,
        false,
        null
    );
  }
}
