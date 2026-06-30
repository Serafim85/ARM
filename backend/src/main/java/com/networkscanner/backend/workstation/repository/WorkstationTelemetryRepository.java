package com.networkscanner.backend.workstation.repository;

import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationLogEntryDto;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkstationTelemetryRepository {

  private final JdbcTemplate jdbcTemplate;

  public WorkstationTelemetryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<WorkstationLogEntryDto> findLogs(long workstationId, List<String> levels, int limit) {
    if (levels == null || levels.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(",", levels.stream().map(ignored -> "?").toList());
    String sql = """
        SELECT recorded_at, level, message, source
        FROM arm_log_events
        WHERE workstation_id = ?
          AND UPPER(level) IN (%s)
        ORDER BY recorded_at DESC
        LIMIT ?
        """.formatted(placeholders);
    Object[] params = new Object[levels.size() + 2];
    params[0] = workstationId;
    for (int i = 0; i < levels.size(); i++) {
      params[i + 1] = levels.get(i).toUpperCase();
    }
    params[params.length - 1] = limit;
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> new WorkstationLogEntryDto(
            rs.getObject("recorded_at", java.time.OffsetDateTime.class),
            rs.getString("level"),
            rs.getString("message"),
            rs.getString("source")
        ),
        params
    );
  }

  public List<WorkstationEventEntryDto> findEvents(long workstationId, int limit) {
    return jdbcTemplate.query(
        """
            SELECT id, recorded_at, event_type, severity, message, error_code, error_text, source
            FROM arm_workstation_events
            WHERE workstation_id = ?
            ORDER BY recorded_at DESC
            LIMIT ?
            """,
        (rs, rowNum) -> new WorkstationEventEntryDto(
            rs.getLong("id"),
            rs.getObject("recorded_at", java.time.OffsetDateTime.class),
            rs.getString("event_type"),
            rs.getString("severity"),
            rs.getString("message"),
            rs.getString("error_code"),
            rs.getString("error_text"),
            rs.getString("source")
        ),
        workstationId,
        limit
    );
  }
}
