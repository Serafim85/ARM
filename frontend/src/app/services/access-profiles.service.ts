import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import type { AccessProfileDetail, AccessProfileSummary, UpsertAccessProfileRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class AccessProfilesService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  listSummaries(): Observable<AccessProfileSummary[]> {
    return this.http.get<AccessProfileSummary[]>(`${this.apiBaseUrl}/api/access-profiles`);
  }

  listDetails(): Observable<AccessProfileDetail[]> {
    return this.http.get<AccessProfileDetail[]>(`${this.apiBaseUrl}/api/admin/access-profiles`);
  }

  getById(id: number): Observable<AccessProfileDetail> {
    return this.http.get<AccessProfileDetail>(`${this.apiBaseUrl}/api/admin/access-profiles/${id}`);
  }

  create(body: UpsertAccessProfileRequest): Observable<AccessProfileDetail> {
    return this.http.post<AccessProfileDetail>(`${this.apiBaseUrl}/api/admin/access-profiles`, body);
  }

  update(id: number, body: UpsertAccessProfileRequest): Observable<AccessProfileDetail> {
    return this.http.put<AccessProfileDetail>(`${this.apiBaseUrl}/api/admin/access-profiles/${id}`, body);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/admin/access-profiles/${id}`);
  }
}
