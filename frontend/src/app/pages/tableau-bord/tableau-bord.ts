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
    this.rentabiliteService.finances().subscribe({
      next: (donnees) => this.finances.set(donnees)
    });
    this.pilotageService.evolution(6).subscribe({
      next: (serie) => this.evolution.set(serie)
    });
    const fin = new Date().toISOString().slice(0, 10);
    const debut = new Date(new Date().getFullYear(), new Date().getMonth(), 1)
      .toISOString()
      .slice(0, 10);
    this.depenseService.repartition(debut, fin).subscribe({
      next: (lignes) => this.depensesParCategorie.set(lignes)
    });
    this.rentabiliteService.global(debut, fin).subscribe({
      next: (donnees) => this.parCamion.set(donnees.camions)
    });
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
}
