package com.networkscanner.backend.monitoring.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.networkscanner.backend.monitoring.dto.ChartSeriesDto;
import com.networkscanner.backend.monitoring.dto.CompactChartPanelDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.DeviceMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.MetricChartPanelDto;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.ValueMapSeriesMeta;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChartCompactMapperTest {

  @Test
  void includesValueMapFieldsInCompactSeries() {
    OffsetDateTime at = OffsetDateTime.parse("2026-04-01T12:00:00Z");
    MetricValueDto point = new MetricValueDto(at, "10.0.0.1", "net.if.status[Gi0/1]", 1d, "", "ifOperStatus", null, null, null);
    MetricChartPanelDto panel = new MetricChartPanelDto(
        "panel-1",
        "Interface status",
        "LINE",
        List.of("net.if.status[Gi0/1]"),
        List.of(),
        List.of(point)
    );
    DeviceMetricsHistoryResponseDto rich = new DeviceMetricsHistoryResponseDto(List.of(panel), 1);
    Map<String, ValueMapSeriesMeta> meta = Map.of(
        "net.if.status[Gi0/1]",
        new ValueMapSeriesMeta("IF-MIB::ifOperStatus", Map.of("1", "up", "2", "down"))
    );

    CompactMetricsHistoryResponseDto compact = ChartCompactMapper.toCompactResponse(rich, meta);

    assertEquals(1, compact.chartPanels().size());
    CompactChartPanelDto compactPanel = compact.chartPanels().get(0);
    assertEquals(1, compactPanel.series().size());
    ChartSeriesDto series = compactPanel.series().get(0);
    assertEquals("IF-MIB::ifOperStatus", series.valueMapName());
    assertEquals("up", series.valueMapMappings().get("1"));
    assertEquals(1d, series.v()[0], 0.0001d);
  }

  @Test
  void omitsValueMapFieldsWhenMetaMissing() {
    OffsetDateTime at = OffsetDateTime.parse("2026-04-01T12:00:00Z");
    MetricValueDto point = new MetricValueDto(at, "10.0.0.1", "system.cpu.util", 42d, "%", "CPU", null, null, null);
    MetricChartPanelDto panel = new MetricChartPanelDto(
        "panel-1",
        "CPU",
        "LINE",
        List.of("system.cpu.util"),
        List.of(),
        List.of(point)
    );
    DeviceMetricsHistoryResponseDto rich = new DeviceMetricsHistoryResponseDto(List.of(panel), 1);

    CompactMetricsHistoryResponseDto compact = ChartCompactMapper.toCompactResponse(rich);

    ChartSeriesDto series = compact.chartPanels().get(0).series().get(0);
    assertNull(series.valueMapName());
    assertNull(series.valueMapMappings());
  }
}
