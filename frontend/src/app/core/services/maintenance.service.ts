import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Entretien,
  EntretienRequete,
  Incident,
  IncidentRequete,
  Pneu,
  PneuRequete,
  RealisationEntretien,
  StatutIncident
} from '../models/maintenance';
import { Page } from '../models/models';

@Injectable({ providedIn: 'root' })
export class MaintenanceService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  // --- Entretiens (chapitre 19) -------------------------------------------

  entretiensOuverts(): Observable<Entretien[]> {
    return this.http.get<Entretien[]>(`${this.api}/entretiens`);
  }

  alertesEntretien(): Observable<Entretien[]> {
    return this.http.get<Entretien[]>(`${this.api}/entretiens/alertes`);
  }

  historiqueCamion(camionId: number): Observable<Entretien[]> {
    return this.http.get<Entretien[]>(`${this.api}/entretiens/camions/${camionId}`);
  }

  planifier(requete: EntretienRequete): Observable<Entretien> {
    return this.http.post<Entretien>(`${this.api}/entretiens`, requete);
  }

  demarrerEntretien(id: number): Observable<Entretien> {
    return this.http.patch<Entretien>(`${this.api}/entretiens/${id}/demarrage`, {});
  }

  realiserEntretien(id: number, realisation: RealisationEntretien): Observable<Entretien> {
    return this.http.patch<Entretien>(`${this.api}/entretiens/${id}/realisation`, realisation);
  }

  annulerEntretien(id: number, motif?: string): Observable<Entretien> {
    const params = motif ? new HttpParams().set('motif', motif) : undefined;
    return this.http.patch<Entretien>(`${this.api}/entretiens/${id}/annulation`, {}, { params });
  }

  // --- Pneumatiques (chapitre 20) -----------------------------------------

  pneus(): Observable<Pneu[]> {
    return this.http.get<Pneu[]>(`${this.api}/pneus`);
  }

  pneusARemplacer(): Observable<Pneu[]> {
    return this.http.get<Pneu[]>(`${this.api}/pneus/alertes`);
  }

  pneusDuCamion(camionId: number): Observable<Pneu[]> {
    return this.http.get<Pneu[]>(`${this.api}/pneus/camions/${camionId}`);
  }

  creerPneu(requete: PneuRequete): Observable<Pneu> {
    return this.http.post<Pneu>(`${this.api}/pneus`, requete);
  }

  monterPneu(id: number, camionId: number, position: string, dateMontage: string,
             kilometrageMontage?: number | null): Observable<Pneu> {
    return this.http.patch<Pneu>(`${this.api}/pneus/${id}/montage`, {
      camionId,
      position,
      dateMontage,
      kilometrageMontage
    });
  }

  deposerPneu(id: number, dateDepose: string, etat: string, kilometrageDepose?: number | null,
              motif?: string, rebuter?: boolean): Observable<Pneu> {
    return this.http.patch<Pneu>(`${this.api}/pneus/${id}/depose`, {
      dateDepose,
      kilometrageDepose,
      etat,
      motif,
      rebuter
    });
  }

  // --- Incidents (chapitre 33) --------------------------------------------

  incidents(page = 0, size = 20): Observable<Page<Incident>> {
    return this.http.get<Page<Incident>>(`${this.api}/incidents`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  incidentsOuverts(): Observable<Incident[]> {
    return this.http.get<Incident[]>(`${this.api}/incidents/ouverts`);
  }

  declarerIncident(requete: IncidentRequete): Observable<Incident> {
    return this.http.post<Incident>(`${this.api}/incidents`, requete);
  }

  traiterIncident(id: number, statut: StatutIncident, resolution?: string,
                  ouvrirEntretien?: boolean, coutEstime?: number | null): Observable<Incident> {
    return this.http.patch<Incident>(`${this.api}/incidents/${id}/traitement`, {
      statut,
      resolution,
      ouvrirEntretien,
      coutEstime
    });
  }
}
