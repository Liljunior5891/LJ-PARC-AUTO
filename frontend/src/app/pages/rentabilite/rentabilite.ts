import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FichePerformance, LigneRentabilite, RentabiliteGlobale } from '../../core/models/finance';
import { RentabiliteService } from '../../core/services/rentabilite.service';

@Component({
  selector: 'app-rentabilite',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './rentabilite.html'
})
export class Rentabilite implements OnInit {
  private readonly service = inject(RentabiliteService);

  readonly global = signal<RentabiliteGlobale | null>(null);
  readonly fiche = signal<FichePerformance | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  debut = this.premierJourDuMois();
  fin = this.aujourdhui();

  /** Reference d'echelle pour les barres de comparaison des camions. */
  readonly recetteMax = computed(() =>
    Math.max(...(this.global()?.camions ?? []).map((c) => c.recettes), 0)
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

  ouvrirFiche(camionId: number): void {
    this.service.camion(camionId, this.debut, this.fin).subscribe({
      next: (fiche) => this.fiche.set(fiche),
      error: () => this.erreur.set('Chargement de la fiche performance impossible.')
    });
  }

  fermerFiche(): void {
    this.fiche.set(null);
  }

  /** Largeur de la barre de recettes, relative au camion le plus productif. */
  largeurRecette(ligne: LigneRentabilite): number {
    const maximum = this.recetteMax();
    return maximum === 0 ? 0 : Math.round((ligne.recettes / maximum) * 100);
  }

  classeResultat(valeur: number): string {
    return valeur >= 0 ? 'resultat-positif' : 'resultat-negatif';
  }

  private aujourdhui(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private premierJourDuMois(): string {
    const date = new Date();
    return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().slice(0, 10);
  }
}
