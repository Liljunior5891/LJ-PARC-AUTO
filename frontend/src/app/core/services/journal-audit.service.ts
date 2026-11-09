import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/models';

/** Un champ change : valeur avant et apres. */
export interface ChangementAudit {
  champ: string;
  libelle: string;
  avant?: string | null;
  apres?: string | null;
}

export interface LigneJournal {
  id: number;
  date: string;
  auteurId?: number | null;
  auteurNom?: string | null;
  module: string;
  action: 'CREATION' | 'MODIFICATION' | 'SUPPRESSION' | 'CONNEXION' | 'ECHEC_CONNEXION' | string;
  objet: string;
  objetLibelle: string;
  objetId?: number | null;
  description: string;
  adresseIp?: string | null;
  changements: ChangementAudit[];
}

export interface FiltresJournal {
  module?: string;
  action?: string;
  objet?: string;
  auteurId?: number | null;
  du?: string;
  au?: string;
  recherche?: string;
}

export interface ReferentielsJournal {
  objets: { valeur: string; libelle: string }[];
  auteurs: { valeur: string; libelle: string }[];
}

/** Journal d'audit des modules de la plateforme (hors Agences, qui a le sien). */
@Injectable({ providedIn: 'root' })
export class JournalAuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/journal-audit`;

  lister(f: FiltresJournal, page: number): Observable<Page<LigneJournal>> {
    let params = new HttpParams().set('page', page).set('size', 50);
    for (const [cle, valeur] of Object.entries(f)) {
      if (valeur !== undefined && valeur !== null && valeur !== '') {
        params = params.set(cle, String(valeur));
      }
    }
    return this.http.get<Page<LigneJournal>>(this.base, { params });
  }

  referentiels(): Observable<ReferentielsJournal> {
    return this.http.get<ReferentielsJournal>(`${this.base}/referentiels`);
  }
}
