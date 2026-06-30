import {
  AfterViewInit,
  Directive,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  inject,
} from '@angular/core';
import { Table } from 'primeng/table';
import type { TableColResizeEvent } from 'primeng/types/table';
import { Subscription } from 'rxjs';
import {
  applyColumnWidthAtIndex,
  applyTableColumnWidths,
  buildColumnBoundsMap,
  clampTableColumnWidth,
  clearTableColumnWidths,
  DEFAULT_TABLE_COLUMN_MAX_WIDTH,
  DEFAULT_TABLE_COLUMN_MIN_WIDTH,
  readTableHeaderWidths,
  type TableColumnWidthBounds,
  type TableColumnWidthDef,
  type TableColumnWidthsMap,
} from '../utils/table-column-widths';
import { TableColumnWidthsService } from '../services/table-column-widths.service';
import type { TableColumnWidthTableKey } from '../utils/table-column-widths';

type ActiveResizeContext = {
  index: number;
  colWidth: number;
  nextWidth: number;
  /** Начальная позиция линии (lastResizerHelperX в PrimeNG). */
  baselineX: number;
  minLineX: number;
  maxLineX: number;
};

@Directive({
  selector: 'p-table[nsTableColumnWidths]',
  standalone: true,
  exportAs: 'nsTableColumnWidths',
})
export class NsTableColumnWidthsDirective implements AfterViewInit, OnChanges, OnDestroy {
  private readonly hostEl = inject(ElementRef).nativeElement as HTMLElement;
  private readonly table = inject(Table);
  private readonly widthsService = inject(TableColumnWidthsService);

  @Input({ required: true }) nsTableColumnWidths!: TableColumnWidthTableKey;
  @Input({ required: true }) nsTableColumnDefs!: TableColumnWidthDef[];
  @Input({ required: true }) nsTableColumnOrder!: string[];
  @Input() nsTableColumnWidthsMap: TableColumnWidthsMap = {};

  private resizeSub: Subscription | null = null;
  private applyRaf: number | null = null;
  private clampRaf: number | null = null;

  private activeResize: ActiveResizeContext | null = null;
  private clickSuppressArmed = false;

  private readonly onHostPointerDownCapture = (event: Event) =>
    this.handleResizerPointerDown(event);
  private readonly onDocumentMouseMove = () => this.clampResizeIndicator();
  private readonly onDocumentMouseUpCapture = () => this.clampResizeIndicator();
  private readonly onDocumentMouseUpBubble = () => this.finishResize();
  private readonly onDocumentTouchEndCapture = () => this.clampResizeIndicator();
  private readonly onDocumentTouchEndBubble = () => this.finishResize();
  private readonly onHostClickCapture = (event: MouseEvent) => {
    if (this.clickSuppressArmed) {
      event.stopPropagation();
      this.clickSuppressArmed = false;
    }
  };

  ngAfterViewInit(): void {
    this.resizeSub = this.table.onColResize.subscribe((event) => this.handleColResize(event));
    this.hostEl.addEventListener('mousedown', this.onHostPointerDownCapture, true);
    this.hostEl.addEventListener('touchstart', this.onHostPointerDownCapture, true);
    this.hostEl.addEventListener('click', this.onHostClickCapture, true);
    this.scheduleApply();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['nsTableColumnWidthsMap'] || changes['nsTableColumnOrder']) {
      this.scheduleApply();
    }
  }

  ngOnDestroy(): void {
    this.resizeSub?.unsubscribe();
    this.hostEl.removeEventListener('mousedown', this.onHostPointerDownCapture, true);
    this.hostEl.removeEventListener('touchstart', this.onHostPointerDownCapture, true);
    this.hostEl.removeEventListener('click', this.onHostClickCapture, true);
    this.unbindResizeListeners();
    if (this.applyRaf != null) {
      cancelAnimationFrame(this.applyRaf);
    }
    this.stopClampLoop();
  }

  resetDomWidths(): void {
    clearTableColumnWidths(this.hostEl);
  }

  private scheduleApply(): void {
    if (this.applyRaf != null) {
      cancelAnimationFrame(this.applyRaf);
    }
    this.applyRaf = requestAnimationFrame(() => {
      this.applyRaf = null;
      this.applyWidths();
    });
  }

  private applyWidths(): void {
    const saved = this.nsTableColumnWidthsMap;
    if (!saved || Object.keys(saved).length === 0) {
      return;
    }
    const order = this.nsTableColumnOrder;
    const boundsById = buildColumnBoundsMap(this.nsTableColumnDefs);
    const measured = readTableHeaderWidths(this.hostEl, order);
    const full: TableColumnWidthsMap = {};
    for (const id of order) {
      const value = saved[id] ?? measured[id];
      if (value != null) {
        full[id] = value;
      }
    }
    if (Object.keys(full).length === 0) {
      return;
    }
    applyTableColumnWidths(this.hostEl, order, full, boundsById);
  }

  /** Как DomHandler.getOffset(el).left в PrimeNG. */
  private containerLeft(): number {
    const rect = this.hostEl.getBoundingClientRect();
    return (
      rect.left +
      (window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0)
    );
  }

  /** Координата синей линии по pageX (как в PrimeNG onColumnResize). */
  private lineXFromPageX(pageX: number): number {
    return pageX - this.containerLeft() + this.hostEl.scrollLeft;
  }

  private pageXFromEvent(event: Event): number | null {
    if (event instanceof MouseEvent) {
      return event.pageX;
    }
    if (event instanceof TouchEvent && event.changedTouches.length > 0) {
      return event.changedTouches[0].pageX;
    }
    return null;
  }

  private boundsForId(id: string | undefined): TableColumnWidthBounds {
    const boundsById = buildColumnBoundsMap(this.nsTableColumnDefs);
    return (
      (id != null ? boundsById[id] : undefined) ?? {
        minWidth: DEFAULT_TABLE_COLUMN_MIN_WIDTH,
        maxWidth: DEFAULT_TABLE_COLUMN_MAX_WIDTH,
      }
    );
  }

  private handleResizerPointerDown(event: Event): void {
    const target = event.target as HTMLElement | null;
    const resizer = target?.closest('.p-datatable-column-resizer');
    if (!resizer) {
      return;
    }
    const th = resizer.closest('th') as HTMLElement | null;
    const nextTh = th?.nextElementSibling as HTMLElement | null;
    if (!th || !nextTh) {
      return;
    }

    const headers = Array.from(
      this.hostEl.querySelectorAll<HTMLElement>('thead tr:first-child th')
    );
    const index = headers.indexOf(th);
    if (index < 0) {
      return;
    }

    const draggedBounds = this.boundsForId(this.nsTableColumnOrder[index]);
    const nextBounds = this.boundsForId(this.nsTableColumnOrder[index + 1]);
    const colWidth = th.offsetWidth;
    const nextWidth = nextTh.offsetWidth;

    const minDelta = Math.max(
      draggedBounds.minWidth - colWidth,
      nextWidth - nextBounds.maxWidth
    );
    const maxDelta = Math.min(
      draggedBounds.maxWidth - colWidth,
      nextWidth - nextBounds.minWidth
    );

    const pageX = this.pageXFromEvent(event);
    if (pageX == null) {
      return;
    }
    const baselineX = this.lineXFromPageX(pageX);
    const lo = Math.min(minDelta, maxDelta);
    const hi = Math.max(minDelta, maxDelta);

    this.activeResize = {
      index,
      colWidth,
      nextWidth,
      baselineX,
      minLineX: baselineX + lo,
      maxLineX: baselineX + hi,
    };

    // После регистрации обработчиков PrimeNG на document.
    queueMicrotask(() => {
      if (!this.activeResize) {
        return;
      }
      document.addEventListener('mousemove', this.onDocumentMouseMove);
      document.addEventListener('mouseup', this.onDocumentMouseUpCapture, true);
      document.addEventListener('mouseup', this.onDocumentMouseUpBubble);
      document.addEventListener('touchend', this.onDocumentTouchEndCapture, true);
      document.addEventListener('touchend', this.onDocumentTouchEndBubble);
      this.startClampLoop();
    });
  }

  private startClampLoop(): void {
    this.stopClampLoop();
    const loop = () => {
      this.clampResizeIndicator();
      if (this.activeResize) {
        this.clampRaf = requestAnimationFrame(loop);
      }
    };
    this.clampRaf = requestAnimationFrame(loop);
  }

  private stopClampLoop(): void {
    if (this.clampRaf != null) {
      cancelAnimationFrame(this.clampRaf);
      this.clampRaf = null;
    }
  }

  private resizeIndicator(): HTMLElement | null {
    return this.hostEl.querySelector<HTMLElement>('.p-datatable-column-resize-indicator');
  }

  /** Стопор: синяя линия не выходит за min/max перетаскиваемой и соседней колонки. */
  private clampResizeIndicator(): void {
    const ctx = this.activeResize;
    if (!ctx) {
      return;
    }
    const indicator = this.resizeIndicator();
    if (!indicator) {
      return;
    }
    const current = indicator.offsetLeft;
    const clamped = Math.min(ctx.maxLineX, Math.max(ctx.minLineX, current));
    if (clamped !== current) {
      indicator.style.left = `${clamped}px`;
    }
  }

  private unbindResizeListeners(): void {
    document.removeEventListener('mousemove', this.onDocumentMouseMove);
    document.removeEventListener('mouseup', this.onDocumentMouseUpCapture, true);
    document.removeEventListener('mouseup', this.onDocumentMouseUpBubble);
    document.removeEventListener('touchend', this.onDocumentTouchEndCapture, true);
    document.removeEventListener('touchend', this.onDocumentTouchEndBubble);
  }

  private finishResize(): void {
    if (!this.activeResize) {
      return;
    }
    const ctx = this.activeResize;
    this.stopClampLoop();
    this.unbindResizeListeners();
    this.enforceClampedWidths(ctx);
    this.activeResize = null;
    this.clickSuppressArmed = true;
    setTimeout(() => {
      this.clickSuppressArmed = false;
    }, 150);
  }

  private enforceClampedWidths(ctx: ActiveResizeContext): void {
    const indicator = this.resizeIndicator();
    if (!indicator) {
      return;
    }

    const delta = indicator.offsetLeft - ctx.baselineX;
    const boundsById = buildColumnBoundsMap(this.nsTableColumnDefs);
    const draggedId = this.nsTableColumnOrder[ctx.index];
    const nextId = this.nsTableColumnOrder[ctx.index + 1];
    if (!draggedId || !nextId) {
      return;
    }

    const newDragged = clampTableColumnWidth(
      ctx.colWidth + delta,
      boundsById[draggedId]
    );
    const newNext = clampTableColumnWidth(ctx.nextWidth - delta, boundsById[nextId]);

    applyColumnWidthAtIndex(this.hostEl, ctx.index, newDragged);
    applyColumnWidthAtIndex(this.hostEl, ctx.index + 1, newNext);

    const widths = readTableHeaderWidths(this.hostEl, this.nsTableColumnOrder);
    const normalized: TableColumnWidthsMap = {};
    for (const [id, width] of Object.entries(widths)) {
      if (boundsById[id]) {
        normalized[id] = width;
      }
    }
    if (Object.keys(normalized).length > 0) {
      this.widthsService.queueSave(this.nsTableColumnWidths, normalized);
    }
  }

  private handleColResize(_event: TableColResizeEvent): void {
    const boundsById = buildColumnBoundsMap(this.nsTableColumnDefs);
    const next = readTableHeaderWidths(this.hostEl, this.nsTableColumnOrder);
    const normalized: TableColumnWidthsMap = {};
    for (const [id, width] of Object.entries(next)) {
      if (!boundsById[id]) {
        continue;
      }
      normalized[id] = width;
    }
    if (Object.keys(normalized).length > 0) {
      this.widthsService.queueSave(this.nsTableColumnWidths, normalized);
    }
  }
}
