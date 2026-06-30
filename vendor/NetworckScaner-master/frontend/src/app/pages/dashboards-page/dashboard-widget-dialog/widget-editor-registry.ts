import type { Type } from '@angular/core';
import { ClockWidgetFieldsComponent } from './clock-widget-fields.component';
import { GraphWidgetFieldsComponent } from './graph-widget-fields.component';
import { ProblemsWidgetFieldsComponent } from './problems-widget-fields.component';
import type { WidgetEditorType, WidgetFieldsEditorApi } from './widget-editor.types';

export type WidgetEditorRegistration = {
  labelRu: string;
  component: Type<WidgetFieldsEditorApi>;
};

export const WIDGET_EDITOR_REGISTRY: Record<WidgetEditorType, WidgetEditorRegistration> = {
  CLOCK: {
    labelRu: 'Часы',
    component: ClockWidgetFieldsComponent,
  },
  PROBLEMS: {
    labelRu: 'Проблемы',
    component: ProblemsWidgetFieldsComponent,
  },
  GRAPH: {
    labelRu: 'График',
    component: GraphWidgetFieldsComponent,
  },
};

/** Размер нового виджета по типу (позиция всегда 0,0; дальше — drag/resize на дашборде). */
export const DEFAULT_WIDGET_CREATE_SIZE: Record<WidgetEditorType, { width: number; height: number }> = {
  CLOCK: { width: 1, height: 2 },
  PROBLEMS: { width: 1, height: 2 },
  GRAPH: { width: 2, height: 3 },
};

export function isEditableWidgetType(type: string): type is WidgetEditorType {
  return type in WIDGET_EDITOR_REGISTRY;
}

export const WIDGET_EDITOR_OPTIONS = Object.entries(WIDGET_EDITOR_REGISTRY).map(([value, meta]) => ({
  value,
  label: meta.labelRu,
}));
