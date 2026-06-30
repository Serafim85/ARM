import {
  CHART_SERIES_BASE_HEX,
  chartSeriesAreaGradient,
  chartSeriesLineColorsByMax,
  chartSeriesStylesByMax,
} from './chart-colors';

describe('chart-colors', () => {
  it('assigns darker color to series with higher max value', () => {
    const colors = chartSeriesLineColorsByMax([10, 50, 30]);
    expect(colors[1]).not.toEqual(colors[0]);
    expect(colors[1]).not.toEqual(colors[2]);
    expect(lightness(colors[1]!)).toBeLessThan(lightness(colors[0]!));
    expect(lightness(colors[1]!)).toBeLessThan(lightness(colors[2]!));
  });

  it('spreads shades evenly even when max values are close', () => {
    const colors = chartSeriesLineColorsByMax([20, 21, 20.5]);
    expect(new Set(colors).size).toBe(3);
  });

  it('keeps distinct shades when max values are equal', () => {
    const colors = chartSeriesLineColorsByMax([20, 20]);
    expect(colors[0]).not.toEqual(colors[1]);
    expect(lightness(colors[0]!)).toBeLessThan(lightness(CHART_SERIES_BASE_HEX));
  });

  it('builds per-line vertical area gradient with multiple stops', () => {
    const styles = chartSeriesStylesByMax([10, 50]);
    expect(styles[0]?.area.colorStops.length).toBe(3);
    expect(styles[1]?.area.colorStops.length).toBe(3);
    expect(styles[0]?.area).not.toEqual(styles[1]?.area);
  });

  it('builds vertical area gradient from line color', () => {
    const gradient = chartSeriesAreaGradient('#2563eb');
    expect(gradient.type).toBe('linear');
    expect(gradient.colorStops[0]?.color).toContain('rgba(37, 99, 235');
    expect(gradient.colorStops[2]?.color).toContain('rgba(37, 99, 235');
  });
});

function lightness(hex: string): number {
  const normalized = hex.replace('#', '');
  const r = parseInt(normalized.slice(0, 2), 16) / 255;
  const g = parseInt(normalized.slice(2, 4), 16) / 255;
  const b = parseInt(normalized.slice(4, 6), 16) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  return (max + min) / 2;
}
