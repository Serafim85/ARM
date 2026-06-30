package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import java.util.List;

public interface MonitoringTemplateResolver {

  void initialize();

  List<MonitoringTemplateSummaryDto> listTemplates();

  MonitoringTemplateDetailsDto describeTemplate(String templateId);

  MonitoringTemplateImportPreviewDto previewArchive(String originalFilename, byte[] archiveBytes);

  ResolvedMonitoringTemplate resolveTemplateById(String templateId);

  ResolvedMonitoringTemplate resolveForDevice(
      String selectedTemplateId,
      String vendor,
      String model
  );

  ResolvedMonitoringTemplate resolveForDevice(
      String selectedTemplateId,
      String vendor,
      String model,
      String firmwareVersion
  );

  ResolvedMonitoringTemplate resolveForDevice(
      List<String> selectedTemplateIds,
      String vendor,
      String model
  );

  ResolvedMonitoringTemplate resolveForDevice(
      List<String> selectedTemplateIds,
      String vendor,
      String model,
      String firmwareVersion
  );

  ResolvedMonitoringTemplate resolveMergedTemplates(List<String> templateIds);

  String mapValue(String templateId, String valueMapName, String rawValue);
}
