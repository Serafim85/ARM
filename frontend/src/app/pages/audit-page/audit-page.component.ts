import { CommonModule, NgStyle } from '@angular/common';
import { Component, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import { NsTableColumnWidthsDirective } from '../../directives/ns-table-column-widths.directive';
import type { SystemAuditAction, SystemAuditCategory, SystemAuditEventRecord } from '../../models';
import { NotifierService } from '../../notifier.service';
import { SystemAuditService } from '../../services/system-audit.service';
import { TableColumnWidthsService } from '../../services/table-column-widths.service';
import { buildColumnBoundsMap, columnBoundsStyle } from '../../utils/table-column-widths';
import { AUDIT_COLUMN_ORDER, AUDIT_TABLE_COLUMNS } from './audit-table-columns';

@Component({
  selector: 'app-audit-page',
  standalone: true,
  imports: [CommonModule, NgStyle, FormsModule, TableModule, ButtonModule, DialogModule, InputTextModule, DeviceOptionSelectComponent, NsTableColumnWidthsDirective],
  templateUrl: './audit-page.component.html',
  styleUrl: './audit-page.component.css',
})
export class AuditPageComponent {
  @ViewChild('auditTableWidths') private auditTableWidths?: NsTableColumnWidthsDirective;

  private readonly audit = inject(SystemAuditService);
  private readonly notify = inject(NotifierService);
  private readonly tableColumnWidths = inject(TableColumnWidthsService);

  protected readonly auditTableColumns = AUDIT_TABLE_COLUMNS;
  protected readonly auditTableColumnOrder = AUDIT_COLUMN_ORDER;
  protected readonly auditTableColumnBounds = buildColumnBoundsMap(AUDIT_TABLE_COLUMNS);
  protected readonly auditTableColumnWidthsMap = signal<Record<string, number>>({});
  protected readonly columnsDialogOpen = signal(false);
  protected readonly columnBoundsStyle = columnBoundsStyle;

  protected readonly events = signal<SystemAuditEventRecord[]>([]);
  protected readonly loading = signal(false);
  protected readonly totalRecords = signal(0);
  protected readonly rows = signal(20);
  protected readonly first = signal(0);

  /** Локальные значения полей фильтра (до «Применить»). */
  protected readonly filterFromLocal = signal('');
  protected readonly filterToLocal = signal('');
  protected readonly filterActorLocal = signal('');
  protected readonly filterCategoryLocal = signal<string>('');
  protected readonly filterActionLocal = signal<string>('');

  /** Активные фильтры, участвующие в запросе. */
  protected readonly filterFrom = signal('');
  protected readonly filterTo = signal('');
  protected readonly filterActor = signal('');
  protected readonly filterCategory = signal<string>('');
  protected readonly filterAction = signal<string>('');

  protected readonly categoryFilterOptions = [
    { label: 'Все разделы', value: '' },
    { label: 'Мониторинг устройств', value: 'MONITORING_DEVICE' },
    { label: 'Автосканирование', value: 'SCAN_JOB' },
    { label: 'Шаблоны мониторинга', value: 'MONITORING_TEMPLATE' },
    { label: 'Топология', value: 'TOPOLOGY' },
    { label: 'Дашборды', value: 'DASHBOARD' },
    { label: 'Внешняя интеграция', value: 'WISLA_INTEGRATION' },
    { label: 'Каталог LDAP/AD', value: 'DIRECTORY_AUTH' },
    { label: 'Сеанс', value: 'AUTH_SESSION' },
    { label: 'Пользователи', value: 'USER_ADMIN' },
    { label: 'Настройки каталога', value: 'DIRECTORY_CONFIG' },
    { label: 'Уведомления', value: 'NOTIFICATION_SETTINGS' },
    { label: 'Профили доступа', value: 'ACCESS_PROFILE' },
  ];

  protected readonly actionFilterOptions = [
    { label: 'Все действия', value: '' },
    { label: 'Создание', value: 'CREATE' },
    { label: 'Изменение', value: 'UPDATE' },
    { label: 'Удаление', value: 'DELETE' },
    { label: 'Вход', value: 'LOGIN' },
    { label: 'Выход', value: 'LOGOUT' },
    { label: 'Неуспешный вход', value: 'LOGIN_FAILED' },
    { label: 'Ошибка подключения', value: 'CONNECTION_ERROR' },
    { label: 'Ошибка публикации', value: 'INTEGRATION_PUBLISH_FAILED' },
  ];

  protected readonly categoryLabel: Record<SystemAuditCategory, string> = {
    MONITORING_DEVICE: 'Мониторинг устройств',
    SCAN_JOB: 'Автосканирование',
    MONITORING_TEMPLATE: 'Шаблоны мониторинга',
    TOPOLOGY: 'Топология',
    DASHBOARD: 'Дашборды',
    WISLA_INTEGRATION: 'Внешняя интеграция',
    DIRECTORY_AUTH: 'Каталог LDAP/AD',
    AUTH_SESSION: 'Сеанс',
    USER_ADMIN: 'Пользователи',
    DIRECTORY_CONFIG: 'Настройки каталога',
    NOTIFICATION_SETTINGS: 'Уведомления',
    ACCESS_PROFILE: 'Профили доступа',
  };

  protected readonly actionLabel: Record<SystemAuditAction, string> = {
    CREATE: 'Создание',
    UPDATE: 'Изменение',
    DELETE: 'Удаление',
    LOGIN: 'Вход',
    LOGOUT: 'Выход',
    LOGIN_FAILED: 'Неуспешный вход',
    CONNECTION_ERROR: 'Ошибка подключения',
    INTEGRATION_PUBLISH_FAILED: 'Ошибка публикации',
  };

  protected onCategoryFilterChange(value: string | number | null): void {
    this.filterCategoryLocal.set(value === null || value === undefined ? '' : String(value));
  }

  protected onActionFilterChange(value: string | number | null): void {
    this.filterActionLocal.set(value === null || value === undefined ? '' : String(value));
  }

  constructor() {
    this.loadTableColumnWidths();
  }

  private loadTableColumnWidths(): void {
    this.tableColumnWidths.load().subscribe({
      next: () => {
        this.auditTableColumnWidthsMap.set(
          this.tableColumnWidths.widthsFor('audit', this.auditTableColumnBounds)
        );
      },
      error: () => {
        this.auditTableColumnWidthsMap.set({});
      },
    });
  }

  protected openColumnsDialog(): void {
    this.columnsDialogOpen.set(true);
  }

  protected closeColumnsDialog(): void {
    this.columnsDialogOpen.set(false);
  }

  protected resetAuditTableColumnWidths(): void {
    this.tableColumnWidths.reset('audit').subscribe({
      next: () => {
        this.auditTableColumnWidthsMap.set({});
        this.auditTableWidths?.resetDomWidths();
        this.notify.success('Ширина колонок сброшена.', 'Аудит');
      },
      error: () => {
        this.notify.error('Не удалось сбросить ширину колонок.', 'Аудит');
      },
    });
  }

  protected applyFilters(): void {
    this.filterFrom.set(this.filterFromLocal().trim());
    this.filterTo.set(this.filterToLocal().trim());
    this.filterActor.set(this.filterActorLocal().trim());
    this.filterCategory.set(this.filterCategoryLocal());
    this.filterAction.set(this.filterActionLocal());
    this.first.set(0);
    this.load();
  }

  protected resetFilters(): void {
    this.filterFromLocal.set('');
    this.filterToLocal.set('');
    this.filterActorLocal.set('');
    this.filterCategoryLocal.set('');
    this.filterActionLocal.set('');
    this.filterFrom.set('');
    this.filterTo.set('');
    this.filterActor.set('');
    this.filterCategory.set('');
    this.filterAction.set('');
    this.first.set(0);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    const page = Math.floor(this.first() / this.rows());
    this.audit
      .listEvents({
        page,
        size: this.rows(),
        from: this.toIsoParam(this.filterFrom()),
        to: this.toIsoParam(this.filterTo()),
        actor: this.filterActor() || undefined,
        category: this.filterCategory() || undefined,
        action: this.filterAction() || undefined,
      })
      .subscribe({
      next: (p) => {
        this.events.set(p.content);
        this.totalRecords.set(p.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        this.notify.error(this.resolveError(err, 'Не удалось загрузить журнал аудита.'), 'Аудит');
        this.events.set([]);
        this.totalRecords.set(0);
        this.loading.set(false);
      },
    });
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.first.set(event.first ?? 0);
    this.rows.set(event.rows ?? 20);
    this.load();
  }

  protected categoryLabelFor(category: SystemAuditCategory): string {
    return this.categoryLabel[category] ?? category;
  }

  protected actionLabelFor(action: SystemAuditAction): string {
    return this.actionLabel[action] ?? action;
  }

  protected formatTime(iso: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return iso;
    return d.toLocaleString('ru-RU', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }

  private resolveError(err: unknown, fallback: string): string {
    if (err && typeof err === 'object' && 'error' in err) {
      const e = (err as { error?: { message?: string } }).error;
      if (e?.message && typeof e.message === 'string') return e.message;
    }
    return fallback;
  }

  /** Значение из input[type=datetime-local] → ISO для API. */
  private toIsoParam(local: string): string | undefined {
    if (!local?.trim()) return undefined;
    const d = new Date(local);
    if (Number.isNaN(d.getTime())) return undefined;
    return d.toISOString();
  }
}
