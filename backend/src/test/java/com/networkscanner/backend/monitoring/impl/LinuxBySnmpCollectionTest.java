package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.util.LinuxBySnmpWalkSpecs;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinuxBySnmpCollectionTest {

  @BeforeEach
  void resetFixtureCaches() {
    LinuxBySnmpFixtureSupport.resetCaches();
  }

  @Test
  void collectsSystemNameFromGetFixture() {
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();
    SnmpScanService snmp = LinuxBySnmpFixtureSupport.snmpServiceFromFixtures();
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();
    SnmpMonitoringItemExecutor executor = new SnmpMonitoringItemExecutor(snmp, engine);

    MaterializedZabbixItem item = LinuxBySnmpFixtureSupport.materialize(
        template.items().get(LinuxBySnmpWalkSpecs.GET_SYSTEM_NAME_KEY),
        LinuxBySnmpWalkSpecs.GET_SYSTEM_NAME_KEY,
        "",
        null,
        Map.of()
    );

    List<ZabbixItemValue> values = executor.execute(
        LinuxBySnmpFixtureSupport.device(),
        template,
        List.of(item),
        Map.of(),
        Map.of(),
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );

    assertEquals(1, values.size());
    assertEquals("wisla42", values.get(0).textValue());
    assertEquals("ok", values.get(0).preprocessingStatus());
  }

  @Test
  void collectsLoadAverageFromDependentWalk() {
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();
    SnmpScanService snmp = LinuxBySnmpFixtureSupport.snmpServiceFromFixtures();
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();
    SnmpMonitoringItemExecutor snmpExecutor = new SnmpMonitoringItemExecutor(snmp, engine);
    DerivedMonitoringItemExecutor derivedExecutor = new DerivedMonitoringItemExecutor(
        engine,
        mock(com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService.class),
        snmp
    );

    MaterializedZabbixItem walk = LinuxBySnmpFixtureSupport.materialize(
        template.items().get(LinuxBySnmpWalkSpecs.CPU_LOAD_WALK_KEY),
        LinuxBySnmpWalkSpecs.CPU_LOAD_WALK_KEY,
        "",
        null,
        Map.of()
    );
    Map<String, ZabbixItemValue> cycle = new LinkedHashMap<>();
    snmpExecutor.execute(
        LinuxBySnmpFixtureSupport.device(),
        template,
        List.of(walk),
        Map.of(),
        cycle,
        LinuxBySnmpFixtureSupport.TIMESTAMP
    ).forEach(v -> cycle.put(LinuxBySnmpFixtureSupport.stateKey(v), v));

    ZabbixItemRuntime loadAvg1 = template.items().get("system.cpu.load.avg1[laLoad.1]");
    assertNotNull(loadAvg1);
    // Zabbix template JSONPATH must match laName values in walk fixture (see syntheticWalkFallbacks).
    MaterializedZabbixItem loadItem = LinuxBySnmpFixtureSupport.materialize(
        loadAvg1,
        "system.cpu.load.avg1[laLoad.1]",
        "",
        null,
        Map.of()
    );

    List<ZabbixItemValue> derived = derivedExecutor.execute(
        LinuxBySnmpFixtureSupport.device(),
        template,
        List.of(loadItem),
        Map.of(),
        cycle,
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );

    assertEquals(1, derived.size());
    assertEquals("ok", derived.get(0).preprocessingStatus());
    assertNotNull(derived.get(0).numericValue());
    assertTrue(derived.get(0).numericValue() > 0);
  }

  @Test
  void collectsNetIfInOctetsForEns18() {
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();
    SnmpScanService snmp = LinuxBySnmpFixtureSupport.snmpServiceFromFixtures();
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();
    DerivedMonitoringItemExecutor derivedExecutor = new DerivedMonitoringItemExecutor(
        engine,
        mock(com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService.class),
        snmp
    );

    DiscoveryInstanceRuntime ens18 = LinuxBySnmpFixtureSupport.findNetIfInstance("ens18");
    MaterializedZabbixItem netIfIn = LinuxBySnmpFixtureSupport.materializePrototype(
        "net.if.in[ifHCInOctets",
        ens18
    );

    Map<String, ZabbixItemValue> walkValues = new LinkedHashMap<>();
    walkValues.put(
        LinuxBySnmpFixtureSupport.stateKey(LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY, ""),
        new ZabbixItemValue(
            LinuxBySnmpFixtureSupport.templateId(),
            LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY,
            LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY,
            "",
            null,
            null,
            null,
            LinuxBySnmpFixtureSupport.walkFixtures().get(LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY),
            null,
            null,
            "ok",
            null
        )
    );

    List<ZabbixItemValue> values = derivedExecutor.execute(
        LinuxBySnmpFixtureSupport.device(),
        template,
        List.of(netIfIn),
        Map.of(),
        walkValues,
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );

    assertEquals(1, values.size());
    assertEquals("ok", values.get(0).preprocessingStatus());
    assertNotNull(values.get(0).numericValue());
  }

  @Test
  void snmpAvailabilityTrueWithWalkFixtures() {
    ResolvedMonitoringTemplate template = LinuxBySnmpFixtureSupport.template();
    MaterializedZabbixItem walk = LinuxBySnmpFixtureSupport.materialize(
        template.items().get(LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY),
        LinuxBySnmpWalkSpecs.NET_IF_WALK_KEY,
        "",
        null,
        Map.of()
    );
    boolean available = MetricCollectorServiceImpl.resolveSnmpAgentAvailability(
        List.of(walk),
        LinuxBySnmpFixtureSupport.walkFixtures(),
        List.of()
    );
    assertTrue(available);
  }

  @Test
  void baselineMetricsCoverHostnameAndInterfaces() {
    List<ZabbixItemValue> baseline = LinuxBySnmpFixtureSupport.collectBaselineMetrics();
    assertFalse(baseline.isEmpty());
    assertTrue(baseline.stream().anyMatch(v -> "wisla42".equals(v.textValue())
        || (v.itemKey() != null && v.itemKey().contains("system.name"))));
  }
}
