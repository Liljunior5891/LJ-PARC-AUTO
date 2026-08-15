import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentParc, DocumentRequete, Page, ProprietaireDocument, TypeDocument } from '../models/models';

export interface FiltreDocument {
  proprietaireType?: ProprietaireDocument | '';
  proprietaireId?: number;
  type?: TypeDocument | '';
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/documents`;

  lister(filtre: FiltreDocument = {}): Observable<Page<DocumentParc>> {
    let params = new HttpParams()
      .set('page', filtre.page ?? 0)
      .set('size', filtre.size ?? 20)
      .set('sort', 'dateExpiration,asc');
    if (filtre.proprietaireType) {
      params = params.set('proprietaireType', filtre.proprietaireType);
    }
    if (filtre.proprietaireId) {
      params = params.set('proprietaireId', filtre.proprietaireId);
    }
    if (filtre.type) {
      params = params.set('type', filtre.type);
    }
    return this.http.get<Page<DocumentParc>>(this.base, { params });
  }

  alertes(jours = 30): Observable<DocumentParc[]> {
    return this.http.get<DocumentParc[]>(`${this.base}/alertes/expiration`, {
      params: new HttpParams().set('jours', jours)
    });
  }

  creer(requete: DocumentRequete, fichier?: File | null): Observable<DocumentParc> {
    return this.http.post<DocumentParc>(this.base, this.enFormData(requete, fichier));
  }

  modifier(id: number, requete: DocumentRequete, fichier?: File | null): Observable<DocumentParc> {
    return this.http.put<DocumentParc>(`${this.base}/${id}`, this.enFormData(requete, fichier));
  }

  telecharger(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/fichier`, { responseType: 'blob' });
  }

  archiver(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** L'API attend un multipart : partie JSON "document" + fichier optionnel. */
  private enFormData(requete: DocumentRequete, fichier?: File | null): FormData {
    const form = new FormData();
    form.append('document', new Blob([JSON.stringify(requete)], { type: 'application/json' }));
    if (fichier) {
      form.append('fichier', fichier);
    }
    return form;
  }
}
