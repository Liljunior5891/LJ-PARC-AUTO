import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Camion, Page, STATUTS_CAMION, StatutCamion } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';

@Component({
  selector: 'app-camions-liste',
  imports: [FormsModule, RouterLink, DecimalPipe],
  templateUrl: './camions-liste.html'
})
export class CamionsListe implements OnInit {
  private readonly service = inject(CamionService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_CAMION;
  readonly page = signal<Page<Camion> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  recherche = '';
  statut: StatutCamion | '' = '';
  masquerArchives = true;
  private pageCourante = 0;

  readonly peutGerer = this.auth.peutGererParc();

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche,
        statut: this.statut,
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
          this.erreur.set('Chargement de la liste des camions impossible.');
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

  archiver(camion: Camion): void {
    if (!confirm(`Archiver le camion ${camion.immatriculation} ? Son historique sera conserve.`)) {
      return;
    }
    this.service.archiver(camion.id).subscribe({
      next: () => this.charger(),
      error: () => this.erreur.set("Archivage impossible.")
    });
  }

  classeStatut(statut: StatutCamion): string {
    return statut.toLowerCase();
  }
}
