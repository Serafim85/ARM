import type { EChartsType } from 'echarts/core';

type EChartsGridRectAccess = {
  getModel(): {
    getComponent(
      type: 'grid',
      index: number
    ): { coordinateSystem?: { getRect(): { x: number; y: number; width: number; height: number } } } | undefined;
  };
};

export function resolveChartPlotRect(
  chart: EChartsType
): { x: number; y: number; width: number; height: number } | null {
  if (chart.isDisposed()) {
    return null;
  }
  try {
    const gridModel = (chart as unknown as EChartsGridRectAccess).getModel().getComponent('grid', 0);
    const rect = gridModel?.coordinateSystem?.getRect();
    if (
      rect &&
      Number.isFinite(rect.x) &&
      Number.isFinite(rect.width) &&
      rect.width > 0
    ) {
      return {
        x: rect.x,
        y: rect.y,
        width: rect.width,
        height: rect.height,
      };
    }
  } catch {
    return null;
  }
  return null;
}

export function syncChartPlotAreaCssVars(container: HTMLElement, chart: EChartsType): void {
  const rect = resolveChartPlotRect(chart);
  if (!rect) {
    container.style.removeProperty('--chart-plot-offset-left');
    container.style.removeProperty('--chart-plot-width');
    return;
  }
  container.style.setProperty('--chart-plot-offset-left', `${Math.round(rect.x)}px`);
  container.style.setProperty('--chart-plot-width', `${Math.round(rect.width)}px`);
}

export function resolveChartPlotAreaHeight(chart: EChartsType): number | null {
  const rect = resolveChartPlotRect(chart);
  if (rect && Number.isFinite(rect.height) && rect.height > 0) {
    return Math.floor(rect.height);
  }
  return null;
}
