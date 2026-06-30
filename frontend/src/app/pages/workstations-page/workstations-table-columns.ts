export type WorkstationsTableColumn = {
  field: string;
  header: string;
  minWidth?: number;
  maxWidth?: number;
};

export const WORKSTATIONS_TABLE_COLUMNS: WorkstationsTableColumn[] = [
  { field: 'hostname', header: 'Имя хоста', minWidth: 140, maxWidth: 320 },
  { field: 'primaryIp', header: 'IP', minWidth: 110, maxWidth: 180 },
  { field: 'osType', header: 'ОС', minWidth: 90, maxWidth: 140 },
  { field: 'status', header: 'Статус', minWidth: 100, maxWidth: 140 },
  { field: 'agentVersion', header: 'Агент', minWidth: 100, maxWidth: 160 },
  { field: 'lastSeenAt', header: 'Последний контакт', minWidth: 160, maxWidth: 240 },
];

export const WORKSTATIONS_COLUMN_ORDER = WORKSTATIONS_TABLE_COLUMNS.map((column) => column.field);
