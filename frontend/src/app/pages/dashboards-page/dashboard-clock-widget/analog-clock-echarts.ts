import type { EChartsOption } from 'echarts';

export type AnalogClockGaugeValues = {
  /** 0–12, дробная часть для плавного хода часовой */
  hour: number;
  /** 0–60 */
  minute: number;
  /** 0–60 */
  second: number;
};

/** Общая форма стрелок, как в официальном примере gauge-clock (index.js демо ECharts). */
const POINTER_PATH =
  'path://M2.9,0.7L2.9,0.7c1.4,0,2.6,1.2,2.6,2.6v115c0,1.4-1.2,2.6-2.6,2.6l0,0c-1.4,0-2.6-1.2-2.6-2.6V3.3C0.3,1.9,1.4,0.7,2.9,0.7z';

/** Подпись на циферблате (вместо логотипа ECharts из демо). */
const DIAL_BRAND = 'АРМ';

const GOLD = '#C0911F';
const HAND_SHADOW = {
  shadowColor: 'rgba(0, 0, 0, 0.3)',
  shadowBlur: 8,
  shadowOffsetX: 2,
  shadowOffsetY: 4,
} as const;

/**
 * Опция графика по официальному sandbox Apache ECharts gauge-clock
 * (см. index.js / style.css из примера).
 */
export function buildAnalogClockEchartsOption(values: AnalogClockGaugeValues, showSeconds: boolean): EChartsOption {
  const series: EChartsOption['series'] = [
    {
      name: 'hour',
      type: 'gauge',
      z: 1,
      center: ['50%', '50%'],
      radius: '88%',
      startAngle: 90,
      endAngle: -270,
      min: 0,
      max: 12,
      splitNumber: 12,
      clockwise: true,
      animation: false,
      axisLine: {
        lineStyle: {
          width: 12,
          color: [[1, 'rgba(0,0,0,0.7)']],
          shadowColor: 'rgba(0, 0, 0, 0.5)',
          shadowBlur: 12,
        },
      },
      splitLine: {
        distance: -6,
        length: 12,
        lineStyle: {
          color: '#1a1a1a',
          width: 2,
          shadowColor: 'rgba(0, 0, 0, 0.3)',
          shadowBlur: 3,
          shadowOffsetX: 1,
          shadowOffsetY: 2,
        },
      },
      axisTick: {
        distance: -6,
        splitNumber: 4,
        length: 5,
        lineStyle: {
          color: '#9ca3af',
          width: 1,
        },
      },
      axisLabel: {
        color: '#4b5563',
        fontSize: 14,
        fontWeight: 600,
        distance: 20,
        formatter: (v: number | string) => {
          const n = Number(v);
          if (!Number.isFinite(n)) {
            return '';
          }
          if (n < 0.5) {
            return '';
          }
          if (n >= 11.5) {
            return '12';
          }
          return String(Math.round(n));
        },
      },
      anchor: { show: false },
      pointer: {
        icon: POINTER_PATH,
        width: 10,
        length: '55%',
        offsetCenter: [0, '8%'],
        itemStyle: {
          color: GOLD,
          ...HAND_SHADOW,
        },
      },
      detail: { show: false },
      title: {
        show: true,
        offsetCenter: [0, '-34%'],
        color: '#707177',
        fontSize: 13,
        fontWeight: 600,
        fontFamily: 'system-ui, -apple-system, Segoe UI, sans-serif',
      },
      data: [{ value: values.hour, name: DIAL_BRAND }],
    },
    {
      name: 'minute',
      type: 'gauge',
      z: 2,
      center: ['50%', '50%'],
      radius: '88%',
      startAngle: 90,
      endAngle: -270,
      min: 0,
      max: 60,
      clockwise: true,
      animation: false,
      axisLine: { show: false },
      splitLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      pointer: {
        icon: POINTER_PATH,
        width: 7,
        length: '70%',
        offsetCenter: [0, '8%'],
        itemStyle: {
          color: GOLD,
          ...HAND_SHADOW,
        },
      },
      anchor: {
        show: true,
        size: 14,
        showAbove: false,
        itemStyle: {
          borderWidth: 8,
          borderColor: GOLD,
          ...HAND_SHADOW,
        },
      },
      detail: { show: false },
      title: { offsetCenter: ['0%', '-40%'], show: false },
      data: [{ value: values.minute }],
    },
  ];

  if (showSeconds) {
    series.push({
      name: 'second',
      type: 'gauge',
      z: 3,
      center: ['50%', '50%'],
      radius: '88%',
      startAngle: 90,
      endAngle: -270,
      min: 0,
      max: 60,
      clockwise: true,
      animation: false,
      axisLine: { show: false },
      splitLine: { show: false },
      axisTick: { show: false },
      axisLabel: { show: false },
      pointer: {
        icon: POINTER_PATH,
        width: 3,
        length: '85%',
        offsetCenter: [0, '8%'],
        itemStyle: {
          color: GOLD,
          ...HAND_SHADOW,
        },
      },
      anchor: {
        show: true,
        size: 10,
        showAbove: true,
        itemStyle: {
          color: GOLD,
          ...HAND_SHADOW,
        },
      },
      detail: { show: false },
      title: { offsetCenter: ['0%', '-40%'], show: false },
      data: [{ value: values.second }],
    });
  }

  return {
    animation: false,
    backgroundColor: 'transparent',
    series,
  } satisfies EChartsOption;
}
