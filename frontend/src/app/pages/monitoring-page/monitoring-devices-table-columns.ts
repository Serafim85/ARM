export type MonitoringDevicesColumnId =
  | 'hostName'
  | 'name'
  | 'deviceParams'
  | 'series'
  | 'model'
  | 'firmwareVersion'
  | 'availability'
  | 'protocol'
  | 'healthStatus'
  | 'tags'
  | 'actions';

export type MonitoringDevicesColumnPreferenceItem = {
  id: MonitoringDevicesColumnId;
  visible: boolean;
};

export type MonitoringDevicesColumnDef = {
  id: MonitoringDevicesColumnId;
  label: string;
  sortField?: string;
  visible: boolean;
  headerClass?: string;
  bodyClass?: string;
  minWidth?: number;
  maxWidth?: number;
  resizable?: boolean;
};

export const DEFAULT_MONITORING_DEVICES_COLUMNS: MonitoringDevicesColumnDef[] = [
  { id: 'hostName', label: 'Имя хоста', sortField: 'hostName', visible: true, minWidth: 96, maxWidth: 280 },
  { id: 'name', label: 'Название', sortField: 'name', visible: true, headerClass: 'col-name', bodyClass: 'col-name', minWidth: 96, maxWidth: 240 },
  {
    id: 'deviceParams',
    label: 'Параметры устройства',
    visible: true,
    headerClass: 'col-ident',
    bodyClass: 'col-ident',
    minWidth: 140,
    maxWidth: 320,
  },
  { id: 'series', label: 'Серия', visible: true, minWidth: 72, maxWidth: 180 },
  { id: 'model', label: 'Модель', sortField: 'model', visible: true, minWidth: 80, maxWidth: 200 },
  {
    id: 'firmwareVersion',
    label: 'Версия ПО',
    sortField: 'firmwareVersion',
    visible: true,
    headerClass: 'col-fw',
    bodyClass: 'monitoring-mono col-fw',
    minWidth: 88,
    maxWidth: 200,
  },
  { id: 'availability', label: 'Доступность', visible: true, minWidth: 120, maxWidth: 220 },
  {
    id: 'protocol',
    label: 'Протокол',
    sortField: 'status',
    visible: true,
    headerClass: 'col-protocol',
    bodyClass: 'col-protocol',
    minWidth: 88,
    maxWidth: 180,
  },
  {
    id: 'healthStatus',
    label: 'Состояние',
    sortField: 'healthStatus',
    visible: true,
    headerClass: 'col-health',
    bodyClass: 'monitoring-tag-cell col-health',
    minWidth: 96,
    maxWidth: 180,
  },
  {
    id: 'tags',
    label: 'Теги',
    visible: true,
    headerClass: 'monitoring-tags-col',
    minWidth: 120,
    maxWidth: 320,
  },
  {
    id: 'actions',
    label: 'Действия',
    visible: true,
    headerClass: 'monitoring-actions-col',
    bodyClass: 'monitoring-actions-cell',
    minWidth: 72,
    maxWidth: 72,
    resizable: false,
  },
];

export const MONITORING_DEVICES_SELECT_COLUMN_ID = '_select' as const;

export function cloneDefaultMonitoringDevicesColumns(): MonitoringDevicesColumnDef[] {
  return DEFAULT_MONITORING_DEVICES_COLUMNS.map((c) => ({ ...c }));
}

export function monitoringDevicesTableWidthDefs(
  displayed: MonitoringDevicesColumnDef[]
): import('../../utils/table-column-widths').TableColumnWidthDef[] {
  return [
    { id: MONITORING_DEVICES_SELECT_COLUMN_ID, label: '', minWidth: 48, maxWidth: 72 },
    ...displayed.map((c) => ({
      id: c.id,
      label: c.label,
      minWidth: c.minWidth,
      maxWidth: c.maxWidth,
      resizable: c.resizable,
    })),
  ];
}

export function monitoringDevicesTableColumnOrder(displayed: MonitoringDevicesColumnDef[]): string[] {
  return [MONITORING_DEVICES_SELECT_COLUMN_ID, ...displayed.map((c) => c.id)];
}
