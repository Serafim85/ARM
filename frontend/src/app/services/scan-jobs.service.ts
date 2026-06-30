import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { finalize, forkJoin, Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import { DeviceScanResult, ScanJob, ScanRunStartResponse } from '../models';
import { NotifierService } from '../notifier.service';

@Injectable({ providedIn: 'root' })
export class ScanJobsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly notify = inject(NotifierService);

  readonly jobs = signal<ScanJob[]>([]);
  readonly loading = signal(false);
  /** Уникальные IP из последних результатов задач, ещё не на мониторинге; null до первой успешной загрузки сводки. */
  readonly discoveredNotMonitoredCount = signal<number | null>(null);
  readonly sortedJobs = computed(() => [...this.jobs()].sort((a, b) => a.id - b.id));

  private pollTimer: ReturnType<typeof setInterval> | null = null;

  load(): void {
    this.loading.set(true);
    forkJoin({
      jobs: this.http.get<ScanJob[]>(`${this.apiBaseUrl}/api/scan-jobs`),
      summary: this.http.get<{ count: number }>(
        `${this.apiBaseUrl}/api/scan-jobs/discovered-not-monitored-summary`
      ),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ jobs, summary }) => {
          this.jobs.set(Array.isArray(jobs) ? jobs : []);
          this.discoveredNotMonitoredCount.set(
            typeof summary?.count === 'number' ? summary.count : null
          );
          this.ensureActiveJobsPolling();
        },
        error: (error) => {
          this.jobs.set([]);
          this.discoveredNotMonitoredCount.set(null);
          this.notify.error(this.resolveError(error, 'Не удалось загрузить задачи автосканирования.'), 'Автосканирование');
        },
      });
  }

  getDiscoveredNotMonitoredDevices() {
    return this.http.get<DeviceScanResult[]>(
      `${this.apiBaseUrl}/api/scan-jobs/discovered-not-monitored-devices`
    );
  }

  /** Обновить только сводку «обнаружено новых» (без перезагрузки списка задач). */
  reloadDiscoverySummary(): void {
    this.http
      .get<{ count: number }>(`${this.apiBaseUrl}/api/scan-jobs/discovered-not-monitored-summary`)
      .subscribe({
        next: (summary) => {
          if (typeof summary?.count === 'number') {
            this.discoveredNotMonitoredCount.set(summary.count);
          }
        },
        error: () => {},
      });
  }

  create(body: { name: string; enabled: boolean; cron: string; request: unknown }) {
    return this.http.post<ScanJob>(`${this.apiBaseUrl}/api/scan-jobs`, body);
  }

  update(id: number, body: { name: string; enabled: boolean; cron: string; request: unknown }) {
    return this.http.put<ScanJob>(`${this.apiBaseUrl}/api/scan-jobs/${id}`, body);
  }

  updateMeta(id: number, body: { name: string; enabled: boolean; cron: string }) {
    return this.http.put<ScanJob>(`${this.apiBaseUrl}/api/scan-jobs/${id}/meta`, body);
  }

  enable(id: number) {
    return this.http.post<ScanJob>(`${this.apiBaseUrl}/api/scan-jobs/${id}/enable`, {});
  }

  disable(id: number) {
    return this.http.post<ScanJob>(`${this.apiBaseUrl}/api/scan-jobs/${id}/disable`, {});
  }

  runNow(id: number): Observable<ScanRunStartResponse> {
    return this.http.post<ScanRunStartResponse>(`${this.apiBaseUrl}/api/scan-jobs/${id}/run`, {});
  }

  markJobRunning(jobId: number, runId: number, totalAddresses: number): void {
    this.jobs.update((jobs) =>
      jobs.map((job) =>
        job.id === jobId
          ? {
              ...job,
              lastStatus: 'RUNNING',
              lastError: null,
              activeRunId: runId,
              scannedAddresses: 0,
              totalAddresses,
            }
          : job
      )
    );
    this.ensureActiveJobsPolling();
  }

  private ensureActiveJobsPolling(): void {
    const hasRunning = this.jobs().some((job) => job.lastStatus === 'RUNNING');
    if (!hasRunning) {
      this.stopActiveJobsPolling();
      return;
    }
    if (this.pollTimer != null) {
      return;
    }
    this.pollTimer = setInterval(() => this.refreshRunningJobs(), 2000);
  }

  private stopActiveJobsPolling(): void {
    if (this.pollTimer != null) {
      clearInterval(this.pollTimer);
      this.pollTimer = null;
    }
  }

  private refreshRunningJobs(): void {
    const runningJobs = this.jobs().filter((job) => job.lastStatus === 'RUNNING');
    if (runningJobs.length === 0) {
      this.stopActiveJobsPolling();
      return;
    }
    forkJoin(
      runningJobs.map((job) => this.http.get<ScanJob>(`${this.apiBaseUrl}/api/scan-jobs/${job.id}`))
    ).subscribe({
      next: (updatedJobs) => {
        const previousById = new Map(this.jobs().map((job) => [job.id, job] as const));
        const updatedById = new Map(updatedJobs.map((job) => [job.id, job] as const));
        this.jobs.update((jobs) => jobs.map((job) => updatedById.get(job.id) ?? job));
        for (const updated of updatedJobs) {
          const previous = previousById.get(updated.id);
          if (previous?.lastStatus === 'RUNNING' && updated.lastStatus === 'SUCCESS') {
            this.notify.success(
              `Задача «${updated.name}» завершена. Найдено устройств: ${updated.lastResultCount ?? 0}.`,
              'Автосканирование'
            );
          } else if (previous?.lastStatus === 'RUNNING' && updated.lastStatus === 'FAILED') {
            this.notify.error(
              updated.lastError ?? `Задача «${updated.name}» завершилась ошибкой.`,
              'Автосканирование'
            );
          }
        }
        if (!updatedJobs.some((job) => job.lastStatus === 'RUNNING')) {
          this.stopActiveJobsPolling();
          this.reloadDiscoverySummary();
        }
      },
      error: () => {},
    });
  }

  getLastResult(id: number) {
    return this.http.get<DeviceScanResult[]>(`${this.apiBaseUrl}/api/scan-jobs/${id}/last-result`);
  }

  getDetails(id: number) {
    return this.http.get<unknown>(`${this.apiBaseUrl}/api/scan-jobs/${id}/details`);
  }

  delete(id: number) {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/scan-jobs/${id}`);
  }

  deleteMany(ids: number[]): Observable<void[]> {
    return forkJoin(ids.map((id) => this.delete(id)));
  }

  private resolveError(error: unknown, fallback: string): string {
    const message = (error as { error?: { message?: string } })?.error?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }
}

