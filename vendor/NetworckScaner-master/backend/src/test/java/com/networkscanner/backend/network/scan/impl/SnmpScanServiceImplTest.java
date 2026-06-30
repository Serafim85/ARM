package com.networkscanner.backend.network.scan.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryConditionRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryFilterRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnmpScanServiceImplTest {

  @Test
  void parsesMultiColumnDiscoveryExpression() {
    List<SnmpScanServiceImpl.DiscoveryColumnSpec> columns = SnmpScanServiceImpl.parseDiscoveryColumns(
        "discovery[{#IFOPERSTATUS},1.3.6.1.2.1.2.2.1.8,{#IFADMINSTATUS},1.3.6.1.2.1.2.2.1.7,{#IFNAME},1.3.6.1.2.1.31.1.1.1.1]"
    );

    assertEquals(3, columns.size());
    assertEquals("{#IFOPERSTATUS}", columns.get(0).macro());
    assertEquals("1.3.6.1.2.1.2.2.1.8", columns.get(0).oid());
    assertEquals("{#IFNAME}", columns.get(2).macro());
  }

  @Test
  void rejectsInvalidDiscoveryExpression() {
    assertTrue(SnmpScanServiceImpl.parseDiscoveryColumns("not-discovery").isEmpty());
    assertTrue(SnmpScanServiceImpl.parseDiscoveryColumns("discovery[{#A},1.2.3,{#B}]").isEmpty());
    assertFalse(SnmpScanServiceImpl.isDiscreteSnmpOid("1.3.6.1.2.1.2.2.1.8,{#IFADMINSTATUS}"));
  }

  @Test
  void appliesAndDiscoveryFilterWithRegexOperators() {
    ZabbixDiscoveryFilterRecord filter = new ZabbixDiscoveryFilterRecord(
        "AND",
        List.of(
            new ZabbixDiscoveryConditionRecord("{#IFADMINSTATUS}", "^2$", "NOT_MATCHES_REGEX"),
            new ZabbixDiscoveryConditionRecord("{#IFOPERSTATUS}", "^6$", "NOT_MATCHES_REGEX"),
            new ZabbixDiscoveryConditionRecord("{#IFNAME}", "^Gi.*$", "MATCHES_REGEX")
        )
    );

    assertTrue(SnmpScanServiceImpl.matchesDiscoveryFilter(filter, Map.of(
        "{#IFADMINSTATUS}", "1",
        "{#IFOPERSTATUS}", "1",
        "{#IFNAME}", "Gi1/0/1"
    )));
    assertFalse(SnmpScanServiceImpl.matchesDiscoveryFilter(filter, Map.of(
        "{#IFADMINSTATUS}", "2",
        "{#IFOPERSTATUS}", "1",
        "{#IFNAME}", "Gi1/0/1"
    )));
    assertFalse(SnmpScanServiceImpl.matchesDiscoveryFilter(filter, Map.of(
        "{#IFADMINSTATUS}", "1",
        "{#IFOPERSTATUS}", "6",
        "{#IFNAME}", "Gi1/0/1"
    )));
  }

  @Test
  void sanitizeSnmpPresentationStripsErrorTokens() {
    assertNull(SnmpScanServiceImpl.sanitizeSnmpPresentation(null));
    assertNull(SnmpScanServiceImpl.sanitizeSnmpPresentation("noSuchObject"));
    assertNull(SnmpScanServiceImpl.sanitizeSnmpPresentation(" noSuchInstance "));
    assertEquals("abc", SnmpScanServiceImpl.sanitizeSnmpPresentation(" abc "));
  }

  @Test
  void resolvesTelemetryFromZabbixStyleKeys() {
    Map<String, Double> values = Map.of(
        "SYSTEM.CPU.LOAD.AVG15[LALOAD.3]", 27.0,
        "VM.MEMORY.FREE[MEMAVAILREAL.0]", 2048.0,
        "VM.MEMORY.TOTAL[MEMTOTALREAL.0]", 8192.0
    );
    Map<String, ZabbixItemRuntime> definitions = Map.of(
        "SYSTEM.CPU.LOAD.AVG15[LALOAD.3]", runtime("SYSTEM.CPU.LOAD.AVG15[LALOAD.3]", "CPU Load avg15", ""),
        "VM.MEMORY.FREE[MEMAVAILREAL.0]", runtime("VM.MEMORY.FREE[MEMAVAILREAL.0]", "Mem free", "B"),
        "VM.MEMORY.TOTAL[MEMTOTALREAL.0]", runtime("VM.MEMORY.TOTAL[MEMTOTALREAL.0]", "Mem total", "B")
    );

    SnmpScanServiceImpl.TelemetrySnapshot snapshot = SnmpScanServiceImpl.resolveTelemetrySnapshot(values, definitions);

    assertEquals(27.0, snapshot.cpuCurrent(), 1e-9);
    assertEquals(27.0, snapshot.cpuAverage(), 1e-9);
    assertEquals(27.0, snapshot.cpuPeak(), 1e-9);
    assertEquals(75, snapshot.ramUsedPercent());
    assertNull(snapshot.romUsedPercent());
  }

  @Test
  void prefersExplicitPercentMetricOverDerivedMemoryPercent() {
    Map<String, Double> values = Map.of(
        "VM.MEMORY.USED.PERCENT[0]", 64.0,
        "VM.MEMORY.FREE[MEMAVAILREAL.0]", 200.0,
        "VM.MEMORY.TOTAL[MEMTOTALREAL.0]", 1000.0
    );
    Map<String, ZabbixItemRuntime> definitions = Map.of(
        "VM.MEMORY.USED.PERCENT[0]", runtime("VM.MEMORY.USED.PERCENT[0]", "Memory used percent", "%"),
        "VM.MEMORY.FREE[MEMAVAILREAL.0]", runtime("VM.MEMORY.FREE[MEMAVAILREAL.0]", "Mem free", "B"),
        "VM.MEMORY.TOTAL[MEMTOTALREAL.0]", runtime("VM.MEMORY.TOTAL[MEMTOTALREAL.0]", "Mem total", "B")
    );

    SnmpScanServiceImpl.TelemetrySnapshot snapshot = SnmpScanServiceImpl.resolveTelemetrySnapshot(values, definitions);
    assertEquals(64, snapshot.ramUsedPercent());
  }

  @Test
  void unresolvedUserMacroInRegexDoesNotCrashFilterEvaluation() {
    ZabbixDiscoveryFilterRecord filter = new ZabbixDiscoveryFilterRecord(
        "AND",
        List.of(new ZabbixDiscoveryConditionRecord("{#IFNAME}", "{$NET.IF.IFNAME.MATCHES}", "MATCHES_REGEX"))
    );

    assertTrue(SnmpScanServiceImpl.matchesDiscoveryFilter(filter, Map.of("{#IFNAME}", "Gi1/0/1")));
  }

  @Test
  void prefersLinuxLoadAverageKeysOverOtherCpuLikeMetrics() {
    Map<String, Double> values = Map.of(
        "system.cpu.intr[ssRawInterrupts.0]", 118_006_394.0,
        "system.cpu.load.avg1[laLoad.1]", 0.42,
        "system.cpu.load.avg5[laLoad.2]", 0.55,
        "system.cpu.load.avg15[laLoad.3]", 0.61
    );
    Map<String, ZabbixItemRuntime> definitions = Map.of(
        "system.cpu.intr[ssRawInterrupts.0]", runtime("system.cpu.intr[ssRawInterrupts.0]", "Interrupts", ""),
        "system.cpu.load.avg1[laLoad.1]", runtime("system.cpu.load.avg1[laLoad.1]", "Load 1m", ""),
        "system.cpu.load.avg5[laLoad.2]", runtime("system.cpu.load.avg5[laLoad.2]", "Load 5m", ""),
        "system.cpu.load.avg15[laLoad.3]", runtime("system.cpu.load.avg15[laLoad.3]", "Load 15m", "")
    );

    SnmpScanServiceImpl.TelemetrySnapshot snapshot = SnmpScanServiceImpl.resolveTelemetrySnapshot(values, definitions);

    assertEquals(0.42, snapshot.cpuCurrent(), 1e-9);
    assertEquals(0.55, snapshot.cpuAverage(), 1e-9);
    assertEquals(0.61, snapshot.cpuPeak(), 1e-9);
    assertEquals("Load 1m", snapshot.cpuCurrentItemName());
    assertEquals("Load 5m", snapshot.cpuAverageItemName());
    assertEquals("Load 15m", snapshot.cpuPeakItemName());
  }

  private static ZabbixItemRuntime runtime(String key, String name, String units) {
    return new ZabbixItemRuntime(
        "uuid-" + key,
        key,
        name,
        "SNMP_AGENT",
        "1.3.6.1.2.1.1.3.0",
        60,
        "FLOAT",
        units,
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );
  }
}
