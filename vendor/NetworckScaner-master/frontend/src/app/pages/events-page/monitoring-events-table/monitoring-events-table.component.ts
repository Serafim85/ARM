import { CommonModule, NgClass, NgStyle } from '@angular/common';
import { Component, computed, input, output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import type { TableLazyLoadEvent } from 'primeng/types/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { NsTableColumnWidthsDirective } from '../../../directives/ns-table-column-widths.directive';
import {
  buildColumnBoundsMap,
  columnBoundsStyle,
  type TableColumnWidthsMap,
} from '../../../utils/table-column-widths';
import {
  MonitoringEvent,
  MonitoringEventLevel,
  MonitoringEventStatus,
  monitoringEventLevelLabel,
  monitoringEventStatusLabel,
  monitoringEventStatusTagSeverity,
  normalizeMonitoringEventLevel,
} from '../../../models';
import { resolveMetricDisplayLabel } from '../../../utils/metric-display-label';
import {
  formatMonitoringEventDate,
  formatMonitoringEventDuration,
} from '../../../utils/monitoring-event-formatters';
import {
  cloneDefaultMonitoringEventsColumns,
  monitoringEventsTableColumnOrder,
  monitoringEventsTableWidthDefs,
  type MonitoringEventsColumnDef,
} from './monitoring-events-table-columns';

@Component({
  selector: 'app-monitoring-events-table',
  standalone: true,
  imports: [
    CommonModule,
    NgClass,
    NgStyle,
    FormsModule,
    TableModule,
    TagModule,
    TooltipModule,
    ButtonModule,
    NsTableColumnWidthsDirective,
  ],
  templateUrl: './monitoring-events-table.component.html',
  styleUrl: './monitoring-events-table.component.css',
})
export class MonitoringEventsTableComponent {
  private static readonly DEVICE_NAME_DISPLAY_MAX = 25;

  @ViewChild('eventsTableWidths') eventsTableWidths?: NsTableColumnWidthsDirective;

  readonly events = input.required<MonitoringEvent[]>();
  readonly loading = input(false);
  readonly lazy = input(true);
  readonly paginator = input(true);
  readonly rows = input(20);
  readonly totalRecords = input(0);
  readonly first = input(0);
  readonly rowsPerPageOptions = input<number[]>([10, 20, 50]);
  readonly showCurrentPageReport = input(true);
  readonly alwaysShowPaginator = input(false);
  readonly currentPageReportTemplate = input('{first}–{last} из {totalRecords}');
  readonly tableMinWidthPx = input(1040);
  readonly showTimeline = input(true);
  readonly highlightRow = input(false);
  readonly cellLinksEnabled = input(true);
  /** Клиентская сортировка страницы (lazy без server sort): состояние заголовков p-table. */
  readonly sortField = input<string | null>(null);
  readonly sortOrder = input<number>(1);
  /** Второй абзац пустого состояния; null — текст по умолчанию для списка «События». */
  readonly emptySecondaryText = input<string | null>(null);
  /** Порядок и видимость колонок; по умолчанию — стандартная раскладка. */
  readonly columns = input<MonitoringEventsColumnDef[]>(cloneDefaultMonitoringEventsColumns());
  readonly columnWidthsMap = input<TableColumnWidthsMap>({});

  readonly lazyLoad = output<TableLazyLoadEvent>();
  readonly deviceNameFilterRequest = output<{ name: string; apply: boolean }>();
  readonly deviceIpFilterRequest = output<{ ip: string; apply: boolean }>();
  readonly macFilterRequest = output<{ mac: string; apply: boolean }>();
  readonly metricFilterRequest = output<{ metricName: string; apply: boolean }>();
  readonly statusFilterRequest = output<{ status: MonitoringEventStatus; apply: boolean }>();
  readonly levelFilterRequest = output<{ level: MonitoringEventLevel; apply: boolean }>();

  protected readonly displayedColumns = computed(() => this.columns().filter((c) => c.visible));

  protected readonly tableWidthColumnDefs = computed(() =>
    monitoringEventsTableWidthDefs(this.displayedColumns())
  );
  protected readonly tableWidthColumnOrder = computed(() =>
    monitoringEventsTableColumnOrder(this.displayedColumns())
  );
  protected readonly tableColumnBounds = computed(() =>
    buildColumnBoundsMap(this.tableWidthColumnDefs())
  );
  protected readonly columnBoundsStyle = columnBoundsStyle;

  protected readonly emptyColspan = computed(() => {
    const n = this.displayedColumns().length;
    return n > 0 ? n : 1;
  });

  protected readonly monitoringEventLevelLabel = monitoringEventLevelLabel;
  protected readonly monitoringEventStatusLabel = monitoringEventStatusLabel;
  protected readonly monitoringEventStatusTagSeverity = monitoringEventStatusTagSeverity;
  protected readonly normalizeMonitoringEventLevel = normalizeMonitoringEventLevel;
  protected readonly resolveMetricDisplayLabel = resolveMetricDisplayLabel;
  protected readonly formatDate = formatMonitoringEventDate;
  protected readonly formatDuration = formatMonitoringEventDuration;

  /** Первые 25 символов и «...», если имя длиннее; полное имя — в подсказке при наведении. */
  protected deviceNameDisplay(name: string | null | undefined): string {
    const n = name ?? '';
    if (n.length <= MonitoringEventsTableComponent.DEVICE_NAME_DISPLAY_MAX) {
      return n;
    }
    return `${n.slice(0, MonitoringEventsTableComponent.DEVICE_NAME_DISPLAY_MAX)}...`;
  }

  /** Подсказка с полным именем только для длинных имён. */
  protected deviceNameTooltipDisabled(name: string | null | undefined): boolean {
    return (name ?? '').length <= MonitoringEventsTableComponent.DEVICE_NAME_DISPLAY_MAX;
  }

  protected onLazyLoad(ev: TableLazyLoadEvent): void {
    this.lazyLoad.emit(ev);
  }

  protected onDeviceNameClick(name: string, apply: boolean): void {
    this.deviceNameFilterRequest.emit({ name, apply });
  }

  protected onDeviceIpClick(ip: string, apply: boolean): void {
    this.deviceIpFilterRequest.emit({ ip, apply });
  }

  protected onMacClick(mac: string, apply: boolean): void {
    this.macFilterRequest.emit({ mac, apply });
  }

  protected onMetricClick(metricName: string, apply: boolean): void {
    this.metricFilterRequest.emit({ metricName, apply });
  }

  protected onStatusClick(status: MonitoringEventStatus, apply: boolean): void {
    this.statusFilterRequest.emit({ status, apply });
  }

  protected onLevelClick(raw: string | null | undefined, apply: boolean): void {
    const level = normalizeMonitoringEventLevel(raw);
    this.levelFilterRequest.emit({ level, apply });
  }

  protected monitoringEventLevelTagSeverity(
    raw: string | null | undefined
  ): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    const level = normalizeMonitoringEventLevel(raw);
    const map: Record<
      MonitoringEventLevel,
      'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'
    > = {
      NOT_CLASSIFIED: 'secondary',
      INFORMATION: 'info',
      WARNING: 'warn',
      AVERAGE: 'warn',
      HIGH: 'danger',
      DISASTER: 'danger',
    };
    return map[level] ?? 'secondary';
  }

  protected tableStyle(): Record<string, string> {
    return { width: '100%', 'min-width': `${this.tableMinWidthPx()}px` };
  }

  resetDomColumnWidths(): void {
    this.eventsTableWidths?.resetDomWidths();
  }
}
