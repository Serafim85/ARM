package com.networkscanner.backend.users.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChartMetricsPeriodIdsTest {

  @Test
  void normalizeOrDefaultAcceptsHourCaseInsensitive() {
    assertEquals(ChartMetricsPeriodIds.HOUR, ChartMetricsPeriodIds.normalizeOrDefault("hour"));
    assertEquals(ChartMetricsPeriodIds.HOUR, ChartMetricsPeriodIds.normalizeOrDefault("HOUR"));
  }

  @Test
  void isKnownIncludesHour() {
    assertTrue(ChartMetricsPeriodIds.isKnown("HOUR"));
    assertFalse(ChartMetricsPeriodIds.isKnown("INVALID"));
  }

  @Test
  void normalizeOrDefaultFallsBackToDayForUnknown() {
    assertEquals(ChartMetricsPeriodIds.DEFAULT, ChartMetricsPeriodIds.normalizeOrDefault("INVALID"));
  }
}
