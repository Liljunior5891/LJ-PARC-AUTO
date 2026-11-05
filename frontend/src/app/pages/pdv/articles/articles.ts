import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Page } from '../../../core/models/models';
import { BilanImport } from '../../../core/models/rapport';
import {
  ArticlePdv,
  ArticleRequete,
  CategoriePdv,
  UNITES_MESURE
} from '../../../core/models/pdv';
import { reduirePhoto } from '../../../shared/photo-article';
import { AuthService } from '../../../core/auth/auth.service';
import { MarketplaceService } from '../../../core/services/marketplace.service';
import { PdvService } from '../../../core/services/pdv.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { ExportPdv } from '../../../shared/export-pdv';
import { ImportPdv, messageImport } from '../../../shared/import-pdv';
import { Modale } from '../../../shared/modale';
import { normaliserCodeBarres } from '../../../shared/code-barres';
import { FenetreCategorie } from '../../../shared/fenetre-categorie';
import { GaleriePhotos } from '../../../shared/galerie-photos';

/** Valeurs du formulaire d'article, nombres compris ; null pour un champ vide. */
interface FormulaireArticle {
  code: string;
  codeBarres: string;
  nom: string;
  categorieId: number | null;
  prixVente: number | null;
  prixAchat: number | null;
  unite: string;
  suiviStock: boolean;
  stockInitial: number | null;
  seuilAlerte: number | null;
  image: string | null;
  /** Point de vente avec Marketplace : vendu aussi dans la boutique en ligne. */
  venteEnLigne: boolean;
}

const VIDE: FormulaireArticle = {
  code: '', codeBarres: '', nom: '', categorieId: null, prixVente: null, prixAchat: null,
  unite: '', suiviStock: true, stockInitial: null, seuilAlerte: null, image: null, venteEnLigne: false
};

/**
 * Catalogue du point de vente : articles, prix, codes-barres et categories.
 * La liste s'exporte en Excel, PDF ou CSV et s'importe depuis un tableur ;
 * chaque produit suivi en stock ouvre sa fiche de stock.
 */
@Component({
  selector: 'app-articles-pdv',
  imports: [FormsModule, DecimalPipe, RouterLink, Modale, ExportPdv, ImportPdv, GaleriePhotos, FenetreCategorie],
  templateUrl: './articles.html',
  styleUrl: './articles.scss'
})
export class ArticlesPdv implements OnInit {
  private readonly service = inject(PdvService);
  readonly page = signal<Page<ArticlePdv> | null>(null);

  // Point de vente et Marketplace : chaque produit se met en vente en ligne depuis cette liste.
  private readonly marketplace = inject(MarketplaceService);
  readonly avecEnLigne = inject(AuthService).aModule('MARKETPLACE');
  /** Autres photos montrees dans la boutique en ligne (galerie de l'offre). */
  readonly photosEnLigne = signal<string[]>([]);
  private photosInitiales = '[]';
  /** Publication en ligne par article (absent : jamais publie). */
  readonly enLigne = signal<Map<number, boolean>>(new Map());
  filtreEnLigne: '' | 'oui' | 'non' = '';
  readonly categories = signal<CategoriePdv[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly importOuvert = signal(false);

  recherche = '';
  categorieId: number | null = null;
  filtreStock = false;
  masquerArchives = true;
  private numeroPage = 0;

  // Fiche article
  readonly edition = signal<ArticlePdv | 'nouveau' | null>(null);
  formulaire: FormulaireArticle = { ...VIDE };
  readonly erreurFormulaire = signal<string | null>(null);
  /** Fenetre de creation d'une categorie, ouverte par-dessus la fiche. */
  readonly nouvelleCategorie = signal(false);
  /** Unites du formulaire : la liste commune, plus celle de l'article si elle n'en fait pas partie (saisie ancienne, import). */
  unites = UNITES_MESURE;

  // Quincaillerie : autres unites de vente du produit (tonne, botte, carton), si l'administrateur les a activees.
  private readonly materiauxService = inject(MateriauxService);
  private readonly materiaux = inject(AuthService).estMateriaux();
  readonly conditionnementsActifs = signal(false);
  conditionnements: { id?: number | null; nom: string; coefficient: number | null; prixVente: number | null; codeBarres: string }[] = [];
  private conditionnementsInitiaux = '[]';

  ngOnInit(): void {
    this.chargerCategories();
    this.charger();
    this.chargerEnLigne();
    // Quincaillerie : les conditionnements ne s'editent que si l'administrateur les a actives.
    if (this.materiaux) {
      this.materiauxService.reglages().subscribe({ next: (r) => this.conditionnementsActifs.set(r.conditionnementsActifs) });
    }
  }

  ajouterConditionnement(): void {
    this.conditionnements = [...this.conditionnements, { nom: '', coefficient: null, prixVente: null, codeBarres: '' }];
  }

  retirerConditionnement(index: number): void {
    this.conditionnements = this.conditionnements.filter((_, i) => i !== index);
  }

  /** Enregistre les conditionnements de l'article s'ils ont change ; les lignes incompletes sont ignorees. */
  private sauverConditionnements(articleId: number): void {
    if (!this.conditionnementsActifs() || JSON.stringify(this.conditionnements) === this.conditionnementsInitiaux) {
      return;
    }
    const liste = this.conditionnements
      .filter((c) => c.nom.trim() && (c.coefficient ?? 0) > 0 && c.prixVente !== null && c.prixVente >= 0)
      .map((c) => ({
        id: c.id ?? null,
        nom: c.nom.trim(),
        coefficient: Number(c.coefficient),
        prixVente: Number(c.prixVente),
        codeBarres: normaliserCodeBarres(c.codeBarres) || null
      }));
    this.materiauxService.enregistrerConditionnements(articleId, liste).subscribe({
      error: (e) => this.erreur.set(this.message(e, 'Article enregistre, mais ses autres unites de vente n\'ont pas pu l\'etre.'))
    });
  }

  chargerEnLigne(): void {
    if (!this.avecEnLigne) {
      return;
    }
    this.marketplace.articlesEnLigne().subscribe({
      next: (liste) => this.enLigne.set(new Map(liste.map((a) => [a.articleId, a.publiee])))
    });
  }

  /** En ligne ou retire de la boutique, d'un clic. */
  basculerEnLigne(article: ArticlePdv): void {
    const publiee = !this.enLigne().get(article.id);
    this.marketplace.publierArticle(article.id, publiee).subscribe({
      next: (r) => {
        this.enLigne.update((carte) => new Map(carte).set(r.articleId, r.publiee));
        if (this.filtreEnLigne) {
          this.charger();
        }
      },
      error: (e) => this.erreur.set(this.message(e, 'Publication en ligne impossible.'))
    });
  }

  importTermine(bilan: BilanImport): void {
    this.importOuvert.set(false);
    this.succes.set(messageImport(bilan));
    this.charger();
  }

  filtrer(): void {
    this.numeroPage = 0;
    this.charger();
  }

  changerPage(sens: number): void {
    this.numeroPage += sens;
    this.charger();
  }

  charger(): void {
    this.service
      .articles({
        q: this.recherche,
        categorieId: this.categorieId ?? undefined,
        actif: this.masquerArchives ? true : undefined,
        stockBas: this.filtreStock,
        enLigne: this.filtreEnLigne === '' ? undefined : this.filtreEnLigne === 'oui',
        page: this.numeroPage
      })
      .subscribe({
        next: (page) => this.page.set(page),
        error: () => this.erreur.set('Chargement des articles impossible.')
      });
  }

  ouvrir(article?: ArticlePdv): void {
    this.erreurFormulaire.set(null);
    this.formulaire = article
      ? {
          code: article.code,
          codeBarres: article.codeBarres ?? '',
          nom: article.nom,
          categorieId: article.categorieId ?? null,
          prixVente: article.prixVente,
          prixAchat: article.prixAchat ?? null,
          unite: article.unite ?? '',
          suiviStock: article.suiviStock,
          stockInitial: null,
          seuilAlerte: article.seuilAlerte ?? null,
          image: article.image ?? null,
          venteEnLigne: !!this.enLigne().get(article.id)
        }
      : { ...VIDE };
    this.preparerUnites();
    this.photosEnLigne.set([]);
    this.photosInitiales = '[]';
    this.conditionnements = [];
    this.conditionnementsInitiaux = '[]';
    if (article && this.conditionnementsActifs()) {
      this.materiauxService.conditionnementsDuProduit(article.id).subscribe({
        next: (liste) => {
          this.conditionnements = liste
            .filter((c) => c.actif)
            .map((c) => ({ id: c.id, nom: c.nom, coefficient: c.coefficient, prixVente: c.prixVente, codeBarres: c.codeBarres ?? '' }));
          this.conditionnementsInitiaux = JSON.stringify(this.conditionnements);
        }
      });
    }
    if (article && this.avecEnLigne && this.enLigne().get(article.id)) {
      this.marketplace.photosArticle(article.id).subscribe({
        next: (r) => {
          this.photosEnLigne.set(r.photos);
          this.photosInitiales = JSON.stringify(r.photos);
        }
      });
    }
    this.edition.set(article ?? 'nouveau');
  }

  /** Aligne l'unite de l'article sur la liste (« KG » devient « kg ») ; une unite inconnue reste proposee, pour ne pas la perdre. */
  private preparerUnites(): void {
    const unite = this.formulaire.unite.trim();
    const connue = UNITES_MESURE.find((u) => u.valeur.toLowerCase() === unite.toLowerCase());
    this.formulaire.unite = connue?.valeur ?? unite;
    this.unites = unite && !connue ? [{ valeur: unite, libelle: unite }, ...UNITES_MESURE] : UNITES_MESURE;
  }

  enregistrer(): void {
    const f = this.formulaire;
    if (!f.nom.trim() || f.prixVente === null || f.prixVente < 0) {
      this.erreurFormulaire.set('Le nom et un prix de vente positif sont obligatoires.');
      return;
    }
    const requete: ArticleRequete = {
      code: f.code.trim() || undefined,
      codeBarres: normaliserCodeBarres(f.codeBarres) || undefined,
      nom: f.nom.trim(),
      categorieId: f.categorieId,
      prixVente: f.prixVente,
      prixAchat: f.prixAchat,
      unite: f.unite.trim() || undefined,
      suiviStock: f.suiviStock,
      stockInitial: f.suiviStock ? f.stockInitial : null,
      seuilAlerte: f.suiviStock ? f.seuilAlerte : null,
      image: f.image
    };
    const edite = this.edition();
    const appel = edite && edite !== 'nouveau'
      ? this.service.modifierArticle(edite.id, requete)
      : this.service.creerArticle(requete);
    appel.subscribe({
      next: (article) => {
        this.edition.set(null);
        this.succes.set(`Article ${article.nom} enregistre (${article.code}).`);
        this.sauverConditionnements(article.id);
        // Vente en ligne choisie dans la fiche : publiee ou retiree de la boutique si elle a change.
        // Les autres photos suivent la vente en ligne : envoyees seulement si elles ont change.
        const photos = JSON.stringify(this.photosEnLigne()) !== this.photosInitiales ? this.photosEnLigne() : undefined;
        if (this.avecEnLigne && (f.venteEnLigne !== !!this.enLigne().get(article.id) || (f.venteEnLigne && photos))) {
          this.marketplace.publierArticle(article.id, f.venteEnLigne, f.venteEnLigne ? photos : undefined).subscribe({
            next: (r) => {
              this.enLigne.update((carte) => new Map(carte).set(r.articleId, r.publiee));
              this.charger();
            },
            error: (e) => this.erreur.set(this.message(e, 'Article enregistre, mais la vente en ligne n\'a pas pu etre modifiee.'))
          });
          return;
        }
        this.charger();
      },
      error: (e) => this.erreurFormulaire.set(this.message(e, "Enregistrement de l'article impossible."))
    });
  }

  archiver(article: ArticlePdv): void {
    this.service.archiverArticle(article.id).subscribe({
      next: () => {
        this.succes.set(`${article.nom} retire de la caisse.`);
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Archivage impossible.'))
    });
  }

  reactiver(article: ArticlePdv): void {
    this.service
      .modifierArticle(article.id, {
        ...article,
        categorieId: article.categorieId ?? null,
        actif: true
      })
      .subscribe({ next: () => this.charger(), error: (e) => this.erreur.set(this.message(e, 'Reactivation impossible.')) });
  }

  /** La categorie creee rejoint les listes et s'applique au produit en cours de saisie. */
  categorieCreee(categorie: CategoriePdv): void {
    this.nouvelleCategorie.set(false);
    this.categories.update((liste) => [...liste, categorie]);
    this.formulaire = { ...this.formulaire, categorieId: categorie.id };
  }

  couleurCategorie(id: number | null): string {
    return this.categories().find((c) => c.id === id)?.couleur ?? '#8a94a0';
  }

  iconeCategorie(id: number | null): string {
    return this.categories().find((c) => c.id === id)?.icone ?? '📷';
  }

  /** La photo est reduite ici, avant l'envoi : quelques dizaines de Ko au lieu de plusieurs Mo. */
  choisirPhoto(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) {
      return;
    }
    reduirePhoto(fichier).then(
      (image) => (this.formulaire = { ...this.formulaire, image }),
      (e: Error) => this.erreurFormulaire.set(e.message)
    );
  }

  retirerPhoto(): void {
    this.formulaire = { ...this.formulaire, image: null };
  }

  private chargerCategories(): void {
    this.service.categories().subscribe({ next: (c) => this.categories.set(c) });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
