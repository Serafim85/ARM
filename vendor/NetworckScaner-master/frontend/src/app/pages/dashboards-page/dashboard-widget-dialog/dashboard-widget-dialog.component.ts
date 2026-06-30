import {
  OnChanges,
  Component,
  EventEmitter,
  Input,
  Output,
  SimpleChanges,
  ViewChild,
  computed,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SliderModule } from 'primeng/slider';
import type {
  DashboardWidget,
  WidgetCreatePayload,
  WidgetFieldRecord,
  WidgetUpdatePayload,
} from '../../../models';
import { DashboardsService } from '../../../services/dashboards.service';
import { DeviceOptionSelectComponent } from '../../../components/device-option-select/device-option-select.component';
import { ClockWidgetFieldsComponent } from './clock-widget-fields.component';
import { GraphWidgetFieldsComponent } from './graph-widget-fields.component';
import { ProblemsWidgetFieldsComponent } from './problems-widget-fields.component';
import {
  DEFAULT_WIDGET_CREATE_SIZE,
  WIDGET_EDITOR_OPTIONS,
  WIDGET_EDITOR_REGISTRY,
  isEditableWidgetType,
} from './widget-editor-registry';
import type { WidgetEditorType, WidgetFieldsEditorApi } from './widget-editor.types';

@Component({
  selector: 'app-dashboard-widget-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    SliderModule,
    SelectModule,
    DeviceOptionSelectComponent,
    CheckboxModule,
    ClockWidgetFieldsComponent,
    GraphWidgetFieldsComponent,
    ProblemsWidgetFieldsComponent,
  ],
  templateUrl: './dashboard-widget-dialog.component.html',
  styleUrl: './dashboard-widget-dialog.component.css',
})
export class DashboardWidgetDialogComponent implements OnChanges {
  private readonly dashboardsApi = inject(DashboardsService);

  @Input() visible = false;
  @Output() readonly visibleChange = new EventEmitter<boolean>();

  @Input() dashboardId: number | null = null;
  @Input() widget: DashboardWidget | null = null;
  @Output() readonly saved = new EventEmitter<void>();

  @ViewChild(ClockWidgetFieldsComponent) private clockEditor?: ClockWidgetFieldsComponent;
  @ViewChild(GraphWidgetFieldsComponent) private graphEditor?: GraphWidgetFieldsComponent;
  @ViewChild(ProblemsWidgetFieldsComponent) private problemsEditor?: ProblemsWidgetFieldsComponent;

  protected readonly typeOptions = WIDGET_EDITOR_OPTIONS;
  protected readonly editorRegistry = WIDGET_EDITOR_REGISTRY;

  protected readonly isEditMode = computed(() => !!this.widget);
  protected selectedType: WidgetEditorType = 'CLOCK';
  protected name = '';
  protected viewMode = 0;
  protected refreshIntervalSeconds: number | null = null;
  protected showHeader = true;
  /** Рамка ячейки сетки (gridster-item), по умолчанию 1px gray. */
  protected borderWidthPx = 1;
  protected borderColor = 'gray';
  protected saving = false;
  protected error = '';

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['visible'] && this.visible) || (changes['widget'] && this.visible)) {
      this.reinitForm();
    }
  }

  protected onVisibleChange(next: boolean): void {
    this.visible = next;
    if (next) {
      this.reinitForm();
    } else {
      this.error = '';
    }
    this.visibleChange.emit(next);
  }

  protected close(): void {
    if (this.saving) {
      return;
    }
    this.onVisibleChange(false);
  }

  protected onTypeChange(next: WidgetEditorType): void {
    this.selectedType = next;
    setTimeout(() => this.patchEditorWith(this.widget?.fields ?? []), 0);
  }

  /** Значение для нативного `<input type="color">` (только #RRGGBB). */
  protected get borderColorPickerValue(): string {
    const v = this.borderColor?.trim() ?? '';
    if (/^#[0-9A-Fa-f]{6}$/.test(v)) {
      return v;
    }
    if (v.toLowerCase() === 'gray' || v.toLowerCase() === 'grey') {
      return '#808080';
    }
    return '#808080';
  }

  protected onBorderColorPicked(event: Event): void {
    this.borderColor = (event.target as HTMLInputElement).value;
  }

  protected onTypeChangeFromSelect(value: string | number | null): void {
    const v = value == null ? this.selectedType : String(value);
    if (v === 'CLOCK' || v === 'PROBLEMS' || v === 'GRAPH') {
      this.onTypeChange(v);
    }
  }

  protected save(): void {
    if (!this.dashboardId) {
      this.error = 'Не выбран дашборд для сохранения виджета.';
      return;
    }
    const trimmedName = this.name.trim();
    if (!trimmedName) {
      this.error = 'Укажите имя виджета.';
      return;
    }
    if (this.refreshIntervalSeconds != null && this.refreshIntervalSeconds < 1) {
      this.error = 'Интервал обновления должен быть не меньше 1 секунды.';
      return;
    }
    const bw = this.borderWidthPx ?? 1;
    if (bw < 0 || bw > 32) {
      this.error = 'Ширина границы должна быть от 0 до 32 пикселей.';
      return;
    }
    const fields = this.getActiveEditor()?.buildFields();
    if (!fields) {
      this.error = 'Редактор для выбранного типа не подключён.';
      return;
    }
    const layout = this.resolveLayoutForSave();
    const borderColor = (this.borderColor ?? '').trim() || 'gray';
    const baseBody = {
      widgetType: this.selectedType,
      name: trimmedName,
      gridX: layout.gridX,
      gridY: layout.gridY,
      width: layout.width,
      height: layout.height,
      viewMode: this.viewMode ?? 0,
      refreshIntervalSeconds: this.refreshIntervalSeconds ?? null,
      showHeader: this.showHeader,
      borderWidthPx: bw,
      borderColor,
      fields,
    };
    this.saving = true;
    this.error = '';
    if (this.widget) {
      const payload: WidgetUpdatePayload = baseBody;
      this.dashboardsApi.updateWidget(this.dashboardId, this.widget.id, payload).subscribe({
        next: () => this.onSaveSuccess(),
        error: (e) => this.onSaveError(e),
      });
      return;
    }
    const payload: WidgetCreatePayload = baseBody;
    this.dashboardsApi.createWidget(this.dashboardId, payload).subscribe({
      next: () => this.onSaveSuccess(),
      error: (e) => this.onSaveError(e),
    });
  }

  private onSaveSuccess(): void {
    this.saving = false;
    this.saved.emit();
    this.onVisibleChange(false);
  }

  private onSaveError(error: unknown): void {
    this.saving = false;
    const msg = (error as { error?: { message?: string }; message?: string })?.error?.message
      ?? (error as { message?: string })?.message
      ?? 'Не удалось сохранить виджет.';
    this.error = msg;
  }

  private reinitForm(): void {
    this.error = '';
    const w = this.widget;
    if (!w) {
      this.selectedType = 'CLOCK';
      this.name = '';
      this.viewMode = 0;
      this.refreshIntervalSeconds = null;
      this.showHeader = true;
      this.borderWidthPx = 1;
      this.borderColor = 'gray';
      setTimeout(() => this.patchEditorWith([]), 0);
      return;
    }
    this.selectedType = isEditableWidgetType(w.widgetType) ? w.widgetType : 'CLOCK';
    this.name = w.name ?? '';
    this.viewMode = w.viewMode ?? 0;
    this.refreshIntervalSeconds = w.refreshIntervalSeconds ?? null;
    this.showHeader = w.showHeader ?? true;
    this.borderWidthPx = w.borderWidthPx ?? 1;
    this.borderColor = (w.borderColor ?? 'gray').trim() || 'gray';
    setTimeout(() => this.patchEditorWith(w.fields ?? []), 0);
  }

  private patchEditorWith(fields: WidgetFieldRecord[]): void {
    this.getActiveEditor()?.patchFromFields(fields);
  }

  /**
   * Создание: (0,0) и размер по типу из {@link DEFAULT_WIDGET_CREATE_SIZE}.
   * Редактирование: сохраняем layout с сервера, чтобы не затирать drag/resize.
   */
  private resolveLayoutForSave(): { gridX: number; gridY: number; width: number; height: number } {
    if (this.widget) {
      return {
        gridX: this.widget.gridX ?? 0,
        gridY: this.widget.gridY ?? 0,
        width: this.widget.width ?? 1,
        height: this.widget.height ?? 2,
      };
    }
    const size = DEFAULT_WIDGET_CREATE_SIZE[this.selectedType];
    return { gridX: 0, gridY: 0, width: size.width, height: size.height };
  }

  private getActiveEditor(): WidgetFieldsEditorApi | undefined {
    if (this.selectedType === 'CLOCK') {
      return this.clockEditor;
    }
    if (this.selectedType === 'PROBLEMS') {
      return this.problemsEditor;
    }
    if (this.selectedType === 'GRAPH') {
      return this.graphEditor;
    }
    return undefined;
  }
}
