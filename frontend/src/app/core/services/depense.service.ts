import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CategorieDepense,
  Depense,
  DepenseRequete,
  LigneCategorieDepense,
  LigneNatureDepense
} from '../models/finance';
import { Page } from '../models/models';

export interface FiltreDepense {
  q?: string;
  categorie?: CategorieDepense | '';
  sousCategorie?: string;
  camionId?: number;
  chauffeurId?: number;
  debut?: string;
  fin?: string;
  /** VEHICULE, GENERALE ou vide (tout). */
  type?: string;
  statut?: string;
  categorieChargeId?: number | null;
  page?: number;
  size?: number;
}

/** Natures de depenses groupees par categorie, servies par l'API. */
export type NaturesParCategorie = Record<CategorieDepense, { valeur: string; libelle: string }[]>;

@Injectable({ providedIn: 'root' })
export class DepenseService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/depenses`;

  lister(filtre: FiltreDepense = {}): Observable<Page<Depense>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateDepense,desc');
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.categorie) {
      params = params.set('categorie', filtre.categorie);
    }
    if (filtre.sousCategorie) {
      params = params.set('sousCategorie', filtre.sousCategorie);
    }
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
    }
    if (filtre.chauffeurId) {
      params = params.set('chauffeurId', filtre.chauffeurId);
    }
    if (filtre.debut) {
      params = params.set('debut', filtre.debut);
    }
    if (filtre.fin) {
      params = params.set('fin', filtre.fin);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.categorieChargeId) {
      params = params.set('categorieChargeId', filtre.categorieChargeId);
    }
    return this.http.get<Page<Depense>>(this.base, { params });
  }

  natures(): Observable<NaturesParCategorie> {
    return this.http.get<NaturesParCategorie>(`${this.base}/natures`);
  }

  repartition(debut: string, fin: string, camionId?: number): Observable<LigneCategorieDepense[]> {
    let params = new HttpParams().set('debut', debut).set('fin', fin);
    if (camionId) {
      params = params.set('camionId', camionId);
    }
    return this.http.get<LigneCategorieDepense[]>(`${this.base}/repartition`, { params });
  }

  repartitionNatures(camionId: number, debut: string, fin: string): Observable<LigneNatureDepense[]> {
    return this.http.get<LigneNatureDepense[]>(`${this.base}/repartition/natures`, {
      params: new HttpParams().set('camionId', camionId).set('debut', debut).set('fin', fin)
    });
  }

  creer(requete: DepenseRequete): Observable<Depense> {
    return this.http.post<Depense>(this.base, requete);
  }

  modifier(id: number, requete: DepenseRequete): Observable<Depense> {
    return this.http.put<Depense>(`${this.base}/${id}`, requete);
  }

}
