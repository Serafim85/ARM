import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, SimpleChanges, computed, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import type { MonitoringTemplateSummary } from '../../models';
import { TooltipOnOverflowDirective } from '../../directives/tooltip-on-overflow.directive';

type TemplateView = {
  id: string;
  name: string;
  description?: string | null;
  vendor?: string | null;
  type?: string | null;
};

export type MonitoringTemplateSelection = {
  templateIds: string[];
  autoDetection: boolean;
};

@Component({
  selector: 'app-monitoring-template-picker-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DialogModule,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    TooltipModule,
    TooltipOnOverflowDirective,
  ],
  templateUrl: './monitoring-template-picker-dialog.component.html',
  styleUrl: './monitoring-template-picker-dialog.component.css',
})
export class MonitoringTemplatePickerDialogComponent {
  @Input({ required: true }) visible = false;
  @Output() readonly visibleChange = new EventEmitter<boolean>();

  readonly templates = input.required<MonitoringTemplateSummary[]>();
  readonly templatesLoading = input(false);
  @Input({ required: true }) selectedTemplateIds: string[] = [];
  @Output() readonly selectedTemplateIdsChange = new EventEmitter<string[]>();

  @Input() title = 'Выбор шаблонов мониторинга';
  /** Закрывать диалог по кнопке «Сохранить»; false — родитель закрывает сам (например, после confirm). */
  @Input() closeOnSave = true;
  /** Минимум выбранных шаблонов; при достижении лимита удаление скрыто. 0 — без ограничения. */
  @Input() minSelectedCount = 0;
  @Input() autoDetectionEnabled = false;
  @Input() autoDetection = false;
  @Output() readonly autoDetectionChange = new EventEmitter<boolean>();
  @Output() readonly selectionChange = new EventEmitter<MonitoringTemplateSelection>();

  protected readonly autoDetectionTooltip =
    'Шаблон подбирается по вендору, модели и версии прошивки. При нескольких совпадениях выбирается шаблон с наивысшим приоритетом (0–100).';

  protected readonly query = signal('');
  protected readonly draftSelectedIds = signal<string[]>([]);
  protected readonly draftAutoDetection = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue === true) {
      this.resetDraftFromInput();
      return;
    }
    // Важно: родитель может менять выбранные шаблоны без открытия этого диалога.
    // Если диалог закрыт — синхронизируем черновик для следующего открытия.
    if (!this.visible && (changes['selectedTemplateIds'] || changes['autoDetection'])) {
      this.resetDraftFromInput();
    }
  }

  private readonly templatesView = computed<TemplateView[]>(() =>
    (this.templates() ?? []).map((t) => ({
      id: String(t.id),
      name: t.name,
      description: t.description ?? null,
      vendor: t.vendor ?? null,
      type: t.type ?? null,
    }))
  );

  protected readonly manualSelectionBlocked = computed(() => this.autoDetectionEnabled && this.draftAutoDetection());

  protected readonly canRemoveSelected = computed(
    () => this.selectedViews().length > this.minSelectedCount,
  );

  protected readonly selectedViews = computed<TemplateView[]>(() => {
    const all = this.templatesView();
    const selected = new Set(this.draftSelectedIds());
    const result: TemplateView[] = [];
    for (const t of all) {
      if (selected.has(t.id)) {
        result.push(t);
      }
    }
    return result;
  });

  protected readonly filteredAvailableViews = computed<TemplateView[]>(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.templatesView();
    const selected = new Set(this.draftSelectedIds());
    const base = all.filter((t) => !selected.has(t.id));
    if (!q) return base;
    return base.filter((t) => {
      const hay = `${t.id ?? ''} ${t.name ?? ''} ${t.description ?? ''} ${t.vendor ?? ''} ${t.type ?? ''}`
        .trim()
        .toLowerCase();
      return hay.includes(q);
    });
  });

  protected onVisibleChange(next: boolean): void {
    this.visible = next;
    this.visibleChange.emit(next);
    // Считаем закрытие без "Сохранить" отменой: сбрасываем черновик.
    this.resetDraftFromInput();
  }

  protected resetDraftFromInput(): void {
    this.query.set('');
    this.draftSelectedIds.set(this.normalizeIds(this.selectedTemplateIds));
    this.draftAutoDetection.set(this.autoDetectionEnabled && this.autoDetection);
  }

  protected onAutoDetectionChange(next: boolean): void {
    this.draftAutoDetection.set(next);
    if (next) {
      this.draftSelectedIds.set([]);
    }
  }

  protected addTemplate(templateId: string): void {
    if (this.manualSelectionBlocked()) return;
    const id = String(templateId ?? '').trim();
    if (!id) return;
    this.draftAutoDetection.set(false);
    this.draftSelectedIds.update((cur) => (cur.includes(id) ? cur : [...cur, id]));
  }

  protected removeTemplate(templateId: string): void {
    if (this.manualSelectionBlocked() || !this.canRemoveSelected()) return;
    const id = String(templateId ?? '').trim();
    if (!id) return;
    this.draftSelectedIds.update((cur) => cur.filter((x) => x !== id));
  }

  protected onSave(): void {
    const autoDetection = this.autoDetectionEnabled && this.draftAutoDetection();
    const next = autoDetection ? [] : this.normalizeIds(this.draftSelectedIds());
    this.selectedTemplateIdsChange.emit(next);
    if (this.autoDetectionEnabled) {
      this.autoDetectionChange.emit(autoDetection);
      this.selectionChange.emit({ templateIds: next, autoDetection });
    }
    if (this.closeOnSave) {
      // Закрываем без дополнительного сброса (родитель применит next через selectedTemplateIdsChange).
      this.visible = false;
      this.visibleChange.emit(false);
    }
  }

  protected onCancel(): void {
    this.onVisibleChange(false);
  }

  private normalizeIds(ids: string[] | null | undefined): string[] {
    return Array.from(
      new Set((ids ?? []).map((v) => String(v ?? '').trim()).filter((v) => v.length > 0))
    );
  }
}
