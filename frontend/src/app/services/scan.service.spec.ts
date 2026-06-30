import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { API_BASE_URL } from '../api-config';
import { NotifierService } from '../notifier.service';
import { ScanService } from './scan.service';

function seedAccessProfile(
  svc: ScanService,
  overrides: Partial<{
    id: number;
    snmpV1Enabled: boolean;
    snmpV2Enabled: boolean;
    snmpV3Enabled: boolean;
    sshEnabled: boolean;
    httpsEnabled: boolean;
  }> = {}
): void {
  svc.accessProfiles.set([
    {
      id: overrides.id ?? 42,
      name: 'lab',
      description: null,
      snmpV1Enabled: overrides.snmpV1Enabled ?? false,
      snmpV2Enabled: overrides.snmpV2Enabled ?? true,
      snmpV3Enabled: overrides.snmpV3Enabled ?? false,
      sshEnabled: overrides.sshEnabled ?? false,
      httpsEnabled: overrides.httpsEnabled ?? false,
    },
  ]);
}

describe('ScanService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ScanService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://localhost:8081' },
        {
          provide: NotifierService,
          useValue: { warn: vi.fn(), info: vi.fn(), success: vi.fn(), error: vi.fn() },
        },
      ],
    });
  });

  it('getMethodLabel returns Russian label for ICMP', () => {
    const svc = TestBed.inject(ScanService);
    expect(svc.getMethodLabel('ICMP')).toBe('ICMP ping');
  });

  it('resolveSubnetRange converts CIDR to internal range', () => {
    const svc = TestBed.inject(ScanService);
    svc.onSubnetRangeInput('192.168.1.0/24');
    expect(svc.resolveSubnetRange()).toBe('192.168.1.0-255');
  });

  it('resolveSubnetRange warns on invalid subnet', () => {
    const svc = TestBed.inject(ScanService);
    const notify = TestBed.inject(NotifierService);
    svc.onSubnetRangeInput('not-a-subnet');
    expect(svc.resolveSubnetRange()).toBeNull();
    expect(notify.warn).toHaveBeenCalled();
  });

  it('toggleProbe adds ICMP and clears results', () => {
    const svc = TestBed.inject(ScanService);
    svc.showResults([
      {
        id: '1',
        hostName: 'h',
        name: 'n',
        serialNumber: 's',
        ip: '10.0.0.1',
        domainName: '',
        macAddress: '',
        vendor: '',
        model: '',
        firmwareVersion: '',
        pollingStatus: '',
        status: '',
        group: '',
        tags: [],
        availability: [],
      },
    ]);
    expect(svc.scanResults().length).toBe(1);
    svc.toggleProbe('ICMP', true);
    expect(svc.isProbeSelected('ICMP')).toBe(true);
    expect(svc.scanResults().length).toBe(0);
  });

  it('currentScanRequest with profile omits inline snmp creds', () => {
    const svc = TestBed.inject(ScanService);
    svc.onSubnetRangeInput('192.168.1.0/24');
    seedAccessProfile(svc);
    svc.accessProfileId.set(42);
    const req = svc.currentScanRequest();
    expect(req.accessProfileId).toBe(42);
    expect(req.probes[0].community).toBeUndefined();
  });

  it('currentScanRequest with profile omits snmp port so backend uses profile port', () => {
    const svc = TestBed.inject(ScanService);
    svc.onSubnetRangeInput('192.168.1.0/24');
    seedAccessProfile(svc, { snmpV2Enabled: false, snmpV3Enabled: true });
    svc.accessProfileId.set(42);
    svc.setSelectedMethods(['SNMP_V3']);
    svc.updateProbe('SNMP_V3', { port: 9999 });
    const req = svc.currentScanRequest();
    expect(req.probes[0].port).toBeUndefined();
  });

  it('currentScanRequest keeps ssh port when profile has ssh disabled', () => {
    const svc = TestBed.inject(ScanService);
    svc.onSubnetRangeInput('192.168.1.0/24');
    seedAccessProfile(svc, { sshEnabled: false });
    svc.accessProfileId.set(42);
    svc.toggleProbe('SSH', true);
    const req = svc.currentScanRequest();
    const sshProbe = req.probes.find((probe) => probe.method === 'SSH');
    expect(sshProbe?.port).toBe(22);
  });

  it('currentScanRequest without profile includes inline snmp creds', () => {
    const svc = TestBed.inject(ScanService);
    svc.onSubnetRangeInput('192.168.1.0/24');
    svc.accessProfileId.set(null);
    svc.updateProbe('SNMP_V2', { community: 'private' });
    const req = svc.currentScanRequest();
    expect(req.accessProfileId).toBeUndefined();
    expect(req.probes[0].community).toBe('private');
  });

  it('canResetDiscoveryMethods is true only with more than one method', () => {
    const svc = TestBed.inject(ScanService);
    expect(svc.canResetDiscoveryMethods()).toBe(false);
    svc.toggleProbe('ICMP', true);
    expect(svc.canResetDiscoveryMethods()).toBe(true);
    svc.resetDiscoveryMethods();
    expect(svc.canResetDiscoveryMethods()).toBe(false);
  });

  it('resetDiscoveryMethods restores SNMP_V2 default', () => {
    const svc = TestBed.inject(ScanService);
    svc.toggleProbe('ICMP', true);
    svc.toggleProbe('SSH', true);
    expect(svc.selectedMethods().length).toBeGreaterThan(1);
    svc.resetDiscoveryMethods();
    expect(svc.selectedMethods()).toEqual(['SNMP_V2']);
  });

  it('setSelectedMethods preserves probe settings', () => {
    const svc = TestBed.inject(ScanService);
    svc.setSelectedMethods(['SNMP_V2', 'ICMP']);
    svc.updateProbe('SNMP_V2', { community: 'private' });
    svc.setSelectedMethods(['ICMP', 'SNMP_V2']);
    expect(svc.getProbe('SNMP_V2')?.community).toBe('private');
  });

  it('removeProbe syncs selectedMethods with multiselect', () => {
    const svc = TestBed.inject(ScanService);
    svc.setSelectedMethods(['SNMP_V2', 'ICMP', 'TCP']);
    svc.removeProbe('ICMP');
    expect(svc.selectedMethods()).toEqual(['SNMP_V2', 'TCP']);
  });

  it('removeProbe keeps at least one method', () => {
    const svc = TestBed.inject(ScanService);
    const notify = TestBed.inject(NotifierService);
    svc.setSelectedMethods(['ICMP']);
    svc.removeProbe('ICMP');
    expect(svc.selectedMethods()).toEqual(['ICMP']);
    expect(notify.warn).toHaveBeenCalled();
  });

  it('probesSummary lists method labels without ports', () => {
    const svc = TestBed.inject(ScanService);
    svc.setSelectedMethods(['ICMP', 'HTTP', 'SNMP_V2']);
    expect(svc.probesSummary()).toBe('ICMP ping, SNMPv2, HTTP');
  });

  it('loadProbesFromScanRequest converts legacy scanMode', () => {
    const svc = TestBed.inject(ScanService);
    svc.loadProbesFromScanRequest({
      subnetRange: '10.0.0.0/24',
      scanMode: 'ICMP',
      port: 1,
      timeout: 2000,
      retries: 2,
    });
    expect(svc.selectedProbes().length).toBe(1);
    expect(svc.selectedProbes()[0].method).toBe('ICMP');
    expect(svc.subnetRange()).toBe('10.0.0.0/24');
  });
});
