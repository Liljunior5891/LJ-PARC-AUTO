import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SyntheseCarburant } from '../../core/models/finance';
import { Camion } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { CarburantService } from '../../core/services/carburant.service';

@Component({
  selector: 'app-carburant',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './carburant.html'
})
export class Carburant implements OnInit {
  private readonly service = inject(CarburantService);
  private readonly camionService = inject(CamionService);

  readonly camions = signal<Camion[]>([]);
  readonly synthese = signal<SyntheseCarburant | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  camionId?: number;
  debut = this.premierJourDeLAnnee();
  fin = this.aujourdhui();

  ngOnInit(): void {
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => {
        this.camions.set(page.content);
        // Le premier camion du parc est selectionne par defaut.
        if (!this.camionId && page.content.length > 0) {
          this.camionId = page.content[0].id;
          this.charger();
        }
      }
    });
  }

  charger(): void {
    if (!this.camionId) {
      this.synthese.set(null);
      return;
    }
    this.chargement.set(true);
    this.service.synthese(this.camionId, this.debut, this.fin).subscribe({
      next: (synthese) => {
        this.synthese.set(synthese);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement de la synthese carburant impossible.');
      }
    });
  }

  /**
   * Hauteur relative de la barre de consommation, pour comparer les pleins entre eux.
   * La consommation la plus elevee de la periode sert de reference.
   */
  hauteurBarre(consommation?: number): number {
    const valeurs = (this.synthese()?.pleins ?? [])
      .map((plein) => plein.consommationL100km)
      .filter((valeur): valeur is number => valeur !== undefined && valeur !== null);
    const maximum = Math.max(...valeurs, 0);
    if (!consommation || maximum === 0) {
      return 0;
    }
    return Math.round((consommation / maximum) * 100);
  }

  private aujourdhui(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private premierJourDeLAnnee(): string {
    return new Date(new Date().getFullYear(), 0, 1).toISOString().slice(0, 10);
  }
}
