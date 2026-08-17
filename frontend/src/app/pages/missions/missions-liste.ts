import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  Mission,
  STATUTS_MISSION,
  StatutMission,
  TYPES_MISSION,
  TypeMission
} from '../../core/models/exploitation';
import { Page } from '../../core/models/models';
import { MissionService } from '../../core/services/mission.service';

@Component({
  selector: 'app-missions-liste',
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './missions-liste.html'
})
export class MissionsListe implements OnInit {
  private readonly service = inject(MissionService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_MISSION;
  readonly types = TYPES_MISSION;
  readonly page = signal<Page<Mission> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  /** Mission dont le formulaire de cloture est ouvert. */
  readonly clotureEnCours = signal<number | null>(null);
  kilometrageArrivee: number | null = null;
  recetteReelle: number | null = null;

  recherche = '';
  statut: StatutMission | '' = '';
  type: TypeMission | '' = '';
  private pageCourante = 0;

  readonly peutPiloter = this.auth.peutGererParc();
  readonly peutGerer = this.auth.peutGererParc();

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({ q: this.recherche, statut: this.statut, type: this.type, page: this.pageCourante })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des missions impossible.');
        }
      });
  }

  filtrer(): void {
    this.pageCourante = 0;
    this.charger();
  }

  changerPage(delta: number): void {
    const page = this.page();
    if (!page) {
      return;
    }
    const cible = page.number + delta;
    if (cible < 0 || cible >= page.totalPages) {
      return;
    }
    this.pageCourante = cible;
    this.charger();
  }

  demarrer(mission: Mission): void {
    this.erreur.set(null);
    this.service.demarrer(mission.id, null).subscribe({
      next: () => {
        this.succes.set(`Mission ${mission.numero} demarree.`);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Demarrage impossible.')
    });
  }

  ouvrirCloture(mission: Mission): void {
    this.kilometrageArrivee = mission.kilometrageDepart ?? null;
    this.recetteReelle = mission.recettePrevue ?? null;
    this.clotureEnCours.set(mission.id);
  }

  cloturer(mission: Mission): void {
    if (this.kilometrageArrivee === null) {
      this.erreur.set("Le compteur d'arrivee est obligatoire.");
      return;
    }
    this.erreur.set(null);
    this.service.cloturer(mission.id, this.kilometrageArrivee, this.recetteReelle).subscribe({
      next: () => {
        this.clotureEnCours.set(null);
        this.succes.set(`Mission ${mission.numero} cloturee.`);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Cloture impossible.')
    });
  }

  annuler(mission: Mission): void {
    if (!confirm(`Annuler la mission ${mission.numero} ?`)) {
      return;
    }
    this.service.annuler(mission.id).subscribe({
      next: () => this.charger(),
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Annulation impossible.')
    });
  }

  classeStatut(statut: StatutMission): string {
    const correspondances: Record<StatutMission, string> = {
      PLANIFIEE: 'etiquette',
      EN_COURS: 'etiquette en_mission',
      TERMINEE: 'etiquette disponible',
      ANNULEE: 'etiquette vendu'
    };
    return correspondances[statut];
  }
}
