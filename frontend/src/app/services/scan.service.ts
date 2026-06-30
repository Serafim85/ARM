import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Subscription, switchMap, timer } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import {
  ActionResult,
  AccessProfileSummary,
  DiscoveryMethod,
  DiscoveryProbeConfig,
  DeviceScanResult,
  ScanRequestPayload,
  ScanRunDto,
  ScanRunStartResponse,
} from '../models';
import { AccessProfilesService } from './access-profiles.service';
import { MonitoringService } from './monitoring.service';
import { NotifierService } from '../notifier.service';
import {
  createDefaultProbe,
  defaultPortForMethod,
  isSnmpMethod,
  legacyScanRequestToProbes,
  probeUsesPort,
  probeUsesProfileSettings,
} from '../utils/scan-probe.util';
import { buildDeviceSearchText } from '../utils/scan-result.util';
import { parseSubnetInput, type SubnetParseResult } from '../utils/subnet-range.util';

@Injectable({ providedIn: 'root' })
export class ScanService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly notify = inject(NotifierService);
  private readonly accessProfilesService = inject(AccessProfilesService);
  private readonly monitoringService = inject(MonitoringService);

  readonly discoveryMethods: Array<{ value: DiscoveryMethod; label: string }> = [
    { value: 'ICMP', label: 'ICMP ping' },
    { value: 'DNS', label: 'DNS (PTR)' },
    { value: 'SNMP_V1', label: 'SNMPv1' },
    { value: 'SNMP_V2', label: 'SNMPv2' },
    { value: 'SNMP_V3', label: 'SNMPv3' },
    { value: 'TCP', label: 'TCP' },
    { value: 'SSH', label: 'SSH' },
    { value: 'HTTP', label: 'HTTP' },
    { value: 'HTTPS', label: 'HTTPS' },
    { value: 'FTP', label: 'FTP' },
    { value: 'TELNET', label: 'Telnet' },
    { value: 'SMTP', label: 'SMTP' },
    { value: 'POP', label: 'POP' },
    { value: 'IMAP', label: 'IMAP' },
    { value: 'LDAP', label: 'LDAP' },
    { value: 'NNTP', label: 'NNTP' },
  ];

  readonly selectedProbes = signal<DiscoveryProbeConfig[]>([createDefaultProbe('SNMP_V2')]);
  readonly accessProfileId = signal<number | null>(null);
  readonly accessProfiles = signal<AccessProfileSummary[]>([]);
  readonly subnetRange = signal('192.168.176.0-255');
  readonly subnetRangeValidation = computed((): SubnetParseResult | null => {
    const trimmed = this.subnetRange().trim();
    if (!trimmed) {
      return null;
    }
    return parseSubnetInput(trimmed);
  });
  readonly timeout = signal('1500');
  readonly retries = signal('1');
  readonly scanLoading = signal(false);
  readonly scanStopping = signal(false);
  readonly scanResults = signal<DeviceScanResult[]>([]);
  readonly resultFilter = signal('');
  readonly selectedResultIps = signal<string[]>([]);
  readonly scanCompletedAtLeastOnce = signal(false);
  readonly scanParamsCollapsed = signal(false);
  readonly scannedAddresses = signal(0);
  readonly totalAddresses = signal(0);
  private readonly frozenProgressPercent = signal(0);
  readonly scanProgressPercent = computed(() => {
    const total = this.totalAddresses();
    if (total <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((this.scannedAddresses() / total) * 100));
  });
  readonly displayProgressPercent = computed(() =>
    this.scanStopping() ? this.frozenProgressPercent() : this.scanProgressPercent()
  );
  readonly displayScannedAddresses = computed(() =>
    this.scanStopping() ? this.frozenScannedAddresses() : this.scannedAddresses()
  );
  readonly displayTotalAddresses = computed(() =>
    this.scanStopping() ? this.frozenTotalAddresses() : this.totalAddresses()
  );
  private readonly frozenScannedAddresses = signal(0);
  private readonly frozenTotalAddresses = signal(0);
  private currentRunId: number | null = null;
  private scanRequestSub: Subscription | null = null;
  private pollSub: Subscription | null = null;

  readonly selectedMethods = computed(() => this.selectedProbes().map((p) => p.method));

  readonly canResetDiscoveryMethods = computed(() => this.selectedProbes().length > 1);

  readonly canRemoveProbe = computed(() => this.selectedProbes().length > 1);

  readonly filteredScanResults = computed(() => {
    const query = this.resultFilter().trim().toLowerCase();
    const results = this.scanResults();
    if (!query) return results;
    return results.filter((d) => buildDeviceSearchText(d).includes(query));
  });

  isProbeSelected(method: DiscoveryMethod): boolean {
    return this.selectedMethods().includes(method);
  }

  getProbe(method: DiscoveryMethod): DiscoveryProbeConfig | undefined {
    return this.selectedProbes().find((p) => p.method === method);
  }

  toggleProbe(method: DiscoveryMethod, enabled: boolean): void {
    if (enabled) {
      if (this.isProbeSelected(method)) {
        return;
      }
      this.selectedProbes.update((probes) => [...probes, createDefaultProbe(method)]);
    } else {
      const next = this.selectedProbes().filter((p) => p.method !== method);
      if (next.length === 0) {
        this.notify.warn('Выберите хотя бы один метод обнаружения.', 'Параметры сканирования');
        return;
      }
      this.selectedProbes.set(next);
    }
    this.clearResults();
  }

  removeProbe(method: DiscoveryMethod): void {
    this.toggleProbe(method, false);
  }

  /** Установить набор методов из p-multiSelect (сохраняет уже введённые настройки probe). */
  setSelectedMethods(methods: DiscoveryMethod[]): void {
    if (methods.length === 0) {
      this.resetDiscoveryMethods();
      return;
    }
    const current = this.selectedProbes();
    const order = this.discoveryMethods.map((m) => m.value);
    const next = methods
      .map((method) => current.find((p) => p.method === method) ?? createDefaultProbe(method))
      .sort((a, b) => order.indexOf(a.method) - order.indexOf(b.method));
    this.selectedProbes.set(next);
    this.clearResults();
  }

  /** Сброс выбора методов к значению по умолчанию (SNMPv2). */
  resetDiscoveryMethods(): void {
    this.selectedProbes.set([createDefaultProbe('SNMP_V2')]);
    this.clearResults();
  }

  updateProbe(method: DiscoveryMethod, patch: Partial<DiscoveryProbeConfig>): void {
    this.selectedProbes.update((probes) =>
      probes.map((p) => (p.method === method ? { ...p, ...patch } : p))
    );
  }

  onSubnetRangeInput(value: string): void {
    this.subnetRange.set(value);
  }

  resolveSubnetRange(): string | null {
    const parsed = parseSubnetInput(this.subnetRange());
    if (!parsed.ok) {
      this.notify.warn(parsed.error, 'Параметры сканирования');
      return null;
    }
    return parsed.normalizedRange;
  }

  isResultSelected(ip: string): boolean {
    return this.selectedResultIps().includes(ip);
  }

  toggleResultSelection(ip: string, checked: boolean): void {
    if (checked) {
      this.selectedResultIps.update((c) => (c.includes(ip) ? c : [...c, ip]));
    } else {
      this.selectedResultIps.update((c) => c.filter((i) => i !== ip));
    }
  }

  clearResults(): void {
    this.scanResults.set([]);
    this.scanCompletedAtLeastOnce.set(false);
    this.resultFilter.set('');
    this.selectedResultIps.set([]);
    this.scanParamsCollapsed.set(false);
  }

  showResults(results: DeviceScanResult[]): void {
    const rows = Array.isArray(results) ? results : [];
    this.scanResults.set(rows);
    this.scanCompletedAtLeastOnce.set(true);
    this.resultFilter.set('');
    this.selectedResultIps.set([]);
    this.scanParamsCollapsed.set(rows.length > 0);
  }

  expandScanParams(): void {
    this.scanParamsCollapsed.set(false);
  }

  getMethodLabel(method: DiscoveryMethod): string {
    return this.discoveryMethods.find((m) => m.value === method)?.label ?? method;
  }

  probesSummary(): string {
    return this.selectedProbes()
      .map((p) => this.getMethodLabel(p.method))
      .join(', ');
  }

  loadAccessProfiles(): void {
    this.accessProfilesService.listSummaries().subscribe({
      next: (profiles) => this.accessProfiles.set(profiles),
      error: () => this.notify.warn('Не удалось загрузить профили доступа.', 'Сканирование'),
    });
  }

  selectedAccessProfile(): AccessProfileSummary | null {
    const id = this.accessProfileId();
    if (id == null) {
      return null;
    }
    return this.accessProfiles().find((profile) => profile.id === id) ?? null;
  }

  profileCoversMethod(method: DiscoveryMethod): boolean {
    const profile = this.selectedAccessProfile();
    if (!profile) {
      return false;
    }
    if (method === 'SNMP_V1') {
      return profile.snmpV1Enabled;
    }
    if (method === 'SNMP_V2') {
      return profile.snmpV2Enabled;
    }
    if (method === 'SNMP_V3') {
      return profile.snmpV3Enabled;
    }
    if (method === 'SSH') {
      return profile.sshEnabled;
    }
    if (method === 'HTTPS') {
      return profile.httpsEnabled;
    }
    return false;
  }

  usesProfileForProbe(method: DiscoveryMethod): boolean {
    return this.accessProfileId() != null
      && probeUsesProfileSettings(method)
      && this.profileCoversMethod(method);
  }

  usesManualProbeConfig(method: DiscoveryMethod): boolean {
    return this.accessProfileId() != null
      && probeUsesProfileSettings(method)
      && !this.profileCoversMethod(method);
  }

  loadProbesFromScanRequest(raw: unknown): void {
    if (!raw || typeof raw !== 'object') {
      return;
    }
    const record = raw as Record<string, unknown>;
    if (typeof record['subnetRange'] === 'string') {
      this.subnetRange.set(record['subnetRange']);
    }
    if (record['timeout'] != null) {
      this.timeout.set(String(record['timeout']));
    }
    if (record['retries'] != null) {
      this.retries.set(String(record['retries']));
    }
    const profileId = Number(record['accessProfileId']);
    this.accessProfileId.set(Number.isFinite(profileId) && profileId > 0 ? profileId : null);
    this.selectedProbes.set(legacyScanRequestToProbes(record));
  }

  validateProbes(): boolean {
    const probes = this.selectedProbes();
    if (probes.length === 0) {
      this.notify.warn('Выберите хотя бы один метод обнаружения.', 'Параметры сканирования');
      return false;
    }
    const profileId = this.accessProfileId();
    if (profileId != null) {
      const profile = this.accessProfiles().find((p) => p.id === profileId);
      if (!profile) {
        this.notify.warn('Выбранный профиль доступа не найден. Обновите список профилей.', 'Параметры сканирования');
        return false;
      }
    }
    for (const probe of probes) {
      if (profileId != null && this.usesProfileForProbe(probe.method)) {
        continue;
      }
      if ((probe.method === 'SNMP_V1' || probe.method === 'SNMP_V2') && !(probe.community ?? '').trim()) {
        this.notify.warn('Для SNMP v1/v2c укажите community string.', 'Параметры сканирования');
        return false;
      }
      if (probe.method === 'SNMP_V3' && !(probe.securityUsername ?? '').trim()) {
        this.notify.warn('Для SNMP v3 укажите имя пользователя.', 'Параметры сканирования');
        return false;
      }
    }
    for (const probe of probes) {
      if (this.usesProfileForProbe(probe.method)) {
        continue;
      }
      if (probeUsesPort(probe.method)) {
        const port = Number(probe.port ?? defaultPortForMethod(probe.method));
        if (!Number.isFinite(port) || port < 1 || port > 65535) {
          this.notify.warn(`Укажите корректный порт для ${this.getMethodLabel(probe.method)}.`, 'Параметры сканирования');
          return false;
        }
      }
    }
    return true;
  }

  startScan(): void {
    if (this.scanLoading()) {
      return;
    }
    const range = this.resolveSubnetRange();
    if (!range) {
      return;
    }
    if (!this.validateProbes()) {
      return;
    }

    this.scanLoading.set(true);
    this.monitoringService.resetScanMonitoringTemplateSelection();
    this.scanResults.set([]);
    this.scanCompletedAtLeastOnce.set(false);
    this.scannedAddresses.set(0);
    this.totalAddresses.set(0);
    this.scanStopping.set(false);
    this.currentRunId = null;
    this.notify.info('Выполняется сканирование…', 'Сканирование', 3500);

    this.scanRequestSub = this.http
      .post<ScanRunStartResponse>(`${this.apiBaseUrl}/api/scan/runs`, this.currentScanRequest(range))
      .subscribe({
        next: (start) => {
          this.currentRunId = start.runId;
          this.totalAddresses.set(start.totalAddresses);
          this.scannedAddresses.set(0);
          this.scanRequestSub = null;
          this.startPolling(start.runId);
        },
        error: (error) => {
          this.handleScanStartError(error);
        },
      });
  }

  stopScan(): void {
    if (!this.scanLoading() || this.scanStopping()) {
      return;
    }
    const runId = this.currentRunId;
    this.frozenProgressPercent.set(this.scanProgressPercent());
    this.frozenScannedAddresses.set(this.scannedAddresses());
    this.frozenTotalAddresses.set(this.totalAddresses());
    this.scanStopping.set(true);
    this.notify.info('Остановка сканирования…', 'Сканирование', 2500);
    if (runId == null) {
      this.clearScanSubscriptions();
      this.finishScanUi();
      return;
    }
    this.http.post<ActionResult>(`${this.apiBaseUrl}/api/scan/runs/${runId}/stop`, {}).subscribe({
      error: () => {
        this.scanStopping.set(false);
        this.notify.warn('Не удалось отправить запрос на остановку. Проверьте backend.', 'Сканирование');
      },
    });
  }

  private startPolling(runId: number): void {
    this.pollSub?.unsubscribe();
    this.pollSub = timer(0, 1500)
      .pipe(switchMap(() => this.http.get<ScanRunDto>(`${this.apiBaseUrl}/api/scan/runs/${runId}`)))
      .subscribe({
        next: (status) => {
          if (!this.scanStopping()) {
            this.scannedAddresses.set(status.scannedAddresses);
            this.totalAddresses.set(status.totalAddresses);
          }
          if (status.status === 'SUCCESS') {
            this.loadScanResults(runId);
          } else if (status.status === 'CANCELLED') {
            this.handleScanCancelled(status.errorMessage ?? 'Сканирование остановлено.');
          } else if (status.status === 'FAILED') {
            this.handleScanTerminalError(status.errorMessage ?? 'Сканирование не завершено.');
          }
        },
        error: (error) => {
          const text =
            error?.status === 401 || error?.status === 403
              ? 'Недостаточно прав для просмотра статуса сканирования.'
              : (error?.error?.message ?? 'Не удалось получить статус сканирования.');
          this.handleScanTerminalError(text);
        },
      });
  }

  private loadScanResults(runId: number): void {
    this.clearScanSubscriptions();
    this.http.get<DeviceScanResult[]>(`${this.apiBaseUrl}/api/scan/runs/${runId}/results`).subscribe({
      next: (results) => {
        const list = Array.isArray(results) ? results : [];
        this.scanResults.set(list);
        this.scanCompletedAtLeastOnce.set(true);
        this.scanParamsCollapsed.set(list.length > 0);
        if (list.length > 0) {
          this.notify.success(
            `Сканирование (${this.probesSummary()}) завершено. Найдено устройств: ${list.length}.`,
            'Сканирование'
          );
        } else {
          this.notify.info('Сканирование завершено. Устройства не найдены.', 'Сканирование');
        }
        this.finishScanUi();
      },
      error: (error) => {
        const text = error?.error?.message ?? 'Не удалось загрузить результаты сканирования.';
        this.handleScanTerminalError(text);
      },
    });
  }

  private handleScanStartError(error: unknown): void {
    const text =
      (error as { status?: number })?.status === 401 || (error as { status?: number })?.status === 403
        ? 'Недостаточно прав для запуска сканирования.'
        : ((error as { error?: { message?: string } })?.error?.message ??
          'Не удалось запустить сканирование через backend.');
    this.handleScanTerminalError(text);
  }

  private handleScanCancelled(message: string): void {
    this.clearScanSubscriptions();
    this.notify.info(message, 'Сканирование');
    this.finishScanUi();
  }

  private handleScanTerminalError(message: string): void {
    this.clearScanSubscriptions();
    this.notify.error(message, 'Сканирование');
    this.finishScanUi();
  }

  private finishScanUi(): void {
    this.scanLoading.set(false);
    this.scanStopping.set(false);
    this.currentRunId = null;
  }

  private clearScanSubscriptions(): void {
    this.scanRequestSub?.unsubscribe();
    this.scanRequestSub = null;
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }

  currentScanRequest(subnetRangeOverride?: string): ScanRequestPayload {
    const subnetRange = subnetRangeOverride ?? this.resolveSubnetRange() ?? '';
    const profileId = this.accessProfileId();
    const probes = this.selectedProbes().map((probe) => {
      const manual = this.usesManualProbeConfig(probe.method);
      const inlineSnmp = profileId == null || manual;
      return {
        method: probe.method,
        ...(probeUsesPort(probe.method) && !this.usesProfileForProbe(probe.method)
          ? { port: Number(probe.port ?? defaultPortForMethod(probe.method)) }
          : {}),
        ...(inlineSnmp && (probe.method === 'SNMP_V1' || probe.method === 'SNMP_V2')
          ? { community: probe.community ?? 'public' }
          : {}),
        ...(inlineSnmp && probe.method === 'SNMP_V3'
          ? {
              port: Number(probe.port ?? 161),
              securityUsername: probe.securityUsername ?? '',
              authProtocol: probe.authProtocol ?? 'SHA',
              authPassword: probe.authPassword ?? '',
              privacyProtocol: probe.privacyProtocol ?? 'AES',
              privacyPassword: probe.privacyPassword ?? '',
            }
          : {}),
      };
    });
    return {
      subnetRange,
      probes,
      ...(profileId != null ? { accessProfileId: profileId } : {}),
      timeout: Number(this.timeout()),
      retries: Number(this.retries()),
      port: 1,
    };
  }

  currentMonitoringSnmpCredentials(): {
    snmpVersion: string;
    community: string;
    securityUsername: string;
    authProtocol: string;
    authPassword: string;
    privacyProtocol: string;
    privacyPassword: string;
  } | null {
    if (this.accessProfileId() != null) {
      return null;
    }
    const snmpProbe = this.preferredSnmpProbe();
    if (!snmpProbe) {
      return null;
    }
    return {
      snmpVersion: this.snmpVersionForProbe(snmpProbe.method),
      community: snmpProbe.community ?? '',
      securityUsername: snmpProbe.securityUsername ?? '',
      authProtocol: snmpProbe.authProtocol ?? 'SHA',
      authPassword: snmpProbe.authPassword ?? '',
      privacyProtocol: snmpProbe.privacyProtocol ?? 'AES',
      privacyPassword: snmpProbe.privacyPassword ?? '',
    };
  }

  currentAccessProfileIdForActivation(): number | null {
    const probes = this.selectedProbes();
    const hasSnmp = probes.some((p) => isSnmpMethod(p.method));
    if (!hasSnmp) {
      return null;
    }
    return this.accessProfileId();
  }

  private preferredSnmpProbe(): DiscoveryProbeConfig | undefined {
    const probes = this.selectedProbes();
    return (
      probes.find((p) => p.method === 'SNMP_V3') ??
      probes.find((p) => p.method === 'SNMP_V2') ??
      probes.find((p) => p.method === 'SNMP_V1')
    );
  }

  private snmpVersionForProbe(method: DiscoveryMethod): string {
    switch (method) {
      case 'SNMP_V1':
        return 'v1';
      case 'SNMP_V3':
        return 'v3';
      default:
        return 'v2c';
    }
  }
}
