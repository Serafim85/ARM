import { Component, input, model, viewChild } from '@angular/core';
import { Popover, PopoverModule } from 'primeng/popover';
import {
  CHART_LEGEND_PLACEMENT_OPTIONS,
  type ChartLegendPlacement,
} from '../../utils/chart-legend-placement';

@Component({
  selector: 'app-chart-legend-placement-select',
  standalone: true,
  imports: [PopoverModule],
  templateUrl: './chart-legend-placement-select.component.html',
  styleUrl: './chart-legend-placement-select.component.css',
  host: {
    '[class.chart-legend-placement-select--inline]': 'inline()',
  },
})
export class ChartLegendPlacementSelectComponent {
  readonly placement = model.required<ChartLegendPlacement>();
  readonly ariaLabelledBy = input<string | undefined>(undefined);
  readonly inline = input(false);

  private readonly placementPopover = viewChild<Popover>('placementPopover');

  protected readonly options = CHART_LEGEND_PLACEMENT_OPTIONS;

  protected iconClass(value: ChartLegendPlacement): string {
    return `chart-legend-placement-icon chart-legend-placement-icon--${value.toLowerCase()}`;
  }

  protected currentPlacementLabel(): string {
    return this.options.find((option) => option.value === this.placement())?.label ?? '';
  }

  protected selectPlacement(value: ChartLegendPlacement): void {
    this.placement.set(value);
    if (!this.inline()) {
      this.placementPopover()?.hide();
    }
  }
}
