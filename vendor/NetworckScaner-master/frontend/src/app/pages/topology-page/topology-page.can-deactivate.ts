import type { CanDeactivateFn } from '@angular/router';
import type { TopologyPageComponent } from './topology-page.component';

export const topologyPageCanDeactivate: CanDeactivateFn<TopologyPageComponent> = (
  component,
  _currentRoute,
  _currentState,
  nextState,
) => component.confirmLeaveWhenUnsaved(nextState?.url ?? '');
