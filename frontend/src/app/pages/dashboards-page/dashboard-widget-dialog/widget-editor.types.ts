import type { WidgetFieldRecord, WidgetFieldUpsert } from '../../../models';

export type WidgetEditorType = 'CLOCK' | 'PROBLEMS' | 'GRAPH';

export interface WidgetFieldsEditorApi {
  patchFromFields(fields: WidgetFieldRecord[]): void;
  buildFields(): WidgetFieldUpsert[];
}
