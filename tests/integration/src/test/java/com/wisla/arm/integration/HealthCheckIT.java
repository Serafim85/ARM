package com.wisla.arm.integration;

import com.wisla.arm.integration.support.HttpIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

class HealthCheckIT extends HttpIntegrationTestBase {

  @Test
  @DisplayName("GET app-config is reachable")
  void appConfigIsReachable() {
    given()
        .baseUri(baseUrl)
        .when()
        .get("/api/public/app-config")
        .then()
        .statusCode(200)
        .body("applicationName", notNullValue());
  }
}
