package com.networkscanner.backend.agentingest.impl;

import com.networkscanner.backend.agentingest.api.AgentIngestPort;
import com.networkscanner.backend.agentingest.dto.AgentEventEntryDto;
import com.networkscanner.backend.agentingest.dto.AgentIngestBatchRequest;
import com.networkscanner.backend.agentingest.dto.AgentIngestResponse;
import com.networkscanner.backend.agentingest.dto.AgentLogEntryDto;
import com.networkscanner.backend.agentingest.dto.AgentMetricPointDto;
import com.networkscanner.backend.workstation.impl.ArmWorkstationAlertService;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import com.networkscanner.backend.workstation.repository.WorkstationRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentIngestServiceImpl implements AgentIngestPort {

  private static final String STATUS_ONLINE = "online";

  private final WorkstationRepository workstationRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ArmWorkstationAlertService armWorkstationAlertService;

  public AgentIngestServiceImpl(
      WorkstationRepository workstationRepository,
      JdbcTemplate jdbcTemplate,
      ArmWorkstationAlertService armWorkstationAlertService
  ) {
    this.workstationRepository = workstationRepository;
    this.jdbcTemplate = jdbcTemplate;
    this.armWorkstationAlertService = armWorkstationAlertService;
  }

  @Override
  @Transactional
  public AgentIngestResponse ingest(AgentIngestBatchRequest batch) {
    OffsetDateTime seenAt = batch.timestamp();
    WorkstationEntity workstation = workstationRepository
        .findByHostnameIgnoreCase(batch.hostname())
        .orElse(null);
    boolean registered = workstation == null;
    if (workstation == null) {
      workstation = new WorkstationEntity();
      workstation.setHostname(batch.hostname().trim());
      workstation.setDisplayName(batch.hostname().trim());
      workstation.setOsType(normalizeOsType(batch.osType()));
    }
    if (batch.primaryIp() != null && !batch.primaryIp().isBlank()) {
      workstation.setPrimaryIp(batch.primaryIp().trim());
    }
    if (batch.agentVersion() != null && !batch.agentVersion().isBlank()) {
      workstation.setAgentVersion(batch.agentVersion().trim());
    }
    workstation.setStatus(STATUS_ONLINE);
    workstation.setLastSeenAt(seenAt);
    workstation = workstationRepository.save(workstation);

    String deviceKey = metricDeviceKey(workstation);
    insertMetrics(deviceKey, batch.metrics(), seenAt);
    insertLogs(workstation.getId(), batch.logs());
    insertEvents(workstation.getId(), batch.events(), seenAt);
    armWorkstationAlertService.evaluateAfterIngest(workstation, batch.metrics(), seenAt);

    return new AgentIngestResponse(
        workstation.getId(),
        workstation.getHostname(),
        batch.metrics().size(),
        batch.logs().size(),
        registered
    );
  }

  public static String metricDeviceKey(WorkstationEntity workstation) {
    if (workstation.getPrimaryIp() != null && !workstation.getPrimaryIp().isBlank()) {
      String ip = workstation.getPrimaryIp().trim();
      return ip.length() <= 64 ? ip : ip.substring(0, 64);
    }
    String host = workstation.getHostname().trim();
    return host.length() <= 64 ? host : host.substring(0, 64);
  }

  private static String normalizeOsType(String osType) {
    if (osType == null || osType.isBlank()) {
      return "unknown";
    }
    return osType.trim().toLowerCase();
  }

  private void insertMetrics(String deviceIp, List<AgentMetricPointDto> metrics, OffsetDateTime fallbackTime) {
    if (metrics.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO metric_values (
              recorded_at, device_ip, metric_name, metric_value, item_key, instance_key, unit_label
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            AgentMetricPointDto metric = metrics.get(i);
            OffsetDateTime recordedAt = metric.clock() != null ? metric.clock() : fallbackTime;
            ps.setObject(1, recordedAt);
            ps.setString(2, deviceIp);
            ps.setString(3, metric.key());
            ps.setDouble(4, metric.value());
            ps.setString(5, metric.key());
            ps.setNull(6, java.sql.Types.VARCHAR);
            ps.setNull(7, java.sql.Types.VARCHAR);
          }

          @Override
          public int getBatchSize() {
            return metrics.size();
          }
        }
    );
  }

  private void insertLogs(Long workstationId, List<AgentLogEntryDto> logs) {
    if (logs.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO arm_log_events (workstation_id, recorded_at, level, message, source)
            VALUES (?, ?, ?, ?, ?)
            """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            AgentLogEntryDto log = logs.get(i);
            ps.setLong(1, workstationId);
            ps.setObject(2, log.clock() != null ? log.clock() : OffsetDateTime.now());
            ps.setString(3, log.level());
            ps.setString(4, log.message());
            ps.setString(5, log.source());
          }

          @Override
          public int getBatchSize() {
            return logs.size();
          }
        }
    );
  }

  private void insertEvents(Long workstationId, List<AgentEventEntryDto> events, OffsetDateTime fallbackTime) {
    if (events.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
            INSERT INTO arm_workstation_events (
              workstation_id, recorded_at, event_type, severity, message, error_code, error_text, source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            AgentEventEntryDto event = events.get(i);
            String eventType = normalizeEventType(event.type());
            ps.setLong(1, workstationId);
            ps.setObject(2, event.clock() != null ? event.clock() : fallbackTime);
            ps.setString(3, eventType);
            ps.setString(4, normalizeEventSeverity(event.severity(), eventType));
            ps.setString(5, event.message());
            ps.setString(6, event.errorCode());
            ps.setString(7, event.errorText());
            ps.setString(8, event.source());
          }

          @Override
          public int getBatchSize() {
            return events.size();
          }
        }
    );
  }

  private static String normalizeEventType(String raw) {
    if (raw == null || raw.isBlank()) {
      return "UNKNOWN";
    }
    return raw.trim().toUpperCase();
  }

  private static String normalizeEventSeverity(String raw, String eventType) {
    if (raw != null && !raw.isBlank()) {
      return raw.trim().toUpperCase();
    }
    if ("BSOD".equals(eventType) || "KERNEL_PANIC".equals(eventType)) {
      return "HIGH";
    }
    return "WARNING";
  }
}
