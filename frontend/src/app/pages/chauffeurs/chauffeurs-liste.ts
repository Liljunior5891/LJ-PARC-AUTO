import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Chauffeur, Page, STATUTS_CHAUFFEUR, StatutChauffeur } from '../../core/models/models';
import { ChauffeurService } from '../../core/services/chauffeur.service';

@Component({
  selector: 'app-chauffeurs-liste',
  imports: [FormsModule, RouterLink],
  templateUrl: './chauffeurs-liste.html'
})
export class ChauffeursListe implements OnInit {
  private readonly service = inject(ChauffeurService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_CHAUFFEUR;
  readonly page = signal<Page<Chauffeur> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);

  recherche = '';
  statut: StatutChauffeur | '' = '';
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
          this.erreur.set('Chargement de la liste des chauffeurs impossible.');
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

  archiver(chauffeur: Chauffeur): void {
    if (!confirm(`Archiver le chauffeur ${chauffeur.nomComplet} ?`)) {
      return;
    }
    this.service.archiver(chauffeur.id).subscribe({
      next: () => this.charger(),
      error: () => this.erreur.set('Archivage impossible.')
    });
  }

  classeStatut(statut: StatutChauffeur): string {
    return 'etiquette ' + statut.toLowerCase();
  }
}
