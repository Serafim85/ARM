package com.networkscanner.backend.network.scan.api;

import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateTelemetrySnapshot;
import com.networkscanner.backend.monitoring.dto.MonitoringDetailsDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.dto.ScanExecutionResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface SnmpScanService {

  ScanExecutionResult scan(ScanRequest request, ScanRunContext context);

  boolean stopScan(long runId);

  boolean checkIcmpReachable(String ip, int timeout);

  boolean checkPortReachable(String ip, int port, int timeout);

  boolean checkSnmpReachable(String ip, int port, int timeout, int retries, String community);

  List<DeviceInterfaceDto> readInterfaces(String ip, int port, int timeout, int retries, String community);

  boolean checkSnmpReachable(String ip, ResolvedMonitoringTemplate template);

  List<DeviceInterfaceDto> readInterfaces(String ip, ResolvedMonitoringTemplate template);

  MonitoringDetailsDto readMonitoringDetails(String ip, int port, int timeout, int retries, String community);

  MonitoringDetailsDto readMonitoringDetails(String ip, ResolvedMonitoringTemplate template);

  Map<String, Double> readMonitoringMetrics(String ip, ResolvedMonitoringTemplate template);

  /**
   * Resolves telemetry panel fields from already collected item values (e.g. monitoring_item_state).
   */
  ItemStateTelemetrySnapshot resolveTelemetryFromItemValues(
      Map<String, Double> itemValues,
      Map<String, ZabbixItemRuntime> itemDefinitions
  );

  Map<String, String> readRawOids(String ip, ResolvedMonitoringTemplate template, Map<String, String> requestedOids);

  List<DiscoveryInstanceRuntime> executeDiscovery(
      String ip,
      ResolvedMonitoringTemplate template,
      ZabbixDiscoveryRuleRuntime discoveryRule,
      OffsetDateTime timestamp
  );
}
