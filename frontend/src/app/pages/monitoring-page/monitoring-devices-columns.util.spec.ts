import {
  applyMonitoringDevicesColumnPreference,
  toMonitoringDevicesColumnPreference,
} from './monitoring-devices-columns.util';
import { cloneDefaultMonitoringDevicesColumns } from './monitoring-devices-table-columns';

describe('monitoring-devices-columns.util', () => {
  it('returns defaults when preference is empty', () => {
    expect(applyMonitoringDevicesColumnPreference(null)).toEqual(cloneDefaultMonitoringDevicesColumns());
    expect(applyMonitoringDevicesColumnPreference([])).toEqual(cloneDefaultMonitoringDevicesColumns());
  });

  it('applies order and visibility from preference', () => {
    const pref = [
      { id: 'name' as const, visible: true },
      { id: 'hostName' as const, visible: false },
      { id: 'deviceParams' as const, visible: true },
      { id: 'series' as const, visible: true },
      { id: 'model' as const, visible: true },
      { id: 'firmwareVersion' as const, visible: true },
      { id: 'availability' as const, visible: true },
      { id: 'protocol' as const, visible: true },
      { id: 'healthStatus' as const, visible: true },
      { id: 'tags' as const, visible: true },
      { id: 'actions' as const, visible: true },
    ];
    const result = applyMonitoringDevicesColumnPreference(pref);
    expect(result.map((c) => c.id)).toEqual(pref.map((c) => c.id));
    expect(result.find((c) => c.id === 'hostName')?.visible).toBe(false);
  });

  it('falls back to defaults when all columns hidden', () => {
    const pref = cloneDefaultMonitoringDevicesColumns().map((c) => ({ id: c.id, visible: false }));
    expect(applyMonitoringDevicesColumnPreference(pref)).toEqual(cloneDefaultMonitoringDevicesColumns());
  });

  it('maps column defs to preference items', () => {
    const cols = cloneDefaultMonitoringDevicesColumns();
    expect(toMonitoringDevicesColumnPreference(cols)).toEqual(cols.map((c) => ({ id: c.id, visible: c.visible })));
  });
});
