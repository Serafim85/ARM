import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { finalize } from 'rxjs';
import { AppRole, AuthService } from '../../../../auth.service';
import { MonitoringTemplatePickerDialogComponent } from '../../../../components/monitoring-template-picker-dialog/monitoring-template-picker-dialog.component';
import { DeviceScanResult, MonitoringDeviceItem, MonitoringDeviceItemSelection } from '../../../../models';
import { NotifierService } from '../../../../notifier.service';
import { MonitoringService } from '../../../../services/monitoring.service';

@Component({
  selector: 'app-device-item-config-tab',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    InputTextModule,
    ButtonModule,
    CheckboxModule,
    TooltipModule,
    ConfirmDialogModule,
    MonitoringTemplatePickerDialogComponent,
  ],
  providers: [ConfirmationService],
  templateUrl: './device-item-config-tab.component.html',
  styleUrl: './device-item-config-tab.component.css',
})
export class DeviceItemConfigTabComponent {
  readonly device = input.required<DeviceScanResult>();

  protected readonly ms = inject(MonitoringService);
  private readonly notify = inject(NotifierService);
  private readonly auth = inject(AuthService);
  private readonly confirmation = inject(ConfirmationService);

  protected readonly search = signal('');
  protected readonly saving = signal(false);
  protected readonly templatesSaving = signal(false);
  protected readonly templatePickerOpen = signal(false);
  protected readonly draftTemplateIds = signal<string[]>([]);
  protected readonly editableItems = signal<MonitoringDeviceItem[]>([]);

  private skipDraftResetOnClose = false;

  private readonly sourceItems = computed(() => this.ms.deviceItems(this.device().id));

  protected readonly deviceMeta = computed(() => this.ms.deviceMeta(this.device().id));

  protected readonly assignedTemplateIds = computed(() =>
    this.ms.resolveDeviceTemplateIds(this.deviceMeta()),
  );

  protected readonly assignedTemplateNames = computed(() => {
    const catalog = new Map(
      (this.ms.monitoringTemplates() ?? []).map((template) => [String(template.id), template.name]),
    );
    return this.assignedTemplateIds().map((id) => catalog.get(id) ?? id);
  });

  protected readonly assignedTemplatesTooltip = computed(() => {
    const names = this.assignedTemplateNames();
    if (names.length === 0) {
      return 'Шаблоны не назначены';
    }
    return names.join('\n');
  });

  protected readonly canManageTemplates = computed(() => this.hasAnyRole('ADMIN', 'OPERATOR'));

  protected readonly filteredItems = computed(() => {
    const q = this.search().trim().toLowerCase();
    const items = this.editableItems();
    if (!q) return items;
    return items.filter((item) =>
      [item.name, item.itemKey, item.itemType, item.discoveryRuleKey ?? '']
        .join(' ')
        .toLowerCase()
        .includes(q),
    );
  });

  protected readonly discoveryState = computed(() => this.ms.deviceDiscoveryState(this.device().id));

  protected readonly activeCount = computed(() => this.editableItems().filter((item) => item.active).length);
  protected readonly totalCount = computed(() => this.editableItems().length);
  protected readonly hasChanges = computed(() => {
    const source = this.sourceItems();
    const edited = this.editableItems();
    if (source.length !== edited.length) return true;
    const sourceIndex = new Map(source.map((item) => [this.itemRowKey(item), item.active]));
    for (const row of edited) {
      if (sourceIndex.get(this.itemRowKey(row)) !== row.active) return true;
    }
    return false;
  });

  constructor() {
    effect(() => {
      const id = this.device().id;
      untracked(() => {
        this.ms.loadMonitoredDeviceMeta(id);
        this.ms.loadDeviceItems(id);
        this.ms.loadMonitoringTemplates();
      });
    });
    effect(() => {
      const items = this.sourceItems();
      this.editableItems.set(items.map((item) => ({ ...item })));
    });
  }

  protected openTemplatePicker(): void {
    if (!this.canManageTemplates()) {
      return;
    }
    this.ensureTemplatesLoaded();
    this.draftTemplateIds.set([...this.assignedTemplateIds()]);
    this.templatePickerOpen.set(true);
  }

  protected onTemplatePickerVisibleChange(visible: boolean): void {
    this.templatePickerOpen.set(visible);
    if (!visible) {
      if (!this.skipDraftResetOnClose) {
        this.draftTemplateIds.set([...this.assignedTemplateIds()]);
      }
      this.skipDraftResetOnClose = false;
    }
  }

  protected onTemplatePickerDraftChange(templateIds: string[]): void {
    this.skipDraftResetOnClose = true;
    this.draftTemplateIds.set(templateIds);
    this.confirmTemplateReplacement(templateIds);
  }

  protected onItemEnabledChange(item: MonitoringDeviceItem, active: boolean): void {
    this.editableItems.update((rows) =>
      rows.map((row) => (this.itemRowKey(row) === this.itemRowKey(item) ? { ...row, active } : row)),
    );
  }

  protected save(): void {
    if (this.saving()) return;
    const device = this.device();
    const activeItems: MonitoringDeviceItemSelection[] = this.editableItems()
      .filter((item) => item.active)
      .map((item) => ({
        itemUuid: item.itemUuid,
        instanceKey: item.instanceKey,
      }));
    this.saving.set(true);
    this.ms
      .updateDeviceItems(device.id, activeItems)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.ms.loadDeviceItems(device.id);
          this.ms.loadDeviceItemState(device.id);
          this.ms.loadDeviceOpenEventCount(device);
          this.notify.success('Настройки мониторинга сохранены.', 'Мониторинг');
        },
        error: (error) =>
          this.notify.error(
            this.resolveError(error, 'Не удалось сохранить настройки мониторинга item.'),
            'Мониторинг',
          ),
      });
  }

  private confirmTemplateReplacement(templateIds: string[]): void {
    const current = this.assignedTemplateIds();
    const next = Array.from(new Set(templateIds.map((id) => id.trim()).filter((id) => id.length > 0)));
    const unchanged =
      current.length === next.length && current.every((id, index) => id === next[index]);
    if (unchanged) {
      this.templatePickerOpen.set(false);
      return;
    }
    if (next.length === 0) {
      this.notify.warn('Выберите хотя бы один шаблон мониторинга.', 'Мониторинг');
      this.draftTemplateIds.set([...current]);
      return;
    }

    this.confirmation.confirm({
      header: 'Замена шаблонов мониторинга',
      message: 'Смена шаблонов пересоберёт каталог item мониторинга. Продолжить?',
      acceptLabel: 'Продолжить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'ns-action-blue',
      rejectButtonStyleClass: 'p-button-outlined ns-action-blue',
      accept: () => this.applyTemplateReplacement(next),
      reject: () => {
        this.draftTemplateIds.set([...current]);
      },
    });
  }

  private applyTemplateReplacement(templateIds: string[]): void {
    const device = this.device();
    this.templatesSaving.set(true);
    this.ms
      .replaceDeviceTemplates(device, templateIds)
      .pipe(finalize(() => this.templatesSaving.set(false)))
      .subscribe({
        next: () => {
          this.templatePickerOpen.set(false);
          this.draftTemplateIds.set([...templateIds]);
          this.reloadDeviceMonitoringState(device);
          this.notify.success('Шаблоны мониторинга обновлены.', 'Мониторинг');
        },
        error: (error) => {
          this.draftTemplateIds.set([...this.assignedTemplateIds()]);
          this.notify.error(
            this.resolveError(error, 'Не удалось обновить шаблоны мониторинга.'),
            'Мониторинг',
          );
        },
      });
  }

  private reloadDeviceMonitoringState(device: DeviceScanResult): void {
    this.ms.loadMonitoredDeviceMeta(device.id);
    this.ms.loadDeviceItems(device.id);
    this.ms.loadDeviceItemState(device.id);
    this.ms.loadDeviceDiscoveryState(device.id);
    this.ms.loadDeviceOpenEventCount(device);
  }

  private ensureTemplatesLoaded(): void {
    if (this.ms.templatesLoading()) return;
    if ((this.ms.monitoringTemplates() ?? []).length > 0) return;
    this.ms.loadMonitoringTemplates();
  }

  private hasAnyRole(...roles: AppRole[]): boolean {
    const current = this.auth.authSession()?.roles ?? [];
    return roles.some((role) => current.includes(role));
  }

  private resolveError(error: unknown, fallback: string): string {
    const message = (error as { error?: { message?: string } })?.error?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }

  private itemRowKey(item: MonitoringDeviceItem): string {
    return `${item.itemUuid}::${item.instanceKey ?? ''}`;
  }
}
