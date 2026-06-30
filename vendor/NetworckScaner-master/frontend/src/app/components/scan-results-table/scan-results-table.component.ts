import { Component, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { TooltipOnOverflowDirective } from '../../directives/tooltip-on-overflow.directive';
import { DeviceScanResult } from '../../models';
import { MonitoringService } from '../../services/monitoring.service';
import { availabilityChipLabel, scanResultSummary } from '../../utils/scan-result.util';

@Component({
  selector: 'app-scan-results-table',
  standalone: true,
  imports: [FormsModule, TableModule, CheckboxModule, TooltipModule, TooltipOnOverflowDirective],
  templateUrl: './scan-results-table.component.html',
  styleUrl: './scan-results-table.component.css',
})
export class ScanResultsTableComponent {
  protected readonly mon = inject(MonitoringService);

  readonly devices = input.required<DeviceScanResult[]>();
  readonly showSelection = input(false);
  readonly selectionDisabled = input(false);
  readonly selectedIps = input<string[]>([]);
  readonly showMonitoring = input(false);
  readonly tableClass = input('p-datatable-sm netscan-p-table scan-results-table');

  readonly selectionChange = output<{ ip: string; checked: boolean }>();

  protected cellText(value: string | null | undefined): string {
    const trimmed = (value ?? '').trim();
    return trimmed && trimmed !== '-' ? trimmed : '—';
  }

  protected cellTooltip(value: string | null | undefined): string {
    const trimmed = (value ?? '').trim();
    return trimmed && trimmed !== '-' ? trimmed : '';
  }

  protected resultSummary(device: DeviceScanResult): string {
    return scanResultSummary(device);
  }

  protected availabilityLabel(label: string): string {
    return availabilityChipLabel(label);
  }

  protected isSelected(ip: string): boolean {
    return this.selectedIps().includes(ip);
  }

  protected onSelectionChange(ip: string, checked: boolean): void {
    this.selectionChange.emit({ ip, checked });
  }
}
