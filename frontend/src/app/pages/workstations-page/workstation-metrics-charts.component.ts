import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, computed, effect, inject, signal } from '@angular/core';
import { SelectButtonModule } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { NgxEchartsDirective } from 'ngx-echarts';
import type { EChartsOption } from 'echarts';
import { WorkstationMetricSeries } from '../../models';
import { WorkstationsService } from '../../services/workstations.service';
import { defaultDayMetricsRange, defaultHourMetricsRange } from '../../utils/metrics-history-range';

type MetricsPeriod = 'HOUR' | 'DAY' | 'WEEK';

type ValueScale = {
  unit: string;
  scale: (value: number) => number;
  format: (value: number) => string;
  axisFormat: (value: number) => string;
};

@Component({
  selector: 'app-workstation-metrics-charts',
  standalone: true,
  imports: [FormsModule, SelectButtonModule, NgxEchartsDirective],
  templateUrl: './workstation-metrics-charts.component.html',
  styleUrl: './workstation-metrics-charts.component.css',
})
export class WorkstationMetricsChartsComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) workstationId!: number;

  protected readonly ws = inject(WorkstationsService);

  protected readonly period = signal<MetricsPeriod>('HOUR');
  /** Период оси графика — обновляется после загрузки данных, без скачка подписей */
  protected readonly displayPeriod = signal<MetricsPeriod>('HOUR');
  protected readonly periodOptions = [
    { label: 'Час', value: 'HOUR' as const },
    { label: 'Сутки', value: 'DAY' as const },
    { label: 'Неделя', value: 'WEEK' as const },
  ];

  protected readonly chartOptions = computed(() => {
    const period = this.displayPeriod();
    return (this.ws.metricsHistory()?.series ?? []).map((series) => {
      const valueScale = this.resolveValueScale(series);
      return {
        key: series.metricKey,
        title: series.displayName,
        unit: valueScale.unit,
        option: this.buildChartOption(series, period, valueScale),
        latest: this.latestValue(series, valueScale),
      };
    });
  });

  private refreshTimer: ReturnType<typeof setInterval> | null = null;

  constructor() {
    effect(() => {
      if (!this.ws.metricsLoading()) {
        this.displayPeriod.set(this.period());
      }
    });
  }

  ngOnInit(): void {
    this.reload();
    this.refreshTimer = setInterval(() => this.reload(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['workstationId'] && !changes['workstationId'].firstChange) {
      this.reload();
    }
  }

  protected onPeriodChange(value: MetricsPeriod): void {
    if (value === this.period()) {
      return;
    }
    this.period.set(value);
    this.reload();
  }

  protected hasChartData(): boolean {
    return this.chartOptions().length > 0;
  }

  protected reload(): void {
    if (!Number.isFinite(this.workstationId)) {
      return;
    }
    const range = this.resolveRange(this.period());
    this.ws.loadMetricsHistory(this.workstationId, range.fromIso, range.toIso);
  }

  private resolveRange(period: MetricsPeriod): { fromIso: string; toIso: string } {
    if (period === 'HOUR') {
      return defaultHourMetricsRange();
    }
    if (period === 'DAY') {
      return defaultDayMetricsRange();
    }
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 7);
    return { fromIso: from.toISOString(), toIso: to.toISOString() };
  }

  private buildChartOption(
    series: WorkstationMetricSeries,
    period: MetricsPeriod,
    valueScale: ValueScale
  ): EChartsOption {
    const data = series.points.map((point) => [
      new Date(point.recordedAt).getTime(),
      valueScale.scale(point.value),
    ] as [number, number]);
    const yMax =
      series.metricKey === 'arm.cpu.util' || series.metricKey === 'arm.disk.root.used_pct' ? 100 : undefined;
    return {
      animation: true,
      animationDuration: 280,
      animationDurationUpdate: 320,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicInOut',
      grid: { left: 52, right: 16, top: 16, bottom: 44, containLabel: true },
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value) => `${valueScale.format(Number(value))} ${valueScale.unit}`,
      },
      xAxis: {
        type: 'time',
        splitNumber: period === 'WEEK' ? 5 : 4,
        axisLabel: {
          hideOverlap: true,
          showMinLabel: true,
          showMaxLabel: true,
          margin: 10,
          formatter: (value: number) => this.formatAxisTime(value, period),
        },
      },
      yAxis: {
        type: 'value',
        min: 0,
        ...(yMax != null ? { max: yMax } : {}),
        axisLabel: {
          hideOverlap: true,
          formatter: (value: number) => valueScale.axisFormat(value),
        },
      },
      series: [
        {
          type: 'line',
          showSymbol: data.length <= 2,
          smooth: false,
          connectNulls: true,
          sampling: data.length > 120 ? 'lttb' : undefined,
          lineStyle: { width: 2, color: '#2563eb' },
          areaStyle: { color: 'rgba(37, 99, 235, 0.08)' },
          data,
        },
      ],
    };
  }

  private formatAxisTime(valueMs: number, period: MetricsPeriod): string {
    const d = new Date(valueMs);
    if (period === 'WEEK') {
      return d.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
    }
    return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  }

  private latestValue(series: WorkstationMetricSeries, valueScale: ValueScale): string {
    const last = series.points.at(-1);
    if (!last) {
      return '—';
    }
    return `${valueScale.format(last.value)} ${valueScale.unit}`;
  }

  private resolveValueScale(series: WorkstationMetricSeries): ValueScale {
    if (series.metricKey === 'arm.mem.used') {
      const maxBytes = series.points.reduce((max, point) => Math.max(max, point.value), 0);
      if (maxBytes < 512 * 1024 * 1024) {
        return {
          unit: 'МБ',
          scale: (value) => value / (1024 * 1024),
          format: (value) =>
            new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value / (1024 * 1024)),
          axisFormat: (value) =>
            `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(value)} МБ`,
        };
      }
      return {
        unit: 'ГБ',
        scale: (value) => value / (1024 * 1024 * 1024),
        format: (value) =>
          new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value / (1024 * 1024 * 1024)),
        axisFormat: (value) =>
          `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value)} ГБ`,
      };
    }
    const percent = series.unit === '%' ? '%' : series.unit || '';
    return {
      unit: percent,
      scale: (value) => value,
      format: (value) => new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value),
      axisFormat: (value) => `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 }).format(value)}${percent}`,
    };
  }
}
