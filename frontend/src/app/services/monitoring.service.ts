import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, forkJoin, map, of, tap } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import { AuthService } from '../auth.service';
import { NotifierService } from '../notifier.service';
import {
  ActionResult,
  BackupComparisonResult,
  DeviceBackupConfig,
  DeviceBackupSnapshot,
  MonitoredDeviceMeta,
  MonitoringDiscoveryInstance,
  MonitoringDeviceFilter,
  MonitoringDeviceListItem,
  MonitoringDevicePage,
  DeviceMonitoringDetails,
  DeviceLiveTelemetryState,
  MonitoringDeviceItem,
  MonitoringDeviceItemSelection,
  MonitoringItemState,
  MonitoringItemStatePage,
  DevicePortConfig,
  DevicePortConfigApi,
  DeviceScanResult,
  MonitoringEventFilter,
  MonitoringEventLevelSummary,
  MonitoringEventPage,
  MonitoringHealthStatus,
  MonitoringHostStatusFilter,
  MonitoringMetricsBatchRequest,
  MonitoringMetricsBatchSeries,
  MonitoringTemplateDetails,
  MonitoringTemplateImportPreview,
  MonitoringTemplateSummary,
  MonitoringTemplateUpdateRequest,
} from '../models';
import type { DeviceMetricsHistoryResponseDto } from '../pages/monitoring-page/device-metrics-history.types';
import { collectChartMetricKeys } from '../utils/chart-metric-keys';
import { defaultDayMetricsRange } from '../utils/metrics-history-range';

/** Ответ бекенда: поле id называется monitoredDeviceId */
type BackendDevice = Omit<DeviceScanResult, 'id'> & { monitoredDeviceId: number | null };
type BackendMonitoringDevice = Omit<MonitoringDeviceListItem, 'id'> & { id: number };
type BackendMonitoringDevicePage = Omit<MonitoringDevicePage, 'content'> & { content: BackendMonitoringDevice[] };

@Injectable({ providedIn: 'root' })
export class MonitoringService {
  private static readonly LIVE_REFRESH_INTERVAL_MS = 10_000;
  private static readonly LIVE_AUTO_STOP_MS = 10 * 60_000;

  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notify = inject(NotifierService);
  private monitoredDevicesLoadSeq = 0;

  readonly monitoredDevicesPage = signal<MonitoringDevicePage | null>(null);
  readonly monitoredDevices = computed<MonitoringDeviceListItem[]>(
    () => this.monitoredDevicesPage()?.content ?? [],
  );
  readonly devicesLoading = signal(false);
  /** GET /api/monitoring/devices/{id} в полёте (карточка по прямой ссылке без кэша). */
  private readonly monitoredDeviceDetailFetchingIds = signal(new Set<string>());
  readonly monitoredDeviceIndex = signal<Record<string, DeviceScanResult>>({});
  readonly monitoringStatuses = signal<Record<string, boolean>>({});
  readonly monitoringTemplates = signal<MonitoringTemplateSummary[]>([]);
  readonly templatesLoading = signal(false);
  readonly templatesMutationLoading = signal(false);
  readonly templatesSearch = signal('');
  readonly selectedTemplateDetails = signal<MonitoringTemplateDetails | null>(null);
  readonly selectedTemplateDetailsLoading = signal(false);
  readonly templateImportPreview = signal<MonitoringTemplateImportPreview | null>(null);
  readonly selectedMonitoringTemplateId = signal('');
  readonly selectedMonitoringTemplateIds = signal<string[]>([]);
  readonly monitoringTemplateAutoDetection = signal(false);
  readonly monitoringHostStatusFilter = signal<MonitoringHostStatusFilter>('ALL');
  /** Поиск по списку хостов (как на странице users — один инпут). */
  readonly devicesSearch = signal('');
  readonly deviceIpFilter = signal('');
  readonly deviceMacFilter = signal('');
  readonly deviceStatusFilter = signal('');
  readonly deviceTagFilter = signal<string[]>([]);
  readonly deviceHealthStatusFilter = signal<MonitoringHealthStatus | 'ALL'>('ALL');
  readonly monitoredDevicesSortField = signal('ip');
  readonly monitoredDevicesSortOrder = signal<1 | -1>(1);
  readonly monitoredDevicesPageSize = signal(15);
  readonly monitoringDetails = signal<Record<string, DeviceMonitoringDetails>>({});
  readonly liveTelemetryState = signal<Record<string, DeviceLiveTelemetryState>>({});
  readonly monitoringPortConfigs = signal<Record<string, DevicePortConfig[]>>({});
  readonly backupSnapshots = signal<Record<string, DeviceBackupSnapshot>>({});
  readonly backupSnapshotsLoading = signal<Record<string, boolean>>({});
  readonly monitoredDeviceMeta = signal<Record<string, MonitoredDeviceMeta>>({});
  readonly deviceItemStatePage = signal<Record<string, MonitoringItemStatePage>>({});
  readonly deviceItemStateLoading = signal<Record<string, boolean>>({});
  /** Ключи метрик с панелями графиков (период «День»), для ссылок на вкладке снимка. */
  readonly deviceChartMetricKeys = signal<Record<string, Set<string>>>({});
  private readonly chartMetricKeysLoading = new Set<string>();
  readonly deviceItemsCatalog = signal<Record<string, MonitoringDeviceItem[]>>({});
  readonly deviceDiscoveryStates = signal<Record<string, MonitoringDiscoveryInstance[]>>({});
  /** События по устройству (вкладка карточки), ключ — id устройства. */
  readonly deviceScopedEventsPage = signal<Record<string, MonitoringEventPage | null>>({});
  readonly deviceScopedEventsLoading = signal<Record<string, boolean>>({});
  /** Число активных (OPEN) событий по устройству — для бейджа вкладки. */
  readonly deviceOpenEventCount = signal<Record<string, number>>({});
  readonly eventsPage = signal<MonitoringEventPage | null>(null);
  /** Агрегаты по уровням порога для текущих фильтров журнала событий. */
  readonly eventsLevelSummary = signal<MonitoringEventLevelSummary | null>(null);
  readonly eventsLoading = signal(false);
  private readonly liveTelemetryPollTimers = new Map<string, number>();
  private readonly liveTelemetryStopTimers = new Map<string, number>();

  readonly monitoringStatusSummary = computed(() => {
    const page = this.monitoredDevicesPage();
    const available = page?.availableCount ?? 0;
    const unavailable = page?.unavailableCount ?? 0;
    const unknown = page?.unknownCount ?? 0;
    return [
      {
        key: 'AVAILABLE' as MonitoringHostStatusFilter,
        label: 'Доступен',
        count: available,
        tone: 'green' as const,
      },
      {
        key: 'UNAVAILABLE' as MonitoringHostStatusFilter,
        label: 'Недоступен',
        count: unavailable,
        tone: 'red' as const,
      },
      {
        key: 'UNKNOWN' as MonitoringHostStatusFilter,
        label: 'Неизвестно',
        count: unknown,
        tone: 'gray' as const,
      },
      {
        key: 'ALL' as MonitoringHostStatusFilter,
        label: 'Всего',
        count: available + unavailable + unknown,
        tone: 'slate' as const,
      },
    ];
  });

  readonly monitoredDevicesTableFirst = computed(() => {
    const page = this.monitoredDevicesPage();
    if (!page) {
      return 0;
    }
    return page.number * page.size;
  });

  /** Есть ли ненулевые фильтры списка мониторинга (панель + плитка доступности). */
  readonly hasActiveMonitoredListFilters = computed(() => {
    if (this.devicesSearch().trim()) return true;
    if (this.deviceIpFilter().trim()) return true;
    if (this.deviceMacFilter().trim()) return true;
    if (this.deviceStatusFilter().trim()) return true;
    if (this.deviceHealthStatusFilter() !== 'ALL') return true;
    if (this.monitoringHostStatusFilter() !== 'ALL') return true;
    return false;
  });

  readonly filteredMonitoringTemplates = computed(() => {
    const query = this.templatesSearch().trim().toLowerCase();
    return this.monitoringTemplates().filter((template) => {
      if (!query) {
        return true;
      }
      return [
        template.id,
        template.name,
        template.description,
        template.uploadedByDisplayName ?? '',
        template.uploadedBy ?? '',
        template.vendor ?? '',
        template.model ?? '',
        template.modelRegex ?? '',
        template.firmware ?? '',
        template.source,
      ]
        .join(' ')
        .toLowerCase()
        .includes(query);
    });
  });

  readonly systemMonitoringTemplatesCount = computed(
    () => this.monitoringTemplates().filter((template) => template.source === 'SYSTEM').length,
  );

  readonly uploadedMonitoringTemplatesCount = computed(
    () => this.monitoringTemplates().filter((template) => template.source === 'UPLOADED').length,
  );

  constructor() {
    if (this.auth.isAuthenticated()) {
      this.loadMonitoringTemplates();
    }

    effect(() => {
      if (!this.auth.isAuthenticated()) {
        return;
      }
      if (this.monitoringTemplates().length > 0 || this.templatesLoading()) {
        return;
      }
      this.loadMonitoringTemplates();
    });

    this.destroyRef.onDestroy(() => {
      this.stopAllLiveTelemetry();
    });
  }

  isMonitoringEnabled(deviceOrIp: DeviceScanResult | string): boolean {
    if (typeof deviceOrIp === 'string') {
      return this.monitoringStatuses()[deviceOrIp] ?? false;
    }
    return !!deviceOrIp.id || this.monitoringStatuses()[deviceOrIp.ip] === true;
  }

  monitoringStatusLabel(deviceOrIp: DeviceScanResult | string): string {
    return this.isMonitoringEnabled(deviceOrIp) ? 'На мониторинге' : 'Не поставлено';
  }

  monitoringStatusClass(deviceOrIp: DeviceScanResult | string): string {
    return this.isMonitoringEnabled(deviceOrIp)
      ? 'status-monitoring-enabled'
      : 'status-monitoring-disabled';
  }

  hostAvailabilityLabel(device: DeviceScanResult): string {
    if (device.status === 'Включено') return 'Доступен';
    if (device.status === 'Недоступно') return 'Недоступен';
    return 'Неизвестно';
  }

  hostAvailabilityClass(device: DeviceScanResult): string {
    if (device.status === 'Включено') return 'host-availability-up';
    if (device.status === 'Недоступно') return 'host-availability-down';
    return 'host-availability-unknown';
  }

  deviceSeries(device: DeviceScanResult): string {
    const model = device.model?.trim();
    if (model && model !== '-') {
      const match = model.match(/^[A-Za-z]+[\d-]*/);
      return match?.[0] ?? model;
    }
    return device.vendor?.trim() || '-';
  }

  protocolAvailabilityChipClass(
    device: DeviceScanResult,
    protocol: 'ICMP' | 'SSH' | 'SNMP',
  ): string {
    const entry = device.availability.find((a) => a.label.toUpperCase() === protocol);
    if (!entry) return 'protocol-chip-unknown';
    return entry.active ? 'protocol-chip-up' : 'protocol-chip-down';
  }

  toggleMonitoringStatusFilter(filter: MonitoringHostStatusFilter): void {
    const next = this.monitoringHostStatusFilter() === filter ? 'ALL' : filter;
    this.monitoringHostStatusFilter.set(next);
    this.loadMonitoredDevices(0, this.currentMonitoredDevicesPageSize());
  }

  isMonitoringStatusFilterActive(filter: MonitoringHostStatusFilter): boolean {
    return this.monitoringHostStatusFilter() === filter;
  }

  getDetails(deviceId: string): DeviceMonitoringDetails | null {
    return this.monitoringDetails()[deviceId] ?? null;
  }

  /** Есть ли закэшированный ответ (или fallback после ошибки) для карточки — без этого не показываем donut’ы, чтобы не мигать нулями. */
  hasMonitoringDetailsEntry(deviceId: string): boolean {
    return Object.hasOwn(this.monitoringDetails(), deviceId);
  }

  getDetailsOrFallback(device: DeviceScanResult): DeviceMonitoringDetails {
    return this.monitoringDetails()[device.id] ?? this.fallbackMonitoringDetails(device);
  }

  deviceLiveTelemetryState(deviceId: string): DeviceLiveTelemetryState {
    return this.liveTelemetryState()[deviceId] ?? this.defaultLiveTelemetryState();
  }

  startDeviceLiveTelemetry(device: DeviceScanResult): void {
    const id = device.id;
    if (!id) {
      return;
    }
    this.stopDeviceLiveTelemetry(id);

    const now = Date.now();
    const expiresAt = new Date(now + MonitoringService.LIVE_AUTO_STOP_MS).toISOString();
    this.liveTelemetryState.update((current) => ({
      ...current,
      [id]: {
        active: true,
        loading: false,
        startedAt: new Date(now).toISOString(),
        expiresAt,
        nextRefreshAt: new Date(now + MonitoringService.LIVE_REFRESH_INTERVAL_MS).toISOString(),
        lastError: null,
      },
    }));

    this.refreshMonitoringDetails(device, true);
    const pollTimer = window.setInterval(
      () => this.refreshMonitoringDetails(device, true),
      MonitoringService.LIVE_REFRESH_INTERVAL_MS,
    );
    const stopTimer = window.setTimeout(
      () => this.stopDeviceLiveTelemetry(id),
      MonitoringService.LIVE_AUTO_STOP_MS,
    );
    this.liveTelemetryPollTimers.set(id, pollTimer);
    this.liveTelemetryStopTimers.set(id, stopTimer);
  }

  stopDeviceLiveTelemetry(deviceId: string): void {
    if (!deviceId) {
      return;
    }
    const pollTimer = this.liveTelemetryPollTimers.get(deviceId);
    if (pollTimer != null) {
      window.clearInterval(pollTimer);
      this.liveTelemetryPollTimers.delete(deviceId);
    }
    const stopTimer = this.liveTelemetryStopTimers.get(deviceId);
    if (stopTimer != null) {
      window.clearTimeout(stopTimer);
      this.liveTelemetryStopTimers.delete(deviceId);
    }
    this.liveTelemetryState.update((current) => ({
      ...current,
      [deviceId]: {
        ...this.defaultLiveTelemetryState(),
      },
    }));
  }

  private fallbackMonitoringDetails(device: DeviceScanResult): DeviceMonitoringDetails {
    return {
      cpu: {
        current: null,
        average: null,
        peak: null,
        currentItemName: null,
        averageItemName: null,
        peakItemName: null,
      },
      ramUsedPercent: null,
      romUsedPercent: null,
      uptime: '-',
      description: device.name !== '-' ? device.name : `${device.vendor} ${device.model}`.trim(),
      adminContact: '-',
      hardwareVersion: '-',
      location: device.group !== '-' ? device.group : '-',
      addedAt: '-',
      bootVersion: device.firmwareVersion !== '-' ? device.firmwareVersion : '-',
      collectedAt: null,
      source: 'FALLBACK',
      liveMode: false,
    };
  }

  backupSnapshot(deviceId: string): DeviceBackupSnapshot | null {
    return this.backupSnapshots()[deviceId] ?? null;
  }

  portConfigs(deviceId: string): DevicePortConfig[] {
    return this.monitoringPortConfigs()[deviceId] ?? [];
  }

  applyMonitoredDevices(devices: DeviceScanResult[]): void {
    this.cacheDevices(devices);
    this.monitoringStatuses.set(Object.fromEntries(devices.map((d) => [d.ip, true])));
    if (this.auth.isAuthenticated()) {
      this.loadMonitoredDevices();
    }
  }

  getMonitoredDevice(deviceId: string): DeviceScanResult | null {
    return this.monitoredDeviceIndex()[deviceId] ?? null;
  }

  isMonitoredDeviceDetailFetching(deviceId: string): boolean {
    return !!deviceId && this.monitoredDeviceDetailFetchingIds().has(deviceId);
  }

  ensureMonitoredDeviceLoaded(deviceId: string): void {
    if (!deviceId) {
      return;
    }
    if (this.monitoredDeviceDetailFetchingIds().has(deviceId)) {
      return;
    }
    if (this.getMonitoredDevice(deviceId)) {
      return;
    }
    this.monitoredDeviceDetailFetchingIds.update((s) => new Set(s).add(deviceId));
    this.http
      .get<BackendDevice>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}`)
      .pipe(
        map(this.mapDevice),
        finalize(() => {
          this.monitoredDeviceDetailFetchingIds.update((s) => {
            const next = new Set(s);
            next.delete(deviceId);
            return next;
          });
        }),
      )
      .subscribe({
        next: (device) => this.cacheDevices([device]),
        error: () => {},
      });
  }

  monitoredDevicesFilterState(): MonitoringDeviceFilter {
    return {
      q: this.devicesSearch(),
      ip: this.deviceIpFilter(),
      macAddress: this.deviceMacFilter(),
      status: this.deviceStatusFilter(),
      tag: this.deviceTagFilter().join(','),
      healthStatus: this.deviceHealthStatusFilter(),
      availability: this.monitoringHostStatusFilter(),
    };
  }

  resetMonitoredDeviceFilters(): void {
    this.devicesSearch.set('');
    this.deviceIpFilter.set('');
    this.deviceMacFilter.set('');
    this.deviceStatusFilter.set('');
    this.deviceTagFilter.set([]);
    this.deviceHealthStatusFilter.set('ALL');
    this.monitoringHostStatusFilter.set('ALL');
    this.monitoredDevicesSortField.set('ip');
    this.monitoredDevicesSortOrder.set(1);
    this.loadMonitoredDevices(0, this.currentMonitoredDevicesPageSize(), 'ip', 1);
  }

  clearSessionState(): void {
    this.stopAllLiveTelemetry();
    this.monitoredDevicesPage.set(null);
    this.monitoredDeviceIndex.set({});
    this.monitoringStatuses.set({});
    this.monitoringTemplates.set([]);
    this.resetScanMonitoringTemplateSelection();
    this.monitoringPortConfigs.set({});
    this.monitoringDetails.set({});
    this.backupSnapshots.set({});
    this.backupSnapshotsLoading.set({});
    this.monitoredDeviceMeta.set({});
    this.deviceItemStatePage.set({});
    this.deviceItemStateLoading.set({});
    this.deviceItemsCatalog.set({});
    this.deviceDiscoveryStates.set({});
    this.liveTelemetryState.set({});
    this.selectedTemplateDetails.set(null);
    this.selectedTemplateDetailsLoading.set(false);
    this.templateImportPreview.set(null);
    this.deviceScopedEventsPage.set({});
    this.deviceScopedEventsLoading.set({});
    this.deviceOpenEventCount.set({});
    this.eventsPage.set(null);
    this.eventsLevelSummary.set(null);
    this.devicesLoading.set(false);
    this.eventsLoading.set(false);
  }

  loadMonitoredDevices(
    page: number = this.monitoredDevicesPage()?.number ?? 0,
    size: number = this.currentMonitoredDevicesPageSize(),
    sortField: string = this.monitoredDevicesSortField(),
    sortOrder: 1 | -1 = this.monitoredDevicesSortOrder(),
  ): void {
    const requestSeq = ++this.monitoredDevicesLoadSeq;
    const isInitialLoad = this.monitoredDevicesPage() == null;
    this.monitoredDevicesPageSize.set(size);
    this.monitoredDevicesSortField.set(sortField);
    this.monitoredDevicesSortOrder.set(sortOrder);
    if (isInitialLoad) {
      this.devicesLoading.set(true);
    }
    const params = this.buildMonitoredDeviceParams(page, size, sortField, sortOrder);
    this.http
      .get<BackendMonitoringDevicePage>(`${this.apiBaseUrl}/api/monitoring`, { params })
      .pipe(map((response) => this.mapMonitoringDevicePage(response)))
      .subscribe({
        next: (devicesPage) => {
          if (requestSeq !== this.monitoredDevicesLoadSeq) {
            return;
          }
          this.applyMonitoredDevicesPage(devicesPage);
          this.devicesLoading.set(false);
        },
        error: (err) => {
          if (requestSeq !== this.monitoredDevicesLoadSeq) {
            return;
          }
          if (err?.status === 401) {
            this.devicesLoading.set(false);
            this.auth.logout();
            return;
          }
          this.monitoredDevicesPage.set({
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: 0,
            size,
            first: true,
            last: true,
            availableCount: 0,
            unavailableCount: 0,
            unknownCount: 0,
          });
          this.devicesLoading.set(false);
        },
      });
  }

  private currentMonitoredDevicesPageSize(): number {
    return this.monitoredDevicesPage()?.size ?? this.monitoredDevicesPageSize();
  }

  private buildMonitoredDeviceParams(
    page: number,
    size: number,
    sortField: string,
    sortOrder: 1 | -1,
  ): Record<string, string> {
    const filter = this.monitoredDevicesFilterState();
    const params: Record<string, string> = {
      page: String(page),
      size: String(size),
      sortField,
      sortOrder: sortOrder === -1 ? 'desc' : 'asc',
    };
    const q = filter.q.trim();
    const ip = filter.ip.trim();
    const macAddress = filter.macAddress.trim();
    const status = filter.status.trim();
    const tag = filter.tag.trim();
    if (q) params['q'] = q;
    if (ip) params['ip'] = ip;
    if (macAddress) params['macAddress'] = macAddress;
    if (status) params['status'] = status;
    if (tag) params['tag'] = tag;
    if (filter.healthStatus !== 'ALL') params['healthStatus'] = filter.healthStatus;
    if (filter.availability !== 'ALL') params['availability'] = filter.availability;
    return params;
  }

  private applyMonitoredDevicesPage(page: MonitoringDevicePage): void {
    this.monitoredDevicesPage.set(page);
    this.cacheDevices(page.content);
    this.monitoringStatuses.update((current) => ({
      ...current,
      ...Object.fromEntries(page.content.map((device) => [device.ip, true])),
    }));
  }

  private cacheDevices(devices: DeviceScanResult[]): void {
    if (devices.length === 0) {
      return;
    }
    this.monitoredDeviceIndex.update((current) => ({
      ...current,
      ...Object.fromEntries(devices.filter((d) => d.id).map((device) => [device.id, device])),
    }));
  }

  private mapMonitoringDevicePage(raw: BackendMonitoringDevicePage): MonitoringDevicePage {
    return {
      ...raw,
      content: raw.content.map(this.mapMonitoringDevice),
    };
  }

  private defaultLiveTelemetryState(): DeviceLiveTelemetryState {
    return {
      active: false,
      loading: false,
      startedAt: null,
      expiresAt: null,
      nextRefreshAt: null,
      lastError: null,
    };
  }

  private stopAllLiveTelemetry(): void {
    for (const [deviceId] of this.liveTelemetryPollTimers) {
      this.stopDeviceLiveTelemetry(deviceId);
    }
    this.liveTelemetryPollTimers.clear();
    this.liveTelemetryStopTimers.clear();
  }

  private readonly mapMonitoringDevice = (
    raw: BackendMonitoringDevice,
  ): MonitoringDeviceListItem => ({
    id: String(raw.id),
    port: (raw as unknown as { snmpPort?: number | null }).snmpPort ?? null,
    hostName: raw.hostName,
    name: raw.name,
    serialNumber: raw.serialNumber,
    ip: raw.ip,
    domainName: raw.domainName ?? '-',
    macAddress: raw.macAddress,
    vendor: raw.vendor,
    model: raw.model,
    firmwareVersion: raw.firmwareVersion,
    pollingStatus: raw.pollingStatus,
    status: raw.status,
    healthStatus: raw.healthStatus,
    group: raw.group,
    tags: Array.isArray((raw as unknown as { tags?: unknown }).tags)
      ? ((raw as unknown as { tags: unknown[] }).tags.filter(
          (t) => typeof t === 'string',
        ) as string[])
      : [],
    availability: raw.availability,
  });

  loadMonitoringTemplates(): void {
    this.templatesLoading.set(true);
    this.http
      .get<MonitoringTemplateSummary[]>(`${this.apiBaseUrl}/api/monitoring/templates`)
      .subscribe({
        next: (templates) => {
          const sorted = [...templates].sort((a, b) => a.name.localeCompare(b.name));
          this.monitoringTemplates.set(sorted);
          this.templatesLoading.set(false);
          if (!sorted.some((template) => template.id === this.selectedMonitoringTemplateId())) {
            this.selectedMonitoringTemplateId.set(sorted[0]?.id ?? '');
          } else if (!this.selectedMonitoringTemplateId() && sorted.length > 0) {
            this.selectedMonitoringTemplateId.set(sorted[0].id);
          }
          const selected = (this.selectedMonitoringTemplateIds() ?? []).filter((id) =>
            sorted.some((template) => template.id === id),
          );
          this.selectedMonitoringTemplateIds.set(selected);
        },
        error: (error) => {
          this.monitoringTemplates.set([]);
          this.selectedMonitoringTemplateId.set('');
          this.selectedMonitoringTemplateIds.set([]);
          this.notify.error(
            this.resolveError(error, 'Не удалось загрузить шаблоны мониторинга.'),
            'Шаблоны',
          );
          this.templatesLoading.set(false);
        },
      });
  }

  loadMonitoringTemplateDetails(templateId: string): void {
    if (!templateId) {
      this.selectedTemplateDetails.set(null);
      return;
    }
    this.selectedTemplateDetailsLoading.set(true);
    this.http
      .get<MonitoringTemplateDetails>(`${this.apiBaseUrl}/api/monitoring/templates/${templateId}`)
      .subscribe({
        next: (details) => {
          this.selectedTemplateDetails.set(details);
          this.selectedTemplateDetailsLoading.set(false);
        },
        error: (error) => {
          this.selectedTemplateDetails.set(null);
          this.selectedTemplateDetailsLoading.set(false);
          this.notify.error(
            this.resolveError(error, 'Не удалось загрузить подробности шаблона.'),
            'Шаблоны',
          );
        },
      });
  }

  previewMonitoringTemplateArchive(
    file: File,
    onReady?: (preview: MonitoringTemplateImportPreview) => void,
  ): void {
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append('file', file, file.name);
    this.templatesMutationLoading.set(true);
    this.templateImportPreview.set(null);
    this.http
      .post<MonitoringTemplateImportPreview>(
        `${this.apiBaseUrl}/api/monitoring/templates/preview`,
        formData,
      )
      .subscribe({
        next: (preview) => {
          this.templateImportPreview.set(preview);
          this.templatesMutationLoading.set(false);
          onReady?.(preview);
        },
        error: (error) => {
          this.templateImportPreview.set(null);
          this.notify.error(
            this.resolveError(error, 'Не удалось выполнить dry-run импорта шаблона.'),
            'Шаблоны',
          );
          this.templatesMutationLoading.set(false);
        },
      });
  }

  uploadMonitoringTemplateArchive(
    file: File,
    meta: { vendor: string; model?: string; firmware?: string },
  ): void {
    if (!file) {
      return;
    }
    if (!meta || !meta.vendor?.trim()) {
      this.notify.error('Необходимо заполнить Вендор перед загрузкой.', 'Шаблоны');
      return;
    }
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('vendor', meta.vendor.trim());
    const model = meta.model?.trim() ?? '';
    const firmware = meta.firmware?.trim() ?? '';
    if (model) {
      formData.append('model', model);
    }
    if (firmware) {
      formData.append('firmware', firmware);
    }
    this.templatesMutationLoading.set(true);
    this.http
      .post<ActionResult>(`${this.apiBaseUrl}/api/monitoring/templates/upload`, formData)
      .subscribe({
        next: (result) => {
          this.notify.success(result.message, 'Шаблоны');
          this.templatesMutationLoading.set(false);
          this.loadMonitoringTemplates();
        },
        error: (error) => {
          this.notify.error(
            this.resolveError(error, 'Не удалось загрузить файл шаблона.'),
            'Шаблоны',
          );
          this.templatesMutationLoading.set(false);
        },
      });
  }

  clearTemplateImportPreview(): void {
    this.templateImportPreview.set(null);
  }

  updateMonitoringTemplate(
    templateId: string,
    body: MonitoringTemplateUpdateRequest,
    onSuccess?: () => void,
  ): void {
    this.templatesMutationLoading.set(true);
    this.http
      .patch<ActionResult>(`${this.apiBaseUrl}/api/monitoring/templates/${templateId}`, body)
      .subscribe({
        next: (result) => {
          this.notify.success(result.message, 'Шаблоны');
          this.templatesMutationLoading.set(false);
          this.loadMonitoringTemplates();
          onSuccess?.();
        },
        error: (error) => {
          this.notify.error(
            this.resolveError(error, 'Не удалось обновить шаблон мониторинга.'),
            'Шаблоны',
          );
          this.templatesMutationLoading.set(false);
        },
      });
  }

  deleteMonitoringTemplate(templateId: string): void {
    this.templatesMutationLoading.set(true);
    this.http
      .delete<ActionResult>(`${this.apiBaseUrl}/api/monitoring/templates/${templateId}`)
      .subscribe({
        next: (result) => {
          this.notify.success(result.message, 'Шаблоны');
          this.templatesMutationLoading.set(false);
          this.loadMonitoringTemplates();
        },
        error: (error) => {
          this.notify.error(
            this.resolveError(error, 'Не удалось удалить шаблон мониторинга.'),
            'Шаблоны',
          );
          this.templatesMutationLoading.set(false);
        },
      });
  }

  isTemplateDeletable(template: MonitoringTemplateSummary): boolean {
    return !!template.deletable;
  }

  loadMonitoringSnapshot(device: DeviceScanResult): void {
    this.http
      .get<DeviceMonitoringDetails>(
        `${this.apiBaseUrl}/api/monitoring/devices/${device.id}/details`,
      )
      .subscribe({
        next: (details) => {
          this.monitoringDetails.update((c) => ({ ...c, [device.id]: details }));
        },
        error: () => {
          this.monitoringDetails.update((c) => ({
            ...c,
            [device.id]: this.fallbackMonitoringDetails(device),
          }));
        },
      });
  }

  loadMonitoringDetails(device: DeviceScanResult): void {
    this.loadMonitoringSnapshot(device);
  }

  refreshMonitoringDetails(device: DeviceScanResult, liveMode: boolean): void {
    const id = device.id;
    if (liveMode) {
      this.liveTelemetryState.update((current) => ({
        ...current,
        [id]: {
          ...this.deviceLiveTelemetryState(id),
          active: true,
          loading: true,
          lastError: null,
        },
      }));
    }
    this.http
      .post<DeviceMonitoringDetails>(
        `${this.apiBaseUrl}/api/monitoring/devices/${id}/details/refresh`,
        {},
        { params: { live: String(liveMode) } },
      )
      .subscribe({
        next: (details) => {
          this.monitoringDetails.update((c) => ({ ...c, [id]: details }));
          if (liveMode) {
            this.liveTelemetryState.update((current) => ({
              ...current,
              [id]: {
                ...this.deviceLiveTelemetryState(id),
                active: true,
                loading: false,
                nextRefreshAt: new Date(
                  Date.now() + MonitoringService.LIVE_REFRESH_INTERVAL_MS,
                ).toISOString(),
                lastError: null,
              },
            }));
          }
        },
        error: (error) => {
          if (liveMode) {
            this.liveTelemetryState.update((current) => ({
              ...current,
              [id]: {
                ...this.deviceLiveTelemetryState(id),
                active: true,
                loading: false,
                nextRefreshAt: new Date(
                  Date.now() + MonitoringService.LIVE_REFRESH_INTERVAL_MS,
                ).toISOString(),
                lastError: this.resolveError(error, 'Не удалось обновить live-телеметрию.'),
              },
            }));
          }
        },
      });
  }

  loadMonitoredDeviceMeta(deviceId: string): void {
    if (!deviceId) {
      return;
    }
    this.http
      .get<MonitoredDeviceMeta>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/meta`)
      .subscribe({
        next: (meta) => this.monitoredDeviceMeta.update((c) => ({ ...c, [deviceId]: meta })),
        error: () => {},
      });
  }

  deviceMeta(deviceId: string): MonitoredDeviceMeta | null {
    return this.monitoredDeviceMeta()[deviceId] ?? null;
  }

  loadDeviceItemStatePage(deviceId: string, q: string, page: number, size: number): void {
    if (!deviceId) {
      return;
    }
    this.deviceItemStateLoading.update((m) => ({ ...m, [deviceId]: true }));
    const params: Record<string, string> = {
      page: String(page),
      size: String(size),
    };
    const trimmedQ = q.trim();
    if (trimmedQ) {
      params['q'] = trimmedQ;
    }
    this.http
      .get<MonitoringItemStatePage>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/state/items`, {
        params,
      })
      .pipe(finalize(() => this.deviceItemStateLoading.update((m) => ({ ...m, [deviceId]: false }))))
      .subscribe({
        next: (p) => this.deviceItemStatePage.update((c) => ({ ...c, [deviceId]: p })),
        error: () =>
          this.deviceItemStatePage.update((c) => ({
            ...c,
            [deviceId]: {
              content: [],
              totalElements: 0,
              totalPages: 0,
              number: 0,
              size,
              first: true,
              last: true,
            },
          })),
      });
  }

  loadDeviceItemState(deviceId: string): void {
    this.loadDeviceItemStatePage(deviceId, '', 0, 20);
  }

  deviceItemState(deviceId: string): MonitoringItemState[] {
    return this.deviceItemStatePage()[deviceId]?.content ?? [];
  }

  /** Загружает ключи метрик с панелями графиков (день), если кэш пуст и запрос ещё не идёт. */
  ensureDeviceChartMetricKeys(deviceId: string): void {
    if (!deviceId) {
      return;
    }
    const cached = this.deviceChartMetricKeys()[deviceId];
    if (cached !== undefined || this.chartMetricKeysLoading.has(deviceId)) {
      return;
    }
    this.chartMetricKeysLoading.add(deviceId);
    const { fromIso, toIso } = defaultDayMetricsRange();
    this.http
      .get<DeviceMetricsHistoryResponseDto>(
        `${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/metrics`,
        {
          params: {
            from: fromIso,
            to: toIso,
            panelsOffset: '0',
            panelsLimit: '0',
            maxPoints: '1',
          },
        },
      )
      .subscribe({
        next: (payload) => {
          const keys = new Set(collectChartMetricKeys(payload?.chartPanels ?? []));
          this.deviceChartMetricKeys.update((c) => ({ ...c, [deviceId]: keys }));
          this.chartMetricKeysLoading.delete(deviceId);
        },
        error: () => {
          this.deviceChartMetricKeys.update((c) => ({ ...c, [deviceId]: new Set() }));
          this.chartMetricKeysLoading.delete(deviceId);
        },
      });
  }

  hasDeviceChartMetric(deviceId: string, itemKey: string): boolean {
    const key = itemKey?.trim();
    if (!key) {
      return false;
    }
    return this.deviceChartMetricKeys()[deviceId]?.has(key) ?? false;
  }

  loadDeviceItems(deviceId: string): void {
    if (!deviceId) {
      return;
    }
    this.http
      .get<MonitoringDeviceItem[]>(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/items`)
      .subscribe({
        next: (items) => this.deviceItemsCatalog.update((c) => ({ ...c, [deviceId]: items })),
        error: () => this.deviceItemsCatalog.update((c) => ({ ...c, [deviceId]: [] })),
      });
  }

  deviceItems(deviceId: string): MonitoringDeviceItem[] {
    return this.deviceItemsCatalog()[deviceId] ?? [];
  }

  updateDeviceItems(
    deviceId: string,
    activeItems: MonitoringDeviceItemSelection[],
  ): Observable<MonitoringDeviceItem[]> {
    return this.http
      .patch<
        MonitoringDeviceItem[]
      >(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/items`, { activeItems })
      .pipe(tap((items) => this.deviceItemsCatalog.update((c) => ({ ...c, [deviceId]: items }))));
  }

  loadDeviceDiscoveryState(deviceId: string): void {
    if (!deviceId) {
      return;
    }
    this.http
      .get<
        MonitoringDiscoveryInstance[]
      >(`${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/state/discovery`)
      .subscribe({
        next: (items) => this.deviceDiscoveryStates.update((c) => ({ ...c, [deviceId]: items })),
        error: () => this.deviceDiscoveryStates.update((c) => ({ ...c, [deviceId]: [] })),
      });
  }

  deviceDiscoveryState(deviceId: string): MonitoringDiscoveryInstance[] {
    return this.deviceDiscoveryStates()[deviceId] ?? [];
  }

  loadPortConfiguration(device: DeviceScanResult): void {
    this.http
      .get<
        DevicePortConfigApi[]
      >(`${this.apiBaseUrl}/api/monitoring/devices/${device.id}/interfaces`)
      .subscribe({
        next: (ports) => {
          this.monitoringPortConfigs.update((c) => ({
            ...c,
            [device.id]: this.normalizePortConfigs(ports),
          }));
          this.refreshPortConfiguration(device);
        },
        error: () => {
          this.monitoringPortConfigs.update((c) => ({ ...c, [device.id]: [] }));
        },
      });
  }

  refreshPortConfiguration(device: DeviceScanResult): void {
    this.http
      .post<
        DevicePortConfigApi[]
      >(`${this.apiBaseUrl}/api/monitoring/devices/${device.id}/interfaces/refresh`, {})
      .subscribe({
        next: (ports) => {
          this.monitoringPortConfigs.update((c) => ({
            ...c,
            [device.id]: this.normalizePortConfigs(ports),
          }));
        },
        error: () => {},
      });
  }

  loadAllEvents(filter: MonitoringEventFilter, page: number, size: number): void {
    this.eventsLoading.set(true);
    const baseParams = this.buildMonitoringEventsQueryParams(filter);
    const pageParams: Record<string, string> = {
      ...baseParams,
      page: String(page),
      size: String(size),
    };
    const emptySummary: MonitoringEventLevelSummary = {
      disaster: 0,
      high: 0,
      average: 0,
      warning: 0,
      information: 0,
      notClassified: 0,
    };
    const levelSummaryParams = this.buildMonitoringEventsQueryParams(filter, {
      omitThresholdLevel: true,
    });
    forkJoin({
      page: this.http.get<MonitoringEventPage>(`${this.apiBaseUrl}/api/monitoring/events`, {
        params: pageParams,
      }),
      levels: this.http
        .get<MonitoringEventLevelSummary>(
          `${this.apiBaseUrl}/api/monitoring/events/level-summary`,
          {
            params: levelSummaryParams,
          },
        )
        .pipe(catchError(() => of(emptySummary))),
    }).subscribe({
      next: ({ page: p, levels }) => {
        this.eventsPage.set(p);
        this.eventsLevelSummary.set(levels);
        this.eventsLoading.set(false);
      },
      error: () => {
        this.eventsLoading.set(false);
      },
    });
  }

  /**
   * Параметры фильтра для GET /api/monitoring/events и /events/level-summary (без page/size).
   * Для level-summary передавайте omitThresholdLevel: true, чтобы полоса уровней показывала
   * фактические счётчики по каждому уровню при тех же прочих фильтрах, а не выборку только по выбранному уровню.
   */
  buildMonitoringEventsQueryParams(
    filter: MonitoringEventFilter,
    options?: { omitThresholdLevel?: boolean },
  ): Record<string, string> {
    const params: Record<string, string> = {};
    if (filter.status) params['status'] = filter.status;
    if (filter.thresholdLevel && !options?.omitThresholdLevel) {
      params['thresholdLevel'] = filter.thresholdLevel;
    }
    if (filter.deviceId != null) params['deviceId'] = String(filter.deviceId);
    if (filter.breachStartedFrom) params['breachStartedFrom'] = filter.breachStartedFrom;
    if (filter.breachStartedTo) params['breachStartedTo'] = filter.breachStartedTo;
    if (filter.normalizedFrom) params['normalizedFrom'] = filter.normalizedFrom;
    if (filter.normalizedTo) params['normalizedTo'] = filter.normalizedTo;
    if (filter.minDurationSeconds != null)
      params['minDurationSeconds'] = String(filter.minDurationSeconds);
    if (filter.maxDurationSeconds != null)
      params['maxDurationSeconds'] = String(filter.maxDurationSeconds);
    const metricQ = filter.metricNameContains?.trim();
    if (metricQ) params['metricNameContains'] = metricQ;
    const macQ = filter.macAddressContains?.trim();
    if (macQ) params['macAddressContains'] = macQ;
    const deviceIp = filter.deviceIpContains?.trim();
    if (deviceIp) params['deviceIpContains'] = deviceIp;
    const deviceName = filter.deviceNameContains?.trim();
    if (deviceName) params['deviceNameContains'] = deviceName;
    const deviceIds = (filter.deviceIds ?? []).filter((id) => Number.isFinite(id) && id > 0);
    if (deviceIds.length > 0) {
      params['deviceIds'] = deviceIds.map((id) => String(Math.trunc(id))).join(',');
    }
    const deviceTags = (filter.deviceTags ?? [])
      .map((t) => t.trim())
      .filter((t) => t.length > 0);
    if (deviceTags.length > 0) {
      params['deviceTags'] = deviceTags.join(',');
    }
    return params;
  }

  /** Одна страница событий без записи в глобальные сигналы (виджеты, разовые запросы). */
  getMonitoringEventsPage(
    filter: MonitoringEventFilter,
    page: number,
    size: number,
  ): Observable<MonitoringEventPage> {
    const params: Record<string, string> = {
      ...this.buildMonitoringEventsQueryParams(filter),
      page: String(page),
      size: String(size),
    };
    return this.http.get<MonitoringEventPage>(`${this.apiBaseUrl}/api/monitoring/events`, {
      params,
    });
  }

  getMetricsHistoryBatch(
    body: MonitoringMetricsBatchRequest,
  ): Observable<MonitoringMetricsBatchSeries[]> {
    return this.http.post<MonitoringMetricsBatchSeries[]>(
      `${this.apiBaseUrl}/api/monitoring/metrics/history-batch`,
      body,
    );
  }

  loadDeviceScopedEvents(
    device: DeviceScanResult,
    page: number,
    size: number,
    filter: MonitoringEventFilter,
  ): void {
    const id = device.id;
    this.deviceScopedEventsLoading.update((m) => ({ ...m, [id]: true }));
    const params: Record<string, string> = {
      ...this.buildMonitoringEventsQueryParams(filter),
      deviceId: id,
      page: String(page),
      size: String(size),
    };
    this.http
      .get<MonitoringEventPage>(`${this.apiBaseUrl}/api/monitoring/events`, { params })
      .pipe(finalize(() => this.deviceScopedEventsLoading.update((m) => ({ ...m, [id]: false }))))
      .subscribe({
        next: (p) => this.deviceScopedEventsPage.update((c) => ({ ...c, [id]: p })),
        error: () =>
          this.deviceScopedEventsPage.update((c) => ({
            ...c,
            [id]: {
              content: [],
              totalElements: 0,
              totalPages: 0,
              number: 0,
              size,
              first: true,
              last: true,
            },
          })),
      });
  }

  loadDeviceOpenEventCount(device: DeviceScanResult): void {
    const id = device.id;
    const params: Record<string, string> = {
      deviceId: id,
      status: 'OPEN',
      page: '0',
      size: '1',
    };
    this.http
      .get<MonitoringEventPage>(`${this.apiBaseUrl}/api/monitoring/events`, { params })
      .subscribe({
        next: (p) => this.deviceOpenEventCount.update((c) => ({ ...c, [id]: p.totalElements })),
        error: () => this.deviceOpenEventCount.update((c) => ({ ...c, [id]: 0 })),
      });
  }

  loadBackupSnapshot(device: DeviceScanResult): void {
    const id = device.id;
    this.backupSnapshotsLoading.update((m) => ({ ...m, [id]: true }));
    this.http
      .get<DeviceBackupSnapshot>(`${this.apiBaseUrl}/api/monitoring/devices/${device.id}/backups`)
      .pipe(finalize(() => this.backupSnapshotsLoading.update((m) => ({ ...m, [id]: false }))))
      .subscribe({
        next: (snapshot) => this.updateBackupSnapshot(device.id, snapshot),
        error: () => {},
      });
  }

  isBackupSnapshotLoading(deviceId: string): boolean {
    return this.backupSnapshotsLoading()[deviceId] ?? false;
  }

  resolveDeviceTemplateIds(meta: MonitoredDeviceMeta | null): string[] {
    if (!meta) {
      return [];
    }
    const fromList = (meta.templateIds ?? [])
      .map((id) => String(id ?? '').trim())
      .filter((id) => id.length > 0);
    if (fromList.length > 0) {
      return Array.from(new Set(fromList));
    }
    const primary = String(meta.templateId ?? '').trim();
    if (primary.length > 0) {
      return [primary];
    }
    // При автоопределении шаблона при активации в template_ids может быть пусто,
    // но effective_template_id указывает на фактически применённый шаблон.
    const effective = String(meta.effectiveTemplateId ?? '').trim();
    return effective.length > 0 ? [effective] : [];
  }

  replaceDeviceTemplates(
    device: DeviceScanResult,
    templateIds: string[],
  ): Observable<DeviceScanResult[]> {
    const normalizedTemplateIds = Array.from(
      new Set(templateIds.map((id) => id.trim()).filter((id) => id.length > 0)),
    );
    const ip = String(device.ip ?? '').trim();
    return this.http
      .post<BackendDevice[]>(`${this.apiBaseUrl}/api/monitoring/activate`, {
        devices: [device],
        templateId: null,
        templateIds: [],
        perDeviceTemplateIds: {},
        perDeviceTemplateIdLists: ip.length > 0 ? { [ip]: normalizedTemplateIds } : {},
        snmpCredentials: null,
        accessProfileIdForActivation: null,
      })
      .pipe(map((ds) => ds.map(this.mapDevice)));
  }

  activateMonitoring(
    devices: DeviceScanResult[],
    templateIds: string[],
    snmpCredentials?: {
      snmpVersion: string;
      community: string;
      securityUsername: string;
      authProtocol: string;
      authPassword: string;
      privacyProtocol: string;
      privacyPassword: string;
    } | null,
    accessProfileIdForActivation?: number | null,
  ): Observable<DeviceScanResult[]> {
    const normalizedTemplateIds = Array.from(
      new Set(templateIds.map((id) => id.trim()).filter((id) => id.length > 0)),
    );
    return this.http
      .post<BackendDevice[]>(`${this.apiBaseUrl}/api/monitoring/activate`, {
        devices,
        templateId: normalizedTemplateIds[0] ?? null,
        templateIds: normalizedTemplateIds,
        perDeviceTemplateIds: {},
        perDeviceTemplateIdLists: {},
        snmpCredentials: snmpCredentials ?? null,
        accessProfileIdForActivation: accessProfileIdForActivation ?? null,
      })
      .pipe(map((ds) => ds.map(this.mapDevice)));
  }

  resetScanMonitoringTemplateSelection(): void {
    this.monitoringTemplateAutoDetection.set(false);
    this.selectedMonitoringTemplateIds.set([]);
    this.selectedMonitoringTemplateId.set('');
  }

  setSelectedMonitoringTemplateIds(templateIds: string[]): void {
    const normalizedTemplateIds = Array.from(
      new Set((templateIds ?? []).map((id) => id.trim()).filter((id) => id.length > 0)),
    );
    this.selectedMonitoringTemplateIds.set(normalizedTemplateIds);
    this.selectedMonitoringTemplateId.set(normalizedTemplateIds[0] ?? '');
    if (normalizedTemplateIds.length > 0) {
      this.monitoringTemplateAutoDetection.set(false);
    }
  }

  setMonitoringTemplateSelection(templateIds: string[], autoDetection: boolean): void {
    if (autoDetection) {
      this.monitoringTemplateAutoDetection.set(true);
      this.selectedMonitoringTemplateIds.set([]);
      this.selectedMonitoringTemplateId.set('');
      return;
    }
    this.monitoringTemplateAutoDetection.set(false);
    this.setSelectedMonitoringTemplateIds(templateIds);
  }

  deactivateMonitoring(ips: string[]): Observable<DeviceScanResult[]> {
    return this.http
      .post<BackendDevice[]>(`${this.apiBaseUrl}/api/monitoring/deactivate`, { ips })
      .pipe(map((ds) => ds.map(this.mapDevice)));
  }

  deactivateMonitoringByIds(deviceIds: number[]): Observable<DeviceScanResult[]> {
    const normalizedIds = Array.from(
      new Set(
        (deviceIds ?? []).filter((id) => Number.isFinite(id) && id > 0).map((id) => Math.trunc(id)),
      ),
    );
    return this.http
      .post<
        BackendDevice[]
      >(`${this.apiBaseUrl}/api/monitoring/deactivate`, { deviceIds: normalizedIds })
      .pipe(map((ds) => ds.map(this.mapDevice)));
  }

  setCurrentConfigAsBaseline(device: DeviceScanResult): Observable<DeviceBackupSnapshot> {
    return this.http
      .post<DeviceBackupSnapshot>(
        `${this.apiBaseUrl}/api/monitoring/devices/${device.id}/backups/current-as-baseline`,
        {},
      )
      .pipe(tap((snapshot) => this.updateBackupSnapshot(device.id, snapshot)));
  }

  uploadBaselineContent(
    device: DeviceScanResult,
    fileName: string,
    content: string,
  ): Observable<DeviceBackupSnapshot> {
    return this.http
      .post<DeviceBackupSnapshot>(
        `${this.apiBaseUrl}/api/monitoring/devices/${device.id}/backups/baseline/upload`,
        { fileName, content },
      )
      .pipe(tap((snapshot) => this.updateBackupSnapshot(device.id, snapshot)));
  }

  useBackupAsBaseline(
    device: DeviceScanResult,
    backup: DeviceBackupConfig,
  ): Observable<DeviceBackupSnapshot> {
    return this.http
      .post<DeviceBackupSnapshot>(
        `${this.apiBaseUrl}/api/monitoring/devices/${device.id}/backups/baseline/select`,
        { backupId: backup.id },
      )
      .pipe(tap((snapshot) => this.updateBackupSnapshot(device.id, snapshot)));
  }

  downloadBackup(device: DeviceScanResult, backup: DeviceBackupConfig): void {
    this.http
      .get(`${this.apiBaseUrl}/api/monitoring/devices/${device.id}/backups/${backup.id}/download`, {
        responseType: 'text',
      })
      .subscribe({
        next: (content) => {
          const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = `${device.ip}-${backup.name}.cfg`;
          link.click();
          window.URL.revokeObjectURL(url);
        },
        error: () => {},
      });
  }

  compareBackupWithBaseline(
    device: DeviceScanResult,
    backup: DeviceBackupConfig,
  ): Observable<BackupComparisonResult> {
    return this.http
      .get<BackupComparisonResult>(
        `${this.apiBaseUrl}/api/monitoring/devices/${device.id}/backups/${backup.id}/compare`,
      )
      .pipe(tap(() => this.loadBackupSnapshot(device)));
  }

  private updateBackupSnapshot(deviceId: string, snapshot: DeviceBackupSnapshot): void {
    this.backupSnapshots.update((c) => ({ ...c, [deviceId]: snapshot }));
  }

  private normalizePortConfigs(ports: DevicePortConfigApi[]): DevicePortConfig[] {
    return ports.map((p) => ({
      name: p.name,
      description: p.description,
      adminStatus: p.adminStatus === 'UP' ? 'UP' : 'DOWN',
      operStatus: p.operStatus === 'UP' ? 'UP' : 'DOWN',
      lost: p.lost === 'Да' ? 'Да' : 'Нет',
      nominalSpeed: p.nominalSpeed || '-',
      activeSpeed: p.activeSpeed || '-',
      purpose: p.purpose || '-',
      mode: p.mode || '-',
      kind: p.kind === 'logical' ? 'logical' : 'physical',
    }));
  }

  private readonly mapDevice = (raw: BackendDevice): DeviceScanResult => ({
    id: raw.monitoredDeviceId != null ? String(raw.monitoredDeviceId) : '',
    port: (raw as unknown as { port?: number | null }).port ?? null,
    hostName: raw.hostName,
    name: raw.name,
    serialNumber: raw.serialNumber,
    ip: raw.ip,
    domainName: raw.domainName ?? '-',
    macAddress: raw.macAddress,
    vendor: raw.vendor,
    model: raw.model,
    firmwareVersion: raw.firmwareVersion,
    pollingStatus: raw.pollingStatus,
    status: raw.status,
    group: raw.group,
    tags: Array.isArray((raw as unknown as { tags?: unknown }).tags)
      ? ((raw as unknown as { tags: unknown[] }).tags.filter(
          (t) => typeof t === 'string',
        ) as string[])
      : [],
    availability: raw.availability,
  });

  updateDeviceTags(deviceId: string, tags: string[]): Observable<MonitoredDeviceMeta> {
    return this.http.patch<MonitoredDeviceMeta>(
      `${this.apiBaseUrl}/api/monitoring/devices/${deviceId}/tags`,
      { tags },
    );
  }

  private resolveError(error: unknown, fallback: string): string {
    const message = (error as { error?: { message?: string } })?.error?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }

  deleteManyTemplates(ids: string[]): Observable<void[]> {
    return forkJoin(ids.map((id) => this.deleteMonitoringTemplate(id)));
  }
}
