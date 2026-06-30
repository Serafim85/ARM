import { inject, Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';

/** Всплывающие уведомления (PrimeNG Toast) с русскими подписями по умолчанию */
@Injectable({ providedIn: 'root' })
export class NotifierService {
  private readonly messages = inject(MessageService);

  success(detail: string, summary = 'Готово', life = 5500): void {
    this.messages.add({ severity: 'success', summary, detail, life });
  }

  error(detail: string, summary = 'Ошибка', life = 9000): void {
    this.messages.add({ severity: 'error', summary, detail, life });
  }

  warn(detail: string, summary = 'Внимание', life = 6500): void {
    this.messages.add({ severity: 'warn', summary, detail, life });
  }

  info(detail: string, summary = 'Сведения', life = 4500): void {
    this.messages.add({ severity: 'info', summary, detail, life });
  }
}
