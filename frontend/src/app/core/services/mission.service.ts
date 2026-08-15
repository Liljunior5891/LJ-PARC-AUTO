import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Mission, MissionRequete, StatutMission, TypeMission } from '../models/exploitation';
import { Page } from '../models/models';

export interface FiltreMission {
  q?: string;
  statut?: StatutMission | '';
  type?: TypeMission | '';
  camionId?: number;
  chauffeurId?: number;
  clientId?: number;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class MissionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/missions`;

  lister(filtre: FiltreMission = {}): Observable<Page<Mission>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateDepart,desc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
    }
    if (filtre.chauffeurId) {
      params = params.set('chauffeurId', filtre.chauffeurId);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    return this.http.get<Page<Mission>>(this.base, { params });
  }

  parId(id: number): Observable<Mission> {
    return this.http.get<Mission>(`${this.base}/${id}`);
  }

  enCours(): Observable<Mission[]> {
    return this.http.get<Mission[]>(`${this.base}/en-cours`);
  }

  creer(requete: MissionRequete): Observable<Mission> {
    return this.http.post<Mission>(this.base, requete);
  }

  modifier(id: number, requete: MissionRequete): Observable<Mission> {
    return this.http.put<Mission>(`${this.base}/${id}`, requete);
  }

  demarrer(id: number, kilometrageDepart?: number | null): Observable<Mission> {
    return this.http.patch<Mission>(`${this.base}/${id}/demarrage`, { kilometrageDepart });
  }

  cloturer(id: number, kilometrageArrivee: number, recetteReelle?: number | null,
           observations?: string): Observable<Mission> {
    return this.http.patch<Mission>(`${this.base}/${id}/cloture`, {
      kilometrageArrivee,
      recetteReelle,
      observations
    });
  }

  annuler(id: number, motif?: string): Observable<Mission> {
    const params = motif ? new HttpParams().set('motif', motif) : undefined;
    return this.http.patch<Mission>(`${this.base}/${id}/annulation`, {}, { params });
  }
}
