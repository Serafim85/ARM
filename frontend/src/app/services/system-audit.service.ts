import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import type { SystemAuditEventPage } from '../models';

export type SystemAuditListParams = {
  page: number;
  size: number;
  from?: string;
  to?: string;
  actor?: string;
  category?: string;
  action?: string;
};

@Injectable({ providedIn: 'root' })
export class SystemAuditService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  listEvents(params: SystemAuditListParams): Observable<SystemAuditEventPage> {
    let httpParams = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.from) httpParams = httpParams.set('from', params.from);
    if (params.to) httpParams = httpParams.set('to', params.to);
    if (params.actor?.trim()) httpParams = httpParams.set('actor', params.actor.trim());
    if (params.category) httpParams = httpParams.set('category', params.category);
    if (params.action) httpParams = httpParams.set('action', params.action);
    return this.http.get<SystemAuditEventPage>(`${this.apiBaseUrl}/api/admin/audit/events`, {
      params: httpParams,
    });
  }
}
