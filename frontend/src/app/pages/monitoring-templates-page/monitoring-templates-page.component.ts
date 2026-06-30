import { NgStyle } from '@angular/common';
import { Component, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { Menu, MenuModule } from 'primeng/menu';
import { TableModule } from 'primeng/table';
import { OverflowTooltipDirective } from '../../directives/overflow-tooltip.directive';
import { NsTableColumnWidthsDirective } from '../../directives/ns-table-column-widths.directive';
import { TableColumnWidthsService } from '../../services/table-column-widths.service';
import { buildColumnBoundsMap, columnBoundsStyle } from '../../utils/table-column-widths';
import { MonitoringTemplateSummary } from '../../models';
import { MonitoringService } from '../../services/monitoring.service';
import { NotifierService } from '../../notifier.service';
import { Checkbox } from 'primeng/checkbox';
import {
  MONITORING_TEMPLATES_COLUMN_ORDER,
  MONITORING_TEMPLATES_TABLE_COLUMNS,
} from './monitoring-templates-table-columns';

@Component({
  selector: 'app-monitoring-templates-page',
  standalone: true,
  imports: [
    FormsModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    InputNumberModule,
    DialogModule,
    ConfirmDialogModule,
    MenuModule,
    OverflowTooltipDirective,
    Checkbox,
    NgStyle,
    NsTableColumnWidthsDirective,
  ],
  providers: [ConfirmationService],
  templateUrl: './monitoring-templates-page.component.html',
  styleUrl: './monitoring-templates-page.component.css',
})
export class MonitoringTemplatesPageComponent implements OnInit {
  @ViewChild('templatesRowMenu') private templatesRowMenu?: Menu;
  @ViewChild('templatesTableWidths') private templatesTableWidths?: NsTableColumnWidthsDirective;

  protected readonly mon = inject(MonitoringService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifierService);
  private readonly tableColumnWidths = inject(TableColumnWidthsService);

  protected readonly templatesTableColumns = MONITORING_TEMPLATES_TABLE_COLUMNS;
  protected readonly templatesTableColumnOrder = MONITORING_TEMPLATES_COLUMN_ORDER;
  protected readonly templatesTableColumnBounds = buildColumnBoundsMap(MONITORING_TEMPLATES_TABLE_COLUMNS);
  protected readonly templatesTableColumnWidthsMap = signal<Record<string, number>>({});
  protected readonly columnsDialogOpen = signal(false);
  protected readonly columnBoundsStyle = columnBoundsStyle;

  protected readonly previewOpen = signal(false);
  protected readonly templatesRowMenuItems = signal<MenuItem[]>([]);
  private readonly pendingArchiveFile = signal<File | null>(null);
  protected readonly importVendor = signal('');
  protected readonly importModel = signal('');
  protected readonly importFirmware = signal('');
  protected readonly importFormValid = computed(() => {
    const vendor = this.importVendor().trim();
    return vendor.length > 0;
  });
  protected readonly selectedTemplateIds = signal<string[]>([]);
  protected readonly editDialogOpen = signal(false);
  protected readonly editingTemplate = signal<MonitoringTemplateSummary | null>(null);
  protected readonly editVendor = signal('');
  protected readonly editModel = signal('');
  protected readonly editFirmware = signal('');
  protected readonly editPriority = signal(0);
  protected readonly editIsUploaded = computed(
    () => this.editingTemplate()?.source === 'UPLOADED',
  );
  protected readonly editPriorityValid = computed(() => {
    const priority = this.editPriority();
    return Number.isInteger(priority) && priority >= 0 && priority <= 100;
  });
  protected readonly editFormValid = computed(() => {
    if (this.editingTemplate()?.source === 'UPLOADED' && !this.editVendor().trim()) {
      return false;
    }
    return this.editPriorityValid();
  });

  ngOnInit(): void {
    this.mon.loadMonitoringTemplates();
    this.clearSelection();
    this.loadTableColumnWidths();
  }

  private loadTableColumnWidths(): void {
    this.tableColumnWidths.load().subscribe({
      next: () => {
        this.templatesTableColumnWidthsMap.set(
          this.tableColumnWidths.widthsFor('templates', this.templatesTableColumnBounds)
        );
      },
      error: () => {
        this.templatesTableColumnWidthsMap.set({});
      },
    });
  }

  protected openColumnsDialog(): void {
    this.columnsDialogOpen.set(true);
  }

  protected closeColumnsDialog(): void {
    this.columnsDialogOpen.set(false);
  }

  protected resetTemplatesTableColumnWidths(): void {
    this.tableColumnWidths.reset('templates').subscribe({
      next: () => {
        this.templatesTableColumnWidthsMap.set({});
        this.templatesTableWidths?.resetDomWidths();
        this.notify.success('Ширина колонок сброшена.', 'Шаблоны');
      },
      error: () => {
        this.notify.error('Не удалось сбросить ширину колонок.', 'Шаблоны');
      },
    });
  }

  protected openArchivePicker(input: HTMLInputElement): void {
    input.click();
  }

  protected onArchiveSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }
    this.pendingArchiveFile.set(file);
    this.importVendor.set('');
    this.importModel.set('');
    this.importFirmware.set('');
    this.mon.previewMonitoringTemplateArchive(file, () => {
      this.previewOpen.set(true);
    });
    input.value = '';
  }

  protected confirmDelete(template: MonitoringTemplateSummary): void {
    if (!this.mon.isTemplateDeletable(template)) {
      return;
    }
    this.confirmation.confirm({
      header: 'Удалить шаблон?',
      message: `Удалить шаблон «${template.name}» (${template.id})? Действие необратимо.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.mon.deleteMonitoringTemplate(template.id),
    });
  }

  protected sourceLabel(template: MonitoringTemplateSummary): string {
    return template.source === 'UPLOADED' ? 'Загруженный' : 'Системный';
  }

  protected showTemplateDetails(template: MonitoringTemplateSummary): void {
    void this.router.navigate(['/monitoring-templates', template.id]);
  }

  protected openTemplatesRowMenu(event: Event, template: MonitoringTemplateSummary): void {
    event.stopPropagation();
    this.templatesRowMenuItems.set([
      {
        label: 'Подробнее',
        icon: 'pi pi-eye',
        disabled: this.mon.selectedTemplateDetailsLoading(),
        command: () => this.showTemplateDetails(template),
      },
      {
        label: 'Редактировать',
        icon: 'pi pi-pencil',
        disabled: this.mon.templatesMutationLoading(),
        command: () => this.openEditDialog(template),
      },
      {
        label: 'Удалить',
        icon: 'pi pi-trash',
        disabled: !this.mon.isTemplateDeletable(template) || this.mon.templatesMutationLoading(),
        command: () => this.confirmDelete(template),
      },
    ]);
    this.templatesRowMenu?.toggle(event);
  }

  protected openEditDialog(template: MonitoringTemplateSummary): void {
    this.editingTemplate.set(template);
    this.editVendor.set(template.vendor ?? '');
    this.editModel.set(template.model ?? '');
    this.editFirmware.set(template.firmware ?? '');
    this.editPriority.set(template.priority ?? 0);
    this.editDialogOpen.set(true);
  }

  protected onEditDialogVisibleChange(visible: boolean): void {
    if (!visible) {
      this.closeEditDialog();
    } else {
      this.editDialogOpen.set(true);
    }
  }

  protected closeEditDialog(): void {
    this.editDialogOpen.set(false);
    this.editingTemplate.set(null);
    this.editVendor.set('');
    this.editModel.set('');
    this.editFirmware.set('');
    this.editPriority.set(0);
  }

  protected saveEdit(): void {
    const template = this.editingTemplate();
    if (!template || !this.editFormValid()) {
      return;
    }
    const priority = this.editPriority();
    this.mon.updateMonitoringTemplate(
      template.id,
      {
        vendor: this.editIsUploaded() ? this.editVendor().trim() : undefined,
        model: this.editIsUploaded() ? this.editModel().trim() : undefined,
        firmware: this.editIsUploaded() ? this.editFirmware().trim() : undefined,
        priority,
      },
      () => this.closeEditDialog(),
    );
  }

  protected closePreview(): void {
    this.previewOpen.set(false);
    this.mon.clearTemplateImportPreview();
    this.pendingArchiveFile.set(null);
    this.importVendor.set('');
    this.importModel.set('');
    this.importFirmware.set('');
  }

  protected uploadFromPreview(): void {
    const file = this.pendingArchiveFile();
    if (!file) {
      return;
    }
    if (!this.importFormValid()) {
      return;
    }
    this.mon.uploadMonitoringTemplateArchive(file, {
      vendor: this.importVendor().trim(),
      model: this.importModel().trim(),
      firmware: this.importFirmware().trim(),
    });
    this.closePreview();
  }

  protected areAllUploadedSelected(): boolean {
    const uploaded = this.mon.filteredMonitoringTemplates().filter((t) => t.source === 'UPLOADED');
    if (uploaded.length === 0) return false;
    return uploaded.every((t) => this.selectedTemplateIds().includes(t.id));
  }

  protected toggleAllUploadedSelection(select: boolean): void {
    if (select) {
      this.selectAllUploaded();
    } else {
      this.clearSelection();
    }
  }

  protected isTemplateSelected(template: MonitoringTemplateSummary): boolean {
    return this.selectedTemplateIds().includes(template.id);
  }

  protected toggleTemplateSelection(template: MonitoringTemplateSummary, select: boolean): void {
    if (select && !this.isTemplateSelected(template)) {
      this.selectedTemplateIds.update((ids) => [...ids, template.id]);
    } else if (!select) {
      this.selectedTemplateIds.update((ids) => ids.filter((id) => id !== template.id));
    }
  }

  /** Выделить все загруженные шаблоны в отфильтрованном списке */
  protected selectAllUploaded(): void {
    const uploadedIds = this.mon
      .filteredMonitoringTemplates()
      .filter((t) => t.source === 'UPLOADED')
      .map((t) => t.id);
    this.selectedTemplateIds.set(uploadedIds);
  }

  protected clearSelection(): void {
    this.selectedTemplateIds.set([]);
  }

  protected deleteSelectedTemplates(): void {
    const ids = this.selectedTemplateIds();
    if (ids.length === 0) return;

    const selectedTemplates = this.mon
      .filteredMonitoringTemplates()
      .filter((t) => ids.includes(t.id) && t.source === 'UPLOADED'); // только загруженные

    if (selectedTemplates.length === 0) {
      this.clearSelection();
      return;
    }

    const names = selectedTemplates.map((t) => `«${t.name}»`).join(', ');
    this.confirmation.confirm({
      header: 'Удалить выбранные шаблоны?',
      message: `Будут удалены шаблоны: ${names}. Действие необратимо.`,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Удалить',
      rejectLabel: 'Отмена',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.executeDeleteSelected(selectedTemplates.map((t) => t.id)),
    });
  }

  private executeDeleteSelected(ids: string[]): void {
    this.mon.deleteManyTemplates(ids).subscribe({
      next: () => {
        this.mon.loadMonitoringTemplates(); // обновляем список
        this.clearSelection();
        this.notify.success(`Удалено шаблонов: ${ids.length}.`, 'Мониторинг');
      },
      error: (err) => {
        // Обработка ошибки
      },
    });
  }
}
