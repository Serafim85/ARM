package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import java.time.OffsetDateTime;

/**
 * Достаёт числовое значение dependent-item из сырого JSON мастера (как при SNMP walk → JSON → JSONPATH).
 */
public interface DependentItemSnapshotPreprocessor {

  /**
   * @param context обычно {@link MonitoringPreprocessContext#NONE} для шагов вроде JSONPATH без SNMP_WALK_VALUE
   */
  Double preprocessDependentNumeric(
      ZabbixItemRuntime dependent,
      String masterRawPayload,
      OffsetDateTime now,
      MonitoringPreprocessContext context
  );
}
