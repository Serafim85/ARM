import { Component, input, model, viewChild } from '@angular/core';
import { Popover, PopoverModule } from 'primeng/popover';
import { CHART_BASE_COLOR_OPTIONS, normalizeChartBaseColor, type ChartBaseColor } from '../../utils/chart-colors';

@Component({
  selector: 'app-chart-base-color-select',
  standalone: true,
  imports: [PopoverModule],
  templateUrl: './chart-base-color-select.component.html',
  styleUrl: './chart-base-color-select.component.css',
})
export class ChartBaseColorSelectComponent {
  readonly color = model.required<ChartBaseColor>();
  readonly ariaLabelledBy = input<string | undefined>(undefined);

  private readonly colorPopover = viewChild<Popover>('colorPopover');

  protected readonly options = CHART_BASE_COLOR_OPTIONS;

  protected currentColorLabel(): string {
    return this.options.find((option) => option.value === this.color())?.label ?? '';
  }

  protected selectColor(value: ChartBaseColor): void {
    this.color.set(normalizeChartBaseColor(value));
    this.colorPopover()?.hide();
  }
}
