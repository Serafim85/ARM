import { computeSeriesStats } from './chart-series-stats.util';

describe('chart-series-stats.util', () => {
  it('computes min, max, avg and last for finite values', () => {
    const stats = computeSeriesStats([10, 20, 30]);
    expect(stats).toEqual({ min: 10, max: 30, avg: 20, last: 30 });
  });

  it('returns null stats for empty input', () => {
    expect(computeSeriesStats([])).toEqual({ min: null, max: null, avg: null, last: null });
  });
});
