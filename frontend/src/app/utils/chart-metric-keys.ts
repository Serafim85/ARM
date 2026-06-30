/** Минимальная форма панели графика для извлечения ключей метрик. */
export type ChartPanelMetricKeySource = {
  metricNames?: string[];
  rightAxisMetricNames?: string[];
};

/** Все ключи метрик из панелей (как на вкладке «Графики метрик»). */
export function collectChartMetricKeys(panels: readonly ChartPanelMetricKeySource[]): string[] {
  const keys = new Set<string>();
  for (const panel of panels) {
    for (const name of panel.metricNames ?? []) {
      const key = name?.trim();
      if (key) keys.add(key);
    }
    for (const name of panel.rightAxisMetricNames ?? []) {
      const key = name?.trim();
      if (key) keys.add(key);
    }
  }
  return [...keys];
}
