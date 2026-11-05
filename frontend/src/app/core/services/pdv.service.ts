import { HttpClient, HttpContext, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/models';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { CommandeEnLigne, StatutCommandeEnLigne } from '../models/marketplace';
import {
  ArticlePdv,
  ArticleRequete,
  CaissePdv,
  CommandeFournisseurDetail,
  CommandeFournisseurRequete,
  CommandeFournisseurResume,
  StatutCommandeFournisseur,
  CaisseRequete,
  MembrePersonnel,
  BilanInventaire,
  Billettage,
  ControleCaisse,
  CategoriePdv,
  CategorieRequete,
  Encaissement,
  MouvementStock,
  SaisieStock,
  SessionCaisse,
  TableauBordPdv,
  VenteProduitSession,
  Ticket,
  VenteResume
} from '../models/pdv';

export interface FiltreArticle {
  q?: string;
  categorieId?: number;
  actif?: boolean;
  stockBas?: boolean;
  page?: number;
  size?: number;
  /** Publie (vrai) ou non (faux) dans la boutique en ligne. */
  enLigne?: boolean;
}

/** Acces a l'API du module Point de vente. */
@Injectable({ providedIn: 'root' })
export class PdvService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv`;

  // ------------------------------------------------------------ catalogue

  categories(): Observable<CategoriePdv[]> {
    return this.http.get<CategoriePdv[]>(`${this.base}/categories`);
  }

  creerCategorie(requete: CategorieRequete): Observable<CategoriePdv> {
    return this.http.post<CategoriePdv>(`${this.base}/categories`, requete);
  }

  modifierCategorie(id: number, requete: CategorieRequete): Observable<CategoriePdv> {
    return this.http.put<CategoriePdv>(`${this.base}/categories/${id}`, requete);
  }

  articles(filtre: FiltreArticle = {}): Observable<Page<ArticlePdv>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'nom,asc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.categorieId) {
      params = params.set('categorieId', filtre.categorieId);
    }
    if (filtre.actif !== undefined) {
      params = params.set('actif', filtre.actif);
    }
    if (filtre.stockBas) {
      params = params.set('stockBas', true);
    }
    if (filtre.enLigne !== undefined) {
      params = params.set('enLigne', filtre.enLigne);
    }
    return this.http.get<Page<ArticlePdv>>(`${this.base}/articles`, { params });
  }

  catalogueCaisse(): Observable<ArticlePdv[]> {
    return this.http.get<ArticlePdv[]>(`${this.base}/articles/caisse`);
  }

  articleParCode(code: string): Observable<ArticlePdv> {
    return this.http.get<ArticlePdv>(`${this.base}/articles/code/${encodeURIComponent(code)}`);
  }

  creerArticle(requete: ArticleRequete): Observable<ArticlePdv> {
    return this.http.post<ArticlePdv>(`${this.base}/articles`, requete);
  }

  modifierArticle(id: number, requete: ArticleRequete): Observable<ArticlePdv> {
    return this.http.put<ArticlePdv>(`${this.base}/articles/${id}`, requete);
  }

  archiverArticle(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/articles/${id}`);
  }

  // ---------------------------------------------------------------- stock

  mouvements(articleId?: number, page = 0): Observable<Page<MouvementStock>> {
    let params = new HttpParams().set('page', page).set('size', 30);
    if (articleId) {
      params = params.set('articleId', articleId);
    }
    return this.http.get<Page<MouvementStock>>(`${this.base}/stock/mouvements`, { params });
  }

  saisirMouvement(saisie: SaisieStock): Observable<MouvementStock> {
    return this.http.post<MouvementStock>(`${this.base}/stock/mouvements`, saisie);
  }

  // ------------------------------------------------ reapprovisionnement

  commandesFournisseurs(statut?: StatutCommandeFournisseur | null, page = 0): Observable<Page<CommandeFournisseurResume>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (statut) {
      params = params.set('statut', statut);
    }
    return this.http.get<Page<CommandeFournisseurResume>>(`${this.base}/approvisionnement/commandes`, { params });
  }

  commandeFournisseur(id: number): Observable<CommandeFournisseurDetail> {
    return this.http.get<CommandeFournisseurDetail>(`${this.base}/approvisionnement/commandes/${id}`);
  }

  fournisseurs(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/approvisionnement/commandes/fournisseurs`);
  }

  creerCommandeFournisseur(requete: CommandeFournisseurRequete): Observable<CommandeFournisseurDetail> {
    return this.http.post<CommandeFournisseurDetail>(`${this.base}/approvisionnement/commandes`, requete);
  }

  modifierCommandeFournisseur(id: number, requete: CommandeFournisseurRequete): Observable<CommandeFournisseurDetail> {
    return this.http.put<CommandeFournisseurDetail>(`${this.base}/approvisionnement/commandes/${id}`, requete);
  }

  /** Quantites recues par ligne ; une ligne absente est recue en totalite. */
  receptionnerCommandeFournisseur(id: number, lignes: { ligneId: number; quantiteRecue: number; coutUnitaire?: number | null }[], lieuId?: number | null):
    Observable<CommandeFournisseurDetail> {
    return this.http.post<CommandeFournisseurDetail>(`${this.base}/approvisionnement/commandes/${id}/reception`, { lignes, lieuId });
  }

  annulerCommandeFournisseur(id: number): Observable<CommandeFournisseurDetail> {
    return this.http.post<CommandeFournisseurDetail>(`${this.base}/approvisionnement/commandes/${id}/annulation`, {});
  }

  /** Feuille d'inventaire validee en une fois : un mouvement par ecart constate. */
  validerInventaire(comptages: { articleId: number; quantite: number }[], motif?: string, lieuId?: number | null): Observable<BilanInventaire> {
    return this.http.post<BilanInventaire>(`${this.base}/stock/inventaire`, { comptages, motif, lieuId });
  }

  // --------------------------------------------------------------- caisses

  /** Sessions ouvertes (par un gerant) sur lesquelles l'utilisateur peut encaisser. */
  sessionsOuvertes(): Observable<SessionCaisse[]> {
    return this.http.get<SessionCaisse[]>(`${this.base}/sessions/ouvertes`);
  }

  /** Valeurs des billets et pieces a compter, de la plus forte a la plus faible. */
  coupures(): Observable<number[]> {
    return this.http.get<number[]>(`${this.base}/sessions/coupures`);
  }

  /** Un gerant ouvre la session d'une caisse, avec le fond compte par coupure. */
  ouvrirSession(caisseId: number, billettage: Billettage): Observable<SessionCaisse> {
    return this.http.post<SessionCaisse>(`${this.base}/sessions`, { caisseId, billettage });
  }

  mouvementCaisse(id: number, type: 'ENTREE' | 'SORTIE', montant: number, motif: string): Observable<SessionCaisse> {
    return this.http.post<SessionCaisse>(`${this.base}/sessions/${id}/mouvements`, { type, montant, motif });
  }

  /** Comptage a l'aveugle : especes attendues et ecart, sans fermer. */
  controlerSession(id: number, billettage: Billettage): Observable<ControleCaisse> {
    return this.http.post<ControleCaisse>(`${this.base}/sessions/${id}/controle`, { billettage },
      { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  fermerSession(id: number, billettage: Billettage, justification?: string, observations?: string): Observable<SessionCaisse> {
    return this.http.post<SessionCaisse>(`${this.base}/sessions/${id}/cloture`, { billettage, justification, observations });
  }

  /** Ventes validees d'une session regroupees par produit. */
  ventesParProduit(sessionId: number): Observable<VenteProduitSession[]> {
    return this.http.get<VenteProduitSession[]>(`${this.base}/sessions/${sessionId}/produits`);
  }

  /** Rapport d'activite de la session, en PDF. */
  rapportSessionPdf(id: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`${this.base}/sessions/${id}/rapport-pdf`, {
      params: new HttpParams().set('fuseau', Intl.DateTimeFormat().resolvedOptions().timeZone || 'Africa/Lome'),
      responseType: 'blob',
      observe: 'response'
    });
  }

  caisses(): Observable<CaissePdv[]> {
    return this.http.get<CaissePdv[]>(`${this.base}/caisses`);
  }

  caisse(id: number): Observable<CaissePdv> {
    return this.http.get<CaissePdv>(`${this.base}/caisses/${id}`);
  }

  /** Caisses que l'utilisateur connecte peut ouvrir. */
  caissesDisponibles(): Observable<CaissePdv[]> {
    return this.http.get<CaissePdv[]>(`${this.base}/caisses/disponibles`);
  }

  creerCaisse(requete: CaisseRequete): Observable<CaissePdv> {
    return this.http.post<CaissePdv>(`${this.base}/caisses`, requete);
  }

  modifierCaisse(id: number, requete: CaisseRequete): Observable<CaissePdv> {
    return this.http.put<CaissePdv>(`${this.base}/caisses/${id}`, requete);
  }

  personnel(): Observable<MembrePersonnel[]> {
    return this.http.get<MembrePersonnel[]>(`${this.base}/personnel`);
  }


  sessions(page = 0, caisseId?: number, taille = 20): Observable<Page<SessionCaisse>> {
    let params = new HttpParams().set('page', page).set('size', taille);
    if (caisseId) {
      params = params.set('caisseId', caisseId);
    }
    return this.http.get<Page<SessionCaisse>>(`${this.base}/sessions`, { params });
  }

  session(id: number): Observable<SessionCaisse> {
    return this.http.get<SessionCaisse>(`${this.base}/sessions/${id}`);
  }

  // ---------------------------------------------------------------- ventes

  /**
   * La fenetre d'encaissement tient lieu de confirmation : une seconde
   * question a chaque ticket ralentirait la file d'attente.
   */
  /** Commandes de la boutique en ligne qui attendent la caisse boutique. */
  commandesEnLigne(): Observable<CommandeEnLigne[]> {
    return this.http.get<CommandeEnLigne[]>(`${this.base}/commandes-en-ligne`);
  }

  /** Accepte, marque prete ou refuse une commande en ligne (geste de caisse, sans confirmation). */
  changerStatutEnLigne(id: number, statut: StatutCommandeEnLigne, motif?: string): Observable<CommandeEnLigne> {
    return this.http.post<CommandeEnLigne>(`${this.base}/commandes-en-ligne/${id}/statut`, { statut, motif }, {
      context: new HttpContext().set(SANS_SUIVI, true)
    });
  }

  encaisser(encaissement: Encaissement): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/ventes`, encaissement, {
      context: new HttpContext().set(SANS_SUIVI, true)
    });
  }

  ventes(filtre: { debut?: string; fin?: string; sessionId?: number; enLigne?: boolean; page?: number }): Observable<Page<VenteResume>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', 30);
    if (filtre.enLigne !== undefined) {
      params = params.set('enLigne', filtre.enLigne);
    }
    if (filtre.sessionId) {
      params = params.set('sessionId', filtre.sessionId);
    } else {
      if (filtre.debut) {
        params = params.set('debut', filtre.debut);
      }
      if (filtre.fin) {
        params = params.set('fin', filtre.fin);
      }
    }
    return this.http.get<Page<VenteResume>>(`${this.base}/ventes`, { params });
  }

  ticket(id: number): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.base}/ventes/${id}`);
  }

  annuler(id: number, motif: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/ventes/${id}/annulation`, { motif });
  }

  // -------------------------------------------------------- tableau de bord

  tableauBord(debut: string, fin: string): Observable<TableauBordPdv> {
    const fuseau = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Africa/Lome';
    return this.http.get<TableauBordPdv>(`${this.base}/tableau-bord`, {
      params: new HttpParams().set('debut', debut).set('fin', fin).set('fuseau', fuseau)
    });
  }
}
