import { Directive, ElementRef, HostListener, Input, OnDestroy } from '@angular/core';

@Directive({
  selector: '[nsOverflowTooltip]',
  standalone: true,
})
export class OverflowTooltipDirective implements OnDestroy {
  @Input('nsOverflowTooltip') tooltipText: string | null | undefined;

  private tooltipEl: HTMLDivElement | null = null;

  constructor(private readonly elRef: ElementRef<HTMLElement>) {}

  ngOnDestroy(): void {
    this.remove();
  }

  @HostListener('mouseenter')
  protected onEnter(): void {
    const host = this.elRef.nativeElement;
    const overflowed = host.scrollWidth > host.clientWidth || host.scrollHeight > host.clientHeight;
    if (!overflowed) return;

    const text = (this.tooltipText ?? host.textContent ?? '').trim();
    if (!text) return;

    this.render(text);
    this.position();
  }

  @HostListener('mouseleave')
  protected onLeave(): void {
    this.remove();
  }

  @HostListener('window:scroll')
  @HostListener('window:resize')
  protected onViewportChange(): void {
    if (this.tooltipEl) this.position();
  }

  private render(text: string): void {
    if (!this.tooltipEl) {
      this.tooltipEl = document.createElement('div');
      this.tooltipEl.setAttribute('role', 'tooltip');
      this.tooltipEl.style.position = 'fixed';
      this.tooltipEl.style.zIndex = '10000';
      this.tooltipEl.style.maxWidth = 'min(520px, calc(100vw - 24px))';
      this.tooltipEl.style.padding = '10px 12px';
      this.tooltipEl.style.borderRadius = '10px';
      this.tooltipEl.style.background = 'rgba(15, 23, 42, 0.94)';
      this.tooltipEl.style.color = '#f8fafc';
      this.tooltipEl.style.fontSize = '12px';
      this.tooltipEl.style.lineHeight = '1.4';
      this.tooltipEl.style.boxShadow = '0 16px 40px rgba(2, 6, 23, 0.35)';
      this.tooltipEl.style.pointerEvents = 'none';
      document.body.appendChild(this.tooltipEl);
    }

    this.tooltipEl.textContent = text;
  }

  private position(): void {
    if (!this.tooltipEl) return;
    const host = this.elRef.nativeElement;
    const rect = host.getBoundingClientRect();
    const gap = 8;

    // Всегда снизу от элемента (без автоперестановки стороны).
    let top = rect.bottom + gap;

    // Горизонтально: центрируем относительно элемента, но не вылезаем за края экрана.
    // Сначала ставим "примерно", потом корректируем по фактической ширине тултипа.
    this.tooltipEl.style.left = '0px';
    this.tooltipEl.style.top = '-9999px';

    const tooltipRect = this.tooltipEl.getBoundingClientRect();
    const desiredLeft = rect.left + rect.width / 2 - tooltipRect.width / 2;
    const minLeft = 12;
    const maxLeft = window.innerWidth - tooltipRect.width - 12;
    const left = Math.max(minLeft, Math.min(desiredLeft, maxLeft));

    // Разрешаем выход за нижнюю границу viewport (требование "всегда снизу").
    this.tooltipEl.style.left = `${left}px`;
    this.tooltipEl.style.top = `${top}px`;
  }

  private remove(): void {
    if (!this.tooltipEl) return;
    this.tooltipEl.remove();
    this.tooltipEl = null;
  }
}

