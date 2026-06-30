import type { DashboardWidget } from '../../models';

/** Число колонок сетки дашборда (единицы width/gridX с бэкенда). */
export const DASHBOARD_GRID_COLUMNS = 12;

export type PlacedDashboardWidget = {
  widget: DashboardWidget;
  /** 1-based для CSS grid-column */
  colStart: number;
  /** 1-based для CSS grid-row */
  rowStart: number;
  colSpan: number;
  rowSpan: number;
};

function isAutoGrid(widget: DashboardWidget): boolean {
  return widget.gridX === 0 && widget.gridY === 0;
}

function cellKey(row: number, col: number): string {
  return `${row},${col}`;
}

/**
 * Сначала фиксируются виджеты с координатами ≠ (0,0), затем остальные — по порядку sortOrder,
 * строка за строкой слева направо, без пересечения занятых ячеек.
 */
export function buildDashboardWidgetPlacement(widgets: DashboardWidget[]): PlacedDashboardWidget[] {
  const sorted = [...widgets].sort((a, b) => a.sortOrder - b.sortOrder);
  const explicit = sorted.filter((w) => !isAutoGrid(w));
  const auto = sorted.filter((w) => isAutoGrid(w));

  const occupied = new Set<string>();
  const placed: PlacedDashboardWidget[] = [];

  const markRegion = (row: number, col: number, w: number, h: number) => {
    for (let dr = 0; dr < h; dr++) {
      for (let dc = 0; dc < w; dc++) {
        occupied.add(cellKey(row + dr, col + dc));
      }
    }
  };

  const canPlace = (row: number, col: number, w: number, h: number): boolean => {
    if (col < 0 || row < 0 || col + w > DASHBOARD_GRID_COLUMNS) {
      return false;
    }
    for (let dr = 0; dr < h; dr++) {
      for (let dc = 0; dc < w; dc++) {
        if (occupied.has(cellKey(row + dr, col + dc))) {
          return false;
        }
      }
    }
    return true;
  };

  for (const w of explicit) {
    let col = Math.max(0, w.gridX);
    const row = Math.max(0, w.gridY);
    let spanW = Math.max(1, w.width);
    const spanH = Math.max(1, w.height);
    if (col >= DASHBOARD_GRID_COLUMNS) {
      col = DASHBOARD_GRID_COLUMNS - 1;
    }
    spanW = Math.min(spanW, DASHBOARD_GRID_COLUMNS - col);
    markRegion(row, col, spanW, spanH);
    placed.push({
      widget: w,
      colStart: col + 1,
      rowStart: row + 1,
      colSpan: spanW,
      rowSpan: spanH,
    });
  }

  for (const w of auto) {
    const spanW = Math.min(Math.max(1, w.width), DASHBOARD_GRID_COLUMNS);
    const spanH = Math.max(1, w.height);
    let found = false;
    const maxScanRows = Math.max(occupied.size > 0 ? 80 : 40, sorted.length * 6);
    outer: for (let row = 0; row < maxScanRows; row++) {
      for (let col = 0; col <= DASHBOARD_GRID_COLUMNS - spanW; col++) {
        if (canPlace(row, col, spanW, spanH)) {
          markRegion(row, col, spanW, spanH);
          placed.push({
            widget: w,
            colStart: col + 1,
            rowStart: row + 1,
            colSpan: spanW,
            rowSpan: spanH,
          });
          found = true;
          break outer;
        }
      }
    }
    if (!found) {
      const row = maxScanRows;
      const col = 0;
      markRegion(row, col, 1, 1);
      placed.push({
        widget: w,
        colStart: col + 1,
        rowStart: row + 1,
        colSpan: 1,
        rowSpan: 1,
      });
    }
  }

  return placed;
}
