export type MonitoringEventsColumnId =
  | 'breachStartedAt'
  | 'duration'
  | 'thresholdLevel'
  | 'status'
  | 'deviceHostName'
  | 'deviceName'
  | 'metricName'
  | 'thresholdValue'
  | 'actualValue';

export type MonitoringEventsColumnPreferenceItem = {
  id: MonitoringEventsColumnId;
  visible: boolean;
};

export type MonitoringEventsColumnsPreference = {
  columns: MonitoringEventsColumnPreferenceItem[] | null;
};

export type MonitoringEventsColumnDef = {
  id: MonitoringEventsColumnId;
  label: string;
  sortField: string;
  visible: boolean;
  headerClass?: string;
  bodyClass?: string;
  minWidth?: number;
  maxWidth?: number;
  resizable?: boolean;
};

export const DEFAULT_MONITORING_EVENTS_COLUMNS: MonitoringEventsColumnDef[] = [
  { id: 'breachStartedAt', label: 'Начало', sortField: 'breachStartedAt', visible: true, minWidth: 120, maxWidth: 220 },
  { id: 'duration', label: 'Продолжительность', sortField: 'normalizedAt', visible: true, minWidth: 120, maxWidth: 240 },
  {
    id: 'thresholdLevel',
    label: 'Критичность',
    sortField: 'thresholdLevel',
    visible: true,
    headerClass: 'events-col-tag',
    bodyClass: 'events-tag-cell events-tag-cell-level',
    minWidth: 112,
    maxWidth: 180,
  },
  {
    id: 'status',
    label: 'Статус',
    sortField: 'status',
    visible: true,
    headerClass: 'events-col-tag',
    bodyClass: 'events-tag-cell events-tag-cell-status',
    minWidth: 104,
    maxWidth: 160,
  },
  {
    id: 'deviceHostName',
    label: 'Имя хоста',
    sortField: 'deviceHostName',
    visible: true,
    minWidth: 96,
    maxWidth: 220,
  },
  {
    id: 'deviceName',
    label: 'Устройство',
    sortField: 'deviceName',
    visible: true,
    bodyClass: 'events-node-cell',
    minWidth: 140,
    maxWidth: 320,
  },
  { id: 'metricName', label: 'Метрика', sortField: 'metricName', visible: true, minWidth: 120, maxWidth: 280 },
  {
    id: 'thresholdValue',
    label: 'Порог',
    sortField: 'thresholdValue',
    visible: true,
    headerClass: 'events-col-numeric',
    bodyClass: 'events-col-numeric',
    minWidth: 72,
    maxWidth: 140,
  },
  {
    id: 'actualValue',
    label: 'Значение',
    sortField: 'actualValue',
    visible: true,
    headerClass: 'events-col-numeric',
    bodyClass: 'events-col-numeric',
    minWidth: 72,
    maxWidth: 140,
  },
];

export function cloneDefaultMonitoringEventsColumns(): MonitoringEventsColumnDef[] {
  return DEFAULT_MONITORING_EVENTS_COLUMNS.map((c) => ({ ...c }));
}

export function monitoringEventsTableWidthDefs(
  displayed: MonitoringEventsColumnDef[]
): import('../../../utils/table-column-widths').TableColumnWidthDef[] {
  return displayed.map((c) => ({
    id: c.id,
    label: c.label,
    minWidth: c.minWidth,
    maxWidth: c.maxWidth,
    resizable: c.resizable,
  }));
}

export function monitoringEventsTableColumnOrder(displayed: MonitoringEventsColumnDef[]): string[] {
  return displayed.map((c) => c.id);
}
