import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { CodeModule, Role } from '../models/models';

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
    return auth.aRole(...roles) ? true : router.createUrlTree([auth.pageAccueil()]);
  };

/**
 * Reserve les ecrans d'un module aux entreprises qui l'ont active et aux
 * profils qui y ont acces ; les autres retournent a leur page d'accueil.
 */
export const moduleGuard =
  (module: CodeModule): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.peutUtiliser(module) ? true : router.createUrlTree([auth.pageAccueil()]);
  };

/** Reserve un ecran a un type d'activite (les tables et la cuisine ne servent qu'en restauration). */
export const varianteGuard =
  (module: CodeModule, variante: string): CanActivateFn =>
  () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    return auth.variante(module) === variante ? true : router.createUrlTree([auth.pageAccueil()]);
  };

/** L'ancienne caisse est celle des boutiques ; un restaurant encaisse au comptoir sur sa nouvelle caisse. */
export const caisseBoutiqueGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.estRestauration() ? router.createUrlTree(['/pdv/comptoir']) : true;
};

/**
 * Un poste de travail (caissier, serveur, cuisinier) reste sur ses ecrans :
 * toute autre adresse le ramene a sa page d'accueil.
 */
export const posteGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.estPoste()) {
    return true;
  }
  const url = state.url.split('?')[0];
  const autorisee = auth.routesPoste().some((r) => url === r || url.startsWith(r + '/'));
  return autorisee ? true : router.createUrlTree([auth.pageAccueil()]);
};

/** Les pages d'entreprise (comptes, parametrage, modules) n'ont pas de sens pour l'exploitant. */
export const entrepriseGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.estSuperAdmin() ? router.createUrlTree(['/plateforme']) : true;
};
