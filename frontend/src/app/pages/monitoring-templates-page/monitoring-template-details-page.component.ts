import { Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MonitoringService } from '../../services/monitoring.service';

@Component({
  selector: 'app-monitoring-template-details-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './monitoring-template-details-page.component.html',
  styleUrl: './monitoring-template-details-page.component.css',
})
export class MonitoringTemplateDetailsPageComponent implements OnInit {
  protected readonly mon = inject(MonitoringService);
  private readonly route = inject(ActivatedRoute);

  protected readonly templateId = computed(() => this.route.snapshot.paramMap.get('id') ?? '');

  ngOnInit(): void {
    const id = this.templateId();
    if (!id) return;
    this.mon.loadMonitoringTemplateDetails(id);
  }
}

