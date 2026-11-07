import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Billettage } from '../models/pdv';
import { Page } from '../models/models';
import {
  Associe,
  Benefices,
  Coffre,
  CompteEntreprise,
  Degagement,
  EnAttenteTresorerie,
  EtatRemiseSession,
  PartAssocie,
  ReglagesPdv,
  Remise
} from '../models/options-pdv';

/**
 * Options du point de vente (benefices des associes, circuit des especes) :
 * l'etat des reglages, garde en memoire pour le menu et les ecrans, et les
 * appels de chaque option.
 */
@Injectable({ providedIn: 'root' })
export class OptionsPdvService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv`;

  /** Reglages lus au demarrage ; indefinis tant qu'ils ne sont pas arrives. */
  readonly reglages = signal<ReglagesPdv | undefined>(undefined);
  readonly associesActif = computed(() => !!this.reglages()?.associesActif);
  readonly circuitActif = computed(() => !!this.reglages()?.circuitCaisseActif);
  /** Validations en attente, pour la pastille du menu. */
  readonly enAttente = signal<EnAttenteTresorerie>({ remises: 0, degagements: 0 });

  charger(): void {
    this.http.get<ReglagesPdv>(`${this.base}/reglages`).subscribe({
      next: (r) => this.reglages.set(r),
      error: () => this.reglages.set({ associesActif: false, circuitCaisseActif: false, ecartTolere: 0 })
    });
  }

  /** Lecture fraiche des reglages (ecran des reglages), qui met aussi a jour l'etat garde en memoire. */
  lireReglages(): Observable<ReglagesPdv> {
    return this.http.get<ReglagesPdv>(`${this.base}/reglages`).pipe(tap((r) => this.reglages.set(r)));
  }

  modifierReglages(r: ReglagesPdv): Observable<ReglagesPdv> {
    return this.http.put<ReglagesPdv>(`${this.base}/reglages`, r).pipe(tap((nouveaux) => this.reglages.set(nouveaux)));
  }

  // ----------------------------------------------------------- associes

  associes(): Observable<Associe[]> {
    return this.http.get<Associe[]>(`${this.base}/associes`);
  }

  remplacerParts(associeId: number, parts: PartAssocie[]): Observable<Associe> {
    return this.http.put<Associe>(`${this.base}/associes/${associeId}/parts`, {
      parts: parts.map((p) => ({ articleId: p.articleId ?? null, categorieId: p.categorieId ?? null, taux: p.taux }))
    });
  }

  benefices(debut: Date, fin: Date): Observable<Benefices> {
    const params = new HttpParams().set('debut', debut.toISOString()).set('fin', fin.toISOString());
    return this.http.get<Benefices>(`${this.base}/associes/benefices`, { params });
  }

  // ------------------------------------------------------------ tresorerie

  rafraichirEnAttente(): void {
    this.http.get<EnAttenteTresorerie>(`${this.base}/tresorerie/en-attente`).subscribe({
      next: (e) => this.enAttente.set(e),
      error: () => this.enAttente.set({ remises: 0, degagements: 0 })
    });
  }

  etatSession(sessionId: number): Observable<EtatRemiseSession> {
    return this.http.get<EtatRemiseSession>(`${this.base}/tresorerie/sessions/${sessionId}`);
  }

  envoyerRemise(sessionId: number, billettage: Billettage, justification?: string): Observable<Remise> {
    return this.http.post<Remise>(`${this.base}/tresorerie/sessions/${sessionId}/remises`, { billettage, justification });
  }

  remisesEnAttente(): Observable<Remise[]> {
    return this.http.get<Remise[]>(`${this.base}/tresorerie/remises/en-attente`);
  }

  historiqueRemises(page = 0): Observable<Page<Remise>> {
    return this.http.get<Page<Remise>>(`${this.base}/tresorerie/remises`, { params: new HttpParams().set('page', page) });
  }

  validerRemise(id: number, billettage: Billettage, commentaire?: string): Observable<Remise> {
    return this.http.post<Remise>(`${this.base}/tresorerie/remises/${id}/validation`, { billettage, commentaire })
      .pipe(tap(() => this.rafraichirEnAttente()));
  }

  refuserRemise(id: number, motif: string): Observable<Remise> {
    return this.http.post<Remise>(`${this.base}/tresorerie/remises/${id}/refus`, { motif })
      .pipe(tap(() => this.rafraichirEnAttente()));
  }

  coffre(): Observable<Coffre> {
    return this.http.get<Coffre>(`${this.base}/tresorerie/coffre`);
  }

  envoyerDegagement(depot: { billettage: Billettage; justification?: string; banque?: string | null;
                      referenceDepot: string; dateDepot: string }): Observable<Degagement> {
    return this.http.post<Degagement>(`${this.base}/tresorerie/degagements`, depot);
  }

  /** Bordereau envoye juste apres le depot deja confirme : pas de seconde confirmation. */
  joindrePieceDegagement(id: number, fichier: File): Observable<Degagement> {
    const form = new FormData();
    form.append('fichier', fichier);
    return this.http.post<Degagement>(`${this.base}/tresorerie/degagements/${id}/piece`, form,
      { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  pieceDegagement(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/tresorerie/degagements/${id}/piece`, { responseType: 'blob' });
  }

  degagementsEnAttente(): Observable<Degagement[]> {
    return this.http.get<Degagement[]>(`${this.base}/tresorerie/degagements/en-attente`);
  }

  historiqueDegagements(page = 0): Observable<Page<Degagement>> {
    return this.http.get<Page<Degagement>>(`${this.base}/tresorerie/degagements`, { params: new HttpParams().set('page', page) });
  }

  validerDegagement(id: number, montantValide: number, commentaire?: string): Observable<Degagement> {
    return this.http.post<Degagement>(`${this.base}/tresorerie/degagements/${id}/validation`, { montantValide, commentaire })
      .pipe(tap(() => this.rafraichirEnAttente()));
  }

  refuserDegagement(id: number, motif: string): Observable<Degagement> {
    return this.http.post<Degagement>(`${this.base}/tresorerie/degagements/${id}/refus`, { motif })
      .pipe(tap(() => this.rafraichirEnAttente()));
  }

  compteEntreprise(): Observable<CompteEntreprise> {
    return this.http.get<CompteEntreprise>(`${this.base}/tresorerie/compte-entreprise`);
  }
}
