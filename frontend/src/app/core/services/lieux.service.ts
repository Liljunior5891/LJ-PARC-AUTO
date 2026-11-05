import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BonRetrait,
  ConflitHorsLigne,
  DecisionEcart,
  DisponibiliteLieu,
  EtatLieux,
  Lieu,
  LieuCourt,
  LieuRequete,
  LigneStockLieu,
  ProduitCourt,
  RemiseDifferee,
  RemiseRetrait,
  ResultatSynchro,
  StatutBonRetrait,
  StatutTransfert,
  Transfert
} from '../models/lieux';
import { BonLivraison } from '../models/materiaux';
import { Page } from '../models/models';
import { SANS_SUIVI } from '../operation/operation.interceptor';

/** API des magasins, entrepots, bons de retrait et transferts. */
@Injectable({ providedIn: 'root' })
export class LieuxService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv`;

  /** Etat partage : le menu et les ecrans de stock s'y adaptent. */
  readonly etat = signal<EtatLieux | null>(null);
  readonly actif = computed(() => !!this.etat()?.actif);

  chargerEtat(): void {
    this.http.get<EtatLieux>(`${this.base}/lieux/etat`, { context: new HttpContext().set(SANS_SUIVI, true) }).subscribe({
      next: (e) => this.etat.set(e),
      error: () => this.etat.set({ actif: false, lieux: [], mesLieux: [] })
    });
  }

  lister(): Observable<Lieu[]> {
    return this.http.get<Lieu[]>(`${this.base}/lieux`);
  }

  creer(r: LieuRequete): Observable<Lieu> {
    return this.http.post<Lieu>(`${this.base}/lieux`, r).pipe(tap(() => this.chargerEtat()));
  }

  modifier(id: number, r: LieuRequete): Observable<Lieu> {
    return this.http.put<Lieu>(`${this.base}/lieux/${id}`, r).pipe(tap(() => this.chargerEtat()));
  }

  disponibilites(articles: number[]): Observable<DisponibiliteLieu[]> {
    return this.http.get<DisponibiliteLieu[]>(`${this.base}/lieux/disponibilites`, {
      params: new HttpParams().set('articles', articles.join(','))
    });
  }

  lieuDeCaisse(caisseId: number): Observable<LieuCourt> {
    return this.http.get<LieuCourt>(`${this.base}/lieux/caisse/${caisseId}`);
  }

  stock(lieuId: number, q: string, page = 0, size = 50): Observable<Page<LigneStockLieu>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) {
      params = params.set('q', q);
    }
    return this.http.get<Page<LigneStockLieu>>(`${this.base}/lieux/${lieuId}/stock`, { params });
  }

  produits(): Observable<ProduitCourt[]> {
    return this.http.get<ProduitCourt[]>(`${this.base}/lieux/produits`);
  }

  modifierSeuil(lieuId: number, articleId: number, seuilAlerte: number | null): Observable<LigneStockLieu> {
    return this.http.put<LigneStockLieu>(`${this.base}/lieux/${lieuId}/stock/${articleId}/seuil`, { seuilAlerte }, {
      context: new HttpContext().set(SANS_SUIVI, true)
    });
  }

  // ---------------------------------------------------------- bons de retrait

  retraits(filtre: { q?: string; lieuId?: number | null; statut?: StatutBonRetrait | ''; page?: number }): Observable<Page<BonRetrait>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', 30);
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.lieuId) {
      params = params.set('lieuId', filtre.lieuId);
    }
    return this.http.get<Page<BonRetrait>>(`${this.base}/retraits`, { params });
  }

  retraitParNumero(numero: string): Observable<BonRetrait> {
    return this.http.get<BonRetrait>(`${this.base}/retraits/numero/${encodeURIComponent(numero)}`);
  }

  retraitsDeLaVente(venteId: number): Observable<BonRetrait[]> {
    return this.http.get<BonRetrait[]>(`${this.base}/ventes/${venteId}/retraits`);
  }

  retraitsHorsLigne(): Observable<BonRetrait[]> {
    return this.http.get<BonRetrait[]>(`${this.base}/retraits/hors-ligne`, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  remettre(bonId: number, r: RemiseRetrait): Observable<BonLivraison> {
    return this.http.post<BonLivraison>(`${this.base}/retraits/${bonId}/remise`, r, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  changerLieu(bonId: number, lieuId: number): Observable<BonRetrait> {
    return this.http.post<BonRetrait>(`${this.base}/retraits/${bonId}/lieu`, { lieuId }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  synchroniser(remises: RemiseDifferee[]): Observable<ResultatSynchro[]> {
    return this.http.post<ResultatSynchro[]>(`${this.base}/retraits/synchronisation`, remises, {
      context: new HttpContext().set(SANS_SUIVI, true)
    });
  }

  conflits(): Observable<ConflitHorsLigne[]> {
    return this.http.get<ConflitHorsLigne[]>(`${this.base}/retraits/conflits`);
  }

  traiterConflit(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/retraits/conflits/${id}/traitement`, null, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  retraitPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/retraits/${id}/pdf`, { responseType: 'blob' });
  }

  livraisonPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/retraits/livraisons/${id}/pdf`, { responseType: 'blob' });
  }

  // --------------------------------------------------------------- transferts

  /** Filtre : un statut, EN_COURS, ou vide pour tout voir. */
  transferts(filtre: StatutTransfert | 'EN_COURS' | '', page = 0): Observable<Page<Transfert>> {
    let params = new HttpParams().set('page', page).set('size', 30);
    if (filtre === 'EN_COURS') {
      params = params.set('enCours', true);
    } else if (filtre) {
      params = params.set('statut', filtre);
    }
    return this.http.get<Page<Transfert>>(`${this.base}/transferts`, { params });
  }

  demanderTransfert(r: { sourceId?: number | null; destinationId: number; note?: string | null; lignes: { articleId: number; quantite: number }[] }): Observable<Transfert> {
    return this.http.post<Transfert>(`${this.base}/transferts`, r, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  validerTransfert(id: number, sourceId: number | null): Observable<Transfert> {
    return this.http.post<Transfert>(`${this.base}/transferts/${id}/validation`, { sourceId }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  annulerTransfert(id: number, motif: string): Observable<Transfert> {
    return this.http.post<Transfert>(`${this.base}/transferts/${id}/annulation`, { motif: motif || null }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  expedierTransfert(id: number, transporteur: string, lignes: { ligneId: number; quantite: number }[]): Observable<Transfert> {
    return this.http.post<Transfert>(`${this.base}/transferts/${id}/expedition`, { transporteur: transporteur || null, lignes }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  receptionnerTransfert(id: number, lignes: { ligneId: number; quantite: number }[]): Observable<Transfert> {
    return this.http.post<Transfert>(`${this.base}/transferts/${id}/reception`, { lignes }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  traiterEcart(id: number, decision: DecisionEcart, motif: string): Observable<Transfert> {
    return this.http.post<Transfert>(`${this.base}/transferts/${id}/ecart`, { decision, motif: motif || null }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  transfertPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/transferts/${id}/pdf`, { responseType: 'blob' });
  }
}
