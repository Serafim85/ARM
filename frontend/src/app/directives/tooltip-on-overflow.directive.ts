import { AfterViewInit, Directive, ElementRef, Host, HostListener, OnDestroy, Optional } from '@angular/core';
import { Tooltip } from 'primeng/tooltip';

/**
 * Включает PrimeNG tooltip только если контент реально обрезан (overflow).
 * Использование: повесьте `pTooltip="..."` и добавьте атрибут `nsTooltipOnOverflow`.
 */
@Directive({
  selector: '[nsTooltipOnOverflow]',
  standalone: true,
})
export class TooltipOnOverflowDirective implements AfterViewInit, OnDestroy {
  private resizeObserver: ResizeObserver | null = null;

  constructor(
    private readonly elRef: ElementRef<HTMLElement>,
    @Optional() @Host() private readonly tooltip: Tooltip | null,
  ) {}

  ngAfterViewInit(): void {
    // Если pTooltip не повешен — нечего управлять.
    if (!this.tooltip) return;

    // По умолчанию выключаем, чтобы не было пустых подсказок.
    this.tooltip.disabled = true;

    // При изменении размеров пересчитываем (удобно для responsive и смены шрифта).
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.updateTooltipState());
      this.resizeObserver.observe(this.elRef.nativeElement);
    }
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
  }

  @HostListener('mouseenter')
  protected onMouseEnter(): void {
    this.updateTooltipState();
  }

  private updateTooltipState(): void {
    if (!this.tooltip) return;
    const el = this.elRef.nativeElement;
    const overflowed = el.scrollWidth > el.clientWidth || el.scrollHeight > el.clientHeight;
    this.tooltip.disabled = !overflowed;
  }
}

