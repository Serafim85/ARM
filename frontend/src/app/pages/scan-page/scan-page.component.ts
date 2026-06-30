import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressBarModule } from 'primeng/progressbar';
import { RadioButtonModule } from 'primeng/radiobutton';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { AuthService } from '../../auth.service';
import { AppRole } from '../../models';
import { NotifierService } from '../../notifier.service';
import { MonitoringService } from '../../services/monitoring.service';
import { ScanService } from '../../services/scan.service';
import { ScanJobsService } from '../../services/scan-jobs.service';
import { DiscoveryProbesFormComponent } from '../../components/discovery-probes-form/discovery-probes-form.component';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import {
  MonitoringTemplatePickerDialogComponent,
  type MonitoringTemplateSelection,
} from '../../components/monitoring-template-picker-dialog/monitoring-template-picker-dialog.component';
import { ScanResultsTableComponent } from '../../components/scan-results-table/scan-results-table.component';

@Component({
  selector: 'app-scan-page',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    CheckboxModule,
    ProgressBarModule,
    DialogModule,
    TooltipModule,
    RadioButtonModule,
    DeviceOptionSelectComponent,
    DiscoveryProbesFormComponent,
    ScanResultsTableComponent,
    MonitoringTemplatePickerDialogComponent,
  ],
  templateUrl: './scan-page.component.html',
  styleUrl: './scan-page.component.css',
})
export class ScanPageComponent {
  protected readonly scan = inject(ScanService);
  protected readonly mon = inject(MonitoringService);
  private readonly scanJobs = inject(ScanJobsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotifierService);

  protected readonly createJobDialogOpen = signal(false);
  protected readonly jobName = signal('Автосканирование');
  /** Выбор интервала автозапуска для пользователя (CRON генерируется на основе этого значения). */
  protected readonly jobIntervalPreset = signal<JobIntervalPreset>('EVERY_30_MIN');
  protected readonly jobEnabled = signal(true);
  protected readonly jobAutoMonitoringEnabled = signal(false);
  protected readonly jobAutoMonitoringMode = signal<JobAutoMonitoringMode>('AUTO');
  protected readonly jobMonitoringTemplateIds = signal<string[]>([]);
  protected readonly monitoringTemplatePickerOpen = signal(false);
  protected readonly jobMonitoringTemplatePickerOpen = signal(false);
  protected readonly autoMonitoringModeAutoTooltip =
    'Шаблон подбирается по вендору, модели и версии прошивки. При нескольких совпадениях выбирается шаблон с наивысшим приоритетом (0–100).';

  constructor() {
    this.mon.resetScanMonitoringTemplateSelection();
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      if (params.get('createJob') === '1') {
        this.openCreateScanJobDialog();
        void this.router.navigate([], {
          queryParams: { createJob: null },
          queryParamsHandling: 'merge',
          replaceUrl: true,
        });
      }
    });
  }

  protected onResultSelectionChange(event: { ip: string; checked: boolean }): void {
    this.scan.toggleResultSelection(event.ip, event.checked);
  }

  protected onJobIntervalPresetChangeFromSelect(value: string | number | null): void {
    if (value == null) return;
    this.jobIntervalPreset.set(String(value) as any);
  }

  protected setJobAutoMonitoringMode(next: JobAutoMonitoringMode): void {
    this.jobAutoMonitoringMode.set(next);
    if (next === 'AUTO') {
      this.jobMonitoringTemplateIds.set([]);
    }
  }

  protected readonly jobIntervalOptions: Array<{ label: string; value: JobIntervalPreset }> = [
    { label: 'Каждые 5 минут', value: 'EVERY_5_MIN' },
    { label: 'Каждые 15 минут', value: 'EVERY_15_MIN' },
    { label: 'Каждые 30 минут', value: 'EVERY_30_MIN' },
    { label: 'Каждый час', value: 'EVERY_1_HOUR' },
    { label: 'Каждые 6 часов', value: 'EVERY_6_HOURS' },
    { label: 'Каждый день в 02:00', value: 'EVERY_DAY_02_00' },
  ];

  protected readonly templateSelectOptions = computed(() =>
    this.mon.monitoringTemplates().map((t) => ({ label: t.name, value: t.id }))
  );

  protected readonly selectedMonitoringTemplateNames = computed(() => {
    const index = new Map(this.mon.monitoringTemplates().map((t) => [t.id, t.name] as const));
    return (this.mon.selectedMonitoringTemplateIds() ?? []).map((id) => index.get(id)).filter((v): v is string => !!v);
  });

  protected readonly selectedJobMonitoringTemplateNames = computed(() => {
    const index = new Map(this.mon.monitoringTemplates().map((t) => [t.id, t.name] as const));
    return (this.jobMonitoringTemplateIds() ?? []).map((id) => index.get(id)).filter((v): v is string => !!v);
  });

  protected canRunScan(): boolean {
    return this.hasAnyRole('ADMIN', 'OPERATOR');
  }

  protected canManageMonitoring(): boolean {
    return this.hasAnyRole('ADMIN', 'OPERATOR');
  }

  protected hasMonitoringTemplateSelection(): boolean {
    return this.mon.monitoringTemplateAutoDetection() || this.mon.selectedMonitoringTemplateIds().length > 0;
  }

  protected onMonitoringTemplateSelectionChange(selection: MonitoringTemplateSelection): void {
    this.mon.setMonitoringTemplateSelection(selection.templateIds, selection.autoDetection);
  }

  protected setMonitoringForSelected(enabled: boolean): void {
    const selected = this.scan.selectedResultIps();
    if (!selected.length) return;

    const devices = this.scan.scanResults().filter((d) => selected.includes(d.ip));

    if (enabled) {
      const templateIds = this.mon.selectedMonitoringTemplateIds();
      const profileId = this.scan.currentAccessProfileIdForActivation();
      this.mon
        .activateMonitoring(
          devices,
          templateIds,
          profileId == null ? this.scan.currentMonitoringSnmpCredentials() : null,
          profileId
        )
        .subscribe({
        next: (result) => {
          this.mon.applyMonitoredDevices(result);
          this.scan.selectedResultIps.set([]);
          this.notify.success(`На мониторинг поставлено устройств: ${devices.length}.`, 'Мониторинг');
        },
        error: () =>
          this.notify.error('Не удалось поставить устройства на мониторинг. Проверьте backend.', 'Мониторинг'),
      });
    } else {
      this.mon.deactivateMonitoring(selected).subscribe({
        next: (result) => {
          this.mon.applyMonitoredDevices(result);
          this.scan.selectedResultIps.set([]);
          this.notify.success(`С мониторинга снято устройств: ${selected.length}.`, 'Мониторинг');
        },
        error: () =>
          this.notify.error('Не удалось снять устройства с мониторинга. Проверьте backend.', 'Мониторинг'),
      });
    }
  }

  protected openMonitoringTemplatePicker(): void {
    this.ensureTemplatesLoaded();
    this.monitoringTemplatePickerOpen.set(true);
  }

  protected openJobMonitoringTemplatePicker(): void {
    this.ensureTemplatesLoaded();
    this.jobMonitoringTemplatePickerOpen.set(true);
  }

  protected openCreateScanJobDialog(): void {
    this.jobName.set(`Автосканирование ${this.scan.subnetRange().trim()}`.trim());
    this.jobIntervalPreset.set('EVERY_30_MIN');
    this.jobEnabled.set(true);
    this.jobAutoMonitoringEnabled.set(false);
    this.jobAutoMonitoringMode.set('AUTO');
    this.jobMonitoringTemplateIds.set(this.mon.selectedMonitoringTemplateIds());
    this.createJobDialogOpen.set(true);
  }

  protected createScanJob(): void {
    const name = this.jobName().trim();
    if (!name) {
      this.notify.warn('Укажите название задачи.', 'Автосканирование');
      return;
    }
    const subnetRange = this.scan.resolveSubnetRange();
    if (!subnetRange) {
      return;
    }
    const cron = this.cronFromIntervalPreset(this.jobIntervalPreset());
    const autoMonitoringEnabled = this.jobAutoMonitoringEnabled();
    const autoMonitoringMode = this.jobAutoMonitoringMode();
    const request = {
      scan: this.scan.currentScanRequest(subnetRange),
      autoMonitoringEnabled,
      monitoringTemplateIds: autoMonitoringEnabled && autoMonitoringMode === 'MANUAL'
        ? Array.from(
            new Set(
              (this.jobMonitoringTemplateIds() ?? [])
                .map((v) => String(v ?? '').trim())
                .filter((v) => v.length > 0)
            )
          )
        : [],
    };
    this.scanJobs
      .create({ name, enabled: this.jobEnabled(), cron, request })
      .subscribe({
        next: (job) => {
          void job;
          this.notify.success('Задача создана.', 'Автосканирование');
          this.createJobDialogOpen.set(false);
        },
        error: (error) =>
          this.notify.error(
            (error as { error?: { message?: string } })?.error?.message ??
              'Не удалось создать задачу. Проверьте backend.',
            'Автосканирование'
          ),
      });
  }

  private cronFromIntervalPreset(
    preset: JobIntervalPreset
  ): string {
    switch (preset) {
      case 'EVERY_5_MIN':
        return '0 */5 * * * *';
      case 'EVERY_15_MIN':
        return '0 */15 * * * *';
      case 'EVERY_1_HOUR':
        return '0 0 * * * *';
      case 'EVERY_6_HOURS':
        return '0 0 */6 * * *';
      case 'EVERY_DAY_02_00':
        return '0 0 2 * * *';
      case 'EVERY_30_MIN':
      default:
        return '0 */30 * * * *';
    }
  }

  private hasAnyRole(...roles: AppRole[]): boolean {
    const current = this.auth.authSession()?.roles ?? [];
    return roles.some((r) => current.includes(r));
  }

  private ensureTemplatesLoaded(): void {
    if (this.mon.templatesLoading()) return;
    if ((this.mon.monitoringTemplates() ?? []).length > 0) return;
    this.mon.loadMonitoringTemplates();
  }
}

type JobIntervalPreset =
  | 'EVERY_5_MIN'
  | 'EVERY_15_MIN'
  | 'EVERY_30_MIN'
  | 'EVERY_1_HOUR'
  | 'EVERY_6_HOURS'
  | 'EVERY_DAY_02_00';

type JobAutoMonitoringMode = 'AUTO' | 'MANUAL';
