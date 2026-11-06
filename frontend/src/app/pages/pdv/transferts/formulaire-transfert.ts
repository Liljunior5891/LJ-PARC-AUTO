import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { ProduitCourt, Transfert } from '../../../core/models/lieux';
import { LieuxService } from '../../../core/services/lieux.service';
import { ChampQuantite } from '../../../shared/champ-quantite';
import { normaliserCodeBarres } from '../../../shared/code-barres';
import { Modale } from '../../../shared/modale';
import { OperationService } from '../../../core/operation/operation.service';

/** Produit sur le transfert en cours de saisie. */
interface LigneSaisie {
  articleId: number;
  nom: string;
  code: string;
  unite?: string | null;
  image?: string | null;
  /** Connu du gerant seulement : le magasinier ne voit pas le stock des autres lieux. */
  disponible: number | null;
  quantite: number | null;
}

/** Produit propose, avec son disponible au depart pour le gerant. */
type ProduitPropose = ProduitCourt & { disponible: number | null };

/**
 * Saisie d'un transfert, au format de la commande fournisseur.
 * <p>
 * Le gerant choisit les deux lieux et voit le disponible au depart. Le magasinier fait
 * une simple demande : le lieu d'arrivee parmi les siens et les produits voulus. C'est
 * le gerant qui choisira d'ou part la marchandise, en validant.
 */
@Component({
  selector: 'app-formulaire-transfert',
  imports: [DecimalPipe, FormsModule, Modale, ChampQuantite],
  templateUrl: './formulaire-transfert.html',
  styleUrls: ['../stock/formulaire-commande.scss', './formulaire-transfert.scss']
})
export class FormulaireTransfert implements OnInit {
  private readonly service = inject(LieuxService);
  private readonly operation = inject(OperationService);
  readonly gerant = inject(AuthService).aRole('ADMIN', 'GERANT');
  readonly etat = this.service.etat;

  readonly enregistre = output<Transfert>();
  readonly ferme = output<void>();

  readonly sourceId = signal<number | null>(null);
  readonly destinationId = signal<number | null>(null);
  readonly catalogue = signal<ProduitPropose[]>([]);
  readonly lignes = signal<LigneSaisie[]>([]);
  readonly recherche = signal('');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  note = '';

  /** Lieux d'arrivee : tous sauf le depart pour le gerant, les siens pour le magasinier. */
  readonly destinations = computed(() => {
    const etat = this.etat();
    return this.gerant
      ? (etat?.lieux ?? []).filter((l) => l.id !== this.sourceId())
      : etat?.mesLieux ?? [];
  });

  readonly suggestions = computed(() => {
    const motif = normaliserCodeBarres(this.recherche()).toLowerCase();
    if (!motif) {
      return [];
    }
    const pris = new Set(this.lignes().map((l) => l.articleId));
    return this.catalogue()
      .filter((s) => !pris.has(s.articleId) && (s.nom.toLowerCase().includes(motif) || s.code.toLowerCase().includes(motif)))
      .slice(0, 8);
  });

  readonly totalUnites = computed(() => this.lignes().reduce((s, l) => s + (l.quantite ?? 0), 0));
  readonly valide = computed(() =>
    (!this.gerant || !!this.sourceId()) && !!this.destinationId()
    && this.lignes().length > 0 && this.lignes().every((l) => (l.quantite ?? 0) > 0));

  constructor() {
    // Un magasinier affecte a un seul lieu n'a rien a choisir.
    effect(() => {
      const lieux = this.destinations();
      if (!this.gerant && lieux.length === 1 && this.destinationId() !== lieux[0].id) {
        this.destinationId.set(lieux[0].id);
      }
    });
  }

  ngOnInit(): void {
    if (!this.etat()) {
      this.service.chargerEtat();
    }
    if (!this.gerant) {
      this.service.produits().subscribe({
        next: (produits) => this.catalogue.set(produits.map((p) => ({ ...p, disponible: null }))),
        error: (e) => this.erreur.set(e?.error?.message ?? 'Lecture des produits impossible.')
      });
    }
  }

  /** Gerant : le catalogue et le disponible suivent le lieu de depart. */
  choisirSource(id: number | null): void {
    this.sourceId.set(id);
    if (id === this.destinationId()) {
      this.destinationId.set(null);
    }
    this.catalogue.set([]);
    this.recherche.set('');
    if (!id) {
      return;
    }
    this.service.stock(id, '', 0, 1000).subscribe({
      next: (p) => {
        this.catalogue.set(p.content);
        this.lignes.update((liste) => liste.map((l) => ({
          ...l, disponible: p.content.find((s) => s.articleId === l.articleId)?.disponible ?? 0
        })));
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Lecture du stock de ce lieu impossible.')
    });
  }

  depasse(l: LigneSaisie): boolean {
    return l.disponible !== null && (l.quantite ?? 0) > l.disponible;
  }

  ajouter(s: ProduitPropose): void {
    this.lignes.update((l) => [...l, {
      articleId: s.articleId, nom: s.nom, code: s.code, unite: s.unite, image: s.image, disponible: s.disponible, quantite: 1
    }]);
    this.recherche.set('');
  }

  /** Entree dans la recherche : le seul produit trouve. */
  valider(): void {
    const s = this.suggestions();
    if (s.length === 1) {
      this.ajouter(s[0]);
    }
  }

  retirer(ligne: LigneSaisie): void {
    this.lignes.update((l) => l.filter((x) => x !== ligne));
  }

  modifier(ligne: LigneSaisie, quantite: number | null): void {
    this.lignes.update((l) => l.map((x) => (x === ligne ? { ...x, quantite } : x)));
  }

  async enregistrer(): Promise<void> {
    const destination = this.destinationId();
    if (!destination || (this.gerant && !this.sourceId())) {
      this.erreur.set(this.gerant ? 'Choisissez le lieu de depart et le lieu d\'arrivee.' : 'Choisissez le lieu d\'arrivee.');
      return;
    }
    if (!this.valide()) {
      this.erreur.set('Ajoutez au moins un produit, avec une quantite pour chacun.');
      return;
    }
    const accepte = await this.operation.demander({
      titre: this.gerant ? 'Enregistrer ce transfert ?' : 'Envoyer cette demande de transfert au gerant ?',
      message: 'Verifiez les lieux et les quantites avant de valider.',
      libelleAction: this.gerant ? 'Enregistrer' : 'Envoyer'
    });
    if (!accepte || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.demanderTransfert({
      sourceId: this.gerant ? this.sourceId() : null,
      destinationId: destination,
      note: this.note.trim() || null,
      lignes: this.lignes().map((l) => ({ articleId: l.articleId, quantite: l.quantite as number }))
    }).subscribe({
      next: (t) => {
        this.envoi.set(false);
        this.enregistre.emit(t);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
