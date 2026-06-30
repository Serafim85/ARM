/**
 * Опции для выбора IANA time zone (как в Zabbix «Time zone» для часов).
 * Предпочтительно полный список из Intl.supportedValuesOf('timeZone'); иначе короткий запасной список.
 */
export function buildTimeZoneSelectOptions(): { label: string; value: string }[] {
  const empty = { label: '— (не задано)', value: '' };
  let ids: string[] = [];
  try {
    const fn = (Intl as unknown as { supportedValuesOf?: (key: string) => string[] }).supportedValuesOf;
    if (typeof fn === 'function') {
      ids = fn.call(Intl, 'timeZone') ?? [];
    }
  } catch {
    /* ignore */
  }
  if (!ids.length) {
    ids = [...FALLBACK_TIMEZONES];
  }
  const sorted = [...new Set(ids)].sort((a, b) => a.localeCompare(b, 'en'));
  return [empty, ...sorted.map((z) => ({ label: z, value: z }))];
}

/** Если окружение без supportedValuesOf('timeZone'). */
const FALLBACK_TIMEZONES: string[] = [
  'UTC',
  'Etc/UTC',
  'Europe/Moscow',
  'Europe/Kaliningrad',
  'Europe/Kyiv',
  'Europe/Minsk',
  'Europe/Warsaw',
  'Europe/Berlin',
  'Europe/London',
  'Europe/Paris',
  'Asia/Yekaterinburg',
  'Asia/Novosibirsk',
  'Asia/Krasnoyarsk',
  'Asia/Irkutsk',
  'Asia/Vladivostok',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Singapore',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'Australia/Sydney',
];
