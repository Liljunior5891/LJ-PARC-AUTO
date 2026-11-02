import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ClientParc, ClientRequete, TypeClient } from '../models/exploitation';
import { Page } from '../models/models';

export interface FiltreClient {
  q?: string;
  type?: TypeClient | '';
  actif?: boolean;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class ClientService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/clients`;

  lister(filtre: FiltreClient = {}): Observable<Page<ClientParc>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'raisonSociale,asc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.actif !== undefined) {
      params = params.set('actif', filtre.actif);
    }
    return this.http.get<Page<ClientParc>>(this.base, { params });
  }

  /** Clients dont le numero de telephone est le meme, quelle que soit l'ecriture (+228, espaces...). */
  parTelephone(telephone: string): Observable<ClientParc[]> {
    return this.http.get<ClientParc[]>(`${this.base}/par-telephone`, { params: { telephone } });
  }

  parId(id: number): Observable<ClientParc> {
    return this.http.get<ClientParc>(`${this.base}/${id}`);
  }

  creer(client: Partial<ClientRequete>): Observable<ClientParc> {
    return this.http.post<ClientParc>(this.base, client);
  }

  modifier(id: number, client: Partial<ClientRequete>): Observable<ClientParc> {
    return this.http.put<ClientParc>(`${this.base}/${id}`, client);
  }

  archiver(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
