import type { TableColumnWidthDef } from '../../utils/table-column-widths';

export type UsersTableColumnId =
  | 'displayName'
  | 'email'
  | 'roles'
  | 'createdAt'
  | 'status'
  | 'actions';

export const USERS_TABLE_COLUMNS: TableColumnWidthDef[] = [
  { id: 'displayName', label: 'Пользователь', minWidth: 140, maxWidth: 320 },
  { id: 'email', label: 'Email', minWidth: 160, maxWidth: 320 },
  { id: 'roles', label: 'Роли', minWidth: 160, maxWidth: 360 },
  { id: 'createdAt', label: 'Создан', minWidth: 120, maxWidth: 220 },
  { id: 'status', label: 'Статус', minWidth: 96, maxWidth: 160 },
  { id: 'actions', label: 'Действия', minWidth: 72, maxWidth: 72, resizable: false },
];

export const USERS_COLUMN_ORDER: UsersTableColumnId[] = USERS_TABLE_COLUMNS.map(
  (c) => c.id as UsersTableColumnId
);

export function usersTableResizableColumn(id: UsersTableColumnId): boolean {
  const col = USERS_TABLE_COLUMNS.find((c) => c.id === id);
  return col?.resizable !== false;
}
