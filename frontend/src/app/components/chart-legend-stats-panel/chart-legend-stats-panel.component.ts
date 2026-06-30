import { Component, input } from '@angular/core';
import type { ChartLegendPlacement } from '../../utils/chart-legend-placement';
import { chartLegendPlacementClass } from '../../utils/chart-legend-placement';
import {
  formatChartStatValue,
  isThresholdLegendRow,
  legendShowsAggregateStats,
  type ChartLegendRow,
  type ChartSeriesStatRow,
  type ChartThresholdLegendRow,
} from '../../utils/chart-series-stats.util';

@Component({
  selector: 'app-chart-legend-stats-panel',
  standalone: true,
  templateUrl: './chart-legend-stats-panel.component.html',
  styleUrl: './chart-legend-stats-panel.component.css',
})
export class ChartLegendStatsPanelComponent {
  readonly rows = input.required<ChartLegendRow[]>();
  readonly placement = input.required<ChartLegendPlacement>();

  protected placementClass(): string {
    return chartLegendPlacementClass(this.placement());
  }

  protected formatValue(value: number | null, unit = '', row?: ChartSeriesStatRow): string {
    return formatChartStatValue(value, unit, row);
  }

  protected showAggregateStats(): boolean {
    return legendShowsAggregateStats(this.seriesRows());
  }

  protected seriesRows(): ChartSeriesStatRow[] {
    return this.rows().filter((row): row is ChartSeriesStatRow => !isThresholdLegendRow(row));
  }

  protected thresholdRows(): ChartThresholdLegendRow[] {
    return this.rows().filter(isThresholdLegendRow);
  }
}
