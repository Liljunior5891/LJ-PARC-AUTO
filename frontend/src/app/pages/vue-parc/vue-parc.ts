import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LigneVueParc } from '../../core/models/exploitation';
import { StatutCamion } from '../../core/models/models';
import { TableauBordService } from '../../core/services/tableau-bord.service';

@Component({
  selector: 'app-vue-parc',
  imports: [FormsModule, RouterLink, DecimalPipe],
  templateUrl: './vue-parc.html'
})
export class VueParc implements OnInit {
  private readonly service = inject(TableauBordService);

  readonly lignes = signal<LigneVueParc[]>([]);
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

  classeStatut(statut: StatutCamion): string {
    return 'etiquette ' + statut.toLowerCase();
  }
}
