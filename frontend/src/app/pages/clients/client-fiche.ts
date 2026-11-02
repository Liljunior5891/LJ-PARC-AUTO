import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Location } from '../../core/models/exploitation';
import { LocationService } from '../../core/services/location.service';
import { ClientEnregistre, FormulaireClient } from '../../shared/formulaire-client';

@Component({
  selector: 'app-client-fiche',
  imports: [RouterLink, DecimalPipe, FormulaireClient],
  templateUrl: './client-fiche.html'
})
export class ClientFiche implements OnInit {
  private readonly locationService = inject(LocationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly id = signal<number | null>(null);
  /** Ouverte dans une fenetre depuis la liste : creation seule, sans en-tete de page. */
  readonly enModale = input(false);
  /** Creation terminee en mode fenetre : identifiant du nouvel enregistrement. */
  readonly cree = output<number>();
  readonly annule = output<void>();
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Historique des contrats du client (chapitre 10). */
  readonly contrats = signal<Location[]>([]);

  get titre(): string {
    return this.id() === null ? 'Nouveau client' : 'Fiche du client';
  }

  ngOnInit(): void {
    const parametre = this.enModale() ? null : this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      const id = Number(parametre);
      this.id.set(id);
      this.locationService.lister({ clientId: id, size: 10 }).subscribe({
        next: (page) => this.contrats.set(page.content)
      });
    }
  }

  /** Fiche enregistree par le formulaire : le message d'echec des pieces y reste affiche. */
  enregistre({ client, creation, echecsPieces }: ClientEnregistre): void {
    if (creation && this.enModale()) {
      this.cree.emit(client.id);
    } else if (creation) {
      void this.router.navigate(['/clients', client.id]);
    } else if (!echecsPieces) {
      this.succes.set('Fiche enregistree.');
    }
  }

  annuler(): void {
    if (this.enModale()) {
      this.annule.emit();
    } else {
      void this.router.navigate(['/clients']);
    }
  }
}
