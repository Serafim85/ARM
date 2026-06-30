package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SnmpMonitoringItemExecutor implements MonitoringItemExecutor {

  private static final Logger log = LoggerFactory.getLogger(SnmpMonitoringItemExecutor.class);

  private final SnmpScanService snmpScanService;
  private final MonitoringPreprocessingEngine preprocessingEngine;

  public SnmpMonitoringItemExecutor(
      SnmpScanService snmpScanService,
      MonitoringPreprocessingEngine preprocessingEngine
  ) {
    this.snmpScanService = snmpScanService;
    this.preprocessingEngine = preprocessingEngine;
  }

  @Override
  public boolean supports(MaterializedZabbixItem item) {
    return item.runtime().isSnmpBased() && !item.runtime().isZabbixIcmpSimpleItem();
  }

  @Override
  public List<ZabbixItemValue> execute(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      List<MaterializedZabbixItem> items,
      Map<String, ItemStateSnapshot> state,
      Map<String, ZabbixItemValue> currentCycleValues,
      OffsetDateTime timestamp
  ) {
    return executeBatch(device, template, items, state, timestamp).values();
  }

  SnmpPollBatch executeBatch(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      List<MaterializedZabbixItem> items,
      Map<String, ItemStateSnapshot> state,
      OffsetDateTime timestamp
  ) {
    if (items.isEmpty()) {
      return new SnmpPollBatch(List.of(), Map.of());
    }
    Map<String, String> requests = new LinkedHashMap<>();
    for (MaterializedZabbixItem item : items) {
      String oid = item.snmpOid();
      if (oid == null || oid.isBlank() || oid.contains("{#")) {
        log.debug("Skip polling item {}: OID empty or macros not resolved", item.key());
        continue;
      }
      requests.put(item.metricName(), oid);
    }
    if (requests.isEmpty()) {
      return new SnmpPollBatch(List.of(), Map.of());
    }
    Map<String, String> rawValues = snmpScanService.readRawOids(device.getIp(), template, requests);
    if (rawValues == null) {
      rawValues = Map.of();
    }
    List<ZabbixItemValue> results = new ArrayList<>();
    for (MaterializedZabbixItem item : items) {
      String rawValue = rawValues.get(item.metricName());
      if (rawValue == null || rawValue.isBlank()) {
        continue;
      }
      String snmpOid = item.snmpOid();
      if (snmpOid != null && snmpOid.contains("walk[") && "[]".equals(rawValue.trim())) {
        continue;
      }
      ItemStateSnapshot previous = state.get(stateKey(item.key(), item.instanceKey()));
      MonitoringPreprocessingEngine.ProcessedMonitoringValue processed =
          preprocessingEngine.process(
              item.runtime(),
              rawValue,
              previous,
              timestamp,
              new MonitoringPreprocessContext(template, item)
          );
      if (processed.discarded()) {
        continue;
      }
      results.add(new ZabbixItemValue(
          item.templateId(),
          item.metricName(),
          item.key(),
          item.instanceKey(),
          item.discoveryRuleKey(),
          item.runtime().uuid(),
          processed.numericValue(),
          processed.textValue(),
          blankToNull(item.runtime().units()),
          item.runtime().valueMapName(),
          processed.status(),
          processed.note()
      ));
    }
    return new SnmpPollBatch(List.copyOf(results), rawValues);
  }

  private String stateKey(String itemKey, String instanceKey) {
    return itemKey + "::" + (instanceKey == null ? "" : instanceKey);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
