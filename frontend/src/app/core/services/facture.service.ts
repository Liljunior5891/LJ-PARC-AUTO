import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Creances, Facture, LigneFacture, ModePaiement, StatutFacture } from '../models/finance';
import { Page } from '../models/models';

export interface FiltreFacture {
  q?: string;
  statut?: StatutFacture | '';
  clientId?: number;
  debut?: string;
  fin?: string;
  page?: number;
  size?: number;
}

export interface FactureRequete {
  clientId: number;
  locationId?: number | null;
  missionId?: number | null;
  dateEmission: string;
  dateEcheance?: string | null;
  periodeDebut?: string | null;
  periodeFin?: string | null;
  tauxTva?: number | null;
  observations?: string;
  lignes: LigneFacture[];
}

export interface PaiementRequete {
  montant: number;
  datePaiement: string;
  modePaiement: ModePaiement;
  reference?: string;
  observations?: string;
}

@Injectable({ providedIn: 'root' })
export class FactureService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/factures`;

  lister(filtre: FiltreFacture = {}): Observable<Page<Facture>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateEmission,desc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    if (filtre.debut) {
      params = params.set('debut', filtre.debut);
    }
    if (filtre.fin) {
      params = params.set('fin', filtre.fin);
    }
    return this.http.get<Page<Facture>>(this.base, { params });
  }

  parId(id: number): Observable<Facture> {
    return this.http.get<Facture>(`${this.base}/${id}`);
  }

  impayees(): Observable<Facture[]> {
    return this.http.get<Facture[]>(`${this.base}/impayees`);
  }

  creances(): Observable<Creances> {
    return this.http.get<Creances>(`${this.base}/creances`);
  }

  creer(requete: FactureRequete): Observable<Facture> {
    return this.http.post<Facture>(this.base, requete);
  }

  modifier(id: number, requete: FactureRequete): Observable<Facture> {
    return this.http.put<Facture>(`${this.base}/${id}`, requete);
  }

  /** Genere une facture a partir d'un contrat de location (chapitre 27). */
  depuisContrat(locationId: number, tauxTva?: number): Observable<Facture> {
    return this.http.post<Facture>(`${this.base}/depuis-contrat`, { locationId, tauxTva });
  }

  depuisMission(missionId: number, tauxTva?: number): Observable<Facture> {
    return this.http.post<Facture>(`${this.base}/depuis-mission`, { missionId, tauxTva });
  }

  emettre(id: number): Observable<Facture> {
    return this.http.patch<Facture>(`${this.base}/${id}/emission`, {});
  }

  payer(id: number, requete: PaiementRequete): Observable<Facture> {
    return this.http.post<Facture>(`${this.base}/${id}/paiements`, requete);
  }

  annuler(id: number, motif?: string): Observable<Facture> {
    const params = motif ? new HttpParams().set('motif', motif) : undefined;
    return this.http.patch<Facture>(`${this.base}/${id}/annulation`, {}, { params });
  }

  marquerRetards(): Observable<number> {
    return this.http.patch<number>(`${this.base}/retards`, {});
  }
}
