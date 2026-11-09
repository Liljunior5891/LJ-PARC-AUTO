import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Page } from '../models/models';
import { MembrePersonnel } from '../models/pdv';
import { Rapport } from '../models/rapport';
import { FichierRecu } from './rapport.service';
import { AgencesHorsLigneService } from './agences-hors-ligne.service';
import {
  Agence,
  AttenteOperateur,
  CommissionAttente,
  VersementCommission,
  AlerteAgence,
  RapportAgenceCatalogue,
  ProduitCarte,
  StockCarte,
  SuiviCartes,
  VenteCarte,
  CategorieDepense,
  ChargeRecurrente,
  CreditAgence,
  DepenseAgence,
  ResumeDepenses,
  SyntheseDepenses,
  TypeCredit,
  Cloture,
  ClotureAFaire,
  LigneAudit,
  PreparationCloture,
  TableauBordAgences,
  EtatCaisses,
  LigneBillettageAgence,
  MouvementAgence,
  RequeteMouvement,
  SessionCaisseAgence,
  StatutMouvement,
  ApercuTransaction,
  ClientAgence,
  FiltresTransactions,
  ResumeTransactions,
  SaisieTransaction,
  Transaction,
  Bareme,
  CommissionCalculee,
  ControleBareme,
  RequeteBareme,
  CompteAgence,
  DroitsAgences,
  DroitsMoi,
  EtatSoldes,
  LigneRegistre,
  ReglagesAgences,
  RequeteAgence,
  RequeteCompte,
  RequeteReseau,
  RequeteService,
  RequeteType,
  ServiceFinancier,
  VerificationSolde
} from '../models/agences';

/** Appels du module Agences multiservices et droits de l'utilisateur connecte. */
@Injectable({ providedIn: 'root' })
export class AgencesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agences`;
  /** Copies locales des lectures utiles a la saisie hors ligne. */
  private readonly horsLigne = inject(AgencesHorsLigneService);

  /** Droits et agences visibles, lus une fois puis gardes pour le menu et les ecrans. */
  readonly moi = signal<DroitsMoi | null>(null);

  chargerMoi(): Observable<DroitsMoi> {
    return this.horsLigne.avecCopie('moi', this.http.get<DroitsMoi>(`${this.base}/moi`)).pipe(tap((m) => this.moi.set(m)));
  }

  peut(permission: string): boolean {
    return this.moi()?.permissions.includes(permission) ?? false;
  }

  // ------------------------------------------------------------ agences
  agences(): Observable<Agence[]> {
    return this.http.get<Agence[]>(this.base);
  }

  personnel(): Observable<MembrePersonnel[]> {
    return this.http.get<MembrePersonnel[]>(`${this.base}/personnel`);
  }

  creerAgence(r: RequeteAgence): Observable<Agence> {
    return this.http.post<Agence>(this.base, r);
  }

  modifierAgence(id: number, r: RequeteAgence): Observable<Agence> {
    return this.http.put<Agence>(`${this.base}/${id}`, r);
  }

  // ---------------------------------------------------------- catalogue
  catalogue(): Observable<ServiceFinancier[]> {
    return this.horsLigne.avecCopie('catalogue', this.http.get<ServiceFinancier[]>(`${this.base}/catalogue`));
  }

  categories(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/catalogue/categories`);
  }

  enregistrerService(id: number | null, r: RequeteService): Observable<ServiceFinancier> {
    return id == null
      ? this.http.post<ServiceFinancier>(`${this.base}/catalogue/services`, r)
      : this.http.put<ServiceFinancier>(`${this.base}/catalogue/services/${id}`, r);
  }

  /** Fait d'un reseau un service a part (son propre float), comme Xpress ou Mixx. */
  detacherReseau(id: number): Observable<ServiceFinancier> {
    return this.http.post<ServiceFinancier>(`${this.base}/catalogue/reseaux/${id}/detacher`, {});
  }

  enregistrerReseau(id: number | null, r: RequeteReseau): Observable<ServiceFinancier> {
    return id == null
      ? this.http.post<ServiceFinancier>(`${this.base}/catalogue/reseaux`, r)
      : this.http.put<ServiceFinancier>(`${this.base}/catalogue/reseaux/${id}`, r);
  }

  enregistrerType(id: number | null, r: RequeteType): Observable<ServiceFinancier> {
    return id == null
      ? this.http.post<ServiceFinancier>(`${this.base}/catalogue/types`, r)
      : this.http.put<ServiceFinancier>(`${this.base}/catalogue/types/${id}`, r);
  }

  // ------------------------------------------------------------- soldes
  soldes(agenceId?: number | null): Observable<EtatSoldes> {
    let params = new HttpParams();
    if (agenceId) {
      params = params.set('agenceId', agenceId);
    }
    return this.http.get<EtatSoldes>(`${this.base}/soldes`, { params });
  }

  registre(compteId: number, page: number, du?: string, au?: string): Observable<Page<LigneRegistre>> {
    let params = new HttpParams().set('page', page).set('size', 30);
    if (du) {
      params = params.set('du', du);
    }
    if (au) {
      params = params.set('au', au);
    }
    return this.http.get<Page<LigneRegistre>>(`${this.base}/comptes/${compteId}/registre`, { params });
  }

  soldeAu(compteId: number, jour: string): Observable<{ compteId: number; jour: string; solde: number }> {
    return this.http.get<{ compteId: number; jour: string; solde: number }>(`${this.base}/comptes/${compteId}/solde-au`,
      { params: new HttpParams().set('jour', jour) });
  }

  ouvrir(agenceId: number, soldes: { compteId: number; montant: number }[], observation?: string): Observable<EtatSoldes> {
    return this.http.post<EtatSoldes>(`${this.base}/${agenceId}/ouverture`, { soldes, observation });
  }

  verifier(agenceId: number): Observable<VerificationSolde[]> {
    return this.http.post<VerificationSolde[]>(`${this.base}/${agenceId}/verification`, {});
  }

  creerCompte(agenceId: number, r: RequeteCompte): Observable<CompteAgence> {
    return this.http.post<CompteAgence>(`${this.base}/${agenceId}/comptes`, r);
  }

  modifierCompte(id: number, r: RequeteCompte): Observable<CompteAgence> {
    return this.http.put<CompteAgence>(`${this.base}/comptes/${id}`, r);
  }

  // ----------------------------------------------------------- reglages
  reglages(): Observable<ReglagesAgences> {
    return this.http.get<ReglagesAgences>(`${this.base}/reglages`);
  }

  enregistrerReglages(r: ReglagesAgences): Observable<ReglagesAgences> {
    return this.http.put<ReglagesAgences>(`${this.base}/reglages`, r);
  }

  droits(): Observable<DroitsAgences> {
    return this.http.get<DroitsAgences>(`${this.base}/droits`);
  }

  enregistrerDroits(profils: { role: string; permissions: string[] }[]): Observable<DroitsAgences> {
    return this.http.put<DroitsAgences>(`${this.base}/droits`, profils);
  }

  // ------------------------------------------------------------ baremes
  baremes(historique = false, serviceId?: number | null): Observable<Bareme[]> {
    let params = new HttpParams().set('historique', historique);
    if (serviceId) {
      params = params.set('serviceId', serviceId);
    }
    return this.http.get<Bareme[]>(`${this.base}/baremes`, { params });
  }

  historiqueBareme(id: number): Observable<Bareme[]> {
    return this.http.get<Bareme[]>(`${this.base}/baremes/${id}/historique`);
  }

  enregistrerBareme(id: number | null, r: RequeteBareme): Observable<Bareme> {
    return id == null
      ? this.http.post<Bareme>(`${this.base}/baremes`, r)
      : this.http.put<Bareme>(`${this.base}/baremes/${id}`, r);
  }

  terminerBareme(id: number, dateFin?: string): Observable<Bareme> {
    let params = new HttpParams();
    if (dateFin) {
      params = params.set('dateFin', dateFin);
    }
    return this.http.post<Bareme>(`${this.base}/baremes/${id}/fin`, {}, { params });
  }

  chargerExemplesBaremes(): Observable<Bareme[]> {
    return this.http.post<Bareme[]>(`${this.base}/baremes/exemples`, {});
  }

  simulerCommission(serviceId: number, montant: number, typeOperationId?: number | null, reseauId?: number | null,
                    jour?: string): Observable<CommissionCalculee> {
    let params = new HttpParams().set('serviceId', serviceId).set('montant', montant);
    if (typeOperationId) {
      params = params.set('typeOperationId', typeOperationId);
    }
    if (reseauId) {
      params = params.set('reseauId', reseauId);
    }
    if (jour) {
      params = params.set('jour', jour);
    }
    return this.http.get<CommissionCalculee>(`${this.base}/baremes/simulation`, { params });
  }

  controleBaremes(): Observable<ControleBareme[]> {
    return this.http.get<ControleBareme[]>(`${this.base}/baremes/controle`);
  }

  // ------------------------------------------------------- transactions
  /** Apercu sans enregistrement : pas de confirmation demandee. */
  apercuTransaction(s: SaisieTransaction): Observable<ApercuTransaction> {
    return this.http.post<ApercuTransaction>(`${this.base}/transactions/apercu`, s,
      { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  enregistrerTransaction(s: SaisieTransaction): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.base}/transactions`, s);
  }

  annulerTransaction(id: number, motif: string): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.base}/transactions/${id}/annulation`, { motif });
  }

  transactions(f: FiltresTransactions, page: number): Observable<Page<Transaction>> {
    return this.http.get<Page<Transaction>>(`${this.base}/transactions`, { params: this.filtres(f).set('page', page).set('size', 30) });
  }

  resumeTransactions(f: FiltresTransactions): Observable<ResumeTransactions> {
    return this.http.get<ResumeTransactions>(`${this.base}/transactions/resume`, { params: this.filtres(f) });
  }

  clientsAgence(q: string): Observable<ClientAgence[]> {
    return this.http.get<ClientAgence[]>(`${this.base}/transactions/clients`, { params: new HttpParams().set('q', q) });
  }

  private filtres(f: object): HttpParams {
    let params = new HttpParams();
    Object.entries(f).forEach(([cle, valeur]) => {
      if (valeur !== null && valeur !== undefined && valeur !== '') {
        params = params.set(cle, String(valeur));
      }
    });
    return params;
  }

  // ------------------------------------------------------------- caisses
  etatCaisses(agenceId: number): Observable<EtatCaisses> {
    return this.http.get<EtatCaisses>(`${this.base}/caisses`, { params: new HttpParams().set('agenceId', agenceId) });
  }

  /** Caisse ouverte de l'utilisateur dans l'agence ; null s'il n'en a pas (reponse 204). */
  maSession(agenceId: number): Observable<SessionCaisseAgence | null> {
    return this.horsLigne.avecCopie('ma-session.' + agenceId, this.http.get<SessionCaisseAgence>(`${this.base}/caisses/ma-session`,
      { params: new HttpParams().set('agenceId', agenceId), observe: 'response' }).pipe(map((r) => r.body ?? null)));
  }

  sessionsCaisse(agenceId: number | null, page: number): Observable<Page<SessionCaisseAgence>> {
    let params = new HttpParams().set('page', page).set('size', 30);
    if (agenceId) {
      params = params.set('agenceId', agenceId);
    }
    return this.http.get<Page<SessionCaisseAgence>>(`${this.base}/caisses/sessions`, { params });
  }

  ouvrirCaisse(agenceId: number, caissierId: number, billettage: LigneBillettageAgence[]): Observable<SessionCaisseAgence> {
    return this.http.post<SessionCaisseAgence>(`${this.base}/caisses/sessions`, { agenceId, caissierId, billettage });
  }

  /** Le caissier compte son tiroir et soumet la fermeture de sa caisse. */
  soumettreCaisse(id: number, billettage: LigneBillettageAgence[], justification?: string): Observable<SessionCaisseAgence> {
    return this.http.post<SessionCaisseAgence>(`${this.base}/caisses/sessions/${id}/soumission`, { billettage, justification });
  }

  /** Le superviseur refuse le comptage soumis : la caisse est rouverte au caissier. */
  rejeterCaisse(id: number, motif: string): Observable<SessionCaisseAgence> {
    return this.http.post<SessionCaisseAgence>(`${this.base}/caisses/sessions/${id}/rejet`, { motif });
  }

  /** Ferme la caisse (directement) ou valide le comptage soumis par le caissier. */
  fermerCaisse(id: number, billettage: LigneBillettageAgence[], justification?: string): Observable<SessionCaisseAgence> {
    return this.http.post<SessionCaisseAgence>(`${this.base}/caisses/sessions/${id}/fermeture`, { billettage, justification });
  }

  // ---------------------------------------------------------- mouvements
  mouvements(f: { agenceId?: number | null; statut?: StatutMouvement | ''; type?: string; du?: string; au?: string },
             page: number): Observable<Page<MouvementAgence>> {
    return this.http.get<Page<MouvementAgence>>(`${this.base}/mouvements`, { params: this.filtres(f).set('page', page).set('size', 30) });
  }

  creerMouvement(r: RequeteMouvement): Observable<MouvementAgence> {
    return this.http.post<MouvementAgence>(`${this.base}/mouvements`, r);
  }

  validerMouvement(id: number): Observable<MouvementAgence> {
    return this.http.post<MouvementAgence>(`${this.base}/mouvements/${id}/validation`, {});
  }

  rejeterMouvement(id: number, motif: string): Observable<MouvementAgence> {
    return this.http.post<MouvementAgence>(`${this.base}/mouvements/${id}/rejet`, { motif });
  }

  annulerMouvement(id: number, motif: string): Observable<MouvementAgence> {
    return this.http.post<MouvementAgence>(`${this.base}/mouvements/${id}/annulation`, { motif });
  }

  /** La piece suit la saisie deja confirmee : pas de seconde confirmation. */
  joindrePieceMouvement(id: number, fichier: File): Observable<MouvementAgence> {
    const form = new FormData();
    form.append('fichier', fichier);
    return this.http.post<MouvementAgence>(`${this.base}/mouvements/${id}/piece`, form,
      { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  pieceMouvement(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/mouvements/${id}/piece`, { responseType: 'blob' });
  }

  // ------------------------------------------------------------- clotures
  preparationCloture(agenceId: number, journee?: string): Observable<PreparationCloture> {
    let params = new HttpParams().set('agenceId', agenceId);
    if (journee) {
      params = params.set('journee', journee);
    }
    return this.http.get<PreparationCloture>(`${this.base}/clotures/preparation`, { params });
  }

  clotures(agenceId: number | null, page: number): Observable<Page<Cloture>> {
    return this.http.get<Page<Cloture>>(`${this.base}/clotures`, { params: this.filtres({ agenceId }).set('page', page).set('size', 30) });
  }

  cloturesAFaire(agenceId?: number | null): Observable<ClotureAFaire[]> {
    return this.http.get<ClotureAFaire[]>(`${this.base}/clotures/a-faire`, { params: this.filtres({ agenceId }) });
  }

  soumettreCloture(agenceId: number, journee: string, lignes: { compteId: number; soldeReel: number | null; justification?: string }[],
                   observations?: string): Observable<Cloture> {
    return this.http.post<Cloture>(`${this.base}/clotures`, { agenceId, journee, lignes, observations });
  }

  validerCloture(id: number): Observable<Cloture> {
    return this.http.post<Cloture>(`${this.base}/clotures/${id}/validation`, {});
  }

  rejeterCloture(id: number, motif: string): Observable<Cloture> {
    return this.http.post<Cloture>(`${this.base}/clotures/${id}/rejet`, { motif });
  }

  rouvrirCloture(id: number, motif: string): Observable<Cloture> {
    return this.http.post<Cloture>(`${this.base}/clotures/${id}/reouverture`, { motif });
  }

  // ------------------------------------------------- tableau de bord, audit
  tableauBord(f: { agenceId?: number | null; du?: string; au?: string; serviceId?: number | null; reseauId?: number | null;
                   caissierId?: number | null }): Observable<TableauBordAgences> {
    return this.http.get<TableauBordAgences>(`${this.base}/tableau-bord`, { params: this.filtres(f) });
  }

  audit(f: { agenceId?: number | null; du?: string; au?: string; objet?: string; q?: string }, page: number): Observable<Page<LigneAudit>> {
    return this.http.get<Page<LigneAudit>>(`${this.base}/audit`, { params: this.filtres(f).set('page', page).set('size', 40) });
  }

  // ------------------------------------------------------------ depenses
  categoriesDepense(): Observable<CategorieDepense[]> {
    return this.horsLigne.avecCopie('categories-depense', this.http.get<CategorieDepense[]>(`${this.base}/depenses/categories`));
  }

  enregistrerCategorieDepense(id: number | null, r: { nom: string; actif?: boolean }): Observable<CategorieDepense> {
    return id == null ? this.http.post<CategorieDepense>(`${this.base}/depenses/categories`, r)
      : this.http.put<CategorieDepense>(`${this.base}/depenses/categories/${id}`, r);
  }

  depenses(f: { agenceId?: number | null; du?: string; au?: string; categorieId?: number | null; statut?: string; q?: string },
           page: number): Observable<Page<DepenseAgence>> {
    return this.http.get<Page<DepenseAgence>>(`${this.base}/depenses`, { params: this.filtres(f).set('page', page).set('size', 30) });
  }

  resumeDepenses(f: { agenceId?: number | null; du?: string; au?: string; categorieId?: number | null; statut?: string; q?: string }):
    Observable<ResumeDepenses> {
    return this.http.get<ResumeDepenses>(`${this.base}/depenses/resume`, { params: this.filtres(f) });
  }

  syntheseDepenses(agenceId: number | null, mois?: string): Observable<SyntheseDepenses> {
    return this.http.get<SyntheseDepenses>(`${this.base}/depenses/synthese`, { params: this.filtres({ agenceId, mois }) });
  }

  creerDepense(r: { agenceId: number; categorieId: number; libelle: string; montant: number; compteId?: number | null;
                    beneficiaire?: string; reference?: string; observations?: string; recurrenteId?: number | null;
                    periode?: string }): Observable<DepenseAgence> {
    return this.http.post<DepenseAgence>(`${this.base}/depenses`, r);
  }

  depensesAValider(agenceId: number | null): Observable<DepenseAgence[]> {
    return this.http.get<DepenseAgence[]>(`${this.base}/depenses/a-valider`, { params: this.filtres({ agenceId }) });
  }

  validerDepense(id: number): Observable<DepenseAgence> {
    return this.http.post<DepenseAgence>(`${this.base}/depenses/${id}/validation`, {});
  }

  refuserDepense(id: number, motif: string): Observable<DepenseAgence> {
    return this.http.post<DepenseAgence>(`${this.base}/depenses/${id}/refus`, { motif });
  }

  annulerDepense(id: number, motif: string): Observable<DepenseAgence> {
    return this.http.post<DepenseAgence>(`${this.base}/depenses/${id}/annulation`, { motif });
  }

  joindreJustificatifDepense(id: number, fichier: File): Observable<DepenseAgence> {
    const form = new FormData();
    form.append('fichier', fichier);
    return this.http.post<DepenseAgence>(`${this.base}/depenses/${id}/justificatif`, form,
      { context: new HttpContext().set(SANS_SUIVI, true) });
  }

  justificatifDepense(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/depenses/${id}/justificatif`, { responseType: 'blob' });
  }

  chargesRecurrentes(agenceId: number, mois?: string): Observable<ChargeRecurrente[]> {
    return this.http.get<ChargeRecurrente[]>(`${this.base}/depenses/recurrentes`, { params: this.filtres({ agenceId, mois }) });
  }

  enregistrerChargeRecurrente(id: number | null, r: { agenceId: number; categorieId: number; libelle: string; montant: number;
                                                        jourEcheance: number; actif?: boolean }): Observable<ChargeRecurrente> {
    return id == null ? this.http.post<ChargeRecurrente>(`${this.base}/depenses/recurrentes`, r)
      : this.http.put<ChargeRecurrente>(`${this.base}/depenses/recurrentes/${id}`, r);
  }

  genererCharges(agenceId: number, mois: string, compteId: number | null):
    Observable<{ mois: string; creees: DepenseAgence[]; dejaReglees: string[] }> {
    return this.http.post<{ mois: string; creees: DepenseAgence[]; dejaReglees: string[] }>(`${this.base}/depenses/recurrentes/generation`,
      { agenceId, mois, compteId });
  }

  // ------------------------------------------------------------- credits
  credits(agenceId?: number | null, statut?: string): Observable<CreditAgence[]> {
    return this.http.get<CreditAgence[]>(`${this.base}/credits`, { params: this.filtres({ agenceId, statut }) });
  }

  creerCredit(r: { agenceId: number; type: TypeCredit; libelle: string; tiersNom: string; tiersTelephone?: string; montant: number;
                   dateDebut?: string; dateEcheance?: string; tauxInteret?: number | null; compteId?: number | null;
                   remboursable?: boolean; observations?: string; echeances?: { datePrevue: string; montant: number }[] }): Observable<CreditAgence> {
    return this.http.post<CreditAgence>(`${this.base}/credits`, r);
  }

  rembourserCredit(id: number, r: { capital: number; interets?: number; compteId: number; observations?: string }): Observable<CreditAgence> {
    return this.http.post<CreditAgence>(`${this.base}/credits/${id}/remboursements`, r);
  }

  annulerRemboursement(id: number, motif: string): Observable<CreditAgence> {
    return this.http.post<CreditAgence>(`${this.base}/credits/remboursements/${id}/annulation`, { motif });
  }

  // --------------------------------------------------------------- cartes
  produitsCartes(): Observable<ProduitCarte[]> {
    return this.horsLigne.avecCopie('produits-cartes', this.http.get<ProduitCarte[]>(`${this.base}/cartes/produits`));
  }

  enregistrerProduitCarte(id: number | null, r: { nom: string; prixVente: number; seuilAlerte: number; actif?: boolean }): Observable<ProduitCarte> {
    return id == null ? this.http.post<ProduitCarte>(`${this.base}/cartes/produits`, r)
      : this.http.put<ProduitCarte>(`${this.base}/cartes/produits/${id}`, r);
  }

  stockCartes(agenceId: number): Observable<StockCarte[]> {
    return this.horsLigne.avecCopie('stock-cartes.' + agenceId,
      this.http.get<StockCarte[]>(`${this.base}/cartes/stock`, { params: new HttpParams().set('agenceId', agenceId) }));
  }

  entrerLotCartes(r: { agenceId: number; produitId: number; quantite: number; coutUnitaire: number; compteId?: number | null;
                       fournisseur?: string; reference?: string }): Observable<StockCarte[]> {
    return this.http.post<StockCarte[]>(`${this.base}/cartes/lots`, r);
  }

  vendreCartes(r: { agenceId: number; produitId: number; numeros: string[]; clientNom?: string; clientPrenom?: string;
                    clientTelephone: string }): Observable<VenteCarte> {
    return this.http.post<VenteCarte>(`${this.base}/cartes/ventes`, r);
  }

  annulerVenteCartes(id: number, motif: string): Observable<VenteCarte> {
    return this.http.post<VenteCarte>(`${this.base}/cartes/ventes/${id}/annulation`, { motif });
  }

  ventesCartes(agenceId: number | null, page: number): Observable<Page<VenteCarte>> {
    return this.http.get<Page<VenteCarte>>(`${this.base}/cartes/ventes`, { params: this.filtres({ agenceId }).set('page', page).set('size', 30) });
  }

  suiviCartes(agenceId: number | null, du?: string, au?: string): Observable<SuiviCartes> {
    return this.http.get<SuiviCartes>(`${this.base}/cartes/suivi`, { params: this.filtres({ agenceId, du, au }) });
  }

  // ------------------------------------------------- rapports et alertes
  rapportsCatalogue(): Observable<RapportAgenceCatalogue[]> {
    return this.http.get<RapportAgenceCatalogue[]>(`${this.base}/rapports`);
  }

  rapport(type: string, f: { agenceId?: number | null; du?: string; au?: string }): Observable<Rapport> {
    return this.http.get<Rapport>(`${this.base}/rapports/${type}`, { params: this.filtres(f) });
  }

  exporterRapport(type: string, format: 'xlsx' | 'pdf' | 'csv', f: { agenceId?: number | null; du?: string; au?: string }): Observable<FichierRecu> {
    return this.http.get(`${this.base}/rapports/${type}/export`, {
      params: this.filtres(f).set('format', format), responseType: 'blob', observe: 'response'
    }).pipe(map((r) => {
      const entete = r.headers.get('Content-Disposition') ?? '';
      const nom = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(entete)?.[1] ?? `rapport.${format}`;
      return { nom: decodeURIComponent(nom), contenu: r.body ?? new Blob() };
    }));
  }

  alertes(agenceId?: number | null): Observable<AlerteAgence[]> {
    return this.http.get<AlerteAgence[]>(`${this.base}/alertes`, { params: this.filtres({ agenceId }) });
  }

  // -------------------------------------------- rapprochement des commissions
  attentesCommissions(agenceId?: number | null): Observable<AttenteOperateur[]> {
    return this.http.get<AttenteOperateur[]>(`${this.base}/rapprochement/attentes`, { params: this.filtres({ agenceId }) });
  }

  commissionsEnAttente(agenceId: number, serviceId: number, page: number, du?: string, au?: string): Observable<Page<CommissionAttente>> {
    return this.http.get<Page<CommissionAttente>>(`${this.base}/rapprochement/commissions`,
      { params: this.filtres({ agenceId, serviceId, du, au }).set('page', page).set('size', 50) });
  }

  versementsCommissions(agenceId: number | null, serviceId: number | null, page: number): Observable<Page<VersementCommission>> {
    return this.http.get<Page<VersementCommission>>(`${this.base}/rapprochement/versements`,
      { params: this.filtres({ agenceId, serviceId }).set('page', page).set('size', 30) });
  }

  enregistrerVersement(r: { agenceId: number; serviceId: number; transactionIds?: number[]; du?: string; au?: string; montantVerse: number;
                            compteId: number; referenceOperateur?: string; observations?: string }): Observable<VersementCommission> {
    return this.http.post<VersementCommission>(`${this.base}/rapprochement/versements`, r);
  }

  annulerVersement(id: number, motif: string): Observable<VersementCommission> {
    return this.http.post<VersementCommission>(`${this.base}/rapprochement/versements/${id}/annulation`, { motif });
  }
}
