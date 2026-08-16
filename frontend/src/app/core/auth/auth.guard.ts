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
  // Le motif est transmis pour distinguer une session expiree d'un simple acces direct.
  return router.createUrlTree(['/connexion'], {
    queryParams: auth.sessionExpiree()
      ? { retour: state.url, motif: 'expiration' }
      : { retour: state.url }
  });
};

/** Restreint une route a une liste de roles. */
export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.aRole(...roles) ? true : router.createUrlTree(['/tableau-bord']);
  };
