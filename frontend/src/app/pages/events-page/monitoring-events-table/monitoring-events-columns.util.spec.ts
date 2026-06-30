import {
  applyMonitoringEventsColumnPreference,
  toMonitoringEventsColumnPreference,
} from './monitoring-events-columns.util';
import { cloneDefaultMonitoringEventsColumns } from './monitoring-events-table-columns';

describe('monitoring-events-columns.util', () => {
  it('toMonitoringEventsColumnPreference maps visibility', () => {
    const cols = cloneDefaultMonitoringEventsColumns();
    cols[0] = { ...cols[0], visible: false };
    expect(toMonitoringEventsColumnPreference(cols)[0]).toEqual({
      id: 'breachStartedAt',
      visible: false,
    });
  });

  it('applyMonitoringEventsColumnPreference restores order and visibility', () => {
    const applied = applyMonitoringEventsColumnPreference([
      { id: 'metricName', visible: true },
      { id: 'deviceHostName', visible: false },
      { id: 'breachStartedAt', visible: true },
      { id: 'duration', visible: true },
      { id: 'thresholdLevel', visible: true },
      { id: 'status', visible: true },
      { id: 'deviceName', visible: true },
      { id: 'thresholdValue', visible: true },
      { id: 'actualValue', visible: true },
    ]);

    expect(applied.map((c) => c.id)).toEqual([
      'metricName',
      'deviceHostName',
      'breachStartedAt',
      'duration',
      'thresholdLevel',
      'status',
      'deviceName',
      'thresholdValue',
      'actualValue',
    ]);
    expect(applied.find((c) => c.id === 'deviceHostName')?.visible).toBe(false);
  });

  it('applyMonitoringEventsColumnPreference appends new default columns', () => {
    const applied = applyMonitoringEventsColumnPreference([
      { id: 'breachStartedAt', visible: true },
      { id: 'duration', visible: true },
      { id: 'thresholdLevel', visible: true },
      { id: 'status', visible: true },
      { id: 'deviceName', visible: true },
      { id: 'metricName', visible: true },
      { id: 'thresholdValue', visible: true },
      { id: 'actualValue', visible: true },
    ]);

    expect(applied.some((c) => c.id === 'deviceHostName')).toBe(true);
  });

  it('applyMonitoringEventsColumnPreference falls back when all hidden', () => {
    const applied = applyMonitoringEventsColumnPreference([
      { id: 'breachStartedAt', visible: false },
      { id: 'duration', visible: false },
      { id: 'thresholdLevel', visible: false },
      { id: 'status', visible: false },
      { id: 'deviceHostName', visible: false },
      { id: 'deviceName', visible: false },
      { id: 'metricName', visible: false },
      { id: 'thresholdValue', visible: false },
      { id: 'actualValue', visible: false },
    ]);

    expect(applied).toEqual(cloneDefaultMonitoringEventsColumns());
  });
});
