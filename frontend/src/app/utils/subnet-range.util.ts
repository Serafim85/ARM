const RANGE_PATTERN = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})-(\d{1,3})$/;
const CIDR_PATTERN = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\/(\d{1,2})$/;

export const SUBNET_FORMAT_ERROR =
  'Неверный формат подсети. Используйте 192.168.1.0-255 или 192.168.1.0/24';

export const SUBNET_SCOPE_ERROR =
  'Слишком большая подсеть. Допускается маска /24–/32 или диапазон последнего октета (например, 192.168.1.0-255).';

const MIN_CIDR_PREFIX = 24;
const MAX_CIDR_PREFIX = 32;

export type SubnetParseResult =
  | { ok: true; normalizedRange: string }
  | { ok: false; error: string };

function isValidOctet(value: number): boolean {
  return Number.isInteger(value) && value >= 0 && value <= 255;
}

function ipv4ToInt(octets: number[]): number {
  return ((octets[0] << 24) | (octets[1] << 16) | (octets[2] << 8) | octets[3]) >>> 0;
}

function intToIpv4(value: number): number[] {
  return [
    (value >>> 24) & 0xff,
    (value >>> 16) & 0xff,
    (value >>> 8) & 0xff,
    value & 0xff,
  ];
}

function cidrToLastOctetRange(ip: string, prefix: number): string | null {
  const octets = ip.split('.').map((part) => Number(part));
  if (!octets.every(isValidOctet)) {
    return null;
  }

  const ipInt = ipv4ToInt(octets);
  const mask = prefix === 0 ? 0 : (~0 << (32 - prefix)) >>> 0;
  const network = (ipInt & mask) >>> 0;
  const broadcast = (network | (~mask >>> 0)) >>> 0;
  const networkOctets = intToIpv4(network);
  const broadcastOctets = intToIpv4(broadcast);

  if (
    networkOctets[0] !== broadcastOctets[0] ||
    networkOctets[1] !== broadcastOctets[1] ||
    networkOctets[2] !== broadcastOctets[2]
  ) {
    return null;
  }

  return `${networkOctets[0]}.${networkOctets[1]}.${networkOctets[2]}.${networkOctets[3]}-${broadcastOctets[3]}`;
}

function parseRange(value: string): SubnetParseResult | null {
  const match = RANGE_PATTERN.exec(value);
  if (!match) {
    return null;
  }

  const baseOctets = [Number(match[1]), Number(match[2]), Number(match[3])];
  const start = Number(match[4]);
  const end = Number(match[5]);

  if (
    !baseOctets.every(isValidOctet) ||
    !isValidOctet(start) ||
    !isValidOctet(end) ||
    start > end
  ) {
    return { ok: false, error: SUBNET_FORMAT_ERROR };
  }

  const base = `${baseOctets[0]}.${baseOctets[1]}.${baseOctets[2]}`;
  return { ok: true, normalizedRange: `${base}.${start}-${end}` };
}

function parseCidr(value: string): SubnetParseResult | null {
  const match = CIDR_PATTERN.exec(value);
  if (!match) {
    return null;
  }

  const ip = `${match[1]}.${match[2]}.${match[3]}.${match[4]}`;
  const prefix = Number(match[5]);
  const octets = [Number(match[1]), Number(match[2]), Number(match[3]), Number(match[4])];

  if (!octets.every(isValidOctet) || !Number.isInteger(prefix) || prefix < 0 || prefix > 99) {
    return { ok: false, error: SUBNET_FORMAT_ERROR };
  }

  if (prefix < MIN_CIDR_PREFIX || prefix > MAX_CIDR_PREFIX) {
    return { ok: false, error: SUBNET_SCOPE_ERROR };
  }

  const normalizedRange = cidrToLastOctetRange(ip, prefix);
  if (!normalizedRange) {
    return { ok: false, error: SUBNET_SCOPE_ERROR };
  }

  return { ok: true, normalizedRange };
}

/** Разбирает диапазон `a.b.c.d-e` или CIDR `a.b.c.d/p` и возвращает внутренний диапазон последнего октета. */
export function parseSubnetInput(value: string): SubnetParseResult {
  const trimmed = value.trim();
  if (!trimmed) {
    return { ok: false, error: SUBNET_FORMAT_ERROR };
  }

  const rangeResult = parseRange(trimmed);
  if (rangeResult) {
    return rangeResult;
  }

  const cidrResult = parseCidr(trimmed);
  if (cidrResult) {
    return cidrResult;
  }

  return { ok: false, error: SUBNET_FORMAT_ERROR };
}
