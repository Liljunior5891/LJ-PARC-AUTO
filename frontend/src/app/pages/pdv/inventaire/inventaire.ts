import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ArticlePdv, BilanInventaire, CategoriePdv } from '../../../core/models/pdv';
import { BilanImport } from '../../../core/models/rapport';
import { AuthService } from '../../../core/auth/auth.service';
import { LieuxService } from '../../../core/services/lieux.service';
import { PdvService } from '../../../core/services/pdv.service';
import { ChampQuantite } from '../../../shared/champ-quantite';
import { ExportPdv } from '../../../shared/export-pdv';
import { ImportPdv, messageImport } from '../../../shared/import-pdv';
import { normaliserCodeBarres } from '../../../shared/code-barres';

/**
 * Feuille d'inventaire : pour chaque produit suivi en stock, la quantite
 * attendue et la quantite comptee en rayon. Seuls les produits comptes sont
 * envoyes ; la validation se fait en une fois et ne trace que les ecarts.
 */
@Component({
  selector: 'app-inventaire-pdv',
  imports: [DecimalPipe, FormsModule, RouterLink, ChampQuantite, ExportPdv, ImportPdv],
  templateUrl: './inventaire.html',
  styleUrl: './inventaire.scss'
})
export class InventairePdv implements OnInit {
  private readonly service = inject(PdvService);

  private readonly auth = inject(AuthService);
  private readonly lieuxService = inject(LieuxService);
  /** Avec des entrepots, on compte un lieu a la fois : l'attendu est le stock de ce lieu. */
  readonly lieux = computed(() => this.lieuxService.etat()?.lieux ?? []);
  readonly lieuId = signal<number | null>(null);
  private readonly stockDuLieu = signal<Map<number, number>>(new Map());
  readonly articles = signal<ArticlePdv[]>([]);
  readonly categories = signal<CategoriePdv[]>([]);
  /** Quantite comptee par produit ; absent tant que le produit n'est pas compte. */
  readonly comptes = signal<Record<number, number | null>>({});
  readonly recherche = signal('');
  readonly categorie = signal<number | null>(null);
  readonly seulementEcarts = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly bilan = signal<BilanInventaire | null>(null);
  /** Comptage saisi dans un tableur puis importe. */
  readonly importOuvert = signal(false);
  readonly succesImport = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly chargement = signal(true);

  motif = `Inventaire du ${new Date().toLocaleDateString('fr-FR')}`;

  readonly lignes = computed(() => {
    const motif = normaliserCodeBarres(this.recherche()).toLowerCase();
    const categorie = this.categorie();
    const comptes = this.comptes();
    return this.articles().filter((a) =>
      (categorie === null || a.categorieId === categorie) &&
      (!motif || a.nom.toLowerCase().includes(motif) || a.code.toLowerCase().includes(motif) ||
        (a.codeBarres ?? '').includes(motif)) &&
      (!this.seulementEcarts() || this.ecart(a, comptes) !== 0)
    );
  });

  readonly nombreComptes = computed(() =>
    Object.values(this.comptes()).filter((q) => q !== null && q !== undefined && !Number.isNaN(q)).length
  );
  readonly nombreEcarts = computed(() => this.articles().filter((a) => (this.ecart(a, this.comptes()) ?? 0) !== 0).length);
  /** Valeur des ecarts au cout d'achat : ce que l'inventaire fait gagner ou perdre. */
  readonly valeurEcarts = computed(() =>
    this.articles().reduce((s, a) => s + (this.ecart(a, this.comptes()) ?? 0) * (a.prixAchat ?? 0), 0)
  );

  ngOnInit(): void {
    if (this.auth.estMateriaux()) {
      this.lieuxService.chargerEtat();
    }
    this.charger();
    this.service.categories().subscribe({ next: (c) => this.categories.set(c.filter((x) => x.actif)) });
  }

  compte(a: ArticlePdv): number | null {
    return this.comptes()[a.id] ?? null;
  }

  saisir(a: ArticlePdv, valeur: number | null | string): void {
    const quantite = valeur === '' || valeur === null || valeur === undefined ? null : Number(valeur);
    this.comptes.update((c) => ({ ...c, [a.id]: quantite !== null && quantite < 0 ? 0 : quantite }));
  }

  /** Le produit est juste : la quantite attendue est reprise comme comptee. */
  confirmer(a: ArticlePdv): void {
    this.saisir(a, this.attendu(a));
  }

  /** Ecart compte - attendu ; null tant que le produit n'est pas compte. */
  ecart(a: ArticlePdv, comptes = this.comptes()): number | null {
    const q = comptes[a.id];
    return q === null || q === undefined || Number.isNaN(q) ? null : q - this.attendu(a);
  }

  attendu(a: ArticlePdv): number {
    return this.lieuId() ? this.stockDuLieu().get(a.id) ?? 0 : a.stockActuel;
  }

  changerLieu(id: number | null): void {
    this.lieuId.set(id);
    this.comptes.set({});
    this.bilan.set(null);
    this.chargerStockDuLieu();
  }

  private chargerStockDuLieu(): void {
    const lieu = this.lieuId();
    if (lieu) {
      this.lieuxService.stock(lieu, '', 0, 2000).subscribe({
        next: (p) => this.stockDuLieu.set(new Map(p.content.map((s) => [s.articleId, s.stock]))),
        error: () => this.erreur.set('Chargement du stock de ce lieu impossible.')
      });
    }
  }

  vider(): void {
    this.comptes.set({});
    this.bilan.set(null);
  }

  valider(): void {
    const comptages = Object.entries(this.comptes())
      .filter(([, q]) => q !== null && q !== undefined && !Number.isNaN(q))
      .map(([id, quantite]) => ({ articleId: Number(id), quantite: quantite as number }));
    if (comptages.length === 0 || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.validerInventaire(comptages, this.motif, this.lieuId()).subscribe({
      next: (bilan) => {
        this.envoi.set(false);
        this.bilan.set(bilan);
        this.comptes.set({});
        this.charger();
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? "Validation de l'inventaire impossible.");
      }
    });
  }

  importTermine(bilan: BilanImport): void {
    this.importOuvert.set(false);
    this.bilan.set(null);
    this.succesImport.set(messageImport(bilan));
    this.charger();
  }

  private charger(): void {
    this.service.articles({ actif: true, size: 1000 }).subscribe({
      next: (p) => {
        this.articles.set(p.content.filter((a) => a.suiviStock));
        this.chargerStockDuLieu();
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des produits impossible.');
      }
    });
  }
}
