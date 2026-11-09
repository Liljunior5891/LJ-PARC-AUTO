import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Imprimante, ImprimanteRequete, TravailImpression } from '../models/restauration';

/** Imprimantes de preparation et file des bons de cuisine. */
@Injectable({ providedIn: 'root' })
export class ImpressionService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv/impression`;

  imprimantes(): Observable<Imprimante[]> {
    return this.http.get<Imprimante[]>(`${this.base}/imprimantes`);
  }

  creerImprimante(r: ImprimanteRequete): Observable<Imprimante> {
    return this.http.post<Imprimante>(`${this.base}/imprimantes`, r);
  }

  modifierImprimante(id: number, r: ImprimanteRequete): Observable<Imprimante> {
    return this.http.put<Imprimante>(`${this.base}/imprimantes/${id}`, r);
  }

  essai(id: number): Observable<TravailImpression> {
    return this.http.post<TravailImpression>(`${this.base}/imprimantes/${id}/essai`, {});
  }

  /** Bons que cette caisse doit imprimer par le navigateur. */
  aImprimer(caisseId?: number | null): Observable<TravailImpression[]> {
    const params: Record<string, string> = caisseId ? { caisseId: String(caisseId) } : {};
    return this.http.get<TravailImpression[]>(`${this.base}/a-imprimer`, { params });
  }

  /** Prend les bons pour les imprimer : seuls ceux qu'aucune autre caisse n'a pris sont rendus. */
  prendre(ids: number[]): Observable<TravailImpression[]> {
    return this.http.post<TravailImpression[]>(`${this.base}/prise`, ids);
  }

  duplicata(id: number): Observable<TravailImpression> {
    return this.http.post<TravailImpression>(`${this.base}/bons/${id}/duplicata`, {});
  }

  deLaCommande(commandeId: number): Observable<TravailImpression[]> {
    return this.http.get<TravailImpression[]>(`${this.base}/commandes/${commandeId}`);
  }
}
