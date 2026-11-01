import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Page } from '../../../core/models/models';
import {
  ArticlePdv,
  CommandeFournisseurDetail,
  CommandeFournisseurResume,
  LIBELLES_STATUT_COMMANDE_FOURNISSEUR,
  MouvementStock,
  StatutCommandeFournisseur,
  TYPES_MOUVEMENT_MANUELS,
  TypeMouvementStock
} from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { Modale } from '../../../shared/modale';
import { FormulaireCommande } from './formulaire-commande';

/**
 * Reapprovisionnement du point de vente : les commandes passees aux
 * fournisseurs (receptionnees plus tard pour entrer en stock), les produits
 * sous leur seuil d'alerte, et l'historique des mouvements avec la saisie des
 * sorties et des pertes.
 */
@Component({
  selector: 'app-stock-pdv',
  imports: [FormsModule, DecimalPipe, DatePipe, RouterLink, Modale, FormulaireCommande],
  templateUrl: './stock.html',
  styleUrl: './stock.scss'
})
export class StockPdv implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(PdvService);
  private readonly router = inject(Router);

  readonly onglet = signal<'commandes' | 'mouvements'>('commandes');
  readonly libellesStatut = LIBELLES_STATUT_COMMANDE_FOURNISSEUR;
  readonly commandes = signal<Page<CommandeFournisseurResume> | null>(null);
  readonly filtreStatut = signal<StatutCommandeFournisseur | null>(null);
  private pageCommandes = 0;
  /** Formulaire de nouvelle commande, eventuellement prerempli des produits sous leur seuil. */
  readonly nouvelleCommande = signal<ArticlePdv[] | null>(null);

  /** L'inventaire se fait sur sa feuille de comptage, a l'ecran Inventaire. */
  readonly types = TYPES_MOUVEMENT_MANUELS.filter((t) => t.valeur !== 'INVENTAIRE');
  readonly mouvements = signal<Page<MouvementStock> | null>(null);
  readonly alertes = signal<ArticlePdv[]>([]);
  readonly articles = signal<ArticlePdv[]>([]);
  readonly succes = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);

  articleFiltre: number | null = null;
  private numeroPage = 0;

  readonly saisie = signal(false);
  articleId: number | null = null;
  type: TypeMouvementStock = 'ENTREE';
  quantite: number | null = null;
  coutUnitaire: number | null = null;
  motif = '';

  readonly aideType = computed(() => this.types.find((t) => t.valeur === this.typeChoisi())?.aide ?? '');
  private readonly typeChoisi = signal<TypeMouvementStock>('ENTREE');

  ngOnInit(): void {
    this.chargerCommandes();
    this.charger();
    this.service.articles({ stockBas: true, actif: true, size: 50 }).subscribe({
      next: (p) => {
        this.alertes.set(p.content);
        // Venue des alertes de la barre du haut (meme depuis cette page) : la commande s'ouvre preremplie.
        this.route.queryParamMap.subscribe((parametres) => {
          const commander = parametres.get('commander');
          if (!commander) {
            return;
          }
          const produits = commander === 'tout' ? this.alertes() : this.alertes().filter((a) => a.id === Number(commander));
          if (produits.length > 0) {
            this.nouvelleCommande.set(produits);
          }
          void this.router.navigate([], { queryParams: {}, replaceUrl: true });
        });
      }
    });
    // Liste des articles suivis, pour la saisie d'un mouvement et le filtre.
    this.service.articles({ actif: true, size: 500 }).subscribe({
      next: (p) => this.articles.set(p.content.filter((a) => a.suiviStock))
    });
  }

  charger(): void {
    this.service.mouvements(this.articleFiltre ?? undefined, this.numeroPage).subscribe({
      next: (p) => this.mouvements.set(p),
      error: () => this.erreur.set('Chargement des mouvements impossible.')
    });
  }

  // ------------------------------------------------------------ commandes

  chargerCommandes(): void {
    this.service.commandesFournisseurs(this.filtreStatut(), this.pageCommandes).subscribe({
      next: (p) => this.commandes.set(p),
      error: () => this.erreur.set('Chargement des commandes impossible.')
    });
  }

  filtrerCommandes(statut: StatutCommandeFournisseur | null): void {
    this.filtreStatut.set(statut);
    this.pageCommandes = 0;
    this.chargerCommandes();
  }

  changerPageCommandes(sens: number): void {
    this.pageCommandes += sens;
    this.chargerCommandes();
  }

  ouvrirCommande(c: CommandeFournisseurResume): void {
    void this.router.navigate(['/pdv/stock/commandes', c.id]);
  }

  commandeEnregistree(detail: CommandeFournisseurDetail): void {
    this.nouvelleCommande.set(null);
    void this.router.navigate(['/pdv/stock/commandes', detail.resume.id]);
  }

  // ------------------------------------------------------------ mouvements

  filtrer(): void {
    this.numeroPage = 0;
    this.charger();
  }

  changerPage(sens: number): void {
    this.numeroPage += sens;
    this.charger();
  }

  ouvrirSaisie(article?: ArticlePdv, type: TypeMouvementStock = 'ENTREE'): void {
    this.articleId = article?.id ?? null;
    this.changerType(type);
    this.quantite = null;
    this.coutUnitaire = article?.prixAchat ?? null;
    this.motif = '';
    this.erreur.set(null);
    this.saisie.set(true);
  }

  changerType(type: TypeMouvementStock): void {
    this.type = type;
    this.typeChoisi.set(type);
  }

  stockDe(id: number | null): number | undefined {
    return this.articles().find((a) => a.id === id)?.stockActuel;
  }

  enregistrer(): void {
    if (!this.articleId || this.quantite === null || this.quantite < 0) {
      this.erreur.set("Choisissez l'article et indiquez une quantite.");
      return;
    }
    this.service
      .saisirMouvement({
        articleId: this.articleId,
        type: this.type,
        quantite: this.quantite,
        coutUnitaire: this.type === 'ENTREE' ? this.coutUnitaire : null,
        motif: this.motif || undefined
      })
      .subscribe({
        next: (m) => {
          this.saisie.set(false);
          this.succes.set(`${m.typeLibelle} enregistree : ${m.articleNom}, stock ${m.stockApres}.`);
          this.articles.update((liste) =>
            liste.map((a) => (a.id === m.articleId ? { ...a, stockActuel: m.stockApres } : a))
          );
          this.alertes.update((liste) =>
            liste
              .map((a) => (a.id === m.articleId ? { ...a, stockActuel: m.stockApres } : a))
              .filter((a) => a.seuilAlerte == null || a.stockActuel <= a.seuilAlerte)
          );
          this.charger();
        },
        error: (e) =>
          this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Enregistrement impossible.')
      });
  }
}
