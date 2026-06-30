import type { TableColumnWidthDef } from '../../utils/table-column-widths';

export type MonitoringTemplatesColumnId =
  | '_select'
  | 'id'
  | 'name'
  | 'source'
  | 'uploadedBy'
  | 'vendor'
  | 'versions'
  | 'priority'
  | 'actions';

export const MONITORING_TEMPLATES_TABLE_COLUMNS: TableColumnWidthDef[] = [
  { id: '_select', label: '', minWidth: 48, maxWidth: 56, resizable: false },
  { id: 'id', label: 'ID', minWidth: 88, maxWidth: 200 },
  { id: 'name', label: 'Название', minWidth: 140, maxWidth: 360 },
  { id: 'source', label: 'Источник', minWidth: 96, maxWidth: 160 },
  { id: 'uploadedBy', label: 'Имя пользователя', minWidth: 120, maxWidth: 240 },
  { id: 'vendor', label: 'Вендор / модель', minWidth: 120, maxWidth: 280 },
  { id: 'versions', label: 'Версии', minWidth: 120, maxWidth: 260 },
  { id: 'priority', label: 'Приоритет', minWidth: 72, maxWidth: 120 },
  { id: 'actions', label: 'Действия', minWidth: 72, maxWidth: 72, resizable: false },
];

export const MONITORING_TEMPLATES_COLUMN_ORDER: MonitoringTemplatesColumnId[] =
  MONITORING_TEMPLATES_TABLE_COLUMNS.map((c) => c.id as MonitoringTemplatesColumnId);

export function monitoringTemplatesResizableColumn(id: MonitoringTemplatesColumnId): boolean {
  const col = MONITORING_TEMPLATES_TABLE_COLUMNS.find((c) => c.id === id);
  return col?.resizable !== false;
}
