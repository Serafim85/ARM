package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.dto.ZabbixPreprocessingStep;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DerivedMonitoringItemExecutorTest {

  @Test
  void executeDependentAppliesSnmpWalkToJsonInRuntimePipeline() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
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
        "d",
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
        "vfs.fs.discovery"
    );
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(master.key(), master);
    items.put(dependent.key(), dependent);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        100,
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
    MaterializedZabbixItem dependentMaterialized = new MaterializedZabbixItem(
        "tpl",
        dependent,
        dependent.key(),
        dependent.key(),
        "1",
        "vfs.fs.discovery",
        null,
        Map.of("{#SNMPINDEX}", "1")
    );
    String masterJson = "[{\"index\":\"1\",\"dskPath\":\"/\",\"dskDevice\":\"/dev/sda1\"},{\"index\":\"2\",\"dskPath\":\"/tmp\"}]";
    Map<String, ZabbixItemValue> currentCycleValues = Map.of(
        "vfs.fs.discovery.walk::",
        new ZabbixItemValue(
            "tpl",
            master.key(),
            master.key(),
            "",
            null,
            master.uuid(),
            null,
            masterJson,
            null,
            null,
            "ok",
            null
        )
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(dependentMaterialized),
        Map.<String, ItemStateSnapshot>of(),
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, values.size());
    assertEquals("ok", values.get(0).preprocessingStatus());
    assertEquals(
        "[{\"{#SNMPINDEX}\":\"1\",\"{#FSNAME}\":\"/\",\"{#FSDEVICE}\":\"/dev/sda1\"}," +
            "{\"{#SNMPINDEX}\":\"2\",\"{#FSNAME}\":\"/tmp\",\"{#FSDEVICE}\":\"unknown\"}]",
        values.get(0).textValue()
    );
    assertNull(values.get(0).numericValue());
  }

  @Test
  void executeDependentMarksFallbackWhenMasterItemIsMissingInTemplate() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime dependent = new ZabbixItemRuntime(
        "d-missing",
        "vfs.fs.discovery.json.missing",
        "Discovery json",
        "DEPENDENT",
        null,
        60,
        "TEXT",
        null,
        null,
        "master.missing",
        null,
        null,
        List.of(new ZabbixPreprocessingStep(
            "SNMP_WALK_TO_JSON",
            List.of("{#FSNAME}", "1.3.6.1.4.1.2021.9.1.2", ""),
            null,
            null
        )),
        null,
        true,
        "vfs.fs.discovery"
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        100,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(dependent.key(), dependent),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem dependentMaterialized = new MaterializedZabbixItem(
        "tpl",
        dependent,
        dependent.key(),
        dependent.key(),
        "1",
        "vfs.fs.discovery",
        null,
        Map.of("{#SNMPINDEX}", "1")
    );
    Map<String, ZabbixItemValue> currentCycleValues = Map.of(
        "master.missing::",
        new ZabbixItemValue("tpl", "master.missing", "master.missing", "", null, "m", null, "[]", null, null, "ok", null)
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(dependentMaterialized),
        Map.<String, ItemStateSnapshot>of(),
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, values.size());
    assertEquals("fallback_applied", values.get(0).preprocessingStatus());
    assertTrue(values.get(0).preprocessingNote().contains("master item not found"));
  }

  @Test
  void executeDependentKeepsRawValueWhenMasterPayloadIsInvalidJson() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m-invalid",
        "vfs.fs.discovery.walk",
        "Walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.4.1.2021.9.1.2]",
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
        "d-invalid",
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
            List.of("{#FSNAME}", "1.3.6.1.4.1.2021.9.1.2", ""),
            null,
            null
        )),
        null,
        true,
        "vfs.fs.discovery"
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        100,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(master.key(), master, dependent.key(), dependent),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem dependentMaterialized = new MaterializedZabbixItem(
        "tpl",
        dependent,
        dependent.key(),
        dependent.key(),
        "1",
        "vfs.fs.discovery",
        null,
        Map.of("{#SNMPINDEX}", "1")
    );
    String brokenMasterPayload = "not-a-json";
    Map<String, ZabbixItemValue> currentCycleValues = Map.of(
        "vfs.fs.discovery.walk::",
        new ZabbixItemValue(
            "tpl",
            master.key(),
            master.key(),
            "",
            null,
            master.uuid(),
            null,
            brokenMasterPayload,
            null,
            null,
            "ok",
            null
        )
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(dependentMaterialized),
        Map.<String, ItemStateSnapshot>of(),
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, values.size());
    assertEquals("ok", values.get(0).preprocessingStatus());
    assertEquals(brokenMasterPayload, values.get(0).textValue());
  }

  @Test
  void executeDependentMarksFallbackWhenMasterOidIsNotWalkExpression() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m-nonwalk",
        "vfs.fs.discovery.walk",
        "Walk",
        "SNMP_AGENT",
        "get[1.3.6.1.2.1.1.3.0]",
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
        "d-nonwalk",
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
            List.of("{#FSNAME}", "1.3.6.1.4.1.2021.9.1.2", ""),
            null,
            null
        )),
        null,
        true,
        "vfs.fs.discovery"
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        100,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(master.key(), master, dependent.key(), dependent),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem dependentMaterialized = new MaterializedZabbixItem(
        "tpl",
        dependent,
        dependent.key(),
        dependent.key(),
        "1",
        "vfs.fs.discovery",
        null,
        Map.of("{#SNMPINDEX}", "1")
    );
    String masterPayload = "{\"uptime\":\"123\"}";
    Map<String, ZabbixItemValue> currentCycleValues = Map.of(
        "vfs.fs.discovery.walk::",
        new ZabbixItemValue(
            "tpl",
            master.key(),
            master.key(),
            "",
            null,
            master.uuid(),
            null,
            masterPayload,
            null,
            null,
            "ok",
            null
        )
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(dependentMaterialized),
        Map.<String, ItemStateSnapshot>of(),
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, values.size());
    assertEquals("fallback_applied", values.get(0).preprocessingStatus());
    assertTrue(values.get(0).preprocessingNote().contains("master snmp_oid is not walk"));
    assertEquals(masterPayload, values.get(0).textValue());
  }

  @Test
  void executeDependentUsesPreviousMasterWhenCurrentWalkPayloadIsEmpty() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m-if",
        "net.if.walk",
        "IF walk",
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
        "d-if-type",
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
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        100,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(master.key(), master, dependent.key(), dependent),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    String goodMaster = "[{\"index\":\"2\",\"col1\":\"1\",\"col2\":\"6\"}]";
    MaterializedZabbixItem dependentMaterialized = new MaterializedZabbixItem(
        "tpl",
        dependent,
        dependent.key(),
        dependent.key(),
        "2",
        "if.discovery",
        null,
        Map.of("{#SNMPINDEX}", "2")
    );
    Map<String, ZabbixItemValue> currentCycleValues = Map.of(
        "net.if.walk::",
        new ZabbixItemValue(
            "tpl",
            master.key(),
            master.key(),
            "",
            null,
            master.uuid(),
            null,
            "[]",
            null,
            null,
            "ok",
            null
        )
    );
    Map<String, ItemStateSnapshot> state = Map.of(
        "net.if.walk::",
        new ItemStateSnapshot(
            "tpl",
            master.key(),
            "",
            null,
            goodMaster,
            null,
            null,
            "ok",
            null,
            OffsetDateTime.now().minusMinutes(1)
        )
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(dependentMaterialized),
        state,
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, values.size());
    assertEquals(6.0d, values.get(0).numericValue());
  }

  @Test
  void executeDependentSkipsWhenOnlyMasterPayloadIsEmptyWalkArray() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime master = new ZabbixItemRuntime(
        "m-if",
        "net.if.walk",
        "IF walk",
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
        "d-if-type",
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
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        100,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(master.key(), master, dependent.key(), dependent),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    MaterializedZabbixItem dependentMaterialized = new MaterializedZabbixItem(
        "tpl",
        dependent,
        dependent.key(),
        dependent.key(),
        "2",
        "if.discovery",
        null,
        Map.of("{#SNMPINDEX}", "2")
    );
    Map<String, ItemStateSnapshot> state = Map.of(
        "net.if.walk::",
        new ItemStateSnapshot(
            "tpl",
            master.key(),
            "",
            null,
            "[]",
            null,
            null,
            "ok",
            null,
            OffsetDateTime.now().minusMinutes(1)
        )
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(dependentMaterialized),
        state,
        Map.of(),
        OffsetDateTime.now()
    );

    assertTrue(values.isEmpty());
  }

  @Test
  void executeDependentResolvesMaterializedMasterKeyForCpuUtil() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime util = new ZabbixItemRuntime(
        "util",
        "system.cpu.util[snmp,{#SNMPINDEX}]",
        "CPU utilization",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        null,
        null,
        "system.cpu.idle[ssCpuRawIdle.{#SNMPINDEX}]",
        null,
        null,
        List.of(new ZabbixPreprocessingStep("JAVASCRIPT", List.of("return (100 - value)"), null, null)),
        null,
        true,
        "cpu.discovery[snmp]"
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "linux-by-snmp",
        "SNMP",
        "Linux by SNMP",
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
        Map.of(
            util.key(),
            util,
            "system.cpu.idle[ssCpuRawIdle.{#SNMPINDEX}]",
            new ZabbixItemRuntime(
                "idle",
                "system.cpu.idle[ssCpuRawIdle.{#SNMPINDEX}]",
                "CPU idle",
                "DEPENDENT",
                null,
                60,
                "FLOAT",
                null,
                null,
                "system.cpu.walk",
                null,
                null,
                List.of(),
                null,
                true,
                "cpu.discovery[snmp]"
            )
        ),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    Map<String, String> macros = Map.of("{#SNMPINDEX}", "0");
    MaterializedZabbixItem utilMaterialized = new MaterializedZabbixItem(
        "linux-by-snmp",
        util,
        "system.cpu.util[snmp,0]",
        "system.cpu.util[snmp,0]",
        "0",
        "cpu.discovery[snmp]",
        null,
        macros
    );
    Map<String, ZabbixItemValue> currentCycleValues = new LinkedHashMap<>();
    currentCycleValues.put(
        "system.cpu.idle[ssCpuRawIdle.0]::0",
        new ZabbixItemValue(
            "linux-by-snmp",
            "system.cpu.idle[ssCpuRawIdle.0]",
            "system.cpu.idle[ssCpuRawIdle.0]",
            "0",
            "cpu.discovery[snmp]",
            "idle",
            25.0d,
            "25",
            "%",
            null,
            "ok",
            null
        )
    );

    List<ZabbixItemValue> values = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(utilMaterialized),
        Map.of(),
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, values.size());
    assertEquals(75.0d, values.get(0).numericValue());
  }

  @Test
  void executeDependentChainsCpuUtilAfterIdleInSameCycle() {
    DerivedMonitoringItemExecutor executor = new DerivedMonitoringItemExecutor(
        new MonitoringPreprocessingEngine(),
        mock(ZabbixRuntimeStateService.class)
    );
    ZabbixItemRuntime idle = new ZabbixItemRuntime(
        "idle",
        "system.cpu.idle[ssCpuRawIdle.{#SNMPINDEX}]",
        "CPU idle",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        null,
        null,
        "system.cpu.walk",
        null,
        null,
        List.of(),
        null,
        true,
        "cpu.discovery[snmp]"
    );
    ZabbixItemRuntime util = new ZabbixItemRuntime(
        "util",
        "system.cpu.util[snmp,{#SNMPINDEX}]",
        "CPU utilization",
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        null,
        null,
        "system.cpu.idle[ssCpuRawIdle.{#SNMPINDEX}]",
        null,
        null,
        List.of(new ZabbixPreprocessingStep("JAVASCRIPT", List.of("return (100 - value)"), null, null)),
        null,
        true,
        "cpu.discovery[snmp]"
    );
    ZabbixItemRuntime walk = new ZabbixItemRuntime(
        "walk",
        "system.cpu.walk",
        "SNMP walk system CPUs",
        "SNMP_AGENT",
        "walk[1.3.6.1.2.1.25.3.3.1.1]",
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
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "linux-by-snmp",
        "SNMP",
        "Linux by SNMP",
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
        Map.of(walk.key(), walk, idle.key(), idle, util.key(), util),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
    Map<String, String> macros = Map.of("{#SNMPINDEX}", "0");
    MaterializedZabbixItem idleMaterialized = new MaterializedZabbixItem(
        "linux-by-snmp",
        idle,
        "system.cpu.idle[ssCpuRawIdle.0]",
        "system.cpu.idle[ssCpuRawIdle.0]",
        "0",
        "cpu.discovery[snmp]",
        null,
        macros
    );
    MaterializedZabbixItem utilMaterialized = new MaterializedZabbixItem(
        "linux-by-snmp",
        util,
        "system.cpu.util[snmp,0]",
        "system.cpu.util[snmp,0]",
        "0",
        "cpu.discovery[snmp]",
        null,
        macros
    );
    Map<String, ZabbixItemValue> currentCycleValues = new LinkedHashMap<>();
    currentCycleValues.put(
        "system.cpu.walk::",
        new ZabbixItemValue(
            "linux-by-snmp",
            "system.cpu.walk",
            "system.cpu.walk",
            "",
            null,
            walk.uuid(),
            null,
            "40",
            null,
            null,
            "ok",
            null
        )
    );

    List<ZabbixItemValue> idleValues = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(idleMaterialized),
        Map.of(),
        currentCycleValues,
        OffsetDateTime.now()
    );
    assertEquals(1, idleValues.size());
    assertEquals(40.0d, idleValues.get(0).numericValue());

    List<ZabbixItemValue> utilValues = executor.execute(
        new MonitoredDeviceEntity(),
        template,
        List.of(utilMaterialized),
        Map.of(),
        currentCycleValues,
        OffsetDateTime.now()
    );

    assertEquals(1, utilValues.size());
    assertEquals(60.0d, utilValues.get(0).numericValue());
  }
}
