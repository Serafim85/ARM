import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MultiSelectModule } from 'primeng/multiselect';
import { RadioButton } from 'primeng/radiobutton';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { AuthService } from '../../auth.service';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import type { DashboardRecord, DashboardVisibility, UserDirectoryEntry } from '../../models';
import { DashboardsService } from '../../services/dashboards.service';

@Component({
  selector: 'app-dashboards-list-page',
  standalone: true,
  imports: [
    DatePipe,
    FormsModule,
    TableModule,
    ButtonModule,
    RadioButton,
    DialogModule,
    InputTextModule,
    SelectModule,
    DeviceOptionSelectComponent,
    MultiSelectModule,
  ],
  templateUrl: './dashboards-list-page.component.html',
  styleUrl: './dashboards-list-page.component.css',
})
export class DashboardsListPageComponent implements OnInit {
  private readonly dashboardsApi = inject(DashboardsService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly dashboards = signal<DashboardRecord[]>([]);
  protected readonly directory = signal<UserDirectoryEntry[]>([]);

  protected readonly createOpen = signal(false);
  protected readonly createName = signal('');
  protected readonly createVisibility = signal<DashboardVisibility>('PRIVATE');
  protected readonly createSharedIds = signal<number[]>([]);
  protected readonly saving = signal(false);
  protected readonly defaultDashboardSaving = signal(false);

  protected readonly visibilityOptions = [
    { label: 'Только я', value: 'PRIVATE' as const },
    { label: 'Общий доступ (выбранные пользователи)', value: 'SHARED' as const },
  ];

  protected readonly currentUserId = computed(() => this.auth.authSession()?.userId ?? null);

  protected readonly myDashboards = computed(() => {
    const uid = this.currentUserId();
    const all = this.dashboards();
    if (uid == null) {
      return all;
    }
    return all.filter((d) => d.ownerId === uid);
  });

  protected readonly sharedDashboards = computed(() => {
    const uid = this.currentUserId();
    const all = this.dashboards();
    if (uid == null) {
      return [];
    }
    return all.filter((d) => d.ownerId !== uid);
  });

  protected readonly multiSelectOptions = computed(() => {
    const uid = this.currentUserId();
    return this.directory().filter((u) => u.id !== uid);
  });

  protected readonly dashboardsSharedCount = computed(
    () => this.dashboards().filter((d) => d.visibility === 'SHARED').length,
  );

  /** Текущий дашборд по умолчанию (для группы радиокнопок в обеих таблицах). */
  protected readonly defaultDashboardId = computed(
    () => this.auth.authSession()?.defaultDashboardId ?? null,
  );

  ngOnInit(): void {
    this.refresh();
    this.dashboardsApi.listUserDirectory().subscribe({
      next: (rows) => this.directory.set(rows),
      error: () => this.directory.set([]),
    });
  }

  protected refresh(): void {
    this.loading.set(true);
    this.error.set('');
    this.dashboardsApi.list().subscribe({
      next: (rows) => {
        this.dashboards.set(this.normalizeList(rows));
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Не удалось загрузить дашборды.');
        this.loading.set(false);
      },
    });
  }

  protected openCreate(): void {
    this.createName.set('');
    this.createVisibility.set('PRIVATE');
    this.createSharedIds.set([]);
    this.createOpen.set(true);
  }

  protected closeCreate(): void {
    if (!this.saving()) {
      this.createOpen.set(false);
    }
  }

  protected submitCreate(): void {
    const name = this.createName().trim();
    if (!name) {
      this.error.set('Укажите название дашборда.');
      return;
    }
    const vis = this.createVisibility();
    const shared = vis === 'SHARED' ? [...this.createSharedIds()] : [];
    this.saving.set(true);
    this.error.set('');
    this.dashboardsApi
      .create({ name, visibility: vis, sharedUserIds: shared })
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          this.createOpen.set(false);
          void this.router.navigate(['/dashboards', created.id]);
        },
        error: (e) => {
          this.saving.set(false);
          this.error.set(e?.error?.message ?? e?.message ?? 'Не удалось создать дашборд.');
        },
      });
  }

  protected onCreateVisibilityChange(value: string | number | null): void {
    const v = value == null ? 'PRIVATE' : String(value);
    if (v === 'PRIVATE' || v === 'SHARED') {
      this.createVisibility.set(v);
    }
  }

  protected openRow(row: DashboardRecord): void {
    void this.router.navigate(['/dashboards', row.id]);
  }

  /**
   * Радиокнопка по умолчанию должна быть "снимаемой":
   * - если кликнули по уже выбранному дашборду — сбрасываем (null)
   * - иначе — выбираем его
   */
  protected onDefaultDashboardToggle(dashboardId: number): void {
    const current = this.auth.authSession()?.defaultDashboardId ?? null;
    const next = current === dashboardId ? null : dashboardId;
    this.setDefaultDashboard(next);
  }

  private setDefaultDashboard(defaultDashboardId: number | null): void {
    const session = this.auth.authSession();
    if (!session || this.defaultDashboardSaving()) {
      return;
    }

    const prev = session.defaultDashboardId ?? null;
    if (prev === defaultDashboardId) {
      return;
    }

    // Оптимистично обновляем UI, чтобы радио сразу переключалось/снималось.
    this.auth.updateSession({
      ...session,
      defaultDashboardId,
    });

    this.defaultDashboardSaving.set(true);
    this.error.set('');
    this.auth.updateDefaultDashboardPreference(defaultDashboardId).subscribe({
      next: (response) => {
        this.defaultDashboardSaving.set(false);
        const cur = this.auth.authSession();
        if (!cur) {
          return;
        }
        this.auth.updateSession({
          ...cur,
          defaultDashboardId: response.defaultDashboardId,
        });
      },
      error: (e) => {
        this.defaultDashboardSaving.set(false);
        // Откат при ошибке.
        const cur = this.auth.authSession();
        if (cur) {
          this.auth.updateSession({
            ...cur,
            defaultDashboardId: prev,
          });
        }
        this.error.set(e?.error?.message ?? 'Не удалось обновить дашборд по умолчанию.');
      },
    });
  }

  protected visibilityLabel(v: DashboardVisibility): string {
    return v === 'PRIVATE' ? 'Приватный' : 'Общий';
  }

  protected formatSharedCount(d: DashboardRecord): string {
    if (d.visibility === 'PRIVATE') {
      return '—';
    }
    return String(d.sharedUserIds?.length ?? 0);
  }

  private normalizeList(rows: DashboardRecord[]): DashboardRecord[] {
    return rows.map((d) => ({
      ...d,
      sharedUserIds: Array.isArray(d.sharedUserIds) ? d.sharedUserIds : [],
      widgets: Array.isArray(d.widgets) ? d.widgets : [],
    }));
  }
}
