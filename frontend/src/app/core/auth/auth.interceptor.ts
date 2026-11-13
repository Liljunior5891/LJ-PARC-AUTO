import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { VerrouService } from '../services/verrou.service';
import { EtatPlateformeService, MODULES_HORS_LIGNE, erreurMaintenance } from '../services/etat-plateforme.service';
import { environment } from '../../../environments/environment';

/** Ajoute le jeton JWT et deconnecte l'utilisateur si la session a expire. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const verrou = inject(VerrouService);
  const etatPlateforme = inject(EtatPlateformeService);
  const token = auth.token;

  // Le jeton ne part que vers l'API de la plateforme, jamais vers un service tiers.
  const versApi = req.url.startsWith(environment.apiUrl);
  const requete = token && versApi
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      if (erreur.status === 401 && !req.url.includes('/auth/login') && !req.url.includes('/auth/logout') && !req.url.includes('/auth/deux-facteurs')) {
        // Session invalide ou expiree : on la ferme et on l'annonce clairement.
        auth.logout('expiration');
      }
      if (erreur.status === 403 && (erreur.error as { code?: string } | null)?.code === 'MOT_DE_PASSE_A_CHANGER') {
        auth.exigerChangementMotDePasse();
      }
      if (erreur.status === 403 && (erreur.error as { code?: string } | null)?.code === 'DEUX_FACTEURS_A_ACTIVER') {
        auth.exigerDeuxFacteurs();
      }
      // Module en maintenance : page de maintenance (sauf pour les agences, qui continuent hors ligne).
      const maintenance = erreurMaintenance(erreur);
      if (maintenance && !MODULES_HORS_LIGNE.includes(maintenance.module)) {
        etatPlateforme.signaler(maintenance);
      }
      if (erreur.status === 423 && (erreur.error as { code?: string } | null)?.code === 'ECRAN_VERROUILLE') {
        verrou.exigerParServeur();
      }
      return throwError(() => erreur);
    })
  );
};
