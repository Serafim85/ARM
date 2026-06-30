import { Component, computed, DestroyRef, effect, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { type EChartsOption } from 'echarts';
import { NgxEchartsDirective } from 'ngx-echarts';
import { Subscription } from 'rxjs';
import type { EChartsType } from 'echarts/core';
import type { MonitoringMetricsBatchSeries, WidgetFieldRecord } from '../../../models';
import { MonitoringService } from '../../../services/monitoring.service';
import { defaultServerResyncIntervalSeconds } from '../dashboard-clock-widget/clock-widget-config';
import { parseGraphWidgetFields, resolvePeriodRange, type GraphWidgetPeriod } from './graph-widget-config';
import { EChartsCoreOption } from 'echarts/core';
import { ChartLegendStatsPanelComponent } from '../../../components/chart-legend-stats-panel/chart-legend-stats-panel.component';
import { chartLegendPlacementClass, legendPanelBeforeChart } from '../../../utils/chart-legend-placement';
import { buildStatRowsFromBatchSeries, computeSeriesStats } from '../../../utils/chart-series-stats.util';
import {
  chartSeriesColor,
  chartSeriesStylesByMax,
} from '../../../utils/chart-colors';

/** CSS-класс контейнера тултипа ECharts (стили в component.css). */
export const DASHBOARD_GRAPH_WIDGET_TOOLTIP_CLASS = 'dashboard-graph-widget-echarts-tooltip';

import {
  resolveChartPlotAreaHeight,
  syncChartPlotAreaCssVars,
} from '../../../utils/chart-plot-layout.util';
import {
  buildValueMapYAxis,
  collectPresentValues,
  isValueMapSeries,
  mapValueMapLabel,
  type ValueMapMappings,
} from '../../../utils/valuemap-chart.util';

@Component({
  selector: 'app-dashboard-graph-widget',
  standalone: true,
  imports: [NgxEchartsDirective, ChartLegendStatsPanelComponent],
  templateUrl: './dashboard-graph-widget.component.html',
  styleUrl: './dashboard-graph-widget.component.css',
})
export class DashboardGraphWidgetComponent {
  private readonly monitoring = inject(MonitoringService);
  private readonly destroyRef = inject(DestroyRef);

  readonly fields = input.required<WidgetFieldRecord[]>();
  readonly refreshIntervalSeconds = input<number | null>(null);
  readonly period = input<GraphWidgetPeriod | undefined>();

  protected readonly config = computed(() => parseGraphWidgetFields(this.fields()));
  private readonly activePeriod = computed(() => this.period() ?? this.config().period);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly chartOption = signal<EChartsCoreOption | null>(null);
  private readonly dataRows = signal<MonitoringMetricsBatchSeries[] | null>(null);
  private readonly chartHost = viewChild<ElementRef<HTMLElement>>('chartHost');
  private chartInstance: EChartsType | null = null;
  private chartFinishedOff: (() => void) | null = null;

  protected readonly hasSeries = computed(() => this.config().series.length > 0);
  protected readonly showLegendPanel = computed(() => this.config().showLegend && (this.dataRows()?.length ?? 0) > 0);
  protected readonly legendStatRows = computed(() => {
    const rows = this.dataRows();
    if (!rows?.length) {
      return [];
    }
    return buildStatRowsFromBatchSeries(rows, (row) => this.resolveSeriesLabel(row));
  });
  protected readonly legendPlacement = computed(() => this.config().legendPlacement);
  protected readonly legendLayoutClass = computed(() => chartLegendPlacementClass(this.legendPlacement()));
  protected readonly legendPanelBefore = computed(() => legendPanelBeforeChart(this.legendPlacement()));

  constructor() {
    this.destroyRef.onDestroy(() => this.detachChartFinishedListener());
    effect((onCleanup) => {
      const cfg = this.config();
      const activePeriod = this.activePeriod();
      const refreshSec = this.refreshIntervalSeconds();
      let cancelled = false;
      let active: Subscription | null = null;

      const run = () => {
        if (cancelled) return;
        if (cfg.series.length === 0) {
          this.error.set('');
          this.dataRows.set(null);
          this.loading.set(false);
          return;
        }
        active?.unsubscribe();
        this.loading.set(true);
        this.error.set('');
        const now = new Date();
        const range = resolvePeriodRange(activePeriod, now);
        active = this.monitoring
          .getMetricsHistoryBatch({
            from: range.fromIso,
            to: range.toIso,
            series: cfg.series.map((s) => ({ deviceId: s.deviceId, metricName: s.metricName })),
            maxPoints: 1000,
          })
          .subscribe({
            next: (rows) => {
              if (cancelled) return;
              this.dataRows.set(rows);
              this.loading.set(false);
            },
            error: () => {
              if (cancelled) return;
              this.loading.set(false);
              this.error.set('Не удалось загрузить данные графика.');
              this.dataRows.set(null);
            },
          });
      };

      run();
      const periodMs = defaultServerResyncIntervalSeconds(refreshSec) * 1000;
      const id = window.setInterval(run, periodMs);
      onCleanup(() => {
        cancelled = true;
        active?.unsubscribe();
        window.clearInterval(id);
      });
    });

    effect(() => {
      const rows = this.dataRows();
      const cfg = this.config();
      const activePeriod = this.activePeriod();
      if (!rows || cfg.series.length === 0) {
        this.chartOption.set(null);
        return;
      }
      this.chartOption.set(this.buildDataOption(rows, cfg.fill, activePeriod));
    });

    effect((onCleanup) => {
      const host = this.chartHost()?.nativeElement;
      if (!host || !this.chartOption()) {
        return;
      }
      const scheduleSync = () => queueMicrotask(() => this.syncTooltipMaxHeight());
      scheduleSync();
      const resizeObserver = new ResizeObserver(scheduleSync);
      resizeObserver.observe(host);
      onCleanup(() => resizeObserver.disconnect());
    });
  }

  protected onChartInit(chart: unknown): void {
    this.detachChartFinishedListener();
    this.chartInstance = chart as EChartsType;
    this.attachChartFinishedListener();
    this.syncTooltipMaxHeight();
  }

  private attachChartFinishedListener(): void {
    const chart = this.chartInstance;
    if (!chart || chart.isDisposed()) {
      return;
    }
    const handler = () => this.syncTooltipMaxHeight();
    chart.on('finished', handler);
    this.chartFinishedOff = () => {
      if (!chart.isDisposed()) {
        chart.off('finished', handler);
      }
    };
  }

  private detachChartFinishedListener(): void {
    this.chartFinishedOff?.();
    this.chartFinishedOff = null;
  }

  /** Ограничивает тултип высотой области построения (grid), а не всего контейнера. */
  private syncTooltipMaxHeight(): void {
    const host = this.chartHost()?.nativeElement;
    if (!host) {
      return;
    }
    const chart = this.chartInstance;
    const layout = host.closest('.chart-legend-layout');
    if (chart && !chart.isDisposed() && layout instanceof HTMLElement) {
      syncChartPlotAreaCssVars(layout, chart);
    }
    const plotHeight = chart && !chart.isDisposed() ? resolveChartPlotAreaHeight(chart) : null;
    const maxH =
      plotHeight != null && plotHeight > 0
        ? Math.max(48, plotHeight - 4)
        : Math.max(72, Math.floor(host.clientHeight * 0.35));
    host.style.setProperty('--dashboard-graph-tooltip-max-h', `${maxH}px`);
  }

  /** Прокрутка тултипа колёсиком над графиком (курсор может быть вне тултипа). */
  protected onChartTooltipWheel(event: WheelEvent): void {
    const wrapper = event.currentTarget;
    if (!(wrapper instanceof HTMLElement)) {
      return;
    }
    const tooltip = wrapper.querySelector<HTMLElement>(`.${DASHBOARD_GRAPH_WIDGET_TOOLTIP_CLASS}`);
    if (!tooltip) {
      return;
    }
    const body = tooltip.querySelector<HTMLElement>('.dashboard-graph-tooltip-body');
    const scrollEl = body ?? tooltip;
    if (scrollEl.scrollHeight <= scrollEl.clientHeight + 1) {
      return;
    }
    const maxScroll = scrollEl.scrollHeight - scrollEl.clientHeight;
    const nextScroll = Math.min(maxScroll, Math.max(0, scrollEl.scrollTop + event.deltaY));
    if (nextScroll === scrollEl.scrollTop) {
      return;
    }
    scrollEl.scrollTop = nextScroll;
    event.preventDefault();
    event.stopPropagation();
  }

  private buildDataOption(
    rows: MonitoringMetricsBatchSeries[],
    fill: boolean,
    period: GraphWidgetPeriod
  ): EChartsOption {
    const seriesEntries = rows.map((row) => {
      const t = row.t ?? [];
      const useValueMap = isValueMapSeries(row.valueMapMappings);
      const rawValues = row.v ?? [];
      const scaledValues = row.sv && row.sv.length === t.length ? row.sv : null;
      const values = useValueMap
        ? rawValues
        : scaledValues && scaledValues.length === t.length
          ? scaledValues
          : rawValues;
      const title = this.resolveSeriesLabel(row);
      const data: Array<[number, number]> = [];
      const size = Math.min(t.length, values.length);
      for (let i = 0; i < size; i++) {
        const value = values[i];
        if (!Number.isFinite(value)) {
          continue;
        }
        data.push([t[i], value]);
      }
      data.sort((a, b) => a[0] - b[0]);
      const stats = computeSeriesStats(data.map((point) => point[1]));
      return {
        title,
        data,
        max: stats.max,
        useValueMap,
        valueMapMappings: row.valueMapMappings ?? undefined,
      };
    });
    const seriesStyles = chartSeriesStylesByMax(seriesEntries.map((entry) => entry.max));

    const series = seriesEntries.map((entry, idx) => {
      const style = seriesStyles[idx];
      const lineColor = style?.line ?? chartSeriesColor(idx);
      const useFill = fill && !entry.useValueMap;
      return {
        name: entry.title,
        type: 'line' as const,
        showSymbol: false,
        smooth: false,
        step: entry.useValueMap ? ('end' as const) : undefined,
        connectNulls: true,
        lineStyle: { width: 2, color: lineColor },
        itemStyle: { color: lineColor },
        areaStyle: useFill ? { color: style?.area } : undefined,
        data: entry.data,
      };
    });

    const allValueMap = seriesEntries.length > 0 && seriesEntries.every((entry) => entry.useValueMap);
    const yAxis = allValueMap
      ? buildValueMapYAxis(
          collectPresentValues(seriesEntries.flatMap((entry) => entry.data.map((point) => point[1]))),
          seriesEntries[0]!.valueMapMappings!,
        )
      : { type: 'value' as const, scale: true };

    const seriesValueMaps = seriesEntries.map((entry) => entry.valueMapMappings ?? null);

    return {
      animation: false,
      grid: { left: 40, right: 16, top: 18, bottom: 32, containLabel: true },
      legend: { show: false },
      tooltip: {
        trigger: 'axis',
        confine: true,
        enterable: false,
        className: DASHBOARD_GRAPH_WIDGET_TOOLTIP_CLASS,
        axisPointer: {
          type: 'line',
          snap: false,
          animation: false,
        },
        formatter: (params: unknown) => this.formatTooltip(params, period, seriesValueMaps),
      },
      axisPointer: {
        animation: false,
      },
      xAxis: {
        type: 'time',
        axisPointer: {
          snap: false,
          animation: false,
        },
        axisLabel: {
          formatter: (value: number) => this.formatAxisTime(value, period),
        },
      },
      yAxis,
      series: series as NonNullable<EChartsOption['series']>,
    };
  }

  private formatTooltip(
    params: unknown,
    period: GraphWidgetPeriod,
    seriesValueMaps?: Array<ValueMapMappings | null>,
  ): string {
    const rows = Array.isArray(params) ? params : params != null ? [params] : [];
    if (rows.length === 0) {
      return '';
    }

    const axisValue =
      (rows[0] as { axisValue?: string | number } | undefined)?.axisValue ??
      (rows[0] as { axisValueLabel?: string } | undefined)?.axisValueLabel;

    const axisText =
      typeof axisValue === 'number'
        ? this.formatAxisTime(axisValue, period)
        : typeof axisValue === 'string'
          ? axisValue
          : '';

    const lines = rows.map((row) => {
      const item = row as {
        marker?: string;
        seriesName?: string;
        data?: unknown;
        value?: unknown;
        seriesIndex?: number;
      };
      const pair = (Array.isArray(item.data) ? item.data : Array.isArray(item.value) ? item.value : null) as
        | [unknown, unknown]
        | null;
      const rawValue = pair ? pair[1] : null;
      const valueMap = seriesValueMaps?.[item.seriesIndex ?? 0];
      const valueText =
        typeof rawValue === 'number' && Number.isFinite(rawValue)
          ? isValueMapSeries(valueMap)
            ? this.escapeHtml(mapValueMapLabel(valueMap!, rawValue))
            : this.formatMetricValue(rawValue)
          : '—';

      return `<div class="dashboard-graph-tooltip-row">${item.marker ?? ''}<span class="dashboard-graph-tooltip-name">${this.escapeHtml(item.seriesName ?? '')}</span><strong class="dashboard-graph-tooltip-value">${valueText}</strong></div>`;
    });

    const body = lines.join('');
    if (!axisText) {
      return `<div class="dashboard-graph-tooltip"><div class="dashboard-graph-tooltip-body">${body}</div></div>`;
    }
    return `<div class="dashboard-graph-tooltip">
      <div class="dashboard-graph-tooltip-head">${this.escapeHtml(axisText)}</div>
      <div class="dashboard-graph-tooltip-body">${body}</div>
    </div>`;
  }

  private formatMetricValue(value: number): string {
    return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
  }

  private escapeHtml(value: string): string {
    return value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  private resolveSeriesLabel(row: MonitoringMetricsBatchSeries): string {
    const display = row.displayName?.trim() || row.metricName;
    return `#${row.deviceId} · ${display}`;
  }

  private formatAxisTime(value: number, period: GraphWidgetPeriod): string {
    const date = new Date(value);
    if (period === 'DAY' || period === 'HOUR') {
      return date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
