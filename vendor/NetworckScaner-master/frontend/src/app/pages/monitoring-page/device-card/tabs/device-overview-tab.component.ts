import { Component, Input, inject } from '@angular/core';
import { DeviceScanResult } from '../../../../models';
import { MonitoringService } from '../../../../services/monitoring.service';
import { DeviceTelemetryLivePanelComponent } from './device-telemetry-live-panel.component';

@Component({
  selector: 'app-device-overview-tab',
  standalone: true,
  imports: [DeviceTelemetryLivePanelComponent],
  templateUrl: './device-overview-tab.component.html',
})
export class DeviceOverviewTabComponent {
  @Input({ required: true }) device!: DeviceScanResult;

  protected readonly ms = inject(MonitoringService);

  protected details() {
    return this.ms.getDetailsOrFallback(this.device);
  }

  protected meta() {
    return this.ms.deviceMeta(this.device.id);
  }
}
