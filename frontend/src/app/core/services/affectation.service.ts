import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Affectation, AffectationRequete, Page } from '../models/models';

export interface FiltreAffectation {
  camionId?: number;
  chauffeurId?: number;
  enCours?: boolean;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class AffectationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/affectations`;

  lister(filtre: FiltreAffectation = {}): Observable<Page<Affectation>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateDebut,desc');
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
    }
    if (filtre.chauffeurId) {
      params = params.set('chauffeurId', filtre.chauffeurId);
    }
    if (filtre.enCours !== undefined) {
      params = params.set('enCours', filtre.enCours);
    }
    return this.http.get<Page<Affectation>>(this.base, { params });
  }

  enCours(): Observable<Affectation[]> {
    return this.http.get<Affectation[]>(`${this.base}/en-cours`);
  }

  /**
   * Affectation en cours d'un camion, c'est-a-dire le chauffeur qui le conduit.
   * Repond 404 quand le camion n'est affecte a personne.
   */
  couranteDuCamion(camionId: number): Observable<Affectation> {
    return this.http.get<Affectation>(`${this.base}/camion/${camionId}/courante`);
  }

  affecter(requete: AffectationRequete): Observable<Affectation> {
    return this.http.post<Affectation>(this.base, requete);
  }

  cloturer(id: number, dateFin: string, observations?: string): Observable<Affectation> {
    return this.http.patch<Affectation>(`${this.base}/${id}/cloture`, { dateFin, observations });
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
