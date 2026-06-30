/** Форматирование дат/длительности для таблицы событий мониторинга. */

export function formatMonitoringEventDate(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatMonitoringEventDuration(
  breachStartedAt: string | null,
  normalizedAt: string | null
): string {
  if (!breachStartedAt) return '—';
  const start = new Date(breachStartedAt).getTime();
  if (Number.isNaN(start)) return '—';

  const endMs =
    normalizedAt && !Number.isNaN(new Date(normalizedAt).getTime())
      ? new Date(normalizedAt).getTime()
      : Date.now();

  const diff = Math.max(0, endMs - start);
  return formatDurationMs(diff);
}

function formatDurationMs(ms: number): string {
  const totalMin = Math.floor(ms / 60000);
  const days = Math.floor(totalMin / (60 * 24));
  const hours = Math.floor((totalMin - days * 24 * 60) / 60);
  const mins = totalMin - days * 24 * 60 - hours * 60;

  if (days > 0) {
    return `${days} д ${hours} ч`;
  }
  if (hours > 0) {
    return `${hours} ч ${mins} мин`;
  }
  return `${Math.max(0, mins)} мин`;
}
