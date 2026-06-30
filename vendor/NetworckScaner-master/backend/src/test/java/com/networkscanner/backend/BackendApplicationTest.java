package com.networkscanner.backend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BackendApplicationTest {

  @AfterEach
  void cleanup() {
    System.clearProperty("spring.profiles.active");
  }

  @Test
  void migratesMisplacedProfileFlagToSystemProperty() {
    String[] normalized = BackendApplication.normalizeArgs(new String[]{
        "-Dspring.profiles.active=prod,prod-max-throughput",
        "--server.port=8081"
    });

    assertEquals("prod,prod-max-throughput", System.getProperty("spring.profiles.active"));
    assertArrayEquals(new String[]{"--server.port=8081"}, normalized);
  }

  @Test
  void keepsSpringProfileArgWhenAlreadyPassedAsApplicationArg() {
    String[] normalized = BackendApplication.normalizeArgs(new String[]{
        "-Dspring.profiles.active=prod",
        "--spring.profiles.active=collector"
    });

    assertNull(System.getProperty("spring.profiles.active"));
    assertArrayEquals(new String[]{"--spring.profiles.active=collector"}, normalized);
  }
}
