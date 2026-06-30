import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { AuthService } from '../../../auth.service';
import { DeviceBackupConfig, DeviceScanResult } from '../../../models';
import { NotifierService } from '../../../notifier.service';
import { MonitoringService } from '../../../services/monitoring.service';

@Component({
  selector: 'app-device-backup-section',
  standalone: true,
  imports: [ButtonModule, FormsModule, InputTextModule, TableModule],
  templateUrl: './device-backup-section.component.html',
  styleUrl: './device-backup-section.component.css',
})
export class DeviceBackupSectionComponent {
  readonly device = input.required<DeviceScanResult>();
  readonly embedded = input(false);

  protected readonly ms = inject(MonitoringService);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotifierService);

  protected readonly expanded = signal(false);

  protected readonly backupLoading = signal(false);
  protected readonly backupSearch = signal('');

  protected readonly filteredBackups = computed(() => {
    const list = this.snapshot()?.backups ?? [];
    const q = this.backupSearch().trim().toLowerCase();
    if (!q) return list;
    return list.filter((b) =>
      [b.name, b.source, b.status, b.baselineStatus, b.createdAt, b.size, b.comparedAt ?? '']
        .join(' ')
        .toLowerCase()
        .includes(q),
    );
  });

  protected readonly backupBaselineDiffCount = computed(
    () => (this.snapshot()?.backups ?? []).filter((b) => b.baselineStatus === 'Есть отличия').length,
  );

  constructor() {
    effect(() => {
      const d = this.device();
      if (!d) return;
      if (this.embedded() || this.expanded()) {
        this.ms.loadBackupSnapshot(d);
      }
    });
  }

  protected snapshot() {
    return this.ms.backupSnapshot(this.device().id);
  }

  protected snapshotLoading(): boolean {
    return this.ms.isBackupSnapshotLoading(this.device().id);
  }

  protected actionInProgress(): boolean {
    return this.backupLoading() || this.snapshotLoading();
  }

  protected toggleExpanded(): void {
    this.expanded.update((v) => !v);
  }

  protected canManageBaselines(): boolean {
    return this.auth.authSession()?.roles.includes('ADMIN') ?? false;
  }

  protected setCurrentConfigAsBaseline(): void {
    this.backupLoading.set(true);
    this.ms.setCurrentConfigAsBaseline(this.device()).subscribe({
      next: () => {
        this.notify.success('Текущее состояние сохранено как эталон и применено для сравнения.', 'Бэкапы');
        this.backupLoading.set(false);
      },
      error: (e) => {
        this.notify.error(
          e?.error?.message ?? 'Не удалось сформировать эталон из текущей конфигурации.',
          'Бэкапы'
        );
        this.backupLoading.set(false);
      },
    });
  }

  protected uploadBaselineFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    this.backupLoading.set(true);

    reader.onload = () => {
      this.ms.uploadBaselineContent(this.device(), file.name, String(reader.result ?? '')).subscribe({
        next: () => {
          this.notify.success(`Эталонный конфиг ${file.name} загружен.`, 'Бэкапы');
          this.backupLoading.set(false);
          input.value = '';
        },
        error: (e) => {
          this.notify.error(e?.error?.message ?? 'Не удалось загрузить эталонную конфигурацию.', 'Бэкапы');
          this.backupLoading.set(false);
          input.value = '';
        },
      });
    };
    reader.onerror = () => {
      this.notify.error('Не удалось прочитать файл эталонной конфигурации.', 'Бэкапы');
      this.backupLoading.set(false);
      input.value = '';
    };
    reader.readAsText(file);
  }

  protected useBackupAsBaseline(backup: DeviceBackupConfig): void {
    this.backupLoading.set(true);
    this.ms.useBackupAsBaseline(this.device(), backup).subscribe({
      next: () => {
        this.notify.success(`Бэкап ${backup.name} выбран как эталон.`, 'Бэкапы');
        this.backupLoading.set(false);
      },
      error: (e) => {
        this.notify.error(e?.error?.message ?? 'Не удалось выбрать бэкап как эталон.', 'Бэкапы');
        this.backupLoading.set(false);
      },
    });
  }

  protected downloadBackup(backup: DeviceBackupConfig): void {
    this.ms.downloadBackup(this.device(), backup);
  }

  protected compareBackupWithBaseline(backup: DeviceBackupConfig): void {
    this.backupLoading.set(true);
    this.ms.compareBackupWithBaseline(this.device(), backup).subscribe({
      next: (result) => {
        this.notify.success(`${result.backupName}: ${result.baselineStatus}. ${result.summary}`, 'Сравнение');
        this.backupLoading.set(false);
      },
      error: (e) => {
        this.notify.error(e?.error?.message ?? 'Не удалось выполнить сравнение с эталоном.', 'Бэкапы');
        this.backupLoading.set(false);
      },
    });
  }
}
