import { Component, computed, effect, inject, input, signal } from '@angular/core';
import type { MonitoringEvent, MonitoringEventPage, WidgetFieldRecord } from '../../../models';
import { MonitoringService } from '../../../services/monitoring.service';
import { forkJoin, Subscription } from 'rxjs';
import { MonitoringEventsTableComponent } from '../../events-page/monitoring-events-table/monitoring-events-table.component';
import { defaultServerResyncIntervalSeconds } from '../dashboard-clock-widget/clock-widget-config';
import {
  parseProblemsWidgetFields,
  problemsWidgetToMonitoringEventFilter,
  problemsWidgetUsesLegacyHostRefNames,
  sortProblemsWidgetEvents,
  type ProblemsWidgetSortBy,
  type ProblemsWidgetSortOrder,
} from './problems-widget-config';

@Component({
  selector: 'app-dashboard-problems-widget',
  standalone: true,
  imports: [MonitoringEventsTableComponent],
  templateUrl: './dashboard-problems-widget.component.html',
  styleUrl: './dashboard-problems-widget.component.css',
})
export class DashboardProblemsWidgetComponent {
  private readonly monitoring = inject(MonitoringService);

  readonly fields = input.required<WidgetFieldRecord[]>();
  readonly refreshIntervalSeconds = input<number | null>(null);

  protected readonly config = computed(() => parseProblemsWidgetFields(this.fields()));

  protected readonly pageData = signal<MonitoringEventPage | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  protected readonly displayEvents = computed(() => {
    const p = this.pageData();
    const rows = p?.content ?? [];
    const c = this.config();
    return sortProblemsWidgetEvents(rows, c.sortBy, c.sortOrder);
  });

  constructor() {
    effect((onCleanup) => {
      const c = this.config();
      const refreshSec = this.refreshIntervalSeconds();
      let cancelled = false;
      let active: Subscription | null = null;

      const run = () => {
        if (cancelled) return;
        active?.unsubscribe();
        this.loading.set(true);
        this.error.set('');
        const size = c.showLines;
        if (problemsWidgetUsesLegacyHostRefNames(c)) {
          const baseFilter = problemsWidgetToMonitoringEventFilter(c, new Date());
          active = forkJoin(
            c.legacyHostRefNames.map((name) =>
              this.monitoring.getMonitoringEventsPage(
                { ...baseFilter, deviceNameContains: name },
                0,
                size
              )
            )
          ).subscribe({
            next: (pages) => {
              if (cancelled) return;
              this.pageData.set(this.mergeEventPages(pages, size, c.sortBy, c.sortOrder));
              this.loading.set(false);
            },
            error: () => {
              if (!cancelled) {
                this.loading.set(false);
                this.error.set('Не удалось загрузить события.');
              }
            },
          });
          return;
        }

        const filter = problemsWidgetToMonitoringEventFilter(c, new Date());
        active = this.monitoring.getMonitoringEventsPage(filter, 0, size).subscribe({
          next: (p) => {
            if (!cancelled) {
              this.pageData.set(p);
              this.loading.set(false);
            }
          },
          error: () => {
            if (!cancelled) {
              this.loading.set(false);
              this.error.set('Не удалось загрузить события.');
            }
          },
        });
      };

      run();
      const periodMs = defaultServerResyncIntervalSeconds(refreshSec) * 1000;
      const id = window.setInterval(run, periodMs);
      onCleanup(() => {
        cancelled = true;
        active?.unsubscribe();
        window.clearInterval(id);
      });
    });
  }

  private mergeEventPages(
    pages: MonitoringEventPage[],
    limit: number,
    sortBy: ProblemsWidgetSortBy,
    sortOrder: ProblemsWidgetSortOrder
  ): MonitoringEventPage {
    const byId = new Map<number, MonitoringEvent>();
    for (const page of pages) {
      for (const event of page.content ?? []) {
        byId.set(event.id, event);
      }
    }
    const content = sortProblemsWidgetEvents([...byId.values()], sortBy, sortOrder).slice(0, limit);
    return {
      content,
      totalElements: content.length,
      totalPages: 1,
      number: 0,
      size: limit,
      first: true,
      last: true,
    };
  }
}
