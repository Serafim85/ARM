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
import static org.hamcrest.Matchers.hasItem;

class WorkstationTelemetryIT extends HttpIntegrationTestBase {

  private static final String AGENT_KEY = System.getenv().getOrDefault(
      "AGENT_INGEST_API_KEY", "dev-arm-ingest-key");

  @Test
  @DisplayName("GET logs and events after ingest with telemetry payload")
  void logsAndEventsAvailable() throws Exception {
    String hostname = "pilot-telemetry-it-" + System.currentTimeMillis();
    Path fixture = Path.of("..", "fixtures", "ingest-batch-linux.json").normalize();
    String body = Files.readString(fixture).replace("pilot-linux-01", hostname);

    long workstationId = given()
        .baseUri(baseUrl)
        .header("X-Agent-Key", AGENT_KEY)
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("/api/v1/agent/ingest")
        .then()
        .statusCode(200)
        .extract()
        .path("workstationId");

    String token = loginToken();

    given()
        .baseUri(baseUrl)
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/api/v1/workstations/" + workstationId + "/logs")
        .then()
        .statusCode(200)
        .body("size()", greaterThan(0))
        .body("level", hasItem("warning"));

    given()
        .baseUri(baseUrl)
        .header("Authorization", "Bearer " + token)
        .when()
        .get("/api/v1/workstations/" + workstationId + "/events")
        .then()
        .statusCode(200)
        .body("size()", greaterThan(0))
        .body("eventType", hasItem("BSOD"))
        .body("[0].errorCode", equalTo("0x000000EF"));
  }

  private static String loginToken() {
    return given()
        .baseUri(baseUrl)
        .contentType(ContentType.JSON)
        .body("""
            {"email":"admin@example.com","password":"password","authMode":"LOCAL"}
            """)
        .when()
        .post("/api/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .path("accessToken");
  }
}
