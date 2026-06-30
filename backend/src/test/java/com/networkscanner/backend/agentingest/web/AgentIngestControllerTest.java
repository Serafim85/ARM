package com.networkscanner.backend.agentingest.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.agentingest.api.AgentIngestPort;
import com.networkscanner.backend.agentingest.dto.AgentIngestBatchRequest;
import com.networkscanner.backend.agentingest.dto.AgentIngestResponse;
import com.networkscanner.backend.agentingest.dto.AgentMetricPointDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AgentIngestControllerTest {

  private final AgentIngestPort port = mock(AgentIngestPort.class);

  @Test
  void ingest_rejectsMissingApiKey() {
    AgentIngestController controller = new AgentIngestController(port, "test-key");
    AgentIngestBatchRequest batch = new AgentIngestBatchRequest(
        "h1", OffsetDateTime.parse("2026-06-22T12:00:00Z"), null, null, null, List.of(), List.of(), List.of());

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> controller.ingest(null, batch)
    );
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
  }

  @Test
  void ingest_acceptsValidBatch() {
    AgentIngestController controller = new AgentIngestController(port, "test-key");
    OffsetDateTime ts = OffsetDateTime.parse("2026-06-22T12:00:00Z");
    AgentIngestBatchRequest batch = new AgentIngestBatchRequest(
        "h1",
        ts,
        "0.1.0",
        "linux",
        null,
        List.of(new AgentMetricPointDto("arm.cpu.util", 1.0, ts)),
        List.of(),
        List.of()
    );
    when(port.ingest(batch)).thenReturn(new AgentIngestResponse(1L, "h1", 1, 0, true));

    AgentIngestResponse response = controller.ingest("test-key", batch);

    assertEquals(1L, response.workstationId());
    assertEquals(true, response.registered());
  }
}
