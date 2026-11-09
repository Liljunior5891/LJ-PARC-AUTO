import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, from, of, switchMap, tap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';
import { SANS_SUIVI } from '../operation/operation.interceptor';

const CLE = 'pdv.caisse.deverrouillee';
/** Delai par defaut sans geste avant le verrouillage, tant que les reglages ne sont pas lus. */
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

  private readonly auth = inject(AuthService);
  readonly verrouille = signal(!this.lire());
  /** Minutes sans geste avant le verrouillage, reglees par l'administrateur (point de vente ou agences). */
  readonly minutes = signal(MINUTES_INACTIVITE);

  /** Sans reseau, un code deja verifie sur cet appareil suffit : on suppose qu'il existe. */
  etat(): Observable<{ defini: boolean }> {
    return this.http.get<{ defini: boolean }>(this.base).pipe(
      catchError((e) => (e?.status === 0 && this.empreinteLocale() ? of({ defini: true }) : throwError(() => e))));
  }

  creer(pin: string): Observable<void> {
    return this.http.post<void>(this.base, { pin }, this.direct).pipe(
      switchMap(() => from(this.memoriser(pin))), tap(() => this.deverrouiller()));
  }

  /**
   * Verification par le serveur ; sans reseau, comparaison avec l'empreinte du
   * dernier code verifie sur cet appareil (le code lui-meme n'est jamais garde).
   */
  verifier(pin: string): Observable<void> {
    return this.http.post<void>(`${this.base}/verification`, { pin }, this.direct).pipe(
      switchMap(() => from(this.memoriser(pin))),
      tap(() => this.deverrouiller()),
      catchError((e) => {
        if (e?.status !== 0) {
          return throwError(() => e);
        }
        const locale = this.empreinteLocale();
        if (!locale) {
          return throwError(() => ({ error: { message: 'Sans connexion : code jamais verifie sur cet appareil' } }));
        }
        return from(this.empreinte(pin)).pipe(switchMap((calculee) => {
          if (calculee !== locale) {
            return throwError(() => ({ error: { message: 'Code PIN incorrect' } }));
          }
          this.deverrouiller();
          return of(undefined);
        }));
      }));
  }

  /** Empreinte salee par le compte (SHA-256), pour le deverrouillage sans reseau. */
  private async empreinte(pin: string): Promise<string> {
    const sel = `lj-saas:${this.auth.utilisateur()?.id ?? ''}:${this.auth.utilisateur()?.email ?? ''}:`;
    const octets = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(sel + pin));
    return Array.from(new Uint8Array(octets)).map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  private async memoriser(pin: string): Promise<void> {
    try {
      if (crypto?.subtle) {
        localStorage.setItem(this.cleEmpreinte(), await this.empreinte(pin));
      }
    } catch {
      // Stockage indisponible : le deverrouillage hors ligne ne sera pas possible.
    }
  }

  private empreinteLocale(): string | null {
    try {
      return localStorage.getItem(this.cleEmpreinte());
    } catch {
      return null;
    }
  }

  private cleEmpreinte(): string {
    return `pin.empreinte.${this.auth.utilisateur()?.id ?? 'inconnu'}`;
  }

  /** Changement du code depuis « Mon compte » : le code actuel est exige. */
  changer(actuel: string, nouveau: string): Observable<void> {
    return this.http.post<void>(`${this.base}/changement`, { actuel, nouveau });
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
