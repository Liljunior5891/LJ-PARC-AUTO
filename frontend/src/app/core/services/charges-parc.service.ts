import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CategorieCharge,
  Depense,
  DepenseRecurrenteParc,
  DepenseRecurrenteParcRequete,
  FicheFournisseurParc,
  FournisseurParc,
  FournisseurParcRequete,
  LigneChargeGenerale,
  ModePaiement,
  MoisAPayerParc,
  ReglementFournisseurParc
} from '../models/finance';

/**
 * Charges generales du parc (categories, recurrentes, reglage de validation),
 * fournisseurs et dettes, et les actions sur une depense (validation,
 * annulation, justificatif).
 */
@Injectable({ providedIn: 'root' })
export class ChargesParcService {
  private readonly http = inject(HttpClient);
  private readonly depenses = `${environment.apiUrl}/depenses`;
  private readonly charges = `${environment.apiUrl}/parc/charges`;
  private readonly fournisseursUrl = `${environment.apiUrl}/parc/fournisseurs`;

  // ------------------------------------------------------- depense : actions
  annuler(id: number, motif: string): Observable<Depense> {
    return this.http.post<Depense>(`${this.depenses}/${id}/annulation`, { motif });
  }

  aValider(): Observable<Depense[]> {
    return this.http.get<Depense[]>(`${this.depenses}/a-valider`);
  }

  valider(id: number): Observable<Depense> {
    return this.http.post<Depense>(`${this.depenses}/${id}/validation`, {});
  }

  refuser(id: number, motif: string): Observable<Depense> {
    return this.http.post<Depense>(`${this.depenses}/${id}/refus`, { motif });
  }

  joindre(id: number, fichier: File): Observable<Depense> {
    const form = new FormData();
    form.append('fichier', fichier);
    return this.http.post<Depense>(`${this.depenses}/${id}/justificatif`, form);
  }

  justificatif(id: number): Observable<Blob> {
    return this.http.get(`${this.depenses}/${id}/justificatif`, { responseType: 'blob' });
  }

  repartitionCharges(debut: string, fin: string): Observable<LigneChargeGenerale[]> {
    return this.http.get<LigneChargeGenerale[]>(`${this.depenses}/repartition/charges`,
      { params: new HttpParams().set('debut', debut).set('fin', fin) });
  }

  // ------------------------------------------------------------- reglage
  reglages(): Observable<{ validationDepenses: boolean }> {
    return this.http.get<{ validationDepenses: boolean }>(`${this.charges}/reglages`);
  }

  modifierReglages(validationDepenses: boolean): Observable<{ validationDepenses: boolean }> {
    return this.http.put<{ validationDepenses: boolean }>(`${this.charges}/reglages`, { validationDepenses });
  }

  // ---------------------------------------------------------- categories
  categories(): Observable<CategorieCharge[]> {
    return this.http.get<CategorieCharge[]>(`${this.charges}/categories`);
  }

  enregistrerCategorie(id: number | null, r: { nom: string; icone?: string; actif?: boolean }): Observable<CategorieCharge> {
    return id === null
      ? this.http.post<CategorieCharge>(`${this.charges}/categories`, r)
      : this.http.put<CategorieCharge>(`${this.charges}/categories/${id}`, r);
  }

  supprimerCategorie(id: number): Observable<void> {
    return this.http.delete<void>(`${this.charges}/categories/${id}`);
  }

  // --------------------------------------------------------- recurrentes
  recurrentes(): Observable<DepenseRecurrenteParc[]> {
    return this.http.get<DepenseRecurrenteParc[]>(`${this.charges}/recurrentes`);
  }

  enregistrerRecurrente(id: number | null, r: DepenseRecurrenteParcRequete): Observable<DepenseRecurrenteParc> {
    return id === null
      ? this.http.post<DepenseRecurrenteParc>(`${this.charges}/recurrentes`, r)
      : this.http.put<DepenseRecurrenteParc>(`${this.charges}/recurrentes/${id}`, r);
  }

  supprimerRecurrente(id: number): Observable<void> {
    return this.http.delete<void>(`${this.charges}/recurrentes/${id}`);
  }

  aPayer(): Observable<MoisAPayerParc[]> {
    return this.http.get<MoisAPayerParc[]>(`${this.charges}/a-payer`);
  }

  payer(recurrenteId: number, p: { periode: string; dateDepense?: string; montant?: number; modePaiement?: ModePaiement | null;
                                   reference?: string; aPayer?: boolean }): Observable<Depense> {
    return this.http.post<Depense>(`${this.charges}/recurrentes/${recurrenteId}/paiement`, p);
  }

  // -------------------------------------------------------- fournisseurs
  fournisseurs(): Observable<FournisseurParc[]> {
    return this.http.get<FournisseurParc[]>(this.fournisseursUrl);
  }

  fiche(id: number): Observable<FicheFournisseurParc> {
    return this.http.get<FicheFournisseurParc>(`${this.fournisseursUrl}/${id}`);
  }

  enregistrerFournisseur(id: number | null, r: FournisseurParcRequete): Observable<FournisseurParc> {
    return id === null
      ? this.http.post<FournisseurParc>(this.fournisseursUrl, r)
      : this.http.put<FournisseurParc>(`${this.fournisseursUrl}/${id}`, r);
  }

  supprimerFournisseur(id: number): Observable<void> {
    return this.http.delete<void>(`${this.fournisseursUrl}/${id}`);
  }

  dettes(fournisseurId?: number): Observable<Depense[]> {
    let params = new HttpParams();
    if (fournisseurId) {
      params = params.set('fournisseurId', fournisseurId);
    }
    return this.http.get<Depense[]>(`${this.fournisseursUrl}/dettes`, { params });
  }

  regler(r: { depenseId: number; montant: number; dateReglement?: string; modePaiement?: ModePaiement | null;
              reference?: string; note?: string }): Observable<ReglementFournisseurParc> {
    return this.http.post<ReglementFournisseurParc>(`${this.fournisseursUrl}/reglements`, r);
  }

  annulerReglement(id: number, motif: string): Observable<ReglementFournisseurParc> {
    return this.http.post<ReglementFournisseurParc>(`${this.fournisseursUrl}/reglements/${id}/annulation`, { motif });
  }
}
