import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Chauffeur, Page, STATUTS_CHAUFFEUR, StatutChauffeur } from '../../core/models/models';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { Modale } from '../../shared/modale';
import { ChauffeurFiche } from '../chauffeurs/chauffeur-fiche';
import { OperationService } from '../../core/operation/operation.service';

@Component({
  selector: 'app-chauffeurs-liste',
  imports: [FormsModule, RouterLink, Modale, ChauffeurFiche],
  templateUrl: './chauffeurs-liste.html'
})
export class ChauffeursListe implements OnInit {
  private readonly service = inject(ChauffeurService);
  private readonly operation = inject(OperationService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_CHAUFFEUR;
  readonly page = signal<Page<Chauffeur> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Fenetre de creation, ouverte par-dessus la liste. */
  readonly creation = signal(false);

  recherche = '';
  statut: StatutChauffeur | '' = '';
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
    this.succes.set('Chauffeur enregistre.');
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

  async archiver(chauffeur: Chauffeur): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Archiver le chauffeur ${chauffeur.nomComplet} ?`,
      message: 'Il sort des listes ; son historique reste consultable.',
      libelleAction: 'Archiver',
      danger: true
    });
    if (!accepte) {
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
