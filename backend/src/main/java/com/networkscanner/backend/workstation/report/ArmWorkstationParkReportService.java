package com.networkscanner.backend.workstation.report;

import com.networkscanner.backend.agentingest.impl.AgentIngestServiceImpl;
import com.networkscanner.backend.monitoring.api.MetricsHistoryService;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.workstation.api.WorkstationPort;
import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationFilter;
import com.networkscanner.backend.workstation.dto.WorkstationListItemDto;
import com.networkscanner.backend.workstation.dto.WorkstationPageDto;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import com.networkscanner.backend.workstation.repository.WorkstationRepository;
import com.networkscanner.backend.workstation.repository.WorkstationTelemetryRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArmWorkstationParkReportService {

  private final WorkstationPort workstationPort;
  private final WorkstationRepository workstationRepository;
  private final MetricsHistoryService metricsHistoryService;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final MonitoringEventRepository monitoringEventRepository;
  private final WorkstationTelemetryRepository telemetryRepository;

  public ArmWorkstationParkReportService(
      WorkstationPort workstationPort,
      WorkstationRepository workstationRepository,
      MetricsHistoryService metricsHistoryService,
      MonitoredDeviceRepository monitoredDeviceRepository,
      MonitoringEventRepository monitoringEventRepository,
      WorkstationTelemetryRepository telemetryRepository
  ) {
    this.workstationPort = workstationPort;
    this.workstationRepository = workstationRepository;
    this.metricsHistoryService = metricsHistoryService;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.monitoringEventRepository = monitoringEventRepository;
    this.telemetryRepository = telemetryRepository;
  }

  @Transactional(readOnly = true)
  public WorkstationParkReport buildParkReport(WorkstationFilter filter) {
    List<WorkstationListItemDto> workstations = loadAllWorkstations(filter);
    List<WorkstationParkReportRow> registry = new ArrayList<>();
    List<WorkstationRecommendationRow> recommendations = new ArrayList<>();

    for (WorkstationListItemDto workstation : workstations) {
      WorkstationEntity entity = workstationRepository.findById(workstation.id()).orElse(null);
      if (entity == null) {
        continue;
      }
      String deviceKey = AgentIngestServiceImpl.metricDeviceKey(entity);
      Map<String, Double> latestMetrics = loadLatestMetrics(deviceKey);
      List<MonitoringEventEntity> openEvents = loadOpenMonitoringEvents(deviceKey);
      List<WorkstationEventEntryDto> armEvents = telemetryRepository.findEvents(workstation.id(), 20);

      registry.add(new WorkstationParkReportRow(
          workstation.id(),
          workstation.hostname(),
          workstation.displayName(),
          workstation.osType(),
          workstation.primaryIp(),
          workstation.agentVersion(),
          workstation.status(),
          workstation.lastSeenAt(),
          latestMetrics.get("arm.cpu.util"),
          latestMetrics.get("arm.mem.used"),
          latestMetrics.get("arm.disk.root.used_pct")
      ));
      recommendations.add(
          ArmWorkstationRecommendationBuilder.build(workstation, latestMetrics, openEvents, armEvents)
      );
    }

    return new WorkstationParkReport(
        OffsetDateTime.now(),
        registry,
        ArmWorkstationRecommendationBuilder.sortByPriority(recommendations)
    );
  }

  private List<WorkstationListItemDto> loadAllWorkstations(WorkstationFilter filter) {
    List<WorkstationListItemDto> rows = new ArrayList<>();
    int page = 0;
    while (true) {
      WorkstationPageDto pageDto = workstationPort.list(filter, page, 500, "hostname", "asc");
      rows.addAll(pageDto.content());
      if (pageDto.last()) {
        break;
      }
      page++;
    }
    return rows;
  }

  private Map<String, Double> loadLatestMetrics(String deviceKey) {
    Map<String, Double> metrics = new HashMap<>();
    List<MetricValueDto> latest = metricsHistoryService.queryLatestMetricValues(deviceKey, null);
    for (MetricValueDto point : latest) {
      if (point.metricName() != null) {
        metrics.put(point.metricName(), point.metricValue());
      }
    }
    return metrics;
  }

  private List<MonitoringEventEntity> loadOpenMonitoringEvents(String deviceKey) {
    Optional<MonitoredDeviceEntity> device = monitoredDeviceRepository.findFirstByIpAndSnmpPortIsNull(deviceKey);
    if (device.isEmpty()) {
      return List.of();
    }
    return monitoringEventRepository.findByDevice_IdAndStatus(device.get().getId(), MonitoringEventStatus.OPEN);
  }
}
