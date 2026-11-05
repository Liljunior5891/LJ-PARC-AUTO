import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { ProduitCourt, Transfert } from '../../../core/models/lieux';
import { LieuxService } from '../../../core/services/lieux.service';
import { ChampQuantite } from '../../../shared/champ-quantite';
import { normaliserCodeBarres } from '../../../shared/code-barres';
import { Modale } from '../../../shared/modale';

/** Produit sur le transfert en cours de saisie. */
interface LigneSaisie {
  articleId: number;
  nom: string;
  code: string;
  unite?: string | null;
  image?: string | null;
  /** Inconnu quand le lieu de depart n'est pas un des lieux de l'utilisateur. */
  disponible: number | null;
  quantite: number | null;
}

/** Produit propose : avec son disponible seulement si l'utilisateur voit le lieu de depart. */
type ProduitPropose = ProduitCourt & { disponible: number | null };

/**
 * Saisie d'un transfert, au format de la commande fournisseur : les deux lieux,
 * puis les produits ajoutes un a un par une recherche, avec le disponible du
 * lieu de depart et la quantite a deplacer.
 */
@Component({
  selector: 'app-formulaire-transfert',
  imports: [DecimalPipe, FormsModule, Modale, ChampQuantite],
  templateUrl: './formulaire-transfert.html',
  styleUrls: ['../stock/formulaire-commande.scss', './formulaire-transfert.scss']
})
export class FormulaireTransfert {
  private readonly service = inject(LieuxService);
  readonly gerant = inject(AuthService).aRole('ADMIN', 'GERANT');
  readonly etat = this.service.etat;

  readonly enregistre = output<Transfert>();
  readonly ferme = output<void>();

  readonly sourceId = signal<number | null>(null);
  readonly destinationId = signal<number | null>(null);
  readonly catalogue = signal<ProduitPropose[]>([]);
  /** Le stock d'un lieu qui n'est pas le sien reste masque : le gerant verifie a la validation. */
  readonly departVisible = computed(() =>
    (this.etat()?.mesLieux ?? []).some((l) => l.id === this.sourceId()));
  readonly lignes = signal<LigneSaisie[]>([]);
  readonly recherche = signal('');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  note = '';

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
    !!this.sourceId() && !!this.destinationId() && this.lignes().length > 0 && this.lignes().every((l) => (l.quantite ?? 0) > 0));

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
    if (!this.departVisible()) {
      this.service.produits().subscribe({
        next: (produits) => {
          this.catalogue.set(produits.map((p) => ({ ...p, disponible: null })));
          this.lignes.update((liste) => liste.map((l) => ({ ...l, disponible: null })));
        },
        error: (e) => this.erreur.set(e?.error?.message ?? 'Lecture des produits impossible.')
      });
      return;
    }
    this.service.stock(id, '', 0, 1000).subscribe({
      next: (p) => {
        this.catalogue.set(p.content);
        // Les produits deja choisis restent : leur disponible suit le nouveau lieu de depart.
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

  enregistrer(): void {
    const source = this.sourceId();
    const destination = this.destinationId();
    if (!source || !destination) {
      this.erreur.set('Choisissez le lieu de depart et le lieu d\'arrivee.');
      return;
    }
    if (!this.valide()) {
      this.erreur.set('Ajoutez au moins un produit, avec une quantite pour chacun.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.demanderTransfert({
      sourceId: source,
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
