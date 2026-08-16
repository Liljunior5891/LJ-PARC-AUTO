import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  FicheSynthese,
  Kpi,
  Notification,
  RapportDisponible
} from '../../core/models/pilotage';
import { Camion } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { PilotageService } from '../../core/services/pilotage.service';

@Component({
  selector: 'app-direction',
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './direction.html',
  styleUrl: './direction.scss'
})
export class Direction implements OnInit {
  private readonly service = inject(PilotageService);
  private readonly camionService = inject(CamionService);

  readonly kpi = signal<Kpi | null>(null);
  readonly notifications = signal<Notification[]>([]);
  readonly rapports = signal<RapportDisponible[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly fiche = signal<FicheSynthese | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  debut = this.premierJourDuMois();
  fin = this.aujourdhui();
  camionSynthese?: number;

  readonly critiques = computed(() =>
    this.notifications().filter((n) => n.niveau === 'CRITIQUE')
  );

  ngOnInit(): void {
    this.charger();
    this.service.rapports().subscribe({ next: (rapports) => this.rapports.set(rapports) });
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service.kpi(this.debut, this.fin).subscribe({
      next: (kpi) => {
        this.kpi.set(kpi);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des indicateurs impossible.');
      }
    });
    this.service.notifications().subscribe({
      next: (notifications) => this.notifications.set(notifications)
    });
    if (this.camionSynthese) {
      this.afficherSynthese();
    }
  }

  afficherSynthese(): void {
    if (!this.camionSynthese) {
      this.fiche.set(null);
      return;
    }
    this.service.synthese(this.camionSynthese, this.debut, this.fin).subscribe({
      next: (fiche) => this.fiche.set(fiche),
      error: () => this.erreur.set('Chargement de la fiche synthetique impossible.')
    });
  }

  /** Telecharge le rapport et propose son enregistrement. */
  exporter(type: RapportDisponible): void {
    this.erreur.set(null);
    this.service.telechargerRapport(type.valeur, this.debut, this.fin).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const lien = document.createElement('a');
        lien.href = url;
        lien.download = `${type.valeur.toLowerCase()}_${this.debut}_${this.fin}.csv`;
        lien.click();
        URL.revokeObjectURL(url);
        this.succes.set(`${type.libelle} exporte.`);
      },
      error: () => this.erreur.set("Export impossible.")
    });
  }

  classeNiveau(notification: Notification): string {
    return notification.niveau === 'CRITIQUE' ? 'etiquette vendu'
      : notification.niveau === 'AVERTISSEMENT' ? 'etiquette en_location' : 'etiquette';
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
