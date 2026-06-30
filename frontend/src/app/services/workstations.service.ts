import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { API_BASE_URL } from '../api-config';
import { NotifierService } from '../notifier.service';
import { WorkstationDetail, WorkstationEventEntry, WorkstationLogEntry, WorkstationMetricsHistory, WorkstationPage, WorkstationStatus } from '../models';

@Injectable({ providedIn: 'root' })
export class WorkstationsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly notify = inject(NotifierService);

  readonly workstationsPage = signal<WorkstationPage | null>(null);
  readonly workstationsLoading = signal(false);
  readonly workstationDetail = signal<WorkstationDetail | null>(null);
  readonly workstationDetailLoading = signal(false);
  readonly metricsHistory = signal<WorkstationMetricsHistory | null>(null);
  readonly metricsLoading = signal(false);
  readonly workstationLogs = signal<WorkstationLogEntry[]>([]);
  readonly workstationLogsLoading = signal(false);
  readonly workstationEvents = signal<WorkstationEventEntry[]>([]);
  readonly workstationEventsLoading = signal(false);

  readonly searchQuery = signal('');
  readonly statusFilter = signal<'ALL' | WorkstationStatus>('ALL');
  readonly osTypeFilter = signal('ALL');

  readonly workstations = computed(() => this.workstationsPage()?.content ?? []);
  readonly totalCount = computed(() => this.workstationsPage()?.totalElements ?? 0);
  readonly onlineCount = computed(() => this.workstationsPage()?.onlineCount ?? 0);
  readonly offlineCount = computed(() => this.workstationsPage()?.offlineCount ?? 0);

  loadWorkstations(
    page = 0,
    size = 25,
    sortField = 'lastSeenAt',
    sortOrder: 'asc' | 'desc' = 'desc'
  ): void {
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size))
      .set('sortField', sortField)
      .set('sortOrder', sortOrder);

    const q = this.searchQuery().trim();
    if (q) {
      params = params.set('q', q);
    }
    const status = this.statusFilter();
    if (status !== 'ALL') {
      params = params.set('status', status);
    }
    const osType = this.osTypeFilter();
    if (osType !== 'ALL') {
      params = params.set('osType', osType);
    }

    this.workstationsLoading.set(true);
    this.http.get<WorkstationPage>(`${this.apiBaseUrl}/api/v1/workstations`, { params }).subscribe({
      next: (pageDto) => {
        this.workstationsPage.set(pageDto);
        this.workstationsLoading.set(false);
      },
      error: () => {
        this.workstationsLoading.set(false);
        this.notify.error('Не удалось загрузить список АРМ.', 'Рабочие станции');
      },
    });
  }

  loadWorkstationDetail(id: number): void {
    this.workstationDetailLoading.set(true);
    this.http.get<WorkstationDetail>(`${this.apiBaseUrl}/api/v1/workstations/${id}`).subscribe({
      next: (detail) => {
        this.workstationDetail.set(detail);
        this.workstationDetailLoading.set(false);
      },
      error: () => {
        this.workstationDetailLoading.set(false);
        this.notify.error('Не удалось загрузить карточку АРМ.', 'Рабочие станции');
      },
    });
  }

  clearWorkstationDetail(): void {
    this.workstationDetail.set(null);
    this.metricsHistory.set(null);
    this.workstationLogs.set([]);
    this.workstationEvents.set([]);
  }

  loadMetricsHistory(
    workstationId: number,
    fromIso: string,
    toIso: string,
    maxPoints = 600
  ): void {
    const params = new HttpParams()
      .set('from', fromIso)
      .set('to', toIso)
      .set('maxPoints', String(maxPoints));
    this.metricsLoading.set(true);
    this.http
      .get<WorkstationMetricsHistory>(`${this.apiBaseUrl}/api/v1/workstations/${workstationId}/metrics`, { params })
      .subscribe({
        next: (history) => {
          this.metricsHistory.set(history);
          this.metricsLoading.set(false);
        },
        error: () => {
          this.metricsHistory.set(null);
          this.metricsLoading.set(false);
          this.notify.error('Не удалось загрузить графики метрик.', 'Рабочие станции');
        },
      });
  }

  loadWorkstationLogs(workstationId: number, levels = 'warning,error', limit = 50): void {
    const params = new HttpParams().set('levels', levels).set('limit', String(limit));
    this.workstationLogsLoading.set(true);
    this.http
      .get<WorkstationLogEntry[]>(`${this.apiBaseUrl}/api/v1/workstations/${workstationId}/logs`, { params })
      .subscribe({
        next: (entries) => {
          this.workstationLogs.set(entries);
          this.workstationLogsLoading.set(false);
        },
        error: () => {
          this.workstationLogs.set([]);
          this.workstationLogsLoading.set(false);
          this.notify.error('Не удалось загрузить логи АРМ.', 'Рабочие станции');
        },
      });
  }

  loadWorkstationEvents(workstationId: number, limit = 30): void {
    const params = new HttpParams().set('limit', String(limit));
    this.workstationEventsLoading.set(true);
    this.http
      .get<WorkstationEventEntry[]>(`${this.apiBaseUrl}/api/v1/workstations/${workstationId}/events`, { params })
      .subscribe({
        next: (entries) => {
          this.workstationEvents.set(entries);
          this.workstationEventsLoading.set(false);
        },
        error: () => {
          this.workstationEvents.set([]);
          this.workstationEventsLoading.set(false);
          this.notify.error('Не удалось загрузить события АРМ.', 'Рабочие станции');
        },
      });
  }

  exportParkReport(format: 'csv' | 'xlsx'): void {
    let params = new HttpParams();
    const q = this.searchQuery().trim();
    if (q) {
      params = params.set('q', q);
    }
    const status = this.statusFilter();
    if (status !== 'ALL') {
      params = params.set('status', status);
    }
    const osType = this.osTypeFilter();
    if (osType !== 'ALL') {
      params = params.set('osType', osType);
    }

    const suffix = format === 'csv' ? 'export.csv' : 'export.xlsx';
    const filename = format === 'csv' ? 'workstations.csv' : 'arm-park-report.xlsx';

    this.http
      .get(`${this.apiBaseUrl}/api/v1/workstations/${suffix}`, {
        params,
        responseType: 'blob',
      })
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = filename;
          link.click();
          URL.revokeObjectURL(url);
          this.notify.success(
            format === 'csv' ? 'CSV выгружен.' : 'XLSX-отчёт с рекомендациями выгружен.',
            'Экспорт'
          );
        },
        error: () => {
          this.notify.error('Не удалось выгрузить отчёт.', 'Экспорт');
        },
      });
  }
}
