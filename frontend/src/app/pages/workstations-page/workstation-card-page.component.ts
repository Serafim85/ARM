import { DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { WorkstationsService } from '../../services/workstations.service';
import { WorkstationMetricsChartsComponent } from './workstation-metrics-charts.component';
import { WorkstationTelemetrySectionComponent } from './workstation-telemetry-section.component';

@Component({
  selector: 'app-workstation-card-page',
  standalone: true,
  imports: [RouterLink, ButtonModule, TagModule, DatePipe, WorkstationMetricsChartsComponent, WorkstationTelemetrySectionComponent],
  templateUrl: './workstation-card-page.component.html',
  styleUrl: './workstation-card-page.component.css',
})
export class WorkstationCardPageComponent implements OnInit {
  protected readonly ws = inject(WorkstationsService);
  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id)) {
      return;
    }
    this.ws.loadWorkstationDetail(id);
    this.ws.loadWorkstationLogs(id);
    this.ws.loadWorkstationEvents(id);
  }

  protected statusSeverity(status: string): 'success' | 'danger' {
    return status === 'online' ? 'success' : 'danger';
  }

  protected statusLabel(status: string): string {
    return status === 'online' ? 'Online' : 'Offline';
  }

  protected osLabel(osType: string): string {
    const normalized = (osType ?? '').toLowerCase();
    if (normalized === 'linux') return 'Linux';
    if (normalized === 'windows') return 'Windows';
    if (normalized === 'macos' || normalized === 'darwin') return 'macOS';
    return osType || '—';
  }
}
