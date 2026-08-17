import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LigneVueParc } from '../../core/models/exploitation';
import { LigneRentabilite } from '../../core/models/finance';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { StatutCamion } from '../../core/models/models';
import { TableauBordService } from '../../core/services/tableau-bord.service';

@Component({
  selector: 'app-vue-parc',
  imports: [FormsModule, RouterLink, DecimalPipe],
  templateUrl: './vue-parc.html',
  styleUrl: './vue-parc.scss'
})
export class VueParc implements OnInit {
  private readonly service = inject(TableauBordService);
  private readonly rentabiliteService = inject(RentabiliteService);

  readonly lignes = signal<LigneVueParc[]>([]);
  /** Rentabilite du mois en cours, indexee par camion (chapitre 5). */
  readonly rentabilite = signal<Map<number, LigneRentabilite>>(new Map());
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  readonly filtre = signal<'tous' | 'engages' | 'disponibles'>('tous');

  /** Camions filtres selon leur engagement commercial. */
  readonly lignesAffichees = computed(() => {
    const filtre = this.filtre();
    return this.lignes().filter((ligne) => {
      if (filtre === 'engages') {
        return ligne.statut === 'EN_LOCATION' || ligne.statut === 'EN_MISSION'
          || ligne.statut === 'EN_LIVRAISON';
      }
      if (filtre === 'disponibles') {
        return ligne.statut === 'DISPONIBLE';
      }
      return true;
    });
  });

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.rentabiliteService.global().subscribe({
      next: (global) =>
        this.rentabilite.set(new Map(global.camions.map((ligne) => [ligne.camionId, ligne])))
    });
    this.service.vueParc().subscribe({
      next: (lignes) => {
        this.lignes.set(lignes);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement de la vue du parc impossible.');
      }
    });
  }

  changerFiltre(valeur: string): void {
    this.filtre.set(valeur as 'tous' | 'engages' | 'disponibles');
  }

  /** Ligne de rentabilite du camion sur le mois en cours, si elle existe. */
  finances(camionId: number): LigneRentabilite | undefined {
    return this.rentabilite().get(camionId);
  }

  classeResultat(valeur?: number): string {
    if (valeur === undefined) {
      return '';
    }
    return valeur >= 0 ? 'resultat-positif' : 'resultat-negatif';
  }

  classeStatut(statut: StatutCamion): string {
    return 'etiquette ' + statut.toLowerCase();
  }

  /**
   * Couleur du bloc selon le statut du camion : c'est elle qui rend la vue
   * lisible d'un coup d'oeil. Elle reprend la legende de la carte du parc.
   */
  couleurStatut(statut: StatutCamion): string {
    const couleurs: Record<StatutCamion, string> = {
      DISPONIBLE: '#1e874b',
      EN_MISSION: '#1565c0',
      EN_LIVRAISON: '#1565c0',
      EN_LOCATION: '#e07b00',
      EN_MAINTENANCE: '#8455c8',
      IMMOBILISE: '#c62828',
      ACCIDENTE: '#c62828',
      HORS_SERVICE: '#6b7684',
      VENDU: '#6b7684'
    };
    return couleurs[statut];
  }
}
