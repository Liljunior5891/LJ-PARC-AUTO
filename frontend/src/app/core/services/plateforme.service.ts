import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CodeModule, LoginResponse } from '../models/models';
import {
  DemandeInscription,
  EntrepriseResume,
  ModuleCatalogue,
  StatistiquesPlateforme,
  StatutEntreprise
} from '../models/plateforme';

/** Modules de l'entreprise, inscription et console de l'exploitant. */
@Injectable({ providedIn: 'root' })
export class PlateformeService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  /** Catalogue public, pour la page d'accueil et l'inscription. */
  cataloguePublic(): Observable<ModuleCatalogue[]> {
    return this.http.get<ModuleCatalogue[]>(`${this.api}/public/modules`);
  }

  inscrire(demande: DemandeInscription): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.api}/public/inscription`, demande);
  }

  /** Catalogue avec l'etat d'activation pour l'entreprise connectee. */
  modules(): Observable<ModuleCatalogue[]> {
    return this.http.get<ModuleCatalogue[]>(`${this.api}/modules`);
  }

  /** Active un module, avec le type d'activite choisi s'il se decline. */
  activer(module: CodeModule, variante?: string): Observable<ModuleCatalogue[]> {
    return this.http.post<ModuleCatalogue[]>(`${this.api}/modules/${module}/activation`, variante ? { variante } : null);
  }

  changerVariante(module: CodeModule, variante: string): Observable<ModuleCatalogue[]> {
    return this.http.put<ModuleCatalogue[]>(`${this.api}/modules/${module}/variante`, { variante });
  }

  desactiver(module: CodeModule): Observable<ModuleCatalogue[]> {
    return this.http.delete<ModuleCatalogue[]>(`${this.api}/modules/${module}/activation`);
  }

  statistiques(): Observable<StatistiquesPlateforme> {
    return this.http.get<StatistiquesPlateforme>(`${this.api}/plateforme/statistiques`);
  }

  entreprises(): Observable<EntrepriseResume[]> {
    return this.http.get<EntrepriseResume[]>(`${this.api}/plateforme/entreprises`);
  }

  changerStatut(id: number, statut: StatutEntreprise): Observable<EntrepriseResume> {
    return this.http.patch<EntrepriseResume>(`${this.api}/plateforme/entreprises/${id}/statut`, null, {
      params: new HttpParams().set('statut', statut)
    });
  }
}
