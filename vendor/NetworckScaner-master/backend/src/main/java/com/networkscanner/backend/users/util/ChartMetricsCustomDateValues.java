package com.networkscanner.backend.users.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class ChartMetricsCustomDateValues {

  private static final DateTimeFormatter ISO_LOCAL_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

  private ChartMetricsCustomDateValues() {
  }

  public static String normalizeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim(), ISO_LOCAL_DATE).format(ISO_LOCAL_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
