export const DEVICE_CARD_TAB_SEGMENTS = [
  'info',
  'configuration',
  'metrics',
  'snapshot',
  'item-config',
  'events',
  'config-management',
] as const;

export type DeviceCardTabSegment = (typeof DEVICE_CARD_TAB_SEGMENTS)[number];

export function isDeviceCardTabSegment(v: string): v is DeviceCardTabSegment {
  return (DEVICE_CARD_TAB_SEGMENTS as readonly string[]).includes(v);
}
