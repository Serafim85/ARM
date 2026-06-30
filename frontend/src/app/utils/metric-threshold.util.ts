import {
  monitoringEventLevelLabel,
  normalizeMonitoringEventLevel,
  type MonitoringEventLevel,
} from '../models';
import type { ChartThresholdLegendRow } from './chart-series-stats.util';
import { isValueMapSeries, mapValueMapLabel, type ValueMapMappings } from './valuemap-chart.util';

/** Макс. число порогов на панели, при котором дублируем их в легенде и подписи на линии. */
export const THRESHOLD_CHART_ANNOTATION_MAX = 2;

export function shouldAnnotateThresholdsOnChart(thresholdCount: number): boolean {
  return thresholdCount > 0 && thresholdCount <= THRESHOLD_CHART_ANNOTATION_MAX;
}

/** Префикс служебных серий ECharts с линиями порогов (скрываются в тултипе). */
export const THRESHOLD_OVERLAY_SERIES_PREFIX = '\u200bthreshold:';

const THRESHOLD_LINE_COLORS: Record<MonitoringEventLevel, string> = {
  NOT_CLASSIFIED: '#94a3b8',
  INFORMATION: '#3b82f6',
  WARNING: '#f59e0b',
  AVERAGE: '#f97316',
  HIGH: '#ef4444',
  DISASTER: '#b91c1c',
};

export function thresholdLevelLineColor(level: string | null | undefined): string {
  const normalized = normalizeMonitoringEventLevel(level);
  return THRESHOLD_LINE_COLORS[normalized];
}

export function formatThresholdValue(value: number): string {
  return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
}

export function formatThresholdLabel(
  level: string | null | undefined,
  value: number,
  unit?: string | null
): string {
  const unitSuffix = unit?.trim() ? ` ${unit.trim()}` : '';
  return `${monitoringEventLevelLabel(level)}: ${formatThresholdValue(value)}${unitSuffix}`;
}

export type ThresholdForDisplay = {
  metricName: string;
  triggerName?: string | null;
  thresholdLevel: string;
  thresholdValue: number;
  scaledThresholdValue?: number | null;
  operator: string;
  dynamic?: boolean;
  t?: number[];
  v?: number[];
  sv?: number[];
  valueMapMappings?: ValueMapMappings | null;
};

export function shortenTriggerLegendName(name: string | null | undefined): string {
  const trimmed = (name ?? '').trim();
  if (!trimmed) {
    return 'Порог';
  }
  const parts = trimmed.split(':').map((part) => part.trim()).filter(Boolean);
  return parts.length >= 2 ? parts[parts.length - 1]! : trimmed;
}

export function formatThresholdOperatorSymbol(operator: string): string {
  switch (operator?.trim()) {
    case '>=':
      return '≥';
    case '<=':
      return '≤';
    case '<':
      return '<';
    case '>':
      return '>';
    case '=':
      return '=';
    case '<>':
      return '≠';
    default:
      return '>';
  }
}

/** Компактное условие: {@code >20%}, {@code <10}, {@code ≥5 Мбит/с}. */
export function formatThresholdConditionCompact(
  operator: string,
  value: number,
  unit?: string | null,
  valueMapMappings?: ValueMapMappings | null,
): string {
  const op = formatThresholdOperatorSymbol(operator);
  if (isValueMapSeries(valueMapMappings)) {
    return `${op}${mapValueMapLabel(valueMapMappings!, value)}`;
  }
  const num = formatThresholdValue(value);
  const u = (unit ?? '').trim();
  if (u === '%') {
    return `${op}${num}%`;
  }
  return u ? `${op}${num} ${u}` : `${op}${num}`;
}

/** Подпись порога: «Триггер: название, >20%». */
export function formatThresholdTriggerCaption(
  triggerName: string | null | undefined,
  operator: string,
  value: number,
  unit?: string | null,
  valueMapMappings?: ValueMapMappings | null,
): string {
  const name = shortenTriggerLegendName(triggerName);
  const condition = formatThresholdConditionCompact(operator, value, unit, valueMapMappings);
  return `Триггер: ${name}, ${condition}`;
}

function resolveThresholdValueMapMappings(
  threshold: ThresholdForDisplay,
  valueMapByMetric?: Record<string, ValueMapMappings>,
): ValueMapMappings | null | undefined {
  if (isValueMapSeries(threshold.valueMapMappings)) {
    return threshold.valueMapMappings;
  }
  return valueMapByMetric?.[threshold.metricName];
}

export function buildThresholdLegendRows(
  thresholds: ThresholdForDisplay[],
  unitByMetric: Record<string, string>,
  metricNames?: string[],
  valueMapByMetric?: Record<string, ValueMapMappings>,
): ChartThresholdLegendRow[] {
  const allowed = metricNames?.length ? new Set(metricNames) : null;
  const seen = new Set<string>();
  const rows: ChartThresholdLegendRow[] = [];
  for (const threshold of thresholds) {
    if (allowed && !allowed.has(threshold.metricName)) {
      continue;
    }
    const value = threshold.scaledThresholdValue ?? threshold.thresholdValue;
    if (!Number.isFinite(value)) {
      continue;
    }
    const unit = unitByMetric[threshold.metricName];
    const mappings = resolveThresholdValueMapMappings(threshold, valueMapByMetric);
    const caption = formatThresholdTriggerCaption(
      threshold.triggerName,
      threshold.operator,
      value,
      isValueMapSeries(mappings) ? null : unit,
      mappings,
    );
    if (seen.has(caption)) {
      continue;
    }
    seen.add(caption);
    rows.push({
      kind: 'threshold',
      caption,
      color: thresholdLevelLineColor(threshold.thresholdLevel),
    });
  }
  if (!shouldAnnotateThresholdsOnChart(rows.length)) {
    return [];
  }
  return rows;
}

export function isPercentChartUnit(unit: string | null | undefined): boolean {
  return (unit ?? '').trim() === '%';
}

export function thresholdsForMetric(
  thresholds: ThresholdForDisplay[],
  metricName: string,
): ThresholdForDisplay[] {
  return thresholds.filter((threshold) => threshold.metricName === metricName);
}

export function thresholdChartValue(threshold: ThresholdForDisplay): number {
  return threshold.scaledThresholdValue ?? threshold.thresholdValue;
}

export function resolvePercentYAxisMax(
  unit: string | null | undefined,
  dataMax: number,
  thresholds: ThresholdForDisplay[],
  metricName: string,
): number | undefined {
  if (!isPercentChartUnit(unit)) {
    return undefined;
  }
  const thresholdMax = thresholdsForMetric(thresholds, metricName)
    .map(thresholdChartValue)
    .filter((value) => Number.isFinite(value))
    .reduce((max, value) => Math.max(max, value), Number.NEGATIVE_INFINITY);
  const upper = Math.max(100, dataMax, thresholdMax);
  return upper > 100 ? Math.ceil(upper * 1.02) : 100;
}

/** Горизонтальные линии порогов как отдельные line-серии (надёжнее markLine при tree-shaking ECharts). */
function thresholdLineData(
  threshold: ThresholdForDisplay,
  timeExtent: [number, number],
): [number, number][] {
  if (threshold.dynamic && threshold.t?.length) {
    const values = threshold.sv?.length === threshold.t.length ? threshold.sv : threshold.v;
    if (!values || values.length !== threshold.t.length) {
      return [];
    }
    return threshold.t.map((time, index) => [time, values[index]!] as [number, number]);
  }
  const value = thresholdChartValue(threshold);
  const [xMin, xMax] = timeExtent;
  return [
    [xMin, value],
    [xMax, value],
  ];
}

export function buildThresholdLineSeries(
  thresholds: ThresholdForDisplay[],
  timeExtent: [number, number],
  unit: string | null | undefined,
  yAxisIndex: number,
  metricName: string,
  valueMapByMetric?: Record<string, ValueMapMappings>,
): Record<string, unknown>[] {
  const [xMin, xMax] = timeExtent;
  if (!Number.isFinite(xMin) || !Number.isFinite(xMax) || xMax < xMin || thresholds.length === 0) {
    return [];
  }

  return thresholds.flatMap((threshold, index) => {
    const data = thresholdLineData(threshold, timeExtent);
    if (data.length === 0) {
      return [];
    }
    const showCaption = shouldAnnotateThresholdsOnChart(thresholds.length);
    const labelValue = data[data.length - 1]?.[1] ?? thresholdChartValue(threshold);
    const color = thresholdLevelLineColor(threshold.thresholdLevel);
    const mappings = resolveThresholdValueMapMappings(threshold, valueMapByMetric);
    const caption = formatThresholdTriggerCaption(
      threshold.triggerName,
      threshold.operator,
      labelValue,
      isValueMapSeries(mappings) ? null : unit,
      mappings,
    );
    return [{
      name: `${THRESHOLD_OVERLAY_SERIES_PREFIX}${metricName}:${index}`,
      type: 'line',
      yAxisIndex,
      data,
      showSymbol: false,
      smooth: false,
      lineStyle: { type: 'dashed', color, width: 2 },
      itemStyle: { color },
      z: 100,
      zlevel: 10,
      silent: true,
      tooltip: { show: false },
      emphasis: { disabled: true },
      label: {
        show: showCaption,
        position: 'end',
        formatter: caption,
        color,
        fontSize: 11,
        fontWeight: 600,
        backgroundColor: 'rgba(255, 255, 255, 0.85)',
        padding: [2, 4],
        borderRadius: 2,
        distance: 4,
      },
    }];
  });
}

export function isThresholdBreached(
  actual: number | null | undefined,
  threshold: number,
  operator: string
): boolean {
  if (actual == null || !Number.isFinite(actual) || !Number.isFinite(threshold)) {
    return false;
  }
  switch (operator) {
    case '>':
      return actual > threshold;
    case '>=':
      return actual >= threshold;
    case '<':
      return actual < threshold;
    case '<=':
      return actual <= threshold;
    case '=':
      return actual === threshold;
    case '<>':
      return actual !== threshold;
    default:
      return false;
  }
}
