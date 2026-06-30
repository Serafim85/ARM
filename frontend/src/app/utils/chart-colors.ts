export const CHART_SERIES_BASE_HEX = '#2563eb';

export type ChartBaseColor =
  | '#2563eb'
  | '#f59e0b'
  | '#ef4444'
  | '#0f766e'
  | '#8b5cf6'
  | '#ec4899';

export const CHART_BASE_COLOR_OPTIONS: { label: string; value: ChartBaseColor }[] = [
  { label: 'Синий', value: '#2563eb' },
  { label: 'Янтарный', value: '#f59e0b' },
  { label: 'Красный', value: '#ef4444' },
  { label: 'Бирюзовый', value: '#0f766e' },
  { label: 'Фиолетовый', value: '#8b5cf6' },
  { label: 'Розовый', value: '#ec4899' },
];

export const DEFAULT_CHART_BASE_COLOR: ChartBaseColor = CHART_SERIES_BASE_HEX;

export function normalizeChartBaseColor(value: unknown): ChartBaseColor {
  if (typeof value !== 'string') {
    return DEFAULT_CHART_BASE_COLOR;
  }
  const normalized = value.trim().toLowerCase() as ChartBaseColor;
  const match = CHART_BASE_COLOR_OPTIONS.find((option) => option.value === normalized);
  return match?.value ?? DEFAULT_CHART_BASE_COLOR;
}

/** Разные цвета для круговых диаграмм и прочих категориальных серий. */
export const CHART_SERIES_COLORS = [
  CHART_SERIES_BASE_HEX,
  '#f59e0b',
  '#ef4444',
  '#0f766e',
  '#8b5cf6',
  '#ec4899',
] as const;

const LINE_LIGHTNESS_MIN = 0.2;
const LINE_LIGHTNESS_MAX = 0.82;
const LINE_SATURATION_MIN_FACTOR = 0.72;
const LINE_SATURATION_MAX_FACTOR = 1.08;

export type ChartSeriesAreaGradient = {
  type: 'linear';
  x: number;
  y: number;
  x2: number;
  y2: number;
  colorStops: Array<{ offset: number; color: string }>;
};

export type ChartSeriesVisualStyle = {
  line: string;
  area: ChartSeriesAreaGradient;
};

export function chartSeriesColor(index: number): string {
  return CHART_SERIES_COLORS[index % CHART_SERIES_COLORS.length];
}

export function chartSeriesLineColorsByMax(
  maxValues: Array<number | null>,
  baseHex: string = CHART_SERIES_BASE_HEX
): string[] {
  return chartSeriesStylesByMax(maxValues, baseHex).map((style) => style.line);
}

export function chartSeriesStylesByMax(
  maxValues: Array<number | null>,
  baseHex: string = CHART_SERIES_BASE_HEX
): ChartSeriesVisualStyle[] {
  const base = hexToHsl(normalizeChartBaseColor(baseHex));
  const count = maxValues.length;
  const ranks = resolveSeriesRanksByMax(maxValues);

  return maxValues.map((value, index) => {
    const rank = ranks[index] ?? count - 1;
    const line = buildLineColor(base, count, rank, value);
    return {
      line,
      area: chartSeriesAreaGradient(line),
    };
  });
}

export function chartSeriesAreaGradient(lineColor: string): ChartSeriesAreaGradient {
  const { r, g, b } = parseHexColor(lineColor);
  const { l } = hexToHsl(lineColor);
  const darkness = resolveLineDarkness(l);
  const topOpacity = 0.38 + darkness * 0.42;
  const midOpacity = 0.22 + darkness * 0.28;
  const bottomOpacity = 0.06 + darkness * 0.16;

  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: `rgba(${r}, ${g}, ${b}, ${topOpacity.toFixed(3)})` },
      { offset: 0.45, color: `rgba(${r}, ${g}, ${b}, ${midOpacity.toFixed(3)})` },
      { offset: 1, color: `rgba(${r}, ${g}, ${b}, ${bottomOpacity.toFixed(3)})` },
    ],
  };
}

function resolveSeriesRanksByMax(maxValues: Array<number | null>): number[] {
  const indexed = maxValues.map((value, index) => ({
    index,
    value: value != null && Number.isFinite(value) ? value : null,
  }));

  const sorted = [...indexed].sort((left, right) => {
    if (left.value == null && right.value == null) {
      return left.index - right.index;
    }
    if (left.value == null) {
      return 1;
    }
    if (right.value == null) {
      return -1;
    }
    if (right.value !== left.value) {
      return right.value - left.value;
    }
    return left.index - right.index;
  });

  const ranks = new Array<number>(maxValues.length).fill(maxValues.length - 1);
  sorted.forEach((entry, rank) => {
    ranks[entry.index] = rank;
  });
  return ranks;
}

function buildLineColor(
  base: { h: number; s: number; l: number },
  count: number,
  rank: number,
  maxValue: number | null
): string {
  if (maxValue == null || !Number.isFinite(maxValue)) {
    return hslToHex(base.h, base.s * LINE_SATURATION_MIN_FACTOR, LINE_LIGHTNESS_MAX);
  }

  const ratio = count <= 1 ? 1 : 1 - rank / (count - 1);
  const lightness = LINE_LIGHTNESS_MAX - ratio * (LINE_LIGHTNESS_MAX - LINE_LIGHTNESS_MIN);
  const saturationFactor =
    LINE_SATURATION_MIN_FACTOR + ratio * (LINE_SATURATION_MAX_FACTOR - LINE_SATURATION_MIN_FACTOR);
  return hslToHex(base.h, base.s * saturationFactor, lightness);
}

function resolveLineDarkness(lightness: number): number {
  const span = LINE_LIGHTNESS_MAX - LINE_LIGHTNESS_MIN;
  if (span <= 0) {
    return 1;
  }
  const normalized = (LINE_LIGHTNESS_MAX - lightness) / span;
  return Math.min(1, Math.max(0, normalized));
}

function parseHexColor(hex: string): { r: number; g: number; b: number } {
  const normalized = hex.replace('#', '').trim();
  if (normalized.length === 3) {
    return {
      r: parseInt(normalized[0]! + normalized[0], 16),
      g: parseInt(normalized[1]! + normalized[1], 16),
      b: parseInt(normalized[2]! + normalized[2], 16),
    };
  }
  if (normalized.length === 6) {
    return {
      r: parseInt(normalized.slice(0, 2), 16),
      g: parseInt(normalized.slice(2, 4), 16),
      b: parseInt(normalized.slice(4, 6), 16),
    };
  }
  throw new Error(`Invalid hex color: ${hex}`);
}

function hexToHsl(hex: string): { h: number; s: number; l: number } {
  const { r, g, b } = parseHexColor(hex);
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const delta = max - min;
  let h = 0;
  let s = 0;
  const l = (max + min) / 2;

  if (delta !== 0) {
    s = delta / (1 - Math.abs(2 * l - 1));
    switch (max) {
      case rn:
        h = ((gn - bn) / delta + (gn < bn ? 6 : 0)) * 60;
        break;
      case gn:
        h = ((bn - rn) / delta + 2) * 60;
        break;
      default:
        h = ((rn - gn) / delta + 4) * 60;
        break;
    }
  }

  return { h, s, l };
}

function hslToHex(h: number, s: number, l: number): string {
  const { r, g, b } = hslToRgb(h, s, l);
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

function hslToRgb(h: number, s: number, l: number): { r: number; g: number; b: number } {
  const hue = ((h % 360) + 360) % 360;
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((hue / 60) % 2) - 1));
  const m = l - c / 2;
  let rn = 0;
  let gn = 0;
  let bn = 0;

  if (hue < 60) {
    rn = c;
    gn = x;
  } else if (hue < 120) {
    rn = x;
    gn = c;
  } else if (hue < 180) {
    gn = c;
    bn = x;
  } else if (hue < 240) {
    gn = x;
    bn = c;
  } else if (hue < 300) {
    rn = x;
    bn = c;
  } else {
    rn = c;
    bn = x;
  }

  return {
    r: Math.round((rn + m) * 255),
    g: Math.round((gn + m) * 255),
    b: Math.round((bn + m) * 255),
  };
}

function toHex(value: number): string {
  return value.toString(16).padStart(2, '0');
}
