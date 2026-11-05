import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArticlePdv, CommandeFournisseurDetail } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { ChampQuantite } from '../../../shared/champ-quantite';
import { Modale } from '../../../shared/modale';
import { normaliserCodeBarres } from '../../../shared/code-barres';

/** Produit sur la commande en cours de saisie. */
interface LigneSaisie {
  article: ArticlePdv;
  quantite: number | null;
  coutUnitaire: number | null;
}

/**
 * Saisie d'une commande fournisseur : le fournisseur, puis les produits
 * ajoutes un a un par une recherche, avec quantite et cout prevus. Sert a la
 * creation comme a la modification d'une commande en cours.
 */
@Component({
  selector: 'app-formulaire-commande',
  imports: [DecimalPipe, FormsModule, Modale, ChampQuantite],
  templateUrl: './formulaire-commande.html',
  styleUrl: './formulaire-commande.scss'
})
export class FormulaireCommande implements OnInit {
  private readonly service = inject(PdvService);

  /** Commande a modifier ; absente pour une nouvelle commande. */
  readonly commande = input<CommandeFournisseurDetail | null>(null);
  /** Produits proposes d'emblee (ceux sous leur seuil d'alerte). */
  readonly preremplissage = input<ArticlePdv[]>([]);
  readonly enregistree = output<CommandeFournisseurDetail>();
  readonly ferme = output<void>();

  readonly articles = signal<ArticlePdv[]>([]);
  readonly fournisseursConnus = signal<string[]>([]);
  readonly lignes = signal<LigneSaisie[]>([]);
  readonly recherche = signal('');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  fournisseur = '';
  dateCommande = new Date().toISOString().slice(0, 10);
  livraisonPrevue = '';
  note = '';

  readonly suggestions = computed(() => {
    const motif = normaliserCodeBarres(this.recherche()).toLowerCase();
    if (!motif) {
      return [];
    }
    const pris = new Set(this.lignes().map((l) => l.article.id));
    return this.articles()
      .filter((a) => !pris.has(a.id) &&
        (a.nom.toLowerCase().includes(motif) || a.code.toLowerCase().includes(motif) || (a.codeBarres ?? '').includes(motif)))
      .slice(0, 8);
  });

  readonly total = computed(() => this.lignes().reduce((s, l) => s + (l.quantite ?? 0) * (l.coutUnitaire ?? 0), 0));
  readonly valide = computed(() =>
    this.lignes().length > 0 && this.lignes().every((l) => (l.quantite ?? 0) > 0)
  );

  ngOnInit(): void {
    this.service.fournisseurs().subscribe({ next: (f) => this.fournisseursConnus.set(f) });
    this.service.articles({ actif: true, size: 1000 }).subscribe({
      next: (p) => {
        this.articles.set(p.content);
        const c = this.commande();
        if (c) {
          this.fournisseur = c.resume.fournisseur;
          this.dateCommande = c.resume.dateCommande;
          this.livraisonPrevue = c.resume.livraisonPrevue ?? '';
          this.note = c.note ?? '';
          this.lignes.set(c.lignes.map((l) => ({
            article: p.content.find((a) => a.id === l.articleId) ?? this.articleMinimal(l.articleId, l.articleNom, l.articleCode),
            quantite: l.quantite,
            coutUnitaire: l.coutUnitaire ?? null
          })));
        } else {
          this.lignes.set(this.preremplissage().map((a) => this.nouvelleLigne(a)));
        }
      }
    });
  }

  ajouter(article: ArticlePdv): void {
    this.lignes.update((l) => [...l, this.nouvelleLigne(article)]);
    this.recherche.set('');
  }

  /** Entree dans la recherche : le seul produit trouve, ou un code-barres exact. */
  valider(): void {
    const s = this.suggestions();
    const exact = s.find((a) => a.codeBarres === normaliserCodeBarres(this.recherche()));
    if (exact || s.length === 1) {
      this.ajouter(exact ?? s[0]);
    }
  }

  retirer(ligne: LigneSaisie): void {
    this.lignes.update((l) => l.filter((x) => x !== ligne));
  }

  modifier(ligne: LigneSaisie, champ: 'quantite' | 'coutUnitaire', valeur: number | null): void {
    this.lignes.update((l) => l.map((x) => (x === ligne ? { ...x, [champ]: valeur } : x)));
  }

  enregistrer(): void {
    if (!this.fournisseur.trim()) {
      this.erreur.set('Indiquez le fournisseur.');
      return;
    }
    if (!this.valide()) {
      this.erreur.set('Ajoutez au moins un produit, avec une quantite pour chacun.');
      return;
    }
    const requete = {
      fournisseur: this.fournisseur.trim(),
      dateCommande: this.dateCommande || undefined,
      livraisonPrevue: this.livraisonPrevue || null,
      note: this.note.trim() || undefined,
      lignes: this.lignes().map((l) => ({ articleId: l.article.id, quantite: l.quantite as number, coutUnitaire: l.coutUnitaire }))
    };
    const c = this.commande();
    const appel = c
      ? this.service.modifierCommandeFournisseur(c.resume.id, requete)
      : this.service.creerCommandeFournisseur(requete);
    this.envoi.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (detail) => {
        this.envoi.set(false);
        this.enregistree.emit(detail);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  /** Quantite proposee : de quoi remonter au double du seuil d'alerte, a defaut une unite. */
  private nouvelleLigne(article: ArticlePdv): LigneSaisie {
    const seuil = article.seuilAlerte ?? 0;
    const manque = Math.ceil(seuil * 2 - article.stockDisponible);
    return { article, quantite: article.suiviStock && manque > 0 ? manque : 1, coutUnitaire: article.prixAchat ?? null };
  }

  private articleMinimal(id: number, nom: string, code: string): ArticlePdv {
    return { id, nom, code, prixVente: 0, suiviStock: true, stockActuel: 0, stockReserve: 0, stockDisponible: 0, stockBas: false, actif: false };
  }
}
