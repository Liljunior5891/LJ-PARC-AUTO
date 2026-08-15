import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TableauBordParc } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TableauBordService {
  private readonly http = inject(HttpClient);

  parc(joursAlerte = 30): Observable<TableauBordParc> {
    return this.http.get<TableauBordParc>(`${environment.apiUrl}/tableau-bord/parc`, {
      params: new HttpParams().set('joursAlerte', joursAlerte)
    });
  }
}
