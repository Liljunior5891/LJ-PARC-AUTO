import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';

/** Ajoute le jeton JWT et deconnecte l'utilisateur si la session a expire. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token;

  // Le jeton ne part que vers l'API de la plateforme, jamais vers un service tiers.
  const versApi = req.url.startsWith(environment.apiUrl);
  const requete = token && versApi
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      if (erreur.status === 401 && !req.url.includes('/auth/login') && !req.url.includes('/auth/logout')) {
        // Session invalide ou expiree : on la ferme et on l'annonce clairement.
        auth.logout('expiration');
      }
      if (erreur.status === 403 && (erreur.error as { code?: string } | null)?.code === 'MOT_DE_PASSE_A_CHANGER') {
        auth.exigerChangementMotDePasse();
      }
      return throwError(() => erreur);
    })
  );
};
