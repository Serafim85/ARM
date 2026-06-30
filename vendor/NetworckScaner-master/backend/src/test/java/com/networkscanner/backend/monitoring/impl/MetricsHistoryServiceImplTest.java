package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MetricsHistoryServiceImplTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-08T12:00:00Z");

  @Test
  void chartRawCutoffIsSevenDaysBeforeNow() {
    assertEquals(NOW.minusDays(7), MetricsHistoryServiceImpl.chartRawCutoff(NOW));
  }

  @Test
  void resolveHistoryTierUsesRawWhenBoundsMissing() {
    OffsetDateTime from = NOW.minusDays(3);
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.RAW, MetricsHistoryServiceImpl.resolveHistoryTier(from, null, NOW));
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.RAW, MetricsHistoryServiceImpl.resolveHistoryTier(null, from, NOW));
  }

  @Test
  void resolveHistoryTierUsesRawWhenRangeWithinSevenDays() {
    OffsetDateTime from = NOW.minusDays(6);
    OffsetDateTime to = NOW.minusHours(1);
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.RAW, MetricsHistoryServiceImpl.resolveHistoryTier(from, to, NOW));
  }

  @Test
  void resolveHistoryTierUsesHourlyWhenRangeEntirelyOlderThanSevenDays() {
    OffsetDateTime cutoff = MetricsHistoryServiceImpl.chartRawCutoff(NOW);
    OffsetDateTime from = cutoff.minusDays(30);
    OffsetDateTime to = cutoff.minusDays(1);
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.HOURLY, MetricsHistoryServiceImpl.resolveHistoryTier(from, to, NOW));
  }

  @Test
  void resolveHistoryTierUsesHourlyWhenToEqualsCutoff() {
    OffsetDateTime cutoff = MetricsHistoryServiceImpl.chartRawCutoff(NOW);
    OffsetDateTime from = cutoff.minusDays(14);
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.HOURLY, MetricsHistoryServiceImpl.resolveHistoryTier(from, cutoff, NOW));
  }

  @Test
  void resolveHistoryTierUsesHybridWhenRangeSpansCutoff() {
    OffsetDateTime cutoff = MetricsHistoryServiceImpl.chartRawCutoff(NOW);
    OffsetDateTime from = cutoff.minusDays(10);
    OffsetDateTime to = NOW.minusHours(1);
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.HYBRID, MetricsHistoryServiceImpl.resolveHistoryTier(from, to, NOW));
  }

  @Test
  void resolveHistoryTierUsesRawWhenFromOnCutoff() {
    OffsetDateTime cutoff = MetricsHistoryServiceImpl.chartRawCutoff(NOW);
    assertEquals(MetricsHistoryServiceImpl.HistoryTier.RAW, MetricsHistoryServiceImpl.resolveHistoryTier(cutoff, NOW, NOW));
  }
}
