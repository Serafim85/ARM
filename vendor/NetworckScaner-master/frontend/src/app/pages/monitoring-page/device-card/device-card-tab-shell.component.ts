import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs/operators';
import { DeviceScanResult } from '../../../models';
import { MonitoringService } from '../../../services/monitoring.service';
import { DeviceCardTabSegment } from './device-card-tab.model';
import { DeviceConfigManagementTabComponent } from './tabs/device-config-management-tab.component';
import { DeviceConfigTabComponent } from './tabs/device-config-tab.component';
import { DeviceEventsTabComponent } from './tabs/device-events-tab.component';
import { DeviceItemConfigTabComponent } from './tabs/device-item-config-tab.component';
import { DeviceMetricsTabComponent } from './tabs/device-metrics-tab.component';
import { DeviceOverviewTabComponent } from './tabs/device-overview-tab.component';
import { DeviceSnapshotTabComponent } from './tabs/device-snapshot-tab.component';

@Component({
  selector: 'app-device-card-tab-shell',
  standalone: true,
  imports: [
    DeviceOverviewTabComponent,
    DeviceConfigTabComponent,
    DeviceMetricsTabComponent,
    DeviceSnapshotTabComponent,
    DeviceItemConfigTabComponent,
    DeviceEventsTabComponent,
    DeviceConfigManagementTabComponent,
  ],
  template: `
    @if (device(); as dev) {
      @switch (tab()) {
        @case ('info') {
          <app-device-overview-tab [device]="dev" />
        }
        @case ('configuration') {
          <app-device-config-tab [device]="dev" />
        }
        @case ('metrics') {
          <app-device-metrics-tab [device]="dev" />
        }
        @case ('snapshot') {
          <app-device-snapshot-tab [device]="dev" />
        }
        @case ('item-config') {
          <app-device-item-config-tab [device]="dev" />
        }
        @case ('events') {
          <app-device-events-tab [device]="dev" />
        }
        @case ('config-management') {
          <app-device-config-management-tab [device]="dev" />
        }
      }
    }
  `,
})
export class DeviceCardTabShellComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly ms = inject(MonitoringService);

  private readonly deviceId = toSignal(
    this.route.parent!.paramMap.pipe(map((p) => p.get('id')!)),
    { initialValue: this.route.parent!.snapshot.paramMap.get('id')! },
  );

  readonly tab = toSignal(this.route.data.pipe(map((d) => d['deviceTab'] as DeviceCardTabSegment)), {
    initialValue: this.route.snapshot.data['deviceTab'] as DeviceCardTabSegment,
  });

  readonly device = computed<DeviceScanResult | null>(() => {
    const id = this.deviceId();
    return this.ms.getMonitoredDevice(id);
  });
}
