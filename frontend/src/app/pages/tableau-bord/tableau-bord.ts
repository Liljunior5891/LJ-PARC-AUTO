import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { STATUTS_CAMION, StatutCamion, TableauBordParc } from '../../core/models/models';
import { TableauBordService } from '../../core/services/tableau-bord.service';

@Component({
  selector: 'app-tableau-bord',
  imports: [RouterLink],
  templateUrl: './tableau-bord.html',
  styleUrl: './tableau-bord.scss'
})
export class TableauBord implements OnInit {
  private readonly service = inject(TableauBordService);

  readonly indicateurs = signal<TableauBordParc | null>(null);
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
