import type { WidgetFieldRecord } from '../../../models';

export type ClockTimeType = 'LOCAL' | 'SERVER' | 'HOST';
export type ClockDisplayType = 'DIGITAL' | 'ANALOG';
export type ClockTimeFormat = '24H' | '12H';
export type ClockTimeZoneNameFormat = 'SHORT' | 'FULL';

export type ClockWidgetConfig = {
  timeType: ClockTimeType;
  clockType: ClockDisplayType;
  showDate: boolean;
  showTime: boolean;
  showTimeZone: boolean;
  showSeconds: boolean;
  timeFormat: ClockTimeFormat;
  timeZone: string;
  timeZoneFormat: ClockTimeZoneNameFormat;
  backgroundColor: string;
};

export type ClockDisplayStrings = {
  dateLine: string | null;
  timeLine: string | null;
  zoneLine: string | null;
};

export type ClockAnalogAngles = {
  hourDeg: number;
  minuteDeg: number;
  secondDeg: number;
};

const DEFAULT_RESYNC_SECONDS = 120;

export function defaultServerResyncIntervalSeconds(refreshIntervalSeconds: number | null | undefined): number {
  if (refreshIntervalSeconds != null && refreshIntervalSeconds > 0) {
    return refreshIntervalSeconds;
  }
  return DEFAULT_RESYNC_SECONDS;
}

export function parseClockWidgetFields(fields: WidgetFieldRecord[]): ClockWidgetConfig {
  const map = new Map(fields.map((x) => [x.name, x]));
  return {
    timeType: normalizeTimeType(pickString(map, 'time_type', 'LOCAL')),
    clockType: normalizeClockType(pickString(map, 'clock_type', 'DIGITAL')),
    showDate: pickBool(map, 'show_date', true),
    showTime: pickBool(map, 'show_time', true),
    showTimeZone: pickBool(map, 'show_time_zone', false),
    showSeconds: pickBool(map, 'show_seconds', false),
    timeFormat: normalizeTimeFormat(pickString(map, 'time_format', '24H')),
    timeZone: pickString(map, 'time_zone', '').trim(),
    timeZoneFormat: normalizeZoneNameFormat(pickString(map, 'time_zone_format', 'SHORT')),
    backgroundColor: pickString(map, 'background_color', '').trim(),
  };
}

export function isValidIanaTimeZone(tz: string): boolean {
  const t = tz.trim();
  if (!t) {
    return true;
  }
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: t }).format(0);
    return true;
  } catch {
    return false;
  }
}

/** Resolved option for `Intl`: omit for browser local zone. */
export function intlTimeZoneOption(config: ClockWidgetConfig): string | undefined {
  const t = config.timeZone.trim();
  return t === '' ? undefined : t;
}

export function parseHexBackgroundColor(config: ClockWidgetConfig): string | null {
  const v = config.backgroundColor.trim();
  return /^#[0-9A-Fa-f]{6}$/.test(v) ? v : null;
}

export function buildClockDisplay(epochMs: number, config: ClockWidgetConfig): ClockDisplayStrings {
  const tz = intlTimeZoneOption(config);
  const hour12 = config.timeFormat === '12H';

  let dateLine: string | null = null;
  let timeLine: string | null = null;
  let zoneLine: string | null = null;

  if (config.showDate) {
    dateLine = formatDateLine(epochMs, tz);
  }
  if (config.showTime) {
    timeLine = formatTimeLine(epochMs, tz, hour12, config.showSeconds);
  }
  if (config.showTimeZone) {
    zoneLine = formatZoneLine(epochMs, tz, config.timeZoneFormat);
  }

  return { dateLine, timeLine, zoneLine };
}

export function buildAnalogAngles(epochMs: number, config: ClockWidgetConfig, includeSecondHand: boolean): ClockAnalogAngles {
  const tz = intlTimeZoneOption(config);
  const { hour24, minute, second } = getHmsInZone(epochMs, tz);
  const hourNorm = (hour24 % 12) + minute / 60 + second / 3600;
  const minuteNorm = minute + second / 60;
  const secondNorm = includeSecondHand ? second : 0;

  return {
    hourDeg: hourNorm * 30 - 90,
    minuteDeg: minuteNorm * 6 - 90,
    secondDeg: secondNorm * 6 - 90,
  };
}

/** Значения для ECharts gauge-clock (час 0–12, минута и секунда 0–60). */
export function buildAnalogGaugeValues(epochMs: number, config: ClockWidgetConfig): {
  hour: number;
  minute: number;
  second: number;
} {
  const tz = intlTimeZoneOption(config);
  const { hour24, minute, second } = getHmsInZone(epochMs, tz);
  return {
    hour: (hour24 % 12) + minute / 60 + second / 3600,
    minute: minute + second / 60,
    second: config.showSeconds ? second : 0,
  };
}

function formatDateLine(epochMs: number, timeZone: string | undefined): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(epochMs));
}

function formatTimeLine(epochMs: number, timeZone: string | undefined, hour12: boolean, withSeconds: boolean): string {
  const opts: Intl.DateTimeFormatOptions = {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hour12,
  };
  if (withSeconds) {
    opts.second = '2-digit';
  }
  return new Intl.DateTimeFormat('ru-RU', opts).format(new Date(epochMs));
}

function formatZoneLine(epochMs: number, timeZone: string | undefined, zoneFmt: ClockTimeZoneNameFormat): string {
  const nameStyle = zoneFmt === 'FULL' ? 'long' : 'short';
  const dtf = new Intl.DateTimeFormat('ru-RU', {
    timeZone,
    timeZoneName: nameStyle,
  });
  const parts = dtf.formatToParts(new Date(epochMs));
  const zn = parts.find((p) => p.type === 'timeZoneName');
  if (zn?.value) {
    return zn.value;
  }
  return timeZone ?? 'Локальная зона';
}

function getHmsInZone(
  epochMs: number,
  timeZone: string | undefined,
): { hour24: number; minute: number; second: number } {
  const dtf = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: 'numeric',
    minute: 'numeric',
    second: 'numeric',
    hour12: false,
  });
  const parts = dtf.formatToParts(new Date(epochMs));
  const hour = Number(parts.find((p) => p.type === 'hour')?.value ?? 0);
  const minute = Number(parts.find((p) => p.type === 'minute')?.value ?? 0);
  const second = Number(parts.find((p) => p.type === 'second')?.value ?? 0);
  return { hour24: hour, minute, second };
}

function normalizeTimeType(raw: string): ClockTimeType {
  if (raw === 'SERVER' || raw === 'HOST') {
    return raw;
  }
  return 'LOCAL';
}

function normalizeClockType(raw: string): ClockDisplayType {
  return raw === 'ANALOG' ? 'ANALOG' : 'DIGITAL';
}

function normalizeTimeFormat(raw: string): ClockTimeFormat {
  return raw === '12H' ? '12H' : '24H';
}

function normalizeZoneNameFormat(raw: string): ClockTimeZoneNameFormat {
  return raw === 'FULL' ? 'FULL' : 'SHORT';
}

function pickBool(map: Map<string, WidgetFieldRecord>, name: string, fallback: boolean): boolean {
  const value = map.get(name);
  if (!value) {
    return fallback;
  }
  return value.valueInt === 1;
}

function pickString(map: Map<string, WidgetFieldRecord>, name: string, fallback: string): string {
  const value = map.get(name);
  if (!value || !value.valueStr) {
    return fallback;
  }
  return value.valueStr;
}
