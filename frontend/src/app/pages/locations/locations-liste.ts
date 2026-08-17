import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  Location,
  STATUTS_LOCATION,
  StatutLocation,
  TYPES_LOCATION,
  TypeLocation
} from '../../core/models/exploitation';
import { Page } from '../../core/models/models';
import { LocationService } from '../../core/services/location.service';

@Component({
  selector: 'app-locations-liste',
  imports: [FormsModule, RouterLink, DecimalPipe],
  templateUrl: './locations-liste.html'
})
export class LocationsListe implements OnInit {
  private readonly service = inject(LocationService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_LOCATION;
  readonly types = TYPES_LOCATION;
  readonly page = signal<Page<Location> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  recherche = '';
  statut: StatutLocation | '' = '';
  type: TypeLocation | '' = '';
  private pageCourante = 0;

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
          this.erreur.set('Chargement des contrats impossible.');
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

  activer(contrat: Location): void {
    this.erreur.set(null);
    this.service.activer(contrat.id).subscribe({
      next: () => {
        this.succes.set(`Contrat ${contrat.numero} active : le camion passe en location.`);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Activation impossible.')
    });
  }

  classeStatut(statut: StatutLocation): string {
    const correspondances: Record<StatutLocation, string> = {
      BROUILLON: 'etiquette',
      EN_ATTENTE: 'etiquette en_location',
      ACTIVE: 'etiquette disponible',
      TERMINEE: 'etiquette en_mission',
      EXPIREE: 'etiquette vendu',
      ANNULEE: 'etiquette vendu'
    };
    return correspondances[statut];
  }
}
