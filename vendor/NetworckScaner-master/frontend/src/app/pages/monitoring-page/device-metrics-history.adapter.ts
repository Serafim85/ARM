import type {
  DeviceMetricsHistoryChartPanelDto,
  DeviceMetricsHistorySeriesDto,
  MetricChartThresholdDto,
} from './device-metrics-history.types';

/** Точка ряда во внутреннем («развёрнутом») представлении графиков. */
export type ExpandedMetricPoint = {
  recordedAt: string;
  deviceIp: string;
  metricName: string;
  metricValue: number;
  unit: string;
  metricDisplayName?: string | null;
  scaledMetricValue?: number | null;
  scaledUnit?: string | null;
  scaledDisplayValue?: string | null;
  valueMapName?: string | null;
  valueMapMappings?: Record<string, string> | null;
};

/** Метаданные ряда в развёрнутой панели. */
export type ExpandedSeriesMeta = {
  metricName: string;
  displayName?: string | null;
  unit?: string | null;
  scaledUnit?: string | null;
  valueMapName?: string | null;
  valueMapMappings?: Record<string, string> | null;
};

/** Панель графика во внутреннем представлении (с развёрнутыми точками). */
export type ExpandedChartPanel = {
  panelKey: string;
  title: string;
  graphType: string;
  metricNames: string[];
  rightAxisMetricNames: string[];
  points: ExpandedMetricPoint[];
  seriesMeta: ExpandedSeriesMeta[];
  thresholds: MetricChartThresholdDto[];
};

/** Разворачивает компактный ряд (t[]/v[]/sv[]) в список точек. */
function expandSeries(series: DeviceMetricsHistorySeriesDto): ExpandedMetricPoint[] {
  const t = series.t ?? [];
  const v = series.v ?? [];
  const sv = series.sv ?? null;
  const unit = series.unit ?? '';
  const scaledUnit = series.scaledUnit ?? null;
  const displayName = series.displayName ?? null;
  const out: ExpandedMetricPoint[] = [];
  const size = Math.min(t.length, v.length);
  for (let i = 0; i < size; i++) {
    out.push({
      recordedAt: new Date(t[i]).toISOString(),
      deviceIp: '',
      metricName: series.metricName,
      metricValue: v[i],
      unit,
      metricDisplayName: displayName,
      scaledMetricValue: sv && i < sv.length ? sv[i] : null,
      scaledUnit,
      scaledDisplayValue: null,
      valueMapName: series.valueMapName ?? null,
      valueMapMappings: series.valueMapMappings ?? null,
    });
  }
  return out;
}

/** Преобразует компактные панели бэкенда во внутренние панели с точками. */
export function expandCompactPanels(
  panels: readonly DeviceMetricsHistoryChartPanelDto[],
): ExpandedChartPanel[] {
  return panels.map((panel) => {
    const points: ExpandedMetricPoint[] = [];
    const seriesMeta: ExpandedSeriesMeta[] = [];
    for (const series of panel.series ?? []) {
      seriesMeta.push({
        metricName: series.metricName,
        displayName: series.displayName ?? null,
        unit: series.unit ?? null,
        scaledUnit: series.scaledUnit ?? null,
        valueMapName: series.valueMapName ?? null,
        valueMapMappings: series.valueMapMappings ?? null,
      });
      points.push(...expandSeries(series));
    }
    return {
      panelKey: panel.panelKey,
      title: panel.title,
      graphType: panel.graphType,
      metricNames: panel.metricNames ?? [],
      rightAxisMetricNames: panel.rightAxisMetricNames ?? [],
      points,
      seriesMeta,
      thresholds: panel.thresholds ?? [],
    };
  });
}
