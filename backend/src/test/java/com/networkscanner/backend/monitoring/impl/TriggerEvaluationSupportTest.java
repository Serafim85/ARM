package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TriggerEvaluationSupportTest {

  private MonitoringTemplateResolver resolver;
  private UploadedMonitoringTemplateRepository uploadedRepository;

  @BeforeEach
  void setUp() {
    TriggerEvaluationSupport.clearCaches();
    uploadedRepository = mock(UploadedMonitoringTemplateRepository.class);
    AppUserRepository appUserRepository = mock(AppUserRepository.class);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.<UploadedMonitoringTemplateEntity>of());
    MonitoringTemplateObfuscator obfuscator = new MonitoringTemplateObfuscator();
    resolver = new MonitoringTemplateResolverImpl(
        new ObjectMapper(),
        uploadedRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        appUserRepository,
        new MonitoringTemplateArchiveReader(obfuscator),
        obfuscator
    );
    resolver.initialize();
  }

  @Test
  void evaluatesArithmeticAndWindowFunctionsWithSingleEngine() {
    OffsetDateTime timestamp = OffsetDateTime.now();
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        "avg(/Template/cpu,600s)>60 and max(/Template/cpu,600s)<95",
        timestamp,
        (metricName, window, ignored) -> List.of(90.0, 80.0, 70.0)
    );

    assertEquals("cpu", evaluation.metricName());
    assertEquals(80.0, evaluation.actualValue());
    assertEquals(60.0, evaluation.thresholdValue());
    assertTrue(evaluation.breached());
  }

  @Test
  void supportsMinFunction() {
    OffsetDateTime timestamp = OffsetDateTime.now();
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        "min(/Template/cpu,600s)>80",
        timestamp,
        (metricName, window, ignored) -> List.of(90.0, 70.0, 95.0)
    );

    assertEquals("cpu", evaluation.metricName());
    assertEquals(70.0, evaluation.actualValue());
    assertEquals(80.0, evaluation.thresholdValue());
    assertFalse(evaluation.breached());
  }

  @Test
  void collectsHistoryRequestsWithoutDuplicatesForSameMetricWindow() {
    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("cisco-ios-by-snmp");
    List<MetricHistoryRequest> requests = TriggerEvaluationSupport.collectHistoryRequests(
        template,
        java.util.Map.of(),
        OffsetDateTime.parse("2026-04-03T12:00:00Z")
    );

    long windows = requests.stream()
        .filter(request -> "icmppingsec".equals(request.metricName()))
        .count();

    assertEquals(1L, windows);
    assertTrue(requests.stream().anyMatch(request -> "icmppingsec".equals(request.metricName())));
  }

  @Test
  void mapsZabbixPriorityStringsToThresholdLevels() {
    assertEquals(ThresholdLevel.NOT_CLASSIFIED, TriggerEvaluationSupport.mapThresholdLevel(null));
    assertEquals(ThresholdLevel.NOT_CLASSIFIED, TriggerEvaluationSupport.mapThresholdLevel("unknown"));
    assertEquals(ThresholdLevel.INFORMATION, TriggerEvaluationSupport.mapThresholdLevel("INFO"));
    assertEquals(ThresholdLevel.INFORMATION, TriggerEvaluationSupport.mapThresholdLevel("INFORMATION"));
    assertEquals(ThresholdLevel.WARNING, TriggerEvaluationSupport.mapThresholdLevel("WARNING"));
    assertEquals(ThresholdLevel.AVERAGE, TriggerEvaluationSupport.mapThresholdLevel("AVERAGE"));
    assertEquals(ThresholdLevel.HIGH, TriggerEvaluationSupport.mapThresholdLevel("HIGH"));
    assertEquals(ThresholdLevel.DISASTER, TriggerEvaluationSupport.mapThresholdLevel("DISASTER"));
  }

  @Test
  void derivesDeviceHealthStatusFromOpenThresholdLevels() {
    assertEquals(DeviceHealthStatus.NORM, TriggerEvaluationSupport.deriveHealthStatus(List.of()));
    assertEquals(DeviceHealthStatus.WARN, TriggerEvaluationSupport.deriveHealthStatus(List.of(ThresholdLevel.WARNING)));
    assertEquals(
        DeviceHealthStatus.CRITICAL,
        TriggerEvaluationSupport.deriveHealthStatus(List.of(ThresholdLevel.WARNING, ThresholdLevel.HIGH))
    );
    assertFalse(TriggerEvaluationSupport.evaluateExpression(
        "last(/Template/cpu)>90 or last(/Template/cpu)<10",
        OffsetDateTime.now(),
        (metricName, window, ignored) -> List.of(50.0)
    ).breached());
  }

  @Test
  void cachesCompiledExpressionBetweenEvaluations() {
    AtomicInteger invocations = new AtomicInteger();
    OffsetDateTime timestamp = OffsetDateTime.now();

    TriggerEvaluationSupport.evaluateExpression(
        "avg(/Template/cpu,600s)>60 and max(/Template/cpu,600s)<95",
        timestamp,
        (metricName, window, ignored) -> {
          invocations.incrementAndGet();
          return List.of(90.0, 80.0, 70.0);
        }
    );
    TriggerEvaluationSupport.evaluateExpression(
        "avg(/Template/cpu,600s)>60 and max(/Template/cpu,600s)<95",
        timestamp.plusSeconds(1),
        (metricName, window, ignored) -> {
          invocations.incrementAndGet();
          return List.of(90.0, 80.0, 70.0);
        }
    );

    assertEquals(1, TriggerEvaluationSupport.cachedExpressionCount());
    assertEquals(4, invocations.get());
  }

  @Test
  void parsesMetricKeysWithCommasInsideBrackets() {
    OffsetDateTime timestamp = OffsetDateTime.now();
    AtomicInteger calls = new AtomicInteger();
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        "max(/Linux by SNMP/zabbix[host,snmp,available],5m)=0",
        timestamp,
        (metricName, window, ignored) -> {
          calls.incrementAndGet();
          if ("zabbix[host,snmp,available]".equals(metricName) && "5m".equals(window)) {
            return List.of(1.0);
          }
          return List.of();
        }
    );

    assertEquals("zabbix[host,snmp,available]", evaluation.metricName());
    assertEquals(1, calls.get());
    assertFalse(evaluation.breached());
  }

  @Test
  void evaluatesCalculatedExpressionWithHostlessMetricReferences() {
    double value = TriggerEvaluationSupport.evaluateNumericExpression(
        "last(//system.swap.free[memAvailSwap.0])/last(//system.swap.total[memTotalSwap.0])*100",
        OffsetDateTime.now(),
        (metricName, window, ignored) -> {
          if ("system.swap.free[memAvailSwap.0]".equals(metricName)) {
            return List.of(2_147_479_552d);
          }
          if ("system.swap.total[memTotalSwap.0]".equals(metricName)) {
            return List.of(8_589_934_592d);
          }
          return List.of();
        }
    );

    assertEquals(25.0d, value, 0.001d);
  }

  @Test
  void skipsEvaluationForUnresolvedTemplateMacro() {
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        "last(/Template/temp.sensor[1])>{$TEMP_WARN}",
        OffsetDateTime.now(),
        (metricName, window, ignored) -> List.of(55.0)
    );

    assertNull(evaluation);
  }

  @Test
  void evaluatesTemperatureExpressionWithResolvedThresholdMacro() {
    TriggerEvaluationSupport.TriggerEvaluation evaluation = TriggerEvaluationSupport.evaluateExpression(
        "last(/Template/temp.sensor[1])>70",
        OffsetDateTime.now(),
        (metricName, window, ignored) -> List.of(68.0)
    );

    assertNotNull(evaluation);
    assertEquals("temp.sensor[1]", evaluation.metricName());
    assertEquals(68.0, evaluation.actualValue());
    assertEquals(70.0, evaluation.thresholdValue());
    assertFalse(evaluation.breached());
  }

  @Test
  void materializesLldTriggerMacrosBeforeThresholdEvaluation() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-temp-lld-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.05.21"
        defaultTemplateId: uploaded-temp-lld-template
        templates:
          - id: uploaded-temp-lld-template
            file: uploaded-temp-lld-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Uploaded Temp LLD Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Uploaded Temp LLD Template",
                "name": "Uploaded Temp LLD Template",
                "macros": [
                  {
                    "macro": "{$TEMP_WARN}",
                    "value": "70"
                  }
                ],
                "discovery_rules": [
                  {
                    "uuid": "rule-1",
                    "name": "Temperature sensors",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "discovery[{#SNMPINDEX},1.3.6.1.4.1.9.9.13.1.3.1.2]",
                    "key": "temp.discovery",
                    "trigger_prototypes": [
                      {
                        "uuid": "trigger-1",
                        "name": "Temperature high {#SNMPINDEX}",
                        "expression": "last(/Uploaded Temp LLD Template/temp.sensor[{#SNMPINDEX}])>{$TEMP_WARN}",
                        "priority": "WARNING"
                      }
                    ],
                    "item_prototypes": [
                      {
                        "uuid": "item-1",
                        "name": "Temperature {#SNMPINDEX}",
                        "type": "SNMP_AGENT",
                        "snmp_oid": "1.3.6.1.4.1.9.9.13.1.3.1.3.{#SNMPINDEX}",
                        "key": "temp.sensor[{#SNMPINDEX}]",
                        "delay": "60"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));
    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-temp-lld-template");
    List<MaterializedZabbixTrigger> materialized = TriggerEvaluationSupport.materializeTriggers(
        template,
        Map.of("temp.discovery", List.of(new DiscoveryInstanceRuntime(
            "temp.discovery",
            "sensor-3",
            Map.of("{#SNMPINDEX}", "3"),
            OffsetDateTime.now(),
            OffsetDateTime.now().plusMinutes(5)
        )))
    );

    assertEquals(1, materialized.size());
    String expression = materialized.get(0).expression();
    assertTrue(expression.contains("temp.sensor[3]"));
    assertTrue(expression.contains(">70"));
    assertFalse(expression.contains("{#SNMPINDEX}"));
    assertFalse(expression.contains("{$TEMP_WARN}"));
  }

  @Test
  void resolvesContextualTemplateMacroWithBaseFallback() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-context-macro-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.05.22"
        defaultTemplateId: uploaded-context-macro-template
        templates:
          - id: uploaded-context-macro-template
            file: uploaded-context-macro-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Uploaded Context Macro Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Uploaded Context Macro Template",
                "name": "Uploaded Context Macro Template",
                "macros": [
                  {
                    "macro": "{$IF.UTIL.MAX}",
                    "value": "80"
                  }
                ],
                "discovery_rules": [
                  {
                    "uuid": "rule-ctx-1",
                    "name": "Interfaces",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "discovery[{#SNMPINDEX},1.3.6.1.2.1.2.2.1.1]",
                    "key": "if.discovery",
                    "trigger_prototypes": [
                      {
                        "uuid": "trigger-ctx-1",
                        "name": "Interface util high {#IFNAME}",
                        "expression": "avg(/Uploaded Context Macro Template/net.if.in[ifInOctets.{#SNMPINDEX}],15m)>{$IF.UTIL.MAX:\\\"{#IFNAME}\\\"}",
                        "priority": "WARNING"
                      }
                    ],
                    "item_prototypes": [
                      {
                        "uuid": "item-ctx-1",
                        "name": "If in {#SNMPINDEX}",
                        "type": "SNMP_AGENT",
                        "snmp_oid": "1.3.6.1.2.1.2.2.1.10.{#SNMPINDEX}",
                        "key": "net.if.in[ifInOctets.{#SNMPINDEX}]",
                        "delay": "60"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));
    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-context-macro-template");
    assertNotNull(template.discoveryRules().get("if.discovery"));
    String expression = template.discoveryRules().get("if.discovery").triggerPrototypes().get(0).expression();
    assertTrue(expression.contains(">80"));
    assertFalse(expression.contains("{$IF.UTIL.MAX"));
  }

  @Test
  void resolvesVendorTriggerMacrosFromDefaultModuleCatalog() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-mellanox-macro-gap-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.05.22"
        defaultTemplateId: uploaded-mellanox-macro-gap-template
        templates:
          - id: uploaded-mellanox-macro-gap-template
            file: uploaded-mellanox-macro-gap-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Mellanox by SNMP
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Mellanox by SNMP",
                "name": "Mellanox by SNMP",
                "macros": [
                  {
                    "macro": "{$SNMP.TIMEOUT}",
                    "value": "5m"
                  }
                ],
                "discovery_rules": [
                  {
                    "uuid": "rule-if-1",
                    "name": "Network interfaces",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "discovery[{#SNMPINDEX},1.3.6.1.2.1.2.2.1.2]",
                    "key": "net.if.discovery",
                    "trigger_prototypes": [
                      {
                        "uuid": "trigger-if-util",
                        "name": "High interface utilization",
                        "expression": "avg(/Mellanox by SNMP/net.if.in[ifHCInOctets.{#SNMPINDEX}],15m)>{$IF.UTIL.MAX:\\\"{#IFNAME}\\\"}",
                        "priority": "WARNING"
                      },
                      {
                        "uuid": "trigger-fs-pused",
                        "name": "FS pused critical",
                        "expression": "last(/Mellanox by SNMP/vfs.fs.pused[{#FSNAME}])>{$VFS.FS.PUSED.MAX.CRIT:\\\"{#FSNAME}\\\"}",
                        "priority": "HIGH"
                      }
                    ],
                    "item_prototypes": [
                      {
                        "uuid": "item-if-1",
                        "name": "If in",
                        "type": "SNMP_AGENT",
                        "snmp_oid": "1.3.6.1.2.1.31.1.1.1.6.{#SNMPINDEX}",
                        "key": "net.if.in[ifHCInOctets.{#SNMPINDEX}]",
                        "delay": "60"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));
    resolver.initialize();

    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("uploaded-mellanox-macro-gap-template");
    var rule = template.discoveryRules().get("net.if.discovery");
    assertNotNull(rule);
    assertEquals(2, rule.triggerPrototypes().size());

    String utilExpression = rule.triggerPrototypes().get(0).expression();
    assertTrue(utilExpression.contains(">90"), utilExpression);
    assertFalse(utilExpression.contains("{$IF.UTIL.MAX"));

    String fsExpression = rule.triggerPrototypes().get(1).expression();
    assertTrue(fsExpression.contains(">90"), fsExpression);
    assertFalse(fsExpression.contains("{$VFS.FS.PUSED.MAX.CRIT}"));
  }

  @Test
  void evaluatesNodataAndTimeleftFunctions() {
    TriggerEvaluationSupport.TriggerEvaluation nodata = TriggerEvaluationSupport.evaluateExpression(
        "nodata(/Template/cpu,5m)=1",
        OffsetDateTime.now(),
        (metricName, window, ignored) -> List.of()
    );
    assertNotNull(nodata);
    assertTrue(nodata.breached());

    TriggerEvaluationSupport.TriggerEvaluation timeleft = TriggerEvaluationSupport.evaluateExpression(
        "timeleft(/Template/cpu,1h,100)<7200",
        OffsetDateTime.now(),
        (metricName, window, ignored) -> List.of(80.0, 20.0)
    );
    assertNotNull(timeleft);
    assertTrue(timeleft.breached());
  }

  @Test
  void parsesCompoundTimeWindows() {
    assertEquals(300L, TriggerEvaluationSupport.parseWindowSeconds("5m"));
    assertEquals(3600L, TriggerEvaluationSupport.parseWindowSeconds("1h"));
    assertEquals(90061L, TriggerEvaluationSupport.parseWindowSeconds("1d 1h 1m 1s"));
    assertEquals(120L, TriggerEvaluationSupport.parseWindowSeconds("120"));
  }

  @Test
  void deduplicateTriggersByMetricInstanceKeepsHighestSeverity() {
    List<MaterializedZabbixTrigger> triggers = List.of(
        materializedTrigger("icmp-warning", "WARNING", "last(/Template/icmppingsec)>1"),
        materializedTrigger("icmp-high", "HIGH", "last(/Template/icmppingsec)>1"),
        materializedTrigger("icmp-average", "AVERAGE", "last(/Template/icmppingsec)>1")
    );

    List<MaterializedZabbixTrigger> deduped = TriggerEvaluationSupport.deduplicateTriggersByMetricInstance(triggers);

    assertEquals(1, deduped.size());
    assertEquals("icmp-high", deduped.get(0).runtime().uuid());
  }

  @Test
  void extractChartThresholdsSkipsSpeedGuardAndKeepsDynamicFormula() {
    OffsetDateTime timestamp = OffsetDateTime.parse("2026-05-25T12:00:00Z");
    TriggerEvaluationSupport.MetricWindowValueProvider provider =
        (metricName, window, ignored) -> {
          if ("net.if.speed[ifHighSpeed.2]".equals(metricName)) {
            return List.of(1000.0);
          }
          if ("net.if.in[ifHCInOctets.2]".equals(metricName)) {
            return List.of(9.0E9);
          }
          return List.of();
        };
    String expression =
        "last(/Linux by SNMP/net.if.speed[ifHighSpeed.2])>0"
            + " and last(/Linux by SNMP/net.if.in[ifHCInOctets.2])>0.9*last(/Linux by SNMP/net.if.speed[ifHighSpeed.2])";

    List<TriggerEvaluationSupport.ChartThresholdComparison> comparisons =
        TriggerEvaluationSupport.extractChartThresholdComparisons(expression, timestamp, provider);

    assertEquals(1, comparisons.size());
    TriggerEvaluationSupport.ChartThresholdComparison comparison = comparisons.get(0);
    assertEquals("net.if.in[ifHCInOctets.2]", comparison.metricName());
    assertEquals(">", comparison.operator());
    assertTrue(comparison.dynamic());
    assertEquals(900.0d, comparison.snapshotThresholdValue(), 0.001d);
  }

  private static MaterializedZabbixTrigger materializedTrigger(
      String uuid,
      String priority,
      String expression
  ) {
    return new MaterializedZabbixTrigger(
        new ZabbixTriggerRuntime(
            uuid,
            "ICMP response time",
            expression,
            "EXPRESSION",
            null,
            List.of(),
            List.of(),
            false,
            priority,
            false,
            null
        ),
        uuid,
        "",
        expression,
        null,
        Set.of(),
        Map.of()
    );
  }
}
