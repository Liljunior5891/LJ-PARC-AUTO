import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CodeModule } from '../../core/models/models';
import {
  EntrepriseResume,
  PRESENTATION_MODULES,
  StatistiquesPlateforme
} from '../../core/models/plateforme';
import { PlateformeService } from '../../core/services/plateforme.service';

const LIBELLES_MODULES: Record<CodeModule, string> = {
  GESTION_PARC: 'Gestion de parc',
  POINT_DE_VENTE: 'Point de vente',
  MARKETPLACE: 'Marketplace',
  AGENCES: 'Agences multiservices'
};

/**
 * Console de l'exploitant : entreprises abonnees, modules souscrits et
 * suspension. Elle ne donne jamais acces aux donnees metier des entreprises.
 */
@Component({
  selector: 'app-plateforme',
  imports: [DatePipe, FormsModule],
  templateUrl: './plateforme.html',
  styleUrl: './plateforme.scss'
})
export class Plateforme implements OnInit {
  private readonly service = inject(PlateformeService);

  readonly presentation = PRESENTATION_MODULES;
  readonly libellesModules = LIBELLES_MODULES;
  readonly codesModules = Object.keys(LIBELLES_MODULES) as CodeModule[];

  readonly statistiques = signal<StatistiquesPlateforme | null>(null);
  readonly entreprises = signal<EntrepriseResume[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly enCours = signal<number | null>(null);

  recherche = '';
  readonly filtre = signal('');

  readonly entreprisesAffichees = computed(() => {
    const motif = this.filtre().trim().toLowerCase();
    return motif
      ? this.entreprises().filter((e) =>
          [e.nom, e.email, e.pays].some((valeur) => valeur?.toLowerCase().includes(motif))
        )
      : this.entreprises();
  });

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.statistiques().subscribe({ next: (s) => this.statistiques.set(s) });
    this.service.entreprises().subscribe({
      next: (liste) => {
        this.entreprises.set(liste);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des entreprises impossible.');
      }
    });
  }

  basculerStatut(entreprise: EntrepriseResume): void {
    if (this.enCours() !== null) {
      return;
    }
    const statut = entreprise.statut === 'ACTIVE' ? 'SUSPENDUE' : 'ACTIVE';
    this.enCours.set(entreprise.id);
    this.erreur.set(null);
    this.service.changerStatut(entreprise.id, statut).subscribe({
      next: (maj) => {
        this.enCours.set(null);
        this.entreprises.update((liste) => liste.map((e) => (e.id === maj.id ? maj : e)));
        this.service.statistiques().subscribe({ next: (s) => this.statistiques.set(s) });
      },
      error: (reponse) => {
        this.enCours.set(null);
        const message = (reponse as { error?: { message?: string } })?.error?.message;
        this.erreur.set(message ?? 'Changement de statut impossible.');
      }
    });
  }
}
