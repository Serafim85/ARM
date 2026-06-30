import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { ChartLegendPlacementSelectComponent } from '../../../components/chart-legend-placement-select/chart-legend-placement-select.component';
import { DeviceOptionSelectComponent, type DeviceOptionSelectItem } from '../../../components/device-option-select/device-option-select.component';
import {
  DEFAULT_CHART_LEGEND_PLACEMENT,
  normalizeChartLegendPlacement,
  type ChartLegendPlacement,
} from '../../../utils/chart-legend-placement';
import { API_BASE_URL } from '../../../api-config';
import type { WidgetFieldRecord, WidgetFieldUpsert } from '../../../models';
import type { DeviceMetricsHistoryResponseDto } from '../../monitoring-page/device-metrics-history.types';
import type { WidgetFieldsEditorApi } from './widget-editor.types';
import { resolvePeriodRange, GRAPH_WIDGET_PERIOD_OPTIONS, type GraphWidgetPeriod } from '../dashboard-graph-widget/graph-widget-config';
import { resolveMetricDisplayLabel } from '../../../utils/metric-display-label';

type GraphSeriesRow = {
  deviceId: number | null;
  metricName: string;
};

/** Один блок в UI: одно устройство и несколько метрик (в JSON виджета — плоский series). */
type GraphSeriesBlock = {
  deviceId: number | null;
  metricNames: string[];
};

type MonitoringDeviceRow = {
  id: number;
  name: string;
  ip: string;
};

type MonitoringDevicePageResponse = {
  content: MonitoringDeviceRow[];
};

type MetricSelectOption = { value: string; label: string };

@Component({
  selector: 'app-graph-widget-fields',
  standalone: true,
  imports: [
    FormsModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    ButtonModule,
    DeviceOptionSelectComponent,
    ChartLegendPlacementSelectComponent,
  ],
  templateUrl: './graph-widget-fields.component.html',
  styleUrl: './graph-widget-fields.component.css',
})
export class GraphWidgetFieldsComponent implements WidgetFieldsEditorApi {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  protected readonly periodOptions = GRAPH_WIDGET_PERIOD_OPTIONS;

  protected period = 'DAY';
  protected showLegend = true;
  protected legendPlacement: ChartLegendPlacement = DEFAULT_CHART_LEGEND_PLACEMENT;
  protected fill = false;
  protected blocks: GraphSeriesBlock[] = [{ deviceId: null, metricNames: [] }];
  protected deviceOptions: DeviceOptionSelectItem[] = [];
  /** Ключ: `${deviceId}:${GraphWidgetPeriod}` — совпадает с горизонтом вкладки «Графики метрик». */
  protected metricOptionsCache: Record<string, MetricSelectOption[]> = {};

  constructor() {
    this.loadDeviceOptions();
  }

  patchFromFields(fields: WidgetFieldRecord[]): void {
    const map = new Map(fields.map((x) => [x.name, x]));
    this.period = this.pickString(map, 'period', 'DAY');
    this.showLegend = this.pickBool(map, 'show_legend', true);
    this.legendPlacement = normalizeChartLegendPlacement(
      this.pickString(map, 'legend_placement', DEFAULT_CHART_LEGEND_PLACEMENT)
    );
    this.fill = this.pickBool(map, 'fill', false);

    const parsedRows = this.parseSeriesRows(this.pickString(map, 'series', '[]'));
    this.blocks = this.seriesRowsToBlocks(parsedRows);
    this.metricOptionsCache = {};
    for (const block of this.blocks) {
      if (block.deviceId != null) {
        this.ensureMetricOptionsLoaded(block.deviceId);
      }
    }
  }

  buildFields(): WidgetFieldUpsert[] {
    const normalizedRows = this.flattenBlocksToSeries(this.blocks);

    return [
      { name: 'period', valueInt: 0, valueStr: this.normalizePeriod(this.period) },
      { name: 'series', valueInt: 0, valueStr: JSON.stringify(normalizedRows) },
      this.boolField('show_legend', this.showLegend),
      { name: 'legend_placement', valueInt: 0, valueStr: this.legendPlacement },
      this.boolField('fill', this.fill),
    ];
  }

  protected addRow(): void {
    this.blocks = [...this.blocks, { deviceId: null, metricNames: [] }];
  }

  protected removeRow(index: number): void {
    const next = this.blocks.filter((_, i) => i !== index);
    this.blocks = next.length > 0 ? next : [{ deviceId: null, metricNames: [] }];
  }

  /** p-multiSelect при очистке может отдать `null` вместо пустого массива. */
  protected onBlockMetricsChange(index: number, value: string[] | null | undefined): void {
    const next = [...this.blocks];
    const block = next[index];
    if (!block) {
      return;
    }
    next[index] = { ...block, metricNames: Array.isArray(value) ? [...value] : [] };
    this.blocks = next;
  }

  protected onPeriodChange(value: string | number | null): void {
    const raw = typeof value === 'string' ? value : String(value ?? 'DAY');
    const nextPeriod = this.normalizePeriod(raw);
    if (nextPeriod === this.period) {
      return;
    }
    this.period = nextPeriod;
    this.metricOptionsCache = {};
    for (const block of this.blocks) {
      if (block.deviceId != null) {
        this.ensureMetricOptionsLoaded(block.deviceId);
      }
    }
  }

  protected onDeviceChange(index: number, deviceId: string | number | null): void {
    const id =
      deviceId == null
        ? null
        : typeof deviceId === 'number'
          ? deviceId
          : Number(deviceId);
    const normalized = id != null && Number.isFinite(id) ? Math.trunc(id) : null;
    const prevBlock = this.blocks[index];
    const prev = prevBlock?.deviceId ?? null;
    const prevMetrics = prevBlock?.metricNames ?? [];
    const next = [...this.blocks];
    next[index] = {
      deviceId: normalized,
      metricNames: normalized === prev ? [...prevMetrics] : [],
    };
    this.blocks = next;
    if (normalized != null) {
      this.ensureMetricOptionsLoaded(normalized);
    }
  }

  protected metricOptions(deviceId: number | null): MetricSelectOption[] {
    if (deviceId == null) {
      return [];
    }
    const key = this.metricCacheKey(deviceId);
    if (!Object.hasOwn(this.metricOptionsCache, key)) {
      return [];
    }
    return this.metricOptionsCache[key];
  }

  private metricCacheKey(deviceId: number): string {
    return `${deviceId}:${this.normalizePeriod(this.period) as GraphWidgetPeriod}`;
  }

  private loadDeviceOptions(): void {
    this.http
      .get<MonitoringDevicePageResponse>(`${this.apiBaseUrl}/api/monitoring`, {
        params: { page: '0', size: '200', sortField: 'ip', sortOrder: 'asc' },
      })
      .subscribe({
        next: (response) => {
          this.deviceOptions = (response.content ?? []).map((row) => ({
            id: row.id,
            label: `#${row.id} · ${row.name} (${row.ip})`,
          }));
        },
        error: () => {
          this.deviceOptions = [];
        },
      });
  }

  private ensureMetricOptionsLoaded(deviceId: number): void {
    const period = this.normalizePeriod(this.period) as GraphWidgetPeriod;
    const cacheKey = `${deviceId}:${period}`;
    if (Object.hasOwn(this.metricOptionsCache, cacheKey)) {
      return;
    }
    const range = resolvePeriodRange(period, new Date());
    this.http
      .get<DeviceMetricsHistoryResponseDto>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/metrics`, {
        params: { from: range.fromIso, to: range.toIso, maxPoints: '1' },
      })
      .subscribe({
        next: (payload) => {
          const keys = this.deriveMetricOptionKeysFromHistory(payload);
          const options = this.buildMetricSelectOptions(payload, keys);
          this.metricOptionsCache = { ...this.metricOptionsCache, [cacheKey]: options };
        },
        error: () => {
          this.metricOptionsCache = { ...this.metricOptionsCache, [cacheKey]: [] };
        },
      });
  }

  /**
   * Подписи в списке: как на вкладке «Графики метрик» — resolveMetricDisplayLabel;
   * для панелей с несколькими рядами добавляется заголовок панели (как у заголовка графика на вкладке).
   */
  private buildMetricSelectOptions(
    payload: DeviceMetricsHistoryResponseDto,
    orderedMetricNames: string[]
  ): MetricSelectOption[] {
    const series = (payload.chartPanels ?? []).flatMap((p) => p.series ?? []);
    const displayNameByMetric: Record<string, string> = {};
    for (const s of series) {
      const d = s.displayName?.trim();
      if (d) {
        displayNameByMetric[s.metricName] = d;
      }
    }

    const panelMetaByMetric = new Map<string, { title: string; distinctMetricCount: number }>();
    for (const panel of payload.chartPanels ?? []) {
      const names = new Set<string>();
      for (const n of panel.metricNames ?? []) {
        const t = n?.trim();
        if (t) {
          names.add(t);
        }
      }
      for (const n of panel.rightAxisMetricNames ?? []) {
        const t = n?.trim();
        if (t) {
          names.add(t);
        }
      }
      const distinctMetricCount = names.size;
      const title = panel.title?.trim() ?? '';
      for (const mn of names) {
        if (!panelMetaByMetric.has(mn)) {
          panelMetaByMetric.set(mn, { title, distinctMetricCount });
        }
      }
    }

    const rows = orderedMetricNames.map((metricName) => {
      const base = resolveMetricDisplayLabel(metricName, displayNameByMetric[metricName]);
      const meta = panelMetaByMetric.get(metricName);
      if (meta && meta.distinctMetricCount > 1 && meta.title.length > 0) {
        return { value: metricName, label: `${meta.title} — ${base}` };
      }
      return { value: metricName, label: base };
    });
    return rows.sort((a, b) => a.label.localeCompare(b.label, 'ru'));
  }

  /**
   * Ключи метрик как на вкладке «Графики метрик»: из истории за интервал;
   * при непустых панелях — пересечение с метриками из chartPanels (как после фильтра раскладки).
   */
  /**
   * Сохранённый series — плоский список; в UI группируем подряд идущие строки с одним deviceId.
   */
  private seriesRowsToBlocks(rows: GraphSeriesRow[]): GraphSeriesBlock[] {
    const meaningful = rows.filter(
      (r) => r.deviceId != null || (r.metricName?.trim().length ?? 0) > 0
    );
    if (meaningful.length === 0) {
      return [{ deviceId: null, metricNames: [] }];
    }
    const blocks: GraphSeriesBlock[] = [];
    for (const row of meaningful) {
      const m = row.metricName.trim();
      const d = row.deviceId;
      const last = blocks[blocks.length - 1];
      if (last && last.deviceId === d && d != null) {
        if (m && !last.metricNames.includes(m)) {
          last.metricNames.push(m);
        }
      } else {
        blocks.push({
          deviceId: d,
          metricNames: m ? [m] : [],
        });
      }
    }
    return blocks;
  }

  private flattenBlocksToSeries(
    blocks: GraphSeriesBlock[]
  ): { deviceId: number; metricName: string }[] {
    const out: { deviceId: number; metricName: string }[] = [];
    for (const b of blocks) {
      if (b.deviceId == null || !Number.isFinite(b.deviceId)) {
        continue;
      }
      const deviceId = Math.floor(b.deviceId);
      for (const raw of b.metricNames ?? []) {
        const metricName = String(raw).trim();
        if (!metricName) {
          continue;
        }
        out.push({ deviceId, metricName });
      }
    }
    return out;
  }

  private deriveMetricOptionKeysFromHistory(payload: DeviceMetricsHistoryResponseDto): string[] {
    const series = (payload.chartPanels ?? []).flatMap((p) => p.series ?? []);
    const panels = payload.chartPanels ?? [];

    const order: string[] = [];
    const seen = new Set<string>();
    for (const s of series) {
      const n = s.metricName?.trim();
      if (!n || seen.has(n)) {
        continue;
      }
      seen.add(n);
      order.push(n);
    }

    if (panels.length === 0) {
      return [...order].sort((a, b) => a.localeCompare(b, 'ru'));
    }

    const panelNames = new Set<string>();
    for (const panel of panels) {
      for (const n of panel.metricNames ?? []) {
        const t = n?.trim();
        if (t) {
          panelNames.add(t);
        }
      }
      for (const n of panel.rightAxisMetricNames ?? []) {
        const t = n?.trim();
        if (t) {
          panelNames.add(t);
        }
      }
    }

    return order.filter((n) => panelNames.has(n)).sort((a, b) => a.localeCompare(b, 'ru'));
  }

  private parseSeriesRows(raw: string): GraphSeriesRow[] {
    const text = raw.trim();
    if (!text) {
      return [];
    }
    try {
      const parsed = JSON.parse(text) as unknown;
      if (!Array.isArray(parsed)) {
        return [];
      }
      const out: GraphSeriesRow[] = [];
      for (const item of parsed) {
        if (!item || typeof item !== 'object') {
          continue;
        }
        const deviceId = Number((item as { deviceId?: unknown }).deviceId);
        const metricName = String((item as { metricName?: unknown }).metricName ?? '').trim();
        if (!Number.isFinite(deviceId) || deviceId <= 0) {
          continue;
        }
        out.push({
          deviceId: Math.floor(deviceId),
          metricName,
        });
      }
      return out;
    } catch {
      return [];
    }
  }

  private normalizePeriod(value: string): string {
    if (value === 'HOUR' || value === 'WEEK' || value === 'MONTH' || value === 'DAY') {
      return value;
    }
    return 'DAY';
  }

  private boolField(name: string, value: boolean): WidgetFieldUpsert {
    return { name, valueInt: value ? 1 : 0, valueStr: '' };
  }

  private pickBool(map: Map<string, WidgetFieldRecord>, name: string, fallback: boolean): boolean {
    const value = map.get(name);
    if (!value) {
      return fallback;
    }
    return value.valueInt === 1;
  }

  private pickString(map: Map<string, WidgetFieldRecord>, name: string, fallback: string): string {
    const value = map.get(name);
    if (!value || !value.valueStr) {
      return fallback;
    }
    return value.valueStr;
  }
}
