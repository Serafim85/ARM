package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DerivedMonitoringItemExecutor implements MonitoringItemExecutor {

  private static final String OID_IF_SPEED = "1.3.6.1.2.1.2.2.1.5";

  private final MonitoringPreprocessingEngine preprocessingEngine;
  private final ZabbixRuntimeStateService runtimeStateService;
  private final SnmpScanService snmpScanService;

  DerivedMonitoringItemExecutor(
      MonitoringPreprocessingEngine preprocessingEngine,
      ZabbixRuntimeStateService runtimeStateService
  ) {
    this(preprocessingEngine, runtimeStateService, null);
  }

  @Autowired
  public DerivedMonitoringItemExecutor(
      MonitoringPreprocessingEngine preprocessingEngine,
      ZabbixRuntimeStateService runtimeStateService,
      SnmpScanService snmpScanService
  ) {
    this.preprocessingEngine = preprocessingEngine;
    this.runtimeStateService = runtimeStateService;
    this.snmpScanService = snmpScanService;
  }

  @Override
  public boolean supports(MaterializedZabbixItem item) {
    return item.runtime().isDependent() || item.runtime().isCalculated();
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
    Map<String, ZabbixItemValue> cycleValues = writableCycleMap(currentCycleValues);
    List<ZabbixItemValue> results = new ArrayList<>();
    for (MaterializedZabbixItem item : items) {
      ZabbixItemValue value = null;
      if (item.runtime().isDependent()) {
        value = executeDependent(device, template, item, state, cycleValues, timestamp);
      } else if (item.runtime().isCalculated()) {
        value = executeCalculated(device, item, timestamp);
      }
      if (value != null) {
        results.add(value);
        cycleValues.put(stateKey(value.itemKey(), value.instanceKey()), value);
      }
    }
    return List.copyOf(results);
  }

  private static Map<String, ZabbixItemValue> writableCycleMap(Map<String, ZabbixItemValue> currentCycleValues) {
    if (currentCycleValues instanceof LinkedHashMap) {
      return currentCycleValues;
    }
    return new LinkedHashMap<>(currentCycleValues == null ? Map.of() : currentCycleValues);
  }

  private ZabbixItemValue executeDependent(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      MaterializedZabbixItem item,
      Map<String, ItemStateSnapshot> state,
      Map<String, ZabbixItemValue> currentCycleValues,
      OffsetDateTime timestamp
  ) {
    String masterKey = item.runtime().masterItemKey();
    if (masterKey == null || masterKey.isBlank()) {
      return null;
    }
    String masterLookupKey = materializedMasterKey(masterKey, item.macros());
    String masterInstanceKey = masterInstanceKeyForLookup(template, masterKey, item.instanceKey());
    ZabbixItemValue currentMaster = currentCycleValues.get(stateKey(masterLookupKey, masterInstanceKey));
    String rawValue = currentMaster != null
        ? firstNonBlank(currentMaster.textValue(), currentMaster.numericValue() == null ? null : String.valueOf(currentMaster.numericValue()))
        : null;
    if (isEmptySnmpWalkPayload(rawValue) && template.item(masterKey) != null) {
      rawValue = null;
    }
    if (rawValue == null) {
      if (currentMaster == null && isWalkBasedMaster(template, masterKey)) {
        return tryDirectIfSpeedPoll(device, template, item, state, timestamp);
      }
      ItemStateSnapshot previousMaster = state.get(stateKey(masterLookupKey, masterInstanceKey));
      rawValue = previousMaster == null
          ? null
          : firstNonBlank(previousMaster.textValue(), previousMaster.numericValue() == null ? null : String.valueOf(previousMaster.numericValue()));
    }
    if (isEmptySnmpWalkPayload(rawValue) && template.item(masterKey) != null) {
      rawValue = null;
    }
    if (rawValue == null) {
      return tryDirectIfSpeedPoll(device, template, item, state, timestamp);
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
      return null;
    }
    ZabbixItemValue value = toItemValue(item, processed);
    if (isNetIfSpeedItem(item.key()) && isEffectivelyZero(value)) {
      ZabbixItemValue direct = tryDirectIfSpeedPoll(device, template, item, state, timestamp);
      if (direct != null) {
        return direct;
      }
    }
    return value;
  }

  private ZabbixItemValue tryDirectIfSpeedPoll(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      MaterializedZabbixItem item,
      Map<String, ItemStateSnapshot> state,
      OffsetDateTime timestamp
  ) {
    if (!isNetIfSpeedItem(item.key()) || snmpScanService == null) {
      return null;
    }
    String snmpIndex = item.macros() == null ? null : item.macros().get("{#SNMPINDEX}");
    if (snmpIndex == null || snmpIndex.isBlank()) {
      return null;
    }
    Map<String, String> raw = snmpScanService.readRawOids(
        device.getIp(),
        template,
        Map.of("ifSpeed", OID_IF_SPEED + "." + snmpIndex.trim())
    );
    String ifSpeed = raw.get("ifSpeed");
    if (isBlankOrZero(ifSpeed)) {
      return null;
    }
    ItemStateSnapshot previous = state.get(stateKey(item.key(), item.instanceKey()));
    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed =
        preprocessingEngine.process(
            item.runtime(),
            ifSpeed.trim(),
            previous,
            timestamp,
            new MonitoringPreprocessContext(template, item)
        );
    if (processed.discarded()) {
      return null;
    }
    return toItemValue(item, processed);
  }

  private static ZabbixItemValue toItemValue(
      MaterializedZabbixItem item,
      MonitoringPreprocessingEngine.ProcessedMonitoringValue processed
  ) {
    return new ZabbixItemValue(
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
    );
  }

  private static boolean isNetIfSpeedItem(String itemKey) {
    return itemKey != null && itemKey.contains("net.if.speed");
  }

  private static boolean isWalkBasedMaster(ResolvedMonitoringTemplate template, String masterKey) {
    if (masterKey == null || masterKey.isBlank() || template == null) {
      return false;
    }
    ZabbixItemRuntime master = template.item(masterKey);
    String oid = master == null ? null : master.snmpOid();
    return oid != null && oid.contains("walk[");
  }

  private static boolean isEffectivelyZero(ZabbixItemValue value) {
    if (value == null) {
      return true;
    }
    if (value.numericValue() != null) {
      return value.numericValue() == 0.0d;
    }
    return isBlankOrZero(value.textValue());
  }

  private static boolean isBlankOrZero(String value) {
    return value == null || value.isBlank() || "0".equals(value.trim());
  }

  private ZabbixItemValue executeCalculated(
      MonitoredDeviceEntity device,
      MaterializedZabbixItem item,
      OffsetDateTime timestamp
  ) {
    String formula = item.runtime().params();
    if (formula == null || formula.isBlank()) {
      return null;
    }
    double value = TriggerEvaluationSupport.evaluateNumericExpression(
        formula,
        timestamp,
        (metricName, window, evaluationTimestamp) -> {
          if (window == null || window.isBlank()) {
            return runtimeStateService.loadRecentNumericValues(device, metricName, null, null, 1);
          }
          String trimmed = window.trim().toLowerCase();
          if (trimmed.startsWith("#")) {
            return runtimeStateService.loadRecentNumericValues(device, metricName, null, null, Integer.parseInt(trimmed.substring(1)));
          }
          if (trimmed.endsWith("s")) {
            long seconds = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
            return runtimeStateService.loadRecentNumericValues(device, metricName, null, evaluationTimestamp.minusSeconds(seconds), null);
          }
          return runtimeStateService.loadRecentNumericValues(device, metricName, null, null, 1);
        }
    );
    return new ZabbixItemValue(
        item.templateId(),
        item.metricName(),
        item.key(),
        item.instanceKey(),
        item.discoveryRuleKey(),
        item.runtime().uuid(),
        value,
        String.valueOf(value),
        blankToNull(item.runtime().units()),
        item.runtime().valueMapName(),
        "ok",
        null
    );
  }

  private static String materializedMasterKey(String masterKey, Map<String, String> macros) {
    if (masterKey == null || macros == null || macros.isEmpty()) {
      return masterKey;
    }
    String resolved = masterKey;
    for (Map.Entry<String, String> macro : macros.entrySet()) {
      if (macro.getKey() == null || macro.getKey().isBlank() || macro.getValue() == null) {
        continue;
      }
      resolved = resolved.replace(macro.getKey(), macro.getValue());
    }
    return resolved;
  }

  private static String masterInstanceKeyForLookup(
      ResolvedMonitoringTemplate template,
      String masterKey,
      String dependentInstanceKey
  ) {
    if (template == null || masterKey == null) {
      return "";
    }
    ZabbixItemRuntime master = template.item(masterKey);
    if (master == null) {
      return "";
    }
    if (master.discoveryPrototype()) {
      return dependentInstanceKey == null ? "" : dependentInstanceKey;
    }
    return "";
  }

  private String stateKey(String itemKey, String instanceKey) {
    return itemKey + "::" + (instanceKey == null ? "" : instanceKey);
  }

  private static boolean isEmptySnmpWalkPayload(String rawValue) {
    return rawValue != null && "[]".equals(rawValue.trim());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
