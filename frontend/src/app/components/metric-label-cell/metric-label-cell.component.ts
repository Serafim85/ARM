import { Component, computed, input, output } from '@angular/core';
import { resolveMetricDisplayLabel } from '../../utils/metric-display-label';

@Component({
  selector: 'app-metric-label-cell',
  standalone: true,
  templateUrl: './metric-label-cell.component.html',
  styleUrl: './metric-label-cell.component.css',
})
export class MetricLabelCellComponent {
  readonly itemKey = input.required<string>();
  readonly itemDisplayName = input<string | null | undefined>(null);
  readonly instanceKey = input<string | null | undefined>(null);
  readonly titleLink = input(false);

  readonly titleClick = output<void>();

  protected readonly title = computed(() =>
    resolveMetricDisplayLabel(this.itemKey(), this.itemDisplayName()),
  );

  protected readonly technicalKey = computed(() => {
    const key = this.itemKey();
    const instance = this.instanceKey()?.trim();
    return instance ? `${key} [${instance}]` : key;
  });

  protected onTitleClick(): void {
    this.titleClick.emit();
  }
}
