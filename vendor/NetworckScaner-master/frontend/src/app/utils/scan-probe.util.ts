import { DiscoveryMethod, DiscoveryProbeConfig } from '../models';

const DEFAULT_PORTS: Partial<Record<DiscoveryMethod, number>> = {
  FTP: 21,
  HTTP: 80,
  HTTPS: 443,
  IMAP: 143,
  LDAP: 389,
  NNTP: 119,
  POP: 110,
  SMTP: 25,
  SNMP_V1: 161,
  SNMP_V2: 161,
  SNMP_V3: 161,
  SSH: 22,
  TCP: 80,
  TELNET: 23,
};

export function defaultPortForMethod(method: DiscoveryMethod): number {
  return DEFAULT_PORTS[method] ?? 1;
}

export function probeUsesPort(method: DiscoveryMethod): boolean {
  return method !== 'ICMP' && method !== 'DNS';
}

export function isSnmpMethod(method: DiscoveryMethod): boolean {
  return method.startsWith('SNMP');
}

/** Методы, для которых при выбранном профиле порт и учётные данные берутся из профиля. */
export function probeUsesProfileSettings(method: DiscoveryMethod): boolean {
  return isSnmpMethod(method) || method === 'SSH' || method === 'HTTPS';
}

export function createDefaultProbe(method: DiscoveryMethod): DiscoveryProbeConfig {
  const probe: DiscoveryProbeConfig = { method };
  if (probeUsesPort(method)) {
    probe.port = defaultPortForMethod(method);
  }
  if (method === 'SNMP_V1' || method === 'SNMP_V2') {
    probe.community = 'public';
  }
  if (method === 'SNMP_V3') {
    probe.securityUsername = 'snmpuser';
    probe.authProtocol = 'SHA';
    probe.authPassword = 'authpass123';
    probe.privacyProtocol = 'AES';
    probe.privacyPassword = 'privpass123';
  }
  return probe;
}

export function legacyScanRequestToProbes(raw: Record<string, unknown>): DiscoveryProbeConfig[] {
  const scanMode = String(raw['scanMode'] ?? '').trim() as DiscoveryMethod;
  if (!scanMode) {
    const probes = raw['probes'];
    if (Array.isArray(probes) && probes.length > 0) {
      return probes as DiscoveryProbeConfig[];
    }
    return [createDefaultProbe('SNMP_V2')];
  }
  const probe = createDefaultProbe(scanMode);
  const port = Number(raw['port']);
  if (Number.isFinite(port) && port > 0) {
    probe.port = port;
  }
  if (typeof raw['community'] === 'string') {
    probe.community = raw['community'];
  }
  if (typeof raw['securityUsername'] === 'string') {
    probe.securityUsername = raw['securityUsername'];
  }
  if (typeof raw['authProtocol'] === 'string') {
    probe.authProtocol = raw['authProtocol'];
  }
  if (typeof raw['authPassword'] === 'string') {
    probe.authPassword = raw['authPassword'];
  }
  if (typeof raw['privacyProtocol'] === 'string') {
    probe.privacyProtocol = raw['privacyProtocol'];
  }
  if (typeof raw['privacyPassword'] === 'string') {
    probe.privacyPassword = raw['privacyPassword'];
  }
  return [probe];
}
