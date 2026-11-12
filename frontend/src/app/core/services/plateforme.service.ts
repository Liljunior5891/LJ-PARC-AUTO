import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CodeModule, LoginResponse } from '../models/models';
import { TableauBordPlateforme } from '../models/abonnement';
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

  /** Premiere etape : un lien de confirmation part a l'adresse donnee. */
  inscrire(demande: DemandeInscription): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.api}/public/inscription`, demande);
  }

  /** Seconde etape : le lien est ouvert, le compte est cree et la session ouverte. */
  confirmerInscription(jeton: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.api}/public/inscription/confirmation`, { jeton });
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

  tableauBord(): Observable<TableauBordPlateforme> {
    return this.http.get<TableauBordPlateforme>(`${this.api}/plateforme/tableau-bord`);
  }

  /** Cree une entreprise, son administrateur et ses modules, en essai gratuit. */
  creerEntreprise(entreprise: DemandeInscription, joursEssai?: number | null): Observable<EntrepriseResume> {
    return this.http.post<EntrepriseResume>(`${this.api}/plateforme/entreprises`, { entreprise, joursEssai });
  }

  statistiques(): Observable<StatistiquesPlateforme> {
    return this.http.get<StatistiquesPlateforme>(`${this.api}/plateforme/statistiques`);
  }

  entreprises(): Observable<EntrepriseResume[]> {
    return this.http.get<EntrepriseResume[]>(`${this.api}/plateforme/entreprises`);
  }

  /** Module Agences : autorise ou non l'entreprise a gerer plusieurs agences. */
  autoriserAgencesMultiples(id: number, autorise: boolean): Observable<EntrepriseResume> {
    return this.http.patch<EntrepriseResume>(`${this.api}/plateforme/entreprises/${id}/agences-multiples`, null, {
      params: new HttpParams().set('autorise', autorise)
    });
  }

  changerStatut(id: number, statut: StatutEntreprise): Observable<EntrepriseResume> {
    return this.http.patch<EntrepriseResume>(`${this.api}/plateforme/entreprises/${id}/statut`, null, {
      params: new HttpParams().set('statut', statut)
    });
  }
}
