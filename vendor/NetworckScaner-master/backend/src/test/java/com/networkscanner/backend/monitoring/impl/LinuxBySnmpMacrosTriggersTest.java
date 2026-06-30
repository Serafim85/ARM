package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import com.networkscanner.backend.monitoring.util.ZabbixTemplateMacroSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LinuxBySnmpMacrosTriggersTest {

  private static ResolvedMonitoringTemplate template;
  private static Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances;
  private static List<ZabbixItemValue> baseline;

  @BeforeAll
  static void setUpClass() {
    LinuxBySnmpFixtureSupport.resetCaches();
    template = LinuxBySnmpFixtureSupport.template();
    discoveryInstances = LinuxBySnmpFixtureSupport.discoverAll(LinuxBySnmpFixtureSupport.snmpServiceFromFixtures());
    baseline = LinuxBySnmpFixtureSupport.collectBaselineMetrics();
  }

  @Test
  void templateMacrosIncludeNetIfAndVfsKeys() {
    Map<String, String> macros = template.templateMacros();
    assertNotNull(macros);
    assertFalse(macros.isEmpty());
    assertTrue(macros.keySet().stream().anyMatch(k -> k.contains("NET.IF.IFNAME")));
    assertTrue(macros.keySet().stream().anyMatch(k -> k.contains("VFS.FS.PUSED.MAX.CRIT")));
  }

  @Test
  void materializesHighBandwidthTriggerForEns18() {
    DiscoveryInstanceRuntime ens18 = discoveryInstances.get("net.if.discovery").stream()
        .filter(i -> "ens18".equals(i.macros().get("{#IFNAME}")))
        .findFirst()
        .orElseThrow();

    List<MaterializedZabbixTrigger> materialized = TriggerEvaluationSupport.materializeTriggers(
        template,
        Map.of("net.if.discovery", List.of(ens18))
    );

    MaterializedZabbixTrigger bandwidth = materialized.stream()
        .filter(t -> t.runtime().name() != null && t.runtime().name().contains("High bandwidth"))
        .findFirst()
        .orElseThrow();
    String expression = bandwidth.expression();
    assertFalse(expression.contains("{#SNMPINDEX}"));
    assertFalse(expression.contains("{#IFNAME}"));
    assertTrue(expression.contains("net.if.in[ifHCInOctets.2]"));
    assertTrue(expression.contains("/Linux by SNMP/") || expression.contains("Linux by SNMP"));
  }

  @Test
  void materializesOperDownExpressionForEns18() {
    DiscoveryInstanceRuntime ens18 = discoveryInstances.get("net.if.discovery").stream()
        .filter(i -> "ens18".equals(i.macros().get("{#IFNAME}")))
        .findFirst()
        .orElseThrow();
    String resolved = resolveExpressionMacros(operDownExpression(ens18.macros()), ens18.macros());
    assertTrue(resolved.contains("net.if.status[ifOperStatus.2]"));
    assertFalse(resolved.contains("{#SNMPINDEX}"));
  }

  @Test
  void collectHistoryRequestsForBandwidthTrigger() {
    DiscoveryInstanceRuntime ens18 = discoveryInstances.get("net.if.discovery").stream()
        .filter(i -> "ens18".equals(i.macros().get("{#IFNAME}")))
        .findFirst()
        .orElseThrow();

    List<MaterializedZabbixTrigger> materialized = TriggerEvaluationSupport.materializeTriggers(
        template,
        Map.of("net.if.discovery", List.of(ens18))
    );

    MaterializedZabbixTrigger bandwidth = materialized.stream()
        .filter(t -> t.runtime().name() != null && t.runtime().name().contains("High bandwidth"))
        .findFirst()
        .orElseThrow();

    var requests = TriggerEvaluationSupport.collectHistoryRequestsForMaterialized(
        List.of(bandwidth),
        LinuxBySnmpFixtureSupport.TIMESTAMP
    );
    assertFalse(requests.isEmpty());
    assertTrue(requests.stream().anyMatch(r -> r.metricName().contains("net.if.in")));
  }

  @Test
  void evaluatesSnmpUnavailableScenario() {
    JsonNode scenario = LinuxBySnmpFixtureSupport.triggerScenarios().get("snmp_unavailable");
    ZabbixTriggerRuntime trigger = template.triggers().values().stream()
        .filter(t -> t.expression() != null && t.expression().contains("zabbix[host,snmp,available]"))
        .findFirst()
        .orElseThrow();

    FixtureMetricValueProvider provider = FixtureMetricValueProvider.fromBaselineWithScenario(baseline, scenario);
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        trigger.expression(),
        LinuxBySnmpFixtureSupport.TIMESTAMP,
        provider
    );
    assertNotNull(evaluation);
    assertTrue(evaluation.breached());
  }

  @Test
  void evaluatesEns18OperDownAndRecovery() {
    DiscoveryInstanceRuntime ens18 = discoveryInstances.get("net.if.discovery").stream()
        .filter(i -> "ens18".equals(i.macros().get("{#IFNAME}")))
        .findFirst()
        .orElseThrow();

    String expression = resolveExpressionMacros(operDownExpression(ens18.macros()), ens18.macros());

    JsonNode downScenario = LinuxBySnmpFixtureSupport.triggerScenarios().get("ens18_link_down");
    FixtureMetricValueProvider downProvider =
        FixtureMetricValueProvider.fromBaselineWithScenario(baseline, downScenario);
    TriggerEvaluationSupport.TriggerEvaluation downEval = TriggerEvaluationSupport.evaluateExpression(
        expression,
        LinuxBySnmpFixtureSupport.TIMESTAMP,
        downProvider
    );
    assertNotNull(downEval);
    assertTrue(downEval.breached());

    JsonNode recoveryScenario = LinuxBySnmpFixtureSupport.triggerScenarios().get("ens18_link_down_recovery");
    FixtureMetricValueProvider recoveryProvider =
        FixtureMetricValueProvider.fromBaselineWithScenario(baseline, recoveryScenario);
    TriggerEvaluationSupport.TriggerEvaluation recoveryEval = TriggerEvaluationSupport.evaluateExpression(
        expression,
        LinuxBySnmpFixtureSupport.TIMESTAMP,
        recoveryProvider
    );
    assertNotNull(recoveryEval);
    assertFalse(recoveryEval.breached());
  }

  @Test
  void evaluatesHighBandwidthScenario() {
    DiscoveryInstanceRuntime ens18 = discoveryInstances.get("net.if.discovery").stream()
        .filter(i -> "ens18".equals(i.macros().get("{#IFNAME}")))
        .findFirst()
        .orElseThrow();

    MaterializedZabbixTrigger bandwidth = TriggerEvaluationSupport.materializeTriggers(
        template,
        Map.of("net.if.discovery", List.of(ens18))
    ).stream()
        .filter(t -> t.runtime().name() != null && t.runtime().name().contains("High bandwidth"))
        .findFirst()
        .orElseThrow();

    JsonNode scenario = LinuxBySnmpFixtureSupport.triggerScenarios().get("ens18_high_bandwidth");
    FixtureMetricValueProvider provider = FixtureMetricValueProvider.fromBaselineWithScenario(baseline, scenario);
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        bandwidth.expression(),
        LinuxBySnmpFixtureSupport.TIMESTAMP,
        provider
    );
    assertNotNull(evaluation);
    assertTrue(evaluation.breached());
  }

  @Test
  void skipsEvaluationWhenTemplateMacroUnresolved() {
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        "last(/Linux by SNMP/vfs.fs.pused[1])>{$VFS.FS.PUSED.MAX.CRIT:\"{#FSNAME}\"}",
        LinuxBySnmpFixtureSupport.TIMESTAMP,
        (metricName, window, timestamp) -> List.of(95.0)
    );
    assertNull(evaluation);
  }

  private static String operDownExpression(Map<String, String> lldMacros) {
    String index = lldMacros.get("{#SNMPINDEX}");
    return "last(/Linux by SNMP/net.if.status[ifOperStatus." + index + "])=2";
  }

  private static String resolveExpressionMacros(String expression, Map<String, String> lldMacros) {
    Map<String, String> templateMacros = new LinkedHashMap<>(template.templateMacros());
    templateMacros.put("{$IFCONTROL:\"ens18\"}", "1");
    templateMacros.put("{$IFCONTROL:ens18}", "1");
    String resolved = ZabbixTemplateMacroSupport.applyTemplateMacros(expression, templateMacros);
    if (resolved.contains("{$")) {
      for (Map.Entry<String, String> entry : lldMacros.entrySet()) {
        resolved = resolved.replace(entry.getKey(), entry.getValue());
      }
      resolved = ZabbixTemplateMacroSupport.applyTemplateMacros(resolved, templateMacros);
    }
    return resolved;
  }
}
