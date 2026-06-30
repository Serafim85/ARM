import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MenuItem } from 'primeng/api';
import { SplitButtonModule } from 'primeng/splitbutton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DeviceOptionSelectComponent } from '../../components/device-option-select/device-option-select.component';
import { WorkstationListItem } from '../../models';
import { WorkstationsService } from '../../services/workstations.service';
import { WORKSTATIONS_COLUMN_ORDER, WORKSTATIONS_TABLE_COLUMNS } from './workstations-table-columns';

@Component({
  selector: 'app-workstations-page',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    SplitButtonModule,
    InputTextModule,
    TagModule,
    DeviceOptionSelectComponent,
    DatePipe,
  ],
  templateUrl: './workstations-page.component.html',
  styleUrl: './workstations-page.component.css',
})
export class WorkstationsPageComponent implements OnInit, OnDestroy {
  protected readonly ws = inject(WorkstationsService);
  private readonly router = inject(Router);

  protected readonly tableColumns = WORKSTATIONS_TABLE_COLUMNS;
  protected readonly tableColumnOrder = WORKSTATIONS_COLUMN_ORDER;

  protected readonly statusFilterOptions = [
    { label: 'Все статусы', value: 'ALL' },
    { label: 'Online', value: 'online' },
    { label: 'Offline', value: 'offline' },
  ];

  protected readonly osTypeFilterOptions = [
    { label: 'Все ОС', value: 'ALL' },
    { label: 'Linux', value: 'linux' },
    { label: 'Windows', value: 'windows' },
    { label: 'Unknown', value: 'unknown' },
  ];

  protected readonly exportMenuItems: MenuItem[] = [
    {
      label: 'CSV (реестр)',
      icon: 'pi pi-file',
      command: () => this.exportReport('csv'),
    },
  ];

  private refreshTimer: ReturnType<typeof setInterval> | null = null;
  private lastLazyEvent: TableLazyLoadEvent | null = null;

  ngOnInit(): void {
    this.refreshTimer = setInterval(() => this.reloadCurrentPage(), 60_000);
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.lastLazyEvent = event;
    const page = event.first != null && event.rows ? Math.floor(event.first / event.rows) : 0;
    const size = event.rows ?? 25;
    const sortField = typeof event.sortField === 'string' ? event.sortField : 'lastSeenAt';
    const sortOrder = event.sortOrder === 1 ? 'asc' : 'desc';
    this.ws.loadWorkstations(page, size, sortField, sortOrder);
  }

  protected applyFilters(): void {
    this.reloadFromStart();
  }

  protected onStatusFilterChange(value: string | number | null): void {
    const normalized = value == null ? 'ALL' : String(value);
    if (normalized === 'ALL' || normalized === 'online' || normalized === 'offline') {
      this.ws.statusFilter.set(normalized);
      this.applyFilters();
    }
  }

  protected onOsTypeFilterChange(value: string | number | null): void {
    this.ws.osTypeFilter.set(value == null ? 'ALL' : String(value));
    this.applyFilters();
  }

  protected reloadCurrentPage(): void {
    if (this.lastLazyEvent) {
      this.onLazyLoad(this.lastLazyEvent);
      return;
    }
    this.ws.loadWorkstations();
  }

  protected openWorkstation(row: WorkstationListItem): void {
    void this.router.navigate(['/workstations', row.id]);
  }

  protected statusSeverity(status: string): 'success' | 'danger' {
    return status === 'online' ? 'success' : 'danger';
  }

  protected statusLabel(status: string): string {
    return status === 'online' ? 'Online' : 'Offline';
  }

  protected osLabel(osType: string): string {
    const normalized = (osType ?? '').toLowerCase();
    if (normalized === 'linux') return 'Linux';
    if (normalized === 'windows') return 'Windows';
    if (normalized === 'macos' || normalized === 'darwin') return 'macOS';
    return osType || '—';
  }

  protected exportReport(format: 'csv' | 'xlsx'): void {
    this.ws.exportParkReport(format);
  }

  private reloadFromStart(): void {
    this.lastLazyEvent = { first: 0, rows: this.lastLazyEvent?.rows ?? 25, sortField: 'lastSeenAt', sortOrder: -1 };
    this.onLazyLoad(this.lastLazyEvent);
  }
}
