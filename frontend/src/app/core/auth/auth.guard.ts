import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from '../models/models';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.connecte()) {
    return true;
  }
  return router.createUrlTree(['/connexion'], { queryParams: { retour: state.url } });
};

/** Restreint une route a une liste de roles. */
export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.aRole(...roles) ? true : router.createUrlTree(['/tableau-bord']);
  };
