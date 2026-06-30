package com.networkscanner.backend.workstation.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.agentingest.impl.AgentIngestServiceImpl;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkstationMonitoredDeviceBridge {

  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final MonitoringTemplateResolver templateResolver;
  private final ObjectMapper objectMapper;

  public WorkstationMonitoredDeviceBridge(
      MonitoredDeviceRepository monitoredDeviceRepository,
      MonitoringTemplateResolver templateResolver,
      ObjectMapper objectMapper
  ) {
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.templateResolver = templateResolver;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public MonitoredDeviceEntity getOrCreate(WorkstationEntity workstation) {
    String deviceIp = AgentIngestServiceImpl.metricDeviceKey(workstation);
    MonitoredDeviceEntity existing = monitoredDeviceRepository.findFirstByIpAndSnmpPortIsNull(deviceIp).orElse(null);
    if (existing != null) {
      syncWorkstationMetadata(existing, workstation);
      return monitoredDeviceRepository.save(existing);
    }
    String templateId = ArmWorkstationTemplateSupport.templateIdForOsType(workstation.getOsType());
    ResolvedMonitoringTemplate template = templateResolver.resolveTemplateById(templateId);
    OffsetDateTime now = OffsetDateTime.now();
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setIp(deviceIp);
    entity.setHostName(workstation.getHostname());
    entity.setDomainName("-");
    entity.setName(displayName(workstation));
    entity.setSerialNumber("ARM-" + (workstation.getId() != null ? workstation.getId() : workstation.getHostname()));
    entity.setMacAddress("00:00:00:00:00:00");
    entity.setVendor("WISLA ARM");
    entity.setModel(workstation.getOsType());
    entity.setFirmwareVersion(workstation.getAgentVersion() == null ? "-" : workstation.getAgentVersion());
    entity.setPollingStatus("AGENT");
    entity.setStatus("Включено");
    entity.setHealthStatus(DeviceHealthStatus.NORM);
    entity.setGroupName("arm-workstations");
    entity.setTagsJson(tagsJson());
    entity.setAvailabilityJson("[]");
    entity.setTemplateId(templateId);
    entity.setTemplateIds(templateId);
    entity.setEffectiveTemplateId(template.id());
    entity.setTemplateVersion(template.templateVersion());
    entity.setPackVersion(template.packVersion());
    entity.setSchemaVersion(template.schemaVersion());
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return monitoredDeviceRepository.save(entity);
  }

  private void syncWorkstationMetadata(MonitoredDeviceEntity device, WorkstationEntity workstation) {
    device.setHostName(workstation.getHostname());
    device.setName(displayName(workstation));
    device.setModel(workstation.getOsType());
    if (workstation.getAgentVersion() != null && !workstation.getAgentVersion().isBlank()) {
      device.setFirmwareVersion(workstation.getAgentVersion().trim());
    }
    String templateId = ArmWorkstationTemplateSupport.templateIdForOsType(workstation.getOsType());
    if (!templateId.equals(device.getEffectiveTemplateId())) {
      ResolvedMonitoringTemplate template = templateResolver.resolveTemplateById(templateId);
      device.setTemplateId(templateId);
      device.setTemplateIds(templateId);
      device.setEffectiveTemplateId(template.id());
      device.setTemplateVersion(template.templateVersion());
      device.setPackVersion(template.packVersion());
      device.setSchemaVersion(template.schemaVersion());
    }
    device.setUpdatedAt(OffsetDateTime.now());
  }

  private static String displayName(WorkstationEntity workstation) {
    if (workstation.getDisplayName() != null && !workstation.getDisplayName().isBlank()) {
      return workstation.getDisplayName().trim();
    }
    return workstation.getHostname();
  }

  private String tagsJson() {
    try {
      return objectMapper.writeValueAsString(List.of(ArmWorkstationTemplateSupport.TAG_ARM_WORKSTATION));
    } catch (JsonProcessingException e) {
      return "[\"" + ArmWorkstationTemplateSupport.TAG_ARM_WORKSTATION + "\"]";
    }
  }
}
