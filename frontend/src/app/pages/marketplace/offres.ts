import { DecimalPipe, LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { OperationService } from '../../core/operation/operation.service';
import {
  LIBELLES_TYPE_OFFRE,
  Offre,
  OffreRequete,
  SourcesOffres,
  TypeOffre
} from '../../core/models/marketplace';
import { MarketplaceService } from '../../core/services/marketplace.service';
import { Modale } from '../../shared/modale';
import { reduirePhoto } from '../../shared/photo-article';

/** Offre en cours de saisie dans la fenetre (creation ou modification). */
interface Edition {
  id: number | null;
  requete: OffreRequete;
  /** Pour un article : prix et photo du point de vente, repris si la boutique n'en fixe pas. */
  prixSource?: number;
  imageSource?: string;
}

/**
 * Offres de la boutique : articles du point de vente, vehicules a louer et
 * produits saisis ici. Chaque offre se publie, se masque ou se met en avant.
 */
@Component({
  selector: 'app-offres-marketplace',
  imports: [FormsModule, DecimalPipe, LowerCasePipe, Modale],
  templateUrl: './offres.html',
  styleUrl: './marketplace.scss'
})
export class OffresMarketplace implements OnInit {
  private readonly service = inject(MarketplaceService);
  private readonly operation = inject(OperationService);

  readonly libellesType = LIBELLES_TYPE_OFFRE;
  readonly offres = signal<Offre[]>([]);
  readonly sources = signal<SourcesOffres | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly filtreType = signal<TypeOffre | ''>('');
  readonly recherche = signal('');
  readonly offresAffichees = computed(() => {
    const type = this.filtreType();
    const texte = this.recherche().trim().toLowerCase();
    return this.offres().filter((o) =>
      (!type || o.type === type) && (!texte || o.titre.toLowerCase().includes(texte)
        || (o.categorie ?? '').toLowerCase().includes(texte)));
  });

  /** Fenetre de publication d'articles du point de vente. */
  readonly choixArticles = signal(false);
  readonly articlesCoches = signal<Set<number>>(new Set());
  readonly rechercheArticle = signal('');
  readonly articlesProposes = computed(() => {
    const texte = this.rechercheArticle().trim().toLowerCase();
    return (this.sources()?.articles ?? []).filter((a) => !a.publiee
      && (!texte || a.nom.toLowerCase().includes(texte) || a.code.toLowerCase().includes(texte)
        || (a.categorie ?? '').toLowerCase().includes(texte)));
  });
  readonly vehiculesProposes = computed(() => (this.sources()?.vehicules ?? []).filter((v) => !v.publiee));

  readonly edition = signal<Edition | null>(null);
  readonly enregistrement = signal(false);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.offres().subscribe({
      next: (offres) => {
        this.offres.set(offres);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des offres impossible.');
      }
    });
    this.service.sources().subscribe({ next: (sources) => this.sources.set(sources) });
  }

  // ------------------------------------------------------- Articles du PDV

  ouvrirArticles(): void {
    this.articlesCoches.set(new Set());
    this.rechercheArticle.set('');
    this.choixArticles.set(true);
  }

  basculerArticle(id: number): void {
    this.articlesCoches.update((coches) => {
      const suivant = new Set(coches);
      if (suivant.has(id)) {
        suivant.delete(id);
      } else {
        suivant.add(id);
      }
      return suivant;
    });
  }

  toutCocher(): void {
    this.articlesCoches.set(new Set(this.articlesProposes().map((a) => a.id)));
  }

  publierArticles(): void {
    const ids = [...this.articlesCoches()];
    if (!ids.length) {
      return;
    }
    this.service.publierArticles(ids).subscribe({
      next: (creees) => {
        this.choixArticles.set(false);
        this.succes.set(`${creees.length} article(s) publie(s) dans la boutique.`);
        this.charger();
      },
      error: (reponse) => this.erreur.set(reponse.error?.message ?? 'Publication impossible.')
    });
  }

  // ------------------------------------------------- Vehicule, produit, modification

  nouveauVehicule(): void {
    const vehicule = this.vehiculesProposes()[0];
    this.edition.set({
      id: null,
      requete: {
        type: 'VEHICULE', camionId: vehicule?.id ?? null, titre: vehicule ? vehicule.libelle : '',
        prix: null, caution: null, publiee: true, miseEnAvant: false, ordre: 0,
        categorie: 'Location de vehicules'
      }
    });
  }

  nouveauProduit(): void {
    this.edition.set({
      id: null,
      requete: { type: 'LIBRE', titre: '', prix: null, stock: null, publiee: true, miseEnAvant: false, ordre: 0 }
    });
  }

  modifier(o: Offre): void {
    this.edition.set({
      id: o.id,
      requete: {
        type: o.type, articleId: o.articleId, camionId: o.camionId, titre: o.titre, description: o.description,
        categorie: o.categorie, prix: o.prix ?? null, unitePrix: o.unitePrix, caution: o.caution ?? null,
        image: o.image ?? null, stock: o.stock ?? null, publiee: o.publiee, miseEnAvant: o.miseEnAvant,
        ordre: o.ordre
      },
      prixSource: o.type === 'ARTICLE' && o.prix == null ? o.prixAffiche : undefined,
      imageSource: o.type === 'ARTICLE' && !o.image ? o.imageAffichee : undefined
    });
  }

  /** Vehicule choisi : le titre suit tant qu'il n'a pas ete personnalise. */
  choisirVehicule(id: number): void {
    const e = this.edition();
    if (!e) {
      return;
    }
    const avant = this.sources()?.vehicules.find((v) => v.id === e.requete.camionId);
    const apres = this.sources()?.vehicules.find((v) => v.id === Number(id));
    const titre = !e.requete.titre || e.requete.titre === avant?.libelle ? (apres?.libelle ?? '') : e.requete.titre;
    this.edition.set({ ...e, requete: { ...e.requete, camionId: Number(id), titre } });
  }

  async choisirImage(evenement: Event): Promise<void> {
    const entree = evenement.target as HTMLInputElement;
    const fichier = entree.files?.[0];
    entree.value = '';
    const e = this.edition();
    if (!fichier || !e) {
      return;
    }
    try {
      const image = await reduirePhoto(fichier);
      this.edition.set({ ...e, requete: { ...e.requete, image } });
    } catch (err) {
      this.erreur.set((err as Error).message);
    }
  }

  retirerImage(): void {
    const e = this.edition();
    if (e) {
      this.edition.set({ ...e, requete: { ...e.requete, image: null } });
    }
  }

  enregistrer(): void {
    const e = this.edition();
    if (!e || this.enregistrement()) {
      return;
    }
    this.enregistrement.set(true);
    this.erreur.set(null);
    const appel = e.id === null ? this.service.creerOffre(e.requete) : this.service.modifierOffre(e.id, e.requete);
    appel.subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.edition.set(null);
        this.succes.set(e.id === null ? 'Offre publiee.' : 'Offre modifiee.');
        this.charger();
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(reponse.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  /** Publier ou masquer d'un clic, sans ouvrir la fenetre. */
  basculerPublication(o: Offre): void {
    this.service.modifierOffre(o.id, {
      type: o.type, articleId: o.articleId, camionId: o.camionId, titre: o.titre, description: o.description,
      categorie: o.categorie, prix: o.prix ?? null, unitePrix: o.unitePrix, caution: o.caution ?? null,
      image: o.image ?? null, stock: o.stock ?? null, publiee: !o.publiee, miseEnAvant: o.miseEnAvant,
      ordre: o.ordre
    }).subscribe({
      next: (modifiee) => this.offres.update((liste) => liste.map((x) => (x.id === modifiee.id ? modifiee : x))),
      error: (reponse) => this.erreur.set(reponse.error?.message ?? 'Modification impossible.')
    });
  }

  async retirer(o: Offre): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Retirer « ${o.titre} » de la boutique ?`,
      message: o.type === 'ARTICLE' ? 'L\'article reste dans le point de vente.' : 'Les commandes passees restent consultables.',
      libelleAction: 'Retirer',
      danger: true
    });
    if (!accepte) {
      return;
    }
    this.service.supprimerOffre(o.id).subscribe({
      next: () => this.charger(),
      error: (reponse) => this.erreur.set(reponse.error?.message ?? 'Retrait impossible.')
    });
  }
}
