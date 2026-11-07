import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ArticlePdv, CommandeFournisseurDetail } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { ChampQuantite } from '../../../shared/champ-quantite';
import { Modale } from '../../../shared/modale';
import { normaliserCodeBarres } from '../../../shared/code-barres';
import { Fournisseur } from '../../../core/models/achats';
import { AchatsService } from '../../../core/services/achats.service';
import { FormulaireFournisseurPdv } from '../../../shared/formulaire-fournisseur-pdv';
import { OptionSelect, SelectRecherche } from '../../../shared/select-recherche';

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
  imports: [DecimalPipe, FormsModule, Modale, ChampQuantite, SelectRecherche, FormulaireFournisseurPdv],
  templateUrl: './formulaire-commande.html',
  styleUrl: './formulaire-commande.scss'
})
export class FormulaireCommande implements OnInit {
  private readonly service = inject(PdvService);
  private readonly achats = inject(AchatsService);

  /** Commande a modifier ; absente pour une nouvelle commande. */
  readonly commande = input<CommandeFournisseurDetail | null>(null);
  /** Produits proposes d'emblee (ceux sous leur seuil d'alerte). */
  readonly preremplissage = input<ArticlePdv[]>([]);
  readonly enregistree = output<CommandeFournisseurDetail>();
  readonly ferme = output<void>();

  readonly articles = signal<ArticlePdv[]>([]);
  /** Fournisseurs enregistres (actifs), proposes dans la liste. */
  readonly fournisseurs = signal<Fournisseur[]>([]);
  readonly nouveauFournisseur = signal(false);
  readonly optionsFournisseurs = computed<OptionSelect[]>(() => this.fournisseurs().map((f) => ({
    valeur: f.id, libelle: f.nom,
    detail: [f.code, f.telephone, f.ville].filter(Boolean).join(' · '),
    monogramme: f.nom.slice(0, 2).toUpperCase()
  })));
  readonly lignes = signal<LigneSaisie[]>([]);
  readonly recherche = signal('');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  fournisseurId: number | null = null;
  /** Nom du fournisseur d'une commande dont la fiche est archivee. */
  private nomInitial = '';
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
    this.achats.fournisseursActifs().subscribe({
      next: (liste) => {
        const c = this.commande();
        // Fiche archivee depuis la commande : elle reste proposee pour ne pas perdre le choix.
        if (c?.resume.fournisseurId && !liste.some((f) => f.id === c.resume.fournisseurId)) {
          liste = [{ id: c.resume.fournisseurId, nom: c.resume.fournisseur, code: '', actif: false, commandes: 0, du: 0, enRetard: 0 }, ...liste];
        }
        this.fournisseurs.set(liste);
      }
    });
    this.service.articles({ actif: true, size: 1000 }).subscribe({
      next: (p) => {
        this.articles.set(p.content);
        const c = this.commande();
        if (c) {
          this.fournisseurId = c.resume.fournisseurId ?? null;
          this.nomInitial = c.resume.fournisseur;
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

  /** Nouveau fournisseur enregistre depuis la commande : il rejoint la liste et devient le fournisseur choisi. */
  fournisseurCree(f: Fournisseur): void {
    this.nouveauFournisseur.set(false);
    this.fournisseurs.update((l) => [...l.filter((x) => x.id !== f.id), f].sort((a, b) => a.nom.localeCompare(b.nom)));
    this.fournisseurId = f.id;
    this.erreur.set(null);
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
    const choisi = this.fournisseurs().find((f) => f.id === this.fournisseurId);
    if (!choisi) {
      this.erreur.set('Choisissez le fournisseur, ou enregistrez-le avec le bouton +.');
      return;
    }
    if (!this.valide()) {
      this.erreur.set('Ajoutez au moins un produit, avec une quantite pour chacun.');
      return;
    }
    const requete = {
      fournisseurId: choisi.id,
      fournisseur: choisi.nom || this.nomInitial,
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
