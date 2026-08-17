import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LigneCategorieDepense,
  LigneRentabilite,
  TableauBordFinances
} from '../../core/models/finance';
import {
  COULEUR_DEPENSES,
  COULEUR_RECETTES,
  EvolutionMensuelle,
  PALETTE_CATEGORIELLE
} from '../../core/models/pilotage';
import { DepenseService } from '../../core/services/depense.service';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { TableauBordParc } from '../../core/models/models';
import { PilotageService } from '../../core/services/pilotage.service';
import { TableauBordService } from '../../core/services/tableau-bord.service';

@Component({
  selector: 'app-tableau-bord',
  imports: [RouterLink, DecimalPipe],
  templateUrl: './tableau-bord.html',
  styleUrl: './tableau-bord.scss'
})
export class TableauBord implements OnInit {
  private readonly service = inject(TableauBordService);
  private readonly rentabiliteService = inject(RentabiliteService);
  private readonly depenseService = inject(DepenseService);
  private readonly pilotageService = inject(PilotageService);

  readonly indicateurs = signal<TableauBordParc | null>(null);
  readonly finances = signal<TableauBordFinances | null>(null);
  readonly evolution = signal<EvolutionMensuelle[]>([]);
  readonly depensesParCategorie = signal<LigneCategorieDepense[]>([]);
  /** Recettes, depenses et kilometres de chaque camion sur le mois en cours. */
  readonly parCamion = signal<LigneRentabilite[]>([]);

  readonly couleurRecettes = COULEUR_RECETTES;
  readonly couleurDepenses = COULEUR_DEPENSES;
  readonly couleurKilometres = PALETTE_CATEGORIELLE[0];

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

  /** Echelle commune aux deux series : un seul axe, jamais deux. */
  readonly maximumEvolution = computed(() =>
    Math.max(
      ...this.evolution().flatMap((point) => [point.recettes, point.depenses]),
      1
    )
  );

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

  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

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

  private part(valeur: number, maximum: number): number {
    if (valeur <= 0) {
      return 0;
    }
    return Math.max(1, Math.round((valeur / maximum) * 100));
  }

  /** Hauteur de barre, en pourcentage de la valeur maximale de la serie. */
  hauteur(valeur: number): number {
    return Math.round((valeur / this.maximumEvolution()) * 100);
  }

  /** Couleur d'une categorie, fixee par son rang dans la liste. */
  couleurCategorie(index: number): string {
    return PALETTE_CATEGORIELLE[index % PALETTE_CATEGORIELLE.length];
  }

  partCategorie(ligne: LigneCategorieDepense): number {
    const total = this.totalDepensesCategories();
    return total === 0 ? 0 : Math.round((ligne.total / total) * 100);
  }

  tauxDisponibilite(): number {
    const donnees = this.indicateurs();
    if (!donnees || donnees.totalCamions === 0) {
      return 0;
    }
    return Math.round((donnees.camionsDisponibles / donnees.totalCamions) * 100);
  }

  /** Date locale au format ISO : toISOString decalerait d'un jour. */
  private iso(date: Date): string {
    const mois = `${date.getMonth() + 1}`.padStart(2, '0');
    const jour = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${mois}-${jour}`;
  }
}

/** Etendue des donnees observees sur le tableau de bord. */
type Portee = 'MOIS' | 'TRIMESTRE' | 'SEMESTRE' | 'TOUTES';
