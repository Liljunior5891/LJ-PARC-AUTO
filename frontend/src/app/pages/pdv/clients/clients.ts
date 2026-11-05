import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Page } from '../../../core/models/models';
import { ClientPdv, TYPES_CLIENT_PDV, TypeClientPdv } from '../../../core/models/materiaux';
import { OperationService } from '../../../core/operation/operation.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { CompteClientPdv } from '../../../shared/compte-client-pdv';
import { FormulaireClientPdv } from '../../../shared/formulaire-client-pdv';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Clients du point de vente : le tableau de la gestion de parc, complete par ce
 * que le client doit. La situation « Doivent de l'argent » liste les debiteurs,
 * les plus gros d'abord ; le bouton Compte ouvre ventes dues et versements.
 */
@Component({
  selector: 'app-clients-pdv',
  imports: [SuiteListe, FormsModule, DecimalPipe, FormulaireClientPdv, CompteClientPdv],
  templateUrl: './clients.html'
})
export class ClientsPdv implements OnInit {
  private readonly service = inject(MateriauxService);
  private readonly operation = inject(OperationService);

  readonly types = TYPES_CLIENT_PDV;
  readonly page = signal<Page<ClientPdv> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  recherche = '';
  type: TypeClientPdv | '' = '';
  /** Seulement les clients qui doivent de l'argent. */
  debiteurs = false;
  masquerArchives = true;
  private readonly suite = new SuiteDePages();

  /** Fiche ouverte en modification (null : creation, undefined : fermee). */
  readonly fiche = signal<ClientPdv | null | undefined>(undefined);
  readonly compteOuvert = signal<number | null>(null);

  /** Total du par les clients affiches, et part en retard. */
  readonly totaux = computed(() => {
    const liste = this.page()?.content ?? [];
    return {
      du: liste.reduce((s, c) => s + c.encours, 0),
      retard: liste.reduce((s, c) => s + c.encoursEchu, 0)
    };
  });

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .clients({
        q: this.recherche.trim().toLowerCase() || undefined,
        type: this.type,
        actif: this.masquerArchives ? true : undefined,
        debiteurs: this.debiteurs,
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
          this.erreur.set(e?.error?.message ?? 'Chargement de la liste des clients impossible.');
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

  enregistre(client: ClientPdv): void {
    this.fiche.set(undefined);
    this.succes.set(`Fiche de ${client.nom} enregistree.`);
    this.charger();
  }

  async archiver(client: ClientPdv): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Archiver le client ${client.nom} ?`,
      message: client.encours > 0
        ? 'Il doit encore de l\'argent : son compte reste consultable et ses versements possibles, mais il ne pourra plus acheter.'
        : 'Son historique reste consultable.',
      libelleAction: 'Archiver',
      danger: true
    });
    if (!accepte) {
      return;
    }
    this.service.archiverClient(client.id).subscribe({
      next: () => {
        this.succes.set(`${client.nom} archive.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Archivage impossible.')
    });
  }
}
