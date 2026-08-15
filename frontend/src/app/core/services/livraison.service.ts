import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ConfirmationLivraison,
  Livraison,
  LivraisonRequete,
  StatutLivraison
} from '../models/exploitation';
import { Page } from '../models/models';

export interface FiltreLivraison {
  q?: string;
  statut?: StatutLivraison | '';
  camionId?: number;
  clientId?: number;
  missionId?: number;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class LivraisonService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/livraisons`;

  lister(filtre: FiltreLivraison = {}): Observable<Page<Livraison>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'datePrevue,desc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    if (filtre.missionId) {
      params = params.set('missionId', filtre.missionId);
    }
    return this.http.get<Page<Livraison>>(this.base, { params });
  }

  parId(id: number): Observable<Livraison> {
    return this.http.get<Livraison>(`${this.base}/${id}`);
  }

  creer(requete: LivraisonRequete): Observable<Livraison> {
    return this.http.post<Livraison>(this.base, requete);
  }

  modifier(id: number, requete: LivraisonRequete): Observable<Livraison> {
    return this.http.put<Livraison>(`${this.base}/${id}`, requete);
  }

  confirmer(id: number, confirmation: ConfirmationLivraison): Observable<Livraison> {
    return this.http.patch<Livraison>(`${this.base}/${id}/confirmation`, confirmation);
  }

  annuler(id: number, motif?: string): Observable<Livraison> {
    const params = motif ? new HttpParams().set('motif', motif) : undefined;
    return this.http.patch<Livraison>(`${this.base}/${id}/annulation`, {}, { params });
  }
}
