import { Component, OnInit, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TextareaModule } from 'primeng/textarea';
import type { AccessProfileDetail, UpsertAccessProfileRequest } from '../../models';
import { NotifierService } from '../../notifier.service';
import { AccessProfilesService } from '../../services/access-profiles.service';

type ProfileFormState = UpsertAccessProfileRequest & {
  editingId: number | null;
};

@Component({
  selector: 'app-access-profiles-tab',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PasswordModule,
    CheckboxModule,
    SelectModule,
    TextareaModule,
  ],
  templateUrl: './access-profiles-tab.component.html',
  styleUrl: './access-profiles-tab.component.css',
})
export class AccessProfilesTabComponent implements OnInit {
  private readonly profiles = inject(AccessProfilesService);
  private readonly notify = inject(NotifierService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly deleting = signal(false);
  protected readonly rows = signal<AccessProfileDetail[]>([]);
  protected readonly selectedRows = signal<AccessProfileDetail[]>([]);
  protected readonly dialogOpen = signal(false);
  protected readonly snmpV1Expanded = signal(false);
  protected readonly snmpV2Expanded = signal(true);
  protected readonly snmpV3Expanded = signal(false);
  protected readonly sshExpanded = signal(false);
  protected readonly httpsExpanded = signal(false);

  protected readonly snmpAuthProtocolOptions = [
    { label: 'MD5', value: 'MD5' },
    { label: 'SHA', value: 'SHA' },
  ];

  protected readonly snmpPrivacyProtocolOptions = [
    { label: 'DES', value: 'DES' },
    { label: 'AES', value: 'AES' },
  ];

  protected readonly form = signal<ProfileFormState>(this.emptyForm());

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.profiles.listDetails().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.selectedRows.set([]);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notify.error('Не удалось загрузить профили доступа.', 'Профили доступа');
      },
    });
  }

  protected openCreate(): void {
    this.form.set(this.emptyForm());
    this.snmpV1Expanded.set(false);
    this.snmpV2Expanded.set(true);
    this.snmpV3Expanded.set(false);
    this.sshExpanded.set(false);
    this.httpsExpanded.set(false);
    this.dialogOpen.set(true);
  }

  protected openEdit(row: AccessProfileDetail): void {
    this.form.set({
      editingId: row.id,
      name: row.name,
      description: row.description,
      snmpV1Enabled: row.snmpV1Enabled,
      snmpV1Port: row.snmpV1Port ?? 161,
      snmpV1Community: row.hasSnmpV1Community ? '' : 'public',
      clearSnmpV1Community: false,
      snmpV2Enabled: row.snmpV2Enabled,
      snmpV2Port: row.snmpV2Port ?? 161,
      snmpV2Community: row.hasSnmpV2Community ? '' : 'public',
      clearSnmpV2Community: false,
      snmpV3Enabled: row.snmpV3Enabled,
      snmpV3Port: row.snmpV3Port ?? 161,
      snmpV3SecurityUsername: row.snmpV3SecurityUsername ?? '',
      snmpV3AuthProtocol: row.snmpV3AuthProtocol ?? 'SHA',
      snmpV3AuthPassword: '',
      clearSnmpV3AuthPassword: false,
      snmpV3PrivacyProtocol: row.snmpV3PrivacyProtocol ?? 'AES',
      snmpV3PrivacyPassword: '',
      clearSnmpV3PrivacyPassword: false,
      sshEnabled: row.sshEnabled,
      sshPort: row.sshPort ?? 22,
      sshUsername: row.sshUsername ?? '',
      sshPassword: '',
      clearSshPassword: false,
      sshPrivateKeyPem: '',
      clearSshPrivateKey: false,
      sshPassphrase: '',
      clearSshPassphrase: false,
      httpsEnabled: row.httpsEnabled,
      httpsPort: row.httpsPort ?? 443,
      httpsUsername: row.httpsUsername ?? '',
      httpsPassword: '',
      clearHttpsPassword: false,
      httpsClientCertPem: '',
      clearHttpsClientCert: false,
      httpsClientKeyPem: '',
      clearHttpsClientKey: false,
      httpsInsecureSkipVerify: row.httpsInsecureSkipVerify,
    });
    this.snmpV1Expanded.set(row.snmpV1Enabled);
    this.snmpV2Expanded.set(row.snmpV2Enabled);
    this.snmpV3Expanded.set(row.snmpV3Enabled);
    this.sshExpanded.set(row.sshEnabled);
    this.httpsExpanded.set(row.httpsEnabled);
    this.dialogOpen.set(true);
  }

  protected save(): void {
    const state = this.form();
    const name = state.name.trim();
    if (!name) {
      this.notify.warn('Укажите имя профиля.', 'Профили доступа');
      return;
    }
    if (!state.snmpV1Enabled && !state.snmpV2Enabled && !state.snmpV3Enabled && !state.sshEnabled && !state.httpsEnabled) {
      this.notify.warn('Включите хотя бы один протокол.', 'Профили доступа');
      return;
    }
    if (
      state.snmpV1Enabled
      && !state.snmpV1Community?.trim()
      && !(state.editingId != null && this.editingRow()?.hasSnmpV1Community)
    ) {
      this.notify.warn('Для SNMP v1 укажите community string.', 'Профили доступа');
      return;
    }
    if (
      state.snmpV2Enabled
      && !state.snmpV2Community?.trim()
      && !(state.editingId != null && this.editingRow()?.hasSnmpV2Community)
    ) {
      this.notify.warn('Для SNMP v2c укажите community string.', 'Профили доступа');
      return;
    }
    if (state.snmpV3Enabled && !state.snmpV3SecurityUsername?.trim()) {
      this.notify.warn('Для SNMP v3 укажите имя пользователя.', 'Профили доступа');
      return;
    }

    const body: UpsertAccessProfileRequest = {
      name,
      description: state.description?.trim() || null,
      snmpV1Enabled: state.snmpV1Enabled,
      snmpV1Port: state.snmpV1Port,
      snmpV1Community: state.snmpV1Community?.trim() || null,
      clearSnmpV1Community: state.clearSnmpV1Community,
      snmpV2Enabled: state.snmpV2Enabled,
      snmpV2Port: state.snmpV2Port,
      snmpV2Community: state.snmpV2Community?.trim() || null,
      clearSnmpV2Community: state.clearSnmpV2Community,
      snmpV3Enabled: state.snmpV3Enabled,
      snmpV3Port: state.snmpV3Port,
      snmpV3SecurityUsername: state.snmpV3SecurityUsername,
      snmpV3AuthProtocol: state.snmpV3AuthProtocol,
      snmpV3AuthPassword: state.snmpV3AuthPassword || undefined,
      clearSnmpV3AuthPassword: state.clearSnmpV3AuthPassword,
      snmpV3PrivacyProtocol: state.snmpV3PrivacyProtocol,
      snmpV3PrivacyPassword: state.snmpV3PrivacyPassword || undefined,
      clearSnmpV3PrivacyPassword: state.clearSnmpV3PrivacyPassword,
      sshEnabled: state.sshEnabled,
      sshPort: state.sshPort,
      sshUsername: state.sshUsername,
      sshPassword: state.sshPassword || undefined,
      clearSshPassword: state.clearSshPassword,
      sshPrivateKeyPem: state.sshPrivateKeyPem || undefined,
      clearSshPrivateKey: state.clearSshPrivateKey,
      sshPassphrase: state.sshPassphrase || undefined,
      clearSshPassphrase: state.clearSshPassphrase,
      httpsEnabled: state.httpsEnabled,
      httpsPort: state.httpsPort,
      httpsUsername: state.httpsUsername,
      httpsPassword: state.httpsPassword || undefined,
      clearHttpsPassword: state.clearHttpsPassword,
      httpsClientCertPem: state.httpsClientCertPem || undefined,
      clearHttpsClientCert: state.clearHttpsClientCert,
      httpsClientKeyPem: state.httpsClientKeyPem || undefined,
      clearHttpsClientKey: state.clearHttpsClientKey,
      httpsInsecureSkipVerify: state.httpsInsecureSkipVerify,
    };

    this.saving.set(true);
    const req = state.editingId == null
      ? this.profiles.create(body)
      : this.profiles.update(state.editingId, body);

    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogOpen.set(false);
        this.notify.success('Профиль сохранён.', 'Профили доступа');
        this.load();
      },
      error: (error) => {
        this.saving.set(false);
        this.notify.error(
          (error as { error?: { message?: string } })?.error?.message ?? 'Не удалось сохранить профиль.',
          'Профили доступа'
        );
      },
    });
  }

  protected deleteRow(row: AccessProfileDetail): void {
    if (!confirm(`Удалить профиль «${row.name}»?`)) {
      return;
    }
    this.deleting.set(true);
    this.profiles.delete(row.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notify.success('Профиль удалён.', 'Профили доступа');
        this.load();
      },
      error: (error) => {
        this.deleting.set(false);
        this.notify.error(
          (error as { error?: { message?: string } })?.error?.message ?? 'Не удалось удалить профиль.',
          'Профили доступа'
        );
      },
    });
  }

  protected deleteSelected(): void {
    const selected = this.selectedRows();
    if (selected.length === 0) {
      return;
    }
    const names = selected.map((row) => `«${row.name}»`).join(', ');
    if (!confirm(`Удалить профили: ${names}?`)) {
      return;
    }
    this.deleting.set(true);
    forkJoin(selected.map((row) => this.profiles.delete(row.id))).subscribe({
      next: () => {
        this.deleting.set(false);
        this.notify.success(`Удалено профилей: ${selected.length}.`, 'Профили доступа');
        this.load();
      },
      error: (error) => {
        this.deleting.set(false);
        this.notify.error(
          (error as { error?: { message?: string } })?.error?.message ?? 'Не удалось удалить выбранные профили.',
          'Профили доступа'
        );
        this.load();
      },
    });
  }

  protected updateForm<K extends keyof ProfileFormState>(key: K, value: ProfileFormState[K]): void {
    this.form.update((f) => ({ ...f, [key]: value }));
  }

  protected protocolChips(row: AccessProfileDetail): string[] {
    const chips: string[] = [];
    if (row.snmpV1Enabled) chips.push('SNMPv1');
    if (row.snmpV2Enabled) chips.push('SNMPv2');
    if (row.snmpV3Enabled) chips.push('SNMPv3');
    if (row.sshEnabled) chips.push('SSH');
    if (row.httpsEnabled) chips.push('HTTPS');
    return chips;
  }

  /** До 3 чипов в строке; при большем числе — 3 сверху, остальные по центру снизу. */
  protected protocolChipRows(row: AccessProfileDetail): string[][] {
    const chips = this.protocolChips(row);
    if (chips.length <= 3) {
      return chips.length > 0 ? [chips] : [];
    }
    return [chips.slice(0, 3), chips.slice(3)];
  }

  protected editingRow(): AccessProfileDetail | null {
    const id = this.form().editingId;
    if (id == null) return null;
    return this.rows().find((r) => r.id === id) ?? null;
  }

  private emptyForm(): ProfileFormState {
    return {
      editingId: null,
      name: '',
      description: '',
      snmpV1Enabled: false,
      snmpV1Port: 161,
      snmpV1Community: 'public',
      clearSnmpV1Community: false,
      snmpV2Enabled: true,
      snmpV2Port: 161,
      snmpV2Community: 'public',
      clearSnmpV2Community: false,
      snmpV3Enabled: false,
      snmpV3Port: 161,
      snmpV3SecurityUsername: '',
      snmpV3AuthProtocol: 'SHA',
      snmpV3AuthPassword: '',
      clearSnmpV3AuthPassword: false,
      snmpV3PrivacyProtocol: 'AES',
      snmpV3PrivacyPassword: '',
      clearSnmpV3PrivacyPassword: false,
      sshEnabled: false,
      sshPort: 22,
      sshUsername: '',
      sshPassword: '',
      clearSshPassword: false,
      sshPrivateKeyPem: '',
      clearSshPrivateKey: false,
      sshPassphrase: '',
      clearSshPassphrase: false,
      httpsEnabled: false,
      httpsPort: 443,
      httpsUsername: '',
      httpsPassword: '',
      clearHttpsPassword: false,
      httpsClientCertPem: '',
      clearHttpsClientCert: false,
      httpsClientKeyPem: '',
      clearHttpsClientKey: false,
      httpsInsecureSkipVerify: false,
    };
  }
}
