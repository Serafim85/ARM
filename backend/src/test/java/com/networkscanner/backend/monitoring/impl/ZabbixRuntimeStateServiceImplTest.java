package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

class ZabbixRuntimeStateServiceImplTest {

  @Test
  void groupsBatchHistoryQueriesByWindowAndLimit() throws Exception {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    ZabbixRuntimeStateServiceImpl service = new ZabbixRuntimeStateServiceImpl(jdbcTemplate, new ObjectMapper());
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setIp("10.10.10.5");

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime cpuRecordedAt = now.minusSeconds(10);
    OffsetDateTime ramRecordedAt = now.minusSeconds(20);
    OffsetDateTime tempRecordedAt = now.minusSeconds(30);

    doAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      RowCallbackHandler handler = invocation.getArgument(1);

      if (sql.contains("ROW_NUMBER()")) {
        ResultSet cpu = mock(ResultSet.class);
        org.mockito.Mockito.when(cpu.getString("metric_name")).thenReturn("cpu");
        org.mockito.Mockito.when(cpu.getObject("recorded_at", OffsetDateTime.class)).thenReturn(cpuRecordedAt);
        org.mockito.Mockito.when(cpu.getDouble("metric_value")).thenReturn(80.0);
        handler.processRow(cpu);

        ResultSet ram = mock(ResultSet.class);
        org.mockito.Mockito.when(ram.getString("metric_name")).thenReturn("ram");
        org.mockito.Mockito.when(ram.getObject("recorded_at", OffsetDateTime.class)).thenReturn(ramRecordedAt);
        org.mockito.Mockito.when(ram.getDouble("metric_value")).thenReturn(60.0);
        handler.processRow(ram);
      } else {
        ResultSet temp = mock(ResultSet.class);
        org.mockito.Mockito.when(temp.getString("metric_name")).thenReturn("temp");
        org.mockito.Mockito.when(temp.getObject("recorded_at", OffsetDateTime.class)).thenReturn(tempRecordedAt);
        org.mockito.Mockito.when(temp.getDouble("metric_value")).thenReturn(42.0);
        handler.processRow(temp);
      }
      return null;
    }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

    Map<MetricHistoryRequest, List<MetricHistoryPoint>> result = service.loadMetricHistoryBatch(
        device,
        List.of(
            new MetricHistoryRequest("cpu", null, 1),
            new MetricHistoryRequest("ram", null, 1),
            new MetricHistoryRequest("temp", now.minusSeconds(600), null)
        )
    );

    assertEquals(3, result.size());
    assertEquals(80.0, result.get(new MetricHistoryRequest("cpu", null, 1)).get(0).value());
    assertEquals(60.0, result.get(new MetricHistoryRequest("ram", null, 1)).get(0).value());
    assertEquals(42.0, result.get(new MetricHistoryRequest("temp", now.minusSeconds(600), null)).get(0).value());
    verify(jdbcTemplate, times(2)).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
  }
}
