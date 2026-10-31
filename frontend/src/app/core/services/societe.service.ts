import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { IdentiteSociete, Societe, SocieteRequete } from '../models/parametrage';

@Injectable({ providedIn: 'root' })
export class SocieteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/parametrage/societe`;

  /**
   * Identite de l'entreprise connectee, affichee en tete de l'application.
   * Elle est mise a jour des qu'un administrateur enregistre le parametrage,
   * pour que l'ecran reflete le changement sans rechargement.
   */
  readonly identite = signal<IdentiteSociete | null>(null);

  /** Identite de l'entreprise connectee, pour l'entete de l'application. */
  chargerIdentite(): Observable<Societe> {
    return this.lire().pipe(tap((societe) => this.rafraichir(societe)));
  }

  lire(): Observable<Societe> {
    return this.http.get<Societe>(this.base);
  }

  enregistrer(requete: SocieteRequete): Observable<Societe> {
    return this.http.put<Societe>(this.base, requete).pipe(tap((s) => this.rafraichir(s)));
  }

  televerserLogo(fichier: File): Observable<Societe> {
    const corps = new FormData();
    corps.append('fichier', fichier);
    return this.http.post<Societe>(`${this.base}/logo`, corps).pipe(tap((s) => this.rafraichir(s)));
  }

  supprimerLogo(): Observable<Societe> {
    return this.http.delete<Societe>(`${this.base}/logo`).pipe(tap((s) => this.rafraichir(s)));
  }

  private rafraichir(societe: Societe): void {
    this.appliquer({ nom: societe.nom, sigle: societe.sigle, logo: societe.logo });
  }

  /** Point unique de mise a jour : le titre et la favicon suivent l'identite. */
  private appliquer(identite: IdentiteSociete): void {
    this.identite.set(identite);
    document.title = identite.nom || environment.nomPlateforme;
    this.definirFavicon(identite.logo);
  }

  /**
   * Le logo parametre sert de favicon. Il arrive en « data:image/...;base64, »,
   * directement utilisable comme href ; sans logo on revient a l'icone livree.
   */
  private definirFavicon(logo?: string | null): void {
    let lien = document.querySelector<HTMLLinkElement>('link[rel~="icon"]');
    if (!lien) {
      lien = document.createElement('link');
      lien.rel = 'icon';
      document.head.appendChild(lien);
    }
    if (logo) {
      const type = /^data:([^;,]+)/.exec(logo)?.[1];
      lien.type = type ?? 'image/png';
      lien.href = logo;
    } else {
      lien.type = 'image/x-icon';
      lien.href = 'favicon.ico';
    }
  }
}
