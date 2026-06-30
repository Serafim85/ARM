import {
  buildThresholdLegendRows,
  buildThresholdLineSeries,
  formatThresholdConditionCompact,
  formatThresholdTriggerCaption,
  resolvePercentYAxisMax,
  thresholdsForMetric,
} from './metric-threshold.util';

describe('metric-threshold.util', () => {
  const icmpThresholds = [
    {
      metricName: 'icmppingloss',
      triggerName: 'High ICMP ping loss',
      thresholdLevel: 'WARNING',
      thresholdValue: 20,
      scaledThresholdValue: 20,
      operator: '>',
    },
    {
      metricName: 'icmppingloss',
      triggerName: 'High ICMP ping loss',
      thresholdLevel: 'WARNING',
      thresholdValue: 100,
      scaledThresholdValue: 100,
      operator: '<',
    },
  ];

  it('formats compact threshold condition', () => {
    expect(formatThresholdConditionCompact('>', 20, '%')).toBe('>20%');
    expect(formatThresholdConditionCompact('<', 10, 'Mbps')).toBe('<10 Mbps');
    expect(formatThresholdConditionCompact('>=', 5, '')).toBe('≥5');
  });

  it('formats trigger caption', () => {
    expect(formatThresholdTriggerCaption('Host: High ICMP ping loss', '>', 20, '%')).toBe(
      'Триггер: High ICMP ping loss, >20%',
    );
  });

  it('formats trigger caption with valuemap threshold value', () => {
    const mappings = { '0': 'not available', '1': 'available' };
    expect(formatThresholdTriggerCaption('Cisco IOS: No SNMP data collection', '=', 0, null, mappings)).toBe(
      'Триггер: No SNMP data collection, =not available',
    );
  });

  it('omits threshold legend when too many thresholds on panel', () => {
    const thresholds = [
      { metricName: 'if.type', thresholdLevel: 'WARNING', thresholdValue: 1, operator: '=', triggerName: 'T1' },
      { metricName: 'if.type', thresholdLevel: 'WARNING', thresholdValue: 2, operator: '=', triggerName: 'T2' },
      { metricName: 'if.type', thresholdLevel: 'WARNING', thresholdValue: 3, operator: '=', triggerName: 'T3' },
    ];
    expect(buildThresholdLegendRows(thresholds, {})).toEqual([]);
  });

  it('filters thresholds by metric name', () => {
    expect(thresholdsForMetric(icmpThresholds, 'icmppingloss')).toHaveLength(2);
    expect(thresholdsForMetric(icmpThresholds, 'cpu.util')).toHaveLength(0);
  });

  it('builds dynamic threshold line from backend series', () => {
    const dynamicThreshold = {
      metricName: 'net.if.in[ifHCInOctets.2]',
      triggerName: 'High bandwidth',
      thresholdLevel: 'WARNING',
      thresholdValue: 900,
      scaledThresholdValue: 900,
      operator: '>',
      dynamic: true,
      t: [1_700_000_000_000, 1_700_003_600_000],
      v: [900, 900],
      sv: [900, 900],
    };
    const series = buildThresholdLineSeries(
      [dynamicThreshold],
      [1_700_000_000_000, 1_700_086_400_000],
      'bps',
      0,
      'net.if.in[ifHCInOctets.2]',
    );

    expect(series).toHaveLength(1);
    expect(series[0]?.['data']).toEqual([
      [1_700_000_000_000, 900],
      [1_700_003_600_000, 900],
    ]);
  });

  it('builds dashed threshold line series across the time range', () => {
    const series = buildThresholdLineSeries(
      thresholdsForMetric(icmpThresholds, 'icmppingloss'),
      [1_700_000_000_000, 1_700_086_400_000],
      '%',
      0,
      'icmppingloss',
    );

    expect(series).toHaveLength(2);
    expect(series[0]?.['type']).toBe('line');
    expect(series[0]?.['lineStyle']).toEqual({ type: 'dashed', color: '#f59e0b', width: 2 });
    expect(series[0]?.['data']).toEqual([
      [1_700_000_000_000, 20],
      [1_700_086_400_000, 20],
    ]);
    expect((series[0]?.['label'] as { formatter: string }).formatter).toBe(
      'Триггер: High ICMP ping loss, >20%',
    );
  });

  it('keeps percent axis max at 100 when thresholds fit', () => {
    expect(resolvePercentYAxisMax('%', 100, icmpThresholds, 'icmppingloss')).toBe(100);
  });
});
