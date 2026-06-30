/** Интервал «Час» для истории метрик. */
export function defaultHourMetricsRange(): { fromIso: string; toIso: string } {
  const to = new Date();
  const from = new Date(to);
  from.setHours(from.getHours() - 1);
  return { fromIso: from.toISOString(), toIso: to.toISOString() };
}

/** Интервал «День» для истории метрик (как на вкладке графиков по умолчанию). */
export function defaultDayMetricsRange(): { fromIso: string; toIso: string } {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - 1);
  return { fromIso: from.toISOString(), toIso: to.toISOString() };
}
