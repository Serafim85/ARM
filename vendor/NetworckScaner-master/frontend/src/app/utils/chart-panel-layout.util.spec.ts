import { resolveChartPanelMinHeightPx } from './chart-panel-layout.util';

describe('chart-panel-layout.util', () => {
  it('returns plot minimum when legend is hidden', () => {
    expect(resolveChartPanelMinHeightPx(0, 'BOTTOM', false)).toBe(340);
  });

  it('adds legend height for top and bottom placement', () => {
    const height = resolveChartPanelMinHeightPx(6, 'BOTTOM', true);
    expect(height).toBeGreaterThan(340);
    expect(height).toBe(340 + 24 + 6 * 23 + 8);
  });

  it('uses max of plot and legend height for side placement', () => {
    expect(resolveChartPanelMinHeightPx(2, 'RIGHT', true)).toBe(340);
    expect(resolveChartPanelMinHeightPx(20, 'LEFT', true)).toBe(24 + 20 * 23);
  });
});
