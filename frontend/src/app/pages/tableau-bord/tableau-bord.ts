import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, LOCALE_ID, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LigneCategorieDepense,
  LigneRentabilite,
  TableauBordFinances
} from '../../core/models/finance';
import {
  COULEUR_DEPENSES,
  COULEUR_RECETTES,
  EvolutionMensuelle
} from '../../core/models/pilotage';
import { DepenseService } from '../../core/services/depense.service';
import { MissionService } from '../../core/services/mission.service';
import { Mission } from '../../core/models/exploitation';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { TableauBordParc } from '../../core/models/models';
import { PilotageService } from '../../core/services/pilotage.service';
import { TableauBordService } from '../../core/services/tableau-bord.service';
import { grosMontant } from '../../shared/montant';

@Component({
  selector: 'app-tableau-bord',
  imports: [RouterLink, DecimalPipe, NgTemplateOutlet],
  templateUrl: './tableau-bord.html',
  styleUrl: './tableau-bord.scss'
})
export class TableauBord implements OnInit {
  private readonly service = inject(TableauBordService);
  private readonly rentabiliteService = inject(RentabiliteService);
  private readonly depenseService = inject(DepenseService);
  private readonly pilotageService = inject(PilotageService);
  private readonly missionService = inject(MissionService);
  private readonly locale = inject(LOCALE_ID);

  readonly indicateurs = signal<TableauBordParc | null>(null);
  readonly finances = signal<TableauBordFinances | null>(null);
  readonly evolution = signal<EvolutionMensuelle[]>([]);
  readonly depensesParCategorie = signal<LigneCategorieDepense[]>([]);
  /** Recettes, depenses et kilometres de chaque camion sur le mois en cours. */
  readonly parCamion = signal<LigneRentabilite[]>([]);
  /** Cinq missions les plus recentes, toutes periodes confondues. */
  readonly dernieresMissions = signal<Mission[]>([]);

  readonly couleurRecettes = COULEUR_RECETTES;
  readonly couleurDepenses = COULEUR_DEPENSES;
  readonly couleurKilometres = '#22c3a6';

  /** Etendue des donnees observees ; tout l'ecran financier la suit. */
  readonly portees: { valeur: Portee; libelle: string; detail: string }[] = [
    { valeur: 'MOIS', libelle: 'Mois', detail: 'Mois en cours' },
    { valeur: 'TRIMESTRE', libelle: 'Trimestre', detail: 'Trimestre en cours' },
    { valeur: 'SEMESTRE', libelle: 'Semestre', detail: 'Semestre en cours' },
    { valeur: 'TOUTES', libelle: 'Toutes', detail: 'Depuis le debut' }
  ];

  readonly portee = signal<Portee>('MOIS');

  /** Bornes envoyees a l'API pour la portee choisie. */
  readonly intervalle = computed(() => {
    const aujourdhui = new Date();
    const annee = aujourdhui.getFullYear();
    const mois = aujourdhui.getMonth();
    const debut = {
      MOIS: new Date(annee, mois, 1),
      TRIMESTRE: new Date(annee, Math.floor(mois / 3) * 3, 1),
      SEMESTRE: new Date(annee, mois < 6 ? 0 : 6, 1),
      // Aucune donnee n'est anterieure : cette borne couvre tout l'historique.
      TOUTES: new Date(2000, 0, 1)
    }[this.portee()];
    return { debut: this.iso(debut), fin: this.iso(aujourdhui) };
  });

  /** Intitule de la periode, repris par les titres des graphiques. */
  readonly libellePeriode = computed(
    () => this.portees.find((p) => p.valeur === this.portee())!.detail
  );

  /** La serie mensuelle s'allonge quand on demande tout l'historique. */
  readonly moisEvolution = computed(() => (this.portee() === 'TOUTES' ? 12 : 6));

  readonly totalDepensesCategories = computed(() =>
    this.depensesParCategorie().reduce((cumul, ligne) => cumul + ligne.total, 0)
  );

  /** Camions classes du plus gros chiffre d'affaires au plus faible. */
  readonly financesParCamion = computed(() =>
    [...this.parCamion()].sort((a, b) => b.recettes - a.recettes)
  );

  /** Les deux series partagent l'echelle du plus gros montant, tous camions confondus. */
  readonly maximumParCamion = computed(() =>
    Math.max(...this.parCamion().flatMap((ligne) => [ligne.recettes, ligne.depenses]), 1)
  );

  /** Camions classes du plus roulant au moins roulant. */
  readonly kilometresParCamion = computed(() =>
    [...this.parCamion()].sort((a, b) => b.distanceKm - a.distanceKm)
  );

  readonly maximumKilometres = computed(() =>
    Math.max(...this.parCamion().map((ligne) => ligne.distanceKm), 1)
  );

  readonly totalKilometres = computed(() =>
    this.parCamion().reduce((cumul, ligne) => cumul + ligne.distanceKm, 0)
  );

  /* ------------------------- Graphiques de la charte ------------------------ */

  /** Couleurs fixes du donut, dans l'ordre des categories. */
  readonly paletteDonut = ['#ff8c1a', '#2f6fd6', '#7c9a3c', '#1f6b52', '#22c3a6', '#a855f7', '#d9b24a'];

  /**
   * Repartition des depenses en anneau : chaque arc est un trait de cercle
   * decale de la somme des arcs precedents.
   */
  readonly arcsDonut = computed(() => {
    const total = this.totalDepensesCategories();
    let cumul = 0;
    return this.depensesParCategorie().map((ligne, i) => {
      const part = total === 0 ? 0 : ligne.total / total;
      const arc = {
        libelle: ligne.libelle,
        total: ligne.total,
        pourcent: Math.round(part * 100),
        couleur: this.paletteDonut[i % this.paletteDonut.length],
        longueur: Math.max(part * CIRCONFERENCE - ECART_ARC, 0),
        decalage: -cumul * CIRCONFERENCE
      };
      cumul += part;
      return arc;
    });
  });

  readonly circonference = CIRCONFERENCE;

  /** Barres empilees : la depense en socle sombre, la marge par-dessus. */
  readonly barresEmpilees = computed(() => {
    const maximum = Math.max(
      ...this.evolution().map((p) => Math.max(p.recettes, p.depenses)),
      1
    );
    return this.evolution().map((point) => ({
      point,
      socle: (point.depenses / maximum) * 100,
      marge: (Math.max(point.resultat, 0) / maximum) * 100
    }));
  });

  readonly graduationsBarres = computed(() => {
    const maximum = Math.max(...this.evolution().map((p) => Math.max(p.recettes, p.depenses)), 0);
    return [4, 3, 2, 1, 0].map((n) => (maximum * n) / 4);
  });

  /** Courbes recettes, depenses, resultat sur un seul axe, zero compris. */
  readonly courbes = computed(() => {
    const serie = this.evolution();
    const valeurs = serie.flatMap((p) => [p.recettes, p.depenses, p.resultat]);
    const haut = Math.max(...valeurs, 1);
    const bas = Math.min(...valeurs, 0);
    const largeurUtile = LARGEUR_COURBES - MARGE_GAUCHE - 16;
    const hauteurUtile = HAUTEUR_COURBES - 30;
    const x = (i: number) =>
      MARGE_GAUCHE + (serie.length < 2 ? largeurUtile / 2 : (i * largeurUtile) / (serie.length - 1));
    const y = (v: number) => 10 + ((haut - v) / (haut - bas)) * hauteurUtile;
    const trace = (cle: 'recettes' | 'depenses' | 'resultat', couleur: string, libelle: string) => ({
      libelle,
      couleur,
      points: serie.map((p, i) => ({ x: x(i), y: y(p[cle]), valeur: p[cle], mois: p.moisLibelle }))
    });
    return {
      series: [
        trace('recettes', '#22a27a', 'Recettes'),
        trace('depenses', '#e8a33d', 'Depenses'),
        trace('resultat', '#2f6fd6', 'Resultat')
      ],
      graduations: [0, 1, 2, 3, 4].map((n) => {
        const valeur = haut - ((haut - bas) * n) / 4;
        return { valeur, y: y(valeur) };
      }),
      mois: serie.map((p, i) => ({ libelle: p.moisLibelle, x: x(i) })),
      zero: y(0)
    };
  });

  readonly largeurCourbes = LARGEUR_COURBES;
  readonly hauteurCourbes = HAUTEUR_COURBES;

  /** Repartition des camions par grand etat, pour la pastille de parc. */
  readonly etatsParc = computed(() => {
    const k = this.indicateurs();
    if (!k) {
      return [];
    }
    return [
      { libelle: 'Disponibles', valeur: k.camionsDisponibles, couleur: '#22c3a6' },
      { libelle: 'En mission', valeur: k.camionsEnMission, couleur: '#2f6fd6' },
      { libelle: 'En location', valeur: k.camionsEnLocation, couleur: '#7c9a3c' },
      { libelle: 'Maintenance', valeur: k.camionsEnMaintenance, couleur: '#ff8c1a' },
      { libelle: 'Immobilises', valeur: k.camionsImmobilises, couleur: '#d94a4a' }
    ];
  });

  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  /** Montant des cartes de tete, lisible en grand corps. */
  grosMontant(valeur: number): string {
    return grosMontant(valeur, this.locale);
  }

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.parc().subscribe({
      next: (donnees) => {
        this.indicateurs.set(donnees);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Impossible de charger les indicateurs du parc.');
      }
    });
    const { debut, fin } = this.intervalle();
    this.rentabiliteService.finances(debut, fin).subscribe({
      next: (donnees) => this.finances.set(donnees)
    });
    this.pilotageService.evolution(this.moisEvolution()).subscribe({
      next: (serie) => this.evolution.set(serie)
    });
    this.depenseService.repartition(debut, fin).subscribe({
      next: (lignes) => this.depensesParCategorie.set(lignes)
    });
    this.rentabiliteService.global(debut, fin).subscribe({
      next: (donnees) => this.parCamion.set(donnees.camions)
    });
    this.missionService.lister({ size: 5 }).subscribe({
      next: (page) => this.dernieresMissions.set(page.content)
    });
  }

  /** Change l'etendue observee et recharge tout ce qui en depend. */
  changerPortee(portee: Portee): void {
    if (this.portee() === portee) {
      return;
    }
    this.portee.set(portee);
    this.charger();
  }

  /**
   * Hauteur de colonne, en pourcentage du plus gros montant de la comparaison.
   * Une valeur nulle ne dessine rien ; une valeur faible garde un trait visible.
   */
  hauteurMontant(valeur: number): number {
    return this.part(valeur, this.maximumParCamion());
  }

  hauteurKilometres(valeur: number): number {
    return this.part(valeur, this.maximumKilometres());
  }

  /** Part d'une valeur dans un maximum, en pourcentage : barres des graphes et jauges des cartes. */
  part(valeur: number, maximum: number): number {
    if (valeur <= 0) {
      return 0;
    }
    return Math.max(1, Math.round((valeur / maximum) * 100));
  }

  tauxDisponibilite(): number {
    const donnees = this.indicateurs();
    if (!donnees || donnees.totalCamions === 0) {
      return 0;
    }
    return Math.round((donnees.camionsDisponibles / donnees.totalCamions) * 100);
  }

  /** Montant abrege pour les axes : 1,2 M, 350 k. */
  abrege(valeur: number): string {
    const absolu = Math.abs(valeur);
    if (absolu >= 1_000_000) {
      return `${(valeur / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} M`;
    }
    if (absolu >= 1_000) {
      return `${Math.round(valeur / 1_000)} k`;
    }
    return `${Math.round(valeur)}`;
  }

  /** Initiales du chauffeur, a defaut de photo. */
  initiales(nom?: string): string {
    return (nom ?? '?')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((mot) => mot[0].toUpperCase())
      .join('');
  }

  points(serie: { points: { x: number; y: number }[] }): string {
    return serie.points.map((p) => `${p.x},${p.y}`).join(' ');
  }

  /** Date locale au format ISO : toISOString decalerait d'un jour. */
  private iso(date: Date): string {
    const mois = `${date.getMonth() + 1}`.padStart(2, '0');
    const jour = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${mois}-${jour}`;
  }
}

/** Geometrie fixe des graphiques SVG ; le navigateur les met a l'echelle. */
const RAYON_DONUT = 60;
const CIRCONFERENCE = 2 * Math.PI * RAYON_DONUT;
/** Fin espace blanc entre deux arcs du donut. */
const ECART_ARC = 3;
/** Le graphe d'evolution occupe la plus large colonne de sa rangee : un cadre large garde un texte lisible. */
const LARGEUR_COURBES = 820;
const HAUTEUR_COURBES = 260;
const MARGE_GAUCHE = 44;

/** Etendue des donnees observees sur le tableau de bord. */
type Portee = 'MOIS' | 'TRIMESTRE' | 'SEMESTRE' | 'TOUTES';
