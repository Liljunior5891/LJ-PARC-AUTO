import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Fournisseur, TotauxFournisseurs } from '../../../core/models/achats';
import { Page } from '../../../core/models/models';
import { OperationService } from '../../../core/operation/operation.service';
import { AchatsService } from '../../../core/services/achats.service';
import { CompteFournisseurPdv } from '../../../shared/compte-fournisseur-pdv';
import { FormulaireFournisseurPdv } from '../../../shared/formulaire-fournisseur-pdv';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Fournisseurs du point de vente : fiches, ce qu'on leur doit et les
 * echeances depassees. Le bouton Compte ouvre livraisons a payer et paiements.
 */
@Component({
  selector: 'app-fournisseurs-pdv',
  imports: [SuiteListe, FormsModule, DecimalPipe, DatePipe, FormulaireFournisseurPdv, CompteFournisseurPdv],
  templateUrl: './fournisseurs.html'
})
export class FournisseursPdv implements OnInit {
  private readonly service = inject(AchatsService);
  private readonly operation = inject(OperationService);

  readonly page = signal<Page<Fournisseur> | null>(null);
  readonly totaux = signal<TotauxFournisseurs | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  recherche = '';
  /** Seulement les fournisseurs a qui l'on doit de l'argent. */
  dettes = false;
  masquerArchives = true;
  private readonly suite = new SuiteDePages();

  /** Fiche ouverte (null : creation, undefined : fermee). */
  readonly fiche = signal<Fournisseur | null | undefined>(undefined);
  readonly compteOuvert = signal<number | null>(null);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.totauxFournisseurs().subscribe({ next: (t) => this.totaux.set(t) });
    this.service
      .fournisseurs({
        q: this.recherche.trim() || undefined,
        actif: this.masquerArchives ? true : undefined,
        dettes: this.dettes,
        page: this.suite.numero()
      })
      .subscribe({
        next: (p) => {
          this.page.set(cumulerPage(this.page(), p));
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: (e) => {
          this.chargement.set(false);
          this.erreur.set(e?.error?.message ?? 'Chargement des fournisseurs impossible.');
        }
      });
  }

  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  enregistre(f: Fournisseur): void {
    this.fiche.set(undefined);
    this.succes.set(`Fiche de ${f.nom} enregistree.`);
    this.charger();
  }

  async archiver(f: Fournisseur): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Archiver ${f.nom} ?`,
      message: f.du > 0
        ? 'On lui doit encore de l\'argent : son compte reste consultable et payable, mais il ne sera plus propose sur les commandes.'
        : 'Son historique reste consultable ; il ne sera plus propose sur les nouvelles commandes.',
      libelleAction: 'Archiver',
      danger: true
    });
    if (!accepte) {
      return;
    }
    this.service.archiverFournisseur(f.id).subscribe({
      next: () => {
        this.succes.set(`${f.nom} archive.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Archivage impossible.')
    });
  }

  reactiver(f: Fournisseur): void {
    this.service.reactiverFournisseur(f.id).subscribe({
      next: () => {
        this.succes.set(`${f.nom} reactive.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Reactivation impossible.')
    });
  }
}
