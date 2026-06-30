package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class UnitScalingServiceTest {

  private final UnitScalingService service = new UnitScalingService();

  @Test
  void scaleSingleBytesUsesIecBase1024() {
    UnitScalingService.ScalingResult result = service.scaleSingle(2147479552d, "B");

    assertNotNull(result);
    assertEquals("GB", result.scaledUnit());
    assertEquals(2.0d, result.scaledValue(), 0.0001d);
    assertEquals("2 GB", result.displayValue());
  }

  @Test
  void resolveSeriesScaleForSecondsPromotesToHours() {
    UnitScalingService.SeriesScale scale = service.resolveSeriesScale("s", 7200d);

    assertNotNull(scale);
    assertEquals("h", scale.unit());
    assertEquals(3600d, scale.factor(), 0.0001d);
  }

  @Test
  void unsupportedUnitFallsBackToRaw() {
    UnitScalingService.SeriesScale scale = service.resolveSeriesScale("%", 99d);
    UnitScalingService.ScalingResult result = service.applySeriesScale(99d, "%", scale);

    assertNull(scale);
    assertNotNull(result);
    assertEquals("%", result.scaledUnit());
    assertEquals(99d, result.scaledValue(), 0.0001d);
    assertEquals("99 %", result.displayValue());
  }

  @Test
  void scaleSingleSecondsDownscalesToMillisecondsForSubsecondValues() {
    UnitScalingService.ScalingResult result = service.scaleSingle(0.25d, "s");

    assertNotNull(result);
    assertEquals("ms", result.scaledUnit());
    assertEquals(250d, result.scaledValue(), 0.0001d);
    assertEquals("250 ms", result.displayValue());
  }

  @Test
  void scaleSingleSecondsDownscalesToNanosecondsWhenNeeded() {
    UnitScalingService.ScalingResult result = service.scaleSingle(0.0000008d, "s");

    assertNotNull(result);
    assertEquals("ns", result.scaledUnit());
    assertEquals(800d, result.scaledValue(), 0.0001d);
    assertEquals("800 ns", result.displayValue());
  }

  @Test
  void nonFiniteValuesDoNotThrowAndYieldDashDisplay() {
    UnitScalingService.ScalingResult nanSingle = service.scaleSingle(Double.NaN, "bps");
    assertNull(nanSingle.scaledValue());
    assertEquals("—", nanSingle.displayValue());

    UnitScalingService.SeriesScale scale = service.resolveSeriesScale("bps", Double.NaN);
    assertNotNull(scale);
    UnitScalingService.ScalingResult nanSeries = service.applySeriesScale(Double.NaN, "bps", scale);
    assertNull(nanSeries.scaledValue());
    assertEquals("—", nanSeries.displayValue());
  }

  @Test
  void resolveSeriesScaleSupportsBitsPerSecond() {
    UnitScalingService.SeriesScale scale = service.resolveSeriesScale("bps", 2_500_000d);
    UnitScalingService.ScalingResult result = service.applySeriesScale(250_000d, "bps", scale);

    assertNotNull(scale);
    assertEquals("Mbps", scale.unit());
    assertEquals(1_000_000d, scale.factor(), 0.0001d);
    assertEquals("Mbps", result.scaledUnit());
    assertEquals(0.25d, result.scaledValue(), 0.0001d);
    assertEquals("0.25 Mbps", result.displayValue());
  }
}
