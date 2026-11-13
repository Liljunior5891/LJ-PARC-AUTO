import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { EtatPlateformeService, MODULES_HORS_LIGNE, MaintenancePlateforme } from '../core/services/etat-plateforme.service';
import { Modale } from './modale';

const MASQUEES = 'lj.maintenances.masquees';

/**
 * Bandeaux de la plateforme en haut des pages : maintenance annoncee ou en cours
 * sur un module de l'entreprise, nouvelle version a recharger, et la fenetre des
 * nouveautes, montree une fois apres chaque version publiee.
 */
@Component({
  selector: 'app-bandeau-maintenance',
  imports: [DatePipe, Modale],
  template: `
    @for (m of visibles(); track m.id) {
      <div class="bandeau" [class.grave]="m.statut === 'EN_COURS'" role="status">
        <span class="icone" aria-hidden="true">🛠</span>
        <span class="texte">
          @if (m.statut === 'EN_COURS') {
            <strong>Maintenance en cours</strong> ({{ libelles(m) }}) jusqu'a
            <strong>{{ m.finPrevue | date: (memeJour(m) ? 'HH:mm' : 'dd/MM HH:mm') }}</strong>.
            @if (horsLigne(m)) { Vos operations d'agence sont gardees sur cet appareil et partiront a la fin. }
          } @else {
            <strong>Maintenance prevue</strong> ({{ libelles(m) }}) le <strong>{{ m.debut | date: 'dd/MM/yyyy' }}</strong>
            de <strong>{{ m.debut | date: 'HH:mm' }}</strong> a <strong>{{ m.finPrevue | date: (memeJour(m) ? 'HH:mm' : 'dd/MM HH:mm') }}</strong>.
          }
          <span class="message-maintenance">{{ m.message }}</span>
        </span>
        @if (m.statut !== 'EN_COURS') {
          <button type="button" class="fermer" (click)="masquer(m)" aria-label="Masquer cette annonce">×</button>
        }
      </div>
    }

    @if (etat.rechargementPropose()) {
      <div class="bandeau info" role="status">
        <span class="icone" aria-hidden="true">✨</span>
        <span class="texte">Une nouvelle version (<strong>{{ etat.version()?.numero }}</strong>) est disponible.
          Rechargez la page pour en profiter.</span>
        <button type="button" class="bouton petit" (click)="recharger()">Recharger</button>
      </div>
    }

    @if (nouveautesOuvertes() && etat.version(); as v) {
      <app-modale [titre]="'Nouveautes — version ' + v.numero" [sousTitre]="'Publiee le ' + (v.publieeLe | date: 'dd/MM/yyyy')"
                  largeur="640px" (ferme)="fermerNouveautes()">
        <h3 class="titre-version">{{ v.titre }}</h3>
        <div class="notes">{{ v.notes }}</div>
        <div class="actions-formulaire">
          <button class="bouton" type="button" (click)="fermerNouveautes()">J'ai compris</button>
        </div>
      </app-modale>
    }
  `,
  styles: `
    .bandeau { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; margin: 0 0 1rem; padding: .6rem .9rem;
      border-radius: 10px; background: #fdf4e1; border: 1px solid #f1d8a2; color: #6b4a05; font-size: .88rem; }
    .bandeau.grave { background: #fdecec; border-color: #f1c4c4; color: #8b1c1c; }
    .bandeau.info { background: #e8f1fd; border-color: #bcd4f5; color: #12407a; }
    .texte { flex: 1; min-width: 200px; }
    .message-maintenance { display: block; margin-top: 2px; opacity: .9; }
    .fermer { border: 0; background: transparent; font-size: 1.2rem; line-height: 1; cursor: pointer; color: inherit; }
    .titre-version { margin: 0 0 .6rem; }
    .notes { white-space: pre-line; line-height: 1.5; }
  `
})
export class BandeauMaintenance {
  readonly etat = inject(EtatPlateformeService);
  private readonly auth = inject(AuthService);
  private readonly masquees = signal<number[]>(lireMasquees());

  /** Maintenances qui touchent un module de l'entreprise (toutes pour l'exploitant). */
  readonly visibles = computed(() => {
    const superAdmin = this.auth.estSuperAdmin();
    return this.etat.maintenances().filter((m) =>
      (m.statut === 'EN_COURS' || !this.masquees().includes(m.id))
      && (superAdmin || m.modules.some((x) => this.auth.aModule(x.code))));
  });

  readonly nouveautesOuvertes = computed(() => this.auth.connecte() && this.etat.nouveautesAVoir());

  libelles(m: MaintenancePlateforme): string {
    return m.modules.map((x) => x.libelle).join(', ');
  }

  memeJour(m: MaintenancePlateforme): boolean {
    return new Date(m.debut).toDateString() === new Date(m.finPrevue).toDateString();
  }

  horsLigne(m: MaintenancePlateforme): boolean {
    return m.modules.some((x) => MODULES_HORS_LIGNE.includes(x.code) && this.auth.aModule(x.code));
  }

  masquer(m: MaintenancePlateforme): void {
    this.masquees.update((l) => [...l, m.id].slice(-50));
    try {
      sessionStorage.setItem(MASQUEES, JSON.stringify(this.masquees()));
    } catch {
      // Stockage indisponible : l'annonce reviendra au prochain chargement.
    }
  }

  recharger(): void {
    // Les nouveautes de la version s'afficheront apres le rechargement.
    location.reload();
  }

  fermerNouveautes(): void {
    this.etat.marquerVue();
  }
}

function lireMasquees(): number[] {
  try {
    const brut = sessionStorage.getItem(MASQUEES);
    const liste = brut ? JSON.parse(brut) : [];
    return Array.isArray(liste) ? liste.filter((x) => typeof x === 'number') : [];
  } catch {
    return [];
  }
}
