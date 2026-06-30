import { NgStyle } from '@angular/common';
import { Component, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, forkJoin, interval, Subject, take } from 'rxjs';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ChipModule } from 'primeng/chip';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { Menu, MenuModule } from 'primeng/menu';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import { MonitoringTemplatePickerDialogComponent } from '../../components/monitoring-template-picker-dialog/monitoring-template-picker-dialog.component';
import { ScanResultsTableComponent } from '../../components/scan-results-table/scan-results-table.component';
import { NsTableColumnWidthsDirective } from '../../directives/ns-table-column-widths.directive';
import { TableColumnWidthsService } from '../../services/table-column-widths.service';
import { buildColumnBoundsMap, columnBoundsStyle } from '../../utils/table-column-widths';
import { buildDeviceSearchText } from '../../utils/scan-result.util';
import {
  AppRole,
  DeviceScanResult,
  MonitoringHealthStatus,
  MonitoringHostStatusFilter,
  monitoringHealthStatusLabel,
} from '../../models';
import { AuthService } from '../../auth.service';
import { NotifierService } from '../../notifier.service';
import { MonitoringService } from '../../services/monitoring.service';
import { ScanJobsService } from '../../services/scan-jobs.service';
import {
  applyMonitoringDevicesColumnPreference,
  toMonitoringDevicesColumnPreference,
} from './monitoring-devices-columns.util';
import {
  cloneDefaultMonitoringDevicesColumns,
  monitoringDevicesTableColumnOrder,
  monitoringDevicesTableWidthDefs,
  type MonitoringDevicesColumnDef,
  type MonitoringDevicesColumnId,
} from './monitoring-devices-table-columns';

@Component({
  selector: 'app-monitoring-page',
  standalone: true,
  imports: [
    NgStyle,
    FormsModule,
    DragDropModule,
    TableModule,
    ButtonModule,
    CheckboxModule,
    ChipModule,
    InputTextModule,
    MenuModule,
    ConfirmDialogModule,
    DialogModule,
    SelectModule,
    DeviceOptionSelectComponent,
    TagModule,
    TooltipModule,
    MonitoringTemplatePickerDialogComponent,
    ScanResultsTableComponent,
    NsTableColumnWidthsDirective,
  ],
  providers: [ConfirmationService],
  templateUrl: './monitoring-page.component.html',
  styleUrl: './monitoring-page.component.css',
})
export class MonitoringPageComponent {
  private readonly devicesSearchApply$ = new Subject<void>();
  /** Подавляет echo onLazyLoad сразу после программной перезагрузки по фильтрам/поиску. */
  private filterDrivenListLoad = false;
  private focusRetryRaf: number | null = null;
  @ViewChild('deviceActionsMenu') private deviceActionsMenu?: Menu;
  @ViewChild('devicesTableWidths') private devicesTableWidths?: NsTableColumnWidthsDirective;

  protected readonly mon = inject(MonitoringService);
  protected readonly scanJobs = inject(ScanJobsService);
  private readonly tableColumnWidths = inject(TableColumnWidthsService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotifierService);
  private readonly confirmation = inject(ConfirmationService);
  protected readonly deviceActionsMenuItems = signal<MenuItem[]>([]);
  private readonly pendingActionDeviceIds = signal(new Set<string>());
  protected readonly monitoringHealthStatusLabel = monitoringHealthStatusLabel;
  protected readonly monitoringHealthStatusTagSeverity = (
    status: MonitoringHealthStatus | null | undefined
  ): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' => {
    if (status === 'NORM') return 'success';
    if (status === 'WARN') return 'warn';
    if (status === 'CRITICAL') return 'danger';
    return 'secondary';
  };

  protected readonly tableColumns = signal<MonitoringDevicesColumnDef[]>(
    cloneDefaultMonitoringDevicesColumns()
  );
  protected readonly columnsDialogOpen = signal(false);
  protected readonly columnsDraft = signal<MonitoringDevicesColumnDef[]>(
    cloneDefaultMonitoringDevicesColumns()
  );
  protected readonly columnsSaving = signal(false);

  protected readonly displayedTableColumns = computed(() =>
    this.tableColumns().filter(
      (c) => c.visible && (c.id !== 'actions' || this.canManageMonitoring())
    )
  );

  protected readonly devicesTableWidthColumnDefs = computed(() =>
    monitoringDevicesTableWidthDefs(this.displayedTableColumns())
  );
  protected readonly devicesTableWidthColumnOrder = computed(() =>
    monitoringDevicesTableColumnOrder(this.displayedTableColumns())
  );
  protected readonly devicesTableColumnBounds = computed(() =>
    buildColumnBoundsMap(this.devicesTableWidthColumnDefs())
  );
  protected readonly devicesTableColumnWidthsMap = signal<Record<string, number>>({});
  protected readonly columnBoundsStyle = columnBoundsStyle;
  protected readonly healthStatusOptions = [
    { label: 'Все', value: 'ALL' as const },
    { label: 'Норма', value: 'NORM' as const },
    { label: 'Предупреждение', value: 'WARN' as const },
    { label: 'Критично', value: 'CRITICAL' as const },
  ];

  /** Фильтры всегда в DOM; здесь только состояние видимости (как на странице «События»). */
  protected readonly filtersExpanded = signal(false);

  /** Черновик фильтров (применяются по кнопке как на странице «События»). */
  protected readonly availabilityDraft = signal<MonitoringHostStatusFilter>(this.mon.monitoringHostStatusFilter());
  protected readonly searchDraft = signal(this.mon.devicesSearch());
  protected readonly ipDraft = signal(this.mon.deviceIpFilter());
  protected readonly macDraft = signal(this.mon.deviceMacFilter());
  protected readonly statusDraft = signal(this.mon.deviceStatusFilter());
  protected readonly tagDraft = signal<string[]>(this.mon.deviceTagFilter());
  protected readonly tagInputDraft = signal('');
  protected readonly healthDraft = signal<MonitoringHealthStatus | 'ALL'>(this.mon.deviceHealthStatusFilter());

  /** Сводка (list-summary-grid) показывается только после действия пользователя (как на «Событиях»). */
  private readonly summaryRequested = signal(false);

  protected readonly hasActiveFilters = computed(() => {
    if (this.searchDraft().trim() !== '') return true;
    if (this.ipDraft().trim() !== '') return true;
    if (this.macDraft().trim() !== '') return true;
    if (this.statusDraft().trim() !== '') return true;
    if (this.tagDraft().length > 0) return true;
    if (this.healthDraft() !== 'ALL') return true;
    if (this.availabilityDraft() !== 'ALL') return true;
    return false;
  });

  protected readonly hasActiveAppliedChips = computed(() => {
    if (this.ipDraft().trim() !== '') return true;
    if (this.macDraft().trim() !== '') return true;
    if (this.statusDraft().trim() !== '') return true;
    if (this.tagDraft().length > 0) return true;
    if (this.healthDraft() !== 'ALL') return true;
    return false;
  });

  protected readonly devicesTotal = computed(() => this.mon.monitoredDevicesPage()?.totalElements ?? 0);
  protected readonly devicesOnPage = computed(() => this.mon.monitoredDevices().length);
  protected readonly showSummary = computed(() => {
    if (this.mon.monitoredDevicesPage() == null) return false;
    return this.hasActiveFilters();
  });

  /** Оверлей PrimeNG только при первой загрузке; при поиске таблица не мигает. */
  protected readonly devicesTableLoading = computed(
    () => this.mon.devicesLoading() && this.mon.monitoredDevicesPage() == null,
  );

  protected readonly appliedFiltersChips = computed((): { key: string; label: string }[] => {
    const chips: { key: string; label: string }[] = [];

    const ip = this.ipDraft().trim();
    if (ip !== '') chips.push({ key: 'ip', label: `IP: ${ip}` });

    const mac = this.macDraft().trim();
    if (mac !== '') chips.push({ key: 'mac', label: `MAC: ${mac}` });

    const status = this.statusDraft().trim();
    if (status !== '') chips.push({ key: 'status', label: `Статус опроса: ${status}` });

    for (const t of this.tagDraft()) {
      chips.push({ key: `tag:${t}`, label: `Тег: ${t}` });
    }

    const health = this.healthDraft();
    if (health !== 'ALL') {
      const map: Record<MonitoringHealthStatus, string> = {
        NORM: 'Норма',
        WARN: 'Предупреждение',
        CRITICAL: 'Критично',
      };
      chips.push({ key: 'health', label: `Состояние: ${map[health] ?? String(health)}` });
    }

    return chips;
  });

  // --- discovered (уникальные IP из автосканов, ещё не на мониторинге) ---
  protected readonly discoveryViewOpen = signal(false);
  protected readonly discoveryLoading = signal(false);
  protected readonly discoveryDevices = signal<DeviceScanResult[]>([]);
  protected readonly discoverySelectedIps = signal<string[]>([]);
  protected readonly discoveryFilter = signal('');
  protected readonly monitoringTemplatePickerOpen = signal(false);
  protected readonly templatePickerPurpose = signal<'discovery' | 'bulk' | null>(null);

  /** Выбранные устройства (id → снимок строки для массовых операций). */
  protected readonly selectedDevicesById = signal<Map<string, DeviceScanResult>>(new Map());
  protected readonly selectedDevicesCount = computed(() => this.selectedDevicesById().size);
  protected readonly hasDeviceSelection = computed(() => this.selectedDevicesCount() > 0);
  protected readonly selectedDevices = computed(() => [...this.selectedDevicesById().values()]);
  protected readonly bulkActionPending = signal(false);

  protected readonly pageSelectAllChecked = computed(() => this.pageSelectionState() === 'all');
  protected readonly pageSelectAllIndeterminate = computed(() => this.pageSelectionState() === 'partial');

  private readonly pageSelectionState = computed<'none' | 'partial' | 'all'>(() => {
    const devices = this.mon.monitoredDevices();
    if (devices.length === 0) {
      return 'none';
    }
    const selected = this.selectedDevicesById();
    let count = 0;
    for (const device of devices) {
      if (device.id && selected.has(device.id)) {
        count++;
      }
    }
    if (count === 0) {
      return 'none';
    }
    if (count === devices.length) {
      return 'all';
    }
    return 'partial';
  });

  protected readonly templateSelectOptions = computed(() =>
    this.mon.monitoringTemplates().map((t) => ({ label: t.name, value: t.id }))
  );

  protected readonly selectedMonitoringTemplateNames = computed(() => {
    const index = new Map(this.mon.monitoringTemplates().map((t) => [t.id, t.name] as const));
    return (this.mon.selectedMonitoringTemplateIds() ?? []).map((id) => index.get(id)).filter((v): v is string => !!v);
  });

  protected readonly filteredDiscoveryDevices = computed(() => {
    const query = this.discoveryFilter().trim().toLowerCase();
    const results = this.discoveryDevices();
    if (!query) return results;
    return results.filter((d) => this.discoverySearchText(d).includes(query));
  });

  constructor() {
    this.devicesSearchApply$
      .pipe(debounceTime(350), takeUntilDestroyed())
      .subscribe(() => {
        if (this.searchDraft() === this.mon.devicesSearch()) {
          return;
        }
        this.applySearchFilter();
      });

    effect(() => {
      const page = this.mon.monitoredDevicesPage();
      if (!this.filterDrivenListLoad || page == null) {
        return;
      }
      queueMicrotask(() => {
        this.filterDrivenListLoad = false;
      });
    });

    // Нужен счётчик для плитки «Обнаружено новых» (как на странице «Автосканирование»).
    this.scanJobs.reloadDiscoverySummary();

    // Восстанавливает состояние из query-параметров и сам инициирует первую загрузку списка.
    this.restoreStateFromQueryParams();

    // Держим URL в актуальном состоянии по мере работы пользователя.
    effect(() => {
      // Не пишем в URL, пока не загружена хотя бы одна страница (иначе лишний replaceUrl на старте).
      if (this.mon.monitoredDevicesPage() == null) {
        return;
      }
      this.syncQueryParamsFromState();
    });

    /** Периодическое обновление списка только пока открыта эта страница (не в root-сервисе). */
    interval(60_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        if (!this.auth.isAuthenticated() || this.mon.monitoredDevicesPage() == null) {
          return;
        }
        this.mon.loadMonitoredDevices();
      });

    this.loadTableColumnsPreference();
    this.loadTableColumnWidths();
  }

  private loadTableColumnWidths(): void {
    this.tableColumnWidths.load().subscribe({
      next: () => {
        this.devicesTableColumnWidthsMap.set(
          this.tableColumnWidths.widthsFor('devices', this.devicesTableColumnBounds())
        );
      },
      error: () => {
        this.devicesTableColumnWidthsMap.set({});
      },
    });
  }

  protected isColumnResizable(col: MonitoringDevicesColumnDef): boolean {
    return col.resizable !== false;
  }

  protected openColumnsDialog(): void {
    this.columnsDraft.set(this.tableColumns().map((c) => ({ ...c })));
    this.columnsDialogOpen.set(true);
  }

  protected closeColumnsDialog(): void {
    this.columnsDialogOpen.set(false);
  }

  protected onColumnsDraftDrop(event: CdkDragDrop<MonitoringDevicesColumnDef[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    this.columnsDraft.update((cols) => {
      const next = [...cols];
      moveItemInArray(next, event.previousIndex, event.currentIndex);
      return next;
    });
  }

  protected onColumnsDraftVisibilityChange(id: MonitoringDevicesColumnId, visible: boolean): void {
    this.columnsDraft.update((cols) => {
      const next = cols.map((c) => (c.id === id ? { ...c, visible } : c));
      if (next.filter((c) => c.visible).length === 0) {
        return cols;
      }
      return next;
    });
  }

  protected isColumnsDraftVisibilityLocked(col: MonitoringDevicesColumnDef): boolean {
    if (!col.visible) {
      return false;
    }
    return this.columnsDraft().filter((c) => c.visible).length <= 1;
  }

  protected resetColumnsDraftToDefault(): void {
    this.columnsDraft.set(cloneDefaultMonitoringDevicesColumns());
    this.resetDevicesTableColumnWidths();
  }

  private resetDevicesTableColumnWidths(): void {
    this.tableColumnWidths.reset('devices').subscribe({
      next: () => {
        this.devicesTableColumnWidthsMap.set({});
        this.devicesTableWidths?.resetDomWidths();
      },
      error: () => {
        this.notify.error('Не удалось сбросить ширину колонок.', 'Устройства');
      },
    });
  }

  protected applyColumnsDraft(): void {
    const draft = this.columnsDraft().map((c) => ({ ...c }));
    this.columnsSaving.set(true);
    this.auth.updateMonitoringDevicesColumnsPreference(toMonitoringDevicesColumnPreference(draft)).subscribe({
      next: () => {
        this.tableColumns.set(draft);
        this.columnsSaving.set(false);
        this.columnsDialogOpen.set(false);
        this.notify.success('Настройки столбцов сохранены.', 'Устройства');
      },
      error: () => {
        this.columnsSaving.set(false);
        this.notify.error('Не удалось сохранить настройки столбцов.', 'Устройства');
      },
    });
  }

  private loadTableColumnsPreference(): void {
    this.auth.getMonitoringDevicesColumnsPreference().subscribe({
      next: (pref) => {
        this.tableColumns.set(applyMonitoringDevicesColumnPreference(pref.columns));
      },
      error: () => {
        this.tableColumns.set(cloneDefaultMonitoringDevicesColumns());
      },
    });
  }

  protected openMonitoringTemplatePicker(): void {
    this.templatePickerPurpose.set('discovery');
    this.ensureTemplatesLoaded();
    this.monitoringTemplatePickerOpen.set(true);
  }

  protected openBulkTemplatePicker(): void {
    if (!this.hasDeviceSelection() || this.bulkActionPending()) {
      return;
    }
    this.templatePickerPurpose.set('bulk');
    this.ensureTemplatesLoaded();
    this.monitoringTemplatePickerOpen.set(true);
  }

  protected onMonitoringTemplatePickerSave(templateIds: string[]): void {
    if (this.templatePickerPurpose() === 'bulk') {
      this.applyBulkTemplates(templateIds);
      this.templatePickerPurpose.set(null);
      return;
    }
    this.mon.setSelectedMonitoringTemplateIds(templateIds);
  }

  protected onMonitoringTemplatePickerVisibleChange(visible: boolean): void {
    this.monitoringTemplatePickerOpen.set(visible);
    if (!visible) {
      this.templatePickerPurpose.set(null);
    }
  }

  protected isDeviceSelected(device: DeviceScanResult): boolean {
    return !!device.id && this.selectedDevicesById().has(device.id);
  }

  protected toggleDeviceSelection(device: DeviceScanResult, selected: boolean): void {
    if (!device.id) {
      return;
    }
    this.selectedDevicesById.update((map) => {
      const next = new Map(map);
      if (selected) {
        next.set(device.id, device);
      } else {
        next.delete(device.id);
      }
      return next;
    });
  }

  protected setSelectAllOnPage(selected: boolean): void {
    const devices = this.mon.monitoredDevices();
    this.selectedDevicesById.update((map) => {
      const next = new Map(map);
      for (const device of devices) {
        if (!device.id) {
          continue;
        }
        if (selected) {
          next.set(device.id, device);
        } else {
          next.delete(device.id);
        }
      }
      return next;
    });
  }

  protected clearDeviceSelection(): void {
    this.selectedDevicesById.set(new Map());
  }

  protected confirmBulkDeactivate(): void {
    const devices = this.selectedDevices();
    if (!devices.length || this.bulkActionPending()) {
      return;
    }
    const count = devices.length;
    this.confirmation.confirm({
      header: 'Снять устройства с мониторинга?',
      message: `Будет остановлен мониторинг всех item у ${count} ${this.deviceCountLabel(count)}. Сами устройства останутся в списке.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Снять с мониторинга',
      rejectLabel: 'Отмена',
      accept: () => this.bulkDeactivate(devices),
    });
  }

  protected confirmBulkDelete(): void {
    const devices = this.selectedDevices();
    if (!devices.length || this.bulkActionPending()) {
      return;
    }
    const count = devices.length;
    this.confirmation.confirm({
      header: 'Удалить выбранные устройства из системы?',
      message: `Будет остановлен сбор данных, и ${count} ${this.deviceCountLabel(count)} будут удалены из мониторинга и системы. Действие необратимо.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.bulkDelete(devices),
    });
  }

  protected openBulkTagEditor(): void {
    if (!this.hasDeviceSelection() || this.bulkActionPending()) {
      return;
    }
    this.tagEditorDevice.set(null);
    this.tagEditorBulkMode.set(true);
    this.tagEditorDraftTags.set([]);
    this.tagEditorNewTag.set('');
    this.tagEditorVisible.set(true);
  }

  protected canManageMonitoring(): boolean {
    return this.hasAnyRole('ADMIN', 'OPERATOR');
  }

  private discoverySearchText(device: DeviceScanResult): string {
    return buildDeviceSearchText(device);
  }

  protected onDiscoverySelectionChange(event: { ip: string; checked: boolean }): void {
    this.setDiscoveryIpSelected(event.ip, event.checked);
  }

  protected openDiscoveryView(): void {
    this.discoveryViewOpen.set(true);
    this.discoveryLoading.set(true);
    this.discoverySelectedIps.set([]);
    this.discoveryFilter.set('');
    this.scanJobs.getDiscoveredNotMonitoredDevices().subscribe({
      next: (devices) => {
        this.discoveryDevices.set(Array.isArray(devices) ? devices : []);
        this.discoveryLoading.set(false);
      },
      error: () => {
        this.discoveryLoading.set(false);
        this.discoveryViewOpen.set(false);
        this.notify.error('Не удалось загрузить список устройств. Проверьте backend.', 'Автосканирование');
      },
    });
  }

  protected closeDiscoveryView(): void {
    this.discoveryViewOpen.set(false);
    this.discoveryDevices.set([]);
    this.discoverySelectedIps.set([]);
    this.discoveryFilter.set('');
    this.discoveryLoading.set(false);
  }

  protected isDiscoveryIpSelected(ip: string): boolean {
    return this.discoverySelectedIps().includes(ip);
  }

  protected setDiscoveryIpSelected(ip: string, selected: boolean): void {
    this.discoverySelectedIps.update((ips) => {
      const next = new Set(ips);
      if (selected) next.add(ip);
      else next.delete(ip);
      return [...next];
    });
  }

  protected activateDiscoveryMonitoring(): void {
    const ips = this.discoverySelectedIps();
    if (!ips.length) return;

    const devices = this.discoveryDevices().filter((d) => ips.includes(d.ip));
    if (!devices.length) return;

    const templateIds = this.mon.selectedMonitoringTemplateIds();
    this.mon.activateMonitoring(devices, templateIds, null).subscribe({
      next: (result) => {
        this.mon.applyMonitoredDevices(result);
        this.discoverySelectedIps.set([]);
        this.scanJobs.reloadDiscoverySummary();

        this.scanJobs.getDiscoveredNotMonitoredDevices().subscribe({
          next: (list) => {
            this.discoveryDevices.set(Array.isArray(list) ? list : []);
          },
          error: () => {},
        });

        this.notify.success(`На мониторинг поставлено устройств: ${devices.length}.`, 'Мониторинг');
      },
      error: () =>
        this.notify.error('Не удалось поставить устройства на мониторинг. Проверьте backend.', 'Мониторинг'),
    });
  }

  // --- tags editor (no access restrictions in UI) ---
  protected readonly tagEditorVisible = signal(false);
  protected readonly tagEditorBulkMode = signal(false);
  protected readonly tagEditorDevice = signal<DeviceScanResult | null>(null);
  protected readonly tagEditorDraftTags = signal<string[]>([]);
  protected readonly tagEditorNewTag = signal('');
  protected readonly tagEditorSaving = signal(false);

  protected openTagEditor(device: DeviceScanResult): void {
    const tags = Array.isArray(device.tags) ? device.tags : [];
    this.tagEditorBulkMode.set(false);
    this.tagEditorDevice.set(device);
    this.tagEditorDraftTags.set([...tags]);
    this.tagEditorNewTag.set('');
    this.tagEditorVisible.set(true);
  }

  protected closeTagEditor(): void {
    this.tagEditorVisible.set(false);
    this.tagEditorBulkMode.set(false);
    this.tagEditorDevice.set(null);
    this.tagEditorDraftTags.set([]);
    this.tagEditorNewTag.set('');
    this.tagEditorSaving.set(false);
  }

  protected addDraftTag(): void {
    const raw = this.tagEditorNewTag();
    const t = raw.trim().replace(/\s+/g, ' ');
    if (!t) return;
    this.tagEditorDraftTags.update((cur) => {
      const exists = cur.some((x) => x.toLowerCase() === t.toLowerCase());
      return exists ? cur : [...cur, t];
    });
    this.tagEditorNewTag.set('');
  }

  protected removeDraftTag(tag: string): void {
    this.tagEditorDraftTags.update((cur) => cur.filter((t) => t !== tag));
  }

  protected updateDraftTagAt(index: number, value: string): void {
    const v = value.replace(/\s+/g, ' ').trim();
    this.tagEditorDraftTags.update((cur) => {
      if (index < 0 || index >= cur.length) return cur;
      const next = [...cur];
      next[index] = v;
      return next;
    });
  }

  protected saveTags(): void {
    const normalized = this.tagEditorDraftTags()
      .map((t) => t.replace(/\s+/g, ' ').trim())
      .filter((t) => t.length > 0)
      .filter((t, i, arr) => arr.findIndex((x) => x.toLowerCase() === t.toLowerCase()) === i);

    if (this.tagEditorBulkMode()) {
      const devices = this.selectedDevices().filter((d) => !!d.id);
      if (!devices.length) {
        this.closeTagEditor();
        return;
      }
      this.tagEditorSaving.set(true);
      forkJoin(devices.map((d) => this.mon.updateDeviceTags(d.id, normalized))).subscribe({
        next: () => {
          this.notify.success(`Теги сохранены для устройств: ${devices.length}.`, 'Устройства');
          this.clearDeviceSelection();
          this.mon.loadMonitoredDevices();
          this.closeTagEditor();
        },
        error: () => {
          this.notify.error('Не удалось сохранить теги.', 'Устройства');
          this.tagEditorSaving.set(false);
        },
      });
      return;
    }

    const device = this.tagEditorDevice();
    if (!device?.id) return;
    this.tagEditorSaving.set(true);
    this.mon.updateDeviceTags(device.id, normalized).subscribe({
      next: () => {
        this.notify.success('Теги сохранены.', 'Устройства');
        this.mon.loadMonitoredDevices();
        this.closeTagEditor();
      },
      error: () => {
        this.notify.error('Не удалось сохранить теги.', 'Устройства');
        this.tagEditorSaving.set(false);
      },
    });
  }

  protected openActionsMenu(event: Event, device: DeviceScanResult): void {
    this.deviceActionsMenuItems.set(this.buildDeviceActionsMenu(device));
    this.deviceActionsMenu?.toggle(event);
  }

  protected isActionPending(device: DeviceScanResult): boolean {
    return this.pendingActionDeviceIds().has(device.id);
  }

  protected openDevice(device: DeviceScanResult): void {
    void this.router.navigate(['/monitoring', device.id, 'info']);
  }

  protected toggleFiltersExpanded(): void {
    this.filtersExpanded.set(!this.filtersExpanded());
  }

  protected focusIpFilter(ip?: string | null, apply = false): void {
    this.filtersExpanded.set(true);
    if (ip != null && ip.trim() !== '') {
      this.ipDraft.set(ip);
    }
    this.focusWhenAvailable('monitoring-ip-filter');
    if (apply) {
      this.applyFilters();
    }
  }

  protected focusMacFilter(mac?: string | null, apply = false): void {
    this.filtersExpanded.set(true);
    if (mac != null && mac.trim() !== '') {
      this.macDraft.set(mac);
    }
    this.focusWhenAvailable('monitoring-mac-filter');
    if (apply) {
      this.applyFilters();
    }
  }

  protected onSearchDraftChange(value: string): void {
    this.searchDraft.set(value);
    this.devicesSearchApply$.next();
  }

  protected clearSearchDraft(): void {
    if (this.searchDraft() === '') {
      return;
    }
    this.searchDraft.set('');
    this.applySearchFilter();
  }

  protected focusSearchFilter(q?: string | null, apply = false): void {
    if (q != null && q.trim() !== '') {
      this.searchDraft.set(q);
    }
    this.focusWhenAvailable('monitoring-search-filter', false);
    if (apply) {
      this.applySearchFilter();
    }
  }

  protected clearAppliedFilter(key: string): void {
    switch (key) {
      case 'ip':
        this.ipDraft.set('');
        break;
      case 'mac':
        this.macDraft.set('');
        break;
      case 'status':
        this.statusDraft.set('');
        break;
      default:
        if (key.startsWith('tag:')) {
          const t = key.slice(4);
          this.tagDraft.update((cur) => cur.filter((x) => x !== t));
          break;
        }
        return;
      case 'health':
        this.healthDraft.set('ALL');
        break;
    }
    this.applyFilters();
  }

  private focusWhenAvailable(elementId: string, waitForExpanded = true): void {
    if (this.focusRetryRaf != null) {
      window.cancelAnimationFrame(this.focusRetryRaf);
      this.focusRetryRaf = null;
    }

    const startedAt = performance.now();
    const maxMs = 1500;

    const tick = () => {
      // Фильтры всегда в DOM; ждём только когда они реально показаны.
      if (waitForExpanded && !this.filtersExpanded()) {
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

  protected isAvailabilityActive(filter: MonitoringHostStatusFilter): boolean {
    return this.availabilityDraft() === filter;
  }

  protected toggleAvailability(filter: MonitoringHostStatusFilter): void {
    const next = this.availabilityDraft() === filter ? 'ALL' : filter;
    this.availabilityDraft.set(next);
    this.summaryRequested.set(true);
    this.applyFilters();
  }

  protected onHealthDraftChange(value: string | number | null): void {
    const v = value == null ? 'ALL' : String(value);
    if (v === 'ALL' || v === 'NORM' || v === 'WARN' || v === 'CRITICAL') {
      this.healthDraft.set(v as MonitoringHealthStatus | 'ALL');
    }
  }

  protected applySearchFilter(): void {
    const nextQ = this.searchDraft();
    if (nextQ === this.mon.devicesSearch()) {
      return;
    }
    this.clearDeviceSelection();
    if (nextQ.trim() !== '') {
      this.summaryRequested.set(true);
    }
    this.mon.devicesSearch.set(nextQ);
    this.reloadListFromFilters(0);
  }

  protected applyFilters(): void {
    this.clearDeviceSelection();
    this.summaryRequested.set(true);
    this.mon.monitoringHostStatusFilter.set(this.availabilityDraft());
    this.mon.devicesSearch.set(this.searchDraft());
    this.mon.deviceIpFilter.set(this.ipDraft());
    this.mon.deviceMacFilter.set(this.macDraft());
    this.mon.deviceStatusFilter.set(this.statusDraft());
    this.mon.deviceTagFilter.set(this.tagDraft());
    this.mon.deviceHealthStatusFilter.set(this.healthDraft());

    this.reloadListFromFilters(0);
  }

  private reloadListFromFilters(page = 0): void {
    this.filterDrivenListLoad = true;
    this.scanJobs.reloadDiscoverySummary();
    const size = this.mon.monitoredDevicesPage()?.size ?? this.mon.monitoredDevicesPageSize();
    this.mon.loadMonitoredDevices(
      page,
      size,
      this.mon.monitoredDevicesSortField(),
      this.mon.monitoredDevicesSortOrder(),
    );
  }

  protected resetFilters(): void {
    this.clearDeviceSelection();
    this.availabilityDraft.set('ALL');
    this.searchDraft.set('');
    this.ipDraft.set('');
    this.macDraft.set('');
    this.statusDraft.set('');
    this.tagDraft.set([]);
    this.tagInputDraft.set('');
    this.healthDraft.set('ALL');
    this.summaryRequested.set(false);
    this.mon.resetMonitoredDeviceFilters();
  }

  protected addTagFromInput(): void {
    const raw = this.tagInputDraft();
    this.tagInputDraft.set('');
    this.applyTagFilter(raw);
  }

  protected applyTagFilter(tag: string): void {
    const t = (tag ?? '').trim();
    if (!t) return;
    this.filtersExpanded.set(true);
    this.tagDraft.update((cur) => (cur.includes(t) ? cur : [...cur, t]));
    this.summaryRequested.set(true);
    this.applyFilters();
  }

  protected removeTagFilter(tag: string): void {
    const t = (tag ?? '').trim();
    if (!t) return;
    this.tagDraft.update((cur) => cur.filter((x) => x !== t));
    this.applyFilters();
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    if (this.filterDrivenListLoad) {
      return;
    }
    this.loadDevicesPageFromUiEvent(event);
  }

  private loadDevicesPageFromUiEvent(event: {
    first?: number | null;
    rows?: number | null;
    sortField?: string | string[] | null;
    sortOrder?: number | null;
  }): void {
    const rows =
      event.rows ?? this.mon.monitoredDevicesPage()?.size ?? this.mon.monitoredDevicesPageSize();
    const first = event.first ?? 0;
    const page = rows > 0 ? Math.floor(first / rows) : 0;
    const rawField = event.sortField;
    const field =
      (Array.isArray(rawField) ? rawField[0] : rawField) ?? this.mon.monitoredDevicesSortField();
    const sortOrder = event.sortOrder === -1 ? -1 : 1;
    const currentPage = this.mon.monitoredDevicesPage();
    const loadedSamePage =
      currentPage != null &&
      currentPage.number === page &&
      currentPage.size === rows &&
      this.mon.monitoredDevicesSortField() === field &&
      this.mon.monitoredDevicesSortOrder() === sortOrder;
    if (loadedSamePage) {
      return;
    }
    this.mon.loadMonitoredDevices(page, rows, field, sortOrder);
  }

  private restoreStateFromQueryParams(): void {
    // Читаем query-параметры один раз при инициализации компонента: queryParamMap — BehaviorSubject,
    this.route.queryParamMap.pipe(take(1), takeUntilDestroyed()).subscribe((params) => {
      const page = this.toSafeInt(params.get('page'), 0);
      const size = this.toSafeInt(params.get('size'), this.mon.monitoredDevicesPageSize());
      const sortField = params.get('sortField')?.trim() || this.mon.monitoredDevicesSortField();
      const sortOrderRaw = (params.get('sortOrder') || '').toLowerCase();
      const sortOrder: 1 | -1 = sortOrderRaw === 'desc' ? -1 : 1;

      const q = params.get('q') ?? '';
      const ip = params.get('ip') ?? '';
      const mac = params.get('mac') ?? '';
      const status = params.get('status') ?? '';
      const tagCsv = params.get('tag') ?? '';
      const tags = tagCsv
        .split(',')
        .map((t) => t.trim())
        .filter((t) => t.length > 0);
      const health = (params.get('health') || '').toUpperCase();
      const availability = (params.get('availability') || '').toUpperCase();

      // Черновики фильтров (UI)
      this.searchDraft.set(q);
      this.ipDraft.set(ip);
      this.macDraft.set(mac);
      this.statusDraft.set(status);
      this.tagDraft.set(tags);
      this.tagInputDraft.set('');
      if (health === 'NORM' || health === 'WARN' || health === 'CRITICAL') {
        this.healthDraft.set(health as MonitoringHealthStatus);
      } else {
        this.healthDraft.set('ALL');
      }
      if (availability === 'AVAILABLE' || availability === 'UNAVAILABLE' || availability === 'UNKNOWN') {
        this.availabilityDraft.set(availability as MonitoringHostStatusFilter);
      } else {
        this.availabilityDraft.set('ALL');
      }

      // Сервисные фильтры (источник правды для запроса)
      this.mon.devicesSearch.set(q);
      this.mon.deviceIpFilter.set(ip);
      this.mon.deviceMacFilter.set(mac);
      this.mon.deviceStatusFilter.set(status);
      this.mon.deviceTagFilter.set(tags);
      this.mon.deviceHealthStatusFilter.set(this.healthDraft());
      this.mon.monitoringHostStatusFilter.set(this.availabilityDraft());

      // Загружаем страницу, если пользователь уже авторизован (иначе это будет сделано после логина).
      if (this.auth.isAuthenticated()) {
        this.mon.loadMonitoredDevices(page, size, sortField, sortOrder);
      }
    });
  }

  private syncQueryParamsFromState(): void {
    const page = this.mon.monitoredDevicesPage()?.number ?? 0;
    const size = this.mon.monitoredDevicesPage()?.size ?? this.mon.monitoredDevicesPageSize();
    const sortField = this.mon.monitoredDevicesSortField();
    const sortOrder = this.mon.monitoredDevicesSortOrder() === -1 ? 'desc' : 'asc';

    const q = this.mon.devicesSearch().trim();
    const ip = this.mon.deviceIpFilter().trim();
    const mac = this.mon.deviceMacFilter().trim();
    const status = this.mon.deviceStatusFilter().trim();
    const tag = this.mon.deviceTagFilter().join(',');
    const health = this.mon.deviceHealthStatusFilter();
    const availability = this.mon.monitoringHostStatusFilter();

    const queryParams: Record<string, string | number | null> = {
      page,
      size,
      sortField,
      sortOrder,
      q: q || null,
      ip: ip || null,
      mac: mac || null,
      status: status || null,
      tag: tag || null,
      health: health !== 'ALL' ? health : null,
      availability: availability !== 'ALL' ? availability : null,
    };

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private toSafeInt(raw: string | null, fallback: number): number {
    if (raw == null) return fallback;
    const parsed = Number.parseInt(raw, 10);
    if (!Number.isFinite(parsed)) return fallback;
    return Math.max(0, parsed);
  }

  private buildDeviceActionsMenu(device: DeviceScanResult): MenuItem[] {
    const pending = this.isActionPending(device) || this.mon.devicesLoading();
    return [
      {
        label: 'Снять с мониторинга',
        icon: 'pi pi-ban',
        disabled: pending,
        command: () => this.confirmDeactivateAllItems(device),
      },
      {
        label: 'Удалить',
        icon: 'pi pi-trash',
        disabled: pending,
        command: () => this.confirmDeleteDevice(device),
      },
    ];
  }

  private confirmDeactivateAllItems(device: DeviceScanResult): void {
    if (this.isActionPending(device)) {
      return;
    }
    this.confirmation.confirm({
      header: 'Снять устройство с мониторинга?',
      message: `Будет остановлен мониторинг всех item устройства «${device.hostName || device.name || device.ip}». Само устройство останется в списке.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Снять с мониторинга',
      rejectLabel: 'Отмена',
      accept: () => this.deactivateAllItems(device),
    });
  }

  private deactivateAllItems(device: DeviceScanResult): void {
    this.setActionPending(device.id, true);
    this.mon.updateDeviceItems(device.id, []).subscribe({
      next: () => {
        this.notify.success('Мониторинг всех item устройства остановлен.', 'Мониторинг');
        this.mon.loadMonitoredDevices();
        this.scanJobs.reloadDiscoverySummary();
      },
      error: () => {
        this.notify.error('Не удалось снять устройство с мониторинга.', 'Мониторинг');
        this.setActionPending(device.id, false);
      },
      complete: () => {
        this.setActionPending(device.id, false);
      },
    });
  }

  private confirmDeleteDevice(device: DeviceScanResult): void {
    if (this.isActionPending(device)) {
      return;
    }
    this.confirmation.confirm({
      header: 'Удалить устройство из системы?',
      message:
        `Будет остановлен сбор данных, и устройство «${device.hostName || device.name || device.ip}» будет удалено из мониторинга и системы. Действие необратимо.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteDevice(device),
    });
  }

  private deleteDevice(device: DeviceScanResult): void {
    const id = this.toDeviceIdNumber(device.id);
    if (id == null) {
      this.notify.error('Не удалось определить ID устройства для удаления.', 'Мониторинг');
      return;
    }
    this.setActionPending(device.id, true);
    this.mon.deactivateMonitoringByIds([id]).subscribe({
      next: (devices) => {
        this.mon.applyMonitoredDevices(devices);
        this.notify.success('Устройство удалено из системы и снято с мониторинга.', 'Мониторинг');
        this.scanJobs.reloadDiscoverySummary();
      },
      error: () => {
        this.notify.error('Не удалось удалить устройство из системы.', 'Мониторинг');
        this.setActionPending(device.id, false);
      },
      complete: () => {
        this.setActionPending(device.id, false);
      },
    });
  }

  private setActionPending(deviceId: string, pending: boolean): void {
    this.pendingActionDeviceIds.update((current) => {
      const next = new Set(current);
      if (pending) {
        next.add(deviceId);
      } else {
        next.delete(deviceId);
      }
      return next;
    });
  }

  private toDeviceIdNumber(deviceId: string): number | null {
    const parsed = Number.parseInt(deviceId, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  }

  private hasAnyRole(...roles: AppRole[]): boolean {
    const current = this.auth.authSession()?.roles ?? [];
    return roles.some((role) => current.includes(role));
  }

  private ensureTemplatesLoaded(): void {
    if (this.mon.templatesLoading()) return;
    if ((this.mon.monitoringTemplates() ?? []).length > 0) return;
    this.mon.loadMonitoringTemplates();
  }

  private applyBulkTemplates(templateIds: string[]): void {
    const devices = this.selectedDevices();
    const normalizedTemplateIds = Array.from(
      new Set(templateIds.map((id) => id.trim()).filter((id) => id.length > 0)),
    );
    if (!devices.length || !normalizedTemplateIds.length) {
      return;
    }
    this.bulkActionPending.set(true);
    this.mon.activateMonitoring(devices, normalizedTemplateIds, null).subscribe({
      next: () => {
        this.notify.success(`Шаблон применён к устройствам: ${devices.length}.`, 'Мониторинг');
        this.clearDeviceSelection();
        this.mon.loadMonitoredDevices();
        this.scanJobs.reloadDiscoverySummary();
      },
      error: () => {
        this.notify.error('Не удалось применить шаблон к выбранным устройствам.', 'Мониторинг');
        this.bulkActionPending.set(false);
      },
      complete: () => {
        this.bulkActionPending.set(false);
      },
    });
  }

  private bulkDeactivate(devices: DeviceScanResult[]): void {
    const targets = devices.filter((d) => !!d.id);
    if (!targets.length) {
      return;
    }
    this.bulkActionPending.set(true);
    forkJoin(targets.map((d) => this.mon.updateDeviceItems(d.id, []))).subscribe({
      next: () => {
        this.notify.success(`С мониторинга снято устройств: ${targets.length}.`, 'Мониторинг');
        this.clearDeviceSelection();
        this.mon.loadMonitoredDevices();
        this.scanJobs.reloadDiscoverySummary();
      },
      error: () => {
        this.notify.error('Не удалось снять устройства с мониторинга.', 'Мониторинг');
        this.bulkActionPending.set(false);
      },
      complete: () => {
        this.bulkActionPending.set(false);
      },
    });
  }

  private bulkDelete(devices: DeviceScanResult[]): void {
    const ids = devices
      .map((d) => this.toDeviceIdNumber(d.id))
      .filter((id): id is number => id != null);
    if (!ids.length) {
      this.notify.error('Не удалось определить ID выбранных устройств для удаления.', 'Мониторинг');
      return;
    }
    this.bulkActionPending.set(true);
    this.mon.deactivateMonitoringByIds(ids).subscribe({
      next: (updated) => {
        this.mon.applyMonitoredDevices(updated);
        this.notify.success(`Удалено устройств: ${ids.length}.`, 'Мониторинг');
        this.clearDeviceSelection();
        this.scanJobs.reloadDiscoverySummary();
      },
      error: () => {
        this.notify.error('Не удалось удалить выбранные устройства.', 'Мониторинг');
        this.bulkActionPending.set(false);
      },
      complete: () => {
        this.bulkActionPending.set(false);
      },
    });
  }

  private deviceCountLabel(count: number): string {
    const mod10 = count % 10;
    const mod100 = count % 100;
    if (mod10 === 1 && mod100 !== 11) {
      return 'устройство';
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
      return 'устройства';
    }
    return 'устройств';
  }

  ngOnDestroy(): void {
    if (this.focusRetryRaf != null) {
      window.cancelAnimationFrame(this.focusRetryRaf);
      this.focusRetryRaf = null;
    }
  }
}
