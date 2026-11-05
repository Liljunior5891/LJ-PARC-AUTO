import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ClientParc } from '../../core/models/exploitation';
import {
  LigneTypeRecette,
  Recette,
  TYPES_RECETTE,
  TypeRecette
} from '../../core/models/finance';
import { Camion, Page } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ClientService } from '../../core/services/client.service';
import { RecetteService } from '../../core/services/recette.service';
import { FactureService } from '../../core/services/facture.service';
import { FormulaireRecette } from '../../shared/formulaire-recette';
import { Modale } from '../../shared/modale';
import { OperationService } from '../../core/operation/operation.service';

import { ActivatedRoute } from '@angular/router';
import { DemandeLocationEnLigne } from '../../core/models/marketplace';
import { MarketplaceService } from '../../core/services/marketplace.service';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';
@Component({
  selector: 'app-recettes',
  imports: [SuiteListe, FormsModule, DecimalPipe, FormulaireRecette, Modale],
  templateUrl: './recettes.html'
})
export class Recettes implements OnInit {
  private readonly service = inject(RecetteService);
  private readonly operation = inject(OperationService);
  private readonly camionService = inject(CamionService);
  private readonly clientService = inject(ClientService);
  private readonly factureService = inject(FactureService);
  private readonly auth = inject(AuthService);

  readonly types = TYPES_RECETTE;

  readonly page = signal<Page<Recette> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly clients = signal<ClientParc[]>([]);
  readonly repartition = signal<LigneTypeRecette[]>([]);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);
  /** Recette venant d'etre enregistree, pour laquelle une facture est proposee. */
  readonly recetteAFacturer = signal<Recette | null>(null);

  /** Total encaisse sur la periode consultee. */
  readonly totalPeriode = computed(() =>
    this.repartition().reduce((cumul, ligne) => cumul + ligne.total, 0)
  );

  recherche = '';
  type: TypeRecette | '' = '';
  /** Gestion de parc avec Marketplace : canal de chaque element, en ligne ou agence. */
  readonly avecEnLigne = this.auth.aModule('MARKETPLACE');
  canal: '' | 'en-ligne' | 'agence' = '';

  camionId?: number;
  debut = this.premierJourDuMois();
  fin = this.aujourdhui();
  private readonly suite = new SuiteDePages();

  readonly peutGerer = this.auth.peutGererParc();


  /** Demande en ligne acceptee dont le client se presente : la recette la conclut. */
  readonly demandeAConclure = signal<DemandeLocationEnLigne | null>(null);
  private readonly route = inject(ActivatedRoute);
  private readonly marketplace = inject(MarketplaceService);

  ngOnInit(): void {
    this.charger();
    const demande = Number(this.route.snapshot.queryParamMap.get('demande'));
    if (demande) {
      this.marketplace.demande(demande).subscribe({
        next: (d) => {
          if (d.statut !== 'ACCEPTEE') {
            this.erreur.set(`La demande ${d.numero} est ${d.statutLibelle.toLowerCase()} : elle ne peut plus etre conclue.`);
            return;
          }
          this.demandeAConclure.set(d);
          this.formulaireVisible.set(true);
        },
        error: () => this.erreur.set('Demande en ligne introuvable.')
      });
    }
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.clientService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.clients.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche,
        type: this.type,
        camionId: this.camionId,
        debut: this.debut,
        fin: this.fin,
        enLigne: this.canal === '' ? undefined : this.canal === 'en-ligne',
        page: this.suite.numero()
      })
      .subscribe({
        next: (page) => {
          this.page.set(cumulerPage(this.page(), page));
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des recettes impossible.');
        }
      });
    this.service.repartition(this.debut, this.fin, this.camionId).subscribe({
      next: (lignes) => this.repartition.set(lignes)
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

  basculerFormulaire(): void {
    this.formulaireVisible.update((visible) => !visible);
    if (!this.formulaireVisible()) {
      this.demandeAConclure.set(null);
    }
    this.erreur.set(null);
    this.succes.set(null);
  }

  /**
   * Le formulaire partage a enregistre la recette : il reste a rafraichir la
   * liste et a proposer la facture, ce que l'ecran seul sait faire.
   */
  /** Derniere recette enregistree : son contrat est propose a l'impression. */
  readonly recetteAImprimer = signal<Recette | null>(null);

  imprimerContrat(recette: Recette): void {
    this.service.ouvrirContrat(recette, (message) => this.erreur.set(message));
  }

  recetteEnregistree(recette: Recette): void {
    this.succes.set(`Recette ${recette.numero} enregistree.`);
    this.recetteAImprimer.set(recette);
    if (this.demandeAConclure()) {
      this.succes.set(`Recette ${recette.numero} enregistree : la demande ${this.demandeAConclure()!.numero} est conclue.`);
      this.demandeAConclure.set(null);
    }
    this.formulaireVisible.set(false);
    // La facture n'est proposee que si la recette a un client a qui la remettre.
    // La facture est desormais emise avec la recette : on propose de la telecharger.
    this.recetteAFacturer.set(recette.factureId ? recette : null);
    this.charger();
  }

  async supprimer(recette: Recette): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Supprimer la recette ${recette.numero} ?`,
      message: `${recette.montant.toLocaleString('fr-FR')} FCFA seront retires des comptes du vehicule.`,
      libelleAction: 'Supprimer',
      danger: true
    });
    if (!accepte) {
      return;
    }
    this.service.supprimer(recette.id).subscribe({
      next: () => this.charger(),
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Suppression impossible.')
    });
  }

  /**
   * Genere la facture correspondant a la recette, puis propose son PDF.
   * La facture reste en brouillon : elle n'est exigible qu'une fois emise.
   */
  /** Telecharge le PDF de la facture emise avec la recette. */
  telechargerFacture(recette: Recette): void {
    if (!recette.factureId) {
      return;
    }
    this.factureService.pdf(recette.factureId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const lien = document.createElement('a');
        lien.href = url;
        lien.download = `facture_${recette.factureNumero}.pdf`;
        lien.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.erreur.set('Telechargement du PDF impossible.')
    });
  }

  fermerProposition(): void {
    this.recetteAFacturer.set(null);
  }

  /** Part d'un type dans le total de la periode, pour la barre de repartition. */
  part(ligne: LigneTypeRecette): number {
    const total = this.totalPeriode();
    return total === 0 ? 0 : Math.round((ligne.total / total) * 100);
  }

  private aujourdhui(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private premierJourDuMois(): string {
    const date = new Date();
    return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().slice(0, 10);
  }
}
