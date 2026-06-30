import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { environment } from '../environments/environment';
import { AuthService } from './auth.service';

export const systemSettingsGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = auth.authSession()?.roles ?? [];
  if (roles.includes('ADMIN') || roles.includes('OPERATOR')) {
    return true;
  }
  return router.createUrlTree([
    environment.hideNetworkScannerFeatures ? '/workstations' : '/scan',
  ]);
};

