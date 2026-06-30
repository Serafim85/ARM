import { DeviceScanResult } from '../models';

const GENERIC_PROBE_RESULTS = new Set([
  'ICMP reachable host',
  'Обнаруженный хост',
]);

function isGenericProbeResult(value: string): boolean {
  if (GENERIC_PROBE_RESULTS.has(value)) {
    return true;
  }
  return / service on \d/.test(value);
}

/** Текст колонки «Название» (поле name / SNMP sysDescr), как на странице устройств. */
export function scanResultSummary(device: DeviceScanResult): string {
  const name = (device.name ?? '').trim();
  if (name && name !== '-' && !isGenericProbeResult(name)) {
    return name;
  }
  return '—';
}

/** Подпись чипа в колонке «Сканирование» без суффикса порта (HTTP:80 → HTTP). */
export function availabilityChipLabel(label: string): string {
  return label.replace(/:\d+$/, '');
}

export function buildDeviceSearchText(device: DeviceScanResult): string {
  return [
    device.hostName,
    device.name,
    scanResultSummary(device),
    device.serialNumber,
    device.ip,
    device.domainName,
    device.macAddress,
    device.vendor,
    device.model,
    device.firmwareVersion,
    device.pollingStatus,
    device.status,
    device.group,
    ...(device.availability ?? []).map((a) => a.label),
  ]
    .filter((v) => v && v !== '-')
    .join(' ')
    .toLowerCase();
}
