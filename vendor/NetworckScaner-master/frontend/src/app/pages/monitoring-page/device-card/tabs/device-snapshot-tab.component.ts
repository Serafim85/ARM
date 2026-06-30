import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule } from 'primeng/paginator';
import { TableModule } from 'primeng/table';
import type { PaginatorState } from 'primeng/types/paginator';
import { debounceTime, Subject } from 'rxjs';
import { MetricLabelCellComponent } from '../../../../components/metric-label-cell/metric-label-cell.component';
import { DeviceScanResult, MonitoringItemState } from '../../../../models';
import { MonitoringService } from '../../../../services/monitoring.service';
import {
  formatThresholdTriggerCaption,
  isThresholdBreached,
} from '../../../../utils/metric-threshold.util';
import { isValueMapSeries } from '../../../../utils/valuemap-chart.util';
/** Таблица для JSON walk: массив однотипных объектов (SNMP walk и т.п.). */
type WalkJsonTable = {
  columns: string[];
  rows: Record<string, string>[];
};

function isWalkJsonRow(v: unknown): v is Record<string, unknown> {
  return v !== null && typeof v === 'object' && !Array.isArray(v);
}

@Component({
  selector: 'app-device-snapshot-tab',
  standalone: true,
  imports: [TableModule, DatePipe, MetricLabelCellComponent, InputTextModule, PaginatorModule],
  templateUrl: './device-snapshot-tab.component.html',
  styleUrl: './device-snapshot-tab.component.css',
})
export class DeviceSnapshotTabComponent implements OnInit {
  readonly device = input.required<DeviceScanResult>();
  private readonly ms = inject(MonitoringService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly q = signal('');
  protected readonly pageSize = signal(20);
  protected readonly pageIndex = signal(0);
  private readonly metricSearchApply$ = new Subject<void>();
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  protected readonly itemPage = computed(() => this.ms.deviceItemStatePage()[this.device().id] ?? null);
  protected readonly snapshotRows = computed(() => this.itemPage()?.content ?? []);
  protected readonly snapshotTotalElements = computed(() => this.itemPage()?.totalElements ?? 0);
  protected readonly loading = computed(() => this.ms.deviceItemStateLoading()[this.device().id] ?? false);
  protected readonly tableFirst = computed(() => {
    const p = this.itemPage();
    return p ? p.number * p.size : 0;
  });

  constructor() {
    effect(() => {
      const deviceId = this.device().id;
      const cached = this.ms.deviceChartMetricKeys()[deviceId];
      if (cached === undefined) {
        this.ms.ensureDeviceChartMetricKeys(deviceId);
      }
    });

    this.metricSearchApply$
      .pipe(debounceTime(350), takeUntilDestroyed())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.loadPage();
      });

    this.destroyRef.onDestroy(() => {
      if (this.pollTimer !== null) {
        window.clearInterval(this.pollTimer);
      }
    });
  }

  ngOnInit(): void {
    this.loadPage();
    this.pollTimer = window.setInterval(() => {
      if (!document.hidden) {
        this.loadPage();
      }
    }, 60_000);
  }

  protected canOpenMetricCharts(itemKey: string): boolean {
    this.ms.deviceChartMetricKeys();
    return this.ms.hasDeviceChartMetric(this.device().id, itemKey);
  }

  protected openMetricCharts(item: MonitoringItemState): void {
    const metricKey = item.itemKey?.trim();
    if (!metricKey || !this.canOpenMetricCharts(metricKey)) return;

    void this.router.navigate(['../metrics'], {
      relativeTo: this.route,
      queryParams: { metricKey },
      queryParamsHandling: 'merge',
    });
  }

  protected onPageChange(state: PaginatorState): void {
    const rows = state.rows ?? this.pageSize();
    const first = state.first ?? 0;
    const page = rows > 0 ? Math.floor(first / rows) : 0;
    if (this.pageIndex() === page && this.pageSize() === rows) {
      return;
    }
    this.pageSize.set(rows);
    this.pageIndex.set(page);
    this.loadPage();
  }

  protected onSearchInput(value: string): void {
    this.q.set(value);
    this.metricSearchApply$.next();
  }

  protected metricValue(item: MonitoringItemState): string {
    if (item.valueMapName) {
      return item.scaledDisplayValue?.trim() || item.presentationValue?.trim() || '—';
    }
    if (item.scaledNumericValue != null) return this.formatNumber(item.scaledNumericValue);
    return this.snapshotRawString(item) ?? '—';
  }

  /**
   * Если значение — JSON-массив объектов (метрики *.walk), возвращает колонки и строки для таблицы.
   */
  protected walkJsonTable(item: MonitoringItemState): WalkJsonTable | null {
    const raw = this.snapshotRawString(item);
    if (raw == null) return null;
    let parsed: unknown;
    try {
      parsed = JSON.parse(raw) as unknown;
    } catch {
      return null;
    }
    if (!Array.isArray(parsed) || parsed.length === 0) return null;
    if (!parsed.every(isWalkJsonRow)) return null;
    const objRows = parsed as Record<string, unknown>[];
    const keySet = new Set<string>();
    for (const r of objRows) {
      for (const k of Object.keys(r)) keySet.add(k);
    }
    const columns = this.orderWalkColumns([...keySet]);
    if (columns.length === 0) return null;
    const rows = objRows.map((r) => {
      const out: Record<string, string> = {};
      for (const c of columns) {
        out[c] = this.walkCellString(r[c]);
      }
      return out;
    });
    return { columns, rows };
  }

  /** Сырая строка для ячейки (без форматирования scaled number). */
  private snapshotRawString(item: MonitoringItemState): string | null {
    if (item.scaledNumericValue != null) return null;
    const fromText = item.presentationValue?.trim() || item.textValue?.trim();
    if (fromText) return fromText;
    if (item.numericValue != null) return String(item.numericValue);
    return null;
  }

  private walkCellString(v: unknown): string {
    if (v === null || v === undefined) return '';
    if (typeof v === 'object') return JSON.stringify(v);
    return String(v);
  }

  /** index → col1, col2, … → остальные ключи по алфавиту. */
  private orderWalkColumns(keys: string[]): string[] {
    const set = new Set(keys);
    const out: string[] = [];
    if (set.delete('index')) out.push('index');
    const colKeys = [...set].filter((k) => /^col\d+$/i.test(k));
    colKeys.sort((a, b) => {
      const na = Number(/^col(\d+)$/i.exec(a)?.[1] ?? 0);
      const nb = Number(/^col(\d+)$/i.exec(b)?.[1] ?? 0);
      return na - nb;
    });
    for (const k of colKeys) set.delete(k);
    const rest = [...set].sort((a, b) => a.localeCompare(b, 'ru'));
    return [...out, ...colKeys, ...rest];
  }

  protected metricUnit(item: MonitoringItemState): string {
    if (item.scaledUnitLabel && item.scaledUnitLabel.trim()) return item.scaledUnitLabel;
    return item.unitLabel || '—';
  }

  protected thresholdLabels(item: MonitoringItemState): Array<{ label: string; breached: boolean }> {
    const thresholds = item.thresholds ?? [];
    if (thresholds.length === 0) {
      return [];
    }
    const actual = item.scaledNumericValue ?? item.numericValue;
    const unit = this.metricUnit(item);
    const itemMappings = isValueMapSeries(item.valueMapMappings) ? item.valueMapMappings : null;
    return thresholds.map((threshold) => {
      const mappings = isValueMapSeries(threshold.valueMapMappings)
        ? threshold.valueMapMappings
        : itemMappings;
      const useValueMap = isValueMapSeries(mappings);
      const value = useValueMap
        ? threshold.thresholdValue
        : (threshold.scaledThresholdValue ?? threshold.thresholdValue);
      return {
        label: formatThresholdTriggerCaption(
          threshold.triggerName,
          threshold.operator,
          value,
          useValueMap ? null : unit === '—' ? null : unit,
          mappings,
        ),
        breached: isThresholdBreached(actual, value, threshold.operator),
      };
    });
  }

  private loadPage(): void {
    this.ms.loadDeviceItemStatePage(this.device().id, this.q(), this.pageIndex(), this.pageSize());
  }

  private formatNumber(value: number): string {
    return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 }).format(value);
  }
}
