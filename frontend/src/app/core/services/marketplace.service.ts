import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/models';
import {
  Boutique,
  BoutiqueRequete,
  CommandeEnLigne,
  DemandeCommande,
  DemandeLocationEnLigne,
  DemandeLocationPublique,
  Marche,
  Offre,
  OffreRequete,
  ResumeMarketplace,
  SourcesOffres,
  StatutCommandeEnLigne,
  StatutDemandeLocation,
  SuiviEnLigne,
  Vitrine,
  VehiculeEnLigne
} from '../models/marketplace';

/** Espace de gestion de la Marketplace (profils du module). */
@Injectable({ providedIn: 'root' })
export class MarketplaceService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/marketplace`;

  resume(): Observable<ResumeMarketplace> {
    return this.http.get<ResumeMarketplace>(`${this.base}/resume`);
  }

  boutique(): Observable<Boutique> {
    return this.http.get<Boutique>(`${this.base}/boutique`);
  }

  enregistrerBoutique(requete: BoutiqueRequete): Observable<Boutique> {
    return this.http.put<Boutique>(`${this.base}/boutique`, requete);
  }

  /** Code QR (SVG) de la boutique, vers l'adresse publique servie par cette application. */
  codeQr(): Observable<string> {
    return this.http.get(`${this.base}/boutique/qr`, {
      params: new HttpParams().set('origine', window.location.origin),
      responseType: 'text'
    });
  }

  /** Articles du catalogue du point de vente et leur publication en ligne. */
  articlesEnLigne(): Observable<{ articleId: number; offreId?: number; publiee: boolean }[]> {
    return this.http.get<{ articleId: number; offreId?: number; publiee: boolean }[]>(`${this.base}/articles-en-ligne`);
  }

  /** Met un article en vente en ligne ou l'en retire, d'un clic dans la liste des produits. */
  publierArticle(articleId: number, publiee: boolean,
                 photos?: string[]): Observable<{ articleId: number; publiee: boolean }> {
    return this.http.put<{ articleId: number; publiee: boolean }>(`${this.base}/articles/${articleId}/en-ligne`,
      { publiee, photos: photos ?? null }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  /** Photos supplementaires d'un article vendu en ligne (galerie de la boutique). */
  photosArticle(articleId: number): Observable<{ photos: string[] }> {
    return this.http.get<{ photos: string[] }>(`${this.base}/articles/${articleId}/photos`);
  }

  /** Photos supplementaires d'un vehicule loue en ligne. */
  photosVehicule(camionId: number): Observable<{ photos: string[] }> {
    return this.http.get<{ photos: string[] }>(`${this.base}/vehicules/${camionId}/photos`);
  }

  /** Vehicules du parc proposes a la location en ligne (fiche vehicule, vue du parc). */
  vehiculesEnLigne(): Observable<VehiculeEnLigne[]> {
    return this.http.get<VehiculeEnLigne[]>(`${this.base}/vehicules-en-ligne`);
  }

  /** Location en ligne reglee depuis la fiche vehicule (suit l'enregistrement de la fiche, deja confirme). */
  publierVehicule(camionId: number, publiee: boolean, tarifJour: number | null,
                  caution: number | null, photos?: string[]): Observable<VehiculeEnLigne> {
    return this.http.put<VehiculeEnLigne>(`${this.base}/vehicules/${camionId}/en-ligne`,
      { publiee, tarifJour, caution, photos: photos ?? null }, { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  offres(): Observable<Offre[]> {
    return this.http.get<Offre[]>(`${this.base}/offres`);
  }

  sources(): Observable<SourcesOffres> {
    return this.http.get<SourcesOffres>(`${this.base}/sources`);
  }

  creerOffre(requete: OffreRequete): Observable<Offre> {
    return this.http.post<Offre>(`${this.base}/offres`, requete);
  }

  publierArticles(articleIds: number[]): Observable<Offre[]> {
    return this.http.post<Offre[]>(`${this.base}/offres/articles`, { articleIds });
  }

  modifierOffre(id: number, requete: OffreRequete): Observable<Offre> {
    return this.http.put<Offre>(`${this.base}/offres/${id}`, requete);
  }

  supprimerOffre(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/offres/${id}`);
  }

  commandes(filtre: string, page = 0): Observable<Page<CommandeEnLigne>> {
    return this.http.get<Page<CommandeEnLigne>>(`${this.base}/commandes`, {
      params: new HttpParams().set('filtre', filtre).set('page', page).set('size', 20)
    });
  }

  changerStatut(id: number, statut: StatutCommandeEnLigne, motif?: string): Observable<CommandeEnLigne> {
    return this.http.post<CommandeEnLigne>(`${this.base}/commandes/${id}/statut`, { statut, motif });
  }

  marquerPayee(id: number): Observable<CommandeEnLigne> {
    return this.http.post<CommandeEnLigne>(`${this.base}/commandes/${id}/paiement`, {});
  }

  demandes(statut: StatutDemandeLocation | '', page = 0): Observable<Page<DemandeLocationEnLigne>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (statut) {
      params = params.set('statut', statut);
    }
    return this.http.get<Page<DemandeLocationEnLigne>>(`${this.base}/demandes-location`, { params });
  }

  accepterDemande(id: number, tarifJour: number | null, chauffeurId: number | null,
                  observations: string): Observable<DemandeLocationEnLigne> {
    return this.http.post<DemandeLocationEnLigne>(`${this.base}/demandes-location/${id}/accepter`,
      { tarifJour, chauffeurId, observations });
  }

  demande(id: number): Observable<DemandeLocationEnLigne> {
    return this.http.get<DemandeLocationEnLigne>(`${this.base}/demandes-location/${id}`);
  }

  /** Le client se desiste : la demande acceptee est annulee et le vehicule libere. */
  annulerDemande(id: number, motif: string): Observable<DemandeLocationEnLigne> {
    return this.http.post<DemandeLocationEnLigne>(`${this.base}/demandes-location/${id}/annuler`, { motif });
  }

  refuserDemande(id: number, motif: string): Observable<DemandeLocationEnLigne> {
    return this.http.post<DemandeLocationEnLigne>(`${this.base}/demandes-location/${id}/refuser`, { motif });
  }
}

/** Pages publiques : place de marche, boutique et suivi, sans compte. */
@Injectable({ providedIn: 'root' })
export class BoutiquePubliqueService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/public`;

  marche(q: string, categorie: string, ville: string): Observable<Marche> {
    let params = new HttpParams();
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    if (categorie) {
      params = params.set('categorie', categorie);
    }
    if (ville) {
      params = params.set('ville', ville);
    }
    return this.http.get<Marche>(`${this.base}/marche`, { params });
  }

  vitrine(slug: string): Observable<Vitrine> {
    return this.http.get<Vitrine>(`${this.base}/boutiques/${slug}`);
  }

  /** Galerie d'une offre : photo principale puis photos supplementaires. */
  galerie(slug: string, offreId: number): Observable<{ photos: string[] }> {
    return this.http.get<{ photos: string[] }>(`${this.base}/boutiques/${slug}/offres/${offreId}/photos`);
  }

  commander(slug: string, demande: DemandeCommande): Observable<SuiviEnLigne> {
    return this.http.post<SuiviEnLigne>(`${this.base}/boutiques/${slug}/commandes`, demande);
  }

  demanderLocation(slug: string, demande: DemandeLocationPublique): Observable<SuiviEnLigne> {
    return this.http.post<SuiviEnLigne>(`${this.base}/boutiques/${slug}/locations`, demande);
  }

  suivi(jeton: string): Observable<SuiviEnLigne> {
    return this.http.get<SuiviEnLigne>(`${this.base}/suivi/${jeton}`);
  }

  relancerPaiement(jeton: string): Observable<SuiviEnLigne> {
    return this.http.post<SuiviEnLigne>(`${this.base}/suivi/${jeton}/paiement`, {});
  }

  confirmerPaiementSimule(jeton: string): Observable<SuiviEnLigne> {
    return this.http.post<SuiviEnLigne>(`${this.base}/suivi/${jeton}/paiement/simulation`, {});
  }
}
