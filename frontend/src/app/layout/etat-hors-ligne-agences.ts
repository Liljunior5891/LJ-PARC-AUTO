import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AgencesHorsLigneService } from '../core/services/agences-hors-ligne.service';

/**
 * Pastille de la barre du haut pour les agences : reseau coupe, operations en
 * attente d'envoi, envoi en cours, operations refusees (conflits). Le
 * superviseur y voit aussi le nombre de conflits a traiter.
 */
@Component({
  selector: 'app-etat-hors-ligne-agences',
  imports: [DatePipe, RouterLink],
  host: { '(document:click)': 'fermerSiDehors($event)' },
  template: `
    @if (visible()) {
      <span class="zone">
        <button type="button" class="pastille" [class.hors]="!horsLigne.enLigne()" [class.alerte]="refusees().length || conflitsServeur()"
                (click)="ouvert.set(!ouvert())" [attr.aria-expanded]="ouvert()" title="Travail hors ligne">
          @if (!horsLigne.enLigne()) {
            <span aria-hidden="true">📴</span> Hors ligne
          } @else if (horsLigne.envoiEnCours()) {
            <span aria-hidden="true">⟳</span> Envoi...
          } @else {
            <span aria-hidden="true">📶</span> En ligne
          }
          @if (horsLigne.enAttente().length) { <span class="compte">{{ horsLigne.enAttente().length }} en attente</span> }
          @if (refusees().length) { <span class="compte rouge">{{ refusees().length }} refusee(s)</span> }
          @if (conflitsServeur()) { <span class="compte rouge">{{ conflitsServeur() }} conflit(s)</span> }
        </button>
        @if (ouvert()) {
          <div class="panneau" role="dialog" aria-label="Operations hors ligne">
            <p class="etat">{{ horsLigne.enLigne() ? 'Connexion au serveur retablie.' : 'Pas de connexion : vos operations sont gardees sur cet appareil.' }}</p>
            @if (horsLigne.enAttente().length) {
              <h4>En attente d'envoi</h4>
              <ul>
                @for (o of horsLigne.enAttente(); track o.cle) {
                  <li><strong>{{ horsLigne.referenceProvisoire(o.cle) }}</strong> {{ o.resume }} <small>{{ o.saisieLe | date: 'dd/MM HH:mm' }}</small></li>
                }
              </ul>
              <button type="button" class="bouton secondaire petit" [disabled]="horsLigne.envoiEnCours()" (click)="horsLigne.envoyer()">Envoyer maintenant</button>
            }
            @if (horsLigne.derniersResultats().length) {
              <h4>Derniers envois</h4>
              <ul>
                @for (r of horsLigne.derniersResultats(); track r.cle) {
                  <li [class.refus]="r.statut === 'CONFLIT'">
                    <strong>{{ r.statut === 'CONFLIT' ? '⚠ Refusee' : '✓ ' + (r.reference ?? 'Enregistree') }}</strong> {{ r.resume }}
                    @if (r.statut === 'CONFLIT') { <small class="motif">{{ r.message }} — le superviseur va la traiter.</small> }
                  </li>
                }
              </ul>
              <button type="button" class="bouton secondaire petit" (click)="horsLigne.oublierResultats()">J'ai vu</button>
            }
            @if (conflitsServeur()) {
              <a class="bouton petit" routerLink="/agences/hors-ligne" (click)="ouvert.set(false)">Traiter {{ conflitsServeur() }} conflit(s)</a>
            }
            @if (!horsLigne.enAttente().length && !horsLigne.derniersResultats().length && !conflitsServeur()) {
              <p class="aide">Aucune operation hors ligne.</p>
            }
          </div>
        }
      </span>
    }
  `,
  styles: `
    .zone { position: relative; display: inline-flex; }
    .pastille {
      display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.7rem; border: 1px solid #cfe3d6;
      border-radius: 999px; background: #e9f7ef; color: #1f7a4c; font: inherit; font-size: 0.8rem; font-weight: 600; cursor: pointer;
      white-space: nowrap;
    }
    .pastille.hors { border-color: #f2dca4; background: #fff6e0; color: #8a6200; }
    .pastille.alerte { border-color: #f3c4c4; }
    .compte { padding: 0.05rem 0.45rem; border-radius: 999px; background: rgba(0, 0, 0, 0.07); font-size: 0.72rem; }
    .compte.rouge { background: #fce1e1; color: #b42318; }
    .panneau {
      position: absolute; z-index: 60; top: calc(100% + 6px); right: 0; width: min(380px, 90vw); max-height: 70vh; overflow: auto;
      padding: 0.8rem 0.9rem; border: 1px solid var(--couleur-bordure); border-radius: 12px; background: #fff; color: var(--couleur-texte);
      box-shadow: 0 12px 30px -12px rgba(15, 30, 50, 0.35); font-size: 0.85rem;
    }
    .etat { margin: 0 0 0.5rem; font-weight: 600; }
    h4 { margin: 0.6rem 0 0.3rem; color: var(--couleur-texte-doux); font-size: 0.72rem; letter-spacing: 0.05em; text-transform: uppercase; }
    ul { margin: 0 0 0.5rem; padding: 0; list-style: none; }
    li { padding: 0.3rem 0; border-bottom: 1px solid #eef1f5; }
    li small { display: block; color: var(--couleur-texte-doux); }
    li.refus strong { color: #b42318; }
    .motif { color: #b42318 !important; }
    .aide { margin: 0; color: var(--couleur-texte-doux); }
  `
})
export class EtatHorsLigneAgences implements OnInit, OnDestroy {
  readonly horsLigne = inject(AgencesHorsLigneService);
  private readonly auth = inject(AuthService);

  readonly ouvert = signal(false);
  readonly conflitsServeur = signal(0);
  readonly refusees = computed(() => this.horsLigne.derniersResultats().filter((r) => r.statut === 'CONFLIT'));
  /** Rien a signaler : la pastille reste discrete, sauf hors ligne ou avec des operations a suivre. */
  readonly visible = computed(() => !this.horsLigne.enLigne() || this.horsLigne.enAttente().length > 0
    || this.horsLigne.derniersResultats().length > 0 || this.conflitsServeur() > 0 || this.horsLigne.envoiEnCours());
  private readonly superviseur = this.auth.aRole('ADMIN', 'SUPERVISEUR_AGENCE');
  private minuterie?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.horsLigne.demarrer();
    if (this.superviseur) {
      this.compterConflits();
      this.minuterie = setInterval(() => this.compterConflits(), 60_000);
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
  }

  private compterConflits(): void {
    this.horsLigne.nombreConflits().subscribe({ next: (r) => this.conflitsServeur.set(r.nombre), error: () => undefined });
  }

  fermerSiDehors(evenement: Event): void {
    if (this.ouvert() && !(evenement.target as HTMLElement).closest('app-etat-hors-ligne-agences')) {
      this.ouvert.set(false);
    }
  }
}
