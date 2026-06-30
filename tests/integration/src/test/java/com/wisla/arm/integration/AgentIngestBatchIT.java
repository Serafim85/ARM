package com.wisla.arm.integration;

import com.wisla.arm.integration.support.HttpIntegrationTestBase;
import io.restassured.http.ContentType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

class AgentIngestBatchIT extends HttpIntegrationTestBase {

  private static final String AGENT_KEY = System.getenv().getOrDefault(
      "AGENT_INGEST_API_KEY", "dev-arm-ingest-key");

  @Test
  @DisplayName("POST ingest batch returns workstation and accepts metrics")
  void ingestBatchPersists() throws Exception {
    Path fixture = Path.of("..", "fixtures", "ingest-batch-linux.json").normalize();
    String body = Files.readString(fixture);
    String hostname = "pilot-linux-it-" + System.currentTimeMillis();
    body = body.replace("pilot-linux-01", hostname);

    given()
        .baseUri(baseUrl)
        .header("X-Agent-Key", AGENT_KEY)
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/v1/agent/ingest")
        .then()
        .statusCode(200)
        .body("hostname", equalTo(hostname))
        .body("workstationId", greaterThan(0))
        .body("metricsAccepted", equalTo(3))
        .body("registered", notNullValue());
  }
}
