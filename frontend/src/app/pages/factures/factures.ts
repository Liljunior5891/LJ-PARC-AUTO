import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Location, Mission } from '../../core/models/exploitation';
import {
  Creances,
  Facture,
  MODES_PAIEMENT,
  ModePaiement,
  STATUTS_FACTURE,
  StatutFacture
} from '../../core/models/finance';
import { Page } from '../../core/models/models';
import { FactureService } from '../../core/services/facture.service';
import { LocationService } from '../../core/services/location.service';
import { MissionService } from '../../core/services/mission.service';
import { Modale } from '../../shared/modale';
import { OperationService } from '../../core/operation/operation.service';
import { messageChampsManquants } from '../../shared/validation-formulaire';

@Component({
  selector: 'app-factures',
  imports: [FormsModule, DecimalPipe, Modale],
  templateUrl: './factures.html'
})
export class Factures implements OnInit {
  private readonly service = inject(FactureService);
  private readonly operation = inject(OperationService);
  private readonly locationService = inject(LocationService);
  private readonly missionService = inject(MissionService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_FACTURE;
  readonly modes = MODES_PAIEMENT;

  readonly page = signal<Page<Facture> | null>(null);
  readonly creances = signal<Creances | null>(null);
  readonly contrats = signal<Location[]>([]);
  readonly missions = signal<Mission[]>([]);
  readonly detail = signal<Facture | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly telechargement = signal(false);
  readonly succes = signal<string | null>(null);
  readonly generationVisible = signal(false);
  readonly paiementVisible = signal(false);

  recherche = '';
  statut: StatutFacture | '' = '';
  private pageCourante = 0;

  /** Source retenue pour la generation : contrat de location ou mission terminee. */
  sourceGeneration: 'contrat' | 'mission' = 'contrat';
  contratId?: number;
  missionId?: number;

  montantPaiement = 0;
  datePaiement = new Date().toISOString().slice(0, 10);
  modePaiement: ModePaiement = 'VIREMENT';
  referencePaiement = '';

  readonly peutGerer = this.auth.peutGererParc();

  ngOnInit(): void {
    this.charger();
    this.locationService.lister({ size: 100 }).subscribe({
      next: (page) => this.contrats.set(page.content)
    });
    this.missionService.lister({ statut: 'TERMINEE', size: 100 }).subscribe({
      next: (page) => this.missions.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({ q: this.recherche, statut: this.statut, page: this.pageCourante })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des factures impossible.');
        }
      });
    this.service.creances().subscribe({
      next: (creances) => this.creances.set(creances)
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

  ouvrir(facture: Facture): void {
    this.service.parId(facture.id).subscribe({
      next: (complete) => {
        this.detail.set(complete);
        this.paiementVisible.set(false);
        this.montantPaiement = complete.resteAPayer;
      },
      error: () => this.erreur.set('Facture introuvable.')
    });
  }

  fermerDetail(): void {
    this.detail.set(null);
    this.paiementVisible.set(false);
  }

  basculerGeneration(): void {
    this.generationVisible.update((visible) => !visible);
    this.erreur.set(null);
    this.succes.set(null);
  }

  generer(): void {
    this.erreur.set(null);
    const appel =
      this.sourceGeneration === 'contrat'
        ? this.contratId && this.service.depuisContrat(this.contratId)
        : this.missionId && this.service.depuisMission(this.missionId);
    if (!appel) {
      this.erreur.set('Selectionnez un contrat ou une mission.');
      return;
    }
    appel.subscribe({
      next: (facture) => {
        this.succes.set(`Facture ${facture.numero} generee en brouillon.`);
        this.generationVisible.set(false);
        this.charger();
        this.detail.set(facture);
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Generation impossible.')
    });
  }

  emettre(facture: Facture): void {
    this.erreur.set(null);
    this.service.emettre(facture.id).subscribe({
      next: (maj) => {
        this.detail.set(maj);
        this.succes.set(`Facture ${maj.numero} emise : les recettes sont enregistrees par vehicule.`);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Emission impossible.')
    });
  }

  payer(facture: Facture): void {
    this.erreur.set(null);
    const manquants = messageChampsManquants({
      Montant: this.montantPaiement || null,
      Date: this.datePaiement,
      Mode: this.modePaiement
    });
    if (manquants) {
      this.erreur.set(manquants);
      return;
    }
    this.service
      .payer(facture.id, {
        montant: this.montantPaiement,
        datePaiement: this.datePaiement,
        modePaiement: this.modePaiement,
        reference: this.referencePaiement
      })
      .subscribe({
        next: (maj) => {
          this.detail.set(maj);
          this.paiementVisible.set(false);
          this.referencePaiement = '';
          this.succes.set(`Reglement enregistre : ${maj.statutLibelle}.`);
          this.charger();
        },
        error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Reglement impossible.')
      });
  }

  async annuler(facture: Facture): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Annuler la facture ${facture.numero} ?`,
      message: 'Cette action ne peut pas etre defaite.',
      libelleAction: 'Annuler la facture',
      danger: true
    });
    if (!accepte) {
      return;
    }
    this.service.annuler(facture.id).subscribe({
      next: (maj) => {
        this.detail.set(maj);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Annulation impossible.')
    });
  }

  marquerRetards(): void {
    this.service.marquerRetards().subscribe({
      next: (nombre) => {
        this.succes.set(`${nombre} facture(s) basculee(s) en retard.`);
        this.charger();
      },
      error: () => this.erreur.set('Mise a jour des retards impossible.')
    });
  }

  /** Edite le PDF de la facture, celui qui est remis au client. */
  telechargerPdf(facture: Facture): void {
    if (this.telechargement()) {
      return;
    }
    this.telechargement.set(true);
    this.erreur.set(null);
    this.service.pdf(facture.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const lien = document.createElement('a');
        lien.href = url;
        lien.download = `facture_${facture.numero}.pdf`;
        lien.click();
        URL.revokeObjectURL(url);
        this.telechargement.set(false);
      },
      error: () => {
        this.telechargement.set(false);
        this.erreur.set("Edition du PDF impossible.");
      }
    });
  }

  classeStatut(statut: StatutFacture): string {
    const correspondances: Record<StatutFacture, string> = {
      BROUILLON: 'etiquette',
      NON_PAYEE: 'etiquette en_location',
      PARTIELLEMENT_PAYEE: 'etiquette en_mission',
      PAYEE: 'etiquette disponible',
      EN_RETARD: 'etiquette vendu',
      ANNULEE: 'etiquette vendu'
    };
    return correspondances[statut];
  }
}
