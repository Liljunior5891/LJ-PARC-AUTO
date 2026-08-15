import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LigneTypeRecette, Recette, RecetteRequete, TypeRecette } from '../models/finance';
import { Page } from '../models/models';

export interface FiltreRecette {
  q?: string;
  type?: TypeRecette | '';
  camionId?: number;
  clientId?: number;
  debut?: string;
  fin?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class RecetteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/recettes`;

  lister(filtre: FiltreRecette = {}): Observable<Page<Recette>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateRecette,desc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
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
    return this.http.get<Page<Recette>>(this.base, { params });
  }

  repartition(debut: string, fin: string, camionId?: number): Observable<LigneTypeRecette[]> {
    let params = new HttpParams().set('debut', debut).set('fin', fin);
    if (camionId) {
      params = params.set('camionId', camionId);
    }
    return this.http.get<LigneTypeRecette[]>(`${this.base}/repartition`, { params });
  }

  creer(requete: RecetteRequete): Observable<Recette> {
    return this.http.post<Recette>(this.base, requete);
  }

  modifier(id: number, requete: RecetteRequete): Observable<Recette> {
    return this.http.put<Recette>(`${this.base}/${id}`, requete);
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
