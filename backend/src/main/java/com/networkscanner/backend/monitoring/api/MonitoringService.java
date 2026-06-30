package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.DeviceMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.MonitoredDeviceDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringEventLevelSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventPageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDiscoveryInstanceDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemsUpdateRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringHostFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostPageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStateDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStatePageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOperationResultDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateUpdateRequest;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

public interface MonitoringService {

  void initialize();

  MonitoringHostPageDto list(MonitoringHostFilter filter, int page, int size, String sortField, String sortOrder);

  List<DeviceScanResult> activate(
      List<DeviceScanResult> devices,
      String templateId,
      List<String> templateIds,
      Map<String, String> perDeviceTemplateIds,
      Map<String, List<String>> perDeviceTemplateIdLists,
      MonitoringSnmpCredentials snmpCredentials,
      Authentication authentication
  );

  List<DeviceScanResult> deactivate(List<String> ips, Authentication authentication);

  List<DeviceScanResult> deactivateByIds(List<Long> deviceIds, Authentication authentication);

  DeviceScanResult getByIp(String ip);

  DeviceScanResult getByDeviceId(Long id);

  MonitoredDeviceDto getMonitoredDeviceById(Long id);

  MonitoredDeviceDto updateDeviceTags(Long deviceId, List<String> tags, Authentication authentication);

  List<DeviceInterfaceDto> getDeviceInterfaces(String ip);

  List<DeviceInterfaceDto> getDeviceInterfacesById(Long deviceId);

  List<DeviceInterfaceDto> refreshDeviceInterfacesById(Long deviceId);

  MonitoringDetailsDto getDeviceMonitoringDetails(String ip);

  MonitoringDetailsDto getDeviceMonitoringDetailsById(Long deviceId);

  MonitoringDetailsDto refreshDeviceMonitoringDetails(String ip, boolean liveMode);

  MonitoringDetailsDto refreshDeviceMonitoringDetailsById(Long deviceId, boolean liveMode);

  List<MetricValueDto> getMetricsWithUnits(
      String ip,
      OffsetDateTime from,
      OffsetDateTime to,
      String metricName
  );

  List<MetricValueDto> getMetricsWithUnitsById(
      Long deviceId,
      OffsetDateTime from,
      OffsetDateTime to,
      String metricName
  );

  DeviceMetricsHistoryResponseDto getMetricsHistoryById(
      Long deviceId,
      OffsetDateTime from,
      OffsetDateTime to,
      String metricName,
      String q,
      Integer panelsOffset,
      Integer panelsLimit,
      Integer maxPoints
  );

  CompactMetricsHistoryResponseDto getMetricsHistoryCompactById(
      Long deviceId,
      OffsetDateTime from,
      OffsetDateTime to,
      String metricName,
      String q,
      Integer panelsOffset,
      Integer panelsLimit,
      Integer maxPoints
  );

  List<MetricValueDto> getLatestMetricsWithUnits(String ip, String metricName);

  List<MetricValueDto> getLatestMetricsWithUnitsById(Long deviceId, String metricName);

  List<MonitoringMetricsBatchSeriesDto> getMetricsWithUnitsBatch(MonitoringMetricsBatchRequest request);

  List<CompactMetricsBatchSeriesDto> getMetricsWithUnitsBatchCompact(MonitoringMetricsBatchRequest request);

  List<MonitoringTemplateSummaryDto> listMonitoringTemplates();

  MonitoringTemplateDetailsDto getMonitoringTemplateDetails(String templateId);

  MonitoringTemplateImportPreviewDto previewMonitoringTemplateArchive(
      String originalFilename,
      byte[] archiveBytes
  );

  MonitoringTemplateOperationResultDto uploadMonitoringTemplateArchive(
      String originalFilename,
      byte[] archiveBytes,
      String vendor,
      String model,
      String firmware,
      Authentication authentication
  );

  MonitoringTemplateOperationResultDto deleteMonitoringTemplate(String templateId, Authentication authentication);

  MonitoringTemplateOperationResultDto updateMonitoringTemplate(
      String templateId,
      MonitoringTemplateUpdateRequest request,
      Authentication authentication
  );

  List<DeviceScanResult> matchScanResults(List<DeviceScanResult> scanned);

  List<MonitoringEventDto> getEventsByDeviceId(Long deviceId);

  MonitoringEventPageDto listMonitoringEvents(MonitoringEventFilter filter, int page, int size);

  MonitoringEventLevelSummaryDto summarizeMonitoringEventsByLevel(MonitoringEventFilter filter);

  MonitoringItemStatePageDto getItemStatePage(Long deviceId, String q, int page, int size);

  List<MonitoringDiscoveryInstanceDto> getDiscoveryStateByDeviceId(Long deviceId);

  List<MonitoringDeviceItemDto> getDeviceItemsByDeviceId(Long deviceId);

  List<MonitoringDeviceItemDto> updateDeviceItemsByDeviceId(
      Long deviceId,
      MonitoringDeviceItemsUpdateRequest request,
      Authentication authentication
  );

  void deactivateDeviceItemByDeviceId(
      Long deviceId,
      String itemUuid,
      String instanceKey,
      Authentication authentication
  );
}
