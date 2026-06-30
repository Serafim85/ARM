import { describe, expect, it } from 'vitest';
import {
  parseSubnetInput,
  SUBNET_FORMAT_ERROR,
  SUBNET_SCOPE_ERROR,
} from './subnet-range.util';

describe('parseSubnetInput', () => {
  it('accepts last-octet range', () => {
    expect(parseSubnetInput('192.168.176.0-255')).toEqual({
      ok: true,
      normalizedRange: '192.168.176.0-255',
    });
  });

  it('converts /24 CIDR to internal range', () => {
    expect(parseSubnetInput('192.168.1.0/24')).toEqual({
      ok: true,
      normalizedRange: '192.168.1.0-255',
    });
  });

  it('converts /25 CIDR to internal range', () => {
    expect(parseSubnetInput('192.168.1.128/25')).toEqual({
      ok: true,
      normalizedRange: '192.168.1.128-255',
    });
  });

  it('rejects invalid formats', () => {
    expect(parseSubnetInput('not-a-subnet')).toEqual({
      ok: false,
      error: SUBNET_FORMAT_ERROR,
    });
    expect(parseSubnetInput('192.168.1.0/23')).toEqual({
      ok: false,
      error: SUBNET_SCOPE_ERROR,
    });
    expect(parseSubnetInput('10.0.0.0/20')).toEqual({
      ok: false,
      error: SUBNET_SCOPE_ERROR,
    });
  });
});
