import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Location, LocationRequete, StatutLocation, TypeLocation } from '../models/exploitation';
import { Page } from '../models/models';

export interface FiltreLocation {
  q?: string;
  statut?: StatutLocation | '';
  type?: TypeLocation | '';
  clientId?: number;
  camionId?: number;
  page?: number;
  size?: number;
  /** Canal : vrai = en ligne (Marketplace), faux = agence. */
  enLigne?: boolean;
}

@Injectable({ providedIn: 'root' })
export class LocationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/locations`;

  lister(filtre: FiltreLocation = {}): Observable<Page<Location>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateDebut,desc');
    if (filtre.enLigne !== undefined) {
      params = params.set('enLigne', filtre.enLigne);
    }
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
    }
    return this.http.get<Page<Location>>(this.base, { params });
  }

  parId(id: number): Observable<Location> {
    return this.http.get<Location>(`${this.base}/${id}`);
  }

  actives(): Observable<Location[]> {
    return this.http.get<Location[]>(`${this.base}/actives`);
  }

  echeances(jours = 15): Observable<Location[]> {
    return this.http.get<Location[]>(`${this.base}/alertes/echeances`, {
      params: new HttpParams().set('jours', jours)
    });
  }

  creer(requete: LocationRequete): Observable<Location> {
    return this.http.post<Location>(this.base, requete);
  }

  modifier(id: number, requete: LocationRequete): Observable<Location> {
    return this.http.put<Location>(`${this.base}/${id}`, requete);
  }

  activer(id: number): Observable<Location> {
    return this.http.patch<Location>(`${this.base}/${id}/activation`, {});
  }

  cloturer(id: number, dateFinReelle: string, kilometrageReel?: number | null,
           observations?: string): Observable<Location> {
    return this.http.patch<Location>(`${this.base}/${id}/cloture`, {
      dateFinReelle,
      kilometrageReel,
      observations
    });
  }

  annuler(id: number, motif?: string): Observable<Location> {
    const params = motif ? new HttpParams().set('motif', motif) : undefined;
    return this.http.patch<Location>(`${this.base}/${id}/annulation`, {}, { params });
  }
}
