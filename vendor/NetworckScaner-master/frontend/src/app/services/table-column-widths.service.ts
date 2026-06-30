import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject, debounceTime, map, switchMap, tap } from 'rxjs';
import { AuthService } from '../auth.service';
import {
  TABLE_COLUMN_WIDTH_KEYS,
  type TableColumnWidthTableKey,
  type TableColumnWidthsMap,
  normalizeStoredColumnWidths,
  type TableColumnWidthBounds,
} from '../utils/table-column-widths';

@Injectable({ providedIn: 'root' })
export class TableColumnWidthsService {
  private readonly auth = inject(AuthService);

  private readonly allWidths = signal<Partial<Record<TableColumnWidthTableKey, TableColumnWidthsMap>>>(
    {}
  );

  private readonly saveRequests$ = new Subject<{
    tableKey: TableColumnWidthTableKey;
    widths: TableColumnWidthsMap;
  }>();

  constructor() {
    this.saveRequests$
      .pipe(
        debounceTime(400),
        switchMap(({ tableKey, widths }) =>
          this.auth.updateTableColumnWidths(tableKey, widths).pipe(
            tap((response) => {
              this.allWidths.set(response.widths ?? {});
            })
          )
        )
      )
      .subscribe();
  }

  load(): Observable<Partial<Record<TableColumnWidthTableKey, TableColumnWidthsMap>>> {
    return this.auth.getTableColumnWidths().pipe(
      tap((response) => {
        this.allWidths.set(response.widths ?? {});
      }),
      map((response) => response.widths ?? {})
    );
  }

  widthsFor(
    tableKey: TableColumnWidthTableKey,
    boundsById: Record<string, TableColumnWidthBounds>
  ): TableColumnWidthsMap {
    const raw = this.allWidths()[tableKey] ?? {};
    return normalizeStoredColumnWidths(raw, boundsById);
  }

  queueSave(tableKey: TableColumnWidthTableKey, widths: TableColumnWidthsMap): void {
    this.allWidths.update((current) => ({
      ...current,
      [tableKey]: widths,
    }));
    this.saveRequests$.next({ tableKey, widths });
  }

  reset(
    tableKey: TableColumnWidthTableKey
  ): Observable<Partial<Record<TableColumnWidthTableKey, TableColumnWidthsMap>>> {
    return this.auth.updateTableColumnWidths(tableKey, {}).pipe(
      tap((response) => {
        this.allWidths.set(response.widths ?? {});
      }),
      map((response) => response.widths ?? {})
    );
  }

  readonly keys = TABLE_COLUMN_WIDTH_KEYS;
}
