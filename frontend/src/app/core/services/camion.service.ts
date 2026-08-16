import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Camion, CamionRequete, MarqueCamion, Page, StatutCamion } from '../models/models';

export interface FiltreCamion {
  q?: string;
  statut?: StatutCamion | '';
  actif?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class CamionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/camions`;

  lister(filtre: FiltreCamion = {}): Observable<Page<Camion>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', filtre.sort ?? 'immatriculation,asc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.actif !== undefined) {
      params = params.set('actif', filtre.actif);
    }
    return this.http.get<Page<Camion>>(this.base, { params });
  }

  /** Referentiel des marques, avec logo et couleur (chapitre 6). */
  marques(): Observable<MarqueCamion[]> {
    return this.http.get<MarqueCamion[]>(`${this.base}/marques`);
  }

  parId(id: number): Observable<Camion> {
    return this.http.get<Camion>(`${this.base}/${id}`);
  }

  creer(camion: CamionRequete): Observable<Camion> {
    return this.http.post<Camion>(this.base, camion);
  }

  modifier(id: number, camion: CamionRequete): Observable<Camion> {
    return this.http.put<Camion>(`${this.base}/${id}`, camion);
  }

  majKilometrage(id: number, kilometrage: number): Observable<Camion> {
    return this.http.patch<Camion>(`${this.base}/${id}/kilometrage`, { kilometrage });
  }

  majStatut(id: number, statut: StatutCamion, motif?: string): Observable<Camion> {
    return this.http.patch<Camion>(`${this.base}/${id}/statut`, { statut, motif });
  }

  archiver(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
