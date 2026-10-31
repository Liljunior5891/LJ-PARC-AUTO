import { Component, inject, signal } from '@angular/core';
import { SessionCaisse } from '../core/models/pdv';
import { SessionTravailService } from '../core/services/session-travail.service';
import { MouvementSession } from './session-caisse';

/**
 * Bouton « Especes » de la barre des ecrans d'encaissement : entree ou sortie
 * d'especes hors vente sur la session du poste (apport de monnaie, depense,
 * retrait). Grise tant qu'aucune caisse n'est ouverte ou choisie.
 */
@Component({
  selector: 'app-bouton-especes',
  imports: [MouvementSession],
  template: `
    <button type="button" class="bouton-especes" [disabled]="!sessions.courante()" (click)="ouvrir()"
            [title]="sessions.courante() ? 'Entree ou sortie d\\'especes sur ' + sessions.courante()!.caisseNom : 'Aucune caisse ouverte'"
            aria-label="Entree ou sortie d'especes">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <rect x="2.5" y="6" width="19" height="12" rx="2.5" /><circle cx="12" cy="12" r="2.6" />
        <path d="M6 9.5v.01M18 14.5v.01" />
      </svg>
      <span>± Especes</span>
    </button>
    @if (session(); as s) {
      <app-mouvement-session [session]="s" (ferme)="session.set(null)" (enregistre)="enregistre($event)" />
    }
  `,
  styles: `
    :host { display: contents; }
    .bouton-especes {
      display: inline-flex; align-items: center; gap: 0.45rem; flex-shrink: 0; height: 44px; padding: 0 0.95rem;
      border: 1px solid #f0cf8f; border-radius: 12px; background: #fff4dc; color: #9a5b00;
      font: inherit; font-weight: 700; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease;
    }
    .bouton-especes:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 14px rgba(201, 122, 10, 0.22); }
    .bouton-especes:disabled { opacity: 0.45; cursor: not-allowed; }
    svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
    @media (max-width: 820px) { span { display: none; } }
  `
})
export class BoutonEspeces {
  readonly sessions = inject(SessionTravailService);
  readonly session = signal<SessionCaisse | null>(null);

  ouvrir(): void {
    this.session.set(this.sessions.courante() ?? null);
  }

  enregistre(session: SessionCaisse): void {
    this.session.set(null);
    this.sessions.remplacer(session);
  }
}
