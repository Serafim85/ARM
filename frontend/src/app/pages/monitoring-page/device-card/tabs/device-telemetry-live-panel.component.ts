import {
  Component,
  DestroyRef,
  Input,
  OnChanges,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DeviceScanResult, MonitoringMetric } from '../../../../models';
import { MonitoringService } from '../../../../services/monitoring.service';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsOption } from 'echarts';

type GaugeMetric = { label: string; value: number; hint: string };

@Component({
  selector: 'app-device-telemetry-live-panel',
  standalone: true,
  imports: [NgxEchartsDirective],
  templateUrl: './device-telemetry-live-panel.component.html',
  styleUrl: './device-telemetry-live-panel.component.css',
})
export class DeviceTelemetryLivePanelComponent implements OnChanges {
  @Input({ required: true }) device!: DeviceScanResult;

  protected readonly ms = inject(MonitoringService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly now = signal(Date.now());
  private previousDeviceId: string | null = null;

  protected readonly details = computed(() => this.ms.getDetailsOrFallback(this.device));
  protected readonly liveState = computed(() => this.ms.deviceLiveTelemetryState(this.device.id));

  protected readonly gaugeMetrics = computed<GaugeMetric[]>(() => {
    const d = this.details();
    return [
      ...this.cpuGaugeMetrics(d.cpu),
      { label: 'RAM used', value: d.ramUsedPercent, hint: 'Использование оперативной памяти' },
      { label: 'ROM used', value: d.romUsedPercent, hint: 'Использование постоянной памяти' },
    ].filter((metric): metric is GaugeMetric => metric.value != null);
  });

  protected readonly hasGaugeMetrics = computed(() => this.gaugeMetrics().length > 0);

  protected readonly liveRemainingSeconds = computed(() => {
    const expiresAt = this.liveState().expiresAt;
    this.now();
    if (!expiresAt) {
      return 0;
    }
    const left = Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000);
    return Math.max(left, 0);
  });

  constructor() {
    const timer = window.setInterval(() => this.now.set(Date.now()), 1000);
    this.destroyRef.onDestroy(() => {
      window.clearInterval(timer);
      this.ms.stopDeviceLiveTelemetry(this.device?.id ?? '');
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['device']) {
      return;
    }
    const currentId = this.device?.id ?? null;
    if (this.previousDeviceId && currentId && this.previousDeviceId !== currentId) {
      this.ms.stopDeviceLiveTelemetry(this.previousDeviceId);
    }
    this.previousDeviceId = currentId;
  }

  /** CPU: одна плашка «Текущее значение», если current, average и peak совпадают. */
  private cpuGaugeMetrics(cpu: MonitoringMetric): GaugeMetric[] {
    const { current, average, peak } = cpu;
    if (
      current != null &&
      average != null &&
      peak != null &&
      this.cpuDisplayValuesEqual(current, average, peak)
    ) {
      return [
        {
          label: cpu.currentItemName?.trim() || 'CPU current',
          value: current,
          hint: 'Текущее значение',
        },
      ];
    }
    return [
      {
        label: cpu.currentItemName?.trim() || 'CPU current',
        value: current,
        hint: 'Текущее значение',
      },
      {
        label: cpu.averageItemName?.trim() || 'CPU average',
        value: average,
        hint: 'Среднее за цикл',
      },
      {
        label: cpu.peakItemName?.trim() || 'CPU peak',
        value: peak,
        hint: 'Пиковое значение',
      },
    ].filter((metric): metric is GaugeMetric => metric.value != null);
  }

  private cpuDisplayValuesEqual(a: number, b: number, c: number): boolean {
    const fa = this.formatGaugeValue(a);
    const fb = this.formatGaugeValue(b);
    const fc = this.formatGaugeValue(c);
    return fa === fb && fb === fc;
  }

  protected refreshSnapshot(): void {
    this.ms.refreshMonitoringDetails(this.device, false);
  }

  protected startLive(): void {
    this.ms.startDeviceLiveTelemetry(this.device);
  }

  protected stopLive(): void {
    this.ms.stopDeviceLiveTelemetry(this.device.id);
  }

  /** Ширина шкалы 0–100% (load average и др. могут быть > 100 — полоса упирается в максимум). */
  protected gaugeBarWidthPercent(value: number): number {
    if (!Number.isFinite(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, value));
  }

  /** Число для подписи: целые проценты без дробной части, иначе до двух знаков (например load average). */
  protected formatGaugeValue(value: number): string {
    if (!Number.isFinite(value)) {
      return '—';
    }
    const rounded = Math.round(value);
    if (Math.abs(value - rounded) < 1e-6) {
      return String(rounded);
    }
    return value.toFixed(2).replace(/\.?0+$/, '');
  }

  protected gaugeToneFillClass(value: number): string {
    const w = this.gaugeBarWidthPercent(value);
    if (w > 90) return 'telemetry-gauge-fill-critical';
    if (w >= 70) return 'telemetry-gauge-fill-warn';
    return 'telemetry-gauge-fill-ok';
  }

  protected gaugeToneValueClass(value: number): string {
    const w = this.gaugeBarWidthPercent(value);
    if (w > 90) return 'telemetry-gauge-value-critical';
    if (w >= 70) return 'telemetry-gauge-value-warn';
    return 'telemetry-gauge-value-ok';
  }

  protected gaugeOption(metric: GaugeMetric): EChartsOption {
    const w = this.gaugeBarWidthPercent(metric.value);
    const tone = w > 90 ? 'critical' : w >= 70 ? 'warn' : 'ok';
    const color =
      tone === 'critical'
        ? '#fb7185'
        : tone === 'warn'
          ? '#fbbf24'
          : '#4ade80';

    return {
      animation: false,
      series: [
        {
          type: 'gauge',
          min: 0,
          max: 100,
          startAngle: 210,
          endAngle: -30,
          splitNumber: 5,
          center: ['50%', '62%'],
          radius: '100%',
          progress: {
            show: true,
            width: 14,
            roundCap: true,
            itemStyle: { color },
          },
          axisLine: {
            lineStyle: {
              width: 14,
              color: [[1, 'rgba(148, 163, 184, 0.18)']],
            },
          },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          pointer: { show: false },
          anchor: { show: false },
          detail: {
            show: true,
            valueAnimation: false,
            color: '#f8fafc',
            fontSize: 24,
            fontWeight: 800,
            offsetCenter: [0, '6%'],
            formatter: (value) => `${this.formatGaugeValue(Number(value))}%`,
          },
          title: { show: false },
          data: [{ value: w }],
        },
      ],
    } satisfies EChartsOption;
  }

  protected formatTimestamp(iso: string | null | undefined): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }
}
