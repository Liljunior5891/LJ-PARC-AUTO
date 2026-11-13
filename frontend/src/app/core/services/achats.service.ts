import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DocumentParc, DocumentRequete, Page } from '../models/models';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import {
  CategorieDepense,
  CategorieDepenseRequete,
  CompteFournisseur,
  Depense,
  DepenseAPayer,
  DepenseModification,
  DepenseRecurrente,
  DepenseRecurrenteRequete,
  DepenseRequete,
  Fournisseur,
  FournisseurRequete,
  ModeSortie,
  RecuReglementFournisseur,
  ReglementFournisseurRequete,
  SyntheseDepenses,
  SortieAttente,
  StatutDepense,
  ValidationsCaisse,
  TotauxFournisseurs
} from '../models/achats';

/** API des fournisseurs et des depenses du point de vente. */
@Injectable({ providedIn: 'root' })
export class AchatsService {
  private readonly http = inject(HttpClient);
  private readonly fournisseursUrl = `${environment.apiUrl}/pdv/fournisseurs`;
  private readonly depensesUrl = `${environment.apiUrl}/pdv/depenses`;

  // ---------------------------------------------------------- fournisseurs

  fournisseurs(filtre: { q?: string; actif?: boolean; dettes?: boolean; page?: number; size?: number }): Observable<Page<Fournisseur>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', filtre.size ?? 30);
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    if (filtre.actif !== undefined) {
      params = params.set('actif', filtre.actif);
    }
    if (filtre.dettes) {
      params = params.set('dettes', true);
    }
    return this.http.get<Page<Fournisseur>>(this.fournisseursUrl, { params });
  }

  fournisseursActifs(): Observable<Fournisseur[]> {
    return this.http.get<Fournisseur[]>(`${this.fournisseursUrl}/actifs`);
  }

  totauxFournisseurs(): Observable<TotauxFournisseurs> {
    return this.http.get<TotauxFournisseurs>(`${this.fournisseursUrl}/totaux`);
  }

  creerFournisseur(r: FournisseurRequete): Observable<Fournisseur> {
    return this.http.post<Fournisseur>(this.fournisseursUrl, r);
  }

  modifierFournisseur(id: number, r: FournisseurRequete): Observable<Fournisseur> {
    return this.http.put<Fournisseur>(`${this.fournisseursUrl}/${id}`, r);
  }

  archiverFournisseur(id: number): Observable<Fournisseur> {
    return this.http.post<Fournisseur>(`${this.fournisseursUrl}/${id}/archivage`, {});
  }

  reactiverFournisseur(id: number): Observable<Fournisseur> {
    return this.http.post<Fournisseur>(`${this.fournisseursUrl}/${id}/reactivation`, {});
  }

  compteFournisseur(id: number): Observable<CompteFournisseur> {
    return this.http.get<CompteFournisseur>(`${this.fournisseursUrl}/${id}/compte`);
  }

  reglerFournisseur(id: number, r: ReglementFournisseurRequete): Observable<RecuReglementFournisseur> {
    return this.http.post<RecuReglementFournisseur>(`${this.fournisseursUrl}/${id}/reglements`, r);
  }

  annulerReglementFournisseur(numero: string, motif: string): Observable<RecuReglementFournisseur> {
    return this.http.post<RecuReglementFournisseur>(
      `${this.fournisseursUrl}/reglements/${encodeURIComponent(numero)}/annulation`, { motif });
  }

  documentsFournisseur(id: number): Observable<Page<DocumentParc>> {
    return this.http.get<Page<DocumentParc>>(`${this.fournisseursUrl}/${id}/documents`);
  }

  /** Piece envoyee juste apres l'enregistrement de la fiche, deja confirme : pas de seconde confirmation. */
  ajouterDocumentFournisseur(id: number, piece: Omit<DocumentRequete, 'proprietaireType' | 'proprietaireId'>,
                             fichier: File | null): Observable<DocumentParc> {
    const requete: DocumentRequete = { ...piece, proprietaireType: 'FOURNISSEUR_PDV', proprietaireId: id };
    const form = new FormData();
    form.append('document', new Blob([JSON.stringify(requete)], { type: 'application/json' }));
    if (fichier) {
      form.append('fichier', fichier);
    }
    return this.http.post<DocumentParc>(`${this.fournisseursUrl}/${id}/documents`, form,
      { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  telechargerDocumentFournisseur(documentId: number): Observable<Blob> {
    return this.http.get(`${this.fournisseursUrl}/documents/${documentId}/fichier`, { responseType: 'blob' });
  }

  retirerDocumentFournisseur(documentId: number): Observable<void> {
    return this.http.post<void>(`${this.fournisseursUrl}/documents/${documentId}/archivage`, {});
  }

  // -------------------------------------------------------------- depenses

  depenses(filtre: {
    debut?: string; fin?: string; categorieId?: number | null; mode?: ModeSortie | '';
    statut?: StatutDepense | ''; q?: string; page?: number; size?: number;
  }): Observable<Page<Depense>> {
    let params = new HttpParams().set('page', filtre.page ?? 0).set('size', filtre.size ?? 30);
    if (filtre.debut) {
      params = params.set('debut', filtre.debut);
    }
    if (filtre.fin) {
      params = params.set('fin', filtre.fin);
    }
    if (filtre.categorieId) {
      params = params.set('categorieId', filtre.categorieId);
    }
    if (filtre.mode) {
      params = params.set('mode', filtre.mode);
    }
    if (filtre.statut) {
      params = params.set('statut', filtre.statut);
    }
    if (filtre.q) {
      params = params.set('q', filtre.q);
    }
    return this.http.get<Page<Depense>>(this.depensesUrl, { params });
  }

  syntheseDepenses(debut?: string, fin?: string): Observable<SyntheseDepenses> {
    let params = new HttpParams();
    if (debut) {
      params = params.set('debut', debut);
    }
    if (fin) {
      params = params.set('fin', fin);
    }
    return this.http.get<SyntheseDepenses>(`${this.depensesUrl}/synthese`, { params });
  }

  creerDepense(r: DepenseRequete): Observable<Depense> {
    return this.http.post<Depense>(this.depensesUrl, r);
  }

  modifierDepense(id: number, r: DepenseModification): Observable<Depense> {
    return this.http.put<Depense>(`${this.depensesUrl}/${id}`, r);
  }

  // ------------------------------------------- validation des depenses de caisse

  validationsCaisse(): Observable<ValidationsCaisse> {
    return this.http.get<ValidationsCaisse>(`${environment.apiUrl}/pdv/validations-caisse`);
  }

  /** {@code version} : celle vue a l'ecran ; une depense changee depuis n'est pas approuvee. */
  validerDepenseCaisse(id: number, version?: number | null): Observable<Depense> {
    const params: Record<string, number> = version === null || version === undefined ? {} : { version };
    return this.http.post<Depense>(`${environment.apiUrl}/pdv/validations-caisse/depenses/${id}/validation`, {}, { params });
  }

  refuserDepenseCaisse(id: number, motif: string): Observable<Depense> {
    return this.http.post<Depense>(`${environment.apiUrl}/pdv/validations-caisse/depenses/${id}/refus`, { motif });
  }

  validerSortieCaisse(id: number): Observable<SortieAttente> {
    return this.http.post<SortieAttente>(`${environment.apiUrl}/pdv/validations-caisse/sorties/${id}/validation`, {});
  }

  refuserSortieCaisse(id: number, motif: string): Observable<SortieAttente> {
    return this.http.post<SortieAttente>(`${environment.apiUrl}/pdv/validations-caisse/sorties/${id}/refus`, { motif });
  }

  annulerDepense(id: number, motif: string): Observable<Depense> {
    return this.http.post<Depense>(`${this.depensesUrl}/${id}/annulation`, { motif });
  }

  /** {@code apresSaisie} : envoi qui suit la saisie deja confirmee, sans seconde confirmation. */
  joindreJustificatif(id: number, fichier: File, apresSaisie = false): Observable<Depense> {
    const form = new FormData();
    form.append('fichier', fichier);
    return this.http.post<Depense>(`${this.depensesUrl}/${id}/justificatif`, form,
      apresSaisie ? { context: new HttpContext().set(SANS_SUIVI, true) } : {});
  }

  justificatif(id: number): Observable<Blob> {
    return this.http.get(`${this.depensesUrl}/${id}/justificatif`, { responseType: 'blob' });
  }

  retirerJustificatif(id: number): Observable<Depense> {
    return this.http.delete<Depense>(`${this.depensesUrl}/${id}/justificatif`);
  }

  ficheDepense(id: number): Observable<Blob> {
    return this.http.get(`${this.depensesUrl}/${id}/fiche-pdf`, { responseType: 'blob' });
  }

  bonCommande(id: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/pdv/approvisionnement/commandes/${id}/bon-pdf`, { responseType: 'blob' });
  }

  bonLivraison(id: number): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/pdv/approvisionnement/commandes/${id}/bon-livraison-pdf`, { responseType: 'blob' });
  }

  /** Ouvre un PDF dans un nouvel onglet (impression ou enregistrement par le navigateur). */
  ouvrirPdf(pdf: Blob): void {
    const url = URL.createObjectURL(pdf);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }

  categoriesDepense(): Observable<CategorieDepense[]> {
    return this.http.get<CategorieDepense[]>(`${this.depensesUrl}/categories`);
  }

  creerCategorieDepense(r: CategorieDepenseRequete): Observable<CategorieDepense> {
    return this.http.post<CategorieDepense>(`${this.depensesUrl}/categories`, r);
  }

  modifierCategorieDepense(id: number, r: CategorieDepenseRequete): Observable<CategorieDepense> {
    return this.http.put<CategorieDepense>(`${this.depensesUrl}/categories/${id}`, r);
  }

  supprimerCategorieDepense(id: number): Observable<void> {
    return this.http.delete<void>(`${this.depensesUrl}/categories/${id}`);
  }

  recurrentes(): Observable<DepenseRecurrente[]> {
    return this.http.get<DepenseRecurrente[]>(`${this.depensesUrl}/recurrentes`);
  }

  aPayer(): Observable<DepenseAPayer[]> {
    return this.http.get<DepenseAPayer[]>(`${this.depensesUrl}/a-payer`);
  }

  creerRecurrente(r: DepenseRecurrenteRequete): Observable<DepenseRecurrente> {
    return this.http.post<DepenseRecurrente>(`${this.depensesUrl}/recurrentes`, r);
  }

  modifierRecurrente(id: number, r: DepenseRecurrenteRequete): Observable<DepenseRecurrente> {
    return this.http.put<DepenseRecurrente>(`${this.depensesUrl}/recurrentes/${id}`, r);
  }

  supprimerRecurrente(id: number): Observable<void> {
    return this.http.delete<void>(`${this.depensesUrl}/recurrentes/${id}`);
  }
}
