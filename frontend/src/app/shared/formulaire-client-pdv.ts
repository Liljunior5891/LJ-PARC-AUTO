import { Component, inject, input, output } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { ClientPdv } from '../core/models/materiaux';
import { MateriauxService } from '../core/services/materiaux.service';
import { ClientEnregistre, FormulaireClient } from './formulaire-client';
import { Modale } from './modale';
import { SOURCE_CLIENTS, sourceClientsPdv } from './source-clients';

/**
 * Fiche d'un client du point de vente : le formulaire de la gestion de parc,
 * branche sur les clients du point de vente. Plafond de credit et delai de
 * paiement ne sont proposes qu'au gerant et a l'administrateur : un caissier
 * cree la fiche au comptoir, dans la version courte, sans credit propre.
 */
@Component({
  selector: 'app-formulaire-client-pdv',
  imports: [FormulaireClient, Modale],
  providers: [{ provide: SOURCE_CLIENTS, useFactory: sourceClientsPdv }],
  template: `
    <app-modale [titre]="client() ? 'Modifier ' + client()!.nom : 'Nouveau client'"
                [sousTitre]="client() ? 'Code ' + client()!.code : ''"
                largeur="1180px" (ferme)="ferme.emit()">
      <app-formulaire-client [clientId]="client()?.id ?? null" [compact]="compact() || !estGestionnaire"
                             [nomInitial]="nomPropose() || null"
                             (enregistre)="surEnregistre($event)" (annule)="ferme.emit()" />
    </app-modale>
  `
})
export class FormulaireClientPdv {
  private readonly service = inject(MateriauxService);
  readonly estGestionnaire = inject(AuthService).aRole('ADMIN', 'GERANT');

  /** Client a modifier ; absent : creation. */
  readonly client = input<ClientPdv | null>(null);
  /** Nom saisi au comptoir avant d'ouvrir la fiche. */
  readonly nomPropose = input('');
  /** Version courte, pour une creation au comptoir. */
  readonly compact = input(false);
  readonly enregistre = output<ClientPdv>();
  readonly ferme = output<void>();

  /** L'appelant attend la fiche du point de vente, encours compris. */
  surEnregistre({ client }: ClientEnregistre): void {
    this.service.client(client.id).subscribe({ next: (fiche) => this.enregistre.emit(fiche) });
  }
}
