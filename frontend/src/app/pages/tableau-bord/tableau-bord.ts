import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TableauBordExploitation } from '../../core/models/exploitation';
import { LigneCategorieDepense, TableauBordFinances } from '../../core/models/finance';
import {
  COULEUR_DEPENSES,
  COULEUR_RECETTES,
  EvolutionMensuelle,
  PALETTE_CATEGORIELLE
} from '../../core/models/pilotage';
import { DepenseService } from '../../core/services/depense.service';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { STATUTS_CAMION, StatutCamion, TableauBordParc } from '../../core/models/models';
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
  readonly exploitation = signal<TableauBordExploitation | null>(null);
  readonly finances = signal<TableauBordFinances | null>(null);
  readonly evolution = signal<EvolutionMensuelle[]>([]);
  readonly depensesParCategorie = signal<LigneCategorieDepense[]>([]);

  readonly couleurRecettes = COULEUR_RECETTES;
  readonly couleurDepenses = COULEUR_DEPENSES;

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
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  readonly statuts = STATUTS_CAMION;

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
    this.service.exploitation().subscribe({
      next: (donnees) => this.exploitation.set(donnees)
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

  nombreParStatut(statut: StatutCamion): number {
    return this.indicateurs()?.repartitionStatuts?.[statut] ?? 0;
  }

  /** Part du statut dans le parc actif, en pourcentage. */
  partParStatut(statut: StatutCamion): number {
    const total = this.indicateurs()?.totalCamions ?? 0;
    return total === 0 ? 0 : Math.round((this.nombreParStatut(statut) / total) * 100);
  }

  tauxDisponibilite(): number {
    const donnees = this.indicateurs();
    if (!donnees || donnees.totalCamions === 0) {
      return 0;
    }
    return Math.round((donnees.camionsDisponibles / donnees.totalCamions) * 100);
  }
}
