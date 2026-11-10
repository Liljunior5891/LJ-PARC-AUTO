import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AbonnementVue,
  DeclarationPaiement,
  DemandeDevis,
  Devis,
  EtatCourtAbonnement,
  Formule,
  FormuleRequete,
  OffreAbonnement,
  PaiementAbonnement,
  ParametresAbonnement,
  SaisiePaiement
} from '../models/abonnement';

/** Abonnement de l'entreprise connectee et console des abonnements de l'exploitant. */
@Injectable({ providedIn: 'root' })
export class AbonnementService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/abonnement`;
  private readonly plateforme = `${environment.apiUrl}/plateforme/abonnements`;

  /** Dernier etat connu, pour le bandeau de rappel. */
  readonly etatCourant = signal<EtatCourtAbonnement | null>(null);

  // ------------------------------------------------------------ entreprise

  etat(): Observable<EtatCourtAbonnement> {
    return this.http.get<EtatCourtAbonnement>(`${this.api}/etat`).pipe(tap((e) => this.etatCourant.set(e)));
  }

  abonnement(): Observable<AbonnementVue> {
    return this.http.get<AbonnementVue>(this.api);
  }

  offre(): Observable<OffreAbonnement> {
    return this.http.get<OffreAbonnement>(`${this.api}/offre`);
  }

  devis(demande: DemandeDevis, plateforme = false): Observable<Devis> {
    return this.http.post<Devis>(`${plateforme ? this.plateforme : this.api}/devis`, demande);
  }

  paiements(): Observable<PaiementAbonnement[]> {
    return this.http.get<PaiementAbonnement[]>(`${this.api}/paiements`);
  }

  declarer(declaration: DeclarationPaiement): Observable<PaiementAbonnement> {
    return this.http.post<PaiementAbonnement>(`${this.api}/paiements`, declaration);
  }

  annuler(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/paiements/${id}`);
  }

  joindrePreuve(id: number, fichier: File): Observable<PaiementAbonnement> {
    const corps = new FormData();
    corps.append('fichier', fichier);
    return this.http.post<PaiementAbonnement>(`${this.api}/paiements/${id}/preuve`, corps);
  }

  /** Preuve d'un paiement ; cote console si plateforme. */
  preuve(id: number, plateforme = false): Observable<Blob> {
    return this.http.get(`${plateforme ? this.plateforme : this.api}/paiements/${id}/preuve`, { responseType: 'blob' });
  }

  // --------------------------------------------------------------- console

  tousLesAbonnements(): Observable<AbonnementVue[]> {
    return this.http.get<AbonnementVue[]>(this.plateforme);
  }

  abonnementEntreprise(id: number): Observable<AbonnementVue> {
    return this.http.get<AbonnementVue>(`${this.plateforme}/entreprises/${id}`);
  }

  paiementsEntreprise(id: number): Observable<PaiementAbonnement[]> {
    return this.http.get<PaiementAbonnement[]>(`${this.plateforme}/entreprises/${id}/paiements`);
  }

  enregistrer(entrepriseId: number, saisie: SaisiePaiement): Observable<PaiementAbonnement> {
    return this.http.post<PaiementAbonnement>(`${this.plateforme}/entreprises/${entrepriseId}/paiements`, saisie);
  }

  prolonger(entrepriseId: number, jours: number): Observable<AbonnementVue> {
    return this.http.post<AbonnementVue>(`${this.plateforme}/entreprises/${entrepriseId}/prolongation`, null, {
      params: new HttpParams().set('jours', jours)
    });
  }

  formules(): Observable<Formule[]> {
    return this.http.get<Formule[]>(`${this.plateforme}/formules`);
  }

  creerFormule(f: FormuleRequete): Observable<Formule> {
    return this.http.post<Formule>(`${this.plateforme}/formules`, f);
  }

  modifierFormule(id: number, f: FormuleRequete): Observable<Formule> {
    return this.http.put<Formule>(`${this.plateforme}/formules/${id}`, f);
  }

  supprimerFormule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.plateforme}/formules/${id}`);
  }

  parametres(): Observable<ParametresAbonnement> {
    return this.http.get<ParametresAbonnement>(`${this.plateforme}/parametres`);
  }

  modifierParametres(p: ParametresAbonnement): Observable<ParametresAbonnement> {
    return this.http.put<ParametresAbonnement>(`${this.plateforme}/parametres`, p);
  }

  tousLesPaiements(): Observable<PaiementAbonnement[]> {
    return this.http.get<PaiementAbonnement[]>(`${this.plateforme}/paiements`);
  }

  valider(id: number): Observable<PaiementAbonnement> {
    return this.http.post<PaiementAbonnement>(`${this.plateforme}/paiements/${id}/validation`, null);
  }

  rejeter(id: number, motif: string): Observable<PaiementAbonnement> {
    return this.http.post<PaiementAbonnement>(`${this.plateforme}/paiements/${id}/rejet`, { motif });
  }
}
