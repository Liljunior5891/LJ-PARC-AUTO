import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FichePerformance, RentabiliteGlobale } from '../../core/models/finance';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { Modale } from '../../shared/modale';
import { Periode, aujourdhui, debutPeriode, estPeriode } from '../../shared/periode';

/** Seuils de lecture de la marge d'un camion : en dessous, la pastille vire a l'orange puis au rouge. */
const MARGE_CONFORTABLE = 30;
const MARGE_FAIBLE = 10;

@Component({
  selector: 'app-rentabilite',
  imports: [FormsModule, DecimalPipe, DatePipe, Modale],
  templateUrl: './rentabilite.html',
  styleUrl: './rentabilite.scss'
})
export class Rentabilite implements OnInit {
  private readonly service = inject(RentabiliteService);

  readonly global = signal<RentabiliteGlobale | null>(null);
  readonly fiche = signal<FichePerformance | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  debut = debutPeriode('mois');
  fin = aujourdhui();

  /**
   * Echelle commune des barres recettes et depenses : le plus gros montant du
   * tableau, toutes colonnes confondues, pour que les longueurs se comparent.
   */
  readonly echelle = computed(() =>
    Math.max(1, ...(this.global()?.camions ?? []).flatMap((c) => [c.recettes, c.depenses]))
  );

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.global(this.debut, this.fin).subscribe({
      next: (donnees) => {
        this.global.set(donnees);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement de la rentabilite impossible.');
      }
    });
    // La fiche ouverte est rechargee sur la nouvelle periode.
    const ouverte = this.fiche();
    if (ouverte) {
      this.ouvrirFiche(ouverte.camionId);
    }
  }

  choisirPeriode(periode: Periode): void {
    this.debut = debutPeriode(periode);
    this.fin = aujourdhui();
    this.charger();
  }

  estPeriode(periode: Periode): boolean {
    return estPeriode(periode, this.debut, this.fin);
  }

  ouvrirFiche(camionId: number): void {
    this.service.camion(camionId, this.debut, this.fin).subscribe({
      next: (fiche) => this.fiche.set(fiche),
      error: () => this.erreur.set('Chargement de la fiche performance impossible.')
    });
  }

  fermerFiche(): void {
    this.fiche.set(null);
  }

  /** Longueur d'une barre du tableau, sur l'echelle commune. */
  largeur(montant: number): number {
    return montant <= 0 ? 0 : Math.max(2, (montant / this.echelle()) * 100);
  }

  /** Part d'un montant dans son total, pour les barres de repartition de la fiche. */
  part(montant: number, total: number): number {
    return total <= 0 ? 0 : Math.max(2, (montant / total) * 100);
  }

  /** Pourcentage ramene entre 0 et 100, pour les jauges. */
  borne(valeur: number): number {
    return Math.min(100, Math.max(0, valeur));
  }

  niveauMarge(marge: number): string {
    if (marge >= MARGE_CONFORTABLE) {
      return 'bonne';
    }
    return marge >= MARGE_FAIBLE ? 'moyenne' : 'faible';
  }
}
