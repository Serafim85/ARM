import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { API_BASE_URL } from './api-config';
import { AUTH_STORAGE_KEY } from './auth.constants';
import { AuthService } from './auth.service';
import { NotifierService } from './notifier.service';
import { DashboardsService } from './services/dashboards.service';

const createJwt = (expSeconds: number): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ exp: expSeconds }));
  return `${header}.${payload}.signature`;
};

describe('AuthService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://localhost:8081' },
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
        {
          provide: NotifierService,
          useValue: { warn: vi.fn(), success: vi.fn(), error: vi.fn(), info: vi.fn() },
        },
        { provide: DashboardsService, useValue: { getById: () => of({}) } },
      ],
    });
  });

  it('updateSession persists JSON to localStorage', () => {
    const service = TestBed.inject(AuthService);
    service.updateSession({
      message: 'ok',
      accessToken: 'test-token',
      email: 'a@example.com',
      displayName: 'User',
      roles: ['VIEWER'],
      userId: 1,
    });
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    expect(raw).toBeTruthy();
    expect(JSON.parse(raw!).accessToken).toBe('test-token');
  });

  it('restores valid non-expired session from localStorage', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        message: 'ok',
        accessToken: createJwt(Math.floor(Date.now() / 1000) + 3600),
        email: 'a@example.com',
        displayName: 'User',
        roles: ['VIEWER'],
        userId: 1,
      })
    );
    const service = TestBed.inject(AuthService);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.authSession()?.userId).toBe(1);
  });

  it('drops expired session from localStorage', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        message: 'ok',
        accessToken: createJwt(Math.floor(Date.now() / 1000) - 60),
        email: 'a@example.com',
        displayName: 'User',
        roles: ['VIEWER'],
        userId: 1,
      })
    );
    const service = TestBed.inject(AuthService);
    expect(service.isAuthenticated()).toBe(false);
    expect(service.authSession()).toBeNull();
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
});
