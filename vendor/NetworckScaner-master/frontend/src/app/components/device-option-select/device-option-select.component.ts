import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { TooltipModule } from 'primeng/tooltip';
import { TooltipOnOverflowDirective } from '../../directives/tooltip-on-overflow.directive';

/** Элемент списка: устройство (значение в поле `id`). */
export type DeviceOptionSelectItem = {
  id: number;
  label: string;
};

/** Элемент списка: значение (например пресет подсети) в поле `value`. */
export type StringValueSelectItem = {
  value: string | number;
  label: string;
};

export type DeviceOptionSelectSource = Array<DeviceOptionSelectItem | StringValueSelectItem>;

@Component({
  selector: 'app-device-option-select',
  standalone: true,
  imports: [FormsModule, SelectModule, TooltipModule, TooltipOnOverflowDirective],
  templateUrl: './device-option-select.component.html',
  styleUrl: './device-option-select.component.css',
})
export class DeviceOptionSelectComponent {
  readonly options = input.required<DeviceOptionSelectSource>();
  /** Имя поля объекта опции, которое уходит в модель (`id` для устройств, `value` для строк). */
  readonly optionValueKey = input<string>('id');
  readonly value = input<string | number | null>(null);
  readonly valueChange = output<string | number | null>();

  readonly placeholder = input('Выберите устройство');
  readonly emptyFilterMessage = input('Ничего не найдено');
  readonly emptyMessage = input('Ничего не найдено');
  readonly filterPlaceholder = input('Поиск');
  readonly disabled = input(false);
  readonly filter = input(true);
  readonly editable = input(false);
  readonly showClear = input(false);
  /** Классы панели выпадающего списка (overlay); можно задать несколько через пробел. */
  readonly panelStyleClass = input('device-option-select-panel');
  /** Дополнительные CSS-классы на корне p-select (например `w-full`). */
  readonly styleClass = input('w-full');
}
