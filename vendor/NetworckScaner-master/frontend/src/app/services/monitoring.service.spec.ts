import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { API_BASE_URL } from '../api-config';
import { AuthService } from '../auth.service';
import { NotifierService } from '../notifier.service';
import { MonitoringService } from './monitoring.service';

describe('MonitoringService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        MonitoringService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: 'http://localhost:8081' },
        {
          provide: AuthService,
          useValue: {
            authSession: signal(null),
            isAuthenticated: signal(false),
          },
        },
        {
          provide: NotifierService,
          useValue: { warn: vi.fn(), error: vi.fn(), success: vi.fn(), info: vi.fn() },
        },
      ],
    });
  });

  it('monitoringStatusSummary sums availability buckets', () => {
    const svc = TestBed.inject(MonitoringService);
    svc.monitoredDevicesPage.set({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 15,
      first: true,
      last: true,
      availableCount: 2,
      unavailableCount: 1,
      unknownCount: 3,
    });
    const summary = svc.monitoringStatusSummary();
    expect(summary.find((s) => s.key === 'ALL')?.count).toBe(6);
    expect(summary.find((s) => s.key === 'AVAILABLE')?.count).toBe(2);
  });
});
