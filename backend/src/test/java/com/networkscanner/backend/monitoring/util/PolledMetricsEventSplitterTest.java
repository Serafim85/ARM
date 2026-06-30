package com.networkscanner.backend.monitoring.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.impl.PolledMetricsBatchAssembler;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolledMetricsEventSplitterTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void splitsOversizedEventAndAssemblerRebuildsIt() throws JsonProcessingException {
    PolledMetricsEvent source = event(largeValues(4000));
    List<PolledMetricsEvent> chunks = PolledMetricsEventSplitter.splitForKafka(source, objectMapper, 200_000);
    assertTrue(chunks.size() > 1);
    for (PolledMetricsEvent chunk : chunks) {
      assertTrue(objectMapper.writeValueAsBytes(chunk).length <= 200_000);
      assertTrue(chunk.isMultiPart());
    }

    PolledMetricsBatchAssembler assembler = new PolledMetricsBatchAssembler(new com.networkscanner.backend.config.MonitoringKafkaProperties());
    PolledMetricsEvent merged = null;
    for (PolledMetricsEvent chunk : chunks) {
      merged = assembler.offer(chunk).orElse(null);
    }
    assertEquals(source.values().size(), merged.values().size());
    assertEquals(source.deviceId(), merged.deviceId());
    assertEquals(chunks.get(0).pollBatchId(), merged.messageId());
  }

  private static PolledMetricsEvent event(List<ZabbixItemValue> values) {
    return new PolledMetricsEvent(
        "msg-1",
        "1",
        119L,
        "10.0.0.119",
        "Cisco",
        "SG500X",
        "cisco-ios-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        OffsetDateTime.parse("2026-05-21T14:40:55+03:00"),
        Map.of(),
        values,
        null,
        null,
        null
    );
  }

  private static List<ZabbixItemValue> largeValues(int count) {
    List<ZabbixItemValue> values = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      values.add(new ZabbixItemValue(
          "cisco-ios-by-snmp",
          "ifInOctets[" + i + "]",
          "ifInOctets[" + i + "]",
          String.valueOf(i),
          "net.if.discovery",
          "uuid-" + i,
          (double) i,
          "x".repeat(200),
          "bps",
          null,
          "ok",
          null
      ));
    }
    return values;
  }
}
