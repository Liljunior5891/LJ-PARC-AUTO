import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Marche } from '../../core/models/marketplace';
import { BoutiquePubliqueService } from '../../core/services/marketplace.service';
import { IconeVitrine } from './icone-vitrine';

/** Embleme de chaque rayon de la place de marche. */
const EMOJIS_RAYON: Record<string, string> = {
  'Alimentation': '🥬', 'Restauration': '🍲', 'Boissons': '🥤', 'Mode et beaute': '👗', 'Electronique': '📱',
  'Maison': '🛋️', 'Quincaillerie et BTP': '🧱', 'Sante': '💊', 'Location de vehicules': '🚗',
  'Transport et logistique': '🚚', 'Services': '🛠️', 'Autre': '🛍️'
};

/**
 * Place de marche commune de la plateforme : toutes les boutiques en ligne et
 * leurs offres, avec recherche, rayon et ville.
 */
@Component({
  selector: 'app-marche',
  imports: [FormsModule, DecimalPipe, RouterLink, IconeVitrine],
  templateUrl: './marche.html',
  styleUrl: './marche.scss'
})
export class MarchePage implements OnInit {
  private readonly service = inject(BoutiquePubliqueService);
  private readonly saisie = new Subject<void>();

  readonly marche = signal<Marche | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  /** Rayons et villes connus au premier chargement : ils restent proposes quand on filtre. */
  readonly categories = signal<string[]>([]);
  readonly villes = signal<string[]>([]);

  readonly annee = new Date().getFullYear();
  /** Quatre offres illustrees, montrees dans le heros. */
  readonly apercus = computed(() => (this.marche()?.offres ?? []).filter((o) => !!o.image).slice(0, 4));

  recherche = '';
  categorie = '';
  ville = '';

  constructor() {
    // La recherche part apres une courte pause de frappe, pas a chaque lettre.
    this.saisie.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => this.charger());
  }

  ngOnInit(): void {
    this.charger();
  }

  rechercher(): void {
    this.saisie.next();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.marche(this.recherche, this.categorie, this.ville).subscribe({
      next: (marche) => {
        this.marche.set(marche);
        if (!this.categories().length) {
          this.categories.set(marche.categories);
        }
        if (!this.villes().length) {
          this.villes.set(marche.villes);
        }
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('La place de marche ne repond pas. Reessayez dans un instant.');
      }
    });
  }

  emojiRayon(rayon: string): string {
    return EMOJIS_RAYON[rayon] ?? '🛍️';
  }

  choisirRayon(rayon: string): void {
    this.categorie = this.categorie === rayon ? '' : rayon;
    this.charger();
  }

  defilerVers(id: string): void {
    setTimeout(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
  }

  reinitialiser(): void {
    this.recherche = '';
    this.categorie = '';
    this.ville = '';
    this.charger();
  }
}
