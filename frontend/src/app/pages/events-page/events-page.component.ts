import { Component, ViewChild, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ChipModule } from 'primeng/chip';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectButtonModule } from 'primeng/selectbutton';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { AuthService } from '../../auth.service';
import {
  MONITORING_EVENT_LEVEL_LABELS,
  MonitoringEvent,
  MonitoringEventFilter,
  MonitoringEventLevel,
  MonitoringEventLevelSummary,
  MonitoringEventStatus,
  monitoringEventLevelLabel,
  monitoringEventStatusLabel,
} from '../../models';
import { NotifierService } from '../../notifier.service';
import { MonitoringService } from '../../services/monitoring.service';
import { TableColumnWidthsService } from '../../services/table-column-widths.service';
import { buildColumnBoundsMap } from '../../utils/table-column-widths';
import {
  applyMonitoringEventsColumnPreference,
  toMonitoringEventsColumnPreference,
} from './monitoring-events-table/monitoring-events-columns.util';
import { MonitoringEventsTableComponent } from './monitoring-events-table/monitoring-events-table.component';
import {
  cloneDefaultMonitoringEventsColumns,
  monitoringEventsTableWidthDefs,
  type MonitoringEventsColumnDef,
  type MonitoringEventsColumnId,
} from './monitoring-events-table/monitoring-events-table-columns';

@Component({
  selector: 'app-events-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DragDropModule,
    MonitoringEventsTableComponent,
    ButtonModule,
    ChipModule,
    CheckboxModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    SelectButtonModule,
  ],
  templateUrl: './events-page.component.html',
  styleUrl: './events-page.component.css',
})
export class EventsPageComponent implements OnInit {
  private focusRetryRaf: number | null = null;

  @ViewChild(MonitoringEventsTableComponent) private eventsTable?: MonitoringEventsTableComponent;

  private static readonly EMPTY_LEVEL_SUMMARY: MonitoringEventLevelSummary = {
    disaster: 0,
    high: 0,
    average: 0,
    warning: 0,
    information: 0,
    notClassified: 0,
  };

  private readonly ms = inject(MonitoringService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotifierService);
  private readonly tableColumnWidths = inject(TableColumnWidthsService);

  protected readonly tableColumns = signal<MonitoringEventsColumnDef[]>(
    cloneDefaultMonitoringEventsColumns()
  );
  protected readonly columnsDialogOpen = signal(false);
  protected readonly columnsDraft = signal<MonitoringEventsColumnDef[]>(
    cloneDefaultMonitoringEventsColumns()
  );
  protected readonly columnsSaving = signal(false);
  protected readonly eventsTableColumnWidthsMap = signal<Record<string, number>>({});

  private readonly eventsTableColumnBounds = computed(() =>
    buildColumnBoundsMap(
      monitoringEventsTableWidthDefs(this.tableColumns().filter((c) => c.visible))
    )
  );

  /** Подписи как у уровня критичности в таблице и на полосе фильтров. */
  protected readonly eventLevelStripLabels = MONITORING_EVENT_LEVEL_LABELS;

  protected readonly monitoringEventLevelLabel = monitoringEventLevelLabel;
  protected readonly monitoringEventStatusLabel = monitoringEventStatusLabel;
  protected readonly page = computed(() => this.ms.eventsPage());
  protected readonly loading = computed(() => this.ms.eventsLoading());
  /** Сводка по уровням для текущих фильтров (вся выборка, не только страница таблицы). */
  protected readonly levelSummary = computed(
    () => this.ms.eventsLevelSummary() ?? EventsPageComponent.EMPTY_LEVEL_SUMMARY
  );
  protected readonly pageSize = signal(20);

  /** Клиентская сортировка текущей страницы (lazy + API без sort). */
  protected readonly sortField = signal<string | null>(null);
  protected readonly sortOrder = signal<number>(1);

  protected readonly statusFilter = signal<MonitoringEventStatus | 'ALL'>('ALL');
  protected readonly breachFrom = signal('');
  protected readonly breachTo = signal('');
  protected readonly minDurationMin = signal<number | null>(null);
  protected readonly maxDurationMin = signal<number | null>(null);
  protected readonly deviceNameSearch = signal('');
  protected readonly deviceIpSearch = signal('');
  protected readonly metricSearch = signal('');
  protected readonly macSearch = signal('');
  protected readonly thresholdLevelFilter = signal<MonitoringEventLevel | null>(null);

  /** Фильтры всегда в DOM; здесь только состояние видимости. */
  protected readonly filtersExpanded = signal(false);

  /** Есть ли ограничения выборки сверх «все события без фильтров». */
  protected readonly hasActiveFilters = computed(() => {
    if (this.statusFilter() !== 'ALL') return true;
    if (this.thresholdLevelFilter() != null) return true;
    if (this.breachFrom().trim() !== '') return true;
    if (this.breachTo().trim() !== '') return true;
    if (this.minDurationMin() != null) return true;
    if (this.maxDurationMin() != null) return true;
    if (this.deviceNameSearch().trim() !== '') return true;
    if (this.deviceIpSearch().trim() !== '') return true;
    if (this.metricSearch().trim() !== '') return true;
    if (this.macSearch().trim() !== '') return true;
    return false;
  });

  protected readonly appliedFiltersChips = computed(
    (): { key: string; label: string }[] => {
      const chips: { key: string; label: string }[] = [];

      const status = this.statusFilter();
      if (status !== 'ALL') {
        chips.push({
          key: 'status',
          label: `Статус: ${this.monitoringEventStatusLabel(status as MonitoringEventStatus)}`,
        });
      }

      const lvl = this.thresholdLevelFilter();
      if (lvl != null) {
        chips.push({ key: 'level', label: `Критичность: ${this.monitoringEventLevelLabel(lvl)}` });
      }

      const from = this.breachFrom().trim();
      const to = this.breachTo().trim();
      if (from !== '' || to !== '') {
        chips.push({ key: 'breach', label: `Начало: ${this.formatLocalDateTimeRange(from, to)}` });
      }

      const minD = this.minDurationMin();
      const maxD = this.maxDurationMin();
      if (minD != null || maxD != null) {
        chips.push({ key: 'duration', label: `Длительность: ${this.formatDurationRange(minD, maxD)}` });
      }

      const dn = this.deviceNameSearch().trim();
      if (dn !== '') chips.push({ key: 'deviceName', label: `Имя устройства: ${dn}` });

      const ip = this.deviceIpSearch().trim();
      if (ip !== '') chips.push({ key: 'deviceIp', label: `IP: ${ip}` });

      const mn = this.metricSearch().trim();
      if (mn !== '') chips.push({ key: 'metric', label: `Метрика: ${mn}` });

      const mac = this.macSearch().trim();
      if (mac !== '') chips.push({ key: 'mac', label: `MAC: ${mac}` });

      return chips;
    }
  );

  protected clearAppliedFilter(key: string): void {
    switch (key) {
      case 'status':
        this.statusFilter.set('ALL');
        break;
      case 'level':
        this.thresholdLevelFilter.set(null);
        break;
      case 'breach':
        this.breachFrom.set('');
        this.breachTo.set('');
        break;
      case 'duration':
        this.minDurationMin.set(null);
        this.maxDurationMin.set(null);
        break;
      case 'deviceName':
        this.deviceNameSearch.set('');
        break;
      case 'deviceIp':
        this.deviceIpSearch.set('');
        break;
      case 'metric':
        this.metricSearch.set('');
        break;
      case 'mac':
        this.macSearch.set('');
        break;
      default:
        return;
    }
    this.applyFilters();
  }

  protected readonly statusOptions = [
    { label: 'Все', value: 'ALL' },
    { label: 'Активные', value: 'OPEN' },
    { label: 'Устранённые', value: 'RESOLVED' },
  ];

  protected readonly tableFirst = computed(() => {
    const p = this.page();
    if (!p) return 0;
    return p.number * p.size;
  });

  protected readonly eventsTotal = computed(() => this.page()?.totalElements ?? 0);

  protected readonly eventsOnPage = computed(() => this.page()?.content?.length ?? 0);

  protected readonly sortedEvents = computed(() => {
    const field = this.sortField();
    const order = this.sortOrder();
    const rows = [...(this.page()?.content ?? [])];
    if (!field || rows.length === 0) {
      return rows;
    }
    rows.sort((a, b) => order * this.compareByField(a, b, field));
    return rows;
  });

  protected readonly eventsOpenOnPage = computed(() => {
    const c = this.page()?.content;
    if (!c?.length) return 0;
    return c.filter((e) => e.status === 'OPEN').length;
  });

  protected readonly eventsResolvedOnPage = computed(() => {
    const c = this.page()?.content;
    if (!c?.length) return 0;
    return c.filter((e) => e.status === 'RESOLVED').length;
  });

  ngOnInit(): void {
    this.loadTableColumnsPreference();
    this.loadTableColumnWidths();
  }

  private loadTableColumnWidths(): void {
    this.tableColumnWidths.load().subscribe({
      next: () => {
        this.eventsTableColumnWidthsMap.set(
          this.tableColumnWidths.widthsFor('events', this.eventsTableColumnBounds())
        );
      },
      error: () => {
        this.eventsTableColumnWidthsMap.set({});
      },
    });
  }

  protected openColumnsDialog(): void {
    this.columnsDraft.set(this.tableColumns().map((c) => ({ ...c })));
    this.columnsDialogOpen.set(true);
  }

  protected closeColumnsDialog(): void {
    this.columnsDialogOpen.set(false);
  }

  protected onColumnsDraftDrop(event: CdkDragDrop<MonitoringEventsColumnDef[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    this.columnsDraft.update((cols) => {
      const next = [...cols];
      moveItemInArray(next, event.previousIndex, event.currentIndex);
      return next;
    });
  }

  protected onColumnsDraftVisibilityChange(id: MonitoringEventsColumnId, visible: boolean): void {
    this.columnsDraft.update((cols) => {
      const next = cols.map((c) => (c.id === id ? { ...c, visible } : c));
      if (next.filter((c) => c.visible).length === 0) {
        return cols;
      }
      return next;
    });
  }

  protected isColumnsDraftVisibilityLocked(col: MonitoringEventsColumnDef): boolean {
    if (!col.visible) {
      return false;
    }
    return this.columnsDraft().filter((c) => c.visible).length <= 1;
  }

  protected resetColumnsDraftToDefault(): void {
    this.columnsDraft.set(cloneDefaultMonitoringEventsColumns());
    this.resetEventsTableColumnWidths();
  }

  private resetEventsTableColumnWidths(): void {
    this.tableColumnWidths.reset('events').subscribe({
      next: () => {
        this.eventsTableColumnWidthsMap.set({});
        this.eventsTable?.resetDomColumnWidths();
      },
      error: () => {
        this.notify.error('Не удалось сбросить ширину колонок.', 'События');
      },
    });
  }

  protected applyColumnsDraft(): void {
    const draft = this.columnsDraft().map((c) => ({ ...c }));
    this.columnsSaving.set(true);
    this.auth.updateMonitoringEventsColumnsPreference(toMonitoringEventsColumnPreference(draft)).subscribe({
      next: () => {
        this.tableColumns.set(draft);
        this.columnsSaving.set(false);
        this.columnsDialogOpen.set(false);
        this.notify.success('Настройки столбцов сохранены.', 'События');
      },
      error: () => {
        this.columnsSaving.set(false);
        this.notify.error('Не удалось сохранить настройки столбцов.', 'События');
      },
    });
  }

  protected applyQuickStatusFilter(status: MonitoringEventStatus): void {
    if (this.loading()) return;
    this.filtersExpanded.set(true);
    this.statusFilter.set(status);
    this.applyFilters();
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? 20;
    const first = event.first ?? 0;
    const pageNum = rows > 0 ? Math.floor(first / rows) : 0;
    if (rows !== this.pageSize()) {
      this.pageSize.set(rows);
    }

    const rawField = event.sortField;
    const field = Array.isArray(rawField) ? rawField[0] : rawField;
    if (field != null && event.sortOrder != null) {
      this.sortField.set(field);
      this.sortOrder.set(event.sortOrder === -1 ? -1 : 1);
    }

    const p = this.page();
    const loadedSamePage = p != null && p.number === pageNum && p.size === rows;
    if (loadedSamePage) {
      return;
    }

    this.ms.loadAllEvents(this.buildFilter(), pageNum, rows);
  }

  protected onTableDeviceNameFilter(e: { name: string; apply: boolean }): void {
    this.focusDeviceNameFilter(e.name, e.apply);
  }

  protected onTableDeviceIpFilter(e: { ip: string; apply: boolean }): void {
    this.focusDeviceIpFilter(e.ip, e.apply);
  }

  protected onTableMacFilter(e: { mac: string; apply: boolean }): void {
    this.focusMacFilter(e.mac, e.apply);
  }

  protected onTableMetricFilter(e: { metricName: string; apply: boolean }): void {
    this.focusMetricNameFilter(e.metricName, e.apply);
  }

  protected onTableStatusFilter(e: { status: MonitoringEventStatus; apply: boolean }): void {
    this.filtersExpanded.set(true);
    this.statusFilter.set(e.status);
    if (e.apply) {
      this.applyFilters();
    }
  }

  protected onTableLevelFilter(e: { level: MonitoringEventLevel; apply: boolean }): void {
    this.filtersExpanded.set(true);
    this.thresholdLevelFilter.set(e.level);
    if (e.apply) {
      this.applyFilters();
    }
  }

  protected applyFilters(): void {
    this.sortField.set(null);
    this.sortOrder.set(1);
    this.ms.loadAllEvents(this.buildFilter(), 0, this.pageSize());
  }

  protected onFiltersEnter(ev: Event): void {
    // Enter должен работать как "Применить", но только если реально есть активные фильтры.
    if (this.loading()) return;
    if (!this.filtersExpanded()) return;
    if (!this.hasActiveFilters()) return;
    (ev as KeyboardEvent).preventDefault();
    this.applyFilters();
  }

  protected resetFilters(): void {
    this.statusFilter.set('ALL');
    this.thresholdLevelFilter.set(null);
    this.breachFrom.set('');
    this.breachTo.set('');
    this.minDurationMin.set(null);
    this.maxDurationMin.set(null);
    this.deviceNameSearch.set('');
    this.deviceIpSearch.set('');
    this.metricSearch.set('');
    this.macSearch.set('');
    this.sortField.set(null);
    this.sortOrder.set(1);
    this.ms.loadAllEvents(this.buildFilter(), 0, this.pageSize());
  }

  private formatLocalDateTimeRange(from: string, to: string): string {
    const f = from ? this.formatLocalDateTimeShort(from) : '—';
    const t = to ? this.formatLocalDateTimeShort(to) : '—';
    return `${f} — ${t}`;
  }

  private formatLocalDateTimeShort(local: string): string {
    // datetime-local приходит без таймзоны; Date интерпретирует как локальное время.
    const d = new Date(local);
    if (Number.isNaN(d.getTime())) return local;
    return d.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  private formatDurationRange(min: number | null, max: number | null): string {
    const a = min != null ? `${min} мин` : '—';
    const b = max != null ? `${max} мин` : '—';
    return `${a} — ${b}`;
  }

  protected toggleThresholdLevel(level: MonitoringEventLevel): void {
    const next = this.thresholdLevelFilter() === level ? null : level;
    this.thresholdLevelFilter.set(next);
    this.applyFilters();
  }

  protected focusDeviceNameFilter(deviceName?: string | null, apply = false): void {
    this.filtersExpanded.set(true);
    if (deviceName != null && deviceName.trim() !== '') {
      this.deviceNameSearch.set(deviceName);
    }
    this.focusWhenAvailable('events-deviceName-filter');
    if (apply) {
      this.applyFilters();
    }
  }

  protected focusDeviceIpFilter(ip?: string | null, apply = false): void {
    this.filtersExpanded.set(true);
    if (ip != null && ip.trim() !== '') {
      this.deviceIpSearch.set(ip);
    }
    this.focusWhenAvailable('events-deviceIp-filter');
    if (apply) {
      this.applyFilters();
    }
  }

  protected focusMetricNameFilter(metricName?: string | null, apply = false): void {
    this.filtersExpanded.set(true);
    if (metricName != null && metricName.trim() !== '') {
      this.metricSearch.set(metricName);
    }
    this.focusWhenAvailable('events-metricName-filter');
    if (apply) {
      this.applyFilters();
    }
  }

  protected focusMacFilter(mac?: string | null, apply = false): void {
    this.filtersExpanded.set(true);
    if (mac != null && mac.trim() !== '') {
      this.macSearch.set(mac);
    }
    this.focusWhenAvailable('events-mac-filter');
    if (apply) {
      this.applyFilters();
    }
  }

  protected isThresholdLevelActive(level: MonitoringEventLevel): boolean {
    return this.thresholdLevelFilter() === level;
  }

  protected toggleFiltersExpanded(): void {
    this.filtersExpanded.set(!this.filtersExpanded());
  }

  private focusWhenAvailable(elementId: string): void {
    if (this.focusRetryRaf != null) {
      window.cancelAnimationFrame(this.focusRetryRaf);
      this.focusRetryRaf = null;
    }

    const startedAt = performance.now();
    const maxMs = 1500;

    const tick = () => {
      // Фильтры всегда в DOM; ждём только когда они реально показаны.
      if (!this.filtersExpanded()) {
        if (performance.now() - startedAt < maxMs) this.focusRetryRaf = window.requestAnimationFrame(tick);
        return;
      }

      const el = document.getElementById(elementId) as HTMLInputElement | null;
      // Ждём, пока элемент реально появится и станет видимым (анимация/ленивая отрисовка).
      if (!el || el.offsetParent == null) {
        if (performance.now() - startedAt < maxMs) {
          this.focusRetryRaf = window.requestAnimationFrame(tick);
        }
        return;
      }

      this.focusRetryRaf = null;
      el.focus();
      el.select?.();
      // Чтобы пользователь точно увидел поле при фокусе.
      el.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
    };

    this.focusRetryRaf = window.requestAnimationFrame(tick);
  }

  private buildFilter(): MonitoringEventFilter {
    const toIso = (local: string) => (local ? new Date(local).toISOString() : null);
    return {
      status: this.statusFilter() !== 'ALL' ? (this.statusFilter() as MonitoringEventStatus) : null,
      thresholdLevel: this.thresholdLevelFilter(),
      breachStartedFrom: toIso(this.breachFrom()),
      breachStartedTo: toIso(this.breachTo()),
      minDurationSeconds:
        this.minDurationMin() != null && this.minDurationMin()! >= 0
          ? Number(this.minDurationMin()) * 60
          : null,
      maxDurationSeconds:
        this.maxDurationMin() != null && this.maxDurationMin()! >= 0
          ? Number(this.maxDurationMin()) * 60
          : null,
      deviceNameContains: this.deviceNameSearch().trim() || null,
      deviceIpContains: this.deviceIpSearch().trim() || null,
      metricNameContains: this.metricSearch().trim() || null,
      macAddressContains: this.macSearch().trim() || null,
    };
  }

  private compareByField(a: MonitoringEvent, b: MonitoringEvent, field: string): number {
    return this.compareValues(this.resolveSortValue(a, field), this.resolveSortValue(b, field));
  }

  private resolveSortValue(e: MonitoringEvent, field: string): string | number {
    switch (field) {
      case 'deviceName':
        return e.deviceName?.toLowerCase() ?? '';
      case 'deviceHostName':
        return e.deviceHostName?.toLowerCase() ?? '';
      case 'deviceIp':
        return e.deviceIp?.toLowerCase() ?? '';
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

  private loadTableColumnsPreference(): void {
    this.auth.getMonitoringEventsColumnsPreference().subscribe({
      next: (pref) => {
        this.tableColumns.set(applyMonitoringEventsColumnPreference(pref.columns));
      },
      error: () => {
        this.tableColumns.set(cloneDefaultMonitoringEventsColumns());
      },
    });
  }
}
