package com.networkscanner.backend.monitoring.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class UnitScalingService {

  ScalingResult scaleSingle(Double value, String rawUnit) {
    if (value == null) {
      return new ScalingResult(null, normalizeDisplayUnit(rawUnit), null);
    }
    if (!Double.isFinite(value)) {
      return new ScalingResult(null, normalizeDisplayUnit(rawUnit), "—");
    }
    SeriesScale scale = resolveScale(rawUnit, Math.abs(value));
    if (scale == null) {
      String unit = normalizeDisplayUnit(rawUnit);
      return new ScalingResult(value, unit, formatDisplay(value, unit));
    }
    double scaled = value / scale.factor();
    return new ScalingResult(scaled, scale.unit(), formatDisplay(scaled, scale.unit()));
  }

  SeriesScale resolveSeriesScale(String rawUnit, Double maxAbsValue) {
    double maxAbs =
        maxAbsValue == null || !Double.isFinite(maxAbsValue) ? 0d : Math.max(0d, maxAbsValue);
    return resolveScale(rawUnit, maxAbs);
  }

  ScalingResult applySeriesScale(Double value, String rawUnit, SeriesScale scale) {
    if (value == null) {
      return new ScalingResult(null, scale == null ? normalizeDisplayUnit(rawUnit) : scale.unit(), null);
    }
    if (!Double.isFinite(value)) {
      String unit = scale == null ? normalizeDisplayUnit(rawUnit) : scale.unit();
      return new ScalingResult(null, unit, "—");
    }
    if (scale == null) {
      String unit = normalizeDisplayUnit(rawUnit);
      return new ScalingResult(value, unit, formatDisplay(value, unit));
    }
    double scaled = value / scale.factor();
    return new ScalingResult(scaled, scale.unit(), formatDisplay(scaled, scale.unit()));
  }

  private SeriesScale resolveScale(String rawUnit, double maxAbsValue) {
    UnitScaleCatalog catalog = UnitScaleCatalog.resolve(rawUnit);
    if (catalog == null) {
      return null;
    }
    int currentUnitIndex = resolveCurrentUnitIndex(catalog, rawUnit);
    if (currentUnitIndex < 0) {
      return null;
    }
    int target = currentUnitIndex;
    double normalized = Math.max(0d, maxAbsValue);
    while (target < catalog.units().length - 1 && normalized >= catalog.factorToNext(target)) {
      normalized = normalized / catalog.factorToNext(target);
      target++;
    }
    while (target > 0 && normalized > 0d && normalized < 1d) {
      normalized = normalized * catalog.factorToNext(target - 1);
      target--;
    }
    double factor = computeFactor(catalog, currentUnitIndex, target);
    return new SeriesScale(factor, catalog.units()[target]);
  }

  private int resolveCurrentUnitIndex(UnitScaleCatalog catalog, String rawUnit) {
    String canonical = UnitScaleCatalog.canonicalUnit(rawUnit);
    if (canonical == null || canonical.isBlank()) {
      return -1;
    }
    return catalog.indexOf(canonical);
  }

  private double computeFactor(UnitScaleCatalog catalog, int fromIndex, int targetIndex) {
    if (fromIndex == targetIndex) {
      return 1d;
    }
    double factor = 1d;
    if (targetIndex > fromIndex) {
      for (int i = fromIndex; i < targetIndex; i++) {
        factor *= catalog.factorToNext(i);
      }
      return factor;
    }
    for (int i = targetIndex; i < fromIndex; i++) {
      factor /= catalog.factorToNext(i);
    }
    return factor;
  }

  private String normalizeDisplayUnit(String rawUnit) {
    String canonical = UnitScaleCatalog.canonicalUnit(rawUnit);
    return canonical != null ? canonical : (rawUnit == null || rawUnit.isBlank() ? null : rawUnit.trim());
  }

  private String formatDisplay(Double value, String unit) {
    if (value == null) {
      return "—";
    }
    String text = formatNumber(value);
    return unit == null || unit.isBlank() ? text : text + " " + unit;
  }

  private String formatNumber(Double value) {
    if (value == null) {
      return "—";
    }
    if (!Double.isFinite(value)) {
      return "—";
    }
    if (Math.abs(value - Math.rint(value)) < 0.000001d) {
      return String.valueOf(value.longValue());
    }
    BigDecimal rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
    symbols.setDecimalSeparator('.');
    DecimalFormat format = new DecimalFormat("0.##", symbols);
    return format.format(rounded);
  }

  record SeriesScale(double factor, String unit) {
  }

  record ScalingResult(Double scaledValue, String scaledUnit, String displayValue) {
  }
}
