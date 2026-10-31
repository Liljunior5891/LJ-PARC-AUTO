import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SANS_SUIVI } from '../operation/operation.interceptor';

const CLE = 'pdv.caisse.deverrouillee';
/** Sans geste pendant ce delai, l'ecran de caisse se verrouille. */
export const MINUTES_INACTIVITE = 5;

/**
 * Verrou de l'ecran de caisse : le caissier deja connecte le leve avec son
 * code PIN, au debut du service et apres chaque absence. L'etat ne survit pas
 * a la fermeture de l'onglet.
 */
@Injectable({ providedIn: 'root' })
export class VerrouService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth/pin`;
  private readonly direct = { context: new HttpContext().set(SANS_SUIVI, true) };

  readonly verrouille = signal(!this.lire());

  etat(): Observable<{ defini: boolean }> {
    return this.http.get<{ defini: boolean }>(this.base);
  }

  creer(pin: string): Observable<void> {
    return this.http.post<void>(this.base, { pin }, this.direct).pipe(tap(() => this.deverrouiller()));
  }

  verifier(pin: string): Observable<void> {
    return this.http.post<void>(`${this.base}/verification`, { pin }, this.direct).pipe(tap(() => this.deverrouiller()));
  }

  verrouiller(): void {
    this.verrouille.set(true);
    try {
      sessionStorage.removeItem(CLE);
    } catch {
      // Stockage indisponible : le verrou reste en memoire.
    }
  }

  private deverrouiller(): void {
    this.verrouille.set(false);
    try {
      sessionStorage.setItem(CLE, '1');
    } catch {
      // Stockage indisponible : il faudra resaisir le code apres un rechargement.
    }
  }

  private lire(): boolean {
    try {
      return sessionStorage.getItem(CLE) === '1';
    } catch {
      return false;
    }
  }
}
