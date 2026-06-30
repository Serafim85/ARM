import type { ChartLegendPlacement } from './chart-legend-placement';

/** Минимальная высота области построения (px). */
export const CHART_PLOT_MIN_HEIGHT_PX = 340;

const LEGEND_TABLE_HEADER_PX = 24;
const LEGEND_TABLE_ROW_PX = 23;
const LEGEND_LAYOUT_GAP_PX = 8;

export function estimateLegendBlockHeightPx(rowCount: number): number {
  if (rowCount <= 0) {
    return 0;
  }
  return LEGEND_TABLE_HEADER_PX + rowCount * LEGEND_TABLE_ROW_PX;
}

export function resolveChartPanelMinHeightPx(
  legendRowCount: number,
  placement: ChartLegendPlacement,
  hasLegend: boolean
): number {
  if (!hasLegend || legendRowCount <= 0) {
    return CHART_PLOT_MIN_HEIGHT_PX;
  }

  const legendHeight = estimateLegendBlockHeightPx(legendRowCount);
  if (placement === 'TOP' || placement === 'BOTTOM') {
    return CHART_PLOT_MIN_HEIGHT_PX + legendHeight + LEGEND_LAYOUT_GAP_PX;
  }
  return Math.max(CHART_PLOT_MIN_HEIGHT_PX, legendHeight);
}
