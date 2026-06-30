import type { TopologyDeviceHostAvailability } from '../../models';

export function deviceHostAvailabilityFromMonitoringStatus(
  status: string | null | undefined,
): TopologyDeviceHostAvailability | undefined {
  if (status == null || status === '') return undefined;
  if (status === 'Включено') return 'AVAILABLE';
  if (status === 'Недоступно') return 'UNAVAILABLE';
  return 'UNKNOWN';
}

/**
 * Обводка и фон карточки узла NODE: без привязки к устройству — нейтральные цвета как раньше;
 * с привязкой — по доступности (те же оттенки, что у классов host-availability-* в shared-ui).
 */
export function topologyLinkedNodeChrome(ele: {
  data: (k: string) => unknown;
}): { borderColor: string; backgroundColor: string } {
  const deviceId = ele.data('deviceId');
  const hasDevice =
    deviceId != null &&
    deviceId !== '' &&
    (typeof deviceId === 'number' ? Number.isFinite(deviceId) : false);
  if (!hasDevice) {
    return { borderColor: '#94a3b8', backgroundColor: '#f1f5f9' };
  }
  const raw = ele.data('deviceHostAvailability');
  const a = raw === 'AVAILABLE' || raw === 'UNAVAILABLE' || raw === 'UNKNOWN' ? raw : undefined;
  switch (a) {
    case 'AVAILABLE':
      return { borderColor: '#059669', backgroundColor: '#ecfdf5' };
    case 'UNAVAILABLE':
      return { borderColor: '#dc2626', backgroundColor: '#fef2f2' };
    case 'UNKNOWN':
      return { borderColor: '#94a3b8', backgroundColor: '#f8fafc' };
    default:
      return { borderColor: '#94a3b8', backgroundColor: '#f1f5f9' };
  }
}
