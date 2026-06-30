import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { debounceTime, Subject } from 'rxjs';
import type { MeterItem } from 'primeng/types/metergroup';
import type { PaginatorState } from 'primeng/types/paginator';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule } from 'primeng/paginator';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { MeterGroupModule } from 'primeng/metergroup';
import { TimelineModule } from 'primeng/timeline';
import { MonitoringEventsTableComponent } from '../../../events-page/monitoring-events-table/monitoring-events-table.component';
import { DeviceOptionSelectComponent } from '../../../../components/device-option-select/device-option-select.component';
import {
  DeviceScanResult,
  MonitoringEvent,
  MonitoringEventFilter,
  MonitoringEventLevel,
  MonitoringEventStatus,
  monitoringEventLevelChipClass,
  monitoringEventLevelLabel,
  monitoringEventStatusLabel,
  normalizeMonitoringEventLevel,
} from '../../../../models';
import { MonitoringService } from '../../../../services/monitoring.service';
import { resolveMetricDisplayLabel } from '../../../../utils/metric-display-label';

@Component({
  selector: 'app-device-events-tab',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MonitoringEventsTableComponent,
    ButtonModule,
    InputTextModule,
    SelectModule,
    DeviceOptionSelectComponent,
    SelectButtonModule,
    TimelineModule,
    PaginatorModule,
    MeterGroupModule,
  ],
  templateUrl: './device-events-tab.component.html',
  styleUrl: './device-events-tab.component.css',
})
export class DeviceEventsTabComponent {
  readonly device = input.required<DeviceScanResult>();

  protected readonly monitoringEventLevelChipClass = monitoringEventLevelChipClass;
  protected readonly monitoringEventLevelLabel = monitoringEventLevelLabel;
  protected readonly monitoringEventStatusLabel = monitoringEventStatusLabel;
  protected readonly normalizeMonitoringEventLevel = normalizeMonitoringEventLevel;
  protected readonly resolveMetricDisplayLabel = resolveMetricDisplayLabel;

  private readonly ms = inject(MonitoringService);
  private readonly metricSearchApply$ = new Subject<void>();

  /** Клиентская сортировка текущей страницы (lazy + API без sort). */
  protected readonly sortField = signal<string | null>(null);
  protected readonly sortOrder = signal<number>(1);

  protected readonly statusFilter = signal<MonitoringEventStatus | 'ALL'>('ALL');
  protected readonly metricSearch = signal('');
  /** Фильтр по уровню порога из клика по тегу в таблице (как на странице «События»). */
  protected readonly thresholdLevelFilter = signal<MonitoringEventLevel | null>(null);

  protected readonly statusOptions = [
    { label: 'Все', value: 'ALL' as const },
    { label: 'Активные', value: 'OPEN' as const },
    { label: 'Устранённые', value: 'RESOLVED' as const },
  ];

  /** Таблица или компактный таймлайн (те же данные и пагинация). */
  protected eventsViewMode: 'table' | 'timeline' = 'table';

  protected readonly eventsViewModeOptions = [
    { mode: 'table' as const, icon: 'pi pi-table', label: 'Таблица' },
    { mode: 'timeline' as const, icon: 'pi pi-clock', label: 'Таймлайн' },
  ];

  protected readonly eventPage = computed(() => {
    const id = this.device().id;
    return this.ms.deviceScopedEventsPage()[id] ?? null;
  });

  protected readonly loading = computed(() => {
    const id = this.device().id;
    return this.ms.deviceScopedEventsLoading()[id] ?? false;
  });

  protected readonly tableFirst = computed(() => {
    const ep = this.eventPage();
    if (!ep) return 0;
    return ep.number * ep.size;
  });

  protected readonly sortedEvents = computed(() => {
    const field = this.sortField();
    const order = this.sortOrder();
    const rows = [...(this.eventPage()?.content ?? [])];
    if (!field || rows.length === 0) {
      return rows;
    }
    rows.sort((a, b) => order * this.compareByField(a, b, field));
    return rows;
  });

  protected readonly eventsTotal = computed(() => this.eventPage()?.totalElements ?? 0);

  protected readonly eventsOnPage = computed(() => this.eventPage()?.content?.length ?? 0);

  protected readonly eventsOpenOnPage = computed(() => {
    const c = this.eventPage()?.content;
    if (!c?.length) return 0;
    return c.filter((e) => e.status === 'OPEN').length;
  });

  protected readonly eventsResolvedOnPage = computed(() => {
    const c = this.eventPage()?.content;
    if (!c?.length) return 0;
    return c.filter((e) => e.status === 'RESOLVED').length;
  });

  /** Текст подсказки в общей таблице событий (пустая выборка). */
  protected readonly eventsTableEmptyHint = computed(() => {
    if (this.statusFilter() !== 'ALL' || this.metricSearch().trim()) {
      return 'Попробуйте изменить фильтры.';
    }
    return 'Нарушения порогов метрик для этого устройства не зафиксированы.';
  });

  constructor() {
    this.metricSearchApply$
      .pipe(debounceTime(350), takeUntilDestroyed())
      .subscribe(() => this.reloadFirstPage());

    effect(() => {
      const id = this.device().id;
      void id;
      untracked(() => {
        this.sortField.set(null);
        this.sortOrder.set(1);
        this.statusFilter.set('ALL');
        this.metricSearch.set('');
        this.thresholdLevelFilter.set(null);
        this.eventsViewMode = 'table';
      });
    });
  }

  protected onStatusFilterChange(value: MonitoringEventStatus | 'ALL'): void {
    this.statusFilter.set(value);
    this.reloadFirstPage();
  }

  protected onStatusFilterChangeFromSelect(value: string | number | null): void {
    const v = value == null ? 'ALL' : String(value);
    if (v === 'ALL' || v === 'OPEN' || v === 'RESOLVED') {
      this.onStatusFilterChange(v);
    }
  }

  protected applyQuickStatusFilter(status: MonitoringEventStatus): void {
    if (this.loading()) return;
    this.statusFilter.set(status);
    this.reloadFirstPage();
  }

  protected onMetricSearchInput(value: string): void {
    this.metricSearch.set(value);
    this.metricSearchApply$.next();
  }

  protected refreshEvents(): void {
    this.reloadFirstPage();
  }

  /** Клики по ячейкам таблицы: устройство уже зафиксировано вкладкой — игнорируем. */
  protected onTableDeviceNameFilter(_e: { name: string; apply: boolean }): void {}

  protected onTableDeviceIpFilter(_e: { ip: string; apply: boolean }): void {}

  protected onTableMacFilter(_e: { mac: string; apply: boolean }): void {}

  protected onTableMetricFilter(e: { metricName: string; apply: boolean }): void {
    this.metricSearch.set(e.metricName);
    if (e.apply) {
      this.reloadFirstPage();
    }
  }

  protected onTableStatusFilter(e: { status: MonitoringEventStatus; apply: boolean }): void {
    this.statusFilter.set(e.status);
    if (e.apply) {
      this.reloadFirstPage();
    }
  }

  protected onTableLevelFilter(e: { level: MonitoringEventLevel; apply: boolean }): void {
    this.thresholdLevelFilter.set(e.level);
    if (e.apply) {
      this.reloadFirstPage();
    }
  }

  protected onEventsPaginatorChange(state: PaginatorState): void {
    const rows = state.rows ?? this.eventPage()?.size ?? 20;
    const first = state.first ?? 0;
    const page = rows > 0 ? Math.floor(first / rows) : 0;
    const ep = this.eventPage();
    if (ep != null && ep.number === page && ep.size === rows) {
      return;
    }
    this.ms.loadDeviceScopedEvents(this.device(), page, rows, this.buildFilter());
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? 20;
    const first = event.first ?? 0;
    const page = rows > 0 ? Math.floor(first / rows) : 0;
    const ep = this.eventPage();

    const rawField = event.sortField;
    const field = Array.isArray(rawField) ? rawField[0] : rawField;
    if (field != null && event.sortOrder != null) {
      this.sortField.set(field);
      this.sortOrder.set(event.sortOrder === -1 ? -1 : 1);
    }

    const loadedSamePage = ep != null && ep.number === page && ep.size === rows;
    if (loadedSamePage) {
      return;
    }

    this.ms.loadDeviceScopedEvents(this.device(), page, rows, this.buildFilter());
  }

  private reloadFirstPage(): void {
    const ep = this.eventPage();
    const size = ep?.size ?? 20;
    this.ms.loadDeviceScopedEvents(this.device(), 0, size, this.buildFilter());
  }

  private buildFilter(): MonitoringEventFilter {
    return {
      status: this.statusFilter() !== 'ALL' ? (this.statusFilter() as MonitoringEventStatus) : null,
      thresholdLevel: this.thresholdLevelFilter(),
      breachStartedFrom: null,
      breachStartedTo: null,
      minDurationSeconds: null,
      maxDurationSeconds: null,
      metricNameContains: this.metricSearch().trim() || null,
    };
  }

  private compareByField(a: MonitoringEvent, b: MonitoringEvent, field: string): number {
    return this.compareValues(this.resolveSortValue(a, field), this.resolveSortValue(b, field));
  }

  private resolveSortValue(e: MonitoringEvent, field: string): string | number {
    switch (field) {
      case 'deviceMacAddress':
        return e.deviceMacAddress?.toLowerCase() ?? '';
      case 'metricName':
        return e.metricName?.toLowerCase() ?? '';
      case 'thresholdLevel':
        return e.thresholdLevel;
      case 'thresholdValue':
        return e.thresholdValue;
      case 'actualValue':
        return e.actualValue;
      case 'breachStartedAt':
        return e.breachStartedAt ?? '';
      case 'normalizedAt':
        return e.normalizedAt ?? '';
      case 'status':
        return e.status;
      case 'deviceName':
        return e.deviceName?.toLowerCase() ?? '';
      default:
        return '';
    }
  }

  private compareValues(va: string | number, vb: string | number): number {
    if (typeof va === 'number' && typeof vb === 'number') {
      if (va < vb) return -1;
      if (va > vb) return 1;
      return 0;
    }
    return String(va).localeCompare(String(vb), undefined, { numeric: true, sensitivity: 'base' });
  }

  protected formatDate(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  /** Дата и время в колонке таймлайна (две строки). */
  protected formatTimelineStamp(iso: string | null): { date: string; time: string } {
    if (!iso) return { date: '—', time: '' };
    const d = new Date(iso);
    return {
      date: d.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short', year: 'numeric' }),
      time: d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
    };
  }

  /**
   * Сегменты для p-metergroup: доля до порога, превышение (оранж./красн.), запас шкалы.
   * Шкала — max(|порог|, |значение|); при пороге 0 вся полоса — превышение, если значение > 0.
   */
  protected thresholdMeterItems(threshold: number, actual: number): MeterItem[] {
    const t = Number(threshold);
    const a = Number(actual);
    if (!Number.isFinite(t) || !Number.isFinite(a)) {
      return [{ label: 'Нет данных', value: 100, color: '#e2e8f0' }];
    }
    const scale = Math.max(Math.abs(t), Math.abs(a), 1e-9);
    const within = (Math.min(a, t) / scale) * 100;
    const excess = (Math.max(0, a - t) / scale) * 100;
    const slack = Math.max(0, 100 - within - excess);

    const items: MeterItem[] = [];
    const eps = 0.08;

    if (within > eps) {
      const withinLabel = a <= t ? 'Значение' : 'До порога';
      const withinColor = a <= t ? '#22c55e' : '#94a3b8';
      items.push({ label: withinLabel, value: within, color: withinColor });
    }
    if (excess > eps) {
      const rel = t > 0 ? (a - t) / t : (a - t) !== 0 ? 1 : 0;
      const color = rel < 0.15 ? '#f97316' : '#dc2626';
      items.push({ label: 'Превышение', value: excess, color });
    }
    if (slack > eps) {
      items.push({
        label: a < t ? 'Запас до порога' : '',
        value: slack,
        color: '#f1f5f9',
      });
    }

    if (items.length === 0) {
      return [{ label: '', value: 100, color: '#f1f5f9' }];
    }
    return items;
  }

  /** Подсветка числа «Значение» над metergroup: нет превышения / слабое (оранж.) / сильное (красн.). */
  protected meterActualExcessTone(ev: MonitoringEvent): 'ok' | 'warn' | 'over' {
    const a = ev.actualValue;
    const t = ev.thresholdValue;
    if (a <= t) return 'ok';
    if (t > 0 && (a - t) / t < 0.15) return 'warn';
    return 'over';
  }

  /**
   * Горизональная позиция стыка первого и второго сегментов полосы (в % ширины трека),
   * совпадает с `thresholdMeterItems` (конец сегмента min(значение, порог) по шкале).
   */
  protected meterBarBoundaryPercentClamped(ev: MonitoringEvent): number {
    const t = ev.thresholdValue;
    const a = ev.actualValue;
    if (!Number.isFinite(t) || !Number.isFinite(a)) return 50;
    const scale = Math.max(Math.abs(t), Math.abs(a), 1e-9);
    const end = (Math.min(a, t) / scale) * 100;
    return Math.min(96, Math.max(4, end));
  }
}
