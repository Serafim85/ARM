import type { WidgetFieldRecord } from '../../../models';
import {
  DEFAULT_CHART_LEGEND_PLACEMENT,
  normalizeChartLegendPlacement,
  type ChartLegendPlacement,
} from '../../../utils/chart-legend-placement';

export type GraphWidgetPeriod = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH';

export const GRAPH_WIDGET_PERIOD_OPTIONS: { label: string; value: GraphWidgetPeriod }[] = [
  { label: 'Час', value: 'HOUR' },
  { label: 'День', value: 'DAY' },
  { label: 'Неделя', value: 'WEEK' },
  { label: 'Месяц', value: 'MONTH' },
];

export type GraphWidgetSeriesRef = {
  deviceId: number;
  metricName: string;
};

export type GraphWidgetConfig = {
  period: GraphWidgetPeriod;
  series: GraphWidgetSeriesRef[];
  showLegend: boolean;
  legendPlacement: ChartLegendPlacement;
  fill: boolean;
};

export function parseGraphWidgetFields(fields: WidgetFieldRecord[]): GraphWidgetConfig {
  const map = new Map(fields.map((x) => [x.name, x]));
  return {
    period: normalizePeriod(pickString(map, 'period', 'DAY')),
    series: parseSeries(pickString(map, 'series', '[]')),
    showLegend: pickBool(map, 'show_legend', true),
    legendPlacement: normalizeChartLegendPlacement(pickString(map, 'legend_placement', DEFAULT_CHART_LEGEND_PLACEMENT)),
    fill: pickBool(map, 'fill', false),
  };
}

export function resolvePeriodRange(
  period: GraphWidgetPeriod,
  now: Date
): { fromIso: string; toIso: string } {
  const from = new Date(now);
  switch (period) {
    case 'HOUR':
      from.setHours(from.getHours() - 1);
      break;
    case 'WEEK':
      from.setDate(from.getDate() - 7);
      break;
    case 'MONTH':
      from.setMonth(from.getMonth() - 1);
      break;
    case 'DAY':
    default:
      from.setDate(from.getDate() - 1);
      break;
  }
  return {
    fromIso: from.toISOString(),
    toIso: now.toISOString(),
  };
}

function normalizePeriod(raw: string): GraphWidgetPeriod {
  if (raw === 'HOUR' || raw === 'WEEK' || raw === 'MONTH' || raw === 'DAY') {
    return raw;
  }
  return 'DAY';
}

function parseSeries(rawJson: string): GraphWidgetSeriesRef[] {
  const text = rawJson.trim();
  if (!text) {
    return [];
  }
  try {
    const parsed = JSON.parse(text) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map((row) => {
        if (!row || typeof row !== 'object') {
          return null;
        }
        const deviceId = Number((row as { deviceId?: unknown }).deviceId);
        const metricName = String((row as { metricName?: unknown }).metricName ?? '').trim();
        if (!Number.isFinite(deviceId) || deviceId <= 0 || !metricName) {
          return null;
        }
        return { deviceId: Math.floor(deviceId), metricName };
      })
      .filter((row): row is GraphWidgetSeriesRef => row != null);
  } catch {
    return [];
  }
}

function pickBool(map: Map<string, WidgetFieldRecord>, name: string, fallback: boolean): boolean {
  const value = map.get(name);
  if (!value) return fallback;
  return value.valueInt === 1;
}

function pickString(map: Map<string, WidgetFieldRecord>, name: string, fallback: string): string {
  const value = map.get(name);
  if (!value || !value.valueStr) return fallback;
  return value.valueStr;
}
