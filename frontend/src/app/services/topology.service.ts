import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map, type Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import type {
  TopologyCreateRequest,
  TopologyLayoutBatchUpdatePayload,
  TopologyObjectCreatePayload,
  TopologyObjectRecord,
  TopologyObjectUpdatePayload,
  TopologyRecord,
  TopologyUpdateRequest,
} from '../models';

@Injectable({ providedIn: 'root' })
export class TopologyService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  list(): Observable<TopologyRecord[]> {
    return this.http.get<TopologyRecord[]>(`${this.apiBaseUrl}/api/topologies`);
  }

  getById(id: number): Observable<TopologyRecord> {
    return this.http.get<TopologyRecord>(`${this.apiBaseUrl}/api/topologies/${id}`);
  }

  create(body: TopologyCreateRequest): Observable<TopologyRecord> {
    return this.http.post<TopologyRecord>(`${this.apiBaseUrl}/api/topologies`, body);
  }

  update(id: number, body: TopologyUpdateRequest): Observable<TopologyRecord> {
    return this.http.put<TopologyRecord>(`${this.apiBaseUrl}/api/topologies/${id}`, body);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/topologies/${id}`);
  }

  getObject(topologyId: number, objectId: number): Observable<TopologyObjectRecord> {
    return this.http.get<TopologyObjectRecord>(
      `${this.apiBaseUrl}/api/topologies/${topologyId}/objects/${objectId}`,
    );
  }

  listObjects(topologyId: number, layerId?: number | null): Observable<TopologyObjectRecord[]> {
    let params = new HttpParams();
    if (layerId != null) {
      params = params.set('layerId', String(layerId));
    }
    return this.http.get<TopologyObjectRecord[]>(`${this.apiBaseUrl}/api/topologies/${topologyId}/objects`, {
      params,
    });
  }

  createObject(topologyId: number, body: TopologyObjectCreatePayload): Observable<TopologyObjectRecord> {
    return this.http.post<TopologyObjectRecord>(`${this.apiBaseUrl}/api/topologies/${topologyId}/objects`, body);
  }

  updateObject(
    topologyId: number,
    objectId: number,
    body: TopologyObjectUpdatePayload,
  ): Observable<TopologyObjectRecord> {
    return this.http.put<TopologyObjectRecord>(
      `${this.apiBaseUrl}/api/topologies/${topologyId}/objects/${objectId}`,
      body,
    );
  }

  /** Одна транзакция на сервере: координаты узлов и рамки групп (без цепочки отдельных PUT). */
  applyLayoutBatch(topologyId: number, body: TopologyLayoutBatchUpdatePayload): Observable<void> {
    return this.http
      .put(`${this.apiBaseUrl}/api/topologies/${topologyId}/objects/layout-batch`, body, {
        observe: 'response',
        responseType: 'text',
      })
      .pipe(map(() => undefined));
  }

  deleteObject(topologyId: number, objectId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/topologies/${topologyId}/objects/${objectId}`);
  }

  uploadLayerBackground(topologyId: number, objectId: number, file: File): Observable<TopologyObjectRecord> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<TopologyObjectRecord>(
      `${this.apiBaseUrl}/api/topologies/${topologyId}/objects/${objectId}/layer-background`,
      formData,
    );
  }

  deleteLayerBackground(topologyId: number, objectId: number): Observable<void> {
    return this.http.delete<void>(
      `${this.apiBaseUrl}/api/topologies/${topologyId}/objects/${objectId}/layer-background`,
    );
  }

  getLayerBackground(topologyId: number, objectId: number): Observable<Blob> {
    return this.http.get(`${this.apiBaseUrl}/api/topologies/${topologyId}/objects/${objectId}/layer-background`, {
      responseType: 'blob',
    });
  }
}
