import { NgStyle } from '@angular/common';
import { Component, OnInit, ViewChild, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MenuItem } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { Menu, MenuModule } from 'primeng/menu';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import type { AppRole, UserManagementRecord, UserStatusFilter } from '../../models';
import { NotifierService } from '../../notifier.service';
import { UsersService } from '../../services/users.service';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import { NsTableColumnWidthsDirective } from '../../directives/ns-table-column-widths.directive';
import { TableColumnWidthsService } from '../../services/table-column-widths.service';
import { buildColumnBoundsMap, columnBoundsStyle } from '../../utils/table-column-widths';
import { UserFormModalComponent } from './user-form-modal.component';
import { USERS_COLUMN_ORDER, USERS_TABLE_COLUMNS } from './users-table-columns';

@Component({
  selector: 'app-users-page',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SelectModule,
    DeviceOptionSelectComponent,
    InputTextModule,
    CheckboxModule,
    MenuModule,
    DialogModule,
    NgStyle,
    NsTableColumnWidthsDirective,
    UserFormModalComponent,
  ],
  templateUrl: './users-page.component.html',
  styleUrl: './users-page.component.css',
})
export class UsersPageComponent implements OnInit {
  @ViewChild('userRowMenu') private userRowMenu?: Menu;
  @ViewChild('usersTableWidths') private usersTableWidths?: NsTableColumnWidthsDirective;

  protected readonly us = inject(UsersService);
  private readonly tableColumnWidths = inject(TableColumnWidthsService);
  private readonly notify = inject(NotifierService);

  protected readonly usersTableColumns = USERS_TABLE_COLUMNS;
  protected readonly usersTableColumnOrder = USERS_COLUMN_ORDER;
  protected readonly usersTableColumnBounds = buildColumnBoundsMap(USERS_TABLE_COLUMNS);
  protected readonly usersTableColumnWidthsMap = signal<Record<string, number>>({});
  protected readonly columnsDialogOpen = signal(false);
  protected readonly columnBoundsStyle = columnBoundsStyle;

  protected readonly userRowMenuItems = signal<MenuItem[]>([]);

  protected readonly statusFilterOptions = [
    { label: 'Все статусы', value: 'ALL' },
    { label: 'Активные', value: 'ACTIVE' },
    { label: 'Заблокированные', value: 'BLOCKED' },
  ];

  protected roleFilterOptions(): { label: string; value: string }[] {
    return [
      { label: 'Все роли', value: 'ALL' },
      ...this.us.appRoles.map((r) => ({ label: r, value: r })),
    ];
  }

  protected onRoleFilterChange(value: string | number | null): void {
    const v = value == null ? 'ALL' : String(value);
    if (v === 'ALL' || this.us.appRoles.includes(v as AppRole)) {
      this.us.usersRoleFilter.set(v as 'ALL' | AppRole);
    }
  }

  protected onStatusFilterChange(value: string | number | null): void {
    const v = value == null ? 'ALL' : String(value);
    if (v === 'ALL' || v === 'ACTIVE' || v === 'BLOCKED') {
      this.us.usersStatusFilter.set(v as UserStatusFilter);
    }
  }

  ngOnInit(): void {
    this.us.loadSystemUsers();
    this.loadTableColumnWidths();
  }

  private loadTableColumnWidths(): void {
    this.tableColumnWidths.load().subscribe({
      next: () => {
        this.usersTableColumnWidthsMap.set(
          this.tableColumnWidths.widthsFor('users', this.usersTableColumnBounds)
        );
      },
      error: () => {
        this.usersTableColumnWidthsMap.set({});
      },
    });
  }

  protected openColumnsDialog(): void {
    this.columnsDialogOpen.set(true);
  }

  protected closeColumnsDialog(): void {
    this.columnsDialogOpen.set(false);
  }

  protected resetUsersTableColumnWidths(): void {
    this.tableColumnWidths.reset('users').subscribe({
      next: () => {
        this.usersTableColumnWidthsMap.set({});
        this.usersTableWidths?.resetDomWidths();
        this.notify.success('Ширина колонок сброшена.', 'Пользователи');
      },
      error: () => {
        this.notify.error('Не удалось сбросить ширину колонок.', 'Пользователи');
      },
    });
  }

  protected openUserRowMenu(event: Event, user: UserManagementRecord): void {
    event.stopPropagation();
    this.userRowMenuItems.set([
      {
        label: 'Редактировать',
        icon: 'pi pi-pencil',
        disabled: this.us.usersLoading(),
        command: () => this.us.startEditUser(user),
      },
      {
        label: user.enabled ? 'Заблокировать' : 'Разблокировать',
        icon: user.enabled ? 'pi pi-lock' : 'pi pi-lock-open',
        disabled: !this.us.canToggleUserStatus(user),
        command: () => this.us.toggleUserBlocked(user),
      },
    ]);
    this.userRowMenu?.toggle(event);
  }
}
