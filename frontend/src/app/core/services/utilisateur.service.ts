import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page, Role } from '../models/models';
import {
  ChangementMotDePasse,
  CreationUtilisateur,
  ModificationUtilisateur,
  ProfilRole,
  Utilisateur
} from '../models/utilisateur';

@Injectable({ providedIn: 'root' })
export class UtilisateurService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/utilisateurs`;

  lister(filtres: { q?: string; role?: Role; actif?: boolean; page?: number; size?: number } = {}):
    Observable<Page<Utilisateur>> {
    let params = new HttpParams();
    if (filtres.q) {
      params = params.set('q', filtres.q);
    }
    if (filtres.role) {
      params = params.set('role', filtres.role);
    }
    if (filtres.actif !== undefined) {
      params = params.set('actif', filtres.actif);
    }
    params = params.set('page', filtres.page ?? 0).set('size', filtres.size ?? 20);
    return this.http.get<Page<Utilisateur>>(this.base, { params });
  }

  profils(): Observable<ProfilRole[]> {
    return this.http.get<ProfilRole[]>(`${this.base}/profils`);
  }

  creer(demande: CreationUtilisateur): Observable<Utilisateur> {
    return this.http.post<Utilisateur>(this.base, demande);
  }

  modifier(id: number, demande: ModificationUtilisateur): Observable<Utilisateur> {
    return this.http.put<Utilisateur>(`${this.base}/${id}`, demande);
  }

  changerActivation(id: number, actif: boolean): Observable<Utilisateur> {
    return this.http.patch<Utilisateur>(`${this.base}/${id}/activation`, null, {
      params: new HttpParams().set('actif', actif)
    });
  }

  /** Code PIN de caisse, defini par l'administrateur ; nul pour l'effacer. */
  definirPin(id: number, pin: string | null): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/pin`, { pin });
  }

  /** Reinitialisation par un administrateur. */
  reinitialiserMotDePasse(id: number, motDePasse: string): Observable<void> {
    return this.http.put<void>(`${this.base}/${id}/mot-de-passe`, { motDePasse });
  }

  /** Changement par l'utilisateur connecte, qui doit fournir l'ancien. */
  /** Compte de la personne connectee. */
  moi(): Observable<Utilisateur> {
    return this.http.get<Utilisateur>(`${this.base}/moi`);
  }

  /** Nom, prenom et telephone, mis a jour par l'utilisateur lui-meme. */
  mettreAJourSonProfil(profil: { nom: string; prenom?: string | null; telephone?: string | null }): Observable<Utilisateur> {
    return this.http.put<Utilisateur>(`${this.base}/moi`, profil);
  }

  changerSonMotDePasse(demande: ChangementMotDePasse): Observable<void> {
    return this.http.put<void>(`${this.base}/moi/mot-de-passe`, demande);
  }
}
