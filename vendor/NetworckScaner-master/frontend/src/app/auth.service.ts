import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api-config';
import { AUTH_STORAGE_KEY } from './auth.constants';
import { NotifierService } from './notifier.service';
import { DashboardsService } from './services/dashboards.service';
import type { MonitoringEventsColumnPreferenceItem } from './pages/events-page/monitoring-events-table/monitoring-events-table-columns';
import type { MonitoringDevicesColumnPreferenceItem } from './pages/monitoring-page/monitoring-devices-table-columns';
import type { ChartUiPreferences } from './utils/chart-legend-placement';

export type AppRole = 'ADMIN' | 'OPERATOR' | 'VIEWER';

export type AuthLoginResponse = {
  message: string;
  accessToken: string;
  email: string;
  displayName: string;
  roles: AppRole[];
  /** С версии API; для старых сессий в localStorage может отсутствовать — перелогиньтесь. */
  userId?: number | null;
  defaultDashboardId?: number | null;
  defaultTopologyId?: number | null;
};

export type AuthSession = AuthLoginResponse;
export type DefaultDashboardPreference = { defaultDashboardId: number | null };
export type DefaultTopologyPreference = { defaultTopologyId: number | null };
export type MonitoringEventsColumnsPreference = {
  columns: MonitoringEventsColumnPreferenceItem[] | null;
};
export type MonitoringDevicesColumnsPreference = {
  columns: MonitoringDevicesColumnPreferenceItem[] | null;
};
export type ChartUiPreferencesResponse = ChartUiPreferences;
export type TableColumnWidthsPreference = {
  widths: Partial<Record<'devices' | 'events' | 'templates' | 'audit' | 'users', Record<string, number>>>;
};
export type LoginMode = 'LOCAL' | 'LDAP';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifierService);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly dashboardsApi = inject(DashboardsService);

  readonly email = signal('admin@example.com');
  readonly password = signal('password');
  readonly loginMode = signal<LoginMode>('LOCAL');
  readonly loading = signal(false);
  readonly isAuthenticated = signal(false);
  readonly authSession = signal<AuthSession | null>(null);

  constructor() {
    this.restoreSession();
  }

  login(): void {
    const email = this.email().trim();
    const password = this.password().trim();
    const authMode = this.loginMode();

    if (!email || !password) {
      this.notify.warn('Введите логин и пароль.', 'Вход');
      return;
    }

    this.loading.set(true);

    this.http.post<AuthLoginResponse>(`${this.apiBaseUrl}/api/auth/login`, { email, password, authMode }).subscribe({
      next: (response) => {
        this.persistSession(response);
        this.authSession.set(response);
        this.isAuthenticated.set(true);
        this.loading.set(false);
        this.notify.success(response.message || 'Вход выполнен.', 'Вход');
        const defaultDashboardId = response.defaultDashboardId;
        if (defaultDashboardId == null) {
          void this.router.navigate(['/scan']);
          return;
        }
        this.dashboardsApi.getById(defaultDashboardId).subscribe({
          next: () => void this.router.navigate(['/dashboards', defaultDashboardId]),
          error: () => void this.router.navigate(['/monitoring']),
        });
      },
      error: (error) => {
        this.notify.error(error?.error?.message ?? 'Неверный email или пароль.', 'Вход');
        this.loading.set(false);
      }
    });
  }

  logout(): void {
    if (this.authSession()?.accessToken) {
      this.http.post<void>(`${this.apiBaseUrl}/api/auth/logout`, {}).subscribe({
        next: () => this.clearLocalSession(),
        error: () => this.clearLocalSession(),
      });
      return;
    }
    this.clearLocalSession();
  }

  private clearLocalSession(): void {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    this.authSession.set(null);
    this.isAuthenticated.set(false);
    void this.router.navigate(['/login']);
  }

  private restoreSession(): void {
    const rawSession = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!rawSession) {
      return;
    }

    try {
      const session = JSON.parse(rawSession) as AuthSession;
      const hasValidUserId = typeof session.userId === 'number' && Number.isFinite(session.userId);
      if (!session.accessToken || !hasValidUserId || this.isTokenExpired(session.accessToken)) {
        localStorage.removeItem(AUTH_STORAGE_KEY);
        return;
      }
      this.authSession.set(session);
      this.isAuthenticated.set(true);
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }

  private persistSession(session: AuthSession): void {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
  }

  /** Синхронизация сессии после смены профиля текущего пользователя. */
  updateSession(session: AuthSession): void {
    this.authSession.set(session);
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
  }

  /** Принудительно очищает локальную сессию и отправляет пользователя на страницу входа. */
  handleUnauthorized(): void {
    this.clearLocalSession();
  }

  updateDefaultDashboardPreference(defaultDashboardId: number | null): Observable<DefaultDashboardPreference> {
    return this.http.patch<DefaultDashboardPreference>(`${this.apiBaseUrl}/api/me/default-dashboard`, {
      defaultDashboardId,
    });
  }

  updateDefaultTopologyPreference(defaultTopologyId: number | null): Observable<DefaultTopologyPreference> {
    return this.http.patch<DefaultTopologyPreference>(`${this.apiBaseUrl}/api/me/default-topology`, {
      defaultTopologyId,
    });
  }

  getMonitoringEventsColumnsPreference(): Observable<MonitoringEventsColumnsPreference> {
    return this.http.get<MonitoringEventsColumnsPreference>(
      `${this.apiBaseUrl}/api/me/monitoring-events-columns`
    );
  }

  updateMonitoringEventsColumnsPreference(
    columns: MonitoringEventsColumnPreferenceItem[]
  ): Observable<MonitoringEventsColumnsPreference> {
    return this.http.patch<MonitoringEventsColumnsPreference>(
      `${this.apiBaseUrl}/api/me/monitoring-events-columns`,
      { columns }
    );
  }

  getMonitoringDevicesColumnsPreference(): Observable<MonitoringDevicesColumnsPreference> {
    return this.http.get<MonitoringDevicesColumnsPreference>(
      `${this.apiBaseUrl}/api/me/monitoring-devices-columns`
    );
  }

  updateMonitoringDevicesColumnsPreference(
    columns: MonitoringDevicesColumnPreferenceItem[]
  ): Observable<MonitoringDevicesColumnsPreference> {
    return this.http.patch<MonitoringDevicesColumnsPreference>(
      `${this.apiBaseUrl}/api/me/monitoring-devices-columns`,
      { columns }
    );
  }

  getChartUiPreferences(): Observable<ChartUiPreferencesResponse> {
    return this.http.get<ChartUiPreferencesResponse>(`${this.apiBaseUrl}/api/me/chart-ui-preferences`);
  }

  updateChartUiPreferences(prefs: ChartUiPreferences): Observable<ChartUiPreferencesResponse> {
    return this.http.patch<ChartUiPreferencesResponse>(`${this.apiBaseUrl}/api/me/chart-ui-preferences`, prefs);
  }

  getTableColumnWidths(): Observable<TableColumnWidthsPreference> {
    return this.http.get<TableColumnWidthsPreference>(`${this.apiBaseUrl}/api/me/table-column-widths`);
  }

  updateTableColumnWidths(
    tableKey: 'devices' | 'events' | 'templates' | 'audit' | 'users',
    widths: Record<string, number>
  ): Observable<TableColumnWidthsPreference> {
    return this.http.patch<TableColumnWidthsPreference>(`${this.apiBaseUrl}/api/me/table-column-widths`, {
      tableKey,
      widths,
    });
  }

  private isTokenExpired(token: string): boolean {
    try {
      const parts = token.split('.');
      if (parts.length < 2) {
        return true;
      }
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
      const payload = JSON.parse(atob(padded)) as { exp?: number };
      if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) {
        return true;
      }
      const nowSeconds = Math.floor(Date.now() / 1000);
      return payload.exp <= nowSeconds;
    } catch {
      return true;
    }
  }
}
