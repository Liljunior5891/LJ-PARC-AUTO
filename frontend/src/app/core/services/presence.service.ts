import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  BornePresence,
  CompteConvertible,
  EmployeAvecPin,
  EmployePresence,
  EmployeRequete,
  HistoriquePresence,
  JourPresence,
  LieuPresence,
  LieuRequete,
  ReglagePresence,
  ResultatPointage
} from '../models/presence';

/** Gestion de presence : suivi, employes, lieux et bornes, horaire ; et le pointage sans compte. */
@Injectable({ providedIn: 'root' })
export class PresenceService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/presence`;
  private readonly public = `${environment.apiUrl}/public/presence`;

  // ------------------------------------------------------------ suivi

  jour(date?: string, lieuId?: number | null): Observable<JourPresence> {
    const params: Record<string, string> = {};
    if (date) {
      params['date'] = date;
    }
    if (lieuId) {
      params['lieuId'] = String(lieuId);
    }
    return this.http.get<JourPresence>(`${this.base}/jour`, { params });
  }

  historique(employeId: number, mois?: string): Observable<HistoriquePresence> {
    return this.http.get<HistoriquePresence>(`${this.base}/employes/${employeId}/historique`, { params: mois ? { mois } : {} });
  }

  // ------------------------------------------------------------ employes

  employes(): Observable<EmployePresence[]> {
    return this.http.get<EmployePresence[]>(`${this.base}/employes`);
  }

  employe(id: number): Observable<EmployePresence> {
    return this.http.get<EmployePresence>(`${this.base}/employes/${id}`);
  }

  creerEmploye(r: EmployeRequete): Observable<EmployeAvecPin> {
    return this.http.post<EmployeAvecPin>(`${this.base}/employes`, r);
  }

  modifierEmploye(id: number, r: EmployeRequete): Observable<EmployePresence> {
    return this.http.put<EmployePresence>(`${this.base}/employes/${id}`, r);
  }

  reinitialiserPin(id: number): Observable<EmployeAvecPin> {
    return this.http.post<EmployeAvecPin>(`${this.base}/employes/${id}/pin`, {});
  }

  comptesConvertibles(): Observable<CompteConvertible[]> {
    return this.http.get<CompteConvertible[]>(`${this.base}/comptes-convertibles`);
  }

  // ------------------------------------------------------------ lieux et bornes

  lieux(): Observable<LieuPresence[]> {
    return this.http.get<LieuPresence[]>(`${this.base}/lieux`);
  }

  responsablesPossibles(): Observable<CompteConvertible[]> {
    return this.http.get<CompteConvertible[]>(`${this.base}/responsables-possibles`);
  }

  creerLieu(r: LieuRequete): Observable<LieuPresence> {
    return this.http.post<LieuPresence>(`${this.base}/lieux`, r);
  }

  modifierLieu(id: number, r: LieuRequete): Observable<LieuPresence> {
    return this.http.put<LieuPresence>(`${this.base}/lieux/${id}`, r);
  }

  renouvelerSecret(id: number): Observable<LieuPresence> {
    return this.http.post<LieuPresence>(`${this.base}/lieux/${id}/secret`, {});
  }

  creerBorne(lieuId: number, nom: string): Observable<BornePresence> {
    return this.http.post<BornePresence>(`${this.base}/bornes`, { lieuId, nom });
  }

  reappairer(id: number): Observable<BornePresence> {
    return this.http.post<BornePresence>(`${this.base}/bornes/${id}/appairage`, {});
  }

  basculerBorne(id: number): Observable<BornePresence> {
    return this.http.post<BornePresence>(`${this.base}/bornes/${id}/activation`, {});
  }

  // ------------------------------------------------------------ horaire

  reglage(): Observable<ReglagePresence> {
    return this.http.get<ReglagePresence>(`${this.base}/reglage`);
  }

  modifierReglage(r: ReglagePresence): Observable<ReglagePresence> {
    return this.http.put<ReglagePresence>(`${this.base}/reglage`, r);
  }

  // ------------------------------------------------------------ pointage sans compte (telephone)

  accueil(jeton: string, code: string | null): Observable<{ lieu: string; entreprise: string; codeValide: boolean }> {
    return this.http.get<{ lieu: string; entreprise: string; codeValide: boolean }>(`${this.public}/lieux/${jeton}`,
      { params: code ? { c: code } : {} });
  }

  pointer(jeton: string, demande: { code: string | null; pin: string; latitude?: number; longitude?: number; precision?: number }):
    Observable<ResultatPointage> {
    return this.http.post<ResultatPointage>(`${this.public}/lieux/${jeton}/pointage`, demande);
  }

  monHistorique(jeton: string, pin: string, mois?: string): Observable<HistoriquePresence> {
    return this.http.post<HistoriquePresence>(`${this.public}/lieux/${jeton}/historique`, { pin, mois });
  }

  // ------------------------------------------------------------ borne

  appairer(code: string): Observable<{ jeton: string; lieu: string; borne: string }> {
    return this.http.post<{ jeton: string; lieu: string; borne: string }>(`${this.public}/appairage`, { code });
  }

  qrBorne(jeton: string, base: string):
    Observable<{ lieu: string; borne: string; entreprise: string; adresse: string; svg: string; secondesRestantes: number }> {
    return this.http.get<{ lieu: string; borne: string; entreprise: string; adresse: string; svg: string; secondesRestantes: number }>(
      `${this.public}/bornes/${jeton}/qr`, { params: { base } });
  }

  pointerBorne(jeton: string, pin: string): Observable<ResultatPointage> {
    return this.http.post<ResultatPointage>(`${this.public}/bornes/${jeton}/pointage`, { pin });
  }

  historiqueBorne(jeton: string, pin: string): Observable<HistoriquePresence> {
    return this.http.post<HistoriquePresence>(`${this.public}/bornes/${jeton}/historique`, { pin });
  }
}
