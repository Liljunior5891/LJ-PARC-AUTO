import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/models';

/** Valeur du filtre entreprise qui designe les actions de la plateforme elle-meme. */
export const JOURNAL_PLATEFORME = 'plateforme';

export interface ChangementJournal {
  champ: string;
  libelle: string;
  avant?: string | null;
  apres?: string | null;
}

export interface LigneJournalGeneral {
  id: string;
  date: string;
  entrepriseId?: number | null;
  entrepriseNom?: string | null;
  auteurId?: number | null;
  auteurNom?: string | null;
  module: string;
  action: string;
  objet: string;
  objetLibelle: string;
  objetId?: number | null;
  description: string;
  adresseIp?: string | null;
  changements: ChangementJournal[];
}

export interface CompteJournal {
  valeur: string;
  libelle: string;
  nombre: number;
}

export interface SyntheseJournal {
  total: number;
  creations: number;
  modifications: number;
  suppressions: number;
  connexions: number;
  connexionsRefusees: number;
  actionsPlateforme: number;
  entreprisesActives: number;
  parModule: CompteJournal[];
  parEntreprise: CompteJournal[];
  parAuteur: CompteJournal[];
  parJour: { jour: string; nombre: number }[];
}

export interface FiltresJournalGeneral {
  entreprise: string;
  module: string;
  action: string;
  du: string;
  au: string;
  recherche: string;
}

/** Journal d'audit general du super-admin : toutes les entreprises et la plateforme. */
@Injectable({ providedIn: 'root' })
export class JournalPlateformeService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/plateforme/journal`;

  /** Une page ; au-dela de la premiere, le total deja connu evite au serveur de recompter le journal. */
  lister(f: FiltresJournalGeneral, page: number, total?: number): Observable<Page<LigneJournalGeneral>> {
    let params = this.params(f).set('page', page).set('size', 50);
    if (page > 0 && total !== undefined) {
      params = params.set('total', total);
    }
    return this.http.get<Page<LigneJournalGeneral>>(this.base, { params });
  }

  /** Rapport aux memes filtres que la liste. */
  synthese(f: FiltresJournalGeneral): Observable<SyntheseJournal> {
    return this.http.get<SyntheseJournal>(`${this.base}/synthese`, { params: this.params(f) });
  }

  entreprises(): Observable<{ valeur: string; libelle: string }[]> {
    return this.http.get<{ valeur: string; libelle: string }[]>(`${this.base}/entreprises`);
  }

  private params(f: FiltresJournalGeneral): HttpParams {
    let p = new HttpParams();
    for (const [cle, valeur] of Object.entries(f)) {
      if (valeur) {
        p = p.set(cle, valeur);
      }
    }
    return p;
  }
}
