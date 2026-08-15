import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FichePerformance, RentabiliteGlobale, TableauBordFinances } from '../models/finance';

@Injectable({ providedIn: 'root' })
export class RentabiliteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/rentabilite`;

  /** Rentabilite du parc, repartition par camion et palmares (chapitres 29 et 30). */
  global(debut?: string, fin?: string): Observable<RentabiliteGlobale> {
    return this.http.get<RentabiliteGlobale>(`${this.base}/global`, {
      params: this.periode(debut, fin)
    });
  }

  /** Fiche performance d'un camion (chapitre 7). */
  camion(camionId: number, debut?: string, fin?: string): Observable<FichePerformance> {
    return this.http.get<FichePerformance>(`${this.base}/camions/${camionId}`, {
      params: this.periode(debut, fin)
    });
  }

  /** Indicateurs financiers du tableau de bord (chapitre 4.2). */
  finances(debut?: string, fin?: string): Observable<TableauBordFinances> {
    return this.http.get<TableauBordFinances>(`${environment.apiUrl}/tableau-bord/finances`, {
      params: this.periode(debut, fin)
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
