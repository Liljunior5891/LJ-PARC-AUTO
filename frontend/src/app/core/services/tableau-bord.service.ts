import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LigneVueParc } from '../models/exploitation';
import { TableauBordParc } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TableauBordService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/tableau-bord`;

  parc(joursAlerte = 30): Observable<TableauBordParc> {
    return this.http.get<TableauBordParc>(`${this.base}/parc`, {
      params: new HttpParams().set('joursAlerte', joursAlerte)
    });
  }

  /** Vue globale du parc, chapitre 5 du cahier des charges. */
  vueParc(): Observable<LigneVueParc[]> {
    return this.http.get<LigneVueParc[]>(`${this.base}/vue-parc`);
  }
}
