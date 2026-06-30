import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import type {
  DashboardCreateRequest,
  DashboardRecord,
  DashboardUpdateRequest,
  ServerTimeResponse,
  UserDirectoryEntry,
  WidgetCreatePayload,
  WidgetUpdatePayload,
  DashboardWidget,
} from '../models';

@Injectable({ providedIn: 'root' })
export class DashboardsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  list(): Observable<DashboardRecord[]> {
    return this.http.get<DashboardRecord[]>(`${this.apiBaseUrl}/api/dashboards`);
  }

  getById(id: number): Observable<DashboardRecord> {
    return this.http.get<DashboardRecord>(`${this.apiBaseUrl}/api/dashboards/${id}`);
  }

  getServerTime(): Observable<ServerTimeResponse> {
    return this.http.get<ServerTimeResponse>(`${this.apiBaseUrl}/api/dashboards/server-time`);
  }

  create(body: DashboardCreateRequest): Observable<DashboardRecord> {
    return this.http.post<DashboardRecord>(`${this.apiBaseUrl}/api/dashboards`, body);
  }

  update(id: number, body: DashboardUpdateRequest): Observable<DashboardRecord> {
    return this.http.put<DashboardRecord>(`${this.apiBaseUrl}/api/dashboards/${id}`, body);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/dashboards/${id}`);
  }

  createWidget(dashboardId: number, body: WidgetCreatePayload): Observable<DashboardWidget> {
    return this.http.post<DashboardWidget>(`${this.apiBaseUrl}/api/dashboards/${dashboardId}/widgets`, body);
  }

  updateWidget(dashboardId: number, widgetId: number, body: WidgetUpdatePayload): Observable<DashboardWidget> {
    return this.http.put<DashboardWidget>(`${this.apiBaseUrl}/api/dashboards/${dashboardId}/widgets/${widgetId}`, body);
  }

  deleteWidget(dashboardId: number, widgetId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/dashboards/${dashboardId}/widgets/${widgetId}`);
  }

  listUserDirectory(): Observable<UserDirectoryEntry[]> {
    return this.http.get<UserDirectoryEntry[]>(`${this.apiBaseUrl}/api/users/directory`);
  }
}
