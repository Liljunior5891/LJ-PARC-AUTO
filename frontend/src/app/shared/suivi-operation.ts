import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, effect, inject, viewChild } from '@angular/core';
import { OperationService } from '../core/operation/operation.service';

/** Circonference de l'anneau de progression (rayon 52). */
const CIRCONFERENCE = 2 * Math.PI * 52;

/**
 * Fenetre unique de l'application pour les operations d'ecriture : la
 * confirmation avant l'envoi, puis l'anneau qui progresse jusqu'a 100 % et
 * annonce le resultat. Elle passe au-dessus des fenetres de formulaire.
 */
@Component({
  selector: 'app-suivi-operation',
  imports: [DecimalPipe],
  host: { '(document:keydown)': 'clavier($event)' },
  template: `
    @if (operation.etat() !== 'inactif') {
      <div class="voile"></div>

      @if (operation.etat() === 'confirmation') {
        @if (operation.demande(); as d) {
          <section class="boite confirmation" [class.danger]="d.danger" role="alertdialog" aria-modal="true"
                   aria-labelledby="titre-confirmation">
            <span class="pictogramme" aria-hidden="true">{{ d.danger ? '!' : '?' }}</span>
            <h2 id="titre-confirmation">{{ d.titre }}</h2>
            @if (d.message) {
              <p>{{ d.message }}</p>
            }
            <div class="boutons">
              <button class="bouton secondaire annuler" type="button" (click)="operation.repondre(false)">Annuler</button>
              <button #valider class="bouton" [class.danger]="d.danger" type="button"
                      (click)="operation.repondre(true)">
                {{ d.libelleAction || 'Confirmer' }}
              </button>
            </div>
          </section>
        }
      } @else {
        <section [class]="'boite progression ' + operation.etat()"
                 role="status" aria-live="polite">
          <div class="anneau">
            <svg viewBox="0 0 120 120" aria-hidden="true">
              <circle cx="60" cy="60" r="52" class="piste" />
              <circle cx="60" cy="60" r="52" class="arc" [attr.stroke-dasharray]="arc()" />
            </svg>
            <div class="centre">
              @switch (operation.etat()) {
                @case ('succes') {
                  <svg class="coche" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
                }
                @case ('echec') {
                  <svg class="croix" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7l10 10M17 7 7 17" /></svg>
                }
                @default {
                  <strong>{{ operation.progression() | number: '1.0-0' }}<small>%</small></strong>
                }
              }
            </div>
          </div>
          <p class="libelle">
            @switch (operation.etat()) {
              @case ('succes') { {{ operation.messageResultat() }} }
              @case ('echec') { Operation refusee }
              @default { Operation en cours... }
            }
          </p>
          @if (operation.etat() === 'echec') {
            <small class="detail">{{ operation.messageResultat() }}</small>
          }
        </section>
      }
    }
  `,
  styleUrl: './suivi-operation.scss'
})
export class SuiviOperation {
  protected readonly operation = inject(OperationService);
  private readonly valider = viewChild<ElementRef<HTMLButtonElement>>('valider');

  constructor() {
    // Le bouton de validation recoit le focus : Entree confirme, Echap renonce.
    effect(() => this.valider()?.nativeElement.focus());
  }

  arc(): string {
    return `${(this.operation.progression() / 100) * CIRCONFERENCE} ${CIRCONFERENCE}`;
  }

  clavier(evenement: KeyboardEvent): void {
    if (this.operation.etat() !== 'confirmation') {
      return;
    }
    if (evenement.key === 'Escape') {
      // La fenetre de formulaire dessous ne doit pas se fermer avec.
      evenement.stopImmediatePropagation();
      this.operation.repondre(false);
    }
  }
}
