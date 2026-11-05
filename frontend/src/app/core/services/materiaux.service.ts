import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentParc, DocumentRequete, Page } from '../models/models';
import { Ticket, VenteResume } from '../models/pdv';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import {
  BonLivraison,
  ClientPdv,
  ClientPdvRequete,
  CompteClient,
  Conditionnement,
  ConditionnementRequete,
  Devis,
  DevisRequete,
  EncaissementDevis,
  RecuReglement,
  ReglagesMateriaux,
  ReglementRequete,
  RemiseRequete,
  SoldeRequete,
  SoldeReste,
  StatutDevis,
  TypeClientPdv,
  VenteALivrer
} from '../models/materiaux';

/** API du point de vente « Quincaillerie & materiaux ». */
@Injectable({ providedIn: 'root' })
export class MateriauxService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv`;

  // ------------------------------------------------------------- reglages

  reglages(): Observable<ReglagesMateriaux> {
    return this.http.get<ReglagesMateriaux>(`${this.base}/materiaux/reglages`);
  }

  modifierReglages(r: ReglagesMateriaux): Observable<ReglagesMateriaux> {
    return this.http.put<ReglagesMateriaux>(`${this.base}/materiaux/reglages`, r);
  }

  // -------------------------------------------------------------- clients

  clients(filtre: { q?: string; type?: TypeClientPdv | ''; actif?: boolean; debiteurs?: boolean; page?: number; size?: number }): Observable<Page<ClientPdv>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', filtre.size ?? 30);
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.actif !== undefined) {
      params = params.set('actif', filtre.actif);
    }
    if (filtre.debiteurs) {
      params = params.set('debiteurs', true);
    }
    return this.http.get<Page<ClientPdv>>(`${this.base}/clients`, { params });
  }

  /** Recherche rapide au comptoir, parmi les clients actifs. */
  chercherClients(q: string): Observable<ClientPdv[]> {
    return this.clients({ q, actif: true, size: 8 }).pipe(map((p) => p.content));
  }

  client(id: number): Observable<ClientPdv> {
    return this.http.get<ClientPdv>(`${this.base}/clients/${id}`);
  }

  creerClient(r: ClientPdvRequete): Observable<ClientPdv> {
    return this.http.post<ClientPdv>(`${this.base}/clients`, r);
  }

  modifierClient(id: number, r: ClientPdvRequete): Observable<ClientPdv> {
    return this.http.put<ClientPdv>(`${this.base}/clients/${id}`, r);
  }

  archiverClient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/clients/${id}`);
  }

  /** Clients dont le numero de telephone est le meme, quelle que soit l'ecriture. */
  clientsParTelephone(telephone: string): Observable<ClientPdv[]> {
    return this.http.get<ClientPdv[]>(`${this.base}/clients/par-telephone`, { params: { telephone } });
  }

  documentsClient(id: number): Observable<Page<DocumentParc>> {
    return this.http.get<Page<DocumentParc>>(`${this.base}/clients/${id}/documents`);
  }

  ajouterDocumentClient(id: number, piece: Omit<DocumentRequete, 'proprietaireType' | 'proprietaireId'>,
                        fichier: File | null): Observable<DocumentParc> {
    const requete: DocumentRequete = { ...piece, proprietaireType: 'CLIENT_PDV', proprietaireId: id };
    const form = new FormData();
    form.append('document', new Blob([JSON.stringify(requete)], { type: 'application/json' }));
    if (fichier) {
      form.append('fichier', fichier);
    }
    return this.http.post<DocumentParc>(`${this.base}/clients/${id}/documents`, form);
  }

  telechargerDocumentClient(documentId: number): Observable<Blob> {
    return this.http.get(`${this.base}/clients/documents/${documentId}/fichier`, { responseType: 'blob' });
  }

  compte(id: number): Observable<CompteClient> {
    return this.http.get<CompteClient>(`${this.base}/clients/${id}/compte`);
  }

  ventesDuClient(id: number, page = 0): Observable<Page<VenteResume>> {
    return this.http.get<Page<VenteResume>>(`${this.base}/clients/${id}/ventes`, {
      params: new HttpParams().set('page', page).set('size', 20)
    });
  }

  regler(clientId: number, r: ReglementRequete): Observable<RecuReglement> {
    return this.http.post<RecuReglement>(`${this.base}/clients/${clientId}/reglements`, r, {
      context: new HttpContext().set(SANS_SUIVI, true)
    });
  }

  recu(numero: string): Observable<RecuReglement> {
    return this.http.get<RecuReglement>(`${this.base}/reglements/${numero}`);
  }

  annulerReglement(numero: string, motif: string): Observable<RecuReglement> {
    return this.http.post<RecuReglement>(`${this.base}/reglements/${numero}/annulation`, { motif });
  }

  // ---------------------------------------------------------------- devis

  devis(filtre: { q?: string; statut?: StatutDevis | ''; clientId?: number; page?: number }): Observable<Page<Devis>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', 30);
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    return this.http.get<Page<Devis>>(`${this.base}/devis`, { params });
  }

  unDevis(id: number): Observable<Devis> {
    return this.http.get<Devis>(`${this.base}/devis/${id}`);
  }

  creerDevis(r: DevisRequete): Observable<Devis> {
    return this.http.post<Devis>(`${this.base}/devis`, r);
  }

  modifierDevis(id: number, r: DevisRequete): Observable<Devis> {
    return this.http.put<Devis>(`${this.base}/devis/${id}`, r);
  }

  prolongerDevis(id: number, jusqua: string): Observable<Devis> {
    return this.http.post<Devis>(`${this.base}/devis/${id}/prolongation`, null, {
      params: new HttpParams().set('jusqua', jusqua)
    });
  }

  annulerDevis(id: number): Observable<Devis> {
    return this.http.post<Devis>(`${this.base}/devis/${id}/annulation`, null);
  }

  encaisserDevis(id: number, e: EncaissementDevis): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/devis/${id}/encaissement`, e, {
      context: new HttpContext().set(SANS_SUIVI, true)
    });
  }

  devisPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/devis/${id}/pdf`, { responseType: 'blob' });
  }

  // ----------------------------------------------------- conditionnements

  conditionnementsVendables(): Observable<Conditionnement[]> {
    return this.http.get<Conditionnement[]>(`${this.base}/conditionnements`);
  }

  conditionnementsDuProduit(articleId: number): Observable<Conditionnement[]> {
    return this.http.get<Conditionnement[]>(`${this.base}/articles/${articleId}/conditionnements`);
  }

  enregistrerConditionnements(articleId: number, liste: ConditionnementRequete[]): Observable<Conditionnement[]> {
    return this.http.put<Conditionnement[]>(`${this.base}/articles/${articleId}/conditionnements`, liste);
  }

  // ------------------------------------------------- livraison en plusieurs fois

  resteALivrer(filtre: { q?: string; clientId?: number | null; page?: number }): Observable<Page<VenteALivrer>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', 30);
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    return this.http.get<Page<VenteALivrer>>(`${this.base}/livraisons/reste`, { params });
  }

  /** Commandes qui ont un bon ou un solde, meme entierement remises. */
  historiqueLivraisons(filtre: { q?: string; clientId?: number | null; page?: number }): Observable<Page<VenteALivrer>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', 30);
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    return this.http.get<Page<VenteALivrer>>(`${this.base}/livraisons/historique`, { params });
  }

  solder(venteId: number, r: SoldeRequete): Observable<SoldeReste> {
    return this.http.post<SoldeReste>(`${this.base}/ventes/${venteId}/soldes`, r);
  }

  soldesDeLaVente(venteId: number): Observable<SoldeReste[]> {
    return this.http.get<SoldeReste[]>(`${this.base}/ventes/${venteId}/soldes`);
  }

  annulerSolde(soldeId: number, motif: string): Observable<SoldeReste> {
    return this.http.post<SoldeReste>(`${this.base}/soldes/${soldeId}/annulation`, { motif: motif || null });
  }

  soldePdf(soldeId: number): Observable<Blob> {
    return this.http.get(`${this.base}/soldes/${soldeId}/pdf`, { responseType: 'blob' });
  }

  remettre(venteId: number, r: RemiseRequete): Observable<BonLivraison> {
    return this.http.post<BonLivraison>(`${this.base}/ventes/${venteId}/livraisons`, r);
  }

  bonsDeLaVente(venteId: number): Observable<BonLivraison[]> {
    return this.http.get<BonLivraison[]>(`${this.base}/ventes/${venteId}/livraisons`);
  }

  annulerBon(bonId: number, motif: string): Observable<BonLivraison> {
    return this.http.post<BonLivraison>(`${this.base}/livraisons/${bonId}/annulation`, { motif: motif || null });
  }

  bonPdf(bonId: number): Observable<Blob> {
    return this.http.get(`${this.base}/livraisons/${bonId}/pdf`, { responseType: 'blob' });
  }

  // -------------------------------------------------------------- facture

  facturePdf(venteId: number): Observable<Blob> {
    return this.http.get(`${this.base}/ventes/${venteId}/facture-pdf`, { responseType: 'blob' });
  }

  /** Ouvre un PDF dans un nouvel onglet (impression ou enregistrement par le navigateur). */
  ouvrirPdf(pdf: Blob): void {
    const url = URL.createObjectURL(pdf);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
}
