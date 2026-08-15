import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SyntheseCarburant } from '../models/finance';

@Injectable({ providedIn: 'root' })
export class CarburantService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/carburant`;

  /** Pleins, consommation moyenne et cout au kilometre d'un camion (chapitre 18). */
  synthese(camionId: number, debut?: string, fin?: string): Observable<SyntheseCarburant> {
    let params = new HttpParams();
    if (debut) {
      params = params.set('debut', debut);
    }
    if (fin) {
      params = params.set('fin', fin);
    }
    return this.http.get<SyntheseCarburant>(`${this.base}/camions/${camionId}`, { params });
  }
}
