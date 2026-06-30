import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, Input, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { API_BASE_URL } from '../../../../api-config';
import { DeviceOptionSelectComponent } from '../../../../components/device-option-select/device-option-select.component';
import { DeviceScanResult, PortFilter } from '../../../../models';
import { MonitoringService } from '../../../../services/monitoring.service';
import { defaultDayMetricsRange } from '../../../../utils/metrics-history-range';

type MetricSeriesDto = {
  metricName: string;
  displayName?: string | null;
};

type MetricChartPanelDto = {
  series?: MetricSeriesDto[];
};

type MetricsHistoryResponseDto = {
  chartPanels: MetricChartPanelDto[];
};

@Component({
  selector: 'app-device-config-tab',
  standalone: true,
  imports: [FormsModule, TableModule, InputTextModule, SelectModule, DeviceOptionSelectComponent],
  templateUrl: './device-config-tab.component.html',
  styleUrl: './device-config-tab.component.css',
})
export class DeviceConfigTabComponent implements OnInit {
  @Input({ required: true }) device!: DeviceScanResult;

  private readonly ms = inject(MonitoringService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  protected readonly portFilterOptions: { label: string; value: PortFilter }[] = [
    { label: 'Все порты/интерфейсы', value: 'ALL' },
    { label: 'Активные', value: 'ACTIVE' },
    { label: 'Логические', value: 'LOGICAL' },
  ];

  protected readonly portFilter = signal<PortFilter>('ALL');
  protected readonly portsSearch = signal('');

  protected readonly totalPortsCount = computed(() => this.ms.portConfigs(this.device.id).length);

  protected readonly operUpTotalCount = computed(() =>
    this.ms.portConfigs(this.device.id).filter((p) => p.operStatus === 'UP').length,
  );

  protected readonly logicalPortsTotalCount = computed(() =>
    this.ms.portConfigs(this.device.id).filter((p) => p.kind === 'logical').length,
  );

  protected readonly filteredPorts = computed(() => {
    const ports = this.ms.portConfigs(this.device.id);
    const filter = this.portFilter();
    let list =
      filter === 'ACTIVE' ? ports.filter((p) => p.operStatus === 'UP') : filter === 'LOGICAL' ? ports.filter((p) => p.kind === 'logical') : [...ports];
    const q = this.portsSearch().trim().toLowerCase();
    if (q) {
      list = list.filter((p) =>
        [p.name, p.description, p.adminStatus, p.operStatus, p.purpose, p.mode, p.nominalSpeed, p.activeSpeed, String(p.lost)]
          .filter((v) => v != null && v !== '')
          .join(' ')
          .toLowerCase()
          .includes(q),
      );
    }
    return list;
  });

  private readonly chartMetricsTextIndex = signal<string[]>([]);

  private escapeRegExpLiteral(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  /**
   * Матчит имя интерфейса как “токен”, чтобы `ens18` не совпадал с `ens18.10`.
   * Разрешаем символы внутри имени (в т.ч. `.`, `-`, `_`, `/`), но требуем границы
   * по классу токена [a-z0-9._-/].
   */
  private buildInterfaceTokenMatcher(ifName: string): RegExp {
    const token = this.escapeRegExpLiteral(ifName.trim().toLowerCase());
    return new RegExp(`(^|[^a-z0-9._\\-/])${token}([^a-z0-9._\\-/]|$)`, 'i');
  }

  ngOnInit(): void {
    const timer = window.setInterval(() => {
      this.ms.loadPortConfiguration(this.device);
      this.loadInterfaceMetricsIndex();
    }, 300_000);
    this.destroyRef.onDestroy(() => window.clearInterval(timer));

    this.loadInterfaceMetricsIndex();
  }

  private loadInterfaceMetricsIndex(): void {
    const { fromIso, toIso } = defaultDayMetricsRange();
    this.http
      .get<MetricsHistoryResponseDto>(`${this.apiBaseUrl}/api/monitoring/devices/${this.device.id}/metrics`, {
        params: {
          from: fromIso,
          to: toIso,
          panelsOffset: '0',
          panelsLimit: '0',
          maxPoints: '1',
        },
      })
      .subscribe({
        next: (payload) => {
          const texts = (payload?.chartPanels ?? []).flatMap((panel) =>
            (panel.series ?? []).map((series) =>
              `${series.displayName ?? ''} ${series.metricName ?? ''}`.toLowerCase()
            )
          );
          this.chartMetricsTextIndex.set(texts);
        },
        error: () => this.chartMetricsTextIndex.set([]),
      });
  }

  protected canOpenInterfaceMetrics(portName: string): boolean {
    const ifName = portName?.trim();
    if (!ifName) return false;
    const rx = this.buildInterfaceTokenMatcher(ifName);
    return this.chartMetricsTextIndex().some((t) => rx.test(t));
  }

  protected openInterfaceMetrics(portName: string): void {
    const ifName = portName?.trim();
    if (!ifName) return;
    if (!this.canOpenInterfaceMetrics(ifName)) return;

    void this.router.navigate(['../metrics'], {
      relativeTo: this.route,
      queryParams: { ifName, metricKey: null },
    });
  }

  protected onPortFilterChange(value: string | number | null): void {
    const v = value == null ? 'ALL' : String(value);
    if (v === 'ALL' || v === 'ACTIVE' || v === 'LOGICAL') {
      this.portFilter.set(v);
    }
  }
}
