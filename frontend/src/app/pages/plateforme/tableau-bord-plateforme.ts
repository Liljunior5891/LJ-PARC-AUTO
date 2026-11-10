import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PointSerie, TableauBordPlateforme, TEINTES_ETATS } from '../../core/models/abonnement';
import { CodeModule } from '../../core/models/models';
import { PRESENTATION_MODULES } from '../../core/models/plateforme';
import { PlateformeService } from '../../core/services/plateforme.service';
import { GrapheBarres } from './graphe-barres';

const LIBELLES_MODULES: Record<string, string> = {
  GESTION_PARC: 'Gestion de parc',
  POINT_DE_VENTE: 'Point de vente',
  MARKETPLACE: 'Marketplace',
  AGENCES: 'Agences multiservices',
  PRESENCE: 'Gestion de presence'
};

const LIBELLES_VARIANTES: Record<string, string> = {
  BOUTIQUE: 'Boutique / commerce',
  RESTAURATION: 'Bar, restaurant, cafe',
  MATERIAUX: 'Quincaillerie & materiaux'
};

const LIBELLES_MOYENS: Record<string, string> = {
  MOBILE_MONEY: 'Mobile Money',
  VIREMENT: 'Virement',
  ESPECES: 'Especes',
  CHEQUE: 'Cheque',
  AUTRE: 'Autre'
};

/** Une repartition prete a dessiner : libelle, valeur et part de la plus grande. */
interface Ligne {
  cle: string;
  libelle: string;
  valeur: number;
  part: number;
  icone?: string;
}

/**
 * Tableau de bord de l'exploitant : entreprises, revenus d'abonnement, usage de
 * la plateforme, repartitions et echeances a suivre.
 */
@Component({
  selector: 'app-tableau-bord-plateforme',
  imports: [DatePipe, DecimalPipe, RouterLink, GrapheBarres],
  templateUrl: './tableau-bord-plateforme.html',
  styleUrl: './tableau-bord-plateforme.scss'
})
export class TableauBordPlateformeComponent implements OnInit {
  private readonly service = inject(PlateformeService);

  readonly donnees = signal<TableauBordPlateforme | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly teintes = TEINTES_ETATS;

  readonly evolutionInscriptions = computed(() => this.evolution(
    this.donnees()?.inscritesCeMois ?? 0, this.donnees()?.inscritesMoisPrecedent ?? 0));
  readonly evolutionEncaisse = computed(() => this.evolution(
    this.donnees()?.encaisseCeMois ?? 0, this.donnees()?.encaisseMoisPrecedent ?? 0));

  readonly etats = computed(() => this.lignes(this.donnees()?.parEtat ?? []));
  readonly formules = computed(() => this.lignes(this.donnees()?.parFormule ?? []));
  readonly modules = computed(() => this.lignes(this.donnees()?.parModule ?? [], (cle) => LIBELLES_MODULES[cle] ?? cle,
    (cle) => PRESENTATION_MODULES[cle as CodeModule]?.icone));
  readonly pays = computed(() => this.lignes(this.donnees()?.parPays ?? []));
  readonly variantes = computed(() => this.lignes(this.donnees()?.parTypeActivite ?? [], (cle) => LIBELLES_VARIANTES[cle] ?? cle));
  readonly moyens = computed(() => this.lignes(this.donnees()?.parMoyen ?? [], (cle) => LIBELLES_MOYENS[cle] ?? cle));

  teinte(cle: string): string {
    return TEINTES_ETATS[cle as keyof typeof TEINTES_ETATS] ?? '';
  }

  /** Part des entreprises, en pourcentage entier. */
  part(n: number): number {
    const total = this.donnees()?.entreprises ?? 0;
    return total ? Math.round((n * 100) / total) : 0;
  }

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.tableauBord().subscribe({
      next: (d) => {
        this.donnees.set(d);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Tableau de bord indisponible.');
      }
    });
  }

  private evolution(courant: number, precedent: number): { texte: string; sens: 'hausse' | 'baisse' | 'stable' } {
    if (!precedent) {
      return { texte: courant ? 'nouveau ce mois' : 'aucun le mois dernier', sens: courant ? 'hausse' : 'stable' };
    }
    const variation = Math.round(((courant - precedent) * 100) / precedent);
    return {
      texte: `${variation > 0 ? '+' : ''}${variation} % vs mois dernier`,
      sens: variation > 0 ? 'hausse' : variation < 0 ? 'baisse' : 'stable'
    };
  }

  private lignes(points: PointSerie[], libelle: (cle: string) => string = (c) => c,
                 icone?: (cle: string) => string | undefined): Ligne[] {
    const max = Math.max(1, ...points.map((p) => p.valeur));
    return points.map((p) => ({
      cle: p.cle,
      libelle: libelle(p.libelle),
      valeur: p.valeur,
      part: Math.round((p.valeur * 100) / max),
      icone: icone?.(p.cle)
    }));
  }
}
