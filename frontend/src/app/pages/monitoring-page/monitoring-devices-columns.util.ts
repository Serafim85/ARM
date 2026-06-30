import {
  cloneDefaultMonitoringDevicesColumns,
  DEFAULT_MONITORING_DEVICES_COLUMNS,
  type MonitoringDevicesColumnDef,
  type MonitoringDevicesColumnId,
  type MonitoringDevicesColumnPreferenceItem,
} from './monitoring-devices-table-columns';

const KNOWN_COLUMN_IDS = new Set<MonitoringDevicesColumnId>(
  DEFAULT_MONITORING_DEVICES_COLUMNS.map((c) => c.id)
);

export function toMonitoringDevicesColumnPreference(
  columns: MonitoringDevicesColumnDef[]
): MonitoringDevicesColumnPreferenceItem[] {
  return columns.map((c) => ({ id: c.id, visible: c.visible }));
}

export function applyMonitoringDevicesColumnPreference(
  preference: MonitoringDevicesColumnPreferenceItem[] | null | undefined
): MonitoringDevicesColumnDef[] {
  const defaults = cloneDefaultMonitoringDevicesColumns();
  if (!preference?.length) {
    return defaults;
  }

  const defaultById = new Map(defaults.map((c) => [c.id, c]));
  const merged: MonitoringDevicesColumnDef[] = [];
  const seen = new Set<MonitoringDevicesColumnId>();

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
