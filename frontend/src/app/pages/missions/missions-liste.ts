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
import { Modale } from '../../shared/modale';
import { MissionFiche } from '../missions/mission-fiche';
import { OperationService } from '../../core/operation/operation.service';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';

@Component({
  selector: 'app-missions-liste',
  imports: [SuiteListe, FormsModule, RouterLink, DecimalPipe, DatePipe, Modale, MissionFiche],
  templateUrl: './missions-liste.html'
})
export class MissionsListe implements OnInit {
  private readonly service = inject(MissionService);
  private readonly operation = inject(OperationService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_MISSION;
  readonly types = TYPES_MISSION;
  readonly page = signal<Page<Mission> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  /** Fenetre de creation, ouverte par-dessus la liste. */
  readonly creation = signal(false);
  readonly succes = signal<string | null>(null);

  /** Mission dont le formulaire de cloture est ouvert. */
  /** Mission dont la fenetre de cloture est ouverte. */
  readonly clotureEnCours = signal<Mission | null>(null);
  kilometrageArrivee: number | null = null;
  recetteReelle: number | null = null;

  recherche = '';
  statut: StatutMission | '' = '';
  type: TypeMission | '' = '';
  private readonly suite = new SuiteDePages();

  readonly peutPiloter = this.auth.peutGererParc();
  readonly peutGerer = this.auth.peutGererParc();

  ngOnInit(): void {
    this.charger();
  }

  ouvrirCreation(): void {
    this.succes.set(null);
    this.creation.set(true);
  }

  /** Le nouvel enregistrement rejoint la liste, rechargee. */
  creationTerminee(): void {
    this.creation.set(false);
    this.succes.set('Mission enregistree.');
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({ q: this.recherche, statut: this.statut, type: this.type, page: this.suite.numero() })
      .subscribe({
        next: (page) => {
          this.page.set(cumulerPage(this.page(), page));
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
    this.charger();
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
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
    this.clotureEnCours.set(mission);
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

  async annuler(mission: Mission): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Annuler la mission ${mission.numero} ?`,
      message: 'Cette action ne peut pas etre defaite.',
      libelleAction: 'Annuler la mission',
      danger: true
    });
    if (!accepte) {
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
