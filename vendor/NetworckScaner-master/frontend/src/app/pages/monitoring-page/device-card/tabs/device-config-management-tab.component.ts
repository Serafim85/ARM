import { Component, input } from '@angular/core';
import { DeviceScanResult } from '../../../../models';
import { DeviceBackupSectionComponent } from '../device-backup-section.component';

@Component({
  selector: 'app-device-config-management-tab',
  standalone: true,
  imports: [DeviceBackupSectionComponent],
  template: `<app-device-backup-section [device]="device()" [embedded]="true" />`,
})
export class DeviceConfigManagementTabComponent {
  readonly device = input.required<DeviceScanResult>();
}
