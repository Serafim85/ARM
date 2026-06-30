package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.network.scan.api.IcmpProbeResult;
import com.networkscanner.backend.network.scan.api.IcmpProbeService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IcmpMonitoringItemExecutor implements MonitoringItemExecutor {

  private final IcmpProbeService icmpProbeService;
  private final MonitoringPreprocessingEngine preprocessingEngine;

  public IcmpMonitoringItemExecutor(
      IcmpProbeService icmpProbeService,
      MonitoringPreprocessingEngine preprocessingEngine
  ) {
    this.icmpProbeService = icmpProbeService;
    this.preprocessingEngine = preprocessingEngine;
  }

  @Override
  public boolean supports(MaterializedZabbixItem item) {
    return item.runtime().isZabbixIcmpSimpleItem();
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
    if (items.isEmpty()) {
      return List.of();
    }
    IcmpProbeResult probe = icmpProbeService.probe(device.getIp());
    List<ZabbixItemValue> results = new ArrayList<>();
    for (MaterializedZabbixItem item : items) {
      String rawValue = rawValueForItem(item.key(), probe);
      if (rawValue == null) {
        continue;
      }
      ItemStateSnapshot previous = state.get(stateKey(item.key(), item.instanceKey()));
      MonitoringPreprocessingEngine.ProcessedMonitoringValue processed =
          preprocessingEngine.process(
              item.runtime(),
              rawValue,
              previous,
              timestamp,
              MonitoringPreprocessContext.NONE
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
    return List.copyOf(results);
  }

  private String rawValueForItem(String key, IcmpProbeResult probe) {
    return switch (normalizedKeyBase(key)) {
      case "icmpping" -> probe.reachable() ? "1" : "0";
      case "icmppingloss" -> Double.toString(probe.packetLossPercent());
      case "icmppingsec" -> Double.toString(probe.averageResponseSeconds());
      default -> null;
    };
  }

  private String normalizedKeyBase(String key) {
    if (key == null) {
      return "";
    }
    int bracketIndex = key.indexOf('[');
    String baseKey = bracketIndex >= 0 ? key.substring(0, bracketIndex) : key;
    return baseKey.trim().toLowerCase();
  }

  private String stateKey(String itemKey, String instanceKey) {
    return itemKey + "::" + (instanceKey == null ? "" : instanceKey);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
