package com.wisla.arm.integration.support;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Base for HTTP integration tests against a running backend.
 * Set env BACKEND_BASE_URL (default http://localhost:8080).
 * Tests skip when backend is not reachable unless INTEGRATION_FORCE=1.
 */
public abstract class HttpIntegrationTestBase {

  protected static String baseUrl;
  protected static HttpClient httpClient;

  @BeforeAll
  static void initClient() {
    baseUrl = System.getenv().getOrDefault("BACKEND_BASE_URL", "http://localhost:8081");
    httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    assumeBackendReachable();
  }

  private static void assumeBackendReachable() {
    if ("1".equals(System.getenv("INTEGRATION_FORCE"))) {
      return;
    }
    boolean up = false;
    try {
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/api/public/app-config"))
          .timeout(Duration.ofSeconds(3))
          .GET()
          .build();
      HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
      up = resp.statusCode() >= 200 && resp.statusCode() < 500;
    } catch (Exception ignored) {
      // backend not running — skip ITs in local dev until stack exists
    }
    Assumptions.assumeTrue(up,
        "Backend not reachable at " + baseUrl + " — start stack or set INTEGRATION_FORCE=1");
  }
}
