import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AlerteGps,
  MarqueurCarte,
  PositionGps,
  SyntheseAlertes,
  Trajet,
  ZoneGeofence,
  ZoneRequete
} from '../models/gps';
import { Page } from '../models/models';

/** Vehicule equipe d'un traceur, avec l'etat de son jeton propre. */
export interface TraceurGps {
  camionId: number;
  immatriculation: string;
  deviceId: string;
  actif: boolean;
  creeLe?: string | null;
  dernierUsage?: string | null;
}

@Injectable({ providedIn: 'root' })
export class GpsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/gps`;

  /** Marqueurs de la carte du parc (chapitre 23). */
  /** Jeton des traceurs de l'entreprise : etat (sans le jeton) et generation (montre une fois). */
  etatJeton(): Observable<{ actif: boolean; creeLe?: string | null; dernierUsage?: string | null }> {
    return this.http.get<{ actif: boolean; creeLe?: string | null; dernierUsage?: string | null }>(`${this.base}/jeton`);
  }

  genererJeton(): Observable<{ jeton: string; adresse: string }> {
    return this.http.post<{ jeton: string; adresse: string }>(`${this.base}/jeton`, {});
  }

  /** Vehicules equipes d'un traceur et etat de leur jeton propre. */
  traceurs(): Observable<TraceurGps[]> {
    return this.http.get<TraceurGps[]>(`${this.base}/traceurs`);
  }

  genererJetonTraceur(camionId: number): Observable<{ jeton: string; adresse: string }> {
    return this.http.post<{ jeton: string; adresse: string }>(`${this.base}/traceurs/${camionId}/jeton`, {});
  }

  revoquerJetonTraceur(camionId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/traceurs/${camionId}/jeton`);
  }

  carte(): Observable<MarqueurCarte[]> {
    return this.http.get<MarqueurCarte[]>(`${this.base}/carte`);
  }

  /** Trajet d'un camion sur une periode (chapitre 24). */
  trajet(camionId: number, debut?: string, fin?: string): Observable<Trajet> {
    let params = new HttpParams();
    if (debut) {
      params = params.set('debut', debut);
    }
    if (fin) {
      params = params.set('fin', fin);
    }
    return this.http.get<Trajet>(`${this.base}/camions/${camionId}/trajet`, { params });
  }

  dernieresPositions(camionId: number, nombre = 20): Observable<PositionGps[]> {
    return this.http.get<PositionGps[]>(`${this.base}/camions/${camionId}/dernieres`, {
      params: new HttpParams().set('nombre', nombre)
    });
  }

  fournisseurs(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/fournisseurs`);
  }

  // --- Zones (chapitre 25) ------------------------------------------------

  zones(actifSeulement = true): Observable<ZoneGeofence[]> {
    return this.http.get<ZoneGeofence[]>(`${this.base}/zones`, {
      params: new HttpParams().set('actifSeulement', actifSeulement)
    });
  }

  creerZone(requete: ZoneRequete): Observable<ZoneGeofence> {
    return this.http.post<ZoneGeofence>(`${this.base}/zones`, requete);
  }

  modifierZone(id: number, requete: ZoneRequete): Observable<ZoneGeofence> {
    return this.http.put<ZoneGeofence>(`${this.base}/zones/${id}`, requete);
  }

  desactiverZone(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/zones/${id}`);
  }

  // --- Alertes (chapitre 26) ----------------------------------------------

  alertes(traitee?: boolean, page = 0, size = 20): Observable<Page<AlerteGps>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (traitee !== undefined) {
      params = params.set('traitee', traitee);
    }
    return this.http.get<Page<AlerteGps>>(`${this.base}/alertes`, { params });
  }

  alertesRecentes(nombre = 10): Observable<AlerteGps[]> {
    return this.http.get<AlerteGps[]>(`${this.base}/alertes/recentes`, {
      params: new HttpParams().set('nombre', nombre)
    });
  }

  syntheseAlertes(): Observable<SyntheseAlertes> {
    return this.http.get<SyntheseAlertes>(`${this.base}/alertes/synthese`);
  }

  traiterAlerte(id: number): Observable<AlerteGps> {
    return this.http.patch<AlerteGps>(`${this.base}/alertes/${id}/traitement`, {});
  }
}
