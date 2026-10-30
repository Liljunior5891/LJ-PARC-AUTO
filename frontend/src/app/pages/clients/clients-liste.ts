import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ClientParc, TYPES_CLIENT, TypeClient } from '../../core/models/exploitation';
import { Page } from '../../core/models/models';
import { ClientService } from '../../core/services/client.service';
import { Modale } from '../../shared/modale';
import { ClientFiche } from '../clients/client-fiche';
import { OperationService } from '../../core/operation/operation.service';

@Component({
  selector: 'app-clients-liste',
  imports: [FormsModule, RouterLink, Modale, ClientFiche],
  templateUrl: './clients-liste.html'
})
export class ClientsListe implements OnInit {
  private readonly service = inject(ClientService);
  private readonly operation = inject(OperationService);
  private readonly auth = inject(AuthService);

  readonly types = TYPES_CLIENT;
  readonly page = signal<Page<ClientParc> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Fenetre de creation, ouverte par-dessus la liste. */
  readonly creation = signal(false);

  recherche = '';
  type: TypeClient | '' = '';
  masquerArchives = true;
  private pageCourante = 0;

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
    this.succes.set('Client enregistre.');
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche,
        type: this.type,
        actif: this.masquerArchives ? true : undefined,
        page: this.pageCourante
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement de la liste des clients impossible.');
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

  async archiver(client: ClientParc): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Archiver le client ${client.raisonSociale} ?`,
      message: 'Son historique reste consultable.',
      libelleAction: 'Archiver',
      danger: true
    });
    if (!accepte) {
      return;
    }
    this.service.archiver(client.id).subscribe({
      next: () => this.charger(),
      error: () => this.erreur.set('Archivage impossible.')
    });
  }
}
