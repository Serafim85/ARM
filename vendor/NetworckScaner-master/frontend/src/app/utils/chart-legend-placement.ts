import { DEFAULT_CHART_BASE_COLOR, normalizeChartBaseColor, type ChartBaseColor } from './chart-colors';

export type ChartLegendPlacement = 'TOP' | 'BOTTOM' | 'LEFT' | 'RIGHT';

export const DEFAULT_CHART_LEGEND_PLACEMENT: ChartLegendPlacement = 'BOTTOM';
export const CHART_LEGEND_PLACEMENT_OPTIONS: {
  label: string;
  value: ChartLegendPlacement;
}[] = [
  { label: 'Сверху', value: 'TOP' },
  { label: 'Справа', value: 'RIGHT' },
  { label: 'Снизу', value: 'BOTTOM' },
  { label: 'Слева', value: 'LEFT' },
];

export type DeviceMetricsPeriod = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'CUSTOM';
export type DeviceMetricsLayout = 'SINGLE' | 'DOUBLE';

export const DEFAULT_DEVICE_METRICS_PERIOD: DeviceMetricsPeriod = 'DAY';
export const DEFAULT_DEVICE_METRICS_LAYOUT: DeviceMetricsLayout = 'DOUBLE';

export type ChartUiPreferences = {
  deviceMetricsLegendPlacement: ChartLegendPlacement;
  deviceMetricsBaseColor: ChartBaseColor;
  dashboardGraphLegendPlacements: Record<string, ChartLegendPlacement>;
  deviceMetricsPeriod: DeviceMetricsPeriod;
  deviceMetricsLayout: DeviceMetricsLayout;
  deviceMetricsCustomFrom: string | null;
  deviceMetricsCustomTo: string | null;
};

export function defaultChartUiPreferences(): ChartUiPreferences {
  return {
    deviceMetricsLegendPlacement: DEFAULT_CHART_LEGEND_PLACEMENT,
    deviceMetricsBaseColor: DEFAULT_CHART_BASE_COLOR,
    dashboardGraphLegendPlacements: {},
    deviceMetricsPeriod: DEFAULT_DEVICE_METRICS_PERIOD,
    deviceMetricsLayout: DEFAULT_DEVICE_METRICS_LAYOUT,
    deviceMetricsCustomFrom: null,
    deviceMetricsCustomTo: null,
  };
}

export function normalizeDeviceMetricsPeriod(value: unknown): DeviceMetricsPeriod {
  if (value === 'HOUR' || value === 'DAY' || value === 'WEEK' || value === 'MONTH' || value === 'CUSTOM') {
    return value;
  }
  return DEFAULT_DEVICE_METRICS_PERIOD;
}

export function normalizeDeviceMetricsLayout(value: unknown): DeviceMetricsLayout {
  if (value === 'SINGLE' || value === 'DOUBLE') {
    return value;
  }
  return DEFAULT_DEVICE_METRICS_LAYOUT;
}

export function normalizeDeviceMetricsCustomDate(value: unknown): string | null {
  if (typeof value !== 'string' || !value.trim()) {
    return null;
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim());
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
    return null;
  }
  const parsed = new Date(year, month - 1, day);
  if (
    parsed.getFullYear() !== year ||
    parsed.getMonth() !== month - 1 ||
    parsed.getDate() !== day
  ) {
    return null;
  }
  return `${match[1]}-${match[2]}-${match[3]}`;
}

export function normalizeChartLegendPlacement(value: unknown): ChartLegendPlacement {
  if (value === 'TOP' || value === 'BOTTOM' || value === 'LEFT' || value === 'RIGHT') {
    return value;
  }
  return DEFAULT_CHART_LEGEND_PLACEMENT;
}

export function mergeChartUiPreferences(
  raw: Partial<ChartUiPreferences> | null | undefined
): ChartUiPreferences {
  const defaults = defaultChartUiPreferences();
  if (!raw) {
    return defaults;
  }
  const dashboardGraphLegendPlacements: Record<string, ChartLegendPlacement> = {
    ...defaults.dashboardGraphLegendPlacements,
  };
  if (raw.dashboardGraphLegendPlacements) {
    for (const [widgetId, placement] of Object.entries(raw.dashboardGraphLegendPlacements)) {
      const key = widgetId.trim();
      if (!key) {
        continue;
      }
      dashboardGraphLegendPlacements[key] = normalizeChartLegendPlacement(placement);
    }
  }
  return {
    deviceMetricsLegendPlacement: normalizeChartLegendPlacement(raw.deviceMetricsLegendPlacement),
    deviceMetricsBaseColor: normalizeChartBaseColor(raw.deviceMetricsBaseColor),
    dashboardGraphLegendPlacements,
    deviceMetricsPeriod: normalizeDeviceMetricsPeriod(raw.deviceMetricsPeriod),
    deviceMetricsLayout: normalizeDeviceMetricsLayout(raw.deviceMetricsLayout),
    deviceMetricsCustomFrom: normalizeDeviceMetricsCustomDate(raw.deviceMetricsCustomFrom),
    deviceMetricsCustomTo: normalizeDeviceMetricsCustomDate(raw.deviceMetricsCustomTo),
  };
}

export function legendPanelBeforeChart(placement: ChartLegendPlacement): boolean {
  return placement === 'TOP' || placement === 'LEFT';
}

export function chartLegendPlacementClass(placement: ChartLegendPlacement): string {
  return `chart-legend-layout--${placement.toLowerCase()}`;
}
