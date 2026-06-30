package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.MetricChartThresholdDto;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateCoverageReportDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricChartThresholdBuilderTest {

  private ZabbixRuntimeStateService runtimeStateService;
  private MetricChartThresholdBuilder builder;
  private OffsetDateTime timestamp;

  @BeforeEach
  void setUp() {
    TriggerEvaluationSupport.clearCaches();
    runtimeStateService = mock(ZabbixRuntimeStateService.class);
    builder = new MetricChartThresholdBuilder(runtimeStateService, new UnitScalingService());
    timestamp = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    when(runtimeStateService.loadItemStateList(any())).thenReturn(List.of());
    when(runtimeStateService.loadMetricHistoryBatch(any(), any())).thenReturn(Map.of());
    when(runtimeStateService.loadRecentNumericValues(any(), eq("cpu.util"), any(), any(), any()))
        .thenReturn(List.of(85.0, 80.0, 75.0));
  }

  @Test
  void buildsThresholdFromMaterializedTrigger() {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(1L);
    device.setIp("10.0.0.1");

    ZabbixTriggerRuntime runtime = new ZabbixTriggerRuntime(
        "trigger-1",
        "High CPU",
        "avg(/Template/cpu.util,600s)>60",
        null,
        null,
        List.of(),
        List.of(),
        false,
        "WARNING",
        false,
        null
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "snmp",
        "Template",
        "",
        null,
        "Linux",
        ".*",
        1,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 1000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of("trigger-1", runtime),
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );

    List<MetricChartThresholdDto> thresholds = builder.build(
        device,
        template,
        Map.of(),
        timestamp,
        Map.of("cpu.util", "%"),
        Map.of()
    );

    assertFalse(thresholds.isEmpty());
    MetricChartThresholdDto threshold = thresholds.get(0);
    assertEquals("cpu.util", threshold.metricName());
    assertEquals(60.0, threshold.thresholdValue());
    assertEquals("WARNING", threshold.thresholdLevel());
    assertEquals(">", threshold.operator());
    assertEquals("High CPU", threshold.triggerName());
  }

  @Test
  void indexesThresholdsByMetricInstance() {
    MetricChartThresholdDto first = new MetricChartThresholdDto(
        "cpu.util", "", "CPU warn", "t1", "WARNING", 60.0, 60.0, ">", false, null, null, null
    );
    MetricChartThresholdDto second = new MetricChartThresholdDto(
        "cpu.util", "eth0", "CPU crit", "t2", "HIGH", 90.0, 90.0, ">", false, null, null, null
    );

    Map<String, List<MetricChartThresholdDto>> indexed = builder.indexByMetricInstance(List.of(first, second));

    assertEquals(1, indexed.get("cpu.util:").size());
    assertEquals(1, indexed.get("cpu.util:eth0").size());
  }

  @Test
  void filtersThresholdsForPanelMetrics() {
    MetricChartThresholdDto cpu = new MetricChartThresholdDto(
        "cpu.util", "", "CPU", "t1", "WARNING", 60.0, 60.0, ">", false, null, null, null
    );
    MetricChartThresholdDto mem = new MetricChartThresholdDto(
        "vm.memory.util", "", "RAM", "t2", "HIGH", 90.0, 90.0, ">", false, null, null, null
    );

    List<MetricChartThresholdDto> panelThresholds = MetricChartThresholdBuilder.forPanel(
        List.of(cpu, mem),
        java.util.Set.of("cpu.util")
    );

    assertEquals(1, panelThresholds.size());
    assertEquals("cpu.util", panelThresholds.get(0).metricName());
  }

  @Test
  void buildsDynamicThresholdSeriesForChartHistory() {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(1L);
    device.setIp("10.0.0.42");

    OffsetDateTime t1 = OffsetDateTime.parse("2026-05-25T10:00:00Z");
    OffsetDateTime t2 = OffsetDateTime.parse("2026-05-25T11:00:00Z");
    when(runtimeStateService.loadMetricHistoryBatch(any(), any())).thenAnswer(invocation -> {
      List<MetricHistoryRequest> requests = invocation.getArgument(1);
      Map<MetricHistoryRequest, List<MetricHistoryPoint>> loaded = new java.util.LinkedHashMap<>();
      for (MetricHistoryRequest request : requests) {
        if ("net.if.speed[ifHighSpeed.2]".equals(request.metricName())) {
          loaded.put(request, List.of(
              new MetricHistoryPoint(t1, 1000.0),
              new MetricHistoryPoint(t2, 1000.0)
          ));
        }
      }
      return loaded;
    });

    ZabbixTriggerRuntime runtime = new ZabbixTriggerRuntime(
        "trigger-bw",
        "High bandwidth",
        "last(/Linux by SNMP/net.if.speed[ifHighSpeed.2])>0"
            + " and last(/Linux by SNMP/net.if.in[ifHCInOctets.2])>0.9*last(/Linux by SNMP/net.if.speed[ifHighSpeed.2])",
        null,
        null,
        List.of(),
        List.of(),
        false,
        "WARNING",
        false,
        null
    );
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "snmp",
        "Template",
        "",
        null,
        "Linux",
        ".*",
        1,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 1000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of("trigger-bw", runtime),
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );

    MetricValueDto p1 = new MetricValueDto(
        t1, "10.0.0.42", "net.if.in[ifHCInOctets.2]", 1.0E9, "bps", null, null, null, null
    );
    MetricValueDto p2 = new MetricValueDto(
        t2, "10.0.0.42", "net.if.in[ifHCInOctets.2]", 2.0E9, "bps", null, null, null, null
    );
    Map<String, List<MetricValueDto>> pointsByMetric = Map.of(
        "net.if.in[ifHCInOctets.2]", List.of(p1, p2)
    );

    List<MetricChartThresholdDto> thresholds = builder.build(
        device,
        template,
        Map.of(),
        t2,
        Map.of("net.if.in[ifHCInOctets.2]", "bps"),
        Map.of(),
        new MetricChartThresholdBuilder.ChartThresholdBuildContext(t1, t2, pointsByMetric)
    );

    assertEquals(1, thresholds.size());
    MetricChartThresholdDto threshold = thresholds.get(0);
    assertTrue(threshold.dynamic());
    assertEquals(2, threshold.seriesT().size());
    assertEquals(900.0d, threshold.seriesV().get(0), 0.001d);
    assertEquals(900.0d, threshold.seriesV().get(1), 0.001d);
  }

  @Test
  void evaluateNumericComparisonsExtractsBothSidesOfAndExpression() {
    List<TriggerEvaluationSupport.TriggerEvaluation> comparisons = TriggerEvaluationSupport.evaluateNumericComparisons(
        "avg(/Template/cpu.util,600s)>60 and max(/Template/cpu.util,600s)<95",
        timestamp,
        (metricName, window, ignored) -> List.of(90.0, 80.0, 70.0)
    );

    assertEquals(2, comparisons.size());
    assertTrue(comparisons.stream().anyMatch(e -> e.thresholdValue() == 60.0));
    assertTrue(comparisons.stream().anyMatch(e -> e.thresholdValue() == 95.0));
  }
}
