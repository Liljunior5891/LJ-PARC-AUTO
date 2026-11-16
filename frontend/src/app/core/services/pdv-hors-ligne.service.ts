import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { erreurReseau } from './agences-hors-ligne.service';
import { PdvService } from './pdv.service';
import { SocieteService } from './societe.service';

/** Ligne vendue sans connexion, au prix affiche par l'appareil. */
export interface LigneHorsLigne {
  articleId: number;
  conditionnementId?: number | null;
  quantite: number;
  prixUnitaire: number;
  remise?: number;
  note?: string | null;
  /** Pour l'affichage sur l'appareil seulement. */
  libelle?: string;
}

/** Vente faite sans connexion, en attente d'envoi. */
export interface VenteHorsLigne {
  cle: string;
  numeroProvisoire: string;
  saisieLe: string;
  sessionId: number | null;
  lignes: LigneHorsLigne[];
  remise: number;
  montantRecu: number | null;
  clientNom?: string | null;
  /** Pour l'affichage sur l'appareil seulement. */
  total?: number;
}

export type StatutVenteHorsLigne = 'ENREGISTREE' | 'CONFLIT' | 'REPRISE' | 'ABANDONNEE';

export interface ResultatVenteHorsLigne {
  cle: string;
  numeroProvisoire: string;
  statut: StatutVenteHorsLigne;
  venteId?: number | null;
  numero?: string | null;
  message?: string | null;
  manques?: string | null;
  ecartsPrix?: string | null;
}

/** Vente hors ligne vue par le gerant. */
export interface SuiviVenteHorsLigne extends ResultatVenteHorsLigne {
  id: number;
  saisieLe: string;
  caissierNom: string;
  total: number;
  traiteePar?: string | null;
  traiteeLe?: string | null;
  note?: string | null;
  articles: string[];
}

const INTERVALLE_ESSAI_MS = 20_000;
/** Les ventes sont envoyees par paquets : le serveur en accepte 100 a la fois. */
const PAQUET = 50;

/**
 * Caisse du point de vente sans connexion. L'appareil garde une copie de ce qu'il
 * faut pour vendre (catalogue, categories, session de caisse, en-tete du ticket)
 * et une file des ventes faites hors ligne, en especes. Au retour du reseau (ou
 * de la fin d'une maintenance), la file part au serveur, qui rejoue chaque vente
 * au prix encaisse ; une vente qu'il ne peut pas enregistrer devient un conflit
 * que le gerant traite.
 */
@Injectable({ providedIn: 'root' })
export class PdvHorsLigneService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly pdv = inject(PdvService);
  private readonly societe = inject(SocieteService);
  private readonly base = `${environment.apiUrl}/pdv/hors-ligne`;
  private readonly direct = { context: new HttpContext().set(SANS_SUIVI, true) };

  /** Le serveur repond (faux apres une erreur reseau ou une maintenance, vrai des la premiere reponse). */
  readonly enLigne = signal(navigator.onLine);
  readonly enAttente = signal<VenteHorsLigne[]>([]);
  readonly envoiEnCours = signal(false);
  /** Resultats des derniers envois a montrer au caissier (conflits, stock manquant). */
  readonly derniersResultats = signal<ResultatVenteHorsLigne[]>([]);
  readonly nombreEnAttente = computed(() => this.enAttente().length);
  private demarre = false;
  private utilisateurCharge: number | null | undefined = undefined;

  demarrer(): void {
    this.suivreUtilisateur();
    if (this.demarre) {
      return;
    }
    this.demarre = true;
    window.addEventListener('online', () => this.envoyer());
    window.addEventListener('offline', () => this.enLigne.set(false));
    // Un autre onglet (caisse, commande, salle) a change la file : on reprend la sienne.
    window.addEventListener('storage', (e) => {
      if (e.key === this.cleFile()) {
        // La file enregistree fait foi : une vente envoyee par l'autre onglet n'y est plus.
        this.enAttente.set(this.lire<VenteHorsLigne[]>(this.cleFile(), []));
      }
    });
    setInterval(() => this.envoyer(), INTERVALLE_ESSAI_MS);
    this.envoyer();
  }

  /**
   * Garde d'avance sur l'appareil ce qu'il faut pour vendre hors ligne (catalogue,
   * categories, en-tete du ticket) et charge l'ecran de la caisse : utile aux
   * postes qui ne l'ont jamais ouverte (salle du restaurant).
   */
  preparer(): void {
    this.demarrer();
    const rien = { error: () => undefined };
    this.avecCopie('catalogue', this.pdv.catalogueCaisse()).subscribe(rien);
    this.avecCopie('categories', this.pdv.categories()).subscribe(rien);
    this.avecCopie('societe', this.societe.lire()).subscribe(rien);
    void import('../../pages/pdv/caisse/caisse').catch(() => undefined);
  }

  /**
   * Garde une vente faite sans connexion ; elle partira au retour du serveur.
   * Faux si l'appareil n'a pas pu l'ecrire (stockage plein) : elle n'est alors
   * gardee que tant que la page reste ouverte, et la caisse doit le dire.
   */
  mettreEnFile(vente: VenteHorsLigne): boolean {
    this.suivreUtilisateur();
    this.enAttente.set(this.fusionner([vente]));
    return this.ecrire(this.cleFile(), this.enAttente());
  }

  /** Numero imprime sur le ticket remis au client : HL-AAMMJJ-HHMM-<utilisateur>-<compteur>. */
  numeroProvisoire(): string {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    const cle = `pdv.horsligne.compteur.${this.auth.utilisateur()?.id ?? 0}`;
    const n = (this.lire<number>(cle, 0) + 1) % 10000;
    this.ecrire(cle, n);
    return `HL-${String(d.getFullYear()).slice(2)}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}`
      + `-${this.auth.utilisateur()?.id ?? 0}-${n}`;
  }

  nouvelleCle(): string {
    return typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  /** Envoie la file au serveur ; garde ce qui n'a pas pu partir. */
  envoyer(): void {
    this.suivreUtilisateur();
    const file = this.enAttente();
    if (this.envoiEnCours() || !this.auth.connecte()) {
      return;
    }
    if (file.length === 0) {
      // Rien a envoyer : on verifie seulement si le serveur repond de nouveau.
      if (!this.enLigne()) {
        this.sonder();
      }
      return;
    }
    const proprietaire = this.utilisateurCharge;
    // La file enregistree peut contenir des ventes d'un autre onglet : elles partent aussi.
    const aEnvoyer = this.fusionner([]);
    this.enAttente.set(aEnvoyer);
    const paquet = aEnvoyer.slice(0, PAQUET).map(({ total: _t, ...v }) => ({ ...v, lignes: v.lignes.map(({ libelle: _l, ...l }) => l) }));
    this.envoiEnCours.set(true);
    this.http.post<ResultatVenteHorsLigne[]>(`${this.base}/synchronisation`, paquet, this.direct).subscribe({
      next: (resultats) => {
        this.envoiEnCours.set(false);
        this.enLigne.set(true);
        if (proprietaire !== this.utilisateurCharge) {
          return;
        }
        const traites = new Set(resultats.map((r) => r.cle));
        // Relue juste avant l'ecriture : une vente ajoutee entre-temps par un autre onglet reste.
        this.enAttente.set(this.fusionner([]).filter((v) => !traites.has(v.cle)));
        this.ecrire(this.cleFile(), this.enAttente());
        const aMontrer = resultats.filter((r) => r.statut === 'CONFLIT' || r.manques || r.ecartsPrix);
        if (aMontrer.length) {
          this.derniersResultats.update((anciens) => [...aMontrer, ...anciens].slice(0, 30));
          this.ecrire(this.cleResultats(), this.derniersResultats());
        }
        if (this.enAttente().length) {
          this.envoyer();
        }
      },
      error: (e) => {
        this.envoiEnCours.set(false);
        if (erreurReseau(e)) {
          this.enLigne.set(false);
        }
      }
    });
  }

  /** Toute reponse du serveur (meme un refus) prouve que le reseau est revenu. */
  private sonder(): void {
    this.http.get(`${this.base}/conflits/nombre`, this.direct).subscribe({
      next: () => this.enLigne.set(true),
      error: (e) => this.enLigne.set(!erreurReseau(e))
    });
  }

  oublierResultats(): void {
    this.derniersResultats.set([]);
    this.ecrire(this.cleResultats(), []);
  }

  /**
   * Garde la derniere reponse d'une lecture ; sans reseau, la rend a la place de
   * l'erreur : la caisse reste utilisable hors ligne.
   */
  avecCopie<T>(cle: string, lecture: Observable<T>): Observable<T> {
    const complete = `pdv.copie.${this.auth.utilisateur()?.id ?? 'inconnu'}.${cle}`;
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

  /** Note une erreur reseau survenue ailleurs (encaissement) : la caisse passe hors ligne. */
  signalerCoupure(): void {
    this.enLigne.set(false);
  }

  // ------------------------------------------------------------- suivi par le gerant

  recentes(): Observable<SuiviVenteHorsLigne[]> {
    return this.http.get<SuiviVenteHorsLigne[]>(this.base);
  }

  nombreConflits(): Observable<{ conflits: number }> {
    return this.http.get<{ conflits: number }>(`${this.base}/conflits/nombre`, this.direct);
  }

  reprendre(id: number, sessionId: number | null): Observable<SuiviVenteHorsLigne> {
    return this.http.post<SuiviVenteHorsLigne>(`${this.base}/${id}/reprise`, { sessionId });
  }

  abandonner(id: number, note: string): Observable<SuiviVenteHorsLigne> {
    return this.http.post<SuiviVenteHorsLigne>(`${this.base}/${id}/abandon`, { note });
  }

  // ---------------------------------------------------------------- outils

  /** Chaque utilisateur a sa file : un autre caissier sur le meme poste ne voit pas celle du precedent. */
  private suivreUtilisateur(): void {
    const id = this.auth.utilisateur()?.id ?? null;
    if (id === this.utilisateurCharge) {
      return;
    }
    this.utilisateurCharge = id;
    this.enAttente.set(id === null ? [] : this.lire<VenteHorsLigne[]>(this.cleFile(), []));
    this.derniersResultats.set(id === null ? [] : this.lire<ResultatVenteHorsLigne[]>(this.cleResultats(), []));
  }

  /**
   * File enregistree sur l'appareil, completee des ventes connues de cet onglet seulement
   * (stockage plein) et des ventes ajoutees : rien n'est perdu quand plusieurs onglets ecrivent.
   */
  private fusionner(ajouts: VenteHorsLigne[]): VenteHorsLigne[] {
    const stockee = this.lire<VenteHorsLigne[]>(this.cleFile(), []);
    const resultat = [...stockee];
    const connues = new Set(stockee.map((v) => v.cle));
    for (const v of [...this.enAttente(), ...ajouts]) {
      if (!connues.has(v.cle)) {
        connues.add(v.cle);
        resultat.push(v);
      }
    }
    return resultat;
  }

  private cleFile(): string {
    return `pdv.horsligne.file.${this.auth.utilisateur()?.id ?? 'inconnu'}`;
  }

  private cleResultats(): string {
    return `pdv.horsligne.resultats.${this.auth.utilisateur()?.id ?? 'inconnu'}`;
  }

  private lire<T>(cle: string, defaut: T): T {
    try {
      const brut = localStorage.getItem(cle);
      return brut ? (JSON.parse(brut) as T) : defaut;
    } catch {
      return defaut;
    }
  }

  private ecrire(cle: string, valeur: unknown): boolean {
    try {
      localStorage.setItem(cle, JSON.stringify(valeur));
      return true;
    } catch {
      // Stockage plein ou indisponible : la copie n'est pas gardee.
      return false;
    }
  }
}
