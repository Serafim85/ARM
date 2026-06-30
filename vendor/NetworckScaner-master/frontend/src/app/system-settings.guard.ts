import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const systemSettingsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = auth.authSession()?.roles ?? [];
  if (roles.includes('ADMIN') || roles.includes('OPERATOR')) {
    return true;
  }
  return router.createUrlTree(['/scan']);
};

