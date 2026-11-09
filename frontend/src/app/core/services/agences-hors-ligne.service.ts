import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { SANS_SUIVI } from '../operation/operation.interceptor';

const INTERVALLE_ESSAI_MS = 20_000;

export type TypeOperationHorsLigne = 'TRANSACTION' | 'VENTE_CARTE' | 'DEPENSE';

/** Operation saisie sans connexion, en attente d'envoi. */
export interface OperationEnAttente {
  cle: string;
  type: TypeOperationHorsLigne;
  saisieLe: string;
  resume: string;
  donnees: Record<string, unknown>;
}

export interface ResultatHorsLigne {
  cle: string;
  statut: 'ENREGISTREE' | 'CONFLIT' | 'REPRISE' | 'ABANDONNEE';
  reference?: string | null;
  message?: string | null;
  /** Ajoutes cote appareil pour l'affichage. */
  resume?: string;
  saisieLe?: string;
}

export interface ConflitHorsLigne {
  id: number;
  cle: string;
  type: TypeOperationHorsLigne;
  agenceId?: number | null;
  agenceNom?: string | null;
  auteurNom: string;
  saisieLe: string;
  recueLe: string;
  resume: string;
  motif?: string | null;
  statut: string;
  reference?: string | null;
}

/** Une erreur sans reponse du serveur : reseau coupe ou serveur injoignable. */
export function erreurReseau(e: unknown): boolean {
  return (e as { status?: number })?.status === 0;
}

/**
 * Travail des caissiers d'agence sans connexion. L'appareil garde une copie
 * des donnees utiles a la saisie (services, droits, caisse, stock de cartes,
 * categories de depenses) et une file des operations faites hors ligne :
 * transactions, ventes de cartes, depenses. Au retour du reseau, la file part
 * au serveur, qui rejoue chaque operation avec ses controles habituels ; une
 * operation refusee devient un conflit que le superviseur traite.
 */
@Injectable({ providedIn: 'root' })
export class AgencesHorsLigneService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/agences/hors-ligne`;
  private readonly direct = { context: new HttpContext().set(SANS_SUIVI, true) };

  readonly enLigne = signal(navigator.onLine);
  readonly enAttente = signal<OperationEnAttente[]>([]);
  readonly envoiEnCours = signal(false);
  /** Resultats des derniers envois (conflits a montrer au caissier). */
  readonly derniersResultats = signal<ResultatHorsLigne[]>([]);
  private demarre = false;

  demarrer(): void {
    if (this.demarre) {
      return;
    }
    this.demarre = true;
    this.enAttente.set(this.lire(this.cleFile(), []));
    this.derniersResultats.set(this.lire(this.cleResultats(), []));
    window.addEventListener('online', () => {
      this.enLigne.set(true);
      this.envoyer();
    });
    window.addEventListener('offline', () => this.enLigne.set(false));
    // Le reseau de l'appareil peut marcher alors que le serveur ne repond pas : on reessaie regulierement.
    setInterval(() => this.envoyer(), INTERVALLE_ESSAI_MS);
    if ('serviceWorker' in navigator && window.isSecureContext) {
      void navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    }
    this.envoyer();
  }

  /** Met une operation dans la file ; elle part des que le serveur repond. Renvoie sa reference provisoire. */
  ajouter(type: TypeOperationHorsLigne, donnees: Record<string, unknown>, resume: string): string {
    const cle = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const op: OperationEnAttente = { cle, type, saisieLe: new Date().toISOString(), resume, donnees };
    this.enAttente.update((f) => [...f, op]);
    this.ecrire(this.cleFile(), this.enAttente());
    this.enLigne.set(false);
    return this.referenceProvisoire(cle);
  }

  /** « HL-1A2B3C » : ce que le caissier note sur le recu en attendant la vraie reference. */
  referenceProvisoire(cle: string): string {
    return 'HL-' + cle.replace(/-/g, '').slice(0, 6).toUpperCase();
  }

  /** Envoie la file au serveur ; chaque operation revient enregistree ou en conflit. */
  envoyer(): void {
    const file = this.enAttente();
    if (!file.length || this.envoiEnCours() || !this.auth.connecte()) {
      return;
    }
    this.envoiEnCours.set(true);
    this.http.post<ResultatHorsLigne[]>(`${this.base}/synchronisation`, file, this.direct).subscribe({
      next: (resultats) => {
        this.envoiEnCours.set(false);
        this.enLigne.set(true);
        const parCle = new Map(file.map((o) => [o.cle, o]));
        const traites = new Set(resultats.map((r) => r.cle));
        this.enAttente.update((f) => f.filter((o) => !traites.has(o.cle)));
        this.ecrire(this.cleFile(), this.enAttente());
        const enrichis = resultats.map((r) => ({ ...r, resume: parCle.get(r.cle)?.resume, saisieLe: parCle.get(r.cle)?.saisieLe }));
        this.derniersResultats.update((anciens) => [...enrichis, ...anciens].slice(0, 30));
        this.ecrire(this.cleResultats(), this.derniersResultats());
      },
      error: (e) => {
        this.envoiEnCours.set(false);
        if (erreurReseau(e)) {
          this.enLigne.set(false);
        }
      }
    });
  }

  /** Le caissier a vu les resultats : on les efface de son bandeau. */
  oublierResultats(): void {
    this.derniersResultats.set([]);
    this.ecrire(this.cleResultats(), []);
  }

  // ------------------------------------------------------------ conflits

  conflits(): Observable<ConflitHorsLigne[]> {
    return this.http.get<ConflitHorsLigne[]>(`${this.base}/conflits`);
  }

  nombreConflits(): Observable<{ nombre: number }> {
    return this.http.get<{ nombre: number }>(`${this.base}/conflits/nombre`, this.direct);
  }

  reprendre(id: number): Observable<ConflitHorsLigne> {
    return this.http.post<ConflitHorsLigne>(`${this.base}/conflits/${id}/reprise`, {});
  }

  abandonner(id: number, note: string): Observable<ConflitHorsLigne> {
    return this.http.post<ConflitHorsLigne>(`${this.base}/conflits/${id}/abandon`, { note });
  }

  // ------------------------------------------------ copies pour la saisie

  /**
   * Garde la derniere reponse d'une lecture ; sans reseau, la rend a la place
   * de l'erreur. Les ecrans de saisie restent ainsi utilisables hors ligne.
   */
  avecCopie<T>(cle: string, lecture: Observable<T>): Observable<T> {
    const complete = `agences.copie.${this.auth.utilisateur()?.id ?? 'inconnu'}.${cle}`;
    return lecture.pipe(
      tap((valeur) => {
        this.enLigne.set(true);
        this.ecrire(complete, valeur);
      }),
      catchError((e) => {
        if (!erreurReseau(e)) {
          return throwError(() => e);
        }
        this.enLigne.set(false);
        const copie = this.lire<T | undefined>(complete, undefined);
        return copie !== undefined ? of(copie) : throwError(() => e);
      })
    );
  }

  // ---------------------------------------------------------------- outils

  private cleFile(): string {
    return `agences.horsligne.file.${this.auth.utilisateur()?.id ?? 'inconnu'}`;
  }

  private cleResultats(): string {
    return `agences.horsligne.resultats.${this.auth.utilisateur()?.id ?? 'inconnu'}`;
  }

  private lire<T>(cle: string, defaut: T): T {
    try {
      const brut = localStorage.getItem(cle);
      return brut ? (JSON.parse(brut) as T) : defaut;
    } catch {
      return defaut;
    }
  }

  private ecrire(cle: string, valeur: unknown): void {
    try {
      localStorage.setItem(cle, JSON.stringify(valeur));
    } catch {
      // Stockage plein ou indisponible : la copie n'est pas gardee.
    }
  }
}
