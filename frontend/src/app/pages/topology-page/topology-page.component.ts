import { HttpClient } from '@angular/common/http';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import {
  afterNextRender,
  ChangeDetectorRef,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, firstValueFrom, forkJoin, map, Observable, of } from 'rxjs';
import type { MenuItem } from 'primeng/api';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { InputTextModule } from 'primeng/inputtext';
import { Menu, MenuModule } from 'primeng/menu';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { TieredMenu, TieredMenuModule } from 'primeng/tieredmenu';
import cytoscape, {
  type Core,
  type ElementDefinition,
  type EventObject,
  type NodeSingular,
} from 'cytoscape';
import { API_BASE_URL } from '../../api-config';
import { AuthService } from '../../auth.service';
import { NotifierService } from '../../notifier.service';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import {
  type MonitoringHealthStatus,
  type TopologyLayoutPatchItem,
  type TopologyNodeKind,
  type TopologyObjectCreatePayload,
  type TopologyObjectRecord,
  type TopologyObjectUpdatePayload,
  type TopologyRecord,
  type TopologyUpdateRequest,
  type TopologyVisibility,
  type UserDirectoryEntry,
} from '../../models';
import { DashboardsService } from '../../services/dashboards.service';
import { TopologyService } from '../../services/topology.service';
import {
  deviceHostAvailabilityFromMonitoringStatus,
  topologyLinkedNodeChrome,
} from './topology-node-device-availability';
import {
  subscribeTopologyNodeIconRasterReady,
  topologyNodeIconBackgroundStyle,
} from './topology-node-icon-background';

type TopologySelectOption = { label: string; value: number };
type TopologyLayerEntry = { parentObjectId: number; label: string };

/** Диалог несохранённого графа: смена топологии в селекте или уход с маршрута `/topology`. */
type PendingUnsavedChangesDialog =
  | { kind: 'topology'; to: TopologySelectOption | null }
  | { kind: 'navigate'; url: string; to?: undefined };

type TopologyCyObjectKind = 'NODE' | 'GROUP' | 'EDGE';

type MonitoringDevicePickRow = {
  id: number;
  name: string;
  ip: string;
  status?: string;
  healthStatus?: string;
};
type MonitoringPickPage = { content: MonitoringDevicePickRow[] };

const TOPOLOGY_NODE_KIND_MENU_OPTIONS: { label: string; value: TopologyNodeKind }[] = [
  { label: 'Сеть', value: 'NETWORK' },
  { label: 'Стойка', value: 'RACK' },
  { label: 'Сервер', value: 'SERVER' },
  { label: 'Принтер', value: 'PRINTER' },
  { label: 'Маршрутизатор', value: 'ROUTER' },
  { label: 'Коммутатор', value: 'SWITCH' },
  { label: 'ПК', value: 'PC' },
  { label: 'Ноутбук', value: 'NOTEBOOK' },
  { label: 'Межсетевой экран', value: 'FIREWALL' },
];

/** Ожидающее сохранение назначение в группу (по id объекта в БД). */
type TopologyPendingMembership =
  | { t: 'g'; id: number }
  | { t: 'c' }
  | { t: 'd'; el: string };

/** Зоны изменения размера рамки группы (экранные координаты). */
type GroupResizeHandle = 'nw' | 'n' | 'ne' | 'w' | 'e' | 'sw' | 's' | 'se';

/** Рамка группы в стиле, если в БД цвет не задан. */
const TOPOLOGY_GROUP_DEFAULT_BORDER = '#64748b';
/** Линия и стрелка связи, если цвет не задан. */
const TOPOLOGY_EDGE_DEFAULT_LINE = '#94a3b8';
/** Значение color input, когда подложка слоя не задана (только UI). */
const TOPOLOGY_LAYER_BACKDROP_PICKER_EMPTY = '#f1f5f9';

function topologyCyEdgeStrokeColor(ele: { data: (k: string) => unknown }): string {
  const c = ele.data('edgeLineColor');
  if (typeof c !== 'string') return TOPOLOGY_EDGE_DEFAULT_LINE;
  const s = c.trim();
  if (/^#[0-9A-Fa-f]{6}$/i.test(s)) return s.toLowerCase();
  if (/^#[0-9A-Fa-f]{3}$/i.test(s)) {
    return (`#${s[1]}${s[1]}${s[2]}${s[2]}${s[3]}${s[3]}`).toLowerCase();
  }
  return TOPOLOGY_EDGE_DEFAULT_LINE;
}

const EDGE_DRAW_LONG_PRESS_MS = 480;
const EDGE_DRAW_PREVIEW_NODE_ID = '__edge_preview_target';
const EDGE_DRAW_PREVIEW_EDGE_ID = '__edge_preview_link';

/** Синхронно с опциями Cytoscape ниже (clamp при чтении из localStorage). */
const TOPOLOGY_CY_MIN_ZOOM = 0.25;
const TOPOLOGY_CY_MAX_ZOOM = 1.59;
const TOPOLOGY_VIEWPORT_STORAGE_PREFIX = 'netscan.topology.cyViewport.v1';

@Component({
  selector: 'app-topology-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    MenuModule,
    TieredMenuModule,
    ConfirmDialogModule,
    DialogModule,
    AutoCompleteModule,
    InputTextModule,
    SelectModule,
    MultiSelectModule,
    CheckboxModule,
    DeviceOptionSelectComponent,
  ],
  providers: [ConfirmationService],
  templateUrl: './topology-page.component.html',
  styleUrl: './topology-page.component.css',
})
export class TopologyPageComponent {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly destroyRef = inject(DestroyRef);
  private readonly topologyService = inject(TopologyService);
  private readonly dashboardsApi = inject(DashboardsService);
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly auth = inject(AuthService);
  private readonly notifier = inject(NotifierService);
  private readonly confirm = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly cyMount = viewChild<ElementRef<HTMLElement>>('cyMount');
  private readonly topologyMenuRef = viewChild<Menu>('topologyMenu');
  private readonly cyContextMenuRef = viewChild<TieredMenu>('cyContextMenu');
  private readonly layerBgFileInput = viewChild<ElementRef<HTMLInputElement>>('layerBgFileInput');
  private readonly layerSettingsBgFileInput = viewChild<ElementRef<HTMLInputElement>>('layerSettingsBgFileInput');
  private readonly cdr = inject(ChangeDetectorRef);

  private cy: Core | null = null;
  /** Подгонка canvas Cytoscape при изменении flex-высоты контейнера. */
  private cyResizeObserver: ResizeObserver | null = null;
  /** Слияние частых срабатываний ResizeObserver в один кадр. */
  private cyResizeAndFitRaf: number | null = null;
  /** Если совпадает с текущим запросом — обновляем элементы без destroy (меньше моргания). */
  private cyGraphContext: { topologyId: number; layerParentId: number | null } | null = null;
  /** Игнорировать устаревший ответ `listObjects` при быстром переключении слоя / повторном rebuild. */
  private topologyGraphFetchSeq = 0;
  /** Игнорировать устаревшие `getObject` / `getLayerBackground` после смены слоя. */
  private layerBackdropFetchSeq = 0;
  /** Не писать в localStorage при программном zoom/pan/fit. */
  private suppressNextCyViewportPersist = false;
  private cyViewportSaveTimer: ReturnType<typeof setTimeout> | null = null;
  /** Якорь для PrimeNG Menu: show() позиционирует оверлей по currentTarget, не по clientX/Y. */
  private cyMenuAnchor: HTMLDivElement | null = null;
  /** Скрывает системное меню по ПКМ на области графа. */
  private readonly cyPreventNativeContextMenu = (e: Event): void => {
    e.preventDefault();
  };
  /** Позиции узлов NODE (id в БД), ожидающие сохранения. */
  private readonly pendingPositionUpdates = new Map<number, { x: number; y: number }>();
  /** Центр и размер рамки GROUP (id в БД). */
  private readonly pendingGroupLayoutUpdates = new Map<
    number,
    { cx: number; cy: number; w: number; h: number }
  >();
  private autosaveLayoutTimer: ReturnType<typeof setTimeout> | null = null;
  private groupResizeState: {
    node: NodeSingular;
    dbId: number | undefined;
    mode: GroupResizeHandle;
    anchorX1: number;
    anchorY1: number;
    anchorX2: number;
    anchorY2: number;
  } | null = null;
  private groupResizeListeners: {
    move: (e: MouseEvent) => void;
    up: () => void;
  } | null = null;
  /** Сброс курсора resize при выходе мыши с canvas. */
  private groupResizeCursorLeaveCleanup: (() => void) | null = null;
  /** Чтобы не затирать курсор Cytoscape на каждом mousemove, когда не над ручкой группы. */
  private lastAppliedGroupResizeCursor: string | null = null;
  private readonly unsubscribeNodeIconRasterReady: (() => void) | null;

  protected onObjectSettingsNodeKindChange(value: string | number | null): void {
    const v = value == null ? null : (String(value) as TopologyNodeKind);
    if (v && this.topologyNodeKindSelectOptions.some((opt) => opt.value === v)) {
      this.objectSettingsNodeKind.set(v);
    }
  }

  protected onObjectSettingsDeviceIdChange(value: string | number | null): void {
    if (value == null || value === '') {
      this.objectSettingsDeviceId.set(null);
      return;
    }
    const n = typeof value === 'number' ? value : Number(value);
    if (Number.isFinite(n)) {
      this.objectSettingsDeviceId.set(Math.trunc(n));
    }
  }
  /** Перенос NODE/GROUP в группу / из группы (PUT после flush). */
  private readonly pendingMembershipByObjectId = new Map<number, TopologyPendingMembership>();
  /** Поле `status` устройства из ответа списка мониторинга при выборе привязки в настройках узла. */
  private readonly deviceStatusFromMonitoringPick = new Map<number, string>();
  /** Поле `healthStatus` из того же списка (NORM / WARN / CRITICAL). */
  private readonly deviceHealthFromMonitoringPick = new Map<number, MonitoringHealthStatus>();
  /** Blob URL фона слоя (GROUP) по id объекта в БД; синхронизировать с revoke при смене графа. */
  private readonly groupLayerBackgroundUrls = new Map<number, string>();
  /** Счётчик для пересчёта превью фона в панели настроек после загрузки URL в карту. */
  private readonly layerBgPreviewRev = signal(0);
  /** Подложка под canvas: корень топологии или родитель слоя (NODE/GROUP). */
  protected readonly layerHostBackdropBgUrl = signal<string | null>(null);
  private readonly layerHostBackdropFillHex = signal<string | null>(null);

  protected readonly layerSettingsDialogVisible = signal(false);
  protected readonly layerSettingsLoading = signal(false);
  protected readonly layerSettingsSaving = signal(false);
  protected readonly layerSettingsName = signal('');
  protected readonly layerSettingsBackdropHex = signal<string | null>(null);
  protected readonly layerSettingsLayerBgPresent = signal(false);
  protected readonly layerSettingsLayerPreviewUrl = signal<string | null>(null);
  protected readonly layerSettingsLayerBgUploading = signal(false);
  /** Загрузка картинки фона в диалоге «Настройки слоя» — только если текущий слой — группа (не корень и не NODE). */
  protected readonly layerSettingsAllowLayerBackgroundImage = signal(false);
  private layerSettingsInitialName: string | null | undefined;
  private layerSettingsInitialBackdrop: string | null | undefined;
  /** Диалог «Настройки слоя» для корневого уровня (не для объекта-слоя). */
  private layerSettingsEditingRoot = false;

  protected readonly layerHostBackdropFillCss = computed(() => {
    const c = this.layerHostBackdropFillHex();
    return c != null && c.length > 0 ? c : 'transparent';
  });
  /**
   * Прямоугольник фонового изображения слоя в пикселях относительно контейнера Cytoscape
   * (совпадает с {@code renderedBoundingBox} узлов/рёбер текущего уровня).
   */
  protected readonly layerHostBackdropImgLayout = signal<{
    left: number;
    top: number;
    width: number;
    height: number;
    objectFit: 'fill' | 'contain';
  } | null>(null);

  private layerBackdropSyncRaf: number | null = null;

  protected readonly layerSettingsLayerPreviewSrc = computed(() => {
    const local = this.layerSettingsLayerPreviewUrl();
    if (local) return local;
    return this.layerHostBackdropBgUrl();
  });
  /**
   * Без compound-parent в Cytoscape дочерние узлы не следуют за рамкой группы сами.
   * При grab GROUP запоминаем позиции невыбранных потомков и на drag сдвигаем их на тот же дельта, что и у группы
   * (выбранные потомки уже двигает Cytoscape; мультивыбор с «чужими» узлами не должен сбрасывать состояние).
   */
  private groupDragFollowState: {
    groupElId: string;
    origin: { x: number; y: number };
    descendantPos: Map<string, { x: number; y: number }>;
  } | null = null;
  private edgeDrawSuppressNextContextMenu = false;
  private edgeDrawSuppressResetTimer: ReturnType<typeof setTimeout> | null = null;
  private edgeDrawLongPressTimer: ReturnType<typeof setTimeout> | null = null;
  private edgeDrawLongPressSource: { sourceElId: string; sourceObjectId: number } | null = null;
  private edgeDrawPressReleaseListener: ((e: MouseEvent) => void) | null = null;
  private edgeDrawActiveState: { sourceElId: string; sourceObjectId: number } | null = null;
  private edgeDrawActiveListeners: {
    move: (e: MouseEvent) => void;
    up: (e: MouseEvent) => void;
  } | null = null;
  private readonly cyEdgeDrawStartHandler = (evt: EventObject): void => {
    this.onCyEdgeDrawStart(evt);
  };
  private readonly cyZoom = signal(1);
  /** Размытие области графа на время пересоздания / замены элементов Cytoscape. */
  protected readonly cyMountRefreshing = signal(false);

  protected readonly topologyList = signal<TopologyRecord[]>([]);
  protected readonly selectedTopologyId = signal<number | null>(null);
  protected readonly layerStack = signal<TopologyLayerEntry[]>([]);
  /** Модель p-autocomplete: выбранная топология с подписью (включая * для «по умолчанию»). */
  protected readonly topologyPick = signal<TopologySelectOption | null>(null);
  protected readonly topologyAutocompleteSuggestions = signal<TopologySelectOption[]>([]);
  protected readonly defaultTopologySaving = signal(false);
  protected readonly userDirectory = signal<UserDirectoryEntry[]>([]);
  protected readonly topologyEditOpen = signal(false);
  protected readonly topologyEditName = signal('');
  protected readonly topologyEditVisibility = signal<TopologyVisibility>('PRIVATE');
  protected readonly topologyEditSharedIds = signal<number[]>([]);
  protected readonly topologyEditOpenByDefault = signal(false);
  protected readonly topologyEditSaving = signal(false);

  protected readonly topologyVisibilityEditOptions = [
    { label: 'Только я', value: 'PRIVATE' as const },
    { label: 'Общий доступ (выбранные пользователи)', value: 'SHARED' as const },
  ];

  protected readonly currentUserId = computed(() => this.auth.authSession()?.userId ?? null);

  protected readonly canEditCurrentTopology = computed(() => {
    const id = this.selectedTopologyId();
    const row = id != null ? this.topologyList().find((r) => r.id === id) : undefined;
    const session = this.auth.authSession();
    if (!row || !session) {
      return false;
    }
    if (session.roles?.includes('ADMIN')) {
      return true;
    }
    const uid = session.userId;
    if (uid == null) {
      return false;
    }
    return row.ownerId === uid;
  });

  protected readonly topologyEditMultiSelectOptions = computed(() => {
    const uid = this.currentUserId();
    return this.userDirectory().filter((u) => u.id !== uid);
  });

  /** Текущая топология открыта только на просмотр (нет прав на изменение графа). */
  protected readonly topologyGraphReadOnly = computed(
    () => this.selectedTopologyId() != null && !this.canEditCurrentTopology(),
  );

  /** Есть несохранённые изменения графа (новые локальные узлы или перенос NODE). */
  protected readonly layoutDirty = signal(false);
  protected readonly layoutSaving = signal(false);

  protected readonly showSaveLayoutButton = computed(() => {
    const id = this.selectedTopologyId();
    if (id == null || !this.layoutDirty()) return false;
    const row = this.topologyList().find((r) => r.id === id);
    return row?.autosave !== true;
  });

  /** Диалог при смене топологии с несохранённым графом. */
  protected readonly topologySwitchDialogVisible = signal(false);
  /** Сценарий диалога: смена топологии или уход со страницы. */
  protected readonly pendingUnsavedChanges = signal<PendingUnsavedChangesDialog | null>(null);
  /** Разрешение `canDeactivate` после выбора в диалоге (только `kind: 'navigate'`). */
  private navigateAwayResolver: ((allowed: boolean) => void) | null = null;

  /** Контекстное меню Cytoscape (ПКМ). */
  protected readonly cyContextMenuModel = signal<MenuItem[]>([]);

  /** Панель настроек выбранного узла / ребра / группы (над холстом). */
  protected readonly objectSettingsVisible = signal(false);
  protected readonly objectSettingsSaving = signal(false);
  protected readonly objectSettingsName = signal('');
  protected readonly objectSettingsNodeKind = signal<TopologyNodeKind>('RACK');
  protected readonly objectSettingsDeviceId = signal<number | null>(null);
  protected readonly objectSettingsObjectKind = signal<TopologyCyObjectKind>('NODE');
  protected readonly objectSettingsElementId = signal<string | null>(null);
  protected readonly objectSettingsDbId = signal<number | null>(null);
  protected readonly objectSettingsPending = signal(false);
  protected readonly objectSettingsDeviceOptions = signal<{ label: string; value: number }[]>([]);
  /** Только GROUP: null — цвет рамки по умолчанию. */
  protected readonly objectSettingsGroupBorderColor = signal<string | null>(null);
  /** Только GROUP: есть ли сохранённый фон слоя. */
  protected readonly objectSettingsLayerBackgroundPresent = signal(false);
  /** Локальное превью выбранного файла (до/после загрузки объединяется с картой URL). */
  protected readonly objectSettingsLayerPreviewUrl = signal<string | null>(null);
  protected readonly objectSettingsLayerBgUploading = signal(false);

  /** Превью фона в панели настроек GROUP: локальный файл или уже загруженный blob URL. */
  protected readonly objectSettingsLayerPreviewSrc = computed(() => {
    this.layerBgPreviewRev();
    const local = this.objectSettingsLayerPreviewUrl();
    if (local) return local;
    const id = this.objectSettingsDbId();
    if (id == null) return null;
    return this.groupLayerBackgroundUrls.get(id) ?? null;
  });
  /** Только EDGE: null — цвет линии по умолчанию. */
  protected readonly objectSettingsEdgeLineColor = signal<string | null>(null);
  protected readonly topologyNodeKindSelectOptions = TOPOLOGY_NODE_KIND_MENU_OPTIONS;
  protected readonly canNavigateBackLevel = computed(() => this.layerStack().length > 0);
  protected readonly currentLayerLabel = computed(() => {
    const stack = this.layerStack();
    if (stack.length === 0) return 'Корневой уровень';
    return `Уровень: ${stack[stack.length - 1].label}`;
  });
  protected readonly cyDebugDomPath = signal(
    'app-root > app-main-workspace > main.dashboard nav-collapsed > section.content > app-topology-page > section.topology-page.content-card > div.topology-page__cy-host > div.topology-page__cy > div#__cytoscape_container > div > canvas[0]',
  );
  protected readonly cyDebugPosition = signal('top=0px, left=0px, width=0px, height=0px');
  protected readonly cyDebugHtmlElement = signal('<canvas data-id="layer0-selectbox"></canvas>');
  protected readonly cyZoomLabel = computed(() => this.cyZoom().toFixed(3));
  private objectSettingsInitialDeviceId: number | null | undefined;
  private objectSettingsInitialGroupBorderColor: string | null | undefined;
  private objectSettingsInitialEdgeLineColor: string | null | undefined;

  protected readonly topologyMenuItems = computed<MenuItem[]>(() => {
    const id = this.selectedTopologyId();
    const row = id != null ? this.topologyList().find((r) => r.id === id) : undefined;
    const autosaveOn = row?.autosave === true;
    const autoCenterOn = row?.autoCenterOnResize !== false;
    const def = this.auth.authSession()?.defaultTopologyId ?? null;
    const isDefault = id != null && def === id;
    const saving = this.defaultTopologySaving();
    const canEditTopo = this.canEditCurrentTopology();
    return [
      {
        label: 'Создать новую топологию',
        icon: 'pi pi-plus',
        command: () => this.onCreateTopology(),
      },
      {
        label: 'Добавить объект',
        icon: 'pi pi-plus-circle',
        disabled: id == null || !canEditTopo,
        command: () => this.onAddObject(),
      },
      {
        label: 'Редактировать',
        icon: 'pi pi-pencil',
        disabled: id == null || !canEditTopo,
        command: () => this.openTopologyEditDialog(),
      },
      {
        label: autosaveOn ? 'Автосохранение: включено' : 'Автосохранение: выключено',
        icon: autosaveOn ? 'pi pi-check' : 'pi pi-times',
        disabled: id == null || !canEditTopo,
        command: () => this.onToggleAutosave(),
      },
      {
        label: autoCenterOn ? 'Автоцентровка: включена' : 'Автоцентровка: выключена',
        icon: autoCenterOn ? 'pi pi-check' : 'pi pi-times',
        disabled: id == null || !canEditTopo,
        command: () => this.onToggleAutoCenterOnResize(),
      },
      {
        label: isDefault ? 'Снять «открывать по умолчанию»' : 'Открывать по умолчанию',
        icon: 'pi pi-star',
        disabled: id == null || saving,
        command: () => this.onToggleDefaultTopology(),
      },
      { separator: true },
      {
        label: 'Удалить топологию',
        icon: 'pi pi-trash',
        disabled: id == null || !canEditTopo,
        command: () => this.onDeleteTopology(),
      },
    ];
  });

  constructor() {
    this.unsubscribeNodeIconRasterReady = subscribeTopologyNodeIconRasterReady(() => {
      this.cy?.style().update();
    });

    afterNextRender(() => {
      if (!isPlatformBrowser(this.platformId)) return;
      this.refreshTopologyList();
      this.dashboardsApi.listUserDirectory().subscribe({
        next: (rows) => this.userDirectory.set(rows),
        error: () => this.userDirectory.set([]),
      });
    });

    this.destroyRef.onDestroy(() => {
      if (this.layerBackdropSyncRaf != null) {
        cancelAnimationFrame(this.layerBackdropSyncRaf);
        this.layerBackdropSyncRaf = null;
      }
      this.cancelAutosaveLayoutTimer();
      this.teardownGroupResize();
      this.teardownEdgeDraw();
      this.teardownCyResizeObserver();
      if (this.cyViewportSaveTimer != null) {
        clearTimeout(this.cyViewportSaveTimer);
        this.cyViewportSaveTimer = null;
      }
      this.revokeAllGroupLayerBackgroundUrls();
      this.clearLayerHostBackdrop();
      const p = this.objectSettingsLayerPreviewUrl();
      if (p) {
        URL.revokeObjectURL(p);
        this.objectSettingsLayerPreviewUrl.set(null);
      }
      const mount = this.cyMount()?.nativeElement;
      if (mount) {
        mount.removeEventListener('contextmenu', this.cyPreventNativeContextMenu);
      }
      this.cyMountRefreshing.set(false);
      this.cy?.destroy();
      this.cy = null;
      this.cyMenuAnchor?.remove();
      this.cyMenuAnchor = null;
      this.unsubscribeNodeIconRasterReady?.();
    });
  }

  protected openTopologyMenu(event: Event): void {
    this.topologyMenuRef()?.toggle(event);
  }

  protected openTopologyEditDialog(): void {
    const id = this.selectedTopologyId();
    const row = id != null ? this.topologyList().find((r) => r.id === id) : undefined;
    if (!row || !this.canEditCurrentTopology()) {
      return;
    }
    this.topologyEditName.set(row.name);
    this.topologyEditVisibility.set(row.visibility);
    this.topologyEditSharedIds.set([...(row.sharedUserIds ?? [])]);
    const def = this.auth.authSession()?.defaultTopologyId ?? null;
    this.topologyEditOpenByDefault.set(id != null && def === id);
    this.topologyEditOpen.set(true);
  }

  protected closeTopologyEditDialog(): void {
    if (!this.topologyEditSaving()) {
      this.topologyEditOpen.set(false);
    }
  }

  protected onTopologyEditVisibleChange(visible: boolean): void {
    if (visible) {
      this.topologyEditOpen.set(true);
    } else {
      this.closeTopologyEditDialog();
    }
  }

  protected onTopologyEditVisibilityChange(value: string | number | null): void {
    const v = value == null ? 'PRIVATE' : String(value);
    if (v === 'PRIVATE' || v === 'SHARED') {
      this.topologyEditVisibility.set(v);
    }
  }

  protected submitTopologyEdit(): void {
    const id = this.selectedTopologyId();
    const row = id != null ? this.topologyList().find((r) => r.id === id) : undefined;
    if (!row || id == null) {
      return;
    }
    const name = this.topologyEditName().trim();
    if (!name) {
      this.notifier.warn('Укажите имя топологии.');
      return;
    }
    const vis = this.topologyEditVisibility();
    const shared = vis === 'SHARED' ? [...this.topologyEditSharedIds()] : [];
    this.topologyEditSaving.set(true);
    this.topologyService
      .update(id, {
        name,
        visibility: vis,
        autosave: row.autosave,
        autoCenterOnResize: row.autoCenterOnResize ?? true,
        sharedUserIds: shared,
        document: row.document ?? {},
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.topologyList.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
          this.syncTopologyPickFromSelection();
          const wantDefault = this.topologyEditOpenByDefault();
          const session = this.auth.authSession();
          const curDef = session?.defaultTopologyId ?? null;
          const needPreferenceUpdate =
            (wantDefault && curDef !== id) || (!wantDefault && curDef === id);

          const finishOk = (): void => {
            this.topologyEditSaving.set(false);
            this.topologyEditOpen.set(false);
            this.notifier.success('Параметры топологии сохранены.');
          };

          if (!needPreferenceUpdate) {
            finishOk();
            return;
          }

          const shouldBeDefault = wantDefault ? id : null;
          this.auth
            .updateDefaultTopologyPreference(shouldBeDefault)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (r) => {
                if (session) {
                  this.auth.updateSession({ ...session, defaultTopologyId: r.defaultTopologyId });
                }
                this.syncTopologyPickFromSelection();
                finishOk();
              },
              error: () => {
                this.topologyEditSaving.set(false);
                this.topologyEditOpen.set(false);
                this.notifier.warn(
                  'Параметры топологии сохранены, но настройку «по умолчанию» обновить не удалось.',
                );
              },
            });
        },
        error: (e: { error?: { message?: string }; message?: string }) => {
          this.topologyEditSaving.set(false);
          this.notifier.error(e?.error?.message ?? e?.message ?? 'Не удалось сохранить изменения.');
        },
      });
  }

  /** Фильтрация подсказок при вводе и при открытии панели (completeOnFocus / dropdown). */
  protected filterTopologies(event: { query: string }): void {
    const q = (event.query ?? '').trim().toLowerCase();
    const all = this.topologyList();
    const selectedId = this.selectedTopologyId();
    let list = q.length > 0 ? all.filter((t) => t.name.toLowerCase().includes(q)) : [...all];
    if (q.length > 0 && selectedId != null) {
      const sel = all.find((t) => t.id === selectedId);
      if (sel && !list.some((t) => t.id === selectedId)) {
        list = [sel, ...list];
      }
    }
    this.topologyAutocompleteSuggestions.set(list.map((t) => this.toSelectOption(t)));
  }

  /** PrimeNG AutoComplete в ngModelChange отдаёт id (число), а не объект { label, value }. */
  protected onTopologyPickChange(value: TopologySelectOption | number | null | undefined): void {
    const newPick = this.normalizeTopologyPick(value);
    const newId = newPick?.value ?? null;
    const currentId = this.selectedTopologyId();

    if (newId === currentId) {
      this.topologyPick.set(newPick);
      return;
    }

    if (this.hasUnsavedLayoutChanges()) {
      this.pendingUnsavedChanges.set({ kind: 'topology', to: newPick });
      this.syncTopologyPickFromSelection();
      this.topologySwitchDialogVisible.set(true);
      return;
    }

    this.applyTopologySwitchImmediate(newPick);
  }

  protected onTopologySwitchDialogVisibleChange(visible: boolean): void {
    this.topologySwitchDialogVisible.set(visible);
    if (!visible) {
      const pending = this.pendingUnsavedChanges();
      if (pending?.kind === 'navigate' && this.navigateAwayResolver) {
        this.navigateAwayResolver(false);
        this.navigateAwayResolver = null;
      }
      if (pending !== null) {
        this.pendingUnsavedChanges.set(null);
        if (pending.kind === 'topology') {
          this.syncTopologyPickFromSelection();
        }
      }
    }
  }

  protected onTopologySwitchCancel(): void {
    this.topologySwitchDialogVisible.set(false);
  }

  protected onTopologySwitchDiscard(): void {
    const pending = this.pendingUnsavedChanges();
    if (!pending) return;
    if (pending.kind === 'navigate') {
      const r = this.navigateAwayResolver;
      this.navigateAwayResolver = null;
      this.pendingUnsavedChanges.set(null);
      this.topologySwitchDialogVisible.set(false);
      if (r) r(true);
      return;
    }
    this.applyTopologySwitch(pending.to);
  }

  protected async onTopologySwitchSave(): Promise<void> {
    const pending = this.pendingUnsavedChanges();
    if (!pending) return;
    const topologyId = this.selectedTopologyId();

    const finishNavigateLeave = (allowed: boolean): void => {
      const r = this.navigateAwayResolver;
      this.navigateAwayResolver = null;
      this.pendingUnsavedChanges.set(null);
      this.topologySwitchDialogVisible.set(false);
      if (r) r(allowed);
    };

    if (pending.kind === 'navigate') {
      if (topologyId == null) {
        finishNavigateLeave(true);
        return;
      }
      const pendingCreates = this.collectPendingCreatesFromGraph();
      const positionEntries = [...this.pendingPositionUpdates.entries()];
      const hasGroupLayout = this.pendingGroupLayoutUpdates.size > 0;
      const hasMembership = this.pendingMembershipByObjectId.size > 0;
      if (pendingCreates.length === 0 && positionEntries.length === 0 && !hasGroupLayout && !hasMembership) {
        finishNavigateLeave(true);
        return;
      }
      this.layoutSaving.set(true);
      const groupLayoutEntries = [...this.pendingGroupLayoutUpdates.entries()];
      const membershipSnapshot = new Map(this.pendingMembershipByObjectId);
      const ok = await this.runFlushLayout(
        topologyId,
        pendingCreates,
        positionEntries,
        groupLayoutEntries,
        membershipSnapshot,
        { silent: false },
      );
      if (ok) {
        finishNavigateLeave(true);
      }
      return;
    }

    const target = pending.to;
    if (topologyId == null) {
      this.applyTopologySwitch(target);
      return;
    }

    const pendingCreates = this.collectPendingCreatesFromGraph();
    const positionEntries = [...this.pendingPositionUpdates.entries()];
    const hasGroupLayout = this.pendingGroupLayoutUpdates.size > 0;
    const hasMembership = this.pendingMembershipByObjectId.size > 0;
    if (pendingCreates.length === 0 && positionEntries.length === 0 && !hasGroupLayout && !hasMembership) {
      this.applyTopologySwitch(target);
      return;
    }

    this.layoutSaving.set(true);
    const groupLayoutEntries = [...this.pendingGroupLayoutUpdates.entries()];
    const membershipSnapshot = new Map(this.pendingMembershipByObjectId);
    const ok = await this.runFlushLayout(
      topologyId,
      pendingCreates,
      positionEntries,
      groupLayoutEntries,
      membershipSnapshot,
      { silent: false },
    );
    if (ok) {
      this.applyTopologySwitch(target);
    }
  }

  /** Текст цели для подписи в диалоге (без префикса *). */
  protected pendingSwitchTargetLabel(): string {
    const p = this.pendingUnsavedChanges();
    if (!p || p.kind !== 'topology' || p.to == null) return '';
    return p.to.label.replace(/^\*\s+/, '');
  }

  /** Вызывается из `canDeactivate` при уходе с `/topology`. */
  confirmLeaveWhenUnsaved(nextUrl: string): Observable<boolean> {
    if (!isPlatformBrowser(this.platformId)) return of(true);
    if (!this.hasUnsavedLayoutChanges()) return of(true);
    return new Observable<boolean>((subscriber) => {
      if (this.navigateAwayResolver != null) {
        subscriber.next(false);
        subscriber.complete();
        return;
      }
      this.navigateAwayResolver = (allowed: boolean) => {
        this.navigateAwayResolver = null;
        subscriber.next(allowed);
        subscriber.complete();
      };
      this.pendingUnsavedChanges.set({ kind: 'navigate', url: nextUrl });
      this.topologySwitchDialogVisible.set(true);
      return () => {
        if (this.navigateAwayResolver != null) {
          this.navigateAwayResolver = null;
          this.pendingUnsavedChanges.set(null);
          this.topologySwitchDialogVisible.set(false);
        }
      };
    });
  }

  private hasUnsavedLayoutChanges(): boolean {
    if (!isPlatformBrowser(this.platformId)) return false;
    return (
      this.layoutDirty() ||
      this.pendingPositionUpdates.size > 0 ||
      this.pendingGroupLayoutUpdates.size > 0 ||
      this.pendingMembershipByObjectId.size > 0 ||
      this.collectPendingCreatesFromGraph().length > 0
    );
  }

  private applyTopologySwitchImmediate(newPick: TopologySelectOption | null): void {
    this.closeObjectSettings();
    this.topologyPick.set(newPick);
    this.cancelAutosaveLayoutTimer();
    this.resetLocalLayoutState();
    this.persistCyViewportNowForCurrentLayer();
    this.layerStack.set([]);
    this.selectedTopologyId.set(newPick?.value ?? null);
    this.rebuildGraph();
  }

  private applyTopologySwitch(newPick: TopologySelectOption | null): void {
    this.pendingUnsavedChanges.set(null);
    this.topologySwitchDialogVisible.set(false);
    this.applyTopologySwitchImmediate(newPick);
  }

  private currentLayerParentId(): number | null {
    const stack = this.layerStack();
    return stack.length === 0 ? null : stack[stack.length - 1].parentObjectId;
  }

  private normalizeTopologyPick(
    value: TopologySelectOption | number | null | undefined,
  ): TopologySelectOption | null {
    if (value == null) return null;
    if (typeof value === 'object' && typeof value.value === 'number') {
      return value as TopologySelectOption;
    }
    const id = typeof value === 'number' ? value : Number(value);
    if (!Number.isFinite(id)) return null;
    const row = this.topologyList().find((r) => r.id === id);
    return row ? this.toSelectOption(row) : null;
  }

  private toSelectOption(t: TopologyRecord): TopologySelectOption {
    const def = this.auth.authSession()?.defaultTopologyId ?? null;
    return {
      label: `${def === t.id ? '* ' : ''}${t.name}`,
      value: t.id,
    };
  }

  private syncTopologyPickFromSelection(): void {
    const id = this.selectedTopologyId();
    if (id == null) {
      this.topologyPick.set(null);
      return;
    }
    const row = this.topologyList().find((r) => r.id === id);
    this.topologyPick.set(row ? this.toSelectOption(row) : null);
  }

  private refreshTopologyList(): void {
    this.topologyService
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.topologyList.set(rows);
          const cur = this.selectedTopologyId();
          const def = this.auth.authSession()?.defaultTopologyId ?? null;
          if (cur != null && !rows.some((r) => r.id === cur)) {
            this.persistCyViewportNowForCurrentLayer();
            this.layerStack.set([]);
            this.selectedTopologyId.set(
              def != null && rows.some((r) => r.id === def) ? def : (rows[0]?.id ?? null),
            );
          } else if (cur == null && rows.length > 0) {
            this.persistCyViewportNowForCurrentLayer();
            this.layerStack.set([]);
            this.selectedTopologyId.set(
              def != null && rows.some((r) => r.id === def) ? def : rows[0].id,
            );
          }
          this.syncTopologyPickFromSelection();
          this.rebuildGraph();
        },
        error: () => this.notifier.error('Не удалось загрузить список топологий.'),
      });
  }

  private rebuildGraph(options?: { preserveViewport?: boolean }): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.closeObjectSettings();
    const mount = this.cyMount()?.nativeElement;
    if (!mount) return;
    const id = this.selectedTopologyId();
    if (id == null) {
      this.cancelAutosaveLayoutTimer();
      this.resetLocalLayoutState();
      this.revokeAllGroupLayerBackgroundUrls();
      this.clearLayerHostBackdrop();
      this.cyGraphContext = null;
      this.initCytoscape(mount, []);
      return;
    }
    const preserveVp = options?.preserveViewport === true;
    const fetchSeq = ++this.topologyGraphFetchSeq;
    const fetchLayerId = this.currentLayerParentId();
    this.topologyService
      .listObjects(id, fetchLayerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (objs) => {
          if (fetchSeq !== this.topologyGraphFetchSeq) return;
          if (this.selectedTopologyId() !== id || this.currentLayerParentId() !== fetchLayerId) return;
          const layerId = fetchLayerId;
          const elements = this.buildCyElements(objs);
          const canSoftReplace =
            this.cy != null &&
            this.cyGraphContext != null &&
            this.cyGraphContext.topologyId === id &&
            this.cyGraphContext.layerParentId === layerId;
          this.revokeAllGroupLayerBackgroundUrls();
          if (canSoftReplace) {
            this.replaceCyElementsInPlace(elements);
          } else {
            this.initCytoscape(mount, elements, { preserveViewport: preserveVp });
            this.cyGraphContext = { topologyId: id, layerParentId: layerId };
          }
          this.loadGroupLayerBackgroundBlobs(id, objs);
          this.refreshLayerHostBackdrop(id);
        },
        error: () => this.notifier.error('Не удалось загрузить объекты топологии.'),
      });
  }

  private revokeAllGroupLayerBackgroundUrls(): void {
    for (const u of this.groupLayerBackgroundUrls.values()) {
      URL.revokeObjectURL(u);
    }
    this.groupLayerBackgroundUrls.clear();
  }

  private revokeLayerHostBackdropBlobUrl(): void {
    const u = this.layerHostBackdropBgUrl();
    if (u) {
      URL.revokeObjectURL(u);
    }
    this.layerHostBackdropBgUrl.set(null);
    this.layerHostBackdropImgLayout.set(null);
  }

  private clearLayerHostBackdrop(): void {
    this.revokeLayerHostBackdropBlobUrl();
    this.layerHostBackdropFillHex.set(null);
  }

  /** Синхронизация рамки фонового изображения с координатами и масштабом графа (pan/zoom/drag). */
  private scheduleSyncLayerHostBackdropImage(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.layerBackdropSyncRaf != null) return;
    this.layerBackdropSyncRaf = requestAnimationFrame(() => {
      this.layerBackdropSyncRaf = null;
      this.syncLayerHostBackdropImageRect();
    });
  }

  private syncLayerHostBackdropImageRect(): void {
    const cy = this.cy;
    const url = this.layerHostBackdropBgUrl();
    if (!cy || !url) {
      this.layerHostBackdropImgLayout.set(null);
      return;
    }
    const cw = cy.width();
    const ch = cy.height();
    if (!(cw > 0) || !(ch > 0)) {
      this.layerHostBackdropImgLayout.set(null);
      return;
    }
    const eles = cy.elements().filter((ele) => ele.data('preview') !== '1' && ele.data('preview') !== 1);
    const bb = eles.renderedBoundingBox({
      includeNodes: true,
      includeEdges: true,
      includeLabels: true,
    });
    const container = cy.container() as HTMLElement | undefined;
    let ox = 0;
    let oy = 0;
    if (container) {
      const host = container.closest('.topology-page__cy');
      const backdrop = host?.querySelector('.topology-page__cy-layer-backdrop') as HTMLElement | null;
      if (backdrop) {
        const cr = container.getBoundingClientRect();
        const br = backdrop.getBoundingClientRect();
        ox = cr.left - br.left;
        oy = cr.top - br.top;
      }
    }
    const pad = 20;
    const hasGraph =
      Number.isFinite(bb.w) &&
      Number.isFinite(bb.h) &&
      bb.w > 2 &&
      bb.h > 2;
    if (hasGraph) {
      const left = bb.x1 - pad + ox;
      const top = bb.y1 - pad + oy;
      const width = bb.w + 2 * pad;
      const height = bb.h + 2 * pad;
      this.layerHostBackdropImgLayout.set({
        left,
        top,
        width: Math.max(width, 1),
        height: Math.max(height, 1),
        objectFit: 'fill',
      });
    } else {
      this.layerHostBackdropImgLayout.set({
        left: ox,
        top: oy,
        width: cw,
        height: ch,
        objectFit: 'contain',
      });
    }
    this.cdr.markForCheck();
  }

  private wireLayerHostBackdropImageSync(): void {
    const cy = this.cy;
    if (!cy) return;
    const onChange = (): void => this.scheduleSyncLayerHostBackdropImage();
    cy.on('viewport', onChange);
    cy.on('drag', 'node', onChange);
    cy.on('add remove', onChange);
  }

  protected onLayerHostBackdropImgLoad(): void {
    this.scheduleSyncLayerHostBackdropImage();
  }

  private refreshLayerHostBackdrop(topologyId: number): void {
    const seq = ++this.layerBackdropFetchSeq;
    const parentId = this.currentLayerParentId();
    this.revokeLayerHostBackdropBlobUrl();
    this.layerHostBackdropFillHex.set(null);
    if (parentId == null) {
      const row = this.topologyList().find((r) => r.id === topologyId);
      this.layerHostBackdropFillHex.set(
        this.normalizeGroupBorderHexOrNull(
          typeof row?.rootLayerBackdropColor === 'string' ? row.rootLayerBackdropColor : null,
        ),
      );
      this.scheduleSyncLayerHostBackdropImage();
      this.cdr.markForCheck();
      return;
    }
    this.topologyService
      .getObject(topologyId, parentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rec) => {
          if (seq !== this.layerBackdropFetchSeq) return;
          if (this.selectedTopologyId() !== topologyId || this.currentLayerParentId() !== parentId) return;
          this.layerHostBackdropFillHex.set(
            this.normalizeGroupBorderHexOrNull(
              typeof rec.layerBackdropColor === 'string' ? rec.layerBackdropColor : null,
            ),
          );
          if (rec.kind === 'GROUP' && rec.layerBackgroundPresent === true) {
            this.topologyService
              .getLayerBackground(topologyId, parentId)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe({
                next: (blob) => {
                  if (seq !== this.layerBackdropFetchSeq) return;
                  if (this.selectedTopologyId() !== topologyId || this.currentLayerParentId() !== parentId) {
                    return;
                  }
                  this.revokeLayerHostBackdropBlobUrl();
                  this.layerHostBackdropBgUrl.set(URL.createObjectURL(blob));
                  this.scheduleSyncLayerHostBackdropImage();
                  this.cdr.markForCheck();
                },
                error: () => {
                  this.notifier.warn('Не удалось загрузить изображение фона слоя.');
                },
              });
          }
          this.scheduleSyncLayerHostBackdropImage();
          this.cdr.markForCheck();
        },
        error: () => {
          this.notifier.warn('Не удалось загрузить подложку слоя.');
        },
      });
  }

  private loadGroupLayerBackgroundBlobs(topologyId: number, objs: TopologyObjectRecord[]): void {
    const groups = objs.filter((o) => o.kind === 'GROUP' && o.layerBackgroundPresent === true);
    if (groups.length === 0) return;
    forkJoin(
      groups.map((o) =>
        this.topologyService.getLayerBackground(topologyId, o.id).pipe(
          map((blob) => ({ id: o.id, blob } as const)),
          catchError(() => of(null)),
        ),
      ),
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          for (const row of results) {
            if (row == null) continue;
            const prev = this.groupLayerBackgroundUrls.get(row.id);
            if (prev) URL.revokeObjectURL(prev);
            this.groupLayerBackgroundUrls.set(row.id, URL.createObjectURL(row.blob));
          }
          this.layerBgPreviewRev.update((v) => v + 1);
          this.applyGroupLayerBackgroundStyles();
          this.cdr.markForCheck();
        },
        error: () => this.notifier.warn('Не удалось загрузить фон одного из слоёв.'),
      });
  }

  private applyGroupLayerBackgroundStyles(): void {
    this.cy?.style().update();
  }

  /**
   * После flush локальных «pending» созданий: без пересоздания Cytoscape проставляем id в БД и поля из ответа API.
   */
  private applySavedPendingCreatesToCy(records: TopologyObjectRecord[]): void {
    const cy = this.cy;
    if (!cy || records.length === 0) return;
    const run = (): void => {
      for (const rec of records) {
        const eid = rec.elementId?.trim();
        if (!eid) continue;
        if (!Number.isFinite(rec.id)) continue;
        const el = cy.getElementById(eid);
        if (el.empty()) continue;
        el.data('topologyObjectId', rec.id);
        el.data('pendingCreate', false);
        if (rec.kind === 'NODE') {
          if (rec.nodeKind != null) el.data('nodeKind', rec.nodeKind);
          if (rec.deviceId != null && Number.isFinite(rec.deviceId)) {
            el.data('deviceId', rec.deviceId);
          }
          if (rec.deviceHostAvailability != null) {
            el.data('deviceHostAvailability', rec.deviceHostAvailability);
          }
          if (rec.deviceHealthStatus != null) {
            el.data('deviceHealthStatus', rec.deviceHealthStatus);
          }
        }
        if (rec.kind === 'GROUP') {
          const w = rec.frameWidth ?? 280;
          const h = rec.frameHeight ?? 200;
          el.data('groupWidth', w);
          el.data('groupHeight', h);
          if (rec.frameBorderColor != null && rec.frameBorderColor.length > 0) {
            el.data('groupFrameBorderColor', rec.frameBorderColor);
          }
          if (rec.layerBackgroundPresent === true) {
            el.data('groupLayerBackgroundPresent', true);
          }
        }
      }
    };
    if (typeof cy.batch === 'function') {
      cy.batch(run);
    } else {
      run();
    }
    this.applyGroupLayerBackgroundStyles();
  }

  private groupLayerBgObjectUrlForEle(ele: { data: (k: string) => unknown }): string | undefined {
    const id = ele.data('topologyObjectId') as number | undefined;
    if (id == null || !Number.isFinite(id)) return undefined;
    return this.groupLayerBackgroundUrls.get(id);
  }

  private buildCyElements(objects: TopologyObjectRecord[]): ElementDefinition[] {
    const byDbId = new Map(objects.map((o) => [o.id, o]));
    const groupRows = objects.filter((o) => o.kind === 'GROUP');
    const nodeRows = objects.filter((o) => o.kind === 'NODE');
    const edges = objects.filter((o) => o.kind === 'EDGE');

    const nestMemo = new Map<number, number>();
    const sortedGroups = this.sortGroupsForCompound(groupRows);
    const groupEls: ElementDefinition[] = sortedGroups.map((o) => {
      const w = o.frameWidth ?? 280;
      const h = o.frameHeight ?? 200;
      const cx = o.positionX ?? 200;
      const cy = o.positionY ?? 200;
      const membershipEl =
        o.groupId != null && byDbId.get(o.groupId)?.kind === 'GROUP'
          ? (byDbId.get(o.groupId)!.elementId as string)
          : undefined;
      const nestDepth = this.computeGroupNestDepth(o, byDbId, nestMemo);
      const label = o.name?.trim() || o.elementId;
      return {
        group: 'nodes',
        data: {
          id: o.elementId,
          label,
          kind: 'GROUP',
          topologyObjectId: o.id,
          layerId: o.layerId ?? undefined,
          pendingCreate: false,
          groupWidth: w,
          groupHeight: h,
          groupNestDepth: nestDepth,
          ...(o.frameBorderColor != null && o.frameBorderColor.length > 0
            ? { groupFrameBorderColor: o.frameBorderColor }
            : {}),
          ...(o.layerBackgroundPresent === true ? { groupLayerBackgroundPresent: true } : {}),
          ...(membershipEl ? { membershipGroupElId: membershipEl } : {}),
        },
        position: { x: cx, y: cy },
      };
    });

    let fallbackI = 0;
    const nodeEls: ElementDefinition[] = nodeRows.map((o) => {
      const membershipEl =
        o.groupId != null && byDbId.get(o.groupId)?.kind === 'GROUP'
          ? (byDbId.get(o.groupId)!.elementId as string)
          : undefined;
      const x = o.positionX ?? 80 + (fallbackI % 6) * 90;
      const y = o.positionY ?? 80 + Math.floor(fallbackI / 6) * 90;
      fallbackI++;
      const label = o.name?.trim() || o.elementId;
      return {
        group: 'nodes',
        data: {
          id: o.elementId,
          label,
          kind: 'NODE',
          topologyObjectId: o.id,
          layerId: o.layerId ?? undefined,
          pendingCreate: false,
          nodeKind: o.nodeKind ?? 'RACK',
          ...(o.deviceId != null && Number.isFinite(o.deviceId)
            ? {
                deviceId: o.deviceId,
                deviceHostAvailability: o.deviceHostAvailability ?? 'UNKNOWN',
                ...(o.deviceHealthStatus != null ? { deviceHealthStatus: o.deviceHealthStatus } : {}),
              }
            : {}),
          ...(membershipEl ? { membershipGroupElId: membershipEl } : {}),
        },
        position: { x, y },
      };
    });

    const edgeEls: ElementDefinition[] = edges
      .filter((e) => e.sourceElementId && e.targetElementId)
      .map((e) => ({
        group: 'edges',
        data: {
          id: e.elementId,
          source: e.sourceElementId as string,
          target: e.targetElementId as string,
          kind: 'EDGE',
          topologyObjectId: e.id,
          layerId: e.layerId ?? undefined,
          label: e.name?.trim() ?? '',
          ...(e.lineColor != null && e.lineColor.length > 0 ? { edgeLineColor: e.lineColor } : {}),
        },
      }));
    return [...groupEls, ...nodeEls, ...edgeEls];
  }

  /** Вложенные группы — после родительских (стабильный порядок; z-index по глубине). */
  private sortGroupsForCompound(groups: TopologyObjectRecord[]): TopologyObjectRecord[] {
    const byId = new Map(groups.map((g) => [g.id, g]));
    const result: TopologyObjectRecord[] = [];
    const stack = new Set<number>();

    const visit = (g: TopologyObjectRecord): void => {
      if (result.some((r) => r.id === g.id)) return;
      if (stack.has(g.id)) return;
      stack.add(g.id);
      const pid = g.groupId;
      if (pid != null) {
        const p = byId.get(pid);
        if (p) visit(p);
      }
      stack.delete(g.id);
      result.push(g);
    };

    for (const g of groups) visit(g);
    return result;
  }

  private computeGroupNestDepth(
    g: TopologyObjectRecord,
    byId: Map<number, TopologyObjectRecord>,
    memo: Map<number, number>,
  ): number {
    if (memo.has(g.id)) return memo.get(g.id)!;
    if (g.groupId == null) {
      memo.set(g.id, 0);
      return 0;
    }
    const p = byId.get(g.groupId);
    if (!p || p.kind !== 'GROUP') {
      memo.set(g.id, 0);
      return 0;
    }
    const d = 1 + this.computeGroupNestDepth(p, byId, memo);
    memo.set(g.id, d);
    return d;
  }

  private beginCyMountRefreshVisual(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.cyMountRefreshing.set(true);
  }

  /** Снимает blur после отрисовки кадра с уже выставленным viewport. */
  private endCyMountRefreshVisualAfterPaint(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        this.cyMountRefreshing.set(false);
      });
    });
  }

  /**
   * Выбранная топология доступна только для просмотра (не владелец и не ADMIN):
   * граф без перетаскивания узлов, без правок из контекстного меню.
   */
  private isTopologyGraphReadOnly(): boolean {
    return this.topologyGraphReadOnly();
  }

  private initCytoscape(
    container: HTMLElement,
    elements: ElementDefinition[],
    options?: { preserveViewport?: boolean },
  ): void {
    this.beginCyMountRefreshVisual();
    let restoreViewport: { zoom: number; pan: { x: number; y: number } } | undefined;
    if (options?.preserveViewport === true) {
      const cur = this.cy;
      if (cur != null) {
        const p = cur.pan();
        restoreViewport = { zoom: cur.zoom(), pan: { x: p.x, y: p.y } };
      }
    }
    this.teardownGroupResize();
    this.teardownEdgeDraw();
    this.groupDragFollowState = null;
    this.cy?.destroy();
    container.removeEventListener('contextmenu', this.cyPreventNativeContextMenu);
    container.addEventListener('contextmenu', this.cyPreventNativeContextMenu);
    const cyReadOnly = this.isTopologyGraphReadOnly();
    this.cy = cytoscape({
      container,
      elements,
      autoungrabify: cyReadOnly,
      style: [
        {
          selector: 'node, edge',
          style: {
            // По умолчанию рёбра всегда под узлами; вручную задаём порядок: GROUP < EDGE < NODE,
            // чтобы линии были видны поверх фона группы, а узлы — поверх линий.
            'z-index-compare': 'manual',
          },
        },
        {
          selector: 'node[kind = "GROUP"]',
          style: {
            shape: 'roundrectangle',
            'background-color': '#ffffff',
            'background-image': (ele: { data: (k: string) => unknown }) => {
              const u = this.groupLayerBgObjectUrlForEle(ele);
              return u != null && u.length > 0 ? u : 'none';
            },
            'background-fit': 'contain',
            'background-position-x': '50%',
            'background-position-y': '50%',
            'background-opacity': (ele: { data: (k: string) => unknown }) =>
              this.groupLayerBgObjectUrlForEle(ele) != null ? 1 : 0,
            'border-color': (ele: { data: (k: string) => unknown }) =>
              (ele.data('groupFrameBorderColor') as string) || TOPOLOGY_GROUP_DEFAULT_BORDER,
            'border-width': 3,
            'border-style': 'solid',
            label: 'data(label)',
            'text-valign': 'top',
            'text-halign': 'center',
            'text-margin-y': -4,
            'font-size': '12px',
            color: '#475569',
            width: (ele: { data: (k: string) => unknown }) => Number(ele.data('groupWidth')) || 280,
            height: (ele: { data: (k: string) => unknown }) => Number(ele.data('groupHeight')) || 200,
            padding: '18px',
            'z-index': (ele: { data: (k: string) => unknown }) =>
              10 + (Number(ele.data('groupNestDepth')) || 0) * 5,
          },
        },
        {
          selector: 'node[kind = "NODE"]',
          style: {
            shape: 'roundrectangle',
            'z-index': 10000,
            label: 'data(label)',
            'text-valign': 'bottom',
            'text-margin-y': 6,
            'font-size': '12px',
            color: '#334155',
            'background-color': (ele: { data: (k: string) => unknown }) =>
              topologyLinkedNodeChrome(ele).backgroundColor,
            'background-image': (ele: { data: (k: string) => unknown }) =>
              topologyNodeIconBackgroundStyle(ele).backgroundImage,
            // `contain` — Cytoscape масштабирует фон через внутреннюю canvas-логику без
            // округлений px/%, поэтому иконка не скачет при прокрутке колёсиком.
            // Пропорции заданы в viewBox SVG (33×33 и 66×66) — дрейфа нет, т.к. position статична.
            'background-fit': 'contain',
            'background-position-x': '50%',
            'background-position-y': '50%',
            width: 66,
            height: 66,
            'border-width': 2,
            'border-color': (ele: { data: (k: string) => unknown }) =>
              topologyLinkedNodeChrome(ele).borderColor,
          },
        },
        {
          selector: 'edge',
          style: {
            'z-index': 5000,
            width: 2,
            'line-color': (ele: { data: (k: string) => unknown }) => topologyCyEdgeStrokeColor(ele),
            'target-arrow-color': (ele: { data: (k: string) => unknown }) => topologyCyEdgeStrokeColor(ele),
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            label: 'data(label)',
            'font-size': '11px',
            color: (ele: { data: (k: string) => unknown }) => topologyCyEdgeStrokeColor(ele),
            'text-background-color': '#f8fafc',
            'text-background-opacity': 0.92,
            'text-background-padding': '3px',
            // Шире зона попадания для ПКМ / тапа (линия остаётся визуально тонкой).
            'overlay-padding': 12,
            'overlay-opacity': 0,
          },
        },
        {
          selector: 'edge[preview = "1"]',
          style: {
            'z-index': 5000,
            width: 2,
            'line-color': '#64748b',
            'line-style': 'dashed',
            'target-arrow-shape': 'none',
            opacity: 0.9,
            label: '',
            events: 'no',
          },
        },
        {
          selector: 'node[preview = "1"]',
          style: {
            width: 2,
            height: 2,
            label: '',
            opacity: 0,
            'background-opacity': 0,
            'border-width': 0,
            events: 'no',
          },
        },
        {
          selector: 'node[kind = "NODE"]:selected',
          style: {
            'border-color': '#2563eb',
            'border-width': 3,
            'background-color': '#eff6ff',
          },
        },
        {
          selector: 'node[kind = "GROUP"]:selected',
          style: {
            'border-color': '#2563eb',
          },
        },
      ],
      layout: elements.length > 0 ? { name: 'preset' } : { name: 'grid', rows: 1, cols: 1 },
      minZoom: TOPOLOGY_CY_MIN_ZOOM,
      maxZoom: TOPOLOGY_CY_MAX_ZOOM,
      wheelSensitivity: 0.35,
    });
    // mousedown resize должен вешаться до прочих обработчиков, чтобы совпасть с autoungrabify.
    if (!cyReadOnly) {
      this.wireGroupResizeHandles();
      this.wireEdgeDrawGesture();
      this.wireCytoscapeLayoutEvents();
    }
    this.wireCyLevelNavigationEvents();
    this.wireCyZoomDebug();
    this.wireCyViewportPersistence();
    this.wireCyContextMenu();
    this.wireLayerHostBackdropImageSync();
    this.ensureCyResizeObserver(container);
    queueMicrotask(() =>
      this.scheduleCyContainerResizeAndMaybeFit({
        ...(restoreViewport != null ? { restoreViewport } : {}),
        source: 'init',
        completeRefreshVisual: true,
      }),
    );
  }

  private cyViewportStorageKey(topologyId: number, layerParentId: number | null): string {
    return `${TOPOLOGY_VIEWPORT_STORAGE_PREFIX}:${topologyId}:${layerParentId ?? 'root'}`;
  }

  private readCyViewportFromStorage(
    topologyId: number,
    layerParentId: number | null,
  ): { zoom: number; pan: { x: number; y: number } } | null {
    if (!isPlatformBrowser(this.platformId) || typeof localStorage === 'undefined') return null;
    try {
      const raw = localStorage.getItem(this.cyViewportStorageKey(topologyId, layerParentId));
      if (raw == null || raw.length === 0) return null;
      const o = JSON.parse(raw) as { zoom?: unknown; pan?: { x?: unknown; y?: unknown } };
      if (typeof o.zoom !== 'number' || !Number.isFinite(o.zoom)) return null;
      const px = o.pan?.x;
      const py = o.pan?.y;
      if (typeof px !== 'number' || typeof py !== 'number' || !Number.isFinite(px) || !Number.isFinite(py)) {
        return null;
      }
      const z = Math.min(TOPOLOGY_CY_MAX_ZOOM, Math.max(TOPOLOGY_CY_MIN_ZOOM, o.zoom));
      return { zoom: z, pan: { x: px, y: py } };
    } catch {
      return null;
    }
  }

  private writeCyViewportToStorage(
    topologyId: number,
    layerParentId: number | null,
    zoom: number,
    pan: { x: number; y: number },
  ): void {
    if (!isPlatformBrowser(this.platformId) || typeof localStorage === 'undefined') return;
    try {
      const z = Math.min(TOPOLOGY_CY_MAX_ZOOM, Math.max(TOPOLOGY_CY_MIN_ZOOM, zoom));
      localStorage.setItem(
        this.cyViewportStorageKey(topologyId, layerParentId),
        JSON.stringify({ zoom: z, pan: { x: pan.x, y: pan.y } }),
      );
    } catch {
      /* квота / приватный режим */
    }
  }

  private flushPendingCyViewportSaveTimer(): void {
    if (this.cyViewportSaveTimer != null) {
      clearTimeout(this.cyViewportSaveTimer);
      this.cyViewportSaveTimer = null;
    }
  }

  /**
   * Сохранить pan/zoom под ключ текущего слоя до смены `layerStack` / топологии: иначе debounce
   * или отложенный `viewport` запишет координаты уже «нового» layerId при старом экземпляре cy.
   */
  private persistCyViewportNowForCurrentLayer(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    this.flushPendingCyViewportSaveTimer();
    const cy = this.cy;
    const tid = this.selectedTopologyId();
    if (!cy || tid == null) return;
    const lid = this.currentLayerParentId();
    this.writeCyViewportToStorage(tid, lid, cy.zoom(), cy.pan());
  }

  private schedulePersistCyViewportToStorage(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const cy = this.cy;
    const tid = this.selectedTopologyId();
    if (!cy || tid == null) return;
    const lid = this.currentLayerParentId();
    if (this.cyViewportSaveTimer != null) {
      clearTimeout(this.cyViewportSaveTimer);
    }
    this.cyViewportSaveTimer = setTimeout(() => {
      this.cyViewportSaveTimer = null;
      const cy2 = this.cy;
      if (!cy2) return;
      if (this.selectedTopologyId() !== tid || this.currentLayerParentId() !== lid) return;
      this.writeCyViewportToStorage(tid, lid, cy2.zoom(), cy2.pan());
    }, 400);
  }

  private wireCyViewportPersistence(): void {
    const cy = this.cy;
    if (!cy || !isPlatformBrowser(this.platformId)) return;
    cy.on('viewport', () => {
      if (this.suppressNextCyViewportPersist) return;
      if (this.selectedTopologyId() == null) return;
      this.schedulePersistCyViewportToStorage();
    });
  }

  /**
   * Тот же экземпляр Cytoscape: только замена элементов (без белого «мигания» canvas).
   */
  private replaceCyElementsInPlace(elements: ElementDefinition[]): void {
    const cy = this.cy;
    if (!cy) return;
    this.beginCyMountRefreshVisual();
    const z = cy.zoom();
    const p = cy.pan();
    this.suppressNextCyViewportPersist = true;
    const run = (): void => {
      cy.elements().remove();
      cy.add(elements);
    };
    if (typeof cy.batch === 'function') {
      cy.batch(run);
    } else {
      run();
    }
    cy.layout({ name: 'preset', animate: false }).run();
    this.layerBgPreviewRev.update((v) => v + 1);
    this.applyGroupLayerBackgroundStyles();
    queueMicrotask(() =>
      this.scheduleCyContainerResizeAndMaybeFit({
        restoreViewport: { zoom: z, pan: { x: p.x, y: p.y } },
        source: 'init',
        completeRefreshVisual: true,
      }),
    );
  }

  /**
   * resize + восстановление вида: снимок из памяти → localStorage (только init) → fit при автоцентровке.
   * Для ResizeObserver: только fit при автоцентровке (не подменяем вид сохранённым масштабом).
   */
  private scheduleCyContainerResizeAndMaybeFit(opts?: {
    restoreViewport?: { zoom: number; pan: { x: number; y: number } };
    /** init — учитываем localStorage; resize — только подгонка под контейнер. */
    source?: 'init' | 'resize';
    /** После resize/viewport снять blur с монтирования графа. */
    completeRefreshVisual?: boolean;
  }): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const source = opts?.source ?? 'init';
    if (opts?.restoreViewport != null && this.cyResizeAndFitRaf != null) {
      cancelAnimationFrame(this.cyResizeAndFitRaf);
      this.cyResizeAndFitRaf = null;
    }
    if (this.cyResizeAndFitRaf != null) return;
    const restore = opts?.restoreViewport;
    const completeRefreshVisual = opts?.completeRefreshVisual === true;
    this.cyResizeAndFitRaf = requestAnimationFrame(() => {
      this.cyResizeAndFitRaf = null;
      const cy = this.cy;
      if (!cy) {
        if (completeRefreshVisual) {
          this.cyMountRefreshing.set(false);
        }
        return;
      }
      this.suppressNextCyViewportPersist = true;
      cy.resize();
      let applied = false;
      if (restore != null) {
        cy.zoom(restore.zoom);
        cy.pan(restore.pan);
        applied = true;
      } else if (source === 'init') {
        const tid = this.selectedTopologyId();
        if (tid != null) {
          const stored = this.readCyViewportFromStorage(tid, this.currentLayerParentId());
          if (stored != null) {
            cy.zoom(stored.zoom);
            cy.pan(stored.pan);
            applied = true;
          }
        }
      }
      if (!applied && this.topologyAutoCenterOnResizeEnabled()) {
        cy.fit(undefined, 48);
      }
      this.updateCyDebugInfo();
      this.scheduleSyncLayerHostBackdropImage();
      if (completeRefreshVisual) {
        this.endCyMountRefreshVisualAfterPaint();
      }
      requestAnimationFrame(() => {
        this.suppressNextCyViewportPersist = false;
      });
    });
  }

  private topologyAutoCenterOnResizeEnabled(): boolean {
    const tid = this.selectedTopologyId();
    if (tid == null) return true;
    const row = this.topologyList().find((r) => r.id === tid);
    return row?.autoCenterOnResize !== false;
  }

  private ensureCyResizeObserver(container: HTMLElement): void {
    if (!isPlatformBrowser(this.platformId) || typeof ResizeObserver === 'undefined') return;
    if (this.cyResizeObserver) return;
    this.cyResizeObserver = new ResizeObserver(() => {
      this.scheduleCyContainerResizeAndMaybeFit({ source: 'resize' });
    });
    this.cyResizeObserver.observe(container);
  }

  private wireCyZoomDebug(): void {
    const cy = this.cy;
    if (!cy) return;
    cy.on('zoom', () => this.updateCyDebugInfo());
  }

  private updateCyDebugInfo(): void {
    const cy = this.cy;
    const container = cy?.container();
    if (!cy || !container) return;
    this.cyZoom.set(cy.zoom());

    const canvas = container.querySelector('canvas[data-id="layer0-selectbox"]') as
      | HTMLCanvasElement
      | null;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    this.cyDebugPosition.set(
      `top=${Math.round(rect.top)}px, left=${Math.round(rect.left)}px, width=${Math.round(rect.width)}px, height=${Math.round(rect.height)}px`,
    );
    this.cyDebugHtmlElement.set(canvas.outerHTML);
    this.cdr.markForCheck();
  }

  private teardownCyResizeObserver(): void {
    if (this.cyResizeAndFitRaf != null) {
      cancelAnimationFrame(this.cyResizeAndFitRaf);
      this.cyResizeAndFitRaf = null;
    }
    this.cyResizeObserver?.disconnect();
    this.cyResizeObserver = null;
  }

  private teardownGroupResize(): void {
    if (this.groupResizeListeners) {
      window.removeEventListener('mousemove', this.groupResizeListeners.move);
      window.removeEventListener('mouseup', this.groupResizeListeners.up);
      this.groupResizeListeners = null;
    }
    this.groupResizeCursorLeaveCleanup?.();
    this.groupResizeCursorLeaveCleanup = null;
    this.groupResizeState = null;
    this.lastAppliedGroupResizeCursor = null;
    this.setCyAutoungrabify(false);
    this.resetCyContainerCursor();
  }

  private wireEdgeDrawGesture(): void {
    const cy = this.cy;
    if (!cy) return;
    cy.on('cxttapstart', 'node', this.cyEdgeDrawStartHandler);
  }

  private teardownEdgeDraw(): void {
    const cy = this.cy;
    if (cy) {
      cy.off('cxttapstart', 'node', this.cyEdgeDrawStartHandler);
    }
    this.clearEdgeDrawLongPress();
    if (this.edgeDrawActiveListeners) {
      window.removeEventListener('mousemove', this.edgeDrawActiveListeners.move);
      window.removeEventListener('mouseup', this.edgeDrawActiveListeners.up);
      this.edgeDrawActiveListeners = null;
    }
    this.edgeDrawActiveState = null;
    this.removeEdgeDrawPreview();
    if (this.edgeDrawSuppressResetTimer != null) {
      clearTimeout(this.edgeDrawSuppressResetTimer);
      this.edgeDrawSuppressResetTimer = null;
    }
    this.edgeDrawSuppressNextContextMenu = false;
    if (this.groupResizeState == null) {
      this.setCyAutoungrabify(false);
    }
  }

  private onCyEdgeDrawStart(evt: EventObject): void {
    const cy = this.cy;
    if (
      !cy ||
      this.isTopologyGraphReadOnly() ||
      this.layoutSaving() ||
      this.groupResizeState != null ||
      this.edgeDrawActiveState != null
    ) {
      return;
    }
    const t = evt.target as {
      isNode?: () => boolean;
      id?: () => string;
      data?: (k: string) => unknown;
    };
    if (!t.isNode?.() || !t.id || !t.data) return;
    const kind = t.data('kind');
    if (kind !== 'NODE' && kind !== 'GROUP') return;
    if (t.data('pendingCreate') === true) return;
    const sourceObjectId = t.data('topologyObjectId');
    if (typeof sourceObjectId !== 'number' || !Number.isFinite(sourceObjectId)) return;
    this.clearEdgeDrawLongPress();
    const sourceElId = t.id();
    this.edgeDrawLongPressSource = { sourceElId, sourceObjectId };
    this.edgeDrawLongPressTimer = setTimeout(() => {
      this.beginEdgeDrawGesture();
    }, EDGE_DRAW_LONG_PRESS_MS);
    const onEarlyRelease = (e: MouseEvent): void => {
      if (e.button !== 2) return;
      this.clearEdgeDrawLongPress();
    };
    this.edgeDrawPressReleaseListener = onEarlyRelease;
    window.addEventListener('mouseup', onEarlyRelease);
  }

  private clearEdgeDrawLongPress(): void {
    if (this.edgeDrawLongPressTimer != null) {
      clearTimeout(this.edgeDrawLongPressTimer);
      this.edgeDrawLongPressTimer = null;
    }
    if (this.edgeDrawPressReleaseListener) {
      window.removeEventListener('mouseup', this.edgeDrawPressReleaseListener);
      this.edgeDrawPressReleaseListener = null;
    }
    this.edgeDrawLongPressSource = null;
  }

  private beginEdgeDrawGesture(): void {
    const cy = this.cy;
    const source = this.edgeDrawLongPressSource;
    this.clearEdgeDrawLongPress();
    if (!cy || !source) return;
    const sourceNode = cy.getElementById(source.sourceElId);
    if (sourceNode.empty() || !sourceNode.isNode()) return;
    this.edgeDrawSuppressNextContextMenu = true;
    this.edgeDrawActiveState = source;
    this.setCyAutoungrabify(true);
    this.addEdgeDrawPreview(source.sourceElId);
    const sourcePos = sourceNode.position();
    this.updateEdgeDrawPreviewTarget(sourcePos.x, sourcePos.y);
    const onMove = (e: MouseEvent): void => {
      const cyNow = this.cy;
      if (!cyNow || !this.edgeDrawActiveState) return;
      const pos = this.clientToModel(cyNow, e.clientX, e.clientY);
      this.updateEdgeDrawPreviewTarget(pos.x, pos.y);
    };
    const onUp = (e: MouseEvent): void => {
      if (e.button !== 2) return;
      const st = this.edgeDrawActiveState;
      this.finishEdgeDrawGesture();
      if (!st) return;
      const target = this.findEdgeDrawTargetByClient(e.clientX, e.clientY, st.sourceElId);
      if (!target) return;
      const targetObjectId = target.data('topologyObjectId');
      if (typeof targetObjectId !== 'number' || !Number.isFinite(targetObjectId)) return;
      const topologyId = this.selectedTopologyId();
      if (topologyId == null) return;
      const layerPid = this.currentLayerParentId();
      this.topologyService
        .createObject(topologyId, {
          kind: 'EDGE',
          sourceObjectId: st.sourceObjectId,
          targetObjectId,
          ...(layerPid != null ? { layerId: layerPid } : {}),
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.notifier.success('Связь создана.');
            this.rebuildGraph({ preserveViewport: true });
          },
          error: () => this.notifier.error('Не удалось создать связь.'),
        });
    };
    this.edgeDrawActiveListeners = { move: onMove, up: onUp };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }

  private finishEdgeDrawGesture(): void {
    if (this.edgeDrawActiveListeners) {
      window.removeEventListener('mousemove', this.edgeDrawActiveListeners.move);
      window.removeEventListener('mouseup', this.edgeDrawActiveListeners.up);
      this.edgeDrawActiveListeners = null;
    }
    this.edgeDrawActiveState = null;
    this.removeEdgeDrawPreview();
    if (this.edgeDrawSuppressResetTimer != null) {
      clearTimeout(this.edgeDrawSuppressResetTimer);
    }
    this.edgeDrawSuppressResetTimer = setTimeout(() => {
      this.edgeDrawSuppressNextContextMenu = false;
      this.edgeDrawSuppressResetTimer = null;
    }, 800);
    if (this.groupResizeState == null) {
      this.setCyAutoungrabify(false);
    }
  }

  private addEdgeDrawPreview(sourceElId: string): void {
    const cy = this.cy;
    if (!cy) return;
    this.removeEdgeDrawPreview();
    const src = cy.getElementById(sourceElId);
    if (src.empty() || !src.isNode()) return;
    const pos = src.position();
    cy.add([
      {
        group: 'nodes',
        data: {
          id: EDGE_DRAW_PREVIEW_NODE_ID,
          preview: '1',
          kind: 'PREVIEW',
        },
        position: { x: pos.x, y: pos.y },
      },
      {
        group: 'edges',
        data: {
          id: EDGE_DRAW_PREVIEW_EDGE_ID,
          source: sourceElId,
          target: EDGE_DRAW_PREVIEW_NODE_ID,
          preview: '1',
        },
      },
    ]);
  }

  private updateEdgeDrawPreviewTarget(modelX: number, modelY: number): void {
    const cy = this.cy;
    if (!cy) return;
    const n = cy.getElementById(EDGE_DRAW_PREVIEW_NODE_ID);
    if (!n.empty() && n.isNode()) {
      n.position({ x: modelX, y: modelY });
    }
  }

  private removeEdgeDrawPreview(): void {
    const cy = this.cy;
    if (!cy) return;
    const edge = cy.getElementById(EDGE_DRAW_PREVIEW_EDGE_ID);
    if (!edge.empty()) edge.remove();
    const node = cy.getElementById(EDGE_DRAW_PREVIEW_NODE_ID);
    if (!node.empty()) node.remove();
  }

  private findEdgeDrawTargetByClient(clientX: number, clientY: number, sourceElId: string): NodeSingular | null {
    const cy = this.cy;
    if (!cy) return null;
    const rect = cy.container()?.getBoundingClientRect();
    if (!rect) return null;
    const rx = clientX - rect.left;
    const ry = clientY - rect.top;
    const candidates: { node: NodeSingular; area: number }[] = [];
    cy.nodes('node[kind = "NODE"], node[kind = "GROUP"]').forEach((ele) => {
      if (!ele.isNode()) return;
      const n = ele as NodeSingular;
      if (n.id() === sourceElId) return;
      if (n.data('pendingCreate') === true) return;
      const dbId = n.data('topologyObjectId');
      if (typeof dbId !== 'number' || !Number.isFinite(dbId)) return;
      const bb = n.renderedBoundingBox({ includeLabels: false });
      if (rx < bb.x1 || rx > bb.x2 || ry < bb.y1 || ry > bb.y2) return;
      const area = Math.max(0, bb.w) * Math.max(0, bb.h);
      candidates.push({ node: n, area });
    });
    if (candidates.length === 0) return null;
    candidates.sort((a, b) => a.area - b.area);
    return candidates[0].node;
  }

  /** Снимает встроенный drag Cytoscape на время ручного resize рамки группы. */
  private setCyAutoungrabify(on: boolean): void {
    const cy = this.cy as (Core & { autoungrabify?: (v: boolean) => void }) | null | undefined;
    cy?.autoungrabify?.(on);
  }

  private resetCyContainerCursor(): void {
    const c = this.cy?.container();
    if (!c) return;
    c.style.removeProperty('cursor');
    requestAnimationFrame(() => {
      this.cy?.container()?.style.removeProperty('cursor');
    });
  }

  /** Координаты указателя относительно контейнера Cytoscape (rendered), в т.ч. когда target — дочерний узел. */
  private cyRenderedPointerFromEvent(evt: EventObject, cy: Core): { x: number; y: number } | null {
    const rp = (evt as { renderedPosition?: { x: number; y: number } }).renderedPosition;
    if (
      rp &&
      typeof rp.x === 'number' &&
      typeof rp.y === 'number' &&
      !Number.isNaN(rp.x) &&
      !Number.isNaN(rp.y)
    ) {
      return { x: rp.x, y: rp.y };
    }
    const raw = (evt as { originalEvent?: unknown }).originalEvent;
    if (raw instanceof MouseEvent) {
      const rect = cy.container()!.getBoundingClientRect();
      return { x: raw.clientX - rect.left, y: raw.clientY - rect.top };
    }
    return null;
  }

  /** Ручка resize у «самой маленькой» подходящей группы (вложенные — приоритет внутренней). */
  private findGroupResizeTarget(
    cy: Core,
    rx: number,
    ry: number,
    tol: number,
  ): { node: NodeSingular; mode: GroupResizeHandle } | null {
    const cands: { node: NodeSingular; mode: GroupResizeHandle; area: number }[] = [];
    cy.nodes('[kind = "GROUP"]').forEach((ele) => {
      const n = ele as NodeSingular;
      if (!n.isNode()) return;
      const bb = n.renderedBoundingBox({ includeLabels: false });
      const mode = this.hitGroupResizeHandle(rx, ry, bb, tol);
      if (mode == null) return;
      const mbb = n.boundingBox({ includeLabels: false });
      const area = Math.max(0, mbb.w) * Math.max(0, mbb.h);
      cands.push({ node: n, mode, area });
    });
    if (cands.length === 0) return null;
    cands.sort((a, b) => a.area - b.area);
    return { node: cands[0].node, mode: cands[0].mode };
  }

  private clientToModel(cy: Core, clientX: number, clientY: number): { x: number; y: number } {
    const rect = cy.container()!.getBoundingClientRect();
    const p = cy.pan();
    const z = cy.zoom();
    return {
      x: (clientX - rect.left - p.x) / z,
      y: (clientY - rect.top - p.y) / z,
    };
  }

  /** Угол или сторона под курсором (rendered bbox). */
  private hitGroupResizeHandle(
    rx: number,
    ry: number,
    bb: { x1: number; y1: number; x2: number; y2: number },
    tol: number,
  ): GroupResizeHandle | null {
    const nearLeft = Math.abs(rx - bb.x1) <= tol;
    const nearRight = Math.abs(rx - bb.x2) <= tol;
    const nearTop = Math.abs(ry - bb.y1) <= tol;
    const nearBottom = Math.abs(ry - bb.y2) <= tol;
    const inX = rx >= bb.x1 - tol && rx <= bb.x2 + tol;
    const inY = ry >= bb.y1 - tol && ry <= bb.y2 + tol;
    if (nearTop && nearLeft && inX && inY) return 'nw';
    if (nearTop && nearRight && inX && inY) return 'ne';
    if (nearBottom && nearLeft && inX && inY) return 'sw';
    if (nearBottom && nearRight && inX && inY) return 'se';
    if (nearTop && inX && !(nearLeft || nearRight)) return 'n';
    if (nearBottom && inX && !(nearLeft || nearRight)) return 's';
    if (nearLeft && inY && !(nearTop || nearBottom)) return 'w';
    if (nearRight && inY && !(nearTop || nearBottom)) return 'e';
    return null;
  }

  private cursorForResizeHandle(h: GroupResizeHandle | null): string {
    switch (h) {
      case 'nw':
      case 'se':
        return 'nwse-resize';
      case 'ne':
      case 'sw':
        return 'nesw-resize';
      case 'n':
      case 's':
        return 'ns-resize';
      case 'w':
      case 'e':
        return 'ew-resize';
      default:
        return '';
    }
  }

  /** Углы и стороны рамки группы — перетаскивание мышью с сохранением в pending / автосохранение. */
  private wireGroupResizeHandles(): void {
    const cy = this.cy;
    if (!cy) return;
    const tol = 18;

    // Без селектора по GROUP: после вложения узлов target mousedown/mousemove часто — дочерний узел, а не группа.
    cy.on('mousemove', (evt: EventObject) => {
      if (this.groupResizeState != null) return;
      const ctn = cy.container();
      if (!ctn) return;
      const rp = this.cyRenderedPointerFromEvent(evt, cy);
      if (!rp) return;
      const hit = this.findGroupResizeTarget(cy, rp.x, rp.y, tol);
      const want = hit ? this.cursorForResizeHandle(hit.mode) : null;
      if (want) {
        if (this.lastAppliedGroupResizeCursor !== want) {
          this.lastAppliedGroupResizeCursor = want;
          ctn.style.cursor = want;
        }
      } else if (this.lastAppliedGroupResizeCursor != null) {
        this.lastAppliedGroupResizeCursor = null;
        this.resetCyContainerCursor();
      }
    });

    const ctn = cy.container();
    if (ctn) {
      const onLeave = (): void => {
        if (this.groupResizeState == null) {
          this.lastAppliedGroupResizeCursor = null;
          this.resetCyContainerCursor();
        }
      };
      ctn.addEventListener('mouseleave', onLeave);
      this.groupResizeCursorLeaveCleanup = () => {
        ctn.removeEventListener('mouseleave', onLeave);
      };
    }

    cy.on('mousedown', (evt: EventObject) => {
      const raw = (evt as { originalEvent?: unknown }).originalEvent;
      if (!(raw instanceof MouseEvent) || raw.button !== 0) return;

      const rp = this.cyRenderedPointerFromEvent(evt, cy);
      if (!rp) return;
      const found = this.findGroupResizeTarget(cy, rp.x, rp.y, tol);
      if (!found) return;

      const n = found.node;
      const mode = found.mode;
      this.setCyAutoungrabify(true);
      evt.stopPropagation();
      evt.preventDefault();
      const modelBb = n.boundingBox({ includeLabels: false });
      const dbId = n.data('topologyObjectId') as number | undefined;
      const ctn2 = cy.container();
      if (ctn2) ctn2.style.cursor = this.cursorForResizeHandle(mode);
      this.lastAppliedGroupResizeCursor = this.cursorForResizeHandle(mode);
      this.groupResizeState = {
        node: n,
        dbId,
        mode,
        anchorX1: modelBb.x1,
        anchorY1: modelBb.y1,
        anchorX2: modelBb.x2,
        anchorY2: modelBb.y2,
      };
      const onMove = (e: MouseEvent): void => {
        if (!this.groupResizeState) return;
        const st = this.groupResizeState;
        const pos = this.clientToModel(cy, e.clientX, e.clientY);
        const minW = 120;
        const minH = 80;
        let x1 = st.anchorX1;
        let y1 = st.anchorY1;
        let x2 = st.anchorX2;
        let y2 = st.anchorY2;
        switch (st.mode) {
          case 'se':
            x2 = Math.max(x1 + minW, pos.x);
            y2 = Math.max(y1 + minH, pos.y);
            break;
          case 'sw':
            x1 = Math.min(x2 - minW, pos.x);
            y2 = Math.max(y1 + minH, pos.y);
            break;
          case 'ne':
            x2 = Math.max(x1 + minW, pos.x);
            y1 = Math.min(y2 - minH, pos.y);
            break;
          case 'nw':
            x1 = Math.min(x2 - minW, pos.x);
            y1 = Math.min(y2 - minH, pos.y);
            break;
          case 'e':
            x2 = Math.max(x1 + minW, pos.x);
            break;
          case 'w':
            x1 = Math.min(x2 - minW, pos.x);
            break;
          case 's':
            y2 = Math.max(y1 + minH, pos.y);
            break;
          case 'n':
            y1 = Math.min(y2 - minH, pos.y);
            break;
        }
        const w = x2 - x1;
        const h = y2 - y1;
        const cx = (x1 + x2) / 2;
        const cyM = (y1 + y2) / 2;
        st.anchorX1 = x1;
        st.anchorY1 = y1;
        st.anchorX2 = x2;
        st.anchorY2 = y2;
        n.data('groupWidth', w);
        n.data('groupHeight', h);
        n.position({ x: cx, y: cyM });
      };
      const onUp = (): void => {
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
        this.groupResizeListeners = null;
        this.setCyAutoungrabify(false);
        this.lastAppliedGroupResizeCursor = null;
        this.resetCyContainerCursor();
        const st = this.groupResizeState;
        this.groupResizeState = null;
        if (!st) return;
        const w = Number(st.node.data('groupWidth')) || 280;
        const h = Number(st.node.data('groupHeight')) || 200;
        const pos = st.node.position();
        if (st.dbId != null) {
          this.pendingGroupLayoutUpdates.set(st.dbId, { cx: pos.x, cy: pos.y, w, h });
          if (this.isAutosaveForCurrentTopology()) {
            this.scheduleAutosaveLayout();
          } else {
            this.layoutDirty.set(true);
          }
        } else {
          this.layoutDirty.set(true);
        }
      };
      this.groupResizeListeners = { move: onMove, up: onUp };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    });
  }

  private wireCyLevelNavigationEvents(): void {
    const cy = this.cy;
    if (!cy) return;
    cy.on('dbltap', 'node', (evt: EventObject) => this.onCyNodeDoubleTap(evt));
  }

  private onCyNodeDoubleTap(evt: EventObject): void {
    const target = evt.target;
    if (!target?.isNode?.()) return;
    const kind = (target.data('kind') as string | undefined) ?? '';
    if (kind !== 'NODE') return;
    if (!this.canOpenLayerForNode(target)) return;
    const parentObjectId = target.data('topologyObjectId') as number | undefined;
    if (parentObjectId == null) return;
    const fallback = String(target.data('label') ?? '').trim();
    const label = fallback.length > 0 ? fallback : `Объект ${parentObjectId}`;
    void this.navigateToLayer({ parentObjectId, label });
  }

  private canOpenLayerForNode(node: { data: (k: string) => unknown }): boolean {
    const nodeKind = (node.data('nodeKind') as string | undefined) ?? '';
    if (nodeKind === 'RACK' || nodeKind === 'SERVER' || nodeKind === 'NETWORK') return true;
    const label = String(node.data('label') ?? '').trim().toLowerCase();
    return label.includes('узел');
  }

  protected onLayerBack(): void {
    if (!this.canNavigateBackLevel()) return;
    const nextStack = this.layerStack().slice(0, -1);
    void this.navigateToLayer(nextStack);
  }

  private async navigateToLayer(nextStackOrEntry: TopologyLayerEntry[] | TopologyLayerEntry): Promise<void> {
    const nextStack = Array.isArray(nextStackOrEntry)
      ? nextStackOrEntry
      : [...this.layerStack(), nextStackOrEntry];
    if (this.layoutSaving()) return;
    if (this.hasUnsavedLayoutChanges()) {
      const topologyId = this.selectedTopologyId();
      if (topologyId == null) return;
      const pendingCreates = this.collectPendingCreatesFromGraph();
      const positionEntries = [...this.pendingPositionUpdates.entries()];
      const groupLayoutEntries = [...this.pendingGroupLayoutUpdates.entries()];
      const membershipSnapshot = new Map(this.pendingMembershipByObjectId);
      this.layoutSaving.set(true);
      const ok = await this.runFlushLayout(
        topologyId,
        pendingCreates,
        positionEntries,
        groupLayoutEntries,
        membershipSnapshot,
        { silent: false },
      );
      if (!ok) return;
    }
    this.closeObjectSettings();
    this.cancelAutosaveLayoutTimer();
    this.resetLocalLayoutState();
    this.persistCyViewportNowForCurrentLayer();
    this.layerStack.set(nextStack);
    this.rebuildGraph();
  }

  private wireCyContextMenu(): void {
    const cy = this.cy;
    if (!cy) return;
    cy.on('cxttap', (evt: EventObject) => this.onCyCxttap(evt, cy));
  }

  private onCyCxttap(evt: EventObject, cy: Core): void {
    if (this.edgeDrawSuppressNextContextMenu) {
      if (this.edgeDrawSuppressResetTimer != null) {
        clearTimeout(this.edgeDrawSuppressResetTimer);
        this.edgeDrawSuppressResetTimer = null;
      }
      this.edgeDrawSuppressNextContextMenu = false;
      return;
    }
    (evt as { preventDefault?: () => void }).preventDefault?.();
    const coords = this.cyContextMenuClientCoords(evt, cy);
    if (!coords) return;

    const t = evt.target as {
      isNode?: () => boolean;
      isEdge?: () => boolean;
      data?: (k: string) => unknown;
    };
    if (t.isEdge?.()) {
      const isPreview = t.data?.('preview') === '1' || t.data?.('preview') === 1;
      if (!isPreview) {
        this.openCyContextMenu(coords.x, coords.y, this.buildEdgeContextMenuItems(evt.target));
        return;
      }
    }
    if (t.isNode?.()) {
      const nodeItems = this.buildNodeContextMenuItems(evt.target);
      if (nodeItems.length === 0) {
        return;
      }
      this.openCyContextMenu(coords.x, coords.y, nodeItems);
      return;
    }
    const modelPos = (evt as { position?: { x: number; y: number } }).position;
    this.openCyContextMenu(coords.x, coords.y, this.buildBackgroundContextMenuItems(modelPos));
  }

  /** Координаты экрана для пункта ПКМ (нужны для якоря под PrimeNG Menu). */
  private cyContextMenuClientCoords(evt: EventObject, cy: Core): { x: number; y: number } | null {
    const raw = evt as { originalEvent?: Event };
    if (raw.originalEvent instanceof MouseEvent) {
      return { x: raw.originalEvent.clientX, y: raw.originalEvent.clientY };
    }
    const rp = (evt as { renderedPosition?: { x: number; y: number } }).renderedPosition;
    const cont = cy.container();
    const rect = cont?.getBoundingClientRect();
    if (rp && rect) {
      return { x: rect.left + rp.x, y: rect.top + rp.y };
    }
    return null;
  }

  private openCyContextMenu(clientX: number, clientY: number, items: MenuItem[]): void {
    // Повторный ПКМ: иначе popup остаётся открытым и show() не переносит оверлей и не подхватывает новую model.
    this.cyContextMenuRef()?.hide();
    this.cyContextMenuModel.set(items);
    this.cdr.markForCheck();
    queueMicrotask(() => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => this.showCyContextMenuAt(clientX, clientY));
      });
    });
  }

  private showCyContextMenuAt(clientX: number, clientY: number): void {
    const menu = this.cyContextMenuRef();
    if (!menu) return;
    if (!this.cyMenuAnchor) {
      const el = document.createElement('div');
      el.setAttribute('aria-hidden', 'true');
      el.style.position = 'fixed';
      el.style.width = '1px';
      el.style.height = '1px';
      el.style.margin = '0';
      el.style.padding = '0';
      el.style.border = 'none';
      el.style.pointerEvents = 'none';
      el.style.opacity = '0';
      el.style.zIndex = '-1';
      document.body.appendChild(el);
      this.cyMenuAnchor = el;
    }
    this.cyMenuAnchor.style.left = `${clientX}px`;
    this.cyMenuAnchor.style.top = `${clientY}px`;
    const anchorEvent = { currentTarget: this.cyMenuAnchor } as unknown as MouseEvent;
    menu.show(anchorEvent);
  }

  private buildBackgroundContextMenuItems(modelPos: { x: number; y: number } | undefined): MenuItem[] {
    const pos = modelPos ?? { x: 120, y: 120 };
    const hasTopology = this.selectedTopologyId() != null;
    const canEdit = this.canEditCurrentTopology();
    const viewItems: MenuItem[] = [
      {
        label: 'Подогнать в экран',
        icon: 'pi pi-window-maximize',
        command: () => this.cy?.fit(undefined, 48),
      },
      {
        label: 'Обновить с сервера',
        icon: 'pi pi-refresh',
        disabled: !hasTopology,
        command: () => this.rebuildGraph(),
      },
    ];
    if (!canEdit) {
      return viewItems;
    }
    return [
      {
        label: 'Настройки слоя',
        icon: 'pi pi-sliders-h',
        disabled: !hasTopology,
        command: () => this.openLayerSettingsDialog(),
      },
      { separator: true },
      {
        label: 'Добавить узел здесь',
        icon: 'pi pi-plus-circle',
        disabled: !hasTopology,
        command: () => this.addNodeAtModelPosition(pos.x, pos.y),
      },
      {
        label: 'Добавить группу',
        icon: 'pi pi-th-large',
        disabled: !hasTopology,
        command: () => this.addGroupAtModelPosition(pos.x, pos.y),
      },
      { separator: true },
      ...viewItems,
    ];
  }

  private buildNodeContextMenuItems(target: unknown): MenuItem[] {
    const node = target as {
      id(): string;
      data: (k: string) => unknown;
      connectedEdges: () => { remove: () => void };
      remove: () => void;
    };
    const kind = (node.data('kind') as string) ?? 'NODE';
    const canEdit = this.canEditCurrentTopology();
    const items: MenuItem[] = [];
    if (kind === 'NODE') {
      const deviceId = node.data('deviceId') as number | undefined;
      if (deviceId != null && Number.isFinite(deviceId)) {
        items.push({
          label: 'Сведения',
          icon: 'pi pi-info-circle',
          command: () => void this.router.navigate(['/monitoring', deviceId]),
        });
      }
    }
    if (!canEdit) {
      return items;
    }
    items.push({
      label: 'Настройки',
      icon: 'pi pi-cog',
      command: () => this.openObjectSettingsFromCyNode(node),
    });
    if (kind === 'NODE') {
      items.push({
        label: 'Сменить тип',
        icon: 'pi pi-tags',
        items: TOPOLOGY_NODE_KIND_MENU_OPTIONS.map((opt) => ({
          label: opt.label,
          command: () => this.applyNodeKindFromContextMenu(node, opt.value),
        })),
      });
      items.push({
        label: 'Удалить узел',
        icon: 'pi pi-trash',
        command: () => this.onCyContextDeleteNode(node, 'NODE'),
      });
    }
    if (kind === 'GROUP') {
      items.push({
        label: 'Удалить группу',
        icon: 'pi pi-trash',
        command: () => this.onCyContextDeleteNode(node, 'GROUP'),
      });
    }
    return items;
  }

  private buildEdgeContextMenuItems(target: unknown): MenuItem[] {
    const edge = target as {
      id(): string;
      data: (k: string) => unknown;
      remove: () => void;
      source: () => { data: (k: string) => unknown; id(): string };
      target: () => { data: (k: string) => unknown; id(): string };
    };
    const infoItem: MenuItem = {
      label: 'Сведения',
      icon: 'pi pi-info-circle',
      command: () => {
        const srcN = edge.source();
        const tgtN = edge.target();
        const s = srcN.data('label') ?? srcN.id();
        const t = tgtN.data('label') ?? tgtN.id();
        const dbId = edge.data('topologyObjectId');
        this.notifier.info(
          `Связь «${String(s)}» → «${String(t)}» (elementId: ${edge.id()}). ID в БД: ${dbId != null ? String(dbId) : '—'}.`,
        );
      },
    };
    if (!this.canEditCurrentTopology()) {
      return [infoItem];
    }
    return [
      infoItem,
      {
        label: 'Настройки',
        icon: 'pi pi-cog',
        command: () => this.openObjectSettingsFromCyEdge(edge),
      },
      {
        label: 'Удалить связь',
        icon: 'pi pi-trash',
        command: () => this.onCyContextDeleteEdge(edge),
      },
    ];
  }

  private closeObjectSettings(): void {
    const preview = this.objectSettingsLayerPreviewUrl();
    if (preview != null) {
      const id = this.objectSettingsDbId();
      const inMap = id != null && this.groupLayerBackgroundUrls.get(id) === preview;
      if (!inMap) {
        URL.revokeObjectURL(preview);
      }
    }
    this.objectSettingsLayerPreviewUrl.set(null);
    this.objectSettingsLayerBackgroundPresent.set(false);
    this.objectSettingsLayerBgUploading.set(false);
    this.objectSettingsVisible.set(false);
    this.objectSettingsElementId.set(null);
    this.objectSettingsSaving.set(false);
    this.objectSettingsGroupBorderColor.set(null);
    this.objectSettingsInitialGroupBorderColor = undefined;
    this.objectSettingsEdgeLineColor.set(null);
    this.objectSettingsInitialEdgeLineColor = undefined;
  }

  private openObjectSettingsFromCyNode(node: {
    id(): string;
    data: (k: string) => unknown;
  }): void {
    const kind = (node.data('kind') as string) ?? 'NODE';
    if (kind === 'GROUP') {
      this.fillObjectSettingsForm('GROUP', node);
    } else {
      this.fillObjectSettingsForm('NODE', node);
      this.loadDeviceOptionsForSettings();
    }
    this.objectSettingsVisible.set(true);
    this.cdr.markForCheck();
  }

  private openObjectSettingsFromCyEdge(edge: { id(): string; data: (k: string) => unknown }): void {
    this.fillObjectSettingsForm('EDGE', edge);
    this.objectSettingsVisible.set(true);
    this.cdr.markForCheck();
  }

  private fillObjectSettingsForm(
    objectKind: TopologyCyObjectKind,
    el: { id(): string; data: (k: string) => unknown },
  ): void {
    const prevPreview = this.objectSettingsLayerPreviewUrl();
    const prevDb = this.objectSettingsDbId();
    if (prevPreview != null) {
      const inMap = prevDb != null && this.groupLayerBackgroundUrls.get(prevDb) === prevPreview;
      if (!inMap) {
        URL.revokeObjectURL(prevPreview);
      }
    }
    this.objectSettingsLayerPreviewUrl.set(null);

    this.objectSettingsObjectKind.set(objectKind);
    this.objectSettingsElementId.set(el.id());
    this.objectSettingsPending.set(el.data('pendingCreate') === true);
    this.objectSettingsDbId.set((el.data('topologyObjectId') as number | undefined) ?? null);
    const label = String(el.data('label') ?? '');
    this.objectSettingsName.set(label);
    if (objectKind === 'NODE') {
      const nk = (el.data('nodeKind') as TopologyNodeKind | undefined) ?? 'RACK';
      this.objectSettingsNodeKind.set(nk);
      const did = el.data('deviceId') as number | null | undefined;
      const normalized = did != null && Number.isFinite(did) ? did : null;
      this.objectSettingsDeviceId.set(normalized);
      this.objectSettingsInitialDeviceId = normalized;
      this.objectSettingsGroupBorderColor.set(null);
      this.objectSettingsInitialGroupBorderColor = undefined;
      this.objectSettingsLayerBackgroundPresent.set(false);
      this.objectSettingsLayerBgUploading.set(false);
      this.objectSettingsEdgeLineColor.set(null);
      this.objectSettingsInitialEdgeLineColor = undefined;
    } else if (objectKind === 'GROUP') {
      this.objectSettingsInitialDeviceId = undefined;
      const raw = el.data('groupFrameBorderColor') as string | null | undefined;
      const norm = this.normalizeGroupBorderHexOrNull(
        typeof raw === 'string' ? raw : null,
      );
      this.objectSettingsGroupBorderColor.set(norm);
      this.objectSettingsInitialGroupBorderColor = norm;
      this.objectSettingsLayerBackgroundPresent.set(el.data('groupLayerBackgroundPresent') === true);
      this.objectSettingsLayerBgUploading.set(false);
      this.objectSettingsEdgeLineColor.set(null);
      this.objectSettingsInitialEdgeLineColor = undefined;
    } else {
      this.objectSettingsInitialDeviceId = undefined;
      this.objectSettingsGroupBorderColor.set(null);
      this.objectSettingsInitialGroupBorderColor = undefined;
      this.objectSettingsLayerBackgroundPresent.set(false);
      this.objectSettingsLayerBgUploading.set(false);
      const rawLine = el.data('edgeLineColor') as string | null | undefined;
      const normLine = this.normalizeGroupBorderHexOrNull(
        typeof rawLine === 'string' ? rawLine : null,
      );
      this.objectSettingsEdgeLineColor.set(normLine);
      this.objectSettingsInitialEdgeLineColor = normLine;
    }
  }

  private patchCyNodeDeviceBindingFromRecord(node: NodeSingular, rec: TopologyObjectRecord): void {
    if (rec.kind !== 'NODE') return;
    const n = node as unknown as { data: (k: string, v?: unknown) => void; removeData: (k: string) => void };
    if (rec.deviceId != null && Number.isFinite(rec.deviceId)) {
      n.data('deviceId', rec.deviceId);
      n.data('deviceHostAvailability', rec.deviceHostAvailability ?? 'UNKNOWN');
      const dh = rec.deviceHealthStatus;
      if (dh === 'NORM' || dh === 'WARN' || dh === 'CRITICAL') {
        n.data('deviceHealthStatus', dh);
      } else {
        n.removeData('deviceHealthStatus');
      }
    } else {
      n.removeData('deviceId');
      n.removeData('deviceHostAvailability');
      n.removeData('deviceHealthStatus');
    }
  }

  /** Значение для input type=color (только #RRGGBB). */
  protected groupBorderPickerDisplayValue(): string {
    const c = this.objectSettingsGroupBorderColor();
    return c != null && /^#[0-9A-Fa-f]{6}$/i.test(c) ? c : TOPOLOGY_GROUP_DEFAULT_BORDER;
  }

  protected onGroupBorderColorInput(ev: Event): void {
    const v = (ev.target as HTMLInputElement).value;
    if (/^#[0-9A-Fa-f]{6}$/i.test(v)) {
      this.objectSettingsGroupBorderColor.set(v.toLowerCase());
    }
  }

  protected resetGroupBorderColorToDefault(): void {
    this.objectSettingsGroupBorderColor.set(null);
  }

  protected openLayerSettingsDialog(): void {
    if (!this.canEditCurrentTopology()) {
      this.notifier.warn('Редактирование этой топологии вам недоступно.');
      return;
    }
    const tid = this.selectedTopologyId();
    if (tid == null) return;
    const lid = this.currentLayerParentId();
    this.layerSettingsEditingRoot = lid == null;
    this.cyContextMenuRef()?.hide();
    const prev = this.layerSettingsLayerPreviewUrl();
    if (prev != null && prev !== this.layerHostBackdropBgUrl()) {
      URL.revokeObjectURL(prev);
    }
    this.layerSettingsLayerPreviewUrl.set(null);
    this.layerSettingsDialogVisible.set(true);
    this.layerSettingsLoading.set(true);
    this.layerSettingsSaving.set(false);
    if (this.layerSettingsEditingRoot) {
      this.layerSettingsAllowLayerBackgroundImage.set(false);
      this.topologyService
        .getById(tid)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (rec) => {
            this.layerSettingsName.set(rec.name?.trim() ?? '');
            this.layerSettingsInitialName = rec.name ?? null;
            const bd = this.normalizeGroupBorderHexOrNull(
              typeof rec.rootLayerBackdropColor === 'string' ? rec.rootLayerBackdropColor : null,
            );
            this.layerSettingsBackdropHex.set(bd);
            this.layerSettingsInitialBackdrop = bd;
            this.layerSettingsLayerBgPresent.set(false);
            this.layerSettingsLoading.set(false);
            this.cdr.markForCheck();
          },
          error: () => {
            this.layerSettingsLoading.set(false);
            this.layerSettingsDialogVisible.set(false);
            this.notifier.error('Не удалось загрузить настройки слоя.');
          },
        });
      return;
    }
    if (lid == null) return;
    this.topologyService
      .getObject(tid, lid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rec) => {
          this.layerSettingsName.set(rec.name?.trim() ?? '');
          this.layerSettingsInitialName = rec.name ?? null;
          const bd = this.normalizeGroupBorderHexOrNull(
            typeof rec.layerBackdropColor === 'string' ? rec.layerBackdropColor : null,
          );
          this.layerSettingsBackdropHex.set(bd);
          this.layerSettingsInitialBackdrop = bd;
          const allowImg = rec.kind === 'GROUP';
          this.layerSettingsAllowLayerBackgroundImage.set(allowImg);
          this.layerSettingsLayerBgPresent.set(allowImg && rec.layerBackgroundPresent === true);
          this.layerSettingsLoading.set(false);
          this.cdr.markForCheck();
        },
        error: () => {
          this.layerSettingsLoading.set(false);
          this.layerSettingsDialogVisible.set(false);
          this.notifier.error('Не удалось загрузить настройки слоя.');
        },
      });
  }

  protected onLayerSettingsDialogVisibleChange(visible: boolean): void {
    this.layerSettingsDialogVisible.set(visible);
    if (!visible) {
      this.layerSettingsDialogCleanup();
    }
  }

  private layerSettingsDialogCleanup(): void {
    const preview = this.layerSettingsLayerPreviewUrl();
    if (preview != null && preview !== this.layerHostBackdropBgUrl()) {
      URL.revokeObjectURL(preview);
    }
    this.layerSettingsLayerPreviewUrl.set(null);
    this.layerSettingsLoading.set(false);
    this.layerSettingsSaving.set(false);
    this.layerSettingsLayerBgUploading.set(false);
    this.layerSettingsAllowLayerBackgroundImage.set(false);
  }

  protected closeLayerSettingsDialog(): void {
    this.layerSettingsDialogVisible.set(false);
  }

  protected layerSettingsBackdropPickerDisplayValue(): string {
    const c = this.layerSettingsBackdropHex();
    return c != null && /^#[0-9A-Fa-f]{6}$/i.test(c) ? c : TOPOLOGY_LAYER_BACKDROP_PICKER_EMPTY;
  }

  protected onLayerSettingsBackdropInput(ev: Event): void {
    const v = (ev.target as HTMLInputElement).value;
    if (/^#[0-9A-Fa-f]{6}$/i.test(v)) {
      this.layerSettingsBackdropHex.set(v.toLowerCase());
    }
  }

  protected resetLayerSettingsBackdropToClear(): void {
    this.layerSettingsBackdropHex.set(null);
  }

  protected saveLayerSettingsFromDialog(): void {
    const tid = this.selectedTopologyId();
    if (tid == null || this.layerSettingsSaving()) return;
    const nameRaw = this.layerSettingsName().trim();
    const initialNameTrimmed = this.layerSettingsInitialName?.trim() ?? '';
    const curBackdrop = this.layerSettingsBackdropHex();
    const iniBackdrop = this.layerSettingsInitialBackdrop ?? null;

    if (this.layerSettingsEditingRoot) {
      const row = this.topologyList().find((r) => r.id === tid);
      if (row == null) {
        this.notifier.error('Топология не найдена в списке.');
        return;
      }
      const nameForPut = nameRaw.length > 0 ? nameRaw : row.name;
      const nameChanged = nameForPut !== initialNameTrimmed;
      const backdropChanged = curBackdrop !== iniBackdrop;
      if (!nameChanged && !backdropChanged) {
        this.notifier.info('Нет изменений для сохранения.');
        this.closeLayerSettingsDialog();
        return;
      }
      const body: TopologyUpdateRequest = {
        name: nameForPut,
        visibility: row.visibility,
        autosave: row.autosave,
        autoCenterOnResize: row.autoCenterOnResize ?? true,
        sharedUserIds: [...row.sharedUserIds],
        document: row.document,
      };
      if (backdropChanged) {
        body.rootLayerBackdropColor = curBackdrop == null ? '' : curBackdrop;
      }
      this.layerSettingsSaving.set(true);
      this.topologyService
        .update(tid, body)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (updated) => {
            this.layerSettingsSaving.set(false);
            this.layerSettingsInitialName = updated.name ?? null;
            this.layerSettingsInitialBackdrop = curBackdrop;
            this.topologyList.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
            this.refreshLayerHostBackdrop(tid);
            this.notifier.success('Настройки слоя сохранены.');
            this.closeLayerSettingsDialog();
          },
          error: () => {
            this.layerSettingsSaving.set(false);
            this.notifier.error('Не удалось сохранить настройки слоя.');
          },
        });
      return;
    }

    const lid = this.currentLayerParentId();
    if (lid == null) return;
    const body: TopologyObjectUpdatePayload = {};
    if (nameRaw !== initialNameTrimmed) {
      body.name = nameRaw.length > 0 ? nameRaw : null;
    }
    if (curBackdrop !== iniBackdrop) {
      body.layerBackdropColor = curBackdrop == null ? '' : curBackdrop;
    }
    if (Object.keys(body).length === 0) {
      this.notifier.info('Нет изменений для сохранения.');
      this.closeLayerSettingsDialog();
      return;
    }
    this.layerSettingsSaving.set(true);
    this.topologyService
      .updateObject(tid, lid, body)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.layerSettingsSaving.set(false);
          this.layerSettingsInitialName = nameRaw.length > 0 ? nameRaw : null;
          this.layerSettingsInitialBackdrop = curBackdrop;
          this.patchTopLayerStackLabel(nameRaw.length > 0 ? nameRaw : `Объект ${lid}`);
          this.refreshLayerHostBackdrop(tid);
          this.notifier.success('Настройки слоя сохранены.');
          this.closeLayerSettingsDialog();
        },
        error: () => {
          this.layerSettingsSaving.set(false);
          this.notifier.error('Не удалось сохранить настройки слоя.');
        },
      });
  }

  private patchTopLayerStackLabel(displayName: string): void {
    const stack = this.layerStack();
    if (stack.length === 0) return;
    const last = stack[stack.length - 1];
    this.layerStack.set([...stack.slice(0, -1), { ...last, label: displayName }]);
  }

  protected pickLayerSettingsBgFile(): void {
    if (!this.layerSettingsAllowLayerBackgroundImage()) return;
    this.layerSettingsBgFileInput()?.nativeElement?.click();
  }

  protected onLayerSettingsBgFileInput(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (file == null) return;
    if (!this.layerSettingsAllowLayerBackgroundImage()) return;
    const tid = this.selectedTopologyId();
    if (tid == null) return;
    const lid = this.currentLayerParentId();
    if (lid == null) return;
    const prevPreview = this.layerSettingsLayerPreviewUrl();
    if (prevPreview != null && prevPreview !== this.layerHostBackdropBgUrl()) {
      URL.revokeObjectURL(prevPreview);
    }
    const previewUrl = URL.createObjectURL(file);
    this.layerSettingsLayerPreviewUrl.set(previewUrl);
    this.layerSettingsLayerBgUploading.set(true);
    const onUploadOk = (): void => {
      this.layerSettingsLayerBgUploading.set(false);
      const oldHost = this.layerHostBackdropBgUrl();
      if (oldHost) URL.revokeObjectURL(oldHost);
      this.layerHostBackdropBgUrl.set(previewUrl);
      this.layerSettingsLayerPreviewUrl.set(null);
      this.layerSettingsLayerBgPresent.set(true);
      this.applyGroupLayerBackgroundStyles();
      this.scheduleSyncLayerHostBackdropImage();
      this.cdr.markForCheck();
      this.notifier.success('Фон слоя сохранён.');
    };
    const onUploadErr = (): void => {
      this.layerSettingsLayerBgUploading.set(false);
      URL.revokeObjectURL(previewUrl);
      this.layerSettingsLayerPreviewUrl.set(null);
      this.notifier.error('Не удалось загрузить фон.');
    };
    this.topologyService
      .uploadLayerBackground(tid, lid, file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => onUploadOk(),
        error: onUploadErr,
      });
  }

  protected deleteLayerSettingsLayerBg(): void {
    if (!this.layerSettingsAllowLayerBackgroundImage()) return;
    const tid = this.selectedTopologyId();
    if (tid == null) return;
    const lid = this.currentLayerParentId();
    if (lid == null) return;
    this.layerSettingsLayerBgUploading.set(true);
    const onDelOk = (): void => {
      this.layerSettingsLayerBgUploading.set(false);
      this.revokeLayerHostBackdropBlobUrl();
      const p = this.layerSettingsLayerPreviewUrl();
      if (p) {
        URL.revokeObjectURL(p);
        this.layerSettingsLayerPreviewUrl.set(null);
      }
      this.layerSettingsLayerBgPresent.set(false);
      this.applyGroupLayerBackgroundStyles();
      this.cdr.markForCheck();
      this.notifier.success('Фон слоя удалён.');
    };
    const onDelErr = (): void => {
      this.layerSettingsLayerBgUploading.set(false);
      this.notifier.error('Не удалось удалить фон.');
    };
    this.topologyService
      .deleteLayerBackground(tid, lid)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: onDelOk, error: onDelErr });
  }

  protected pickLayerBackgroundFile(): void {
    this.layerBgFileInput()?.nativeElement?.click();
  }

  protected onGroupLayerBackgroundFileInput(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (file == null) return;
    const topoId = this.selectedTopologyId();
    const dbId = this.objectSettingsDbId();
    if (topoId == null || dbId == null) {
      this.notifier.warn('Нет сохранённой группы для загрузки фона.');
      return;
    }
    if (this.objectSettingsPending()) {
      this.notifier.warn('Сначала сохраните топологию, затем загрузите фон слоя.');
      return;
    }
    const prevPreview = this.objectSettingsLayerPreviewUrl();
    if (prevPreview != null) {
      const inMap = this.groupLayerBackgroundUrls.get(dbId) === prevPreview;
      if (!inMap) {
        URL.revokeObjectURL(prevPreview);
      }
    }
    const previewUrl = URL.createObjectURL(file);
    this.objectSettingsLayerPreviewUrl.set(previewUrl);
    this.objectSettingsLayerBgUploading.set(true);
    this.topologyService
      .uploadLayerBackground(topoId, dbId, file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.objectSettingsLayerBgUploading.set(false);
          const elId = this.objectSettingsElementId();
          const cy = this.cy;
          if (cy != null && elId != null) {
            const n = cy.getElementById(elId);
            if (!n.empty() && n.isNode()) {
              (n as unknown as { data: (k: string, v?: unknown) => void }).data(
                'groupLayerBackgroundPresent',
                true,
              );
            }
          }
          const oldMap = this.groupLayerBackgroundUrls.get(dbId);
          if (oldMap) {
            URL.revokeObjectURL(oldMap);
          }
          this.groupLayerBackgroundUrls.set(dbId, previewUrl);
          this.objectSettingsLayerPreviewUrl.set(null);
          this.objectSettingsLayerBackgroundPresent.set(true);
          this.layerBgPreviewRev.update((v) => v + 1);
          this.applyGroupLayerBackgroundStyles();
          this.notifier.success('Фон слоя сохранён.');
          this.cdr.markForCheck();
        },
        error: () => {
          this.objectSettingsLayerBgUploading.set(false);
          URL.revokeObjectURL(previewUrl);
          this.objectSettingsLayerPreviewUrl.set(null);
          this.notifier.error('Не удалось загрузить фон.');
          this.cdr.markForCheck();
        },
      });
  }

  protected deleteLayerBackgroundFromPanel(): void {
    const topoId = this.selectedTopologyId();
    const dbId = this.objectSettingsDbId();
    const elId = this.objectSettingsElementId();
    if (topoId == null || dbId == null || elId == null) return;
    if (this.objectSettingsPending()) return;
    this.objectSettingsLayerBgUploading.set(true);
    this.topologyService
      .deleteLayerBackground(topoId, dbId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.objectSettingsLayerBgUploading.set(false);
          const u = this.groupLayerBackgroundUrls.get(dbId);
          if (u) {
            URL.revokeObjectURL(u);
          }
          this.groupLayerBackgroundUrls.delete(dbId);
          const cy = this.cy;
          if (cy != null) {
            const n = cy.getElementById(elId);
            if (!n.empty() && n.isNode()) {
              (n as unknown as { removeData: (k: string) => void }).removeData('groupLayerBackgroundPresent');
            }
          }
          this.objectSettingsLayerBackgroundPresent.set(false);
          const p = this.objectSettingsLayerPreviewUrl();
          if (p) {
            URL.revokeObjectURL(p);
            this.objectSettingsLayerPreviewUrl.set(null);
          }
          this.layerBgPreviewRev.update((v) => v + 1);
          this.applyGroupLayerBackgroundStyles();
          this.notifier.success('Фон слоя удалён.');
          this.cdr.markForCheck();
        },
        error: () => {
          this.objectSettingsLayerBgUploading.set(false);
          this.notifier.error('Не удалось удалить фон.');
        },
      });
  }

  /** Значение для input type=color у связи (только #RRGGBB). */
  protected edgeLinePickerDisplayValue(): string {
    const c = this.objectSettingsEdgeLineColor();
    return c != null && /^#[0-9A-Fa-f]{6}$/i.test(c) ? c : TOPOLOGY_EDGE_DEFAULT_LINE;
  }

  protected onEdgeLineColorInput(ev: Event): void {
    const v = (ev.target as HTMLInputElement).value;
    if (/^#[0-9A-Fa-f]{6}$/i.test(v)) {
      this.objectSettingsEdgeLineColor.set(v.toLowerCase());
    }
  }

  protected resetEdgeLineColorToDefault(): void {
    this.objectSettingsEdgeLineColor.set(null);
  }

  private normalizeGroupBorderHexOrNull(raw: string | null): string | null {
    if (raw == null) return null;
    const s = raw.trim();
    if (s.length === 0) return null;
    const withHash = s.startsWith('#') ? s : `#${s}`;
    if (/^#[0-9A-Fa-f]{6}$/i.test(withHash)) {
      return withHash.toLowerCase();
    }
    if (/^#[0-9A-Fa-f]{3}$/i.test(withHash)) {
      return (
        `#${withHash[1]}${withHash[1]}${withHash[2]}${withHash[2]}${withHash[3]}${withHash[3]}`
      ).toLowerCase();
    }
    return null;
  }

  private loadDeviceOptionsForSettings(): void {
    this.http
      .get<MonitoringPickPage>(`${this.apiBaseUrl}/api/monitoring`, {
        params: { page: '0', size: '500', sortField: 'ip', sortOrder: 'asc' },
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.deviceStatusFromMonitoringPick.clear();
          this.deviceHealthFromMonitoringPick.clear();
          for (const d of page.content ?? []) {
            if (typeof d.status === 'string' && d.status.length > 0) {
              this.deviceStatusFromMonitoringPick.set(d.id, d.status);
            }
            const hs = d.healthStatus;
            if (hs === 'NORM' || hs === 'WARN' || hs === 'CRITICAL') {
              this.deviceHealthFromMonitoringPick.set(d.id, hs);
            }
          }
          const opts = (page.content ?? []).map((d) => ({
            label: `#${d.id} · ${(d.name || '').trim() || d.ip} (${d.ip})`,
            value: d.id,
          }));
          this.objectSettingsDeviceOptions.set(opts);
          this.cdr.markForCheck();
        },
        error: () => {
          this.objectSettingsDeviceOptions.set([]);
          this.notifier.warn('Не удалось загрузить список устройств для привязки.');
        },
      });
  }

  protected onObjectSettingsClose(): void {
    this.closeObjectSettings();
  }

  protected objectSettingsDialogHeader(): string {
    switch (this.objectSettingsObjectKind()) {
      case 'NODE':
        return 'Настройки узла';
      case 'GROUP':
        return 'Настройки группы';
      case 'EDGE':
        return 'Настройки связи';
      default:
        return 'Настройки объекта';
    }
  }

  protected onObjectSettingsDialogVisibleChange(visible: boolean): void {
    this.objectSettingsVisible.set(visible);
    if (!visible) {
      this.closeObjectSettings();
    }
  }

  protected saveObjectSettingsFromPanel(): void {
    const cy = this.cy;
    const elId = this.objectSettingsElementId();
    const topoId = this.selectedTopologyId();
    if (!cy || elId == null || topoId == null || this.objectSettingsSaving()) return;

    const objectKind = this.objectSettingsObjectKind();
    const nameRaw = this.objectSettingsName().trim();
    const name = nameRaw.length > 0 ? nameRaw : null;

    if (objectKind === 'EDGE') {
      const edge = cy.getElementById(elId);
      if (edge.empty() || !edge.isEdge()) return;
      const curLine = this.objectSettingsEdgeLineColor();
      const iniLine = this.objectSettingsInitialEdgeLineColor;
      const applyEdgeLineToCy = (): void => {
        const e = edge as unknown as { data: (k: string, v?: unknown) => void; removeData: (k: string) => void };
        if (curLine != null) {
          e.data('edgeLineColor', curLine);
        } else {
          e.removeData('edgeLineColor');
        }
      };
      if (this.objectSettingsPending()) {
        edge.data('label', nameRaw);
        applyEdgeLineToCy();
        this.layoutDirty.set(true);
        this.notifier.success('Сохранено локально.');
        this.closeObjectSettings();
        return;
      }
      const dbId = this.objectSettingsDbId();
      if (dbId == null) {
        this.notifier.warn('Нет ID объекта для сохранения.');
        return;
      }
      const body: TopologyObjectUpdatePayload = { name };
      if (curLine !== iniLine) {
        body.lineColor = curLine === null ? '' : curLine;
      }
      this.objectSettingsSaving.set(true);
      this.topologyService
        .updateObject(topoId, dbId, body)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (rec) => {
            edge.data('label', nameRaw);
            applyEdgeLineToCy();
            if (rec.kind === 'EDGE') {
              if (rec.lineColor != null && rec.lineColor.length > 0) {
                (edge as unknown as { data: (k: string, v?: unknown) => void }).data(
                  'edgeLineColor',
                  rec.lineColor,
                );
              } else {
                (edge as unknown as { removeData: (k: string) => void }).removeData('edgeLineColor');
              }
            }
            this.objectSettingsSaving.set(false);
            this.notifier.success('Сохранено.');
            this.closeObjectSettings();
          },
          error: () => {
            this.objectSettingsSaving.set(false);
            this.notifier.error('Не удалось сохранить связь.');
          },
        });
      return;
    }

    const node = cy.getElementById(elId);
    if (node.empty() || !node.isNode()) return;

    if (objectKind === 'GROUP') {
      const displayLabel = nameRaw.length > 0 ? nameRaw : node.id();
      const curBorder = this.objectSettingsGroupBorderColor();
      const iniBorder = this.objectSettingsInitialGroupBorderColor;
      const applyGroupBorderToCy = (): void => {
        if (curBorder != null) {
          node.data('groupFrameBorderColor', curBorder);
        } else {
          node.removeData('groupFrameBorderColor');
        }
      };
      if (this.objectSettingsPending()) {
        node.data('label', displayLabel);
        applyGroupBorderToCy();
        this.layoutDirty.set(true);
        this.notifier.success('Сохранено локально.');
        this.closeObjectSettings();
        return;
      }
      const dbId = this.objectSettingsDbId();
      if (dbId == null) {
        this.notifier.warn('Нет ID объекта для сохранения.');
        return;
      }
      const body: TopologyObjectUpdatePayload = { name };
      if (curBorder !== iniBorder) {
        body.frameBorderColor = curBorder === null ? '' : curBorder;
      }
      this.objectSettingsSaving.set(true);
      this.topologyService
        .updateObject(topoId, dbId, body)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            node.data('label', displayLabel);
            applyGroupBorderToCy();
            this.objectSettingsInitialGroupBorderColor = curBorder;
            this.objectSettingsSaving.set(false);
            this.notifier.success('Сохранено.');
            this.closeObjectSettings();
          },
          error: () => {
            this.objectSettingsSaving.set(false);
            this.notifier.error('Не удалось сохранить группу.');
          },
        });
      return;
    }

    const nk = this.objectSettingsNodeKind();
    const devId = this.objectSettingsDeviceId();
    const initialDev = this.objectSettingsInitialDeviceId;
    const displayLabel = nameRaw.length > 0 ? nameRaw : node.id();

    if (this.objectSettingsPending()) {
      node.data('label', displayLabel);
      node.data('nodeKind', nk);
      if (devId != null) {
        node.data('deviceId', devId);
        const st = this.deviceStatusFromMonitoringPick.get(devId);
        const av = deviceHostAvailabilityFromMonitoringStatus(st);
        node.data('deviceHostAvailability', av ?? 'UNKNOWN');
        const hs = this.deviceHealthFromMonitoringPick.get(devId);
        if (hs === 'NORM' || hs === 'WARN' || hs === 'CRITICAL') {
          node.data('deviceHealthStatus', hs);
        } else {
          node.removeData('deviceHealthStatus');
        }
      } else {
        node.removeData('deviceId');
        node.removeData('deviceHostAvailability');
        node.removeData('deviceHealthStatus');
      }
      this.layoutDirty.set(true);
      this.notifier.success('Сохранено локально.');
      this.closeObjectSettings();
      return;
    }

    const dbId = this.objectSettingsDbId();
    if (dbId == null) {
      this.notifier.warn('Нет ID объекта для сохранения.');
      return;
    }

    const body: TopologyObjectUpdatePayload = { name, nodeKind: nk };
    if (devId != null) {
      body.deviceId = devId;
    } else if (initialDev != null) {
      body.clearDevice = true;
    }

    this.objectSettingsSaving.set(true);
    this.topologyService
      .updateObject(topoId, dbId, body)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rec) => {
          node.data('label', displayLabel);
          node.data('nodeKind', nk);
          this.patchCyNodeDeviceBindingFromRecord(node, rec);
          this.objectSettingsInitialDeviceId = devId;
          this.objectSettingsSaving.set(false);
          this.notifier.success('Сохранено.');
          this.closeObjectSettings();
        },
        error: () => {
          this.objectSettingsSaving.set(false);
          this.notifier.error('Не удалось сохранить узел.');
        },
      });
  }

  protected deleteObjectFromSettingsPanel(): void {
    const cy = this.cy;
    const elId = this.objectSettingsElementId();
    if (!cy || elId == null) return;
    const objectKind = this.objectSettingsObjectKind();
    if (objectKind === 'EDGE') {
      const edge = cy.getElementById(elId);
      if (!edge.empty() && edge.isEdge()) {
        this.onCyContextDeleteEdge(edge);
      }
      this.closeObjectSettings();
      return;
    }
    const node = cy.getElementById(elId);
    if (node.empty() || !node.isNode()) {
      this.closeObjectSettings();
      return;
    }
    const k = node.data('kind') === 'GROUP' ? 'GROUP' : 'NODE';
    this.onCyContextDeleteNode(node, k);
    this.closeObjectSettings();
  }

  private applyNodeKindFromContextMenu(
    node: { data: (k: string, v?: unknown) => unknown },
    nodeKind: TopologyNodeKind,
  ): void {
    if (node.data('pendingCreate') === true) {
      (node as { data: (k: string, v?: unknown) => void }).data('nodeKind', nodeKind);
      this.layoutDirty.set(true);
      this.notifier.success('Тип узла обновлён.');
      return;
    }
    const dbId = node.data('topologyObjectId') as number | undefined;
    const topoId = this.selectedTopologyId();
    if (dbId == null || topoId == null) {
      this.notifier.warn('Сохраните узел на сервере, затем смените тип.');
      return;
    }
    this.topologyService
      .updateObject(topoId, dbId, { nodeKind })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rec) => {
          (node as { data: (k: string, v?: unknown) => void }).data('nodeKind', nodeKind);
          this.patchCyNodeDeviceBindingFromRecord(node as NodeSingular, rec);
          this.notifier.success('Тип узла обновлён.');
        },
        error: () => this.notifier.error('Не удалось сменить тип узла.'),
      });
  }

  private onCyContextDeleteEdge(edge: { remove: () => void; data: (k: string) => unknown }): void {
    const dbId = edge.data('topologyObjectId') as number | undefined;
    const topoId = this.selectedTopologyId();
    if (dbId == null || topoId == null) {
      this.notifier.warn('Нет данных для удаления связи на сервере.');
      return;
    }
    this.topologyService
      .deleteObject(topoId, dbId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          edge.remove();
          if (
            this.pendingPositionUpdates.size === 0 &&
            this.pendingGroupLayoutUpdates.size === 0 &&
            this.pendingMembershipByObjectId.size === 0 &&
            this.collectPendingCreatesFromGraph().length === 0
          ) {
            this.layoutDirty.set(false);
          }
          this.notifier.success('Связь удалена.');
        },
        error: () => this.notifier.error('Не удалось удалить связь на сервере.'),
      });
  }

  private onCyContextDeleteNode(
    node: {
      data: (k: string) => unknown;
      connectedEdges: () => { remove: () => void };
      remove: () => void;
    },
    kind: 'NODE' | 'GROUP',
  ): void {
    if (node.data('pendingCreate') === true) {
      node.connectedEdges().remove();
      node.remove();
      this.layoutDirty.set(true);
      return;
    }
    const dbId = node.data('topologyObjectId') as number | undefined;
    const topoId = this.selectedTopologyId();
    if (dbId == null || topoId == null) {
      this.notifier.warn(
        'Нет данных для удаления на сервере (выберите топологию и убедитесь, что объект сохранён).',
      );
      return;
    }
    this.topologyService
      .deleteObject(topoId, dbId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingPositionUpdates.delete(dbId);
          this.pendingGroupLayoutUpdates.delete(dbId);
          this.pendingMembershipByObjectId.delete(dbId);
          if (kind === 'GROUP') {
            this.rebuildGraph({ preserveViewport: true });
          } else {
            node.connectedEdges().remove();
            node.remove();
          }
          if (
            this.pendingPositionUpdates.size === 0 &&
            this.pendingGroupLayoutUpdates.size === 0 &&
            this.pendingMembershipByObjectId.size === 0 &&
            this.collectPendingCreatesFromGraph().length === 0
          ) {
            this.layoutDirty.set(false);
          }
          this.notifier.success(kind === 'GROUP' ? 'Группа удалена.' : 'Узел удалён.');
        },
        error: () =>
          this.notifier.error(
            kind === 'GROUP' ? 'Не удалось удалить группу на сервере.' : 'Не удалось удалить узел на сервере.',
          ),
      });
  }

  private addNodeAtModelPosition(x: number, y: number): void {
    const id = this.selectedTopologyId();
    const layerId = this.currentLayerParentId();
    if (id == null) {
      this.notifier.warn('Сначала выберите топологию.');
      return;
    }
    if (this.isAutosaveForCurrentTopology()) {
      this.topologyService
        .createObject(id, {
          kind: 'NODE',
          name: 'Новый узел',
          nodeKind: 'RACK',
          ...(layerId != null ? { layerId } : {}),
          positionX: x,
          positionY: y,
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.notifier.success('Объект добавлен.');
            this.rebuildGraph({ preserveViewport: true });
          },
          error: () => this.notifier.error('Не удалось добавить объект.'),
        });
      return;
    }
    const cy = this.cy;
    if (!cy) return;
    const elId = `local-${crypto.randomUUID()}`;
    cy.add({
      group: 'nodes',
      data: {
        id: elId,
        label: 'Новый узел',
        kind: 'NODE',
        pendingCreate: true,
        nodeKind: 'RACK',
        layerId: layerId ?? undefined,
      },
      position: { x, y },
    });
    this.layoutDirty.set(true);
  }

  private addGroupAtModelPosition(x: number, y: number): void {
    const id = this.selectedTopologyId();
    const layerId = this.currentLayerParentId();
    if (id == null) {
      this.notifier.warn('Сначала выберите топологию.');
      return;
    }
    if (this.isAutosaveForCurrentTopology()) {
      this.topologyService
        .createObject(id, {
          kind: 'GROUP',
          name: 'Новая группа',
          ...(layerId != null ? { layerId } : {}),
          positionX: x,
          positionY: y,
          frameWidth: 280,
          frameHeight: 200,
        })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.notifier.success('Группа добавлена.');
            this.rebuildGraph({ preserveViewport: true });
          },
          error: () => this.notifier.error('Не удалось добавить группу.'),
        });
      return;
    }
    const cy = this.cy;
    if (!cy) return;
    const elId = `local-${crypto.randomUUID()}`;
    cy.add({
      group: 'nodes',
      data: {
        id: elId,
        label: 'Новая группа',
        kind: 'GROUP',
        pendingCreate: true,
        layerId: layerId ?? undefined,
        groupWidth: 280,
        groupHeight: 200,
        groupNestDepth: 0,
      },
      position: { x, y },
    });
    this.layoutDirty.set(true);
  }

  private bumpLayoutDirtyOrAutosave(): void {
    if (this.isAutosaveForCurrentTopology()) this.scheduleAutosaveLayout();
    else this.layoutDirty.set(true);
  }

  /**
   * Группы без compound-parent: запрещённые кандидаты для hit-test «внутренняя группа».
   * NODE — только сам объект. GROUP — сама и все группы, вложенные внутрь неё (нельзя класть родителя в потомка).
   */
  private forbiddenParentGroupElementIds(node: NodeSingular): Set<string> {
    const s = new Set<string>();
    s.add(node.id());
    if (node.data('kind') !== 'GROUP') return s;
    const cy = this.cy;
    if (!cy) return s;
    cy.nodes('[kind = "GROUP"]').forEach((ele) => {
      const h = ele as NodeSingular;
      if (h.id() === node.id()) return;
      if (this.membershipAncestorElementIds(h).has(node.id())) {
        s.add(h.id());
      }
    });
    return s;
  }

  /**
   * Все NODE/GROUP, чей membership в итоге лежит внутри данной группы (прямые и вложенные потомки).
   */
  private collectGroupDescendantNodes(cy: Core, groupElementId: string): NodeSingular[] {
    const out: NodeSingular[] = [];
    const visit = (gid: string): void => {
      cy.nodes().forEach((ele) => {
        if (!ele.isNode()) return;
        const k = ele.data('kind') as string | undefined;
        if (k !== 'NODE' && k !== 'GROUP') return;
        const mid = ele.data('membershipGroupElId') as string | undefined;
        if (mid !== gid) return;
        const n = ele as NodeSingular;
        out.push(n);
        if (k === 'GROUP') visit(n.id());
      });
    };
    visit(groupElementId);
    return out;
  }

  /** Цепочка elementId родительских групп по полю membershipGroupElId. */
  private membershipAncestorElementIds(node: NodeSingular): Set<string> {
    const s = new Set<string>();
    const cy = this.cy;
    if (!cy) return s;
    let gid = node.data('membershipGroupElId') as string | undefined;
    const seen = new Set<string>();
    while (gid != null && gid.length > 0) {
      if (seen.has(gid)) break;
      seen.add(gid);
      s.add(gid);
      const g = cy.getElementById(gid);
      if (g.empty() || !g.isNode()) break;
      gid = g.data('membershipGroupElId') as string | undefined;
    }
    return s;
  }

  /** Самая «внутренняя» группа, чей прямоугольник содержит точку (модель графа). */
  private findInnermostSavedOrPendingGroupAt(
    x: number,
    y: number,
    forbidden: Set<string>,
  ): NodeSingular | null {
    const cy = this.cy;
    if (!cy) return null;
    const cands: { n: NodeSingular; area: number }[] = [];
    cy.nodes('[kind = "GROUP"]').forEach((ele) => {
      const n = ele as NodeSingular;
      if (!n.isNode()) return;
      if (forbidden.has(n.id())) return;
      const bb = n.boundingBox({ includeLabels: false });
      if (x < bb.x1 || x > bb.x2 || y < bb.y1 || y > bb.y2) return;
      cands.push({ n, area: bb.w * bb.h });
    });
    if (cands.length === 0) return null;
    cands.sort((a, b) => a.area - b.area);
    return cands[0].n;
  }

  /**
   * После drop: группа по попаданию центра в рамку (без compound-parent — размер группы не пересчитывается по детям).
   */
  private reconcileGroupMembershipAfterDrag(node: NodeSingular): void {
    const kind = node.data('kind') as string | undefined;
    if (kind !== 'NODE' && kind !== 'GROUP') return;
    const dbIdRaw = node.data('topologyObjectId') as number | undefined;
    const hasDbId = dbIdRaw != null && Number.isFinite(dbIdRaw);
    const dbId = hasDbId ? dbIdRaw : undefined;

    const pos = node.position();
    const forbidden = this.forbiddenParentGroupElementIds(node);
    const inner = this.findInnermostSavedOrPendingGroupAt(pos.x, pos.y, forbidden);

    const curRaw = node.data('membershipGroupElId') as string | undefined;
    const curEl = curRaw != null && curRaw.length > 0 ? curRaw : null;
    const newEl = inner?.id() ?? null;
    if (curEl === newEl) return;

    const n = node as unknown as { removeData: (k: string) => void; data: (k: string, v?: unknown) => void };
    if (newEl == null) {
      n.removeData('membershipGroupElId');
    } else {
      n.data('membershipGroupElId', newEl);
    }

    if (kind === 'GROUP') {
      if (newEl == null) {
        n.data('groupNestDepth', 0);
      } else {
        const p = this.cy?.getElementById(newEl);
        const pd =
          p != null && !p.empty() && p.isNode() ? Number(p.data('groupNestDepth')) || 0 : 0;
        n.data('groupNestDepth', pd + 1);
      }
    }

    if (!hasDbId || dbId == null) {
      this.bumpLayoutDirtyOrAutosave();
      return;
    }

    if (newEl == null) {
      if (curEl != null) {
        this.pendingMembershipByObjectId.set(dbId, { t: 'c' });
        this.bumpLayoutDirtyOrAutosave();
      }
      return;
    }
    const desiredDb = inner!.data('topologyObjectId') as number | undefined;
    if (desiredDb != null) {
      this.pendingMembershipByObjectId.set(dbId, { t: 'g', id: desiredDb });
    } else {
      this.pendingMembershipByObjectId.set(dbId, { t: 'd', el: newEl });
    }
    this.bumpLayoutDirtyOrAutosave();
  }

  private wireCytoscapeLayoutEvents(): void {
    const cy = this.cy;
    if (!cy) return;
    cy.on('grab', 'node', (evt: EventObject) => this.onGroupDragGrab(evt));
    cy.on('drag', 'node', (evt: EventObject) => this.onGroupDragDuring(evt));
    cy.on('dragfreeon', 'node', (evt: EventObject) => this.onNodeDragFree(evt));
  }

  private onGroupDragGrab(evt: EventObject): void {
    const cy = this.cy;
    if (!cy) return;
    const node = evt.target;
    if (!node.isNode()) return;
    if (node.data('pendingCreate') === true) {
      this.groupDragFollowState = null;
      return;
    }
    const grabbed = cy.nodes(':grabbed');
    const anyGroupGrabbed = grabbed.filter('[kind = "GROUP"]').length > 0;
    if (node.data('kind') !== 'GROUP') {
      if (!anyGroupGrabbed) this.groupDragFollowState = null;
      return;
    }
    const groupElId = node.id();
    const descendants = this.collectGroupDescendantNodes(cy, groupElId);
    const descendantPos = new Map<string, { x: number; y: number }>();
    for (const d of descendants) {
      if (d.selected()) continue;
      const p = d.position();
      descendantPos.set(d.id(), { x: p.x, y: p.y });
    }
    const o = node.position();
    this.groupDragFollowState = {
      groupElId,
      origin: { x: o.x, y: o.y },
      descendantPos,
    };
  }

  private onGroupDragDuring(_evt: EventObject): void {
    const st = this.groupDragFollowState;
    if (!st) return;
    const cy = this.cy;
    if (!cy) return;
    const groupNode = cy.getElementById(st.groupElId);
    if (groupNode.empty() || !groupNode.isNode()) return;
    const pos = groupNode.position();
    const dx = pos.x - st.origin.x;
    const dy = pos.y - st.origin.y;
    for (const [id, p0] of st.descendantPos) {
      const ele = cy.getElementById(id);
      if (!ele.empty() && ele.isNode()) {
        ele.position({ x: p0.x + dx, y: p0.y + dy });
      }
    }
  }

  private clearGroupDragFollowIfNothingGrabbed(): void {
    const cy = this.cy;
    if (!cy?.nodes(':grabbed').length) {
      this.groupDragFollowState = null;
    }
  }

  private onNodeDragFree(evt: EventObject): void {
    const node = evt.target;
    if (!node.isNode()) return;
    const pending = node.data('pendingCreate') === true;
    this.reconcileGroupMembershipAfterDrag(node);
    if (pending) {
      this.layoutDirty.set(true);
      this.clearGroupDragFollowIfNothingGrabbed();
      this.resetCyContainerCursor();
      return;
    }
    const kind = node.data('kind') as string | undefined;
    const dbId = node.data('topologyObjectId') as number | undefined;
    if (dbId == null) {
      this.clearGroupDragFollowIfNothingGrabbed();
      this.resetCyContainerCursor();
      return;
    }
    if (kind === 'NODE') {
      const pos = node.position();
      this.pendingPositionUpdates.set(dbId, { x: pos.x, y: pos.y });
      if (this.isAutosaveForCurrentTopology()) {
        this.scheduleAutosaveLayout();
      } else {
        this.layoutDirty.set(true);
      }
      this.clearGroupDragFollowIfNothingGrabbed();
      this.resetCyContainerCursor();
      return;
    }
    if (kind === 'GROUP') {
      const pos = node.position();
      const w = Number(node.data('groupWidth')) || 280;
      const h = Number(node.data('groupHeight')) || 200;
      this.pendingGroupLayoutUpdates.set(dbId, { cx: pos.x, cy: pos.y, w, h });
      const cy = this.cy;
      if (cy) {
        for (const d of this.collectGroupDescendantNodes(cy, node.id())) {
          const childDbId = d.data('topologyObjectId') as number | undefined;
          if (childDbId == null) continue;
          const dp = d.position();
          const dk = d.data('kind') as string | undefined;
          if (dk === 'NODE') {
            this.pendingPositionUpdates.set(childDbId, { x: dp.x, y: dp.y });
          } else if (dk === 'GROUP') {
            const dw = Number(d.data('groupWidth')) || 280;
            const dh = Number(d.data('groupHeight')) || 200;
            this.pendingGroupLayoutUpdates.set(childDbId, { cx: dp.x, cy: dp.y, w: dw, h: dh });
          }
        }
      }
      if (this.isAutosaveForCurrentTopology()) {
        this.scheduleAutosaveLayout();
      } else {
        this.layoutDirty.set(true);
      }
    }
    this.clearGroupDragFollowIfNothingGrabbed();
    this.resetCyContainerCursor();
  }

  private isAutosaveForCurrentTopology(): boolean {
    const id = this.selectedTopologyId();
    if (id == null) return false;
    return this.topologyList().find((r) => r.id === id)?.autosave === true;
  }

  private scheduleAutosaveLayout(): void {
    if (!this.isAutosaveForCurrentTopology()) return;
    if (this.autosaveLayoutTimer != null) {
      clearTimeout(this.autosaveLayoutTimer);
    }
    this.autosaveLayoutTimer = setTimeout(() => {
      this.autosaveLayoutTimer = null;
      this.flushLayoutToServer({ silent: true });
    }, 450);
  }

  private cancelAutosaveLayoutTimer(): void {
    if (this.autosaveLayoutTimer != null) {
      clearTimeout(this.autosaveLayoutTimer);
      this.autosaveLayoutTimer = null;
    }
  }

  private resetLocalLayoutState(): void {
    this.cancelAutosaveLayoutTimer();
    this.pendingPositionUpdates.clear();
    this.pendingGroupLayoutUpdates.clear();
    this.pendingMembershipByObjectId.clear();
    this.layoutDirty.set(false);
  }

  private collectPendingCreatesFromGraph(): TopologyObjectCreatePayload[] {
    const cy = this.cy;
    if (!cy) return [];
    const out: TopologyObjectCreatePayload[] = [];
    cy.nodes('[?pendingCreate]').forEach((n) => {
      if (!n.isNode()) return;
      const pos = n.position();
      const kind = (n.data('kind') as string) ?? 'NODE';
      const layerIdRaw = n.data('layerId') as number | null | undefined;
      const layerId = layerIdRaw != null && Number.isFinite(layerIdRaw) ? layerIdRaw : this.currentLayerParentId();
      if (kind === 'GROUP') {
        const name = (n.data('label') as string)?.trim() || 'Новая группа';
        const gForbidden = this.forbiddenParentGroupElementIds(n as NodeSingular);
        let gGroupId: number | undefined;
        let gParentElementId: string | undefined;
        const gMem = n.data('membershipGroupElId') as string | undefined;
        if (gMem != null && gMem.length > 0) {
          const gEl = cy.getElementById(gMem);
          if (!gEl.empty() && gEl.isNode() && gEl.data('kind') === 'GROUP') {
            const gdb = gEl.data('topologyObjectId') as number | undefined;
            if (gdb != null && Number.isFinite(gdb)) gGroupId = gdb;
            else gParentElementId = gMem;
          }
        }
        if (gGroupId == null && gParentElementId == null) {
          const gInner = this.findInnermostSavedOrPendingGroupAt(pos.x, pos.y, gForbidden);
          if (gInner != null && gInner.data('kind') === 'GROUP') {
            const gdb = gInner.data('topologyObjectId') as number | undefined;
            if (gdb != null) gGroupId = gdb;
            else gParentElementId = gInner.id();
          }
        }
        const gFrameBorder = n.data('groupFrameBorderColor') as string | undefined;
        out.push({
          kind: 'GROUP',
          name,
          elementId: n.id(),
          ...(layerId != null ? { layerId } : {}),
          positionX: pos.x,
          positionY: pos.y,
          frameWidth: Number(n.data('groupWidth')) || 280,
          frameHeight: Number(n.data('groupHeight')) || 200,
          ...(gFrameBorder != null && gFrameBorder.length > 0 ? { frameBorderColor: gFrameBorder } : {}),
          ...(gGroupId != null ? { groupId: gGroupId } : {}),
          ...(gParentElementId != null ? { parentElementId: gParentElementId } : {}),
        });
        return;
      }
      const forbidden = this.forbiddenParentGroupElementIds(n as NodeSingular);
      let groupId: number | undefined;
      let parentElementId: string | undefined;
      const memEl = n.data('membershipGroupElId') as string | undefined;
      if (memEl != null && memEl.length > 0) {
        const gEl = cy.getElementById(memEl);
        if (!gEl.empty() && gEl.isNode() && gEl.data('kind') === 'GROUP') {
          const gdb = gEl.data('topologyObjectId') as number | undefined;
          if (gdb != null && Number.isFinite(gdb)) groupId = gdb;
          else parentElementId = memEl;
        }
      }
      if (groupId == null && parentElementId == null) {
        const inner = this.findInnermostSavedOrPendingGroupAt(pos.x, pos.y, forbidden);
        if (inner != null && inner.data('kind') === 'GROUP') {
          const gdb = inner.data('topologyObjectId') as number | undefined;
          if (gdb != null) groupId = gdb;
          else parentElementId = inner.id();
        }
      }
      const nodeName = (n.data('label') as string)?.trim() || 'Новый узел';
      const nodeKind = (n.data('nodeKind') as TopologyNodeKind | undefined) ?? 'RACK';
      const deviceRaw = n.data('deviceId') as number | undefined;
      out.push({
        kind: 'NODE',
        name: nodeName,
        nodeKind,
        elementId: n.id(),
        ...(layerId != null ? { layerId } : {}),
        positionX: pos.x,
        positionY: pos.y,
        ...(deviceRaw != null && Number.isFinite(deviceRaw) ? { deviceId: deviceRaw } : {}),
        ...(groupId != null ? { groupId } : {}),
        ...(parentElementId != null ? { parentElementId } : {}),
      });
    });
    return out;
  }

  private flushLayoutToServer(options: { silent?: boolean } = {}): void {
    const topologyId = this.selectedTopologyId();
    if (topologyId == null || this.layoutSaving()) return;

    const pendingCreates = this.collectPendingCreatesFromGraph();
    const positionEntries = [...this.pendingPositionUpdates.entries()];
    const groupLayoutEntries = [...this.pendingGroupLayoutUpdates.entries()];
    const membershipSnapshot = new Map(this.pendingMembershipByObjectId);
    if (
      pendingCreates.length === 0 &&
      positionEntries.length === 0 &&
      groupLayoutEntries.length === 0 &&
      membershipSnapshot.size === 0
    ) {
      return;
    }

    this.layoutSaving.set(true);
    void this.runFlushLayout(
      topologyId,
      pendingCreates,
      positionEntries,
      groupLayoutEntries,
      membershipSnapshot,
      options,
    );
  }

  private createPayloadForApi(p: TopologyObjectCreatePayload): TopologyObjectCreatePayload {
    const { parentElementId: _pe, ...rest } = p as TopologyObjectCreatePayload & { parentElementId?: string };
    return rest;
  }

  private async runFlushLayout(
    topologyId: number,
    pendingCreates: TopologyObjectCreatePayload[],
    positionEntries: [number, { x: number; y: number }][],
    groupLayoutEntries: [number, { cx: number; cy: number; w: number; h: number }][],
    membershipSnapshot: Map<number, TopologyPendingMembership>,
    options: { silent?: boolean },
  ): Promise<boolean> {
    try {
      const elToId = new Map<string, number>();
      const createdRecords: TopologyObjectRecord[] = [];
      const groups = pendingCreates.filter((c) => c.kind === 'GROUP');
      const nodes = pendingCreates.filter((c) => c.kind === 'NODE');
      for (const p of groups) {
        const body: TopologyObjectCreatePayload = { ...p };
        if (p.parentElementId) {
          const gid = elToId.get(p.parentElementId);
          if (gid != null) body.groupId = gid;
        }
        delete (body as { parentElementId?: string }).parentElementId;
        const rec = await firstValueFrom(
          this.topologyService.createObject(topologyId, this.createPayloadForApi(body)),
        );
        elToId.set(rec.elementId, rec.id);
        createdRecords.push(rec);
      }
      for (const p of nodes) {
        const body: TopologyObjectCreatePayload = { ...p };
        if (p.parentElementId) {
          const gid = elToId.get(p.parentElementId);
          if (gid != null) body.groupId = gid;
        }
        delete (body as { parentElementId?: string }).parentElementId;
        const rec = await firstValueFrom(
          this.topologyService.createObject(topologyId, this.createPayloadForApi(body)),
        );
        elToId.set(rec.elementId, rec.id);
        createdRecords.push(rec);
      }

      const resolvedMembership = new Map(membershipSnapshot);
      for (const [objId, m] of [...resolvedMembership.entries()]) {
        if (m.t === 'd') {
          const gid = elToId.get(m.el);
          if (gid != null) resolvedMembership.set(objId, { t: 'g', id: gid });
        }
      }
      for (const [objId, m] of resolvedMembership.entries()) {
        if (m.t === 'g') {
          await firstValueFrom(this.topologyService.updateObject(topologyId, objId, { groupId: m.id }));
        } else if (m.t === 'c') {
          await firstValueFrom(this.topologyService.updateObject(topologyId, objId, { clearGroup: true }));
        }
      }
      for (const id of membershipSnapshot.keys()) {
        this.pendingMembershipByObjectId.delete(id);
      }

      const layoutItems: TopologyLayoutPatchItem[] = [];
      for (const [objId, pos] of positionEntries) {
        layoutItems.push({ objectId: objId, positionX: pos.x, positionY: pos.y });
      }
      for (const [objId, layout] of groupLayoutEntries) {
        layoutItems.push({
          objectId: objId,
          positionX: layout.cx,
          positionY: layout.cy,
          frameWidth: layout.w,
          frameHeight: layout.h,
        });
      }
      if (layoutItems.length > 0) {
        await firstValueFrom(this.topologyService.applyLayoutBatch(topologyId, { items: layoutItems }));
      }
      this.pendingPositionUpdates.clear();
      this.pendingGroupLayoutUpdates.clear();
      this.layoutDirty.set(false);
      if (!options.silent) {
        this.notifier.success('Изменения сохранены.');
      }
      if (createdRecords.length > 0) {
        this.applySavedPendingCreatesToCy(createdRecords);
        const needsGroupLayerBlobs = createdRecords.some(
          (r) => r.kind === 'GROUP' && r.layerBackgroundPresent === true,
        );
        if (needsGroupLayerBlobs) {
          const blobsLayerId = this.currentLayerParentId();
          this.topologyService
            .listObjects(topologyId, blobsLayerId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: (objs) => {
                if (this.selectedTopologyId() !== topologyId || this.currentLayerParentId() !== blobsLayerId) {
                  return;
                }
                this.loadGroupLayerBackgroundBlobs(topologyId, objs);
                this.applyGroupLayerBackgroundStyles();
                this.cdr.markForCheck();
              },
              error: () =>
                this.notifier.warn('Не удалось обновить фоны групп после сохранения.'),
            });
        }
      }
      return true;
    } catch {
      this.notifier.error('Не удалось сохранить изменения на сервере.');
      return false;
    } finally {
      this.layoutSaving.set(false);
    }
  }

  protected saveTopologyLayout(): void {
    if (!this.showSaveLayoutButton()) return;
    this.flushLayoutToServer({ silent: false });
  }

  private onCreateTopology(): void {
    const name = `Новая топология ${new Date().toLocaleString('ru-RU')}`;
    this.topologyService
      .create({
        name,
        visibility: 'PRIVATE',
        sharedUserIds: [],
        document: {},
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (t) => {
          this.notifier.success(`Топология «${t.name}» создана.`);
          this.selectedTopologyId.set(t.id);
          this.refreshTopologyList();
        },
        error: () => this.notifier.error('Не удалось создать топологию.'),
      });
  }

  private onToggleAutosave(): void {
    const id = this.selectedTopologyId();
    const row = id != null ? this.topologyList().find((r) => r.id === id) : undefined;
    if (id == null || row == null) {
      this.notifier.warn('Сначала выберите топологию.');
      return;
    }
    if (!this.canEditCurrentTopology()) {
      this.notifier.warn('Изменять эту топологию вам недоступно.');
      return;
    }
    const next = !row.autosave;
    this.topologyService
      .update(id, {
        name: row.name,
        visibility: row.visibility,
        autosave: next,
        autoCenterOnResize: row.autoCenterOnResize ?? true,
        sharedUserIds: [...row.sharedUserIds],
        document: row.document ?? {},
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.topologyList.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
          this.notifier.success(
            next ? 'Автосохранение включено.' : 'Автосохранение выключено.',
          );
          if (
            !next &&
            (this.pendingPositionUpdates.size > 0 ||
              this.pendingGroupLayoutUpdates.size > 0 ||
              this.pendingMembershipByObjectId.size > 0)
          ) {
            this.layoutDirty.set(true);
          }
          if (next) {
            this.cancelAutosaveLayoutTimer();
            const hasPending =
              this.layoutDirty() ||
              this.pendingPositionUpdates.size > 0 ||
              this.pendingGroupLayoutUpdates.size > 0 ||
              this.pendingMembershipByObjectId.size > 0 ||
              this.collectPendingCreatesFromGraph().length > 0;
            if (hasPending) {
              this.flushLayoutToServer({ silent: true });
            }
          }
        },
        error: () => this.notifier.error('Не удалось сохранить настройку.'),
      });
  }

  private onToggleAutoCenterOnResize(): void {
    const id = this.selectedTopologyId();
    const row = id != null ? this.topologyList().find((r) => r.id === id) : undefined;
    if (id == null || row == null) {
      this.notifier.warn('Сначала выберите топологию.');
      return;
    }
    if (!this.canEditCurrentTopology()) {
      this.notifier.warn('Изменять эту топологию вам недоступно.');
      return;
    }
    const cur = row.autoCenterOnResize !== false;
    const next = !cur;
    this.topologyService
      .update(id, {
        name: row.name,
        visibility: row.visibility,
        autosave: row.autosave,
        autoCenterOnResize: next,
        sharedUserIds: [...row.sharedUserIds],
        document: row.document ?? {},
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.topologyList.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
          this.notifier.success(
            next ? 'Автоцентровка включена.' : 'Автоцентровка выключена.',
          );
          if (next) {
            this.scheduleCyContainerResizeAndMaybeFit({ source: 'resize' });
          }
        },
        error: () => this.notifier.error('Не удалось сохранить настройку.'),
      });
  }

  private onToggleDefaultTopology(): void {
    const id = this.selectedTopologyId();
    if (id == null) {
      this.notifier.warn('Сначала выберите топологию.');
      return;
    }
    const session = this.auth.authSession();
    const cur = session?.defaultTopologyId ?? null;
    const next = cur === id ? null : id;
    this.defaultTopologySaving.set(true);
    this.auth
      .updateDefaultTopologyPreference(next)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          this.defaultTopologySaving.set(false);
          if (session) {
            this.auth.updateSession({ ...session, defaultTopologyId: r.defaultTopologyId });
          }
          this.syncTopologyPickFromSelection();
          this.notifier.success(
            r.defaultTopologyId != null
              ? 'Эта топология будет открываться по умолчанию.'
              : 'Топология по умолчанию снята.',
          );
        },
        error: () => {
          this.defaultTopologySaving.set(false);
          this.notifier.error('Не удалось сохранить настройку.');
        },
      });
  }

  private onAddObject(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    if (!this.canEditCurrentTopology()) {
      this.notifier.warn('Добавлять объекты в эту топологию вам недоступно.');
      return;
    }
    if (this.cy == null && this.selectedTopologyId() != null) {
      this.notifier.warn('Дождитесь загрузки графа.');
      return;
    }
    this.addNodeAtModelPosition(120 + Math.random() * 80, 120 + Math.random() * 80);
  }

  private onDeleteTopology(): void {
    const id = this.selectedTopologyId();
    if (id == null) {
      this.notifier.warn('Нет выбранной топологии.');
      return;
    }
    if (!this.canEditCurrentTopology()) {
      this.notifier.warn('Удалять эту топологию вам недоступно.');
      return;
    }
    const row = this.topologyList().find((r) => r.id === id);
    this.confirm.confirm({
      message: `Удалить топологию «${row?.name ?? id}» и все её объекты?`,
      header: 'Удаление топологии',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.topologyService
          .delete(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe({
            next: () => {
              this.notifier.success('Топология удалена.');
              const session = this.auth.authSession();
              if (session?.defaultTopologyId === id) {
                this.auth.updateSession({ ...session, defaultTopologyId: null });
              }
              this.resetLocalLayoutState();
              this.persistCyViewportNowForCurrentLayer();
              this.layerStack.set([]);
              this.selectedTopologyId.set(null);
              this.topologyPick.set(null);
              this.refreshTopologyList();
            },
            error: () => this.notifier.error('Не удалось удалить топологию.'),
          });
      },
    });
  }

  protected currentAutosaveHint(): string {
    const id = this.selectedTopologyId();
    if (id == null) return '';
    const row = this.topologyList().find((r) => r.id === id);
    if (row == null) return '';
    return row.autosave ? 'Автосохранение включено' : 'Автосохранение выключено';
  }

  protected currentAutoCenterHint(): string {
    const id = this.selectedTopologyId();
    if (id == null) return '';
    const row = this.topologyList().find((r) => r.id === id);
    if (row == null) return '';
    return row.autoCenterOnResize !== false ? 'Автоцентровка включена' : 'Автоцентровка выключена';
  }
}
