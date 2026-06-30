export const TABLE_COLUMN_WIDTH_KEYS = {
  devices: 'devices',
  events: 'events',
  templates: 'templates',
  audit: 'audit',
  users: 'users',
} as const;

export type TableColumnWidthTableKey =
  (typeof TABLE_COLUMN_WIDTH_KEYS)[keyof typeof TABLE_COLUMN_WIDTH_KEYS];

export type TableColumnWidthsMap = Record<string, number>;

export type TableColumnWidthBounds = {
  minWidth: number;
  maxWidth: number;
};

export type TableColumnWidthDef = {
  id: string;
  label: string;
  minWidth?: number;
  maxWidth?: number;
  /** false — без pResizableColumn (фиксированная колонка). */
  resizable?: boolean;
};

export const DEFAULT_TABLE_COLUMN_MIN_WIDTH = 48;
export const DEFAULT_TABLE_COLUMN_MAX_WIDTH = 640;

export function clampTableColumnWidth(
  width: number,
  bounds?: Partial<TableColumnWidthBounds>
): number {
  const min = bounds?.minWidth ?? DEFAULT_TABLE_COLUMN_MIN_WIDTH;
  const max = bounds?.maxWidth ?? DEFAULT_TABLE_COLUMN_MAX_WIDTH;
  if (!Number.isFinite(width)) {
    return min;
  }
  return Math.min(max, Math.max(min, Math.round(width)));
}

export function buildColumnBoundsMap(
  columns: TableColumnWidthDef[]
): Record<string, TableColumnWidthBounds> {
  const map: Record<string, TableColumnWidthBounds> = {};
  for (const col of columns) {
    map[col.id] = {
      minWidth: col.minWidth ?? DEFAULT_TABLE_COLUMN_MIN_WIDTH,
      maxWidth: col.maxWidth ?? DEFAULT_TABLE_COLUMN_MAX_WIDTH,
    };
  }
  return map;
}

export function normalizeStoredColumnWidths(
  raw: Record<string, number> | null | undefined,
  boundsById: Record<string, TableColumnWidthBounds>
): TableColumnWidthsMap {
  if (!raw) {
    return {};
  }
  const next: TableColumnWidthsMap = {};
  for (const [id, width] of Object.entries(raw)) {
    if (!boundsById[id]) {
      continue;
    }
    next[id] = clampTableColumnWidth(width, boundsById[id]);
  }
  return next;
}

export function readTableHeaderWidths(
  tableHost: HTMLElement,
  columnIds: string[]
): TableColumnWidthsMap {
  const headers = tableHost.querySelectorAll<HTMLElement>('thead tr:first-child th');
  const widths: TableColumnWidthsMap = {};
  columnIds.forEach((id, index) => {
    const th = headers.item(index);
    if (!th) {
      return;
    }
    const rect = th.getBoundingClientRect();
    if (rect.width > 0) {
      widths[id] = Math.round(rect.width);
    }
  });
  return widths;
}

/**
 * Применяет ширины к колонкам. Задаём ТОЛЬКО `width` (в auto-layout это «предпочтительная»
 * ширина), не трогая min-width/max-width: их выставляет [ngStyle] реальными границами,
 * и именно min-width читает PrimeNG, ограничивая сжатие при перетаскивании.
 */
export function applyTableColumnWidths(
  tableHost: HTMLElement,
  columnIds: string[],
  widths: TableColumnWidthsMap,
  boundsById: Record<string, TableColumnWidthBounds>
): void {
  const headers = tableHost.querySelectorAll<HTMLElement>('thead tr:first-child th');
  columnIds.forEach((id, index) => {
    const width = widths[id];
    const th = headers.item(index);
    if (!th) {
      return;
    }
    if (width == null) {
      th.style.width = '';
      return;
    }
    th.style.width = `${clampTableColumnWidth(width, boundsById[id])}px`;
  });
}

export function clearTableColumnWidths(tableHost: HTMLElement): void {
  const headers = tableHost.querySelectorAll<HTMLElement>('thead tr:first-child th');
  headers.forEach((th) => {
    th.style.width = '';
    th.style.removeProperty('max-width');
  });
  const cells = tableHost.querySelectorAll<HTMLElement>('tbody td');
  cells.forEach((td) => {
    td.style.width = '';
    td.style.removeProperty('max-width');
  });
}

/** Задаёт ширину колонки по индексу (th + td) с !important — для принудительного стопора после ресайза. */
export function applyColumnWidthAtIndex(
  tableHost: HTMLElement,
  colIndex: number,
  widthPx: number
): void {
  const px = `${widthPx}px`;
  const th = tableHost.querySelectorAll<HTMLElement>('thead tr:first-child th').item(colIndex);
  if (th) {
    th.style.setProperty('width', px, 'important');
    th.style.setProperty('max-width', px, 'important');
  }
  tableHost.querySelectorAll<HTMLElement>('tbody tr').forEach((row) => {
    const td = row.children.item(colIndex) as HTMLElement | null;
    if (td) {
      td.style.setProperty('width', px, 'important');
      td.style.setProperty('max-width', px, 'important');
    }
  });
}

export function columnWidthStyle(
  id: string,
  widths: TableColumnWidthsMap,
  boundsById: Record<string, TableColumnWidthBounds>
): Record<string, string> | null {
  const width = widths[id];
  if (width == null) {
    return null;
  }
  const clamped = clampTableColumnWidth(width, boundsById[id]);
  const px = `${clamped}px`;
  return { width: px, minWidth: px, maxWidth: px };
}

export function columnBoundsStyle(
  boundsById: Record<string, TableColumnWidthBounds>,
  id: string
): Record<string, string> {
  const bounds = boundsById[id];
  if (!bounds) {
    return {};
  }
  return {
    minWidth: `${bounds.minWidth}px`,
    maxWidth: `${bounds.maxWidth}px`,
  };
}
