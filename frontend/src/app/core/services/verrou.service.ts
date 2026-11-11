import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, from, of, switchMap, tap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { environment } from '../../../environments/environment';
import { SANS_SUIVI } from '../operation/operation.interceptor';

const CLE = 'pdv.caisse.deverrouillee';
/** Delai par defaut sans geste avant le verrouillage, tant que les reglages ne sont pas lus. */
export const MINUTES_INACTIVITE = 5;
/** Iterations PBKDF2 de l'empreinte hors ligne (recommandation OWASP pour SHA-256). */
const ITERATIONS_PBKDF2 = 600_000;
/** Erreurs de code toleres hors ligne avant d'effacer l'empreinte locale. */
const ESSAIS_LOCAUX_MAX = 5;

function hex(octets: Uint8Array): string {
  return Array.from(octets).map((b) => b.toString(16).padStart(2, '0')).join('');
}

function deHex(texte: string): Uint8Array {
  const octets = new Uint8Array(texte.length / 2);
  for (let i = 0; i < octets.length; i++) {
    octets[i] = parseInt(texte.substring(i * 2, i * 2 + 2), 16);
  }
  return octets;
}

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
        return from(this.comparer(pin, locale)).pipe(switchMap((egal) => {
          if (!egal) {
            // Hors ligne aussi, 5 erreurs effacent l'empreinte : il faudra le reseau pour deverrouiller.
            if (this.noterEchecLocal() >= ESSAIS_LOCAUX_MAX) {
              this.oublierEmpreinte();
              return throwError(() => ({ error: { message: "Trop d'erreurs : reconnectez-vous au reseau pour deverrouiller" } }));
            }
            return throwError(() => ({ error: { message: 'Code PIN incorrect' } }));
          }
          this.effacerEchecsLocaux();
          this.deverrouiller();
          return of(undefined);
        }));
      }));
  }

  /**
   * Empreinte lente (PBKDF2-SHA256, sel aleatoire) pour le deverrouillage sans
   * reseau : retrouver un code de 6 chiffres a partir de l'empreinte coute des
   * heures, et non une fraction de seconde. Format : pbkdf2$iterations$sel$empreinte.
   */
  private async empreinte(pin: string, sel: Uint8Array, iterations: number): Promise<string> {
    const cle = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveBits']);
    const bits = await crypto.subtle.deriveBits(
      { name: 'PBKDF2', hash: 'SHA-256', salt: sel as BufferSource, iterations }, cle, 256);
    return `pbkdf2$${iterations}$${hex(sel)}$${hex(new Uint8Array(bits))}`;
  }

  private async comparer(pin: string, stockee: string): Promise<boolean> {
    const morceaux = stockee.split('$');
    if (morceaux.length !== 4 || morceaux[0] !== 'pbkdf2') {
      // Ancien format (SHA-256 rapide) : abandonne, il faudra le reseau une fois.
      this.oublierEmpreinte();
      return false;
    }
    const calculee = await this.empreinte(pin, deHex(morceaux[2]), Number(morceaux[1]));
    return calculee === stockee;
  }

  private async memoriser(pin: string): Promise<void> {
    try {
      if (crypto?.subtle) {
        const sel = crypto.getRandomValues(new Uint8Array(16));
        localStorage.setItem(this.cleEmpreinte(), await this.empreinte(pin, sel, ITERATIONS_PBKDF2));
        this.effacerEchecsLocaux();
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

  private oublierEmpreinte(): void {
    try {
      localStorage.removeItem(this.cleEmpreinte());
    } catch {
      // Stockage indisponible.
    }
    this.effacerEchecsLocaux();
  }

  private noterEchecLocal(): number {
    try {
      const n = Number(localStorage.getItem(this.cleEmpreinte() + '.echecs') ?? '0') + 1;
      localStorage.setItem(this.cleEmpreinte() + '.echecs', String(n));
      return n;
    } catch {
      return ESSAIS_LOCAUX_MAX;
    }
  }

  private effacerEchecsLocaux(): void {
    try {
      localStorage.removeItem(this.cleEmpreinte() + '.echecs');
    } catch {
      // Stockage indisponible.
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
