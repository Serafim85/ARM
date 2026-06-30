import type { YAXisComponentOption } from 'echarts';

export type ValueMapMappings = Record<string, string>;

export function isValueMapSeries(mappings?: ValueMapMappings | null): boolean {
  return mappings != null && Object.keys(mappings).length > 0;
}

export function mapValueMapLabel(mappings: ValueMapMappings, rawValue: number): string {
  if (!Number.isFinite(rawValue)) {
    return '—';
  }
  const rounded = String(Math.round(rawValue));
  const exact = String(rawValue);
  return mappings[rounded] ?? mappings[exact] ?? exact;
}

export function collectPresentValues(values: number[]): number[] {
  const present = new Set<number>();
  for (const value of values) {
    if (!Number.isFinite(value)) {
      continue;
    }
    present.add(Math.round(value));
  }
  return [...present].sort((a, b) => a - b);
}

/** Значения для подписей valuemap-оси: фактические точки ряда + уровни порогов на этой оси. */
export function collectValueMapAxisValues(
  seriesValues: number[],
  thresholdValues: number[] = [],
): number[] {
  return collectPresentValues([...seriesValues, ...thresholdValues]);
}

export function formatValueMapStatValue(value: number | null, mappings: ValueMapMappings): string {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return mapValueMapLabel(mappings, value);
}

const INTEGER_TICK_EPSILON = 1e-6;

function isIntegerTick(value: number): boolean {
  const rounded = Math.round(value);
  return Math.abs(value - rounded) <= INTEGER_TICK_EPSILON;
}

export function buildValueMapYAxis(presentValues: number[], mappings: ValueMapMappings): YAXisComponentOption {
  const presentSet = new Set(presentValues);
  const minValue = presentValues.length > 0 ? presentValues[0]! : 0;
  const maxValue = presentValues.length > 0 ? presentValues[presentValues.length - 1]! : 0;
  const span = maxValue - minValue;

  const axisMin = span === 0 ? minValue - 1 : Math.floor(minValue) - 1;
  const axisMax = span === 0 ? maxValue + 1 : Math.ceil(maxValue) + 1;

  return {
    type: 'value',
    min: axisMin,
    max: axisMax,
    interval: 1,
    axisLabel: {
      hideOverlap: true,
      formatter: (value: number) => {
        if (!isIntegerTick(value)) {
          return '';
        }
        const rounded = Math.round(value);
        if (!presentSet.has(rounded)) {
          return '';
        }
        return mapValueMapLabel(mappings, rounded);
      },
    },
  };
}

export function seriesChartValues(
  rawValues: number[],
  scaledValues: number[] | null | undefined,
  mappings?: ValueMapMappings | null,
): number[] {
  if (isValueMapSeries(mappings)) {
    return rawValues;
  }
  if (scaledValues && scaledValues.length === rawValues.length) {
    return scaledValues;
  }
  return rawValues;
}
