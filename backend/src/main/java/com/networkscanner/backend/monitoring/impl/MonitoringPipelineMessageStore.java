package com.networkscanner.backend.monitoring.impl;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MonitoringPipelineMessageStore {

  private final JdbcTemplate jdbcTemplate;

  public MonitoringPipelineMessageStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean markProcessed(String messageId, String stage) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    int updated = jdbcTemplate.update(
        """
            INSERT INTO monitoring_pipeline_messages (message_id, stage)
            VALUES (?, ?)
            ON CONFLICT (message_id, stage) DO NOTHING
            """,
        messageId,
        stage
    );
    return updated > 0;
  }

  public boolean[] markProcessedBatch(List<String> messageIds, String stage) {
    if (messageIds == null || messageIds.isEmpty()) {
      return new boolean[0];
    }
    boolean[] accepted = new boolean[messageIds.size()];
    List<Object[]> batchArgs = messageIds.stream()
        .map(messageId -> new Object[] {messageId, stage})
        .collect(Collectors.toList());
    int[] updates = jdbcTemplate.batchUpdate(
        """
            INSERT INTO monitoring_pipeline_messages (message_id, stage)
            VALUES (?, ?)
            ON CONFLICT (message_id, stage) DO NOTHING
            """,
        batchArgs
    );
    for (int i = 0; i < messageIds.size(); i++) {
      String messageId = messageIds.get(i);
      accepted[i] = messageId == null || messageId.isBlank() || updates[i] > 0;
    }
    return accepted;
  }
}
