import { HttpClient, HttpContext, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { BilanImport, Rapport, RapportCatalogue, TypeImport } from '../models/rapport';

/** Fichier recu de l'API, avec le nom propose par le serveur. */
export interface FichierRecu {
  nom: string;
  contenu: Blob;
}

/** Format d'export d'un rapport. */
export type FormatExport = 'xlsx' | 'pdf' | 'csv';

/** Periode du rapport (absente pour un etat a l'instant) et produit de la fiche de stock. */
export interface OptionsRapport {
  debut?: string;
  fin?: string;
  article?: number | null;
  ecran?: number | null;
  fournisseur?: number | null;
}

/** Rapports, exports CSV / Excel / PDF et imports du point de vente. */
@Injectable({ providedIn: 'root' })
export class RapportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv/rapports`;
  private readonly fuseau = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Africa/Lome';

  catalogue(): Observable<RapportCatalogue[]> {
    return this.http.get<RapportCatalogue[]>(this.base);
  }

  rapport(type: string, options: OptionsRapport = {}): Observable<Rapport> {
    return this.http.get<Rapport>(`${this.base}/${type}`, { params: this.parametres(options) });
  }

  exporter(type: string, format: FormatExport, options: OptionsRapport = {}): Observable<FichierRecu> {
    return this.http
      .get(`${this.base}/${type}/export`, {
        params: this.parametres(options).set('format', format),
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(map((r) => this.fichier(r, `${type.toLowerCase()}.${format}`)));
  }

  modele(type: TypeImport, format: 'csv' | 'xlsx'): Observable<FichierRecu> {
    return this.http
      .get(`${this.base}/import/${type}/modele`, {
        params: new HttpParams().set('format', format).set('fuseau', this.fuseau),
        responseType: 'blob',
        observe: 'response'
      })
      .pipe(map((r) => this.fichier(r, `modele-${type.toLowerCase()}.${format}`)));
  }

  /** La verification (simulation) passe sans confirmation : elle n'enregistre rien. */
  importer(type: TypeImport, fichier: File, simulation: boolean): Observable<BilanImport> {
    const donnees = new FormData();
    donnees.append('fichier', fichier);
    return this.http.post<BilanImport>(`${this.base}/import/${type}`, donnees, {
      params: new HttpParams().set('simulation', simulation),
      context: new HttpContext().set(SANS_SUIVI, simulation)
    });
  }

  private parametres(options: OptionsRapport): HttpParams {
    let params = new HttpParams().set('fuseau', this.fuseau);
    if (options.debut && options.fin) {
      params = params.set('debut', options.debut).set('fin', options.fin);
    }
    if (options.ecran) {
      params = params.set('ecran', options.ecran);
    }
    if (options.fournisseur) {
      params = params.set('fournisseur', options.fournisseur);
    }
    return options.article ? params.set('article', options.article) : params;
  }

  private fichier(reponse: HttpResponse<Blob>, defaut: string): FichierRecu {
    const entete = reponse.headers.get('Content-Disposition') ?? '';
    const nom = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(entete)?.[1] ?? defaut;
    return { nom: decodeURIComponent(nom), contenu: reponse.body ?? new Blob() };
  }
}

/** Propose le fichier au telechargement, sans ouvrir de fenetre. */
export function enregistrerFichier(fichier: FichierRecu): void {
  const url = URL.createObjectURL(fichier.contenu);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = fichier.nom;
  document.body.appendChild(lien);
  lien.click();
  lien.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
