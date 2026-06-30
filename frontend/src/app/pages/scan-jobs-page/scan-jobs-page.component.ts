import { Component, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { Menu, MenuModule } from 'primeng/menu';
import { RadioButtonModule } from 'primeng/radiobutton';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DiscoveryProbesFormComponent } from '../../components/discovery-probes-form/discovery-probes-form.component';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import {
  MonitoringTemplatePickerDialogComponent,
  type MonitoringTemplateSelection,
} from '../../components/monitoring-template-picker-dialog/monitoring-template-picker-dialog.component';
import { ScanResultsTableComponent } from '../../components/scan-results-table/scan-results-table.component';
import { buildDeviceSearchText } from '../../utils/scan-result.util';
import { ScanJobStatus, DeviceScanResult, ScanJob } from '../../models';
import { NotifierService } from '../../notifier.service';
import { MonitoringService } from '../../services/monitoring.service';
import { ScanJobsService } from '../../services/scan-jobs.service';
import { ScanService } from '../../services/scan.service';
import { formatMonitoringEventDate } from '../../utils/monitoring-event-formatters';

@Component({
  selector: 'app-scan-jobs-page',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    CheckboxModule,
    RadioButtonModule,
    TagModule,
    MenuModule,
    ConfirmDialogModule,
    SelectModule,
    DeviceOptionSelectComponent,
    TooltipModule,
    MonitoringTemplatePickerDialogComponent,
    ScanResultsTableComponent,
    DiscoveryProbesFormComponent,
  ],
  providers: [ConfirmationService],
  templateUrl: './scan-jobs-page.component.html',
  styleUrl: './scan-jobs-page.component.css',
})
export class ScanJobsPageComponent {
  @ViewChild('jobsRowMenu') private jobsRowMenu?: Menu;

  protected readonly jobs = inject(ScanJobsService);
  protected readonly scan = inject(ScanService);
  protected readonly mon = inject(MonitoringService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly confirmation = inject(ConfirmationService);
  private readonly notify = inject(NotifierService);

  protected readonly hasJobs = computed(() => this.jobs.sortedJobs().length > 0);

  protected readonly jobsRowMenuItems = signal<MenuItem[]>([]);
  protected readonly resultsViewOpen = signal(false);
  protected readonly resultsJob = signal<ScanJob | null>(null);
  protected readonly selectedJobs = signal<ScanJob[]>([]);

  protected readonly editDialogOpen = signal(false);
  protected readonly editLoading = signal(false);
  protected readonly editJobId = signal<number | null>(null);
  protected readonly editName = signal('');
  protected readonly editCron = signal('0 */30 * * * *');
  protected readonly editEnabled = signal(true);
  protected readonly cronSelectValue = signal<string | null>(null);
  protected readonly editAutoMonitoringEnabled = signal(false);
  protected readonly editAutoMonitoringMode = signal<JobAutoMonitoringMode>('AUTO');
  protected readonly editMonitoringTemplateIds = signal<string[]>([]);
  protected readonly monitoringTemplatePickerOpen = signal(false);
  protected readonly autoMonitoringModeAutoTooltip =
    'Шаблон подбирается по вендору, модели и версии прошивки. При нескольких совпадениях выбирается шаблон с наивысшим приоритетом (0–100).';
  protected readonly editMonitoringTemplatePickerOpen = signal(false);
  /** Сохранённые параметры сканирования задачи (нужны для PUT /api/scan-jobs/{id}). */
  protected readonly editScanRequest = signal<unknown | null>(null);

  protected readonly templateSelectOptions = computed(() =>
    this.mon.monitoringTemplates().map((t) => ({ label: t.name, value: t.id }))
  );

  protected readonly selectedMonitoringTemplateNames = computed(() => {
    const index = new Map(this.mon.monitoringTemplates().map((t) => [t.id, t.name] as const));
    return (this.mon.selectedMonitoringTemplateIds() ?? [])
      .map((id) => index.get(id))
      .filter((v): v is string => !!v);
  });

  protected readonly selectedEditMonitoringTemplateNames = computed(() => {
    const index = new Map(this.mon.monitoringTemplates().map((t) => [t.id, t.name] as const));
    return (this.editMonitoringTemplateIds() ?? [])
      .map((id) => index.get(id))
      .filter((v): v is string => !!v);
  });

  protected readonly cronSelectOptions = computed(() =>
    Object.entries(this.cronLabels).map(([value, label]) => ({ label, value }))
  );

  private readonly cronLabels: Record<string, string> = {
    '0 */5 * * * *': 'Каждые 5 минут',
    '0 */15 * * * *': 'Каждые 15 минут',
    '0 */30 * * * *': 'Каждые 30 минут',
    '0 0 * * * *': 'Каждый час',
    '0 0 */6 * * *': 'Каждые 6 часов',
    '0 0 2 * * *': 'Каждый день в 02:00',
  };

  protected readonly lastResultDevices = signal<DeviceScanResult[]>([]);
  protected readonly lastResultFilter = signal('');
  protected readonly selectedIps = signal<string[]>([]);

  protected readonly discoveryViewOpen = signal(false);
  protected readonly discoveryLoading = signal(false);
  protected readonly discoveryDevices = signal<DeviceScanResult[]>([]);
  protected readonly discoverySelectedIps = signal<string[]>([]);
  protected readonly discoveryFilter = signal('');

  protected readonly filteredDiscoveryDevices = computed(() => {
    const query = this.discoveryFilter().trim().toLowerCase();
    const results = this.discoveryDevices();
    if (!query) {
      return results;
    }
    return results.filter((d) => this.discoverySearchText(d).includes(query));
  });

  protected readonly filteredLastResultDevices = computed(() => {
    const query = this.lastResultFilter().trim().toLowerCase();
    const results = this.lastResultDevices();
    if (!query) {
      return results;
    }
    return results.filter((d) => this.discoverySearchText(d).includes(query));
  });

  constructor() {
    this.mon.resetScanMonitoringTemplateSelection();
    this.jobs.load();

    // Позволяет открыть «Обнаружено новых» по прямой ссылке из других страниц.
    // Пример: /scan-jobs?discovery=1
    const discoveryParam = this.route.snapshot.queryParamMap.get('discovery');
    if (discoveryParam === '1' || discoveryParam === 'true') {
      this.openDiscoveryView();
    }
  }

  protected hasMonitoringTemplateSelection(): boolean {
    return this.mon.monitoringTemplateAutoDetection() || this.mon.selectedMonitoringTemplateIds().length > 0;
  }

  protected onMonitoringTemplateSelectionChange(selection: MonitoringTemplateSelection): void {
    this.mon.setMonitoringTemplateSelection(selection.templateIds, selection.autoDetection);
  }

  protected openMonitoringTemplatePicker(): void {
    this.ensureTemplatesLoaded();
    this.monitoringTemplatePickerOpen.set(true);
  }

  protected openEditMonitoringTemplatePicker(): void {
    this.ensureTemplatesLoaded();
    this.editMonitoringTemplatePickerOpen.set(true);
  }

  protected setEditAutoMonitoringMode(next: JobAutoMonitoringMode): void {
    this.editAutoMonitoringMode.set(next);
    if (next === 'AUTO') {
      this.editMonitoringTemplateIds.set([]);
    }
  }

  private discoverySearchText(device: DeviceScanResult): string {
    return buildDeviceSearchText(device);
  }

  protected onDiscoverySelectionChange(event: { ip: string; checked: boolean }): void {
    this.setDiscoveryIpSelected(event.ip, event.checked);
  }

  protected onLastResultSelectionChange(event: { ip: string; checked: boolean }): void {
    this.setLastResultIpSelected(event.ip, event.checked);
  }

  protected goToCreateJob(): void {
    void this.router.navigate(['/scan'], { queryParams: { createJob: 1 } });
  }

  protected cronLabel(cron: string | null | undefined): string {
    if (!cron) return '—';
    const c = String(cron).trim();
    return this.cronLabels[c] ?? c;
  }

  protected scanJobStatusLabelForJob(job: ScanJob): string {
    if (job.lastStatus === 'RUNNING' && job.totalAddresses > 0) {
      return `Выполняется (${job.scannedAddresses}/${job.totalAddresses})`;
    }
    return this.scanJobStatusLabel(job.lastStatus);
  }

  protected scanJobStatusLabel(status: ScanJobStatus | null | undefined): string {
    switch (status) {
      case 'RUNNING':
        return 'Выполняется';
      case 'SUCCESS':
        return 'Успешно';
      case 'FAILED':
        return 'Ошибка';
      default:
        return '—';
    }
  }

  protected scanJobStatusTagSeverity(
    status: ScanJobStatus | null | undefined
  ): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    switch (status) {
      case 'RUNNING':
        return 'info';
      case 'SUCCESS':
        return 'success';
      case 'FAILED':
        return 'danger';
      default:
        return 'secondary';
    }
  }

  protected formatDateTime(iso: string | null | undefined): string {
    return formatMonitoringEventDate(iso ?? null);
  }

  protected hasLastResult(job: ScanJob): boolean {
    return job.lastRunAt != null;
  }

  protected openJobsRowMenu(event: Event, job: ScanJob): void {
    event.stopPropagation();
    this.jobsRowMenuItems.set([
      {
        label: 'Запустить',
        icon: 'pi pi-play',
        disabled: this.jobs.loading() || job.lastStatus === 'RUNNING',
        command: () => this.runNow(job),
      },
      {
        label: 'Редактировать',
        icon: 'pi pi-pencil',
        disabled: this.jobs.loading(),
        command: () => this.openEditDialog(job),
      },
      {
        label: job.enabled ? 'Выключить' : 'Включить',
        icon: job.enabled ? 'pi pi-times-circle' : 'pi pi-check-circle',
        disabled: this.jobs.loading(),
        command: () => this.toggleEnabled(job),
      },
      {
        label: 'Удалить',
        icon: 'pi pi-trash',
        disabled: this.jobs.loading(),
        command: () => this.confirmDelete(job),
      },
    ]);
    this.jobsRowMenu?.toggle(event);
  }

  protected openResultsActionsMenu(event: Event): void {
    const job = this.resultsJob();
    if (!job) return;
    this.openJobsRowMenu(event, job);
  }

  protected toggleEnabled(job: ScanJob): void {
    const id = job.id;
    const call = job.enabled ? this.jobs.disable(id) : this.jobs.enable(id);
    call.subscribe({
      next: (updated) => {
        this.notify.success(
          updated.enabled ? 'Задача включена.' : 'Задача выключена.',
          'Автосканирование'
        );

        // Обновляем локально, чтобы меню/шапка результатов сразу отразили новое состояние.
        this.jobs.jobs.update((all) => all.map((j) => (j.id === updated.id ? updated : j)));
        const current = this.resultsJob();
        if (current?.id === updated.id) {
          this.resultsJob.set(updated);
        }
      },
      error: () =>
        this.notify.error(
          'Не удалось изменить статус задачи. Проверьте backend.',
          'Автосканирование'
        ),
    });
  }

  protected openEditDialog(job: ScanJob): void {
    this.editJobId.set(job.id);
    this.editName.set(job.name ?? '');
    this.editEnabled.set(!!job.enabled);
    this.cronSelectValue.set(job.cron ?? null);
    this.editAutoMonitoringEnabled.set(false);
    this.editAutoMonitoringMode.set('AUTO');
    this.editMonitoringTemplateIds.set([]);
    this.editScanRequest.set(null);
    this.editDialogOpen.set(true);

    this.editLoading.set(true);
    this.jobs.getDetails(job.id).subscribe({
      next: (details) => {
        const d = details as {
          request?: {
            scan?: unknown;
            autoMonitoringEnabled?: boolean;
            monitoringTemplateIds?: unknown;
          };
        };
        const scanReq = d?.request?.scan ?? null;
        const enabled = !!d?.request?.autoMonitoringEnabled;
        const idsRaw = d?.request?.monitoringTemplateIds;
        const ids = Array.isArray(idsRaw)
          ? idsRaw
              .map((v) => String(v ?? '').trim())
              .filter((v) => v.length > 0)
          : [];

        this.editScanRequest.set(scanReq);
        this.scan.loadProbesFromScanRequest(scanReq);
        this.editAutoMonitoringEnabled.set(enabled);
        this.editMonitoringTemplateIds.set(Array.from(new Set(ids)));
        this.editAutoMonitoringMode.set(ids.length > 0 ? 'MANUAL' : 'AUTO');
        this.editLoading.set(false);
      },
      error: () => {
        this.editLoading.set(false);
        this.notify.error(
          'Не удалось загрузить параметры задачи для редактирования. Проверьте backend.',
          'Автосканирование'
        );
      },
    });
  }

  protected onCronPresetChange(value: string | null): void {
    this.cronSelectValue.set(value);
    if (value) {
      this.editCron.set(value);
    }
  }

  protected onCronPresetChangeFromSelect(value: string | number | null): void {
    this.onCronPresetChange(value == null ? null : String(value));
  }

  protected saveEdit(): void {
    const id = this.editJobId();
    if (id == null) return;
    const name = this.editName().trim();
    const cron = (this.cronSelectValue() ?? '').trim();
    if (!name || !cron) {
      this.notify.warn('Заполните название и расписание.', 'Автосканирование');
      return;
    }
    const scanReq = this.editScanRequest();
    if (scanReq == null) {
      this.notify.warn(
        'Не удалось определить параметры сканирования задачи. Обновите страницу и попробуйте снова.',
        'Автосканирование'
      );
      return;
    }
    const subnetRange = this.scan.resolveSubnetRange();
    if (!subnetRange) {
      return;
    }
    if (!this.scan.validateProbes()) {
      return;
    }
    const autoMonitoringEnabled = this.editAutoMonitoringEnabled();
    const autoMonitoringMode = this.editAutoMonitoringMode();
    const request = {
      scan: this.scan.currentScanRequest(subnetRange),
      autoMonitoringEnabled,
      monitoringTemplateIds:
        autoMonitoringEnabled && autoMonitoringMode === 'MANUAL'
          ? Array.from(
              new Set(
                (this.editMonitoringTemplateIds() ?? [])
                  .map((v) => String(v ?? '').trim())
                  .filter((v) => v.length > 0)
              )
            )
          : [],
    };

    this.jobs.update(id, { name, enabled: this.editEnabled(), cron, request }).subscribe({
      next: (updated) => {
        this.jobs.jobs.update((all) => all.map((j) => (j.id === updated.id ? updated : j)));
        if (this.resultsJob()?.id === updated.id) {
          this.resultsJob.set(updated);
        }
        this.notify.success('Задача обновлена.', 'Автосканирование');
        this.editDialogOpen.set(false);
      },
      error: (error) =>
        this.notify.error(
          (error as { error?: { message?: string } })?.error?.message ??
            'Не удалось обновить задачу. Проверьте backend.',
          'Автосканирование'
        ),
    });
  }

  protected confirmDelete(job: ScanJob): void {
    this.confirmation.confirm({
      header: 'Удалить задачу?',
      message: `Удалить задачу «${job.name}»? Действие необратимо.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteJob(job),
    });
  }

  private deleteJob(job: ScanJob): void {
    this.jobs.delete(job.id).subscribe({
      next: () => {
        this.jobs.jobs.update((all) => all.filter((j) => j.id !== job.id));
        this.jobs.reloadDiscoverySummary();
        if (this.resultsJob()?.id === job.id) {
          this.closeResultsView();
        }
        this.notify.success('Задача удалена.', 'Автосканирование');
      },
      error: (error) =>
        this.notify.error(
          (error as { error?: { message?: string } })?.error?.message ??
            'Не удалось удалить задачу. Проверьте backend.',
          'Автосканирование'
        ),
    });
  }

  /** Запрашивает подтверждение и удаляет выбранные задачи */
  protected confirmDeleteSelected(): void {
    const selected = this.selectedJobs();
    if (selected.length === 0) return;

    const names = selected.map((j) => `«${j.name}»`).join(', ');
    this.confirmation.confirm({
      header: 'Удалить выбранные задачи?',
      message: `Будут удалены задачи: ${names}. Действие необратимо.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteSelectedJobs(selected),
    });
  }

  private deleteSelectedJobs(jobs: ScanJob[]): void {
    const ids = jobs.map((j) => j.id);
    this.jobs.deleteMany(ids).subscribe({
      next: () => {
        // Удаляем эти задачи из локального списка
        this.jobs.jobs.update((all) => all.filter((j) => !ids.includes(j.id)));
        // Сбрасываем выделение и обновляем счётчик «Обнаружено новых»
        this.selectedJobs.set([]);
        this.jobs.reloadDiscoverySummary();
        this.notify.success(`Удалено задач: ${ids.length}.`, 'Автосканирование');
      },
      error: (err) => {
        const msg = (err as any)?.error?.message ?? 'Не удалось удалить задачи. Проверьте backend.';
        this.notify.error(msg, 'Автосканирование');
      },
    });
  }

  protected runNow(job: ScanJob): void {
    if (job.lastStatus === 'RUNNING') {
      return;
    }
    this.jobs.runNow(job.id).subscribe({
      next: (start) => {
        this.jobs.markJobRunning(job.id, start.runId, start.totalAddresses);
        this.notify.info(`Задача «${job.name}» запущена.`, 'Автосканирование');
      },
      error: (err) => {
        const msg =
          (err as { error?: { message?: string } })?.error?.message ??
          'Не удалось запустить задачу. Проверьте backend.';
        this.notify.error(msg, 'Автосканирование');
      },
    });
  }

  protected isJobRunning(job: ScanJob): boolean {
    return job.lastStatus === 'RUNNING';
  }

  protected openLastResult(job: ScanJob, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    if (!this.hasLastResult(job)) {
      return;
    }
    this.closeDiscoveryView();
    this.jobs.getLastResult(job.id).subscribe({
      next: (devices) => {
        this.mon.resetScanMonitoringTemplateSelection();
        this.lastResultDevices.set(Array.isArray(devices) ? devices : []);
        this.lastResultFilter.set('');
        this.selectedIps.set([]);
        this.resultsJob.set(job);
        this.resultsViewOpen.set(true);
      },
      error: () =>
        this.notify.error(
          'Не удалось загрузить последний результат. Проверьте backend.',
          'Автосканирование'
        ),
    });
  }

  protected closeResultsView(): void {
    this.resultsViewOpen.set(false);
    this.resultsJob.set(null);
    this.lastResultDevices.set([]);
    this.lastResultFilter.set('');
    this.selectedIps.set([]);
  }

  protected backToJobs(): void {
    this.closeResultsView();
    this.closeDiscoveryView();
    this.jobs.load();
    this.selectedJobs.set([]);
  }

  protected openDiscoveryView(): void {
    this.mon.resetScanMonitoringTemplateSelection();
    this.resultsViewOpen.set(false);
    this.resultsJob.set(null);
    this.discoveryViewOpen.set(true);
    this.discoveryLoading.set(true);
    this.discoverySelectedIps.set([]);
    this.discoveryFilter.set('');
    this.jobs.getDiscoveredNotMonitoredDevices().subscribe({
      next: (devices) => {
        this.discoveryDevices.set(Array.isArray(devices) ? devices : []);
        this.discoveryLoading.set(false);
      },
      error: () => {
        this.discoveryLoading.set(false);
        this.discoveryViewOpen.set(false);
        this.notify.error(
          'Не удалось загрузить список устройств. Проверьте backend.',
          'Автосканирование'
        );
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
      if (selected) {
        next.add(ip);
      } else {
        next.delete(ip);
      }
      return [...next];
    });
  }

  protected activateDiscoveryMonitoring(): void {
    const ips = this.discoverySelectedIps();
    if (!ips.length) {
      return;
    }
    const devices = this.discoveryDevices().filter((d) => ips.includes(d.ip));
    if (!devices.length) {
      return;
    }
    const templateIds = this.mon.selectedMonitoringTemplateIds();
    this.mon.activateMonitoring(devices, templateIds, null).subscribe({
      next: (result) => {
        this.mon.applyMonitoredDevices(result);
        this.discoverySelectedIps.set([]);
        this.jobs.reloadDiscoverySummary();
        this.jobs.getDiscoveredNotMonitoredDevices().subscribe({
          next: (list) => {
            this.discoveryDevices.set(Array.isArray(list) ? list : []);
          },
          error: () => {},
        });
        this.notify.success(`На мониторинг поставлено устройств: ${devices.length}.`, 'Мониторинг');
      },
      error: () =>
        this.notify.error(
          'Не удалось поставить устройства на мониторинг. Проверьте backend.',
          'Мониторинг'
        ),
    });
  }

  protected isLastResultIpSelected(ip: string): boolean {
    return this.selectedIps().includes(ip);
  }

  protected setLastResultIpSelected(ip: string, selected: boolean): void {
    this.selectedIps.update((ips) => {
      const next = new Set(ips);
      if (selected) {
        next.add(ip);
      } else {
        next.delete(ip);
      }
      return [...next];
    });
  }

  protected setMonitoringForSelected(enabled: boolean): void {
    const selected = this.selectedIps();
    if (!selected.length) return;

    const devices = this.lastResultDevices().filter((d) => selected.includes(d.ip));
    if (enabled) {
      const templateIds = this.mon.selectedMonitoringTemplateIds();
      this.mon.activateMonitoring(devices, templateIds, null).subscribe({
        next: (result) => {
          this.mon.applyMonitoredDevices(result);
          this.selectedIps.set([]);
          this.jobs.reloadDiscoverySummary();
          this.notify.success(
            `На мониторинг поставлено устройств: ${devices.length}.`,
            'Мониторинг'
          );
        },
        error: () =>
          this.notify.error(
            'Не удалось поставить устройства на мониторинг. Проверьте backend.',
            'Мониторинг'
          ),
      });
      return;
    }

    this.mon.deactivateMonitoring(selected).subscribe({
      next: (result) => {
        this.mon.applyMonitoredDevices(result);
        this.selectedIps.set([]);
        this.jobs.reloadDiscoverySummary();
        this.notify.success(`С мониторинга снято устройств: ${selected.length}.`, 'Мониторинг');
      },
      error: () =>
        this.notify.error(
          'Не удалось снять устройства с мониторинга. Проверьте backend.',
          'Мониторинг'
        ),
    });
  }

  private ensureTemplatesLoaded(): void {
    if (this.mon.templatesLoading()) return;
    if ((this.mon.monitoringTemplates() ?? []).length > 0) return;
    this.mon.loadMonitoringTemplates();
  }
}

type JobAutoMonitoringMode = 'AUTO' | 'MANUAL';

