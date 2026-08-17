import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  EvolutionMensuelle,
  FicheSynthese,
  Kpi,
  Notification,
  RapportDisponible,
  SyntheseNotifications
} from '../models/pilotage';

@Injectable({ providedIn: 'root' })
export class PilotageService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pilotage`;

  /** Indicateurs de direction (chapitre 41). */
  kpi(debut?: string, fin?: string): Observable<Kpi> {
    return this.http.get<Kpi>(`${this.base}/kpi`, { params: this.periode(debut, fin) });
  }

  /** Fiche synthetique d'un camion (chapitre 36). */
  synthese(camionId: number, debut?: string, fin?: string): Observable<FicheSynthese> {
    return this.http.get<FicheSynthese>(`${this.base}/camions/${camionId}/synthese`, {
      params: this.periode(debut, fin)
    });
  }

  /** Serie mensuelle recettes / depenses (remarque 6). */
  evolution(mois = 6): Observable<EvolutionMensuelle[]> {
    return this.http.get<EvolutionMensuelle[]>(`${this.base}/evolution`, {
      params: new HttpParams().set('mois', mois)
    });
  }

  notifications(joursAlerte = 30): Observable<Notification[]> {
    return this.http.get<Notification[]>(`${this.base}/notifications`, {
      params: new HttpParams().set('joursAlerte', joursAlerte)
    });
  }

  syntheseNotifications(joursAlerte = 30): Observable<SyntheseNotifications> {
    return this.http.get<SyntheseNotifications>(`${this.base}/notifications/synthese`, {
      params: new HttpParams().set('joursAlerte', joursAlerte)
    });
  }

  rapports(): Observable<RapportDisponible[]> {
    return this.http.get<RapportDisponible[]>(`${this.base}/rapports`);
  }

  /** Telecharge un rapport CSV et declenche l'enregistrement du fichier. */
  telechargerRapport(type: string, debut: string, fin: string): Observable<Blob> {
    return this.http.get(`${this.base}/rapports/${type}`, {
      params: new HttpParams().set('debut', debut).set('fin', fin),
      responseType: 'blob'
    });
  }

  private periode(debut?: string, fin?: string): HttpParams {
    let params = new HttpParams();
    if (debut) {
      params = params.set('debut', debut);
    }
    if (fin) {
      params = params.set('fin', fin);
    }
    return params;
  }
}
