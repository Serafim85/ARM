import type { MonitoringMetricsBatchSeries } from '../models';
import { chartSeriesColor, chartSeriesStylesByMax } from './chart-colors';
import { formatValueMapStatValue, isValueMapSeries } from './valuemap-chart.util';

export type ChartSeriesStatRow = {
  kind?: 'series';
  name: string;
  color: string;
  unit: string;
  min: number | null;
  max: number | null;
  avg: number | null;
  last: number | null;
  valueMapMappings?: Record<string, string>;
  statsMode?: 'full' | 'lastOnly';
};

export type ChartThresholdLegendRow = {
  kind: 'threshold';
  caption: string;
  color: string;
};

export type ChartLegendRow = ChartSeriesStatRow | ChartThresholdLegendRow;

export function isThresholdLegendRow(row: ChartLegendRow): row is ChartThresholdLegendRow {
  return row.kind === 'threshold';
}

export function computeSeriesStats(values: number[]): Pick<ChartSeriesStatRow, 'min' | 'max' | 'avg' | 'last'> {
  const finite = values.filter((value) => Number.isFinite(value));
  if (finite.length === 0) {
    return { min: null, max: null, avg: null, last: null };
  }
  let min = finite[0];
  let max = finite[0];
  let sum = 0;
  for (const value of finite) {
    if (value < min) {
      min = value;
    }
    if (value > max) {
      max = value;
    }
    sum += value;
  }
  return {
    min,
    max,
    avg: sum / finite.length,
    last: finite[finite.length - 1],
  };
}

export function buildStatRowsFromBatchSeries(
  rows: MonitoringMetricsBatchSeries[],
  resolveLabel: (row: MonitoringMetricsBatchSeries) => string
): ChartSeriesStatRow[] {
  const entries = rows.map((row) => {
    const mappings = row.valueMapMappings ?? undefined;
    const useValueMap = isValueMapSeries(mappings);
    const rawValues = row.v ?? [];
    const scaledValues = row.sv && row.sv.length === row.t.length ? row.sv : null;
    const values = useValueMap
      ? rawValues
      : scaledValues && scaledValues.length === row.t.length
        ? scaledValues
        : rawValues;
    const stats = computeSeriesStats(values);
    return { row, stats, mappings, useValueMap };
  });
  const lineColors = chartSeriesStylesByMax(entries.map((entry) => entry.stats.max)).map((style) => style.line);

    return entries.map((entry, index) => ({
    name: resolveLabel(entry.row),
    color: lineColors[index] ?? chartSeriesColor(index),
    unit: entry.useValueMap ? '' : (entry.row.scaledUnit ?? entry.row.unit ?? '').trim(),
    min: entry.useValueMap ? null : entry.stats.min,
    max: entry.useValueMap ? null : entry.stats.max,
    avg: entry.useValueMap ? null : entry.stats.avg,
    last: entry.stats.last,
    valueMapMappings: entry.mappings,
    statsMode: entry.useValueMap ? 'lastOnly' : 'full',
  }));
}

export function buildStatRowsFromTimeSeries(
  series: Array<{
    name: string;
    color: string;
    unit: string;
    data: Array<[string | number, number]>;
    valueMapMappings?: Record<string, string>;
  }>
): ChartSeriesStatRow[] {
  return series.map((entry) => {
    const values = entry.data
      .map((point) => point[1])
      .filter((value) => Number.isFinite(value));
    const stats = computeSeriesStats(values);
    const useValueMap = isValueMapSeries(entry.valueMapMappings);
    return {
      name: entry.name,
      color: entry.color,
      unit: useValueMap ? '' : entry.unit,
      min: useValueMap ? null : stats.min,
      max: useValueMap ? null : stats.max,
      avg: useValueMap ? null : stats.avg,
      last: stats.last,
      valueMapMappings: entry.valueMapMappings,
      statsMode: useValueMap ? 'lastOnly' : 'full',
    };
  });
}

export function buildStatRowsFromScalars(
  slices: Array<{ name: string; value: number; unit: string; valueMapMappings?: Record<string, string> }>,
  colors: string[]
): ChartSeriesStatRow[] {
  return slices.map((slice, index) => {
    const value = Number.isFinite(slice.value) ? slice.value : null;
    const useValueMap = isValueMapSeries(slice.valueMapMappings);
    return {
      name: slice.name,
      color: colors[index % colors.length],
      unit: useValueMap ? '' : slice.unit,
      min: useValueMap ? null : value,
      max: useValueMap ? null : value,
      avg: useValueMap ? null : value,
      last: value,
      valueMapMappings: slice.valueMapMappings,
      statsMode: useValueMap ? 'lastOnly' : 'full',
    };
  });
}

export function formatChartStatValue(
  value: number | null,
  unit = '',
  row?: Pick<ChartSeriesStatRow, 'statsMode' | 'valueMapMappings'>
): string {
  if (row?.statsMode === 'lastOnly' && isValueMapSeries(row.valueMapMappings)) {
    return formatValueMapStatValue(value, row.valueMapMappings!);
  }
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  const formatted = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
  return unit ? `${formatted} ${unit}` : formatted;
}

export function legendShowsAggregateStats(rows: ChartSeriesStatRow[]): boolean {
  return rows.some((row) => row.statsMode !== 'lastOnly');
}
