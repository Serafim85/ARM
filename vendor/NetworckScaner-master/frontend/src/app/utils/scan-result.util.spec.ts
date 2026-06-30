import { describe, expect, it } from 'vitest';
import { availabilityChipLabel, scanResultSummary } from './scan-result.util';
import { DeviceScanResult } from '../models';

function device(partial: Partial<DeviceScanResult>): DeviceScanResult {
  return {
    id: '1',
    hostName: '-',
    name: '-',
    serialNumber: '-',
    ip: '192.168.1.1',
    domainName: '-',
    macAddress: '-',
    vendor: '-',
    model: '-',
    firmwareVersion: '-',
    pollingStatus: '',
    status: 'Включено',
    group: '-',
    tags: [],
    availability: [],
    ...partial,
  };
}

describe('availabilityChipLabel', () => {
  it('strips trailing port suffix', () => {
    expect(availabilityChipLabel('HTTP:80')).toBe('HTTP');
    expect(availabilityChipLabel('HTTPS:443')).toBe('HTTPS');
  });

  it('keeps SNMP labels unchanged', () => {
    expect(availabilityChipLabel('SNMP v2c')).toBe('SNMP v2c');
    expect(availabilityChipLabel('ICMP')).toBe('ICMP');
  });
});

describe('scanResultSummary', () => {
  it('returns SNMP sysDescr when present', () => {
    expect(
      scanResultSummary(
        device({
          hostName: 'switch-1',
          name: 'Cisco IOS Software, C2960',
        })
      )
    ).toBe('Cisco IOS Software, C2960');
  });

  it('hides generic ICMP fallback text', () => {
    expect(
      scanResultSummary(
        device({
          name: 'ICMP reachable host',
        })
      )
    ).toBe('—');
  });
});
