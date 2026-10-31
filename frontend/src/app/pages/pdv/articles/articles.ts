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
  ICONES_CATEGORIES,
  POSTES_PREPARATION,
  PostePreparation
} from '../../../core/models/pdv';
import { reduirePhoto } from '../../../shared/photo-article';
import { AuthService } from '../../../core/auth/auth.service';
import { PdvService } from '../../../core/services/pdv.service';
import { ExportPdv } from '../../../shared/export-pdv';
import { ImportPdv, messageImport } from '../../../shared/import-pdv';
import { Modale } from '../../../shared/modale';

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
}

const VIDE: FormulaireArticle = {
  code: '', codeBarres: '', nom: '', categorieId: null, prixVente: null, prixAchat: null,
  unite: '', suiviStock: true, stockInitial: null, seuilAlerte: null, image: null
};

/**
 * Catalogue du point de vente : articles, prix, codes-barres et categories.
 * La liste s'exporte en Excel, PDF ou CSV et s'importe depuis un tableur ;
 * chaque produit suivi en stock ouvre sa fiche de stock.
 */
@Component({
  selector: 'app-articles-pdv',
  imports: [FormsModule, DecimalPipe, RouterLink, Modale, ExportPdv, ImportPdv],
  templateUrl: './articles.html',
  styleUrl: './articles.scss'
})
export class ArticlesPdv implements OnInit {
  private readonly service = inject(PdvService);
  /** En restauration, chaque categorie indique l'ecran (cuisine ou bar) qui recoit ses articles. */
  readonly restauration = inject(AuthService).estRestauration();
  readonly postes = POSTES_PREPARATION;
  readonly icones = ICONES_CATEGORIES;
  /** Categorie dont on choisit le pictogramme, ou 'nouvelle' pour celle en cours de creation. */
  readonly choixIcone = signal<CategoriePdv | 'nouvelle' | null>(null);

  readonly page = signal<Page<ArticlePdv> | null>(null);
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

  // Categories
  readonly gestionCategories = signal(false);
  nouvelleCategorie: { nom: string; couleur: string; poste: PostePreparation; icone: string } = {
    nom: '', couleur: '#1f6fd1', poste: 'CUISINE', icone: '🍽️'
  };

  ngOnInit(): void {
    this.chargerCategories();
    this.charger();
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
          image: article.image ?? null
        }
      : { ...VIDE };
    this.edition.set(article ?? 'nouveau');
  }

  enregistrer(): void {
    const f = this.formulaire;
    if (!f.nom.trim() || f.prixVente === null || f.prixVente < 0) {
      this.erreurFormulaire.set('Le nom et un prix de vente positif sont obligatoires.');
      return;
    }
    const requete: ArticleRequete = {
      code: f.code.trim() || undefined,
      codeBarres: f.codeBarres.trim() || undefined,
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

  // ------------------------------------------------------------ categories

  ajouterCategorie(): void {
    if (!this.nouvelleCategorie.nom.trim()) {
      return;
    }
    this.service
      .creerCategorie({
        nom: this.nouvelleCategorie.nom.trim(),
        couleur: this.nouvelleCategorie.couleur,
        ordre: this.categories().length,
        icone: this.nouvelleCategorie.icone,
        poste: this.restauration ? this.nouvelleCategorie.poste : 'AUCUN'
      })
      .subscribe({
        next: () => {
          this.nouvelleCategorie = { ...this.nouvelleCategorie, nom: '' };
          this.chargerCategories();
        },
        error: (e) => this.erreurFormulaire.set(this.message(e, 'Creation de la categorie impossible.'))
      });
  }

  basculerCategorie(categorie: CategoriePdv): void {
    this.service
      .modifierCategorie(categorie.id, { ...categorie, actif: !categorie.actif })
      .subscribe({ next: () => this.chargerCategories() });
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

  choisirIcone(icone: string): void {
    const cible = this.choixIcone();
    this.choixIcone.set(null);
    if (cible === 'nouvelle') {
      this.nouvelleCategorie = { ...this.nouvelleCategorie, icone };
    } else if (cible) {
      this.service.modifierCategorie(cible.id, { ...cible, icone }).subscribe({
        next: () => this.chargerCategories(),
        error: (e) => this.erreurFormulaire.set(this.message(e, 'Modification impossible.'))
      });
    }
  }

  changerPoste(categorie: CategoriePdv, poste: PostePreparation): void {
    this.service
      .modifierCategorie(categorie.id, { ...categorie, poste })
      .subscribe({
        next: () => this.chargerCategories(),
        error: (e) => this.erreurFormulaire.set(this.message(e, 'Modification impossible.'))
      });
  }

  private chargerCategories(): void {
    this.service.categories().subscribe({ next: (c) => this.categories.set(c) });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
