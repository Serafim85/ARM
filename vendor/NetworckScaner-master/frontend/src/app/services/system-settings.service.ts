import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api-config';
import type {
  CreateUserFromDirectoryRequest,
  DirectoryGroup,
  DirectoryRoleMapping,
  DirectorySettings,
  DirectoryUserCandidate,
  DirectoryUserSearchRequest,
  NotificationSubscription,
  SmtpSettings,
  TestSmtpRequest,
  UpdateSmtpSettingsRequest,
  UpdateDirectoryRoleMappingsRequest,
  UpdateDirectorySettingsRequest
} from '../models';

@Injectable({ providedIn: 'root' })
export class SystemSettingsService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = inject(API_BASE_URL);

  getAppConfig(): Observable<{
    debugMode: boolean;
    version: string | null;
    buildTime: string | null;
  }> {
    return this.http.get<{
      debugMode: boolean;
      version: string | null;
      buildTime: string | null;
    }>(`${this.apiBaseUrl}/api/public/app-config`);
  }

  getDirectorySettings(): Observable<DirectorySettings> {
    return this.http.get<DirectorySettings>(`${this.apiBaseUrl}/api/admin/system/directory-settings`);
  }

  updateDirectorySettings(payload: UpdateDirectorySettingsRequest): Observable<DirectorySettings> {
    return this.http.put<DirectorySettings>(`${this.apiBaseUrl}/api/admin/system/directory-settings`, payload);
  }

  discoverDirectoryGroups(): Observable<DirectoryGroup[]> {
    return this.http.get<DirectoryGroup[]>(`${this.apiBaseUrl}/api/admin/system/directory-role-mappings/discover-groups`);
  }

  listDirectoryRoleMappings(): Observable<DirectoryRoleMapping[]> {
    return this.http.get<DirectoryRoleMapping[]>(`${this.apiBaseUrl}/api/admin/system/directory-role-mappings`);
  }

  updateDirectoryRoleMappings(payload: UpdateDirectoryRoleMappingsRequest): Observable<DirectoryRoleMapping[]> {
    return this.http.put<DirectoryRoleMapping[]>(`${this.apiBaseUrl}/api/admin/system/directory-role-mappings`, payload);
  }

  searchDirectoryUsers(payload: DirectoryUserSearchRequest): Observable<DirectoryUserCandidate[]> {
    return this.http.post<DirectoryUserCandidate[]>(`${this.apiBaseUrl}/api/admin/system/directory-users/search`, payload);
  }

  createUserFromDirectory(payload: CreateUserFromDirectoryRequest): Observable<{
    id: number;
    email: string;
    displayName: string;
    enabled: boolean;
    createdAt: string;
    roles: string[];
  }> {
    return this.http.post<{
      id: number;
      email: string;
      displayName: string;
      enabled: boolean;
      createdAt: string;
      roles: string[];
    }>(`${this.apiBaseUrl}/api/admin/system/directory-users/create`, payload);
  }

  getSmtpSettings(): Observable<SmtpSettings> {
    return this.http.get<SmtpSettings>(`${this.apiBaseUrl}/api/admin/system/smtp-settings`);
  }

  updateSmtpSettings(payload: UpdateSmtpSettingsRequest): Observable<SmtpSettings> {
    return this.http.put<SmtpSettings>(`${this.apiBaseUrl}/api/admin/system/smtp-settings`, payload);
  }

  listNotificationSubscriptions(): Observable<NotificationSubscription[]> {
    return this.http.get<NotificationSubscription[]>(`${this.apiBaseUrl}/api/admin/system/notification-subscriptions`);
  }

  upsertNotificationSubscription(payload: NotificationSubscription): Observable<NotificationSubscription> {
    return this.http.post<NotificationSubscription>(`${this.apiBaseUrl}/api/admin/system/notification-subscriptions`, payload);
  }

  deleteNotificationSubscription(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiBaseUrl}/api/admin/system/notification-subscriptions/${id}`);
  }

  sendTestNotificationEvent(payload: {
    notificationKind: 'ADMIN' | 'OPERATOR';
    eventCode: string;
    deviceIp?: string | null;
    deviceName?: string | null;
    severity?: string | null;
    metricName?: string | null;
    deviceTags?: string | null;
    details?: string | null;
  }): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/api/admin/system/notification-subscriptions/test-event`, payload);
  }

  sendTestSmtpEmail(payload: TestSmtpRequest): Observable<void> {
    return this.http.put<void>(`${this.apiBaseUrl}/api/admin/system/smtp-settings/test`, payload);
  }
}
