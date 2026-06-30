import { Component, Input, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DeviceScanResult } from '../../../../models';
import { MonitoringService } from '../../../../services/monitoring.service';
import { resolveMetricDisplayLabel } from '../../../../utils/metric-display-label';
import { DeviceMetricsChartComponent } from '../../device-metrics-chart/device-metrics-chart.component';
import { distinctUntilChanged, map } from 'rxjs';

@Component({
  selector: 'app-device-metrics-tab',
  standalone: true,
  imports: [DeviceMetricsChartComponent, ButtonModule],
  template: `
    @if (ifName(); as activeIf) {
      <div class="device-metrics-active-filter">
        <span class="device-metrics-active-filter-label">Интерфейс:</span>
        <strong class="device-metrics-active-filter-value">{{ activeIf }}</strong>
        <span class="device-metrics-active-filter-actions list-page-heading-actions">
          <p-button
            label="Сбросить фильтр"
            [outlined]="true"
            (onClick)="clearInterfaceFilter()"
          />
        </span>
      </div>
    }

    @if (metricKey(); as activeMetricKey) {
      <div class="device-metrics-active-filter">
        <span class="device-metrics-active-filter-label">Метрика:</span>
        <strong class="device-metrics-active-filter-value">{{ metricFilterTitle(activeMetricKey) }}</strong>
        <span class="device-metrics-active-filter-actions list-page-heading-actions">
          <p-button
            label="Сбросить фильтр"
            [outlined]="true"
            (onClick)="clearMetricFilter()"
          />
        </span>
      </div>
    }

    <app-device-metrics-chart
      [deviceId]="device.id"
      [ifName]="ifName()"
      [metricKey]="metricKey()"
    />
  `,
  styles: `
    .device-metrics-active-filter {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0.5rem;
      margin: 0 0 0.75rem 0;
    }

    .device-metrics-active-filter-label {
      color: var(--text-color-secondary, #6b7280);
    }
  `,
})
export class DeviceMetricsTabComponent {
  @Input({ required: true }) device!: DeviceScanResult;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly ms = inject(MonitoringService);

  protected readonly ifName = toSignal(
    this.route.queryParamMap.pipe(
      map((m) => m.get('ifName')?.trim() || null),
      distinctUntilChanged(),
    ),
    { initialValue: this.route.snapshot.queryParamMap.get('ifName')?.trim() || null },
  );

  protected readonly metricKey = toSignal(
    this.route.queryParamMap.pipe(
      map((m) => m.get('metricKey')?.trim() || null),
      distinctUntilChanged(),
    ),
    { initialValue: this.route.snapshot.queryParamMap.get('metricKey')?.trim() || null },
  );

  protected metricFilterTitle(metricKey: string): string {
    this.ms.deviceItemStatePage();
    const item = this.ms.deviceItemState(this.device.id).find((row) => row.itemKey === metricKey);
    return resolveMetricDisplayLabel(metricKey, item?.itemDisplayName);
  }

  protected clearInterfaceFilter(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ifName: null },
      queryParamsHandling: 'merge',
    });
  }

  protected clearMetricFilter(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { metricKey: null },
      queryParamsHandling: 'merge',
    });
  }
}
