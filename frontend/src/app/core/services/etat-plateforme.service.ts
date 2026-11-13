import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CodeModule } from '../models/models';

export type StatutMaintenance = 'PROGRAMMEE' | 'ANNONCEE' | 'EN_COURS' | 'TERMINEE' | 'ANNULEE';

export interface MaintenancePlateforme {
  id: number;
  message: string;
  modules: { code: CodeModule; libelle: string }[];
  annonceLe: string;
  debut: string;
  finPrevue: string;
  termineeLe?: string | null;
  annuleeLe?: string | null;
  statut: StatutMaintenance;
  creePar?: string | null;
  creeLe?: string | null;
}

export interface VersionPlateforme {
  id: number;
  numero: string;
  titre: string;
  notes: string;
  rechargement: boolean;
  publieeLe: string;
  creePar?: string | null;
}

export interface EtatPlateforme {
  maintenant: string;
  maintenances: MaintenancePlateforme[];
  version: VersionPlateforme | null;
}

/** Reponse 503 d'un ecran dont le module est en maintenance. */
export interface MaintenanceRencontree {
  module: CodeModule;
  moduleLibelle: string;
  message: string;
  finPrevue: string;
}

/** Module dont le travail continue hors ligne pendant une maintenance (pas de page bloquante). */
export const MODULES_HORS_LIGNE: CodeModule[] = ['AGENCES'];

/** Pages du parc (elles n'ont pas de prefixe commun). */
const PAGES_PARC = ['/vue-parc', '/camions', '/chauffeurs', '/affectations', '/documents', '/clients', '/locations',
  '/missions', '/recettes', '/depenses', '/fournisseurs-parc', '/carburant', '/facturation', '/rentabilite', '/gps',
  '/maintenance', '/direction', '/rapports', '/livraisons'];

/** Module auquel appartient une page ; null pour les pages communes (accueil, utilisateurs, reglages...). */
export function moduleDeLaPage(url: string): CodeModule | null {
  const chemin = url.split(/[?#]/)[0];
  const sous = (prefixe: string) => chemin === prefixe || chemin.startsWith(prefixe + '/');
  if (sous('/pdv') || sous('/t')) {
    return 'POINT_DE_VENTE';
  }
  if (sous('/agences')) {
    return 'AGENCES';
  }
  if (sous('/presence') || sous('/pointage') || sous('/borne-presence')) {
    return 'PRESENCE';
  }
  if (sous('/marketplace') || sous('/boutique') || sous('/suivi') || sous('/marche')) {
    return 'MARKETPLACE';
  }
  return PAGES_PARC.some(sous) ? 'GESTION_PARC' : null;
}

const CLE_VERSION_VUE = 'lj.version.vue';
const INTERVALLE = 60 * 1000;

/** Une reponse d'erreur est-elle le refus d'un module en maintenance ? */
export function erreurMaintenance(e: unknown): MaintenanceRencontree | null {
  const r = e as { status?: number; error?: (Partial<MaintenanceRencontree> & { code?: string }) | null };
  if (r?.status !== 503 || r.error?.code !== 'MAINTENANCE') {
    return null;
  }
  return {
    module: r.error.module as CodeModule,
    moduleLibelle: r.error.moduleLibelle ?? '',
    message: r.error.message ?? '',
    finPrevue: r.error.finPrevue ?? ''
  };
}

/**
 * Maintenances et version de la plateforme, relues chaque minute par tous les
 * navigateurs (connectes ou non) : bandeau d'annonce, page de maintenance et
 * invitation a recharger quand une nouvelle version est publiee.
 */
@Injectable({ providedIn: 'root' })
export class EtatPlateformeService {
  private readonly http = inject(HttpClient);

  readonly maintenances = signal<MaintenancePlateforme[]>([]);
  readonly version = signal<VersionPlateforme | null>(null);
  /** Derniere maintenance renvoyee par l'API pour l'ecran ouvert ; videe a chaque navigation. */
  readonly rencontree = signal<MaintenanceRencontree | null>(null);
  /** Version en vigueur quand ce navigateur a charge l'application. */
  private versionAuChargement: string | null | undefined = undefined;
  readonly versionVue = signal<string | null>(lire(CLE_VERSION_VUE));
  private minuterie?: ReturnType<typeof setInterval>;

  /** Une version plus recente que celle chargee demande un rechargement. */
  readonly rechargementPropose = computed(() => {
    const v = this.version();
    return !!v && v.rechargement && this.versionAuChargement !== undefined && v.numero !== this.versionAuChargement;
  });

  /** Nouveautes pas encore lues par cet utilisateur sur ce navigateur. */
  readonly nouveautesAVoir = computed(() => {
    const v = this.version();
    return !!v && v.numero !== this.versionVue();
  });

  demarrer(): void {
    if (this.minuterie) {
      return;
    }
    this.actualiser();
    this.minuterie = setInterval(() => this.actualiser(), INTERVALLE);
    // Un onglet revenu au premier plan se met a jour sans attendre la minute.
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        this.actualiser();
      }
    });
  }

  actualiser(): void {
    this.http.get<EtatPlateforme>(`${environment.apiUrl}/public/etat-plateforme`).subscribe({
      next: (e) => {
        this.maintenances.set(e.maintenances);
        this.version.set(e.version);
        if (this.versionAuChargement === undefined) {
          this.versionAuChargement = e.version?.numero ?? null;
          // Premiere visite : rien a annoncer, la version en vigueur est celle qu'on decouvre.
          if (this.versionVue() === null && e.version) {
            this.marquerVue();
          }
        }
        const r = this.rencontree();
        if (r && !e.maintenances.some((m) => m.statut === 'EN_COURS' && m.modules.some((x) => x.code === r.module))) {
          // La maintenance est finie : la page de ce module se recharge pour rouvrir l'ecran ;
          // une page d'un autre module, qui n'etait pas bloquee, reste telle quelle.
          this.rencontree.set(null);
          if (moduleDeLaPage(location.pathname) === r.module) {
            location.reload();
          }
        }
      },
      error: () => undefined
    });
  }

  signaler(m: MaintenanceRencontree): void {
    this.rencontree.set(m);
  }

  oublier(): void {
    this.rencontree.set(null);
  }

  marquerVue(): void {
    const v = this.version();
    if (v) {
      ecrire(CLE_VERSION_VUE, v.numero);
      this.versionVue.set(v.numero);
    }
  }

  nouveautes(limite = 20): Observable<VersionPlateforme[]> {
    return this.http.get<VersionPlateforme[]>(`${environment.apiUrl}/public/nouveautes`, { params: { limite } });
  }

  // ------------------------------------------------------------- console super-admin

  listeMaintenances(): Observable<MaintenancePlateforme[]> {
    return this.http.get<MaintenancePlateforme[]>(`${environment.apiUrl}/plateforme/maintenances`);
  }

  programmer(saisie: SaisieMaintenance): Observable<MaintenancePlateforme> {
    return this.http.post<MaintenancePlateforme>(`${environment.apiUrl}/plateforme/maintenances`, saisie);
  }

  modifierMaintenance(id: number, saisie: SaisieMaintenance): Observable<MaintenancePlateforme> {
    return this.http.put<MaintenancePlateforme>(`${environment.apiUrl}/plateforme/maintenances/${id}`, saisie);
  }

  terminer(id: number): Observable<MaintenancePlateforme> {
    return this.http.post<MaintenancePlateforme>(`${environment.apiUrl}/plateforme/maintenances/${id}/terminer`, {});
  }

  annuler(id: number): Observable<MaintenancePlateforme> {
    return this.http.post<MaintenancePlateforme>(`${environment.apiUrl}/plateforme/maintenances/${id}/annuler`, {});
  }

  listeVersions(): Observable<VersionPlateforme[]> {
    return this.http.get<VersionPlateforme[]>(`${environment.apiUrl}/plateforme/versions`);
  }

  publier(saisie: SaisieVersion): Observable<VersionPlateforme> {
    return this.http.post<VersionPlateforme>(`${environment.apiUrl}/plateforme/versions`, saisie);
  }

  modifierVersion(id: number, saisie: SaisieVersion): Observable<VersionPlateforme> {
    return this.http.put<VersionPlateforme>(`${environment.apiUrl}/plateforme/versions/${id}`, saisie);
  }

  supprimerVersion(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/plateforme/versions/${id}`);
  }
}

export interface SaisieMaintenance {
  message: string;
  modules: CodeModule[];
  debut: string;
  finPrevue: string;
  annonceHeures: number | null;
}

export interface SaisieVersion {
  numero: string;
  titre: string;
  notes: string;
  rechargement: boolean;
  publieeLe: string | null;
}

function lire(cle: string): string | null {
  try {
    return localStorage.getItem(cle);
  } catch {
    return null;
  }
}

function ecrire(cle: string, valeur: string): void {
  try {
    localStorage.setItem(cle, valeur);
  } catch {
    // Stockage indisponible (navigation privee) : les nouveautes se remontreront.
  }
}
