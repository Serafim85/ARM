import { DatePipe } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { WorkstationsService } from '../../services/workstations.service';

@Component({
  selector: 'app-workstation-telemetry-section',
  standalone: true,
  imports: [DatePipe, TableModule, TagModule],
  templateUrl: './workstation-telemetry-section.component.html',
  styleUrl: './workstation-telemetry-section.component.css',
})
export class WorkstationTelemetrySectionComponent {
  @Input({ required: true }) workstationId!: number;

  protected readonly ws = inject(WorkstationsService);

  protected eventTypeLabel(eventType: string): string {
    const normalized = (eventType ?? '').toUpperCase();
    if (normalized === 'BSOD') return 'Синий экран (BSoD)';
    if (normalized === 'KERNEL_PANIC') return 'Kernel panic';
    return eventType || '—';
  }

  protected severityTag(severity: string): 'danger' | 'warn' | 'info' {
    const normalized = (severity ?? '').toUpperCase();
    if (normalized === 'HIGH' || normalized === 'CRITICAL') return 'danger';
    if (normalized === 'WARNING' || normalized === 'AVERAGE') return 'warn';
    return 'info';
  }

  protected logLevelTag(level: string): 'danger' | 'warn' | 'info' {
    const normalized = (level ?? '').toUpperCase();
    if (normalized === 'ERROR' || normalized === 'CRITICAL') return 'danger';
    if (normalized === 'WARNING' || normalized === 'WARN') return 'warn';
    return 'info';
  }
}
