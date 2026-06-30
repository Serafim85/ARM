package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JsPreprocessingCompatServiceTest {

  private final JsPreprocessingCompatService service = new JsPreprocessingCompatService(Optional.empty());

  @AfterEach
  void tearDown() {
    service.shutdown();
  }

  @Test
  void executesScriptAndConvertsResultToString() {
    JsPreprocessingCompatService.JsResult result = service.execute(
        "2.62128e+07",
        "return Number(value);",
        Map.of()
    );

    assertEquals("ok", result.status());
    assertEquals("26212800", result.value());
  }

  @Test
  void nullReturnMarksValueAsDiscarded() {
    JsPreprocessingCompatService.JsResult result = service.execute(
        "41",
        "return null;",
        Map.of()
    );

    assertEquals("discarded", result.status());
  }

  @Test
  void undefinedReturnIsError() {
    JsPreprocessingCompatService.JsResult result = service.execute(
        "41",
        "var x = 1;",
        Map.of()
    );

    assertEquals("error", result.status());
    assertTrue(result.note().contains("undefined"));
  }

  @Test
  void throwReturnsErrorMessage() {
    JsPreprocessingCompatService.JsResult result = service.execute(
        "0",
        "if (value == 0) { throw 'Zero input value'; } return 1 / value;",
        Map.of()
    );

    assertEquals("error", result.status());
    assertTrue(result.note().contains("Zero input value"));
  }

  @Test
  void macrosAreResolvedBeforeExecution() {
    JsPreprocessingCompatService.JsResult result = service.execute(
        "5",
        "var threshold = '{$THRESHOLD}'; return (!isNaN(threshold) && Number(value) > Number(threshold)) ? threshold : value;",
        Map.of("{$THRESHOLD}", "3")
    );

    assertEquals("ok", result.status());
    assertEquals("3", result.value());
  }

  @Test
  void timeoutReturnsError() {
    JsPreprocessingCompatService.JsResult result = service.execute(
        "1",
        "var start = Date.now(); while (Date.now() - start < 15000) {} return value;",
        Map.of()
    );

    assertEquals("error", result.status());
    assertTrue(result.note().toLowerCase().contains("timeout"));
  }

  @Test
  void scriptCacheKeyUsesSha256Hex() {
    String a = JsPreprocessingCompatService.scriptCacheKey("return 1;");
    String b = JsPreprocessingCompatService.scriptCacheKey("return 2;");
    assertEquals(64, a.length());
    assertNotEquals(a, b);
  }

  @Test
  void compileCacheKeyIgnoresMacroValuesButDependsOnMacroNames() {
    String body = "return value;";
    String k1 = JsPreprocessingCompatService.compileCacheKey(body, Map.of("{$A}", "1"));
    String k2 = JsPreprocessingCompatService.compileCacheKey(body, Map.of("{$A}", "999"));
    assertEquals(k1, k2);
    String k3 = JsPreprocessingCompatService.compileCacheKey(body, Map.of("{$B}", "1"));
    assertNotEquals(k1, k3);
    String k4 = JsPreprocessingCompatService.compileCacheKey(body, Map.of("{$B}", "x", "{$A}", "y"));
    String k5 = JsPreprocessingCompatService.compileCacheKey(body, Map.of("{$A}", "y", "{$B}", "x"));
    assertEquals(k4, k5);
  }

  @Test
  void changingMacroValuesDoesNotRebuildSourceCache() {
    String script = "var threshold = '{$T}'; return (!isNaN(threshold) && Number(value) > Number(threshold)) ? threshold : value;";
    long loadsBefore = service.scriptCacheLoadCountForTests();
    for (int i = 0; i < 200; i++) {
      JsPreprocessingCompatService.JsResult result = service.execute(
          "10",
          script,
          Map.of("{$T}", String.valueOf(i % 7))
      );
      assertEquals("ok", result.status(), "iteration " + i);
    }
    long loadsAfter = service.scriptCacheLoadCountForTests();
    assertTrue(loadsAfter - loadsBefore <= 3L, "compile churn: loads before=" + loadsBefore + " after=" + loadsAfter);
  }

  @Test
  void repeatedExecutionManyTimesRemainsConsistent() {
    String script = "return Number(value) * 2;";
    for (int i = 0; i < 10_000; i++) {
      JsPreprocessingCompatService.JsResult result = service.execute(String.valueOf(i), script, Map.of());
      assertEquals("ok", result.status(), "iteration " + i);
      assertEquals(String.valueOf(i * 2), result.value(), "iteration " + i);
    }
  }

  @Test
  void engineContinuesAfterThreeConsecutiveFailures() {
    for (int i = 0; i < 3; i++) {
      JsPreprocessingCompatService.JsResult failed = service.execute(
          "1",
          "throw new Error('boom');",
          Map.of()
      );
      assertEquals("error", failed.status());
    }

    JsPreprocessingCompatService.JsResult success = service.execute(
        "2",
        "return Number(value) + 2;",
        Map.of()
    );

    assertEquals("ok", success.status());
    assertEquals("4", success.value());
  }
}
