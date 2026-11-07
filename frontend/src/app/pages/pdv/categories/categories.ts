import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { CategoriePdv } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { FenetreCategorie } from '../../../shared/fenetre-categorie';
import { SuiteListe } from '../../../shared/suite-liste';
import { ExportPdv } from '../../../shared/export-pdv';
import { ImportPdv, messageImport } from '../../../shared/import-pdv';
import { BilanImport } from '../../../core/models/rapport';

const PAR_PAGE = 20;

/**
 * Categories du catalogue : une page a part entiere, pour les entreprises qui
 * en ont beaucoup. Elles organisent les boutons de la caisse ; en restauration,
 * elles envoient aussi chaque article en cuisine ou au bar.
 */
@Component({
  selector: 'app-categories-pdv',
  imports: [FormsModule, FenetreCategorie, SuiteListe, ExportPdv, ImportPdv],
  templateUrl: './categories.html',
  styleUrl: './categories.scss'
})
export class CategoriesPdv implements OnInit {
  private readonly service = inject(PdvService);
  readonly restauration = inject(AuthService).estRestauration();

  readonly categories = signal<CategoriePdv[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly recherche = signal('');
  readonly masquerInactives = signal(true);
  readonly numeroPage = signal(0);

  /** Categories retenues par les filtres, dans l'ordre de la caisse. */
  private readonly filtrees = computed(() => {
    const terme = this.recherche().trim().toLowerCase();
    return this.categories().filter((c) =>
      (!this.masquerInactives() || c.actif) && (!terme || c.nom.toLowerCase().includes(terme)));
  });
  /** Lignes affichees : elles s'allongent au defilement. */
  readonly affichees = computed(() => this.filtrees().slice(0, (this.numeroPage() + 1) * PAR_PAGE));
  readonly etatListe = computed(() => ({
    last: this.affichees().length >= this.filtrees().length,
    content: this.affichees(),
    totalElements: this.filtrees().length,
    number: this.numeroPage()
  }));

  // Fenetre d'ajout et de modification
  readonly edition = signal<CategoriePdv | 'nouvelle' | null>(null);

  readonly importOuvert = signal(false);

  ngOnInit(): void {
    this.charger();
  }

  importTermine(bilan: BilanImport): void {
    this.importOuvert.set(false);
    this.succes.set(messageImport(bilan));
    this.charger();
  }

  private charger(): void {
    this.service.categories().subscribe({
      next: (liste) => {
        this.categories.set(liste);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des categories impossible.');
      }
    });
  }

  chercher(terme: string): void {
    this.recherche.set(terme);
    this.numeroPage.set(0);
  }

  filtrerInactives(masquer: boolean): void {
    this.masquerInactives.set(masquer);
    this.numeroPage.set(0);
  }

  ouvrir(categorie?: CategoriePdv): void {
    this.edition.set(categorie ?? 'nouvelle');
  }

  enregistre(categorie: CategoriePdv): void {
    this.edition.set(null);
    this.succes.set(`Categorie ${categorie.nom} enregistree.`);
    this.charger();
  }

  /** Une categorie masquee disparait de la caisse ; ses articles restent au catalogue. */
  basculer(categorie: CategoriePdv): void {
    this.service.modifierCategorie(categorie.id, { ...categorie, actif: !categorie.actif }).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Modification impossible.')
    });
  }
}
