import {
  cloneDefaultMonitoringEventsColumns,
  DEFAULT_MONITORING_EVENTS_COLUMNS,
  type MonitoringEventsColumnDef,
  type MonitoringEventsColumnId,
  type MonitoringEventsColumnPreferenceItem,
} from './monitoring-events-table-columns';

const KNOWN_COLUMN_IDS = new Set<MonitoringEventsColumnId>(
  DEFAULT_MONITORING_EVENTS_COLUMNS.map((c) => c.id)
);

export function toMonitoringEventsColumnPreference(
  columns: MonitoringEventsColumnDef[]
): MonitoringEventsColumnPreferenceItem[] {
  return columns.map((c) => ({ id: c.id, visible: c.visible }));
}

export function applyMonitoringEventsColumnPreference(
  preference: MonitoringEventsColumnPreferenceItem[] | null | undefined
): MonitoringEventsColumnDef[] {
  const defaults = cloneDefaultMonitoringEventsColumns();
  if (!preference?.length) {
    return defaults;
  }

  const defaultById = new Map(defaults.map((c) => [c.id, c]));
  const merged: MonitoringEventsColumnDef[] = [];
  const seen = new Set<MonitoringEventsColumnId>();

  for (const item of preference) {
    if (!item?.id || !KNOWN_COLUMN_IDS.has(item.id) || seen.has(item.id)) {
      continue;
    }
    const base = defaultById.get(item.id);
    if (!base) {
      continue;
    }
    merged.push({ ...base, visible: item.visible });
    seen.add(item.id);
  }

  for (const col of defaults) {
    if (!seen.has(col.id)) {
      merged.push({ ...col });
    }
  }

  if (merged.filter((c) => c.visible).length === 0) {
    return defaults;
  }

  return merged;
}

export function isKnownMonitoringEventsColumnId(id: string): id is MonitoringEventsColumnId {
  return KNOWN_COLUMN_IDS.has(id as MonitoringEventsColumnId);
}
