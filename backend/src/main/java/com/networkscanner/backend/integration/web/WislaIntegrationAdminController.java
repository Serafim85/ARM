package com.networkscanner.backend.integration.web;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.api.WislaEventPublisher;
import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;
import com.networkscanner.backend.integration.dto.IncidentStatus;
import com.networkscanner.backend.integration.dto.MonitorState;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshotDevice;
import com.networkscanner.backend.integration.dto.ProbeAvailability;
import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/integration/wisla")
@Tag(name = "Интеграция Wisla (admin)", description = "Административные операции для smoke-тестов интеграции")
public class WislaIntegrationAdminController {

  private final WislaEventPublisher wislaEventPublisher;
  private final SourceSystemProvider sourceSystemProvider;

  public WislaIntegrationAdminController(
      WislaEventPublisher wislaEventPublisher,
      SourceSystemProvider sourceSystemProvider
  ) {
    this.wislaEventPublisher = wislaEventPublisher;
    this.sourceSystemProvider = sourceSystemProvider;
  }

  @PostMapping("/test-event")
  @Operation(summary = "Публикует синтетическое событие в Kafka для smoke-теста интеграции")
  public ResponseEntity<Void> sendTestEvent(
      @Parameter(description = "Тип тестового события: availability | incident | state")
      @RequestParam("type") String type
  ) {
    String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    Instant now = Instant.now();
    String sourceSystem = sourceSystemProvider.getSourceSystem();
    if ("availability".equals(normalized)) {
      wislaEventPublisher.publishAvailability(new ProbeAvailabilityUpdate(
          "1.0",
          UUID.randomUUID().toString(),
          sourceSystem,
          -1L,
          ProbeAvailability.AVAILABLE,
          now
      ));
      return ResponseEntity.accepted().build();
    }
    if ("incident".equals(normalized)) {
      wislaEventPublisher.publishIncident(new ExternalIncidentUpsert(
          "1.0",
          UUID.randomUUID().toString(),
          sourceSystem,
          -1L,
          "test-template",
          "1",
          IncidentStatus.OPEN,
          "test-trigger-uuid",
          "Test Trigger",
          "test.metric",
          "instance-1",
          "WARNING",
          80.0d,
          92.0d,
          now.minusSeconds(120),
          null,
          now,
          "test-correlation-key",
          "WARNING",
          "last(/test.metric)>80",
          null,
          null,
          "test-pack",
          "",
          "test.metric"
      ));
      return ResponseEntity.accepted().build();
    }
    if ("state".equals(normalized)) {
      wislaEventPublisher.publishMonitorStateSnapshot(new MonitorStateSnapshot(
          "1.0",
          UUID.randomUUID().toString(),
          sourceSystem,
          -1L,
          new MonitorStateSnapshotDevice(
              MonitorState.MONITOR_ON,
              "test-host",
              "127.0.0.1",
              List.of("test-template:1"),
              "1"
          )
      ));
      return ResponseEntity.accepted().build();
    }
    return ResponseEntity.badRequest().build();
  }
}
