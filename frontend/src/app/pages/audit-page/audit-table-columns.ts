import type { TableColumnWidthDef } from '../../utils/table-column-widths';

export type AuditTableColumnId =
  | 'occurredAt'
  | 'actorLogin'
  | 'category'
  | 'action'
  | 'target'
  | 'details';

export const AUDIT_TABLE_COLUMNS: TableColumnWidthDef[] = [
  { id: 'occurredAt', label: 'Время', minWidth: 140, maxWidth: 240 },
  { id: 'actorLogin', label: 'Пользователь', minWidth: 120, maxWidth: 240 },
  { id: 'category', label: 'Раздел', minWidth: 120, maxWidth: 240 },
  { id: 'action', label: 'Действие', minWidth: 96, maxWidth: 180 },
  { id: 'target', label: 'Объект', minWidth: 140, maxWidth: 360 },
  { id: 'details', label: 'Подробности', minWidth: 160, maxWidth: 480 },
];

export const AUDIT_COLUMN_ORDER: AuditTableColumnId[] = AUDIT_TABLE_COLUMNS.map(
  (c) => c.id as AuditTableColumnId
);
