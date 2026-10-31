import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SANS_SUIVI } from '../operation/operation.interceptor';
import { PostePreparation } from '../models/pdv';
import {
  Bon,
  CarteEnLigne,
  CommandeDetail,
  EcranPreparation,
  EcranRequete,
  ListeEcrans,
  EncaissementCommande,
  LigneBon,
  PlanSalles,
  ReglagesRestauration,
  ResultatEncaissement,
  Salle,
  SuiviClient,
  TableRequete,
  TableRestaurant
} from '../models/restauration';

/**
 * Acces a l'API du mode restaurant.
 * <p>
 * Le service en salle enchaine les gestes (ajouter un plat, envoyer, servir) :
 * une confirmation a chacun ralentirait le serveur. Ces appels passent donc
 * sans la confirmation generique ; les actions qui engagent (annulations,
 * reglages du plan) la conservent.
 */
@Injectable({ providedIn: 'root' })
export class RestaurationService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/pdv/restauration`;
  private readonly direct = { context: new HttpContext().set(SANS_SUIVI, true) };

  // ------------------------------------------------------------ plan

  plan(): Observable<PlanSalles> {
    return this.http.get<PlanSalles>(`${this.base}/plan`);
  }

  salles(): Observable<Salle[]> {
    return this.http.get<Salle[]>(`${this.base}/salles`);
  }

  creerSalle(nom: string, ordre?: number): Observable<Salle> {
    return this.http.post<Salle>(`${this.base}/salles`, { nom, ordre });
  }

  modifierSalle(id: number, salle: { nom: string; ordre?: number; actif?: boolean }): Observable<Salle> {
    return this.http.put<Salle>(`${this.base}/salles/${id}`, salle);
  }

  tables(): Observable<TableRestaurant[]> {
    return this.http.get<TableRestaurant[]>(`${this.base}/tables`);
  }

  creerTable(table: TableRequete): Observable<TableRestaurant> {
    return this.http.post<TableRestaurant>(`${this.base}/tables`, table);
  }

  modifierTable(id: number, table: TableRequete): Observable<TableRestaurant> {
    return this.http.put<TableRestaurant>(`${this.base}/tables/${id}`, table);
  }

  placerTables(positions: { id: number; posX: number; posY: number }[]): Observable<TableRestaurant[]> {
    return this.http.put<TableRestaurant[]>(`${this.base}/tables/positions`, positions);
  }

  /** Code QR de la table en SVG ; il pointe vers la carte en ligne de cette application. */
  codeQr(id: number): Observable<string> {
    return this.http.get(`${this.base}/tables/${id}/qr`, {
      params: new HttpParams().set('origine', window.location.origin),
      responseType: 'text'
    });
  }

  renouvelerJeton(id: number): Observable<TableRestaurant> {
    return this.http.post<TableRestaurant>(`${this.base}/tables/${id}/jeton`, {});
  }

  reglages(): Observable<ReglagesRestauration> {
    return this.http.get<ReglagesRestauration>(`${this.base}/reglages`);
  }

  modifierReglages(reglages: ReglagesRestauration): Observable<ReglagesRestauration> {
    return this.http.put<ReglagesRestauration>(`${this.base}/reglages`, reglages);
  }

  retirerTable(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/tables/${id}`);
  }

  // -------------------------------------------------------- commandes

  ouvrir(ouverture: { tableId?: number; libelle?: string; couverts?: number | null; sessionId?: number | null }): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes`, ouverture, this.direct);
  }

  commande(id: number): Observable<CommandeDetail> {
    return this.http.get<CommandeDetail>(`${this.base}/commandes/${id}`);
  }

  modifier(id: number, infos: { couverts?: number | null; note?: string }): Observable<CommandeDetail> {
    return this.http.put<CommandeDetail>(`${this.base}/commandes/${id}`, infos, this.direct);
  }

  ajouter(id: number, articleId: number, quantite = 1, note?: string): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/lignes`, { articleId, quantite, note }, this.direct);
  }

  modifierLigne(id: number, ligneId: number, quantite: number, note?: string): Observable<CommandeDetail> {
    return this.http.put<CommandeDetail>(`${this.base}/commandes/${id}/lignes/${ligneId}`, { quantite, note }, this.direct);
  }

  retirerLigne(id: number, ligneId: number): Observable<CommandeDetail> {
    return this.http.delete<CommandeDetail>(`${this.base}/commandes/${id}/lignes/${ligneId}`, this.direct);
  }

  annulerLigne(id: number, ligneId: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/lignes/${ligneId}/annulation`, {});
  }

  envoyer(id: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/envoi`, {}, this.direct);
  }

  servir(id: number, lignes?: number[]): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/service`, lignes ?? [], this.direct);
  }

  transferer(id: number, tableId: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/transfert`, { tableId }, this.direct);
  }

  addition(id: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/addition`, {}, this.direct);
  }

  /** La fenetre de paiement tient lieu de confirmation, comme a la caisse. */
  encaisser(id: number, encaissement: EncaissementCommande): Observable<ResultatEncaissement> {
    return this.http.post<ResultatEncaissement>(`${this.base}/commandes/${id}/encaissement`, encaissement, this.direct);
  }

  validerDemandes(id: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/demandes/validation`, {}, this.direct);
  }

  refuserDemandes(id: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/demandes/refus`, {});
  }

  priseEnCompte(id: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/appel/prise-en-compte`, {}, this.direct);
  }

  // ------------------------------------------------ carte en ligne (client)

  carteEnLigne(jeton: string): Observable<CarteEnLigne> {
    return this.http.get<CarteEnLigne>(`${environment.apiUrl}/public/qr/${jeton}`);
  }

  suiviClient(jeton: string): Observable<SuiviClient | null> {
    return this.http.get<SuiviClient | null>(`${environment.apiUrl}/public/qr/${jeton}/suivi`);
  }

  commanderEnLigne(jeton: string, lignes: { articleId: number; quantite: number; note?: string }[]): Observable<SuiviClient> {
    return this.http.post<SuiviClient>(`${environment.apiUrl}/public/qr/${jeton}/commande`, { lignes });
  }

  appelerServeur(jeton: string, motif: 'SERVEUR' | 'ADDITION'): Observable<SuiviClient> {
    return this.http.post<SuiviClient>(`${environment.apiUrl}/public/qr/${jeton}/appel`, { motif });
  }

  annuler(id: number): Observable<CommandeDetail> {
    return this.http.post<CommandeDetail>(`${this.base}/commandes/${id}/annulation`, {});
  }

  // ------------------------------------------------------ preparation

  /** Bons d'un ecran de preparation, ou a defaut d'un poste quand aucun ecran n'est cree. */
  bons(source: { ecranId?: number; poste?: PostePreparation }): Observable<Bon[]> {
    const params = source.ecranId
      ? new HttpParams().set('ecran', source.ecranId)
      : new HttpParams().set('poste', source.poste ?? 'CUISINE');
    return this.http.get<Bon[]>(`${this.base}/preparation`, { params });
  }

  avancerBon(commandeId: number, envoi: number, source: { ecranId?: number; poste?: PostePreparation }): Observable<Bon> {
    return this.http.post<Bon>(`${this.base}/preparation/bons/avancement`,
      { commandeId, envoi, ecranId: source.ecranId ?? null, poste: source.ecranId ? null : source.poste }, this.direct);
  }

  ecrans(): Observable<ListeEcrans> {
    return this.http.get<ListeEcrans>(`${this.base}/ecrans`);
  }

  ecransAccessibles(): Observable<EcranPreparation[]> {
    return this.http.get<EcranPreparation[]>(`${this.base}/ecrans/accessibles`);
  }

  creerEcran(requete: EcranRequete): Observable<EcranPreparation> {
    return this.http.post<EcranPreparation>(`${this.base}/ecrans`, requete);
  }

  modifierEcran(id: number, requete: EcranRequete): Observable<EcranPreparation> {
    return this.http.put<EcranPreparation>(`${this.base}/ecrans/${id}`, requete);
  }

  avancerLigne(id: number): Observable<LigneBon> {
    return this.http.post<LigneBon>(`${this.base}/preparation/lignes/${id}/avancement`, {}, this.direct);
  }

  reculerLigne(id: number): Observable<LigneBon> {
    return this.http.post<LigneBon>(`${this.base}/preparation/lignes/${id}/retour`, {}, this.direct);
  }
}
