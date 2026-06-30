import type { MonitoringEvent, MonitoringEventFilter, MonitoringEventLevel, WidgetFieldRecord } from '../../../models';
import { normalizeMonitoringEventLevel } from '../../../models';

export type ProblemsWidgetShowMode = 'RECENT' | 'PROBLEMS' | 'HISTORY';
export type ProblemsWidgetSortBy = 'TIME' | 'SEVERITY' | 'PROBLEM' | 'HOST';
export type ProblemsWidgetSortOrder = 'ASC' | 'DESC';

export type ProblemsWidgetConfig = {
  show: ProblemsWidgetShowMode;
  showLines: number;
  showTimeline: boolean;
  showSuppressed: boolean;
  highlightRow: boolean;
  sortBy: ProblemsWidgetSortBy;
  sortOrder: ProblemsWidgetSortOrder;
  problem: string;
  deviceIds: number[];
  deviceTags: string[];
  /** Legacy host_refs (имена), если device_ids ещё не сохранены. */
  legacyHostRefNames: string[];
};

const RECENT_WINDOW_MS = 14 * 24 * 60 * 60 * 1000;
const MAX_LINES = 200;

const SEVERITY_RANK: Record<MonitoringEventLevel, number> = {
  NOT_CLASSIFIED: 0,
  INFORMATION: 1,
  WARNING: 2,
  AVERAGE: 3,
  HIGH: 4,
  DISASTER: 5,
};

export function parseProblemsWidgetFields(fields: WidgetFieldRecord[]): ProblemsWidgetConfig {
  const map = new Map(fields.map((x) => [x.name, x]));
  return {
    show: normalizeShow(pickString(map, 'show', 'RECENT')),
    showLines: normalizeLines(pickInt(map, 'show_lines', 10)),
    showTimeline: pickBool(map, 'show_timeline', true),
    showSuppressed: pickBool(map, 'show_suppressed', false),
    highlightRow: pickBool(map, 'highlight_row', false),
    sortBy: normalizeSortBy(pickString(map, 'sort_by', 'TIME')),
    sortOrder: normalizeSortOrder(pickString(map, 'sort_order', 'DESC')),
    problem: pickString(map, 'problem', ''),
    deviceIds: parseDeviceIdsField(map),
    deviceTags: parseDeviceTagsField(map),
    legacyHostRefNames: parseLegacyHostRefNames(map),
  };
}

/**
 * Фильтр для GET /api/monitoring/events.
 * deviceIds и deviceTags применяются вместе (AND), если заданы оба.
 */
export function problemsWidgetUsesLegacyHostRefNames(config: ProblemsWidgetConfig): boolean {
  return config.deviceIds.length === 0 && config.legacyHostRefNames.length > 0;
}

export function problemsWidgetToMonitoringEventFilter(
  config: ProblemsWidgetConfig,
  now: Date,
  options?: { legacyHostName?: string | null }
): MonitoringEventFilter {
  const legacyName = options?.legacyHostName?.trim() || null;
  const base: MonitoringEventFilter = {
    status: null,
    thresholdLevel: null,
    breachStartedFrom: null,
    breachStartedTo: null,
    normalizedFrom: null,
    normalizedTo: null,
    minDurationSeconds: null,
    maxDurationSeconds: null,
    metricNameContains: config.problem.trim() || null,
    macAddressContains: null,
    deviceNameContains: legacyName,
    deviceIds: config.deviceIds.length > 0 ? config.deviceIds : null,
    deviceTags: config.deviceTags.length > 0 ? config.deviceTags : null,
  };

  switch (config.show) {
    case 'PROBLEMS':
      return { ...base, status: 'OPEN' };
    case 'HISTORY':
      return { ...base, status: 'RESOLVED' };
    case 'RECENT':
    default: {
      const from = new Date(now.getTime() - RECENT_WINDOW_MS);
      return {
        ...base,
        breachStartedFrom: from.toISOString(),
      };
    }
  }
}

/** Клиентская сортировка строк виджета (сервер отдаёт только breach_started_at DESC). */
export function sortProblemsWidgetEvents(
  events: MonitoringEvent[],
  sortBy: ProblemsWidgetSortBy,
  sortOrder: ProblemsWidgetSortOrder
): MonitoringEvent[] {
  const mult = sortOrder === 'ASC' ? 1 : -1;
  const rows = [...events];
  rows.sort((a, b) => mult * compareForSort(a, b, sortBy));
  return rows;
}

function compareForSort(a: MonitoringEvent, b: MonitoringEvent, sortBy: ProblemsWidgetSortBy): number {
  switch (sortBy) {
    case 'SEVERITY':
      return (
        SEVERITY_RANK[normalizeMonitoringEventLevel(a.thresholdLevel)] -
        SEVERITY_RANK[normalizeMonitoringEventLevel(b.thresholdLevel)]
      );
    case 'PROBLEM':
      return compareStrings(metricSortKey(a), metricSortKey(b));
    case 'HOST':
      return compareStrings(a.deviceName?.toLowerCase() ?? '', b.deviceName?.toLowerCase() ?? '');
    case 'TIME':
    default:
      return compareStrings(a.breachStartedAt ?? '', b.breachStartedAt ?? '');
  }
}

function metricSortKey(e: MonitoringEvent): string {
  const m = e.metricName?.toLowerCase() ?? '';
  const d = e.metricDisplayName?.toLowerCase() ?? '';
  return `${m}\u0000${d}`;
}

function compareStrings(va: string, vb: string): number {
  return va.localeCompare(vb, undefined, { numeric: true, sensitivity: 'base' });
}

function parseDeviceIdsField(map: Map<string, WidgetFieldRecord>): number[] {
  return parseJsonNumberArray(pickString(map, 'device_ids', ''));
}

function parseLegacyHostRefNames(map: Map<string, WidgetFieldRecord>): string[] {
  if (parseDeviceIdsField(map).length > 0) {
    return [];
  }
  return parseJsonStringArray(pickString(map, 'host_refs', ''));
}

function parseDeviceTagsField(map: Map<string, WidgetFieldRecord>): string[] {
  const fromNew = parseJsonStringArray(pickString(map, 'device_tags', ''));
  if (fromNew.length > 0) {
    return fromNew;
  }
  return parseLegacyTagFilters(pickString(map, 'tag_filters', ''));
}

function parseJsonNumberArray(raw: string): number[] {
  const t = raw.trim();
  if (!t) return [];
  try {
    const v = JSON.parse(t) as unknown;
    if (!Array.isArray(v)) return [];
    return v
      .map((item) => Number(item))
      .filter((id) => Number.isFinite(id) && id > 0)
      .map((id) => Math.trunc(id));
  } catch {
    return [];
  }
}

function parseJsonStringArray(raw: string): string[] {
  const t = raw.trim();
  if (!t) return [];
  try {
    const v = JSON.parse(t) as unknown;
    if (!Array.isArray(v)) return [];
    return v
      .map((item) => (typeof item === 'string' ? item.trim() : ''))
      .filter((item) => item.length > 0);
  } catch {
    return [];
  }
}

/** Legacy Zabbix-style tag_filters: [{"tag":"service",...}] */
function parseLegacyTagFilters(raw: string): string[] {
  const t = raw.trim();
  if (!t) return [];
  try {
    const v = JSON.parse(t) as unknown;
    if (!Array.isArray(v)) return [];
    const tags: string[] = [];
    for (const row of v) {
      if (!row || typeof row !== 'object') continue;
      const tag = String((row as { tag?: unknown }).tag ?? '').trim();
      if (tag) tags.push(tag);
    }
    return [...new Set(tags)];
  } catch {
    return [];
  }
}

/** Legacy host_refs (имена) → id, если известны устройства. */
export function migrateLegacyHostRefsToDeviceIds(
  hostRefsJson: string,
  devices: Array<{ id: number; name: string; hostName?: string | null }>
): number[] {
  const names = parseJsonStringArray(hostRefsJson);
  if (names.length === 0) return [];
  const ids: number[] = [];
  for (const ref of names) {
    const refLower = ref.toLowerCase();
    const match = devices.find((d) => {
      const name = d.name?.trim().toLowerCase() ?? '';
      const hostName = d.hostName?.trim().toLowerCase() ?? '';
      return name === refLower || hostName === refLower || name.includes(refLower);
    });
    if (match) ids.push(match.id);
  }
  return [...new Set(ids)];
}

function normalizeLines(value: number | null | undefined): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return 10;
  }
  return Math.min(MAX_LINES, Math.max(1, Math.floor(value)));
}

function normalizeShow(v: string): ProblemsWidgetShowMode {
  if (v === 'PROBLEMS' || v === 'HISTORY' || v === 'RECENT') return v;
  return 'RECENT';
}

function normalizeSortBy(v: string): ProblemsWidgetSortBy {
  if (v === 'TIME' || v === 'SEVERITY' || v === 'PROBLEM' || v === 'HOST') return v;
  return 'TIME';
}

function normalizeSortOrder(v: string): ProblemsWidgetSortOrder {
  return v === 'ASC' ? 'ASC' : 'DESC';
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

function pickInt(map: Map<string, WidgetFieldRecord>, name: string, fallback: number): number {
  const value = map.get(name);
  if (!value) return fallback;
  return normalizeLines(value.valueInt);
}
