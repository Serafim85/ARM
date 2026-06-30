package com.networkscanner.backend.monitoring.impl;

import java.util.Locale;
import java.util.regex.Pattern;

enum UnitScaleCatalog {
  BYTES_IEC(
      new String[]{"B", "KB", "MB", "GB", "TB", "PB"},
      new double[]{1024d, 1024d, 1024d, 1024d, 1024d}
  ),
  BYTES_PER_SECOND(
      new String[]{"Bps", "KBps", "MBps", "GBps", "TBps", "PBps"},
      new double[]{1024d, 1024d, 1024d, 1024d, 1024d}
  ),
  BITS_PER_SECOND(
      new String[]{"bps", "Kbps", "Mbps", "Gbps", "Tbps", "Pbps"},
      new double[]{1000d, 1000d, 1000d, 1000d, 1000d}
  ),
  TIME_DURATION(
      new String[]{"ns", "us", "ms", "s", "min", "h", "d"},
      new double[]{1000d, 1000d, 1000d, 60d, 60d, 24d}
  );

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final int MAX_UNIT_LENGTH = 64;

  private final String[] units;
  private final double[] stepFactors;

  UnitScaleCatalog(String[] units, double[] stepFactors) {
    this.units = units;
    this.stepFactors = stepFactors;
  }

  String[] units() {
    return units;
  }

  double factorToNext(int index) {
    if (index < 0 || index >= stepFactors.length) {
      return 1d;
    }
    return stepFactors[index];
  }

  int indexOf(String unit) {
    if (unit == null || unit.isBlank()) {
      return -1;
    }
    String normalized = normalizeUnit(unit);
    for (int i = 0; i < units.length; i++) {
      if (units[i].equalsIgnoreCase(normalized)) {
        return i;
      }
    }
    return -1;
  }

  static UnitScaleCatalog resolve(String unit) {
    ResolvedUnit resolved = resolveUnit(unit);
    if (resolved == null) {
      return null;
    }
    return resolved.catalog();
  }

  static String canonicalUnit(String unit) {
    ResolvedUnit resolved = resolveUnit(unit);
    return resolved == null ? null : resolved.unit();
  }

  private static ResolvedUnit resolveUnit(String rawUnit) {
    if (rawUnit == null || rawUnit.isBlank()) {
      return null;
    }
    String raw = rawUnit.trim();
    String normalized = normalizeUnit(raw);
    if (normalized.isEmpty()) {
      return null;
    }

    // Byte units.
    if ("BYTE".equals(normalized) || "BYTES".equals(normalized)) {
      return new ResolvedUnit(BYTES_IEC, "B");
    }
    if ("B".equals(normalized) || "KB".equals(normalized) || "MB".equals(normalized)
        || "GB".equals(normalized) || "TB".equals(normalized) || "PB".equals(normalized)) {
      return new ResolvedUnit(BYTES_IEC, normalized);
    }

    // Byte-per-second units (Bps family).
    if (raw.equals("Bps") || normalized.equals("B/S") || normalized.equals("BYTES/S")) {
      return new ResolvedUnit(BYTES_PER_SECOND, "Bps");
    }
    if (raw.equals("KBps") || raw.equals("MBps") || raw.equals("GBps")
        || raw.equals("TBps") || raw.equals("PBps")) {
      return new ResolvedUnit(BYTES_PER_SECOND, raw);
    }

    // Bit-per-second units (explicit bit labels and lowercase bps family).
    if (raw.equals("bps") || normalized.equals("BIT/S") || normalized.equals("BITS/S")
        || normalized.equals("BITPS") || normalized.equals("BITPERSECOND")) {
      return new ResolvedUnit(BITS_PER_SECOND, "bps");
    }
    if (raw.equals("Kbps") || raw.equals("Mbps") || raw.equals("Gbps") || raw.equals("Tbps") || raw.equals("Pbps")) {
      return new ResolvedUnit(BITS_PER_SECOND, raw);
    }

    // Time duration units.
    if ("SEC".equals(normalized) || "SECOND".equals(normalized) || "SECONDS".equals(normalized) || "S".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "s");
    }
    if ("MS".equals(normalized) || "MSEC".equals(normalized) || "MILLISECOND".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "ms");
    }
    if ("US".equals(normalized) || "USEC".equals(normalized) || "MICROSECOND".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "us");
    }
    if ("NS".equals(normalized) || "NSEC".equals(normalized) || "NANOSECOND".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "ns");
    }
    if ("MIN".equals(normalized) || "M".equals(normalized) || "MINUTE".equals(normalized) || "MINUTES".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "min");
    }
    if ("H".equals(normalized) || "HR".equals(normalized) || "HOUR".equals(normalized) || "HOURS".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "h");
    }
    if ("D".equals(normalized) || "DAY".equals(normalized) || "DAYS".equals(normalized)) {
      return new ResolvedUnit(TIME_DURATION, "d");
    }

    return null;
  }

  static String normalizeUnit(String unit) {
    if (unit == null) {
      return "";
    }
    String trimmed = unit.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    if (trimmed.length() > MAX_UNIT_LENGTH) {
      trimmed = trimmed.substring(0, MAX_UNIT_LENGTH);
    }
    return WHITESPACE.matcher(trimmed).replaceAll("").toUpperCase(Locale.ROOT);
  }

  record ResolvedUnit(UnitScaleCatalog catalog, String unit) {
  }
}
