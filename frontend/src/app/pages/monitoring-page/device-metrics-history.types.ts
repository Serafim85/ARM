/** Ответ `GET /api/monitoring/devices/{id}/metrics` — компактный формат для графиков. */

/** Порог триггера для линии на графике или колонки в таблице снимка. */
export type MetricChartThresholdDto = {
  metricName: string;
  instanceKey: string;
  triggerName?: string | null;
  triggerUuid?: string | null;
  thresholdLevel: string;
  thresholdValue: number;
  scaledThresholdValue?: number | null;
  operator: string;
  /** Динамический порог (формула от метрик, например 90% × speed). */
  dynamic?: boolean;
  valueMapMappings?: Record<string, string> | null;
  /** Ряд порога для графика (epoch millis + значения). */
  t?: number[];
  v?: number[];
  sv?: number[];
};

/**
 * Ряд графика в компактном виде: метаданные один раз + параллельные массивы точек.
 * `t` — метки времени (epoch millis), `v` — сырые значения, `sv` — масштабированные (если есть).
 */
export type DeviceMetricsHistorySeriesDto = {
  metricName: string;
  /** Человекочитаемое имя из шаблона мониторинга, если бэкенд передал. */
  displayName?: string | null;
  unit?: string | null;
  scaledUnit?: string | null;
  valueMapName?: string | null;
  valueMapMappings?: Record<string, string> | null;
  t: number[];
  v: number[];
  sv?: number[] | null;
};

export type DeviceMetricsHistoryChartPanelDto = {
  panelKey: string;
  title: string;
  graphType: string;
  metricNames: string[];
  rightAxisMetricNames: string[];
  /** Ряды этой панели в компактном формате. */
  series?: DeviceMetricsHistorySeriesDto[];
  thresholds?: MetricChartThresholdDto[];
};

export type DeviceMetricsHistoryResponseDto = {
  chartPanels: DeviceMetricsHistoryChartPanelDto[];
  /** Полное число панелей до среза (для `panelsOffset` / `panelsLimit`). */
  totalChartPanels: number;
};
