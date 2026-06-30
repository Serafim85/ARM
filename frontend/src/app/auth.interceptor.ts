import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AUTH_STORAGE_KEY } from './auth.constants';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const rawSession = localStorage.getItem(AUTH_STORAGE_KEY);
  let token: string | undefined;
  if (rawSession) {
    try {
      token = JSON.parse(rawSession)?.accessToken as string | undefined;
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      token = undefined;
    }
  }

  const authorizedRequest = token
    ? request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
    : request;

  return next(authorizedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        localStorage.removeItem(AUTH_STORAGE_KEY);
        const isAuthEndpoint = request.url.includes('/api/auth/login') || request.url.includes('/api/auth/logout');
        if (!isAuthEndpoint) {
          auth.handleUnauthorized();
        }
      }
      return throwError(() => error);
    })
  );
};
