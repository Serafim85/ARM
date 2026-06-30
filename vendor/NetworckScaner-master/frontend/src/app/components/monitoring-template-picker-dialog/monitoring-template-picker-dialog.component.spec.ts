import { TestBed, ComponentFixture } from '@angular/core/testing';
import { describe, expect, it, beforeEach } from 'vitest';
import type { MonitoringTemplateSummary } from '../../models';
import { MonitoringTemplatePickerDialogComponent } from './monitoring-template-picker-dialog.component';

type PickerTestVm = MonitoringTemplatePickerDialogComponent & {
  filteredAvailableViews: () => { id: string; name: string }[];
};

describe('MonitoringTemplatePickerDialogComponent', () => {
  let fixture: ComponentFixture<MonitoringTemplatePickerDialogComponent>;
  let component: PickerTestVm;

  const sampleTemplate: MonitoringTemplateSummary = {
    id: 'tpl-1',
    name: 'Test Template',
    description: 'Описание',
    uploadedBy: 'system',
    uploadedByDisplayName: 'System',
    extendsTemplate: null,
    vendor: 'Cisco',
    model: null,
    modelRegex: null,
    firmware: null,
    priority: 50,
    source: 'SYSTEM',
    deletable: false,
    type: 'switch',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MonitoringTemplatePickerDialogComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(MonitoringTemplatePickerDialogComponent);
    component = fixture.componentInstance as PickerTestVm;
    fixture.componentRef.setInput('templates', []);
    fixture.componentRef.setInput('selectedTemplateIds', []);
    fixture.componentRef.setInput('visible', true);
    fixture.detectChanges();
  });

  it('updates available list when templates input changes after open', () => {
    expect(component.filteredAvailableViews().length).toBe(0);

    fixture.componentRef.setInput('templates', [sampleTemplate]);
    fixture.detectChanges();

    expect(component.filteredAvailableViews().length).toBe(1);
    expect(component.filteredAvailableViews()[0]?.id).toBe('tpl-1');
    expect(component.filteredAvailableViews()[0]?.name).toBe('Test Template');
  });
});
