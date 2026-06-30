import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  input,
  signal,
  OnDestroy,
  viewChild,
  viewChildren,
} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { Popover, PopoverModule } from 'primeng/popover';
import { SelectButtonModule } from 'primeng/selectbutton';
import { NgxEchartsDirective } from 'ngx-echarts';
import { API_BASE_URL } from '../../../api-config';
import { ChartLegendPlacementSelectComponent } from '../../../components/chart-legend-placement-select/chart-legend-placement-select.component';
import { ChartBaseColorSelectComponent } from '../../../components/chart-base-color-select/chart-base-color-select.component';
import { ChartLegendStatsPanelComponent } from '../../../components/chart-legend-stats-panel/chart-legend-stats-panel.component';
import { ChartUiPreferencesService } from '../../../services/chart-ui-preferences.service';
import {
  chartLegendPlacementClass,
  legendPanelBeforeChart,
  type ChartLegendPlacement,
  type DeviceMetricsLayout,
  type DeviceMetricsPeriod,
} from '../../../utils/chart-legend-placement';
import { resolveChartPanelMinHeightPx } from '../../../utils/chart-panel-layout.util';
import {
  resolveChartPlotAreaHeight,
  syncChartPlotAreaCssVars,
} from '../../../utils/chart-plot-layout.util';
import { resolveMetricDisplayLabel } from '../../../utils/metric-display-label';
import {
  buildStatRowsFromScalars,
  buildStatRowsFromTimeSeries,
  computeSeriesStats,
  type ChartLegendRow,
} from '../../../utils/chart-series-stats.util';
import {
  chartSeriesColor,
  chartSeriesStylesByMax,
  type ChartBaseColor,
} from '../../../utils/chart-colors';
import { defaultDayMetricsRange, defaultHourMetricsRange } from '../../../utils/metrics-history-range';
import type { DeviceMetricsHistoryResponseDto as CompactMetricsHistoryResponseDto } from '../device-metrics-history.types';
import {
  expandCompactPanels,
  type ExpandedChartPanel,
  type ExpandedMetricPoint,
} from '../device-metrics-history.adapter';
import {
  buildThresholdLegendRows,
  buildThresholdLineSeries,
  resolvePercentYAxisMax,
  shouldAnnotateThresholdsOnChart,
  thresholdsForMetric,
  thresholdChartValue,
  THRESHOLD_OVERLAY_SERIES_PREFIX,
} from '../../../utils/metric-threshold.util';
import {
  buildValueMapYAxis,
  collectPresentValues,
  collectValueMapAxisValues,
  isValueMapSeries,
  mapValueMapLabel,
  type ValueMapMappings,
} from '../../../utils/valuemap-chart.util';
import type { EChartsOption } from 'echarts';
import type { EChartsType } from 'echarts/core';
import { Subscription, Subject, debounceTime } from 'rxjs';

/** CSS-класс контейнера тултипа ECharts (стили в component.css). */
const DEVICE_METRICS_CHART_TOOLTIP_CLASS = 'device-metrics-chart-echarts-tooltip';

type TimePeriod = DeviceMetricsPeriod;
type ChartLayout = DeviceMetricsLayout;

/** Не более 31 календарного дня включительно («С» … «По»). */
const MAX_CUSTOM_RANGE_INCLUSIVE_DAYS = 31;

type MetricValueDto = ExpandedMetricPoint;

type MetricChartPanelDto = ExpandedChartPanel;

/** Сколько панелей запрашивать за раз (подгрузка по скроллу). */
const PANEL_PAGE_SIZE = 6;

/** Целевой максимум точек на ряд: ориентируемся на ширину экрана (×2 под HiDPI). */
function resolveChartMaxPoints(): number {
  const width = typeof window === 'undefined' ? 1000 : window.innerWidth;
  return Math.min(2000, Math.max(600, Math.round(width * 2)));
}

type ChartPanel = {
  groupKey: string;
  displayName: string;
  unit: string;
  chartOption: EChartsOption;
  legendRows: ChartLegendRow[];
  /** Круговая диаграмма: выше холст, без временной оси. */
  isPie?: boolean;
};

function escapeRegExpLiteral(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Матчит имя интерфейса как “токен”, чтобы `ens18` не совпадал с `ens18.10`.
 * Разрешаем символы внутри имени (в т.ч. `.`, `-`, `_`, `/`), но требуем границы
 * по классу токена [a-z0-9._-/].
 */
function buildInterfaceTokenMatcher(ifName: string): RegExp {
  const token = escapeRegExpLiteral(ifName.trim().toLowerCase());
  return new RegExp(`(^|[^a-z0-9._\\-/])${token}([^a-z0-9._\\-/]|$)`, 'i');
}

function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/** Локальная полуночь календарного дня. */
function startOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function endOfLocalDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999);
}

function formatLocalDateInput(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}

function valueMapByMetricFromPanel(panel: MetricChartPanelDto): Record<string, ValueMapMappings> {
  const out: Record<string, ValueMapMappings> = {};
  for (const meta of panel.seriesMeta ?? []) {
    if (isValueMapSeries(meta.valueMapMappings)) {
      out[meta.metricName] = meta.valueMapMappings!;
    }
  }
  for (const point of panel.points ?? []) {
    if (isValueMapSeries(point.valueMapMappings) && !out[point.metricName]) {
      out[point.metricName] = point.valueMapMappings!;
    }
  }
  return out;
}

function parseLocalDateInput(ymd: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd.trim());
  if (!m) return null;
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const day = Number(m[3]);
  if (!Number.isFinite(y) || !Number.isFinite(mo) || !Number.isFinite(day)) return null;
  const d = new Date(y, mo - 1, day);
  if (d.getFullYear() !== y || d.getMonth() !== mo - 1 || d.getDate() !== day) return null;
  return d;
}

function addLocalDays(d: Date, deltaDays: number): Date {
  const x = startOfLocalDay(d);
  x.setDate(x.getDate() + deltaDays);
  return x;
}

/** Число календарных дней включительно между двумя датами (локальные полуночи). */
function inclusiveLocalDaySpan(from: Date, to: Date): number {
  const a = startOfLocalDay(from).getTime();
  const b = startOfLocalDay(to).getTime();
  return Math.floor((b - a) / 86400000) + 1;
}

@Component({
  selector: 'app-device-metrics-chart',
  standalone: true,
  imports: [
    NgxEchartsDirective,
    FormsModule,
    InputTextModule,
    SelectButtonModule,
    PopoverModule,
    ChartLegendPlacementSelectComponent,
    ChartBaseColorSelectComponent,
    ChartLegendStatsPanelComponent,
  ],
  templateUrl: './device-metrics-chart.component.html',
  styleUrl: './device-metrics-chart.component.css',
})
export class DeviceMetricsChartComponent implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly chartUiPrefs = inject(ChartUiPreferencesService);
  private activeSub: Subscription | null = null;
  private loadMoreSub: Subscription | null = null;
  /** Фиксированный интервал запроса для совпадения страниц панелей при подгрузке. */
  private queryRange: { fromIso: string; toIso: string } | null = null;
  private readonly chartHosts = viewChildren(NgxEchartsDirective);
  private readonly loadMoreSentinel = viewChild<ElementRef<HTMLElement>>('loadMoreSentinel');
  private readonly customRangePopover = viewChild<Popover>('customRangePopover');
  private readonly periodSelectHost = viewChild('periodSelect', { read: ElementRef });
  private resizeTimer: number | null = null;
  private showCustomPopoverRaf: number | null = null;
  private readonly panelCharts = new Map<string, { chart: EChartsType; wrapper: HTMLElement }>();
  private readonly chartFinishedOffByKey = new Map<string, () => void>();
  private readonly chartWheelOffByKey = new Map<string, () => void>();

  readonly deviceId = input.required<string>();
  readonly ifName = input<string | null>(null);
  readonly metricKey = input<string | null>(null);
  readonly loading = signal(false);
  readonly periodOptions: { label: string; value: TimePeriod }[] = [
    { label: 'Час', value: 'HOUR' },
    { label: 'День', value: 'DAY' },
    { label: 'Неделя', value: 'WEEK' },
    { label: 'Месяц', value: 'MONTH' },
    { label: 'Произвольно', value: 'CUSTOM' },
  ];
  readonly layoutOptions: { label: string; value: ChartLayout; icon: string }[] = [
    { label: '1 в строке', value: 'SINGLE', icon: 'pi pi-window-maximize' },
    { label: '2 в строке', value: 'DOUBLE', icon: 'pi pi-th-large' },
  ];
  protected readonly legendPlacement = this.chartUiPrefs.deviceMetricsLegendPlacement;
  protected readonly baseColor = this.chartUiPrefs.deviceMetricsBaseColor;
  protected readonly selectedPeriod = this.chartUiPrefs.deviceMetricsPeriod;
  protected readonly selectedLayout = this.chartUiPrefs.deviceMetricsLayout;
  protected readonly customFromDay = computed(() => {
    const raw = this.chartUiPrefs.deviceMetricsCustomFrom();
    return raw ? parseLocalDateInput(raw) : null;
  });
  protected readonly customToDay = computed(() => {
    const raw = this.chartUiPrefs.deviceMetricsCustomTo();
    return raw ? parseLocalDateInput(raw) : null;
  });
  readonly chartPanels = signal<ChartPanel[]>([]);
  private readonly rawPanels = signal<MetricChartPanelDto[]>([]);
  readonly totalChartPanels = signal(0);
  readonly loadingMore = signal(false);
  protected readonly chartNameSearch = signal('');
  private readonly chartNameSearchDebounced = signal('');
  private readonly chartNameSearchApply$ = new Subject<void>();
  readonly hasMorePanels = computed(
    () => this.rawPanels().length > 0 && this.rawPanels().length < this.totalChartPanels()
  );
  constructor() {
    this.chartUiPrefs.ensureLoaded().subscribe();

    this.chartNameSearchApply$
      .pipe(debounceTime(350), takeUntilDestroyed())
      .subscribe(() => {
        this.chartNameSearchDebounced.set(this.chartNameSearch());
      });

    this.destroyRef.onDestroy(() => {
      for (const key of this.chartFinishedOffByKey.keys()) {
        this.detachChartFinishedListener(key);
      }
      for (const key of this.chartWheelOffByKey.keys()) {
        this.detachChartWheelListener(key);
      }
      this.panelCharts.clear();
    });

    effect(() => {
      const period = this.selectedPeriod();
      if (period !== 'CUSTOM') {
        return;
      }
      const cf = this.customFromDay();
      const ct = this.customToDay();
      if (!this.isReadyCustomRange(cf, ct)) {
        this.initCustomRangeDefaults();
      }
    });

    effect(() => {
      const id = this.deviceId();
      const period = this.selectedPeriod();
      const cf = this.customFromDay();
      const ct = this.customToDay();
      this.metricKey();
      this.ifName();
      this.chartNameSearchDebounced();
      if (period === 'CUSTOM') {
        if (!this.isReadyCustomRange(cf, ct)) {
          this.activeSub?.unsubscribe();
          this.loadMoreSub?.unsubscribe();
          this.loading.set(false);
          this.loadingMore.set(false);
          this.rawPanels.set([]);
          this.totalChartPanels.set(0);
          this.queryRange = null;
          return;
        }
        this.fetchMetricsCustomRange(id, cf!, ct!);
      } else {
        this.fetchMetricsPreset(id, period);
      }
    });

    effect(() => {
      // Resize charts after layout toggle (1/2 columns).
      // ECharts doesn't always detect CSS grid reflow immediately.
      this.selectedLayout();
      this.legendPlacement();
      this.baseColor();
      this.resizeChartsSoon();
    });

    effect(() => {
      const panels = this.rawPanels();
      const ifName = this.ifName();
      this.baseColor();
      const filteredLayout: MetricChartPanelDto[] = [];
      for (const panel of panels) {
        const filteredPoints = this.filterMetricsByInterfaceName(panel.points ?? [], ifName);
        const metricsInPoints = new Set(filteredPoints.map((p) => p.metricName));
        const left = (panel.metricNames ?? []).some((name) => metricsInPoints.has(name));
        const right = (panel.rightAxisMetricNames ?? []).some((name) => metricsInPoints.has(name));
        if (!left && !right) {
          continue;
        }
        filteredLayout.push({ ...panel, points: filteredPoints });
      }
      this.buildChartsFromPanels(filteredLayout);
      this.resizeChartsSoon();
    });

    // Графики монтируются лениво (@defer on viewport); после появления хостов — догоняем размер ECharts.
    effect(() => {
      const hosts = this.chartHosts();
      if (hosts.length > 0) {
        this.resizeChartsSoon();
      }
    });

    effect((onCleanup) => {
      const el = this.loadMoreSentinel()?.nativeElement;
      if (!el || !this.hasMorePanels() || this.loading()) {
        return;
      }
      const observer = new IntersectionObserver(
        (entries) => {
          if (!entries.some((e) => e.isIntersecting)) {
            return;
          }
          if (!this.hasMorePanels() || this.loadingMore() || this.loading()) {
            return;
          }
          this.fetchMorePanels();
        },
        { root: null, rootMargin: '400px 0px', threshold: 0 }
      );
      observer.observe(el);
      onCleanup(() => observer.disconnect());
    });
  }

  ngOnDestroy(): void {
    this.activeSub?.unsubscribe();
    this.loadMoreSub?.unsubscribe();
    if (this.showCustomPopoverRaf != null) {
      cancelAnimationFrame(this.showCustomPopoverRaf);
      this.showCustomPopoverRaf = null;
    }
    if (this.resizeTimer != null) {
      window.clearTimeout(this.resizeTimer);
      this.resizeTimer = null;
    }
  }

  private resizeChartsSoon(delayMs = 140): void {
    if (this.resizeTimer != null) window.clearTimeout(this.resizeTimer);

    // Small debounce helps avoid visible "jumping" while CSS grid reflows.
    this.resizeTimer = window.setTimeout(() => {
      // Two RAFs helps after grid/layout + DOM paint.
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          for (const host of this.chartHosts()) {
            host.resize();
          }
          this.syncAllTooltipMaxHeights();
        });
      });
    }, delayMs);
  }

  onPeriodChange(value: TimePeriod): void {
    const was = this.selectedPeriod();
    this.chartUiPrefs.setDeviceMetricsPeriod(value);
    if (value === 'CUSTOM') {
      if (was !== 'CUSTOM') {
        this.initCustomRangeDefaults();
      }
      this.scheduleShowCustomPopover();
    } else {
      this.customRangePopover()?.hide();
    }
  }

  /** Каждое нажатие на пункт «Произвольно» (в т.ч. когда он уже выбран). */
  onPeriodSelectControlClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (!target?.closest?.('[data-period-value="CUSTOM"]')) {
      return;
    }
    queueMicrotask(() => {
      if (this.selectedPeriod() !== 'CUSTOM') {
        return;
      }
      this.scheduleShowCustomPopover();
    });
  }

  onLayoutChange(value: ChartLayout): void {
    this.chartUiPrefs.setDeviceMetricsLayout(value);
  }

  onChartNameSearchInput(value: string): void {
    this.chartNameSearch.set(value);
    this.chartNameSearchApply$.next();
  }

  protected chartNameSearchActive(): string {
    return this.chartNameSearchDebounced().trim();
  }

  private initCustomRangeDefaults(): void {
    const to = startOfLocalDay(new Date());
    const from = addLocalDays(to, -7);
    this.chartUiPrefs.setDeviceMetricsCustomRange(formatLocalDateInput(from), formatLocalDateInput(to));
  }

  private persistCustomRange(from: Date | null, to: Date | null): void {
    this.chartUiPrefs.setDeviceMetricsCustomRange(
      from ? formatLocalDateInput(startOfLocalDay(from)) : null,
      to ? formatLocalDateInput(startOfLocalDay(to)) : null
    );
  }

  private scheduleShowCustomPopover(): void {
    if (this.showCustomPopoverRaf != null) {
      cancelAnimationFrame(this.showCustomPopoverRaf);
    }
    this.showCustomPopoverRaf = requestAnimationFrame(() => {
      this.showCustomPopoverRaf = null;
      this.showCustomRangePopoverAnchored();
    });
  }

  private showCustomRangePopoverAnchored(): void {
    const run = (): void => {
      const pop = this.customRangePopover();
      const host = this.periodSelectHost()?.nativeElement;
      if (!pop || !host) {
        return;
      }
      const rect = host.getBoundingClientRect();
      const ev = new MouseEvent('click', {
        bubbles: true,
        clientX: rect.left + rect.width / 2,
        clientY: rect.bottom,
      });
      pop.show(ev, host);
    };
    queueMicrotask(() => {
      requestAnimationFrame(() => {
        requestAnimationFrame(run);
      });
    });
  }

  onCustomFromInput(ymd: string): void {
    const parsed = parseLocalDateInput(ymd);
    if (!parsed) {
      this.persistCustomRange(null, this.customToDay());
      return;
    }
    let from = startOfLocalDay(parsed);
    const today = startOfLocalDay(new Date());
    if (from > today) {
      from = today;
    }
    let to = this.customToDay();
    if (to) {
      to = startOfLocalDay(to);
      if (from > to) {
        to = from;
      }
      if (inclusiveLocalDaySpan(from, to) > MAX_CUSTOM_RANGE_INCLUSIVE_DAYS) {
        to = addLocalDays(from, MAX_CUSTOM_RANGE_INCLUSIVE_DAYS - 1);
      }
      if (to > today) {
        to = today;
      }
    } else {
      to = from;
    }
    this.persistCustomRange(from, to);
  }

  onCustomToInput(ymd: string): void {
    const parsed = parseLocalDateInput(ymd);
    if (!parsed) {
      this.persistCustomRange(this.customFromDay(), null);
      return;
    }
    let to = startOfLocalDay(parsed);
    const today = startOfLocalDay(new Date());
    if (to > today) {
      to = today;
    }
    let from = this.customFromDay();
    if (!from) {
      this.persistCustomRange(to, to);
      return;
    }
    from = startOfLocalDay(from);
    if (to < from) {
      from = to;
    }
    if (inclusiveLocalDaySpan(from, to) > MAX_CUSTOM_RANGE_INCLUSIVE_DAYS) {
      from = addLocalDays(to, -(MAX_CUSTOM_RANGE_INCLUSIVE_DAYS - 1));
    }
    this.persistCustomRange(from, to);
  }

  /** Строка yyyy-MM-dd для атрибута min у поля «С» (не раньше чем «По» − 30 календарных дней). */
  minFromInputValue(): string | null {
    const to = this.customToDay();
    if (!to) {
      return null;
    }
    const t = startOfLocalDay(to);
    return formatLocalDateInput(addLocalDays(t, -(MAX_CUSTOM_RANGE_INCLUSIVE_DAYS - 1)));
  }

  /** Строка yyyy-MM-dd для атрибута max у поля «С». */
  maxFromInputValue(): string {
    const to = this.customToDay();
    const today = startOfLocalDay(new Date());
    if (!to) {
      return formatLocalDateInput(today);
    }
    const cap = startOfLocalDay(to) < today ? startOfLocalDay(to) : today;
    return formatLocalDateInput(cap);
  }

  /** Строка yyyy-MM-dd для атрибута min у поля «По». */
  minToInputValue(): string | null {
    const from = this.customFromDay();
    if (!from) {
      return null;
    }
    return formatLocalDateInput(startOfLocalDay(from));
  }

  /** Строка yyyy-MM-dd для атрибута max у поля «По». */
  maxToInputValue(): string {
    const from = this.customFromDay();
    const today = startOfLocalDay(new Date());
    if (!from) {
      return formatLocalDateInput(today);
    }
    const f = startOfLocalDay(from);
    const bySpan = addLocalDays(f, MAX_CUSTOM_RANGE_INCLUSIVE_DAYS - 1);
    const cap = bySpan < today ? bySpan : today;
    return formatLocalDateInput(cap);
  }

  customFromInputString(): string {
    const d = this.customFromDay();
    return d ? formatLocalDateInput(startOfLocalDay(d)) : '';
  }

  customToInputString(): string {
    const d = this.customToDay();
    return d ? formatLocalDateInput(startOfLocalDay(d)) : '';
  }

  private isReadyCustomRange(from: Date | null, to: Date | null): boolean {
    if (!from || !to) {
      return false;
    }
    const f = startOfLocalDay(from);
    const t = startOfLocalDay(to);
    const today = startOfLocalDay(new Date());
    if (f > t || t > today) {
      return false;
    }
    return inclusiveLocalDaySpan(f, t) <= MAX_CUSTOM_RANGE_INCLUSIVE_DAYS;
  }

  private fetchMetricsPreset(deviceId: string, period: Exclude<TimePeriod, 'CUSTOM'>): void {
    if (period === 'HOUR') {
      const { fromIso, toIso } = defaultHourMetricsRange();
      this.beginMetricsRequest(deviceId, fromIso, toIso);
      return;
    }
    if (period === 'DAY') {
      const { fromIso, toIso } = defaultDayMetricsRange();
      this.beginMetricsRequest(deviceId, fromIso, toIso);
      return;
    }
    const now = new Date();
    const from = new Date(now);
    if (period === 'WEEK') from.setDate(from.getDate() - 7);
    else from.setMonth(from.getMonth() - 1);
    this.beginMetricsRequest(deviceId, from.toISOString(), now.toISOString());
  }

  private fetchMetricsCustomRange(deviceId: string, fromDay: Date, toDay: Date): void {
    const fromIso = startOfLocalDay(fromDay).toISOString();
    const toIso = endOfLocalDay(toDay).toISOString();
    this.beginMetricsRequest(deviceId, fromIso, toIso);
  }

  private beginMetricsRequest(deviceId: string, fromIso: string, toIso: string): void {
    this.activeSub?.unsubscribe();
    this.loadMoreSub?.unsubscribe();
    this.loading.set(true);
    this.loadingMore.set(false);
    this.rawPanels.set([]);
    this.totalChartPanels.set(0);

    this.queryRange = { fromIso, toIso };

    const activeMetricKey = this.metricKey()?.trim() || null;
    const activeIfName = this.ifName()?.trim() || null;
    const params = this.buildMetricsQueryParams(
      0,
      activeMetricKey || activeIfName ? 0 : PANEL_PAGE_SIZE,
    );
    if (activeMetricKey) {
      params['metric'] = activeMetricKey;
    }

    this.activeSub = this.http
      .get<CompactMetricsHistoryResponseDto>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/metrics`, { params })
      .subscribe({
        next: (payload) => {
          this.rawPanels.set(expandCompactPanels(payload?.chartPanels ?? []));
          this.totalChartPanels.set(payload?.totalChartPanels ?? 0);
          this.loading.set(false);
        },
        error: () => {
          this.queryRange = null;
          this.rawPanels.set([]);
          this.totalChartPanels.set(0);
          this.loading.set(false);
        },
      });
  }

  private fetchMorePanels(): void {
    const id = this.deviceId();
    const range = this.queryRange;
    if (!range) {
      return;
    }
    if (this.metricKey()?.trim()) {
      return;
    }
    if (this.ifName()?.trim()) {
      return;
    }
    if (this.loadingMore() || this.loading()) {
      return;
    }
    if (this.rawPanels().length >= this.totalChartPanels()) {
      return;
    }

    this.loadMoreSub?.unsubscribe();
    this.loadingMore.set(true);
    const params = this.buildMetricsQueryParams(this.rawPanels().length, PANEL_PAGE_SIZE);
    const activeMetricKey = this.metricKey()?.trim();
    if (activeMetricKey) {
      params['metric'] = activeMetricKey;
    }
    this.loadMoreSub = this.http
      .get<CompactMetricsHistoryResponseDto>(`${this.apiBaseUrl}/api/monitoring/devices/${id}/metrics`, { params })
      .subscribe({
        next: (payload) => {
          const chunk = expandCompactPanels(payload?.chartPanels ?? []);
          this.rawPanels.update((prev) => [...prev, ...chunk]);
          this.totalChartPanels.set(payload?.totalChartPanels ?? this.totalChartPanels());
          this.loadingMore.set(false);
        },
        error: () => {
          this.loadingMore.set(false);
        },
      });
  }

  private filterMetricsByInterfaceName(raw: MetricValueDto[], ifName: string | null): MetricValueDto[] {
    const q = ifName?.trim();
    if (!q) return raw;

    const rx = buildInterfaceTokenMatcher(q);
    return raw.filter((m) => {
      const display = m.metricDisplayName ?? '';
      const name = m.metricName ?? '';
      return rx.test(display) || rx.test(name);
    });
  }

  private buildMetricsQueryParams(panelsOffset: number, panelsLimit: number): Record<string, string> {
    const range = this.queryRange;
    if (!range) {
      return {};
    }
    const params: Record<string, string> = {
      from: range.fromIso,
      to: range.toIso,
      panelsOffset: String(panelsOffset),
      panelsLimit: String(panelsLimit),
      maxPoints: String(resolveChartMaxPoints()),
    };
    const q = this.chartNameSearchDebounced().trim();
    if (q) {
      params['q'] = q;
    }
    return params;
  }

  private buildChartsFromPanels(layout: MetricChartPanelDto[]): void {
    for (const key of [...this.chartFinishedOffByKey.keys()]) {
      this.detachChartFinishedListener(key);
    }
    for (const key of [...this.chartWheelOffByKey.keys()]) {
      this.detachChartWheelListener(key);
    }
    this.panelCharts.clear();

    const baseColor = this.baseColor();
    const panels: ChartPanel[] = [];
    for (const panel of layout) {
      const sortedPoints = [...(panel.points ?? [])].sort(
        (a, b) => new Date(a.recordedAt).getTime() - new Date(b.recordedAt).getTime()
      );

      const displayNameByMetric: Record<string, string> = {};
      for (const m of sortedPoints) {
        const d = m.metricDisplayName?.trim();
        if (d) displayNameByMetric[m.metricName] = d;
      }

      const unitByMetric: Record<string, string> = {};
      for (const m of sortedPoints) {
        if (m.scaledUnit) unitByMetric[m.metricName] = m.scaledUnit;
        else if (m.unit) unitByMetric[m.metricName] = m.unit;
      }

      const metricsInPanel = [...new Set(panel.metricNames)];
      if (metricsInPanel.length === 0) continue;

      const valueMapByMetric = valueMapByMetricFromPanel(panel);

      if (panel.graphType?.toUpperCase() === 'PIE') {
        const slices: { name: string; value: number; unit: string; valueMapMappings?: ValueMapMappings }[] = [];
        for (const metricName of metricsInPanel) {
          const latest = this.latestScalarInPeriod(sortedPoints, metricName);
          if (latest == null) continue;
          const display = resolveMetricDisplayLabel(metricName, displayNameByMetric[metricName]);
          slices.push({
            name: display,
            value: latest.value,
            unit: isValueMapSeries(valueMapByMetric[metricName]) ? '' : latest.unit,
            valueMapMappings: valueMapByMetric[metricName],
          });
        }
        if (slices.length === 0) {
          continue;
        }
        const distinctUnits = [...new Set(slices.map((s) => s.unit).filter((u) => !!u))];
        const unitText =
          distinctUnits.length === 0 ? 'последняя точка' : distinctUnits.length === 1 ? distinctUnits[0]! : distinctUnits.join(' · ');
        panels.push({
          groupKey: panel.panelKey,
          displayName: panel.title || this.formatGroupName(panel.panelKey),
          unit: unitText,
          isPie: true,
          legendRows: buildStatRowsFromScalars(
            slices,
            slices.map((_, idx) => chartSeriesColor(idx))
          ),
          chartOption: this.buildPieChartOption(slices),
        });
        continue;
      }

      const layoutRightSet = new Set(panel.rightAxisMetricNames ?? []);
      let rightAxisMetricSet = layoutRightSet;
      const u0 = unitByMetric[metricsInPanel[0]] || '';
      const u1 = metricsInPanel.length >= 2 ? unitByMetric[metricsInPanel[1]] || '' : '';
      if (
        layoutRightSet.size === 0 &&
        metricsInPanel.length === 2 &&
        u0 &&
        u1 &&
        u0 !== u1
      ) {
        rightAxisMetricSet = new Set([metricsInPanel[1]]);
      }
      const leftAxisMetric = metricsInPanel.find((name) => !rightAxisMetricSet.has(name)) ?? metricsInPanel[0];
      const rightAxisMetric = metricsInPanel.find((name) => rightAxisMetricSet.has(name)) ?? null;
      const leftUnit = isValueMapSeries(valueMapByMetric[leftAxisMetric])
        ? ''
        : unitByMetric[leftAxisMetric] || '';
      const rightUnit =
        rightAxisMetric && !isValueMapSeries(valueMapByMetric[rightAxisMetric])
          ? unitByMetric[rightAxisMetric] || ''
          : '';
      const tooltipSeriesUnits = metricsInPanel.map((name) =>
        isValueMapSeries(valueMapByMetric[name]) ? '' : unitByMetric[name] || '',
      );
      const tooltipSeriesValueMaps = metricsInPanel.map((name) => valueMapByMetric[name] ?? null);

      const seriesEntries = metricsInPanel.map((metricName) => {
        const display = resolveMetricDisplayLabel(metricName, displayNameByMetric[metricName]);
        const mappings = valueMapByMetric[metricName];
        const useValueMap = isValueMapSeries(mappings);
        const points = sortedPoints
          .filter((m) => m.metricName === metricName)
          .map(
            (m) =>
              [
                new Date(m.recordedAt).getTime(),
                useValueMap ? m.metricValue : (m.scaledMetricValue ?? m.metricValue),
              ] as [number, number],
          );
        const stats = computeSeriesStats(points.map((point) => point[1]));
        return {
          metricName,
          display,
          points,
          max: stats.max,
          unit: useValueMap ? '' : unitByMetric[metricName] || '',
          valueMapMappings: mappings,
          useValueMap,
        };
      });
      const seriesStyles = chartSeriesStylesByMax(
        seriesEntries.map((entry) => entry.max),
        baseColor
      );

      const panelThresholds = panel.thresholds ?? [];
      const series = seriesEntries.map((entry, idx) => {
        const style = seriesStyles[idx];
        const lineColor = style?.line ?? chartSeriesColor(idx);
        const isRightAxis = rightAxisMetricSet.has(entry.metricName);
        const isStacked = panel.graphType?.toUpperCase() === 'STACKED' && !entry.useValueMap;
        return {
          name: entry.display,
          type: 'line' as const,
          showSymbol: false,
          smooth: false,
          step: entry.useValueMap ? ('end' as const) : undefined,
          connectNulls: true,
          lineStyle: { width: 2, color: lineColor },
          itemStyle: { color: lineColor },
          yAxisIndex: isRightAxis ? 1 : 0,
          stack: isStacked ? 'stacked-panel' : undefined,
          areaStyle: isStacked ? { color: style?.area ?? lineColor } : undefined,
          data: entry.points,
          z: 2,
        };
      });
      const thresholdOverlays = seriesEntries.flatMap((entry) => {
        const metricThresholds = thresholdsForMetric(panelThresholds, entry.metricName);
        if (metricThresholds.length === 0 || entry.points.length === 0) {
          return [];
        }
        const times = entry.points.map((point) => point[0]);
        const timeExtent: [number, number] = [Math.min(...times), Math.max(...times)];
        const isRightAxis = rightAxisMetricSet.has(entry.metricName);
        return buildThresholdLineSeries(
          metricThresholds,
          timeExtent,
          entry.unit,
          isRightAxis ? 1 : 0,
          entry.metricName,
          valueMapByMetric,
        );
      });
      const leftDataMax = seriesEntries
        .filter((entry) => !rightAxisMetricSet.has(entry.metricName))
        .map((entry) => entry.max ?? 0)
        .reduce((max, value) => Math.max(max, value), 0);
      const rightDataMax = seriesEntries
        .filter((entry) => rightAxisMetricSet.has(entry.metricName))
        .map((entry) => entry.max ?? 0)
        .reduce((max, value) => Math.max(max, value), 0);
      const leftYAxisMax = isValueMapSeries(valueMapByMetric[leftAxisMetric])
        ? undefined
        : resolvePercentYAxisMax(leftUnit, leftDataMax, panelThresholds, leftAxisMetric);
      const rightYAxisMax = rightAxisMetric
        ? isValueMapSeries(valueMapByMetric[rightAxisMetric])
          ? undefined
          : resolvePercentYAxisMax(rightUnit, rightDataMax, panelThresholds, rightAxisMetric)
        : undefined;

      const leftSeriesValues = seriesEntries
        .filter((entry) => !rightAxisMetricSet.has(entry.metricName))
        .flatMap((entry) => entry.points.map((point) => point[1]));
      const leftThresholdValues = panelThresholds
        .filter((threshold) => !rightAxisMetricSet.has(threshold.metricName))
        .map((threshold) => thresholdChartValue(threshold));
      const annotateThresholds = shouldAnnotateThresholdsOnChart(panelThresholds.length);
      const leftPresentValues = isValueMapSeries(valueMapByMetric[leftAxisMetric])
        ? collectValueMapAxisValues(
            leftSeriesValues,
            annotateThresholds ? leftThresholdValues : [],
          )
        : collectPresentValues(leftSeriesValues);
      const rightPresentValues = rightAxisMetric
        ? isValueMapSeries(valueMapByMetric[rightAxisMetric])
          ? collectValueMapAxisValues(
              seriesEntries
                .filter((entry) => rightAxisMetricSet.has(entry.metricName))
                .flatMap((entry) => entry.points.map((point) => point[1])),
              annotateThresholds
                ? panelThresholds
                    .filter((threshold) => rightAxisMetricSet.has(threshold.metricName))
                    .map((threshold) => thresholdChartValue(threshold))
                : [],
            )
          : collectPresentValues(
              seriesEntries
                .filter((entry) => rightAxisMetricSet.has(entry.metricName))
                .flatMap((entry) => entry.points.map((point) => point[1])),
            )
        : [];

      const singleDisplay = metricsInPanel.length === 1
        ? resolveMetricDisplayLabel(metricsInPanel[0], displayNameByMetric[metricsInPanel[0]])
        : undefined;

      const unitText = rightUnit && rightUnit !== leftUnit
        ? `${leftUnit} | ${rightUnit}`
        : leftUnit || rightUnit;

      const legendRows: ChartLegendRow[] = [
        ...buildStatRowsFromTimeSeries(
          seriesEntries.map((entry, idx) => ({
            name: entry.display,
            color: seriesStyles[idx]?.line ?? chartSeriesColor(idx),
            unit: entry.unit,
            data: entry.points,
            valueMapMappings: entry.valueMapMappings,
          }))
        ),
        ...buildThresholdLegendRows(panelThresholds, unitByMetric, metricsInPanel, valueMapByMetric),
      ];

      panels.push({
        groupKey: panel.panelKey,
        displayName: singleDisplay || panel.title || this.formatGroupName(panel.panelKey),
        unit: unitText,
        legendRows,
        chartOption: this.buildChartOption(
          [...series, ...thresholdOverlays],
          leftUnit,
          rightUnit,
          tooltipSeriesUnits,
          leftYAxisMax,
          rightYAxisMax,
          {
            leftValueMap: isValueMapSeries(valueMapByMetric[leftAxisMetric])
              ? { presentValues: leftPresentValues, mappings: valueMapByMetric[leftAxisMetric]! }
              : null,
            rightValueMap:
              rightAxisMetric && isValueMapSeries(valueMapByMetric[rightAxisMetric])
                ? { presentValues: rightPresentValues, mappings: valueMapByMetric[rightAxisMetric]! }
                : null,
            tooltipSeriesValueMaps,
          },
        ),
      });
    }

    this.chartPanels.set(panels);
  }

  protected onPanelChartInit(chart: unknown): void {
    const instance = chart as EChartsType;
    if (instance.isDisposed?.()) {
      return;
    }
    const dom = instance.getDom();
    const wrapper = dom?.closest('.chart-canvas-wrapper');
    if (!(wrapper instanceof HTMLElement)) {
      return;
    }
    const panelKey = wrapper.getAttribute('data-panel-key');
    if (!panelKey) {
      return;
    }

    this.detachChartFinishedListener(panelKey);
    this.detachChartWheelListener(panelKey);
    this.panelCharts.set(panelKey, { chart: instance, wrapper });
    this.attachChartWheelListener(panelKey, wrapper);

    const sync = () => this.syncTooltipMaxHeight(wrapper, instance);
    sync();
    const onFinished = () => sync();
    instance.on('finished', onFinished);
    this.chartFinishedOffByKey.set(panelKey, () => {
      if (!instance.isDisposed()) {
        instance.off('finished', onFinished);
      }
    });
  }

  /** Прокрутка тултипа колёсиком над графиком; страница при этом не скроллится. */
  protected onChartTooltipWheel(event: WheelEvent): void {
    const wrapper = event.currentTarget;
    if (!(wrapper instanceof HTMLElement)) {
      return;
    }

    const tooltip = wrapper.querySelector<HTMLElement>(`.${DEVICE_METRICS_CHART_TOOLTIP_CLASS}`);
    if (!tooltip) {
      return;
    }

    // Блокируем прокрутку страницы, пока над графиком крутят колёсико (в т.ч. у края списка в тултипе).
    event.preventDefault();
    event.stopPropagation();

    const body = tooltip.querySelector<HTMLElement>('.device-metrics-tooltip-body');
    const scrollEl = body ?? tooltip;
    if (scrollEl.scrollHeight <= scrollEl.clientHeight + 1) {
      return;
    }

    const maxScroll = scrollEl.scrollHeight - scrollEl.clientHeight;
    const nextScroll = Math.min(maxScroll, Math.max(0, scrollEl.scrollTop + event.deltaY));
    scrollEl.scrollTop = nextScroll;
  }

  private attachChartWheelListener(panelKey: string, wrapper: HTMLElement): void {
    const onWheel = (event: WheelEvent) => this.onChartTooltipWheel(event);
    wrapper.addEventListener('wheel', onWheel, { passive: false });
    this.chartWheelOffByKey.set(panelKey, () => wrapper.removeEventListener('wheel', onWheel));
  }

  private detachChartWheelListener(panelKey: string): void {
    this.chartWheelOffByKey.get(panelKey)?.();
    this.chartWheelOffByKey.delete(panelKey);
  }

  private detachChartFinishedListener(panelKey: string): void {
    this.chartFinishedOffByKey.get(panelKey)?.();
    this.chartFinishedOffByKey.delete(panelKey);
  }

  private syncTooltipMaxHeight(wrapper: HTMLElement, chart: EChartsType): void {
    if (chart.isDisposed()) {
      return;
    }
    const layout = wrapper.querySelector('.chart-legend-layout');
    if (layout instanceof HTMLElement) {
      syncChartPlotAreaCssVars(layout, chart);
    }
    const plotHeight = resolveChartPlotAreaHeight(chart);
    const maxH =
      plotHeight != null && plotHeight > 0
        ? Math.max(48, plotHeight - 4)
        : Math.max(72, Math.floor(wrapper.clientHeight * 0.35));
    wrapper.style.setProperty('--chart-tooltip-max-h', `${maxH}px`);
  }

  private syncAllTooltipMaxHeights(): void {
    for (const { chart, wrapper } of this.panelCharts.values()) {
      if (!chart.isDisposed()) {
        this.syncTooltipMaxHeight(wrapper, chart);
      }
    }
  }

  /** Последнее значение метрики в отсортированном по времени ряду (для PIE и снимков). */
  private latestScalarInPeriod(
    sortedPoints: MetricValueDto[],
    metricName: string
  ): { value: number; unit: string } | null {
    for (let i = sortedPoints.length - 1; i >= 0; i--) {
      const p = sortedPoints[i];
      if (p.metricName !== metricName) continue;
      const v = p.scaledMetricValue ?? p.metricValue;
      if (typeof v !== 'number' || !Number.isFinite(v)) continue;
      const u = (p.scaledUnit ?? p.unit ?? '').trim();
      return { value: v, unit: u };
    }
    return null;
  }

  protected legendLayoutClass(): string {
    return chartLegendPlacementClass(this.legendPlacement());
  }

  protected legendPanelBefore(): boolean {
    return legendPanelBeforeChart(this.legendPlacement());
  }

  protected panelMinHeightPx(panel: ChartPanel): number {
    return resolveChartPanelMinHeightPx(
      panel.legendRows.length,
      this.legendPlacement(),
      panel.legendRows.length > 0
    );
  }

  protected onLegendPlacementChange(placement: ChartLegendPlacement): void {
    this.chartUiPrefs.setDeviceMetricsLegendPlacement(placement);
  }

  protected onBaseColorChange(color: ChartBaseColor): void {
    this.chartUiPrefs.setDeviceMetricsBaseColor(color);
  }

  private buildPieChartOption(
    slices: { name: string; value: number; unit: string; valueMapMappings?: ValueMapMappings }[],
  ): EChartsOption {
    const metaByName = new Map(
      slices.map((s) => [s.name, { unit: s.unit, valueMapMappings: s.valueMapMappings }] as const),
    );
    return {
      animation: false,
      tooltip: {
        trigger: 'item',
        confine: true,
        enterable: false,
        className: DEVICE_METRICS_CHART_TOOLTIP_CLASS,
        formatter: (params: unknown) => {
          const p = params as { name?: string; percent?: number; value?: number; marker?: string };
          const meta = p.name != null ? metaByName.get(p.name) : undefined;
          const unit = meta?.unit ?? '';
          const pct = typeof p.percent === 'number' ? p.percent.toFixed(1) : '';
          const val =
            typeof p.value === 'number' && Number.isFinite(p.value)
              ? isValueMapSeries(meta?.valueMapMappings)
                ? this.escapeHtml(mapValueMapLabel(meta!.valueMapMappings!, p.value))
                : this.formatMetricValue(p.value)
              : '—';
          const unitSuffix = isValueMapSeries(meta?.valueMapMappings) ? '' : unit ? ` ${unit}` : '';
          return `<div style="min-width:200px;">
      <div style="font-weight:700;margin-bottom:4px;">${this.escapeHtml(p.name ?? '')}</div>
      <div>${p.marker ?? ''} <span style="font-variant-numeric:tabular-nums;font-weight:600;">${val}</span>
      ${this.escapeHtml(unitSuffix)}${pct ? ` (${pct}%)` : ''}</div>
    </div>`;
        },
      },
      legend: { show: false },
      series: [
        {
          type: 'pie',
          radius: ['36%', '70%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          itemStyle: {
            borderRadius: 4,
            borderColor: 'var(--ns-surface, #fff)',
            borderWidth: 2,
          },
          label: {
            formatter: (p: unknown) => {
              const x = p as { name: string; percent: number };
              const pct = typeof x.percent === 'number' ? x.percent.toFixed(0) : '';
              return `${x.name}\n${pct}%`;
            },
          },
          data: slices.map((s, idx) => ({
            name: s.name,
            value: s.value,
            itemStyle: { color: chartSeriesColor(idx) },
          })),
        },
      ],
    };
  }

  private buildChartOption(
    series: NonNullable<EChartsOption['series']>,
    leftUnit: string,
    rightUnit: string,
    tooltipSeriesUnits: string[],
    leftYAxisMax?: number,
    rightYAxisMax?: number,
    valueMapOptions?: {
      leftValueMap: { presentValues: number[]; mappings: ValueMapMappings } | null;
      rightValueMap: { presentValues: number[]; mappings: ValueMapMappings } | null;
      tooltipSeriesValueMaps: Array<ValueMapMappings | null>;
    },
  ): EChartsOption {
    const seriesList = Array.isArray(series) ? series : [series];
    const hasRightAxis = seriesList.some((entry: unknown) => (entry as { yAxisIndex?: number }).yAxisIndex === 1);
    const leftAxis: NonNullable<EChartsOption['yAxis']> = valueMapOptions?.leftValueMap
      ? buildValueMapYAxis(
          valueMapOptions.leftValueMap.presentValues,
          valueMapOptions.leftValueMap.mappings,
        )
      : {
          type: 'value',
          min: 0,
          ...(leftYAxisMax != null ? { max: leftYAxisMax } : {}),
          axisLabel: {
            hideOverlap: true,
            formatter: (v: number) => `${v}${leftUnit || ''}`,
          },
        };
    const rightAxis: NonNullable<EChartsOption['yAxis']> = valueMapOptions?.rightValueMap
      ? buildValueMapYAxis(
          valueMapOptions.rightValueMap.presentValues,
          valueMapOptions.rightValueMap.mappings,
        )
      : {
          type: 'value',
          min: 0,
          ...(rightYAxisMax != null ? { max: rightYAxisMax } : {}),
          axisLabel: {
            hideOverlap: true,
            formatter: (v: number) => `${v}${rightUnit || ''}`,
          },
        };
    return {
      animation: false,
      grid: {
        left: 48,
        right: hasRightAxis ? 52 : 16,
        top: 18,
        bottom: 32,
        containLabel: true,
      },
      legend: { show: false },
      tooltip: {
        trigger: 'axis',
        confine: true,
        enterable: false,
        className: DEVICE_METRICS_CHART_TOOLTIP_CLASS,
        axisPointer: {
          type: 'line',
          snap: false,
          animation: false,
        },
        formatter: (params: unknown) =>
          this.formatTooltip(
            params,
            tooltipSeriesUnits,
            leftUnit || rightUnit,
            valueMapOptions?.tooltipSeriesValueMaps,
          ),
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
          hideOverlap: true,
          formatter: (value: number) => this.formatAxisTime(value),
        },
      },
      yAxis: hasRightAxis ? [leftAxis, rightAxis] : leftAxis,
      series,
    };
  }

  private formatGroupName(groupKey: string): string {
    return groupKey.toUpperCase();
  }

  private formatAxisTime(valueMs: number): string {
    const d = new Date(valueMs);
    const period = this.selectedPeriod();
    if (period === 'DAY' || period === 'HOUR') {
      return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
    if (period === 'CUSTOM') {
      return d.toLocaleString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    }
    return d.toLocaleDateString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /**
   * Единицы в тултипе — по порядку серий (= порядку метрик в панели), из `unitByMetric`.
   * Привязка только к `yAxisIndex` недостаточна: read/write часто на одной оси, но с разными единицами.
   * Раньше для `axisIndex` в ECharts (ось курсора) обе линии получали одну подпись.
   */
  private formatTooltip(
    params: unknown,
    seriesUnits: string[],
    fallbackUnit: string,
    seriesValueMaps?: Array<ValueMapMappings | null>,
  ): string {
    const rows = Array.isArray(params) ? params : params != null ? [params] : [];
    if (rows.length === 0) return '';

    const axisValue =
      (rows[0] as { axisValue?: string | number } | undefined)?.axisValue ??
      (rows[0] as { axisValueLabel?: string } | undefined)?.axisValueLabel;

    const axisText =
      typeof axisValue === 'number'
        ? this.formatAxisTime(axisValue)
        : typeof axisValue === 'string'
          ? axisValue
          : '';

    const lines = rows
      .filter((r) => {
        const name = (r as { seriesName?: string }).seriesName ?? '';
        return !name.startsWith(THRESHOLD_OVERLAY_SERIES_PREFIX);
      })
      .map((r) => {
      const anyR = r as {
        marker?: string;
        seriesName?: string;
        data?: unknown;
        value?: unknown;
        seriesIndex?: number;
      };

      const pair = (Array.isArray(anyR.data) ? anyR.data : Array.isArray(anyR.value) ? anyR.value : null) as
        | [unknown, unknown]
        | null;
      const v = pair ? pair[1] : null;
      const si = anyR.seriesIndex ?? 0;
      const unitText = (seriesUnits[si] ?? fallbackUnit) || '';
      const valueMap = seriesValueMaps?.[si];

      const valueText =
        typeof v === 'number' && Number.isFinite(v)
          ? isValueMapSeries(valueMap)
            ? this.escapeHtml(mapValueMapLabel(valueMap!, v))
            : this.formatMetricValue(v)
          : '—';

      const unitCell =
        isValueMapSeries(valueMap) || !unitText.trim() ? '' : this.escapeHtml(unitText);
      const rowClass = unitCell ? 'device-metrics-tooltip-row' : 'device-metrics-tooltip-row device-metrics-tooltip-row--no-unit';

      return `<div class="${rowClass}">
        <div class="device-metrics-tooltip-row-name">
          ${anyR.marker ?? ''}
          <span>${this.escapeHtml(anyR.seriesName ?? '')}</span>
        </div>
        <span class="device-metrics-tooltip-row-value">${valueText}</span>
        ${unitCell ? `<span class="device-metrics-tooltip-row-unit">${unitCell}</span>` : ''}
      </div>`;
    });

    const body = lines.join('');
    if (!axisText) {
      return `<div class="device-metrics-tooltip"><div class="device-metrics-tooltip-body">${body}</div></div>`;
    }
    return `<div class="device-metrics-tooltip">
      <div class="device-metrics-tooltip-head">${this.escapeHtml(axisText)}</div>
      <div class="device-metrics-tooltip-body">${body}</div>
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
}
