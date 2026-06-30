import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { DeviceOptionSelectComponent } from '../../../components/device-option-select/device-option-select.component';
import type { WidgetFieldRecord, WidgetFieldUpsert } from '../../../models';
import { buildTimeZoneSelectOptions } from './iana-timezone-options';
import type { WidgetFieldsEditorApi } from './widget-editor.types';

@Component({
  selector: 'app-clock-widget-fields',
  standalone: true,
  imports: [FormsModule, SelectModule, CheckboxModule, InputTextModule, DeviceOptionSelectComponent],
  templateUrl: './clock-widget-fields.component.html',
  styleUrl: './widget-fields-editor.component.css',
})
export class ClockWidgetFieldsComponent implements WidgetFieldsEditorApi {
  private static readonly baseTimeZoneOptions = buildTimeZoneSelectOptions();

  protected timeZoneOptions = [...ClockWidgetFieldsComponent.baseTimeZoneOptions];

  protected readonly timeTypeOptions = [
    { label: 'Локальное время', value: 'LOCAL' },
    { label: 'Серверное время', value: 'SERVER' },
    { label: 'Время хоста', value: 'HOST' },
  ];
  protected readonly clockTypeOptions = [
    { label: 'Цифровые', value: 'DIGITAL' },
    { label: 'Аналоговые', value: 'ANALOG' },
  ];
  protected readonly timeFormatOptions = [
    { label: '24-часовой', value: '24H' },
    { label: '12-часовой', value: '12H' },
  ];
  protected readonly timeZoneFormatOptions = [
    { label: 'Короткий', value: 'SHORT' },
    { label: 'Полный', value: 'FULL' },
  ];

  protected timeType = 'LOCAL';
  protected clockType = 'DIGITAL';
  protected showDate = true;
  protected showTime = true;
  protected showTimeZone = false;
  protected itemRef = '';
  protected showSeconds = false;
  protected timeFormat = '24H';
  protected timeZone = '';
  protected timeZoneFormat = 'SHORT';
  protected backgroundColor = '';

  /** Значение для нативного `<input type="color">` (только #RRGGBB). */
  protected get backgroundColorPickerValue(): string {
    const v = this.backgroundColor?.trim() ?? '';
    return /^#[0-9A-Fa-f]{6}$/.test(v) ? v : '#1f2937';
  }

  protected onBackgroundColorPicked(event: Event): void {
    this.backgroundColor = (event.target as HTMLInputElement).value;
  }

  patchFromFields(fields: WidgetFieldRecord[]): void {
    const map = new Map(fields.map((x) => [x.name, x]));
    this.timeType = this.pickString(map, 'time_type', 'LOCAL');
    this.clockType = this.pickString(map, 'clock_type', 'DIGITAL');
    this.showDate = this.pickBool(map, 'show_date', true);
    this.showTime = this.pickBool(map, 'show_time', true);
    this.showTimeZone = this.pickBool(map, 'show_time_zone', false);
    this.itemRef = this.pickString(map, 'item_ref', '');
    this.showSeconds = this.pickBool(map, 'show_seconds', false);
    this.timeFormat = this.pickString(map, 'time_format', '24H');
    this.timeZoneOptions = [...ClockWidgetFieldsComponent.baseTimeZoneOptions];
    this.timeZone = this.pickString(map, 'time_zone', '');
    this.ensureTimeZoneOption(this.timeZone);
    this.timeZoneFormat = this.pickString(map, 'time_zone_format', 'SHORT');
    this.backgroundColor = this.pickString(map, 'background_color', '');
  }

  buildFields(): WidgetFieldUpsert[] {
    const out: WidgetFieldUpsert[] = [
      this.boolField('show_date', this.showDate),
      this.boolField('show_time', this.showTime),
      this.boolField('show_time_zone', this.showTimeZone),
      this.boolField('show_seconds', this.showSeconds),
      { name: 'time_type', valueInt: 0, valueStr: this.timeType },
      { name: 'clock_type', valueInt: 0, valueStr: this.clockType },
      { name: 'time_format', valueInt: 0, valueStr: this.timeFormat },
      { name: 'time_zone_format', valueInt: 0, valueStr: this.timeZoneFormat },
    ];
    if (this.itemRef.trim()) {
      out.push({ name: 'item_ref', valueInt: 0, valueStr: this.itemRef.trim() });
    }
    if (this.timeZone.trim()) {
      out.push({ name: 'time_zone', valueInt: 0, valueStr: this.timeZone.trim() });
    }
    if (this.backgroundColor.trim()) {
      out.push({ name: 'background_color', valueInt: 0, valueStr: this.backgroundColor.trim() });
    }
    return out;
  }

  private boolField(name: string, value: boolean): WidgetFieldUpsert {
    return { name, valueInt: value ? 1 : 0, valueStr: '' };
  }

  private pickBool(map: Map<string, WidgetFieldRecord>, name: string, fallback: boolean): boolean {
    const value = map.get(name);
    if (!value) {
      return fallback;
    }
    return value.valueInt === 1;
  }

  private pickString(map: Map<string, WidgetFieldRecord>, name: string, fallback: string): string {
    const value = map.get(name);
    if (!value || !value.valueStr) {
      return fallback;
    }
    return value.valueStr;
  }

  /** Зона из сохранённых данных может отсутствовать в справочнике — добавляем одну опцию. */
  private ensureTimeZoneOption(tz: string): void {
    const v = tz?.trim();
    if (!v || this.timeZoneOptions.some((o) => o.value === v)) {
      return;
    }
    const empty = this.timeZoneOptions.find((o) => o.value === '');
    const rest = this.timeZoneOptions.filter((o) => o.value !== '');
    const merged = [...rest, { label: v, value: v }].sort((a, b) => a.label.localeCompare(b.label, 'en'));
    this.timeZoneOptions = empty ? [empty, ...merged] : merged;
  }
}
