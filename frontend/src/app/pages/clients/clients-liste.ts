import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ClientParc, TYPES_CLIENT, TypeClient } from '../../core/models/exploitation';
import { Page } from '../../core/models/models';
import { ClientService } from '../../core/services/client.service';

@Component({
  selector: 'app-clients-liste',
  imports: [FormsModule, RouterLink],
  templateUrl: './clients-liste.html'
})
export class ClientsListe implements OnInit {
  private readonly service = inject(ClientService);
  private readonly auth = inject(AuthService);

  readonly types = TYPES_CLIENT;
  readonly page = signal<Page<ClientParc> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  recherche = '';
  type: TypeClient | '' = '';
  masquerArchives = true;
  private pageCourante = 0;

  readonly peutGerer = this.auth.aRole('ADMIN', 'LOGISTIQUE', 'COMPTABLE');

  ngOnInit(): void {
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

  archiver(client: ClientParc): void {
    if (!confirm(`Archiver le client ${client.raisonSociale} ? Son historique reste consultable.`)) {
      return;
    }
    this.service.archiver(client.id).subscribe({
      next: () => this.charger(),
      error: () => this.erreur.set('Archivage impossible.')
    });
  }
}
