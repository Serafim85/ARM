import { Injectable, computed, inject, signal } from '@angular/core';

import { Observable, of, tap } from 'rxjs';

import { AuthService } from '../auth.service';

import { type ChartBaseColor } from '../utils/chart-colors';

import {

  type ChartLegendPlacement,

  type ChartUiPreferences,

  type DeviceMetricsLayout,

  type DeviceMetricsPeriod,

  DEFAULT_CHART_LEGEND_PLACEMENT,

  defaultChartUiPreferences,

  mergeChartUiPreferences,

} from '../utils/chart-legend-placement';



@Injectable({ providedIn: 'root' })

export class ChartUiPreferencesService {

  private readonly auth = inject(AuthService);

  private readonly prefs = signal<ChartUiPreferences>(defaultChartUiPreferences());

  private loaded = false;

  private loading = false;



  readonly deviceMetricsLegendPlacement = computed(() => this.prefs().deviceMetricsLegendPlacement);

  readonly deviceMetricsBaseColor = computed(() => this.prefs().deviceMetricsBaseColor);

  readonly deviceMetricsPeriod = computed(() => this.prefs().deviceMetricsPeriod);

  readonly deviceMetricsLayout = computed(() => this.prefs().deviceMetricsLayout);

  readonly deviceMetricsCustomFrom = computed(() => this.prefs().deviceMetricsCustomFrom);

  readonly deviceMetricsCustomTo = computed(() => this.prefs().deviceMetricsCustomTo);



  ensureLoaded(): Observable<ChartUiPreferences> {

    if (this.loaded) {

      return of(this.prefs());

    }

    if (this.loading) {

      return of(this.prefs());

    }

    this.loading = true;

    return this.auth.getChartUiPreferences().pipe(

      tap({

        next: (raw) => {

          this.prefs.set(mergeChartUiPreferences(raw));

          this.loaded = true;

          this.loading = false;

        },

        error: () => {

          this.loading = false;

        },

      })

    );

  }



  dashboardWidgetPlacement(widgetId: number): ChartLegendPlacement {

    const key = String(widgetId);

    return this.prefs().dashboardGraphLegendPlacements[key] ?? DEFAULT_CHART_LEGEND_PLACEMENT;

  }



  setDeviceMetricsLegendPlacement(placement: ChartLegendPlacement): void {

    const next = {

      ...this.prefs(),

      deviceMetricsLegendPlacement: placement,

    };

    this.prefs.set(next);

    this.persist(next);

  }



  setDeviceMetricsBaseColor(color: ChartBaseColor): void {

    const next = {

      ...this.prefs(),

      deviceMetricsBaseColor: color,

    };

    this.prefs.set(next);

    this.persist(next);

  }



  setDeviceMetricsPeriod(period: DeviceMetricsPeriod): void {

    const next = {

      ...this.prefs(),

      deviceMetricsPeriod: period,

    };

    this.prefs.set(next);

    this.persist(next);

  }



  setDeviceMetricsLayout(layout: DeviceMetricsLayout): void {

    const next = {

      ...this.prefs(),

      deviceMetricsLayout: layout,

    };

    this.prefs.set(next);

    this.persist(next);

  }



  setDeviceMetricsCustomRange(from: string | null, to: string | null): void {

    const next = {

      ...this.prefs(),

      deviceMetricsCustomFrom: from,

      deviceMetricsCustomTo: to,

    };

    this.prefs.set(next);

    this.persist(next);

  }



  setDashboardWidgetLegendPlacement(widgetId: number, placement: ChartLegendPlacement): void {

    const next = {

      ...this.prefs(),

      dashboardGraphLegendPlacements: {

        ...this.prefs().dashboardGraphLegendPlacements,

        [String(widgetId)]: placement,

      },

    };

    this.prefs.set(next);

    this.persist(next);

  }



  private persist(prefs: ChartUiPreferences): void {

    this.auth.updateChartUiPreferences(prefs).subscribe({

      next: (saved) => this.prefs.set(mergeChartUiPreferences(saved)),

      error: () => {

        // Оставляем локальное значение; ошибку не показываем при каждом клике по переключателю.

      },

    });

  }

}


