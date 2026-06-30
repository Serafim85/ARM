import { DatePipe, NgStyle } from '@angular/common';
import { Component, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CompactType, DisplayGrid, GridType, Gridster, GridsterItem } from 'angular-gridster2';
import type { GridsterConfig, GridsterItemConfig } from 'angular-gridster2';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { Menu, MenuModule } from 'primeng/menu';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../../auth.service';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import type { DashboardRecord, DashboardVisibility, DashboardWidget, UserDirectoryEntry, WidgetFieldRecord } from '../../models';
import { DashboardsService } from '../../services/dashboards.service';
import { buildDashboardWidgetPlacement } from './dashboard-widget-grid-layout';
import { DashboardClockWidgetComponent } from './dashboard-clock-widget/dashboard-clock-widget.component';
import { DashboardGraphWidgetComponent } from './dashboard-graph-widget/dashboard-graph-widget.component';
import {
  GRAPH_WIDGET_PERIOD_OPTIONS,
  parseGraphWidgetFields,
  type GraphWidgetPeriod,
} from './dashboard-graph-widget/graph-widget-config';
import { DashboardProblemsWidgetComponent } from './dashboard-problems-widget/dashboard-problems-widget.component';
import { DashboardWidgetDialogComponent } from './dashboard-widget-dialog/dashboard-widget-dialog.component';
import { isEditableWidgetType } from './dashboard-widget-dialog/widget-editor-registry';

type WidgetGridItem = GridsterItemConfig & {
  widgetId: number;
  widget: DashboardWidget;
};

const LAYOUT_SAVE_DEBOUNCE_MS = 400;

/** Класс зоны захвата для drag (должен совпадать с шаблоном). */
const DASHBOARD_WIDGET_TILE_DRAG_HANDLE_CLASS = 'dashboard-widget-tile-drag-handle';

@Component({
  selector: 'app-dashboard-detail-page',
  standalone: true,
  imports: [
    DatePipe,
    NgStyle,
    FormsModule,
    RouterLink,
    Gridster,
    GridsterItem,
    ButtonModule,
    DialogModule,
    MenuModule,
    InputTextModule,
    SelectModule,
    SelectButtonModule,
    MultiSelectModule,
    TooltipModule,
    DeviceOptionSelectComponent,
    DashboardWidgetDialogComponent,
    DashboardClockWidgetComponent,
    DashboardGraphWidgetComponent,
    DashboardProblemsWidgetComponent,
  ],
  templateUrl: './dashboard-detail-page.component.html',
  styleUrl: './dashboard-detail-page.component.css',
})
export class DashboardDetailPageComponent implements OnInit {
  @ViewChild('dashboardActionsMenu') private dashboardActionsMenu?: Menu;
  @ViewChild('widgetTileMenu') private widgetTileMenu?: Menu;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dashboardsApi = inject(DashboardsService);
  private readonly auth = inject(AuthService);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly dashboard = signal<DashboardRecord | null>(null);
  protected readonly directory = signal<UserDirectoryEntry[]>([]);

  protected readonly editOpen = signal(false);
  protected readonly editName = signal('');
  protected readonly editVisibility = signal<DashboardVisibility>('PRIVATE');
  protected readonly editSharedIds = signal<number[]>([]);
  protected readonly saving = signal(false);

  protected readonly deleteOpen = signal(false);
  protected readonly deleting = signal(false);
  protected readonly widgetDialogVisible = signal(false);
  protected readonly widgetDialogTarget = signal<DashboardWidget | null>(null);
  protected readonly widgetTileMenuItems = signal<MenuItem[]>([]);
  protected readonly widgetDeleteOpen = signal(false);
  protected readonly widgetDeleteTarget = signal<DashboardWidget | null>(null);
  protected readonly widgetDeleting = signal(false);
  protected readonly graphPeriodOverrides = signal<Record<number, GraphWidgetPeriod>>({});

  protected readonly graphPeriodOptions = GRAPH_WIDGET_PERIOD_OPTIONS;

  protected readonly visibilityOptions = [
    { label: 'Только я', value: 'PRIVATE' as const },
    { label: 'Общий доступ (выбранные пользователи)', value: 'SHARED' as const },
  ];

  protected readonly currentUserId = computed(() => this.auth.authSession()?.userId ?? null);

  protected readonly canEdit = computed(() => {
    const d = this.dashboard();
    const session = this.auth.authSession();
    if (!d || !session) {
      return false;
    }
    if (session.roles?.includes('ADMIN')) {
      return true;
    }
    const uid = session.userId;
    if (uid == null) {
      return false;
    }
    return d.ownerId === uid;
  });

  protected readonly dashboardDetailMenuItems = computed<MenuItem[]>(() => {
    if (!this.canEdit()) {
      return [];
    }
    return [
      { label: 'Добавить виджет', icon: 'pi pi-plus', command: () => this.openCreateWidget() },
      { label: 'Редактировать', icon: 'pi pi-pencil', command: () => this.openEdit() },
      { label: 'Удалить', icon: 'pi pi-trash', command: () => this.openDelete() },
    ];
  });

  protected readonly multiSelectOptions = computed(() => {
    const uid = this.currentUserId();
    return this.directory().filter((u) => u.id !== uid);
  });

  protected readonly widgetPlacements = computed(() => {
    const w = this.dashboard()?.widgets ?? [];
    if (!w.length) {
      return [];
    }
    return buildDashboardWidgetPlacement(w);
  });
  protected readonly widgetGridItems = computed<WidgetGridItem[]>(() =>
    this.widgetPlacements().map((p) => ({
      widgetId: p.widget.id,
      widget: p.widget,
      x: Math.max(0, p.colStart - 1),
      y: Math.max(0, p.rowStart - 1),
      cols: Math.max(1, p.colSpan),
      rows: Math.max(1, p.rowSpan),
      minItemCols: 1,
      minItemRows: 1,
    })),
  );
  protected readonly gridOptions = computed<GridsterConfig>(() => {
    const edit = this.canEdit();
    return {
      gridType: GridType.VerticalFixed,
      fixedRowHeight: 92,
      compactType: CompactType.None,
      minCols: 12,
      maxCols: 12,
      outerMargin: true,
      margin: 10,
      displayGrid: edit ? DisplayGrid.OnDragAndResize : DisplayGrid.None,
      pushItems: false,
      swap: true,
      swapWhileDragging: false,
      scrollSensitivity: 56,
      scrollSpeed: 10,
      disableScrollVertical: true,
      draggable: {
        enabled: edit,
        ignoreContent: edit,
        dragHandleClass: DASHBOARD_WIDGET_TILE_DRAG_HANDLE_CLASS,
        ignoreContentClass: 'dashboard-widget-tile-body',
        delayStart: edit ? 120 : 0,
      },
      resizable: {
        enabled: edit,
      },
      itemChangeCallback: (item) => this.onGridLayoutChanged(item as WidgetGridItem),
      itemResizeCallback: (item) => this.onGridLayoutChanged(item as WidgetGridItem),
    };
  });
  protected readonly layoutSaving = signal(false);

  /** Рамка вокруг `gridster-item` (настраивается в диалоге виджета). */
  protected gridsterItemBorderStyle(widget: DashboardWidget): Record<string, string> {
    const w = widget.borderWidthPx ?? 1;
    const raw = (widget.borderColor ?? 'gray').trim();
    const c = raw.length > 0 ? raw : 'gray';
    const box: Record<string, string> = { 'box-sizing': 'border-box' };
    if (w <= 0) {
      box['border'] = 'none';
      return box;
    }
    box['border'] = `${w}px solid ${c}`;
    return box;
  }

  private readonly pendingLayoutByWidgetId = new Map<
    number,
    { x: number; y: number; cols: number; rows: number; version: number }
  >();
  private readonly layoutSaveTimers = new Map<number, ReturnType<typeof setTimeout>>();
  private readonly layoutSaveInFlight = new Set<number>();
  private readonly layoutSavedVersionByWidgetId = new Map<number, number>();

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : NaN;
    if (!Number.isFinite(id)) {
      this.error.set('Некорректный идентификатор дашборда.');
      this.loading.set(false);
      return;
    }

    this.dashboardsApi.listUserDirectory().subscribe({
      next: (rows) => this.directory.set(rows),
      error: () => this.directory.set([]),
    });

    this.load(id);
  }

  protected load(id: number): void {
    this.loading.set(true);
    this.error.set('');
    this.dashboardsApi.getById(id).subscribe({
      next: (row) => {
        this.dashboard.set(this.normalize(row));
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Дашборд не найден или нет доступа.');
        this.dashboard.set(null);
        this.loading.set(false);
      },
    });
  }

  protected openDashboardActionsMenu(event: Event): void {
    this.dashboardActionsMenu?.toggle(event);
  }

  protected graphPeriod(widget: DashboardWidget): GraphWidgetPeriod {
    const override = this.graphPeriodOverrides()[widget.id];
    if (override) {
      return override;
    }
    return parseGraphWidgetFields(widget.fields ?? []).period;
  }

  protected setGraphPeriod(widgetId: number, period: GraphWidgetPeriod): void {
    this.graphPeriodOverrides.update((map) => ({
      ...map,
      [widgetId]: period,
    }));
  }

  protected openEdit(): void {
    const d = this.dashboard();
    if (!d) {
      return;
    }
    this.editName.set(d.name);
    this.editVisibility.set(d.visibility);
    this.editSharedIds.set([...(d.sharedUserIds ?? [])]);
    this.editOpen.set(true);
  }

  protected closeEdit(): void {
    if (!this.saving()) {
      this.editOpen.set(false);
    }
  }

  protected submitEdit(): void {
    const d = this.dashboard();
    if (!d) {
      return;
    }
    const name = this.editName().trim();
    if (!name) {
      this.error.set('Укажите название дашборда.');
      return;
    }
    const vis = this.editVisibility();
    const shared = vis === 'SHARED' ? [...this.editSharedIds()] : [];
    this.saving.set(true);
    this.error.set('');
    this.dashboardsApi
      .update(d.id, { name, visibility: vis, sharedUserIds: shared })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.editOpen.set(false);
          this.load(d.id);
        },
        error: (e) => {
          this.saving.set(false);
          this.error.set(e?.error?.message ?? e?.message ?? 'Не удалось сохранить изменения.');
        },
      });
  }

  protected onEditVisibilityChange(value: string | number | null): void {
    const v = value == null ? 'PRIVATE' : String(value);
    if (v === 'PRIVATE' || v === 'SHARED') {
      this.editVisibility.set(v);
    }
  }

  protected openDelete(): void {
    this.deleteOpen.set(true);
  }

  protected closeDelete(): void {
    if (!this.deleting()) {
      this.deleteOpen.set(false);
    }
  }

  protected confirmDelete(): void {
    const d = this.dashboard();
    if (!d) {
      return;
    }
    this.deleting.set(true);
    this.error.set('');
    this.dashboardsApi.delete(d.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteOpen.set(false);
        void this.router.navigate(['/dashboards']);
      },
      error: (e) => {
        this.deleting.set(false);
        this.error.set(e?.error?.message ?? e?.message ?? 'Не удалось удалить дашборд.');
      },
    });
  }

  protected visibilityLabel(v: DashboardVisibility): string {
    return v === 'PRIVATE' ? 'Приватный' : 'Общий';
  }

  protected openCreateWidget(): void {
    if (!this.canEdit()) {
      return;
    }
    this.widgetDialogTarget.set(null);
    this.widgetDialogVisible.set(true);
  }

  protected openEditWidget(widget: DashboardWidget): void {
    if (!this.canEdit()) {
      return;
    }
    if (!isEditableWidgetType(widget.widgetType)) {
      return;
    }
    this.widgetDialogTarget.set(widget);
    this.widgetDialogVisible.set(true);
  }

  protected openWidgetTileMenu(event: Event, widget: DashboardWidget): void {
    event.stopPropagation();
    const editable = this.canEditWidget(widget.widgetType);
    const items: MenuItem[] = [
      {
        label: 'Редактировать',
        icon: 'pi pi-pencil',
        disabled: !editable,
        command: () => this.openEditWidget(widget),
      },
      {
        label: 'Удалить',
        icon: 'pi pi-trash',
        command: () => this.openWidgetDeleteDialog(widget),
      },
    ];
    this.widgetTileMenuItems.set(items);
    this.widgetTileMenu?.toggle(event);
  }

  protected openWidgetDeleteDialog(widget: DashboardWidget): void {
    if (!this.canEdit()) {
      return;
    }
    this.widgetDeleteTarget.set(widget);
    this.widgetDeleteOpen.set(true);
  }

  protected closeWidgetDelete(): void {
    if (!this.widgetDeleting()) {
      this.widgetDeleteOpen.set(false);
      this.widgetDeleteTarget.set(null);
    }
  }

  protected onWidgetDeleteDialogVisibleChange(visible: boolean): void {
    this.widgetDeleteOpen.set(visible);
    if (!visible && !this.widgetDeleting()) {
      this.widgetDeleteTarget.set(null);
    }
  }

  protected confirmDeleteWidget(): void {
    const d = this.dashboard();
    const w = this.widgetDeleteTarget();
    if (!d || !w) {
      return;
    }
    this.widgetDeleting.set(true);
    this.error.set('');
    this.dashboardsApi.deleteWidget(d.id, w.id).subscribe({
      next: () => {
        this.widgetDeleting.set(false);
        this.widgetDeleteOpen.set(false);
        this.widgetDeleteTarget.set(null);
        this.load(d.id);
      },
      error: (e) => {
        this.widgetDeleting.set(false);
        this.error.set(e?.error?.message ?? e?.message ?? 'Не удалось удалить виджет.');
      },
    });
  }

  protected canEditWidget(widgetType: string): boolean {
    return isEditableWidgetType(widgetType);
  }

  protected onWidgetDialogVisibleChange(visible: boolean): void {
    this.widgetDialogVisible.set(visible);
    if (!visible) {
      this.widgetDialogTarget.set(null);
    }
  }

  protected onWidgetSaved(): void {
    const d = this.dashboard();
    if (!d) {
      return;
    }
    this.load(d.id);
  }

  private onGridLayoutChanged(item: WidgetGridItem): void {
    if (!this.canEdit()) {
      return;
    }
    const d = this.dashboard();
    const widget = d?.widgets.find((w) => w.id === item.widgetId);
    if (!d || !widget) {
      return;
    }
    const x = Math.max(0, Math.round(item.x ?? 0));
    const y = Math.max(0, Math.round(item.y ?? 0));
    const cols = Math.max(1, Math.round(item.cols ?? 1));
    const rows = Math.max(1, Math.round(item.rows ?? 1));
    if (widget.gridX === x && widget.gridY === y && widget.width === cols && widget.height === rows) {
      return;
    }
    const version = (this.pendingLayoutByWidgetId.get(widget.id)?.version ?? 0) + 1;
    this.pendingLayoutByWidgetId.set(widget.id, { x, y, cols, rows, version });
    const prevTimer = this.layoutSaveTimers.get(widget.id);
    if (prevTimer) {
      clearTimeout(prevTimer);
    }
    const timer = setTimeout(() => {
      this.layoutSaveTimers.delete(widget.id);
      this.flushLayoutSave(widget.id);
    }, LAYOUT_SAVE_DEBOUNCE_MS);
    this.layoutSaveTimers.set(widget.id, timer);
  }

  private flushLayoutSave(widgetId: number): void {
    if (this.layoutSaveInFlight.has(widgetId)) {
      return;
    }
    const pending = this.pendingLayoutByWidgetId.get(widgetId);
    if (!pending) {
      return;
    }
    const lastSaved = this.layoutSavedVersionByWidgetId.get(widgetId) ?? 0;
    if (pending.version <= lastSaved) {
      return;
    }
    const d = this.dashboard();
    const widget = d?.widgets.find((w) => w.id === widgetId);
    if (!d || !widget) {
      return;
    }
    this.layoutSaveInFlight.add(widgetId);
    this.layoutSaving.set(true);
    this.error.set('');
    this.dashboardsApi
      .updateWidget(d.id, widgetId, {
        widgetType: widget.widgetType,
        name: widget.name,
        gridX: pending.x,
        gridY: pending.y,
        width: pending.cols,
        height: pending.rows,
        viewMode: widget.viewMode ?? 0,
        refreshIntervalSeconds: widget.refreshIntervalSeconds ?? null,
        showHeader: widget.showHeader ?? true,
        borderWidthPx: widget.borderWidthPx ?? 1,
        borderColor: (widget.borderColor ?? 'gray').trim() || 'gray',
        fields: this.toFieldUpserts(widget.fields),
      })
      .subscribe({
        next: (updatedWidget) => {
          this.layoutSavedVersionByWidgetId.set(widgetId, pending.version);
          this.mergeUpdatedWidget(updatedWidget);
          this.layoutSaveInFlight.delete(widgetId);
          if (this.layoutSaveInFlight.size === 0) {
            this.layoutSaving.set(false);
          }
          const newest = this.pendingLayoutByWidgetId.get(widgetId);
          if (newest && newest.version > pending.version) {
            queueMicrotask(() => this.flushLayoutSave(widgetId));
          }
        },
        error: (e) => {
          this.layoutSaveInFlight.delete(widgetId);
          if (this.layoutSaveInFlight.size === 0) {
            this.layoutSaving.set(false);
          }
          this.error.set(e?.error?.message ?? e?.message ?? 'Не удалось сохранить новую позицию виджета.');
        },
      });
  }

  private mergeUpdatedWidget(updated: DashboardWidget): void {
    const current = this.dashboard();
    if (!current) {
      return;
    }
    const normalized = this.normalizeWidget(updated);
    const widgets = current.widgets.map((w) => (w.id === normalized.id ? normalized : w));
    this.dashboard.set({
      ...current,
      widgets: widgets.sort((a, b) => a.sortOrder - b.sortOrder),
    });
  }

  private toFieldUpserts(fields: WidgetFieldRecord[]) {
    return (fields ?? []).map((f) => ({
      name: f.name ?? '',
      valueInt: f.valueInt ?? 0,
      valueStr: f.valueStr ?? '',
    }));
  }

  private normalizeWidget(w: DashboardWidget): DashboardWidget {
    const borderColor = (w.borderColor ?? 'gray').trim() || 'gray';
    return {
      ...w,
      gridX: w.gridX ?? 0,
      gridY: w.gridY ?? 0,
      width: w.width ?? 1,
      height: w.height ?? 2,
      viewMode: w.viewMode ?? 0,
      refreshIntervalSeconds: w.refreshIntervalSeconds ?? null,
      showHeader: w.showHeader ?? true,
      borderWidthPx: w.borderWidthPx ?? 1,
      borderColor,
      fields: Array.isArray(w.fields) ? w.fields : [],
    };
  }

  private normalize(d: DashboardRecord): DashboardRecord {
    return {
      ...d,
      sharedUserIds: Array.isArray(d.sharedUserIds) ? d.sharedUserIds : [],
      widgets: Array.isArray(d.widgets)
        ? [...d.widgets].map((w) => this.normalizeWidget(w)).sort((a, b) => a.sortOrder - b.sortOrder)
        : [],
    };
  }
}
