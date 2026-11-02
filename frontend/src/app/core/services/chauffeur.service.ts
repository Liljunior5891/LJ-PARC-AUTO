import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Chauffeur, ChauffeurRequete, Page, StatutChauffeur } from '../models/models';

export interface FiltreChauffeur {
  q?: string;
  statut?: StatutChauffeur | '';
  actif?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class ChauffeurService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/chauffeurs`;

  lister(filtre: FiltreChauffeur = {}): Observable<Page<Chauffeur>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', filtre.sort ?? 'nom,asc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.actif !== undefined) {
      params = params.set('actif', filtre.actif);
    }
    return this.http.get<Page<Chauffeur>>(this.base, { params });
  }

  parId(id: number): Observable<Chauffeur> {
    return this.http.get<Chauffeur>(`${this.base}/${id}`);
  }

  permisAExpirer(jours = 30): Observable<Chauffeur[]> {
    return this.http.get<Chauffeur[]>(`${this.base}/alertes/permis`, {
      params: new HttpParams().set('jours', jours)
    });
  }

  creer(chauffeur: Partial<ChauffeurRequete>): Observable<Chauffeur> {
    return this.http.post<Chauffeur>(this.base, chauffeur);
  }

  modifier(id: number, chauffeur: Partial<ChauffeurRequete>): Observable<Chauffeur> {
    return this.http.put<Chauffeur>(`${this.base}/${id}`, chauffeur);
  }

  archiver(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
