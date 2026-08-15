import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, Role, UtilisateurCourant } from '../models/models';

const CLE_TOKEN = 'parcauto.token';
const CLE_UTILISATEUR = 'parcauto.utilisateur';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly utilisateurSignal = signal<UtilisateurCourant | null>(this.lireUtilisateurStocke());

  readonly utilisateur = this.utilisateurSignal.asReadonly();
  readonly connecte = computed(() => this.utilisateurSignal() !== null);

  login(email: string, motDePasse: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/auth/login`, { email, motDePasse })
      .pipe(
        tap((reponse) => {
          localStorage.setItem(CLE_TOKEN, reponse.token);
          localStorage.setItem(CLE_UTILISATEUR, JSON.stringify(reponse.utilisateur));
          this.utilisateurSignal.set(reponse.utilisateur);
        })
      );
  }

  logout(): void {
    localStorage.removeItem(CLE_TOKEN);
    localStorage.removeItem(CLE_UTILISATEUR);
    this.utilisateurSignal.set(null);
    void this.router.navigate(['/connexion']);
  }

  get token(): string | null {
    return localStorage.getItem(CLE_TOKEN);
  }

  /** Vrai si l'utilisateur possede au moins un des roles demandes. */
  aRole(...roles: Role[]): boolean {
    const courant = this.utilisateurSignal();
    if (!courant) {
      return false;
    }
    return roles.some((role) => courant.roles.includes(role));
  }

  /** Roles autorises a modifier le parc (camions, chauffeurs, affectations, documents). */
  peutGererParc(): boolean {
    return this.aRole('ADMIN', 'GESTIONNAIRE_PARC', 'LOGISTIQUE');
  }

  private lireUtilisateurStocke(): UtilisateurCourant | null {
    const brut = localStorage.getItem(CLE_UTILISATEUR);
    if (!brut || !localStorage.getItem(CLE_TOKEN)) {
      return null;
    }
    try {
      return JSON.parse(brut) as UtilisateurCourant;
    } catch {
      return null;
    }
  }
}
