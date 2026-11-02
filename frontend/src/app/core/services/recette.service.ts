import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  LigneTypeRecette,
  Recette,
  RecetteLocationRequete,
  RecetteRequete,
  TypeRecette
} from '../models/finance';
import { Page } from '../models/models';

/** Engagement du vehicule sur une partie de la periode d'une recette. */
export interface OccupationVehicule {
  reference: string;
  debut: string;
  fin?: string | null;
  message: string;
}

export interface FiltreRecette {
  q?: string;
  type?: TypeRecette | '';
  camionId?: number;
  clientId?: number;
  debut?: string;
  fin?: string;
  page?: number;
  size?: number;
  /** Canal : vrai = en ligne (Marketplace), faux = agence. */
  enLigne?: boolean;
}

@Injectable({ providedIn: 'root' })
export class RecetteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/recettes`;

  /** Engagements du vehicule (location, mission, transport...) qui chevauchent la periode d'une recette. */
  disponibilite(camionId: number, type: string, debut: string, fin: string | null,
                demandeId?: number | null): Observable<OccupationVehicule[]> {
    let params = new HttpParams().set('camionId', camionId).set('type', type).set('debut', debut);
    if (demandeId) {
      params = params.set('demandeId', demandeId);
    }
    if (fin) {
      params = params.set('fin', fin);
    }
    return this.http.get<OccupationVehicule[]>(`${this.base}/disponibilite`, { params });
  }

  /** Contrat de la recette (PDF), redige selon son type. */
  contratPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/contrat-pdf`, { responseType: 'blob' });
  }

  /**
   * Ouvre le contrat dans un nouvel onglet, pret a imprimer. L'onglet s'ouvre
   * tout de suite (au clic) pour ne pas etre bloque par le navigateur, puis
   * recoit le PDF ; sans onglet possible, le fichier est telecharge.
   */
  ouvrirContrat(recette: { id: number; numero: string }, echec: (message: string) => void): void {
    const onglet = window.open('', '_blank');
    this.contratPdf(recette.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        if (onglet) {
          onglet.location.href = url;
        } else {
          const lien = document.createElement('a');
          lien.href = url;
          lien.download = `contrat_${recette.numero}.pdf`;
          lien.click();
        }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => {
        onglet?.close();
        echec('Edition du contrat impossible.');
      }
    });
  }

  lister(filtre: FiltreRecette = {}): Observable<Page<Recette>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateRecette,desc');
    if (filtre.enLigne !== undefined) {
      params = params.set('enLigne', filtre.enLigne);
    }
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    if (filtre.camionId) {
      params = params.set('camionId', filtre.camionId);
    }
    if (filtre.clientId) {
      params = params.set('clientId', filtre.clientId);
    }
    if (filtre.debut) {
      params = params.set('debut', filtre.debut);
    }
    if (filtre.fin) {
      params = params.set('fin', filtre.fin);
    }
    return this.http.get<Page<Recette>>(this.base, { params });
  }

  repartition(debut: string, fin: string, camionId?: number): Observable<LigneTypeRecette[]> {
    let params = new HttpParams().set('debut', debut).set('fin', fin);
    if (camionId) {
      params = params.set('camionId', camionId);
    }
    return this.http.get<LigneTypeRecette[]>(`${this.base}/repartition`, { params });
  }

  /** Recette de location : le serveur cree aussi le contrat correspondant. */
  enregistrerLocation(requete: RecetteLocationRequete): Observable<Recette> {
    return this.http.post<Recette>(`${this.base}/location`, requete);
  }

  creer(requete: RecetteRequete): Observable<Recette> {
    return this.http.post<Recette>(this.base, requete);
  }

  modifier(id: number, requete: RecetteRequete): Observable<Recette> {
    return this.http.put<Recette>(`${this.base}/${id}`, requete);
  }

  supprimer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
