import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, model } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MODES_SORTIE, ModeSortie } from '../core/models/achats';
import { SessionTravailService } from '../core/services/session-travail.service';

/**
 * Moyen de paiement d'une depense ou d'un fournisseur. En « Caisse », la
 * caisse ouverte d'ou sortent les especes est choisie ici (la seule ouverte
 * est prise d'office), avec les especes attendues dans son tiroir.
 */
@Component({
  selector: 'app-choix-sortie',
  imports: [FormsModule, DecimalPipe],
  template: `
    <div class="moyens" role="radiogroup" aria-label="Moyen de paiement">
      @for (m of modes; track m.valeur) {
        <button type="button" class="moyen" [class.actif]="mode() === m.valeur" [title]="m.aide"
                role="radio" [attr.aria-checked]="mode() === m.valeur" (click)="mode.set(m.valeur)">
          <span aria-hidden="true">{{ m.icone }}</span> {{ m.libelle }}
        </button>
      }
    </div>
    @if (mode() === 'CAISSE') {
      @let liste = ouvertes() ?? [];
      @if (liste.length === 0) {
        <div class="message avertissement">Aucune caisse ouverte : ouvrez une session ou choisissez un autre moyen de paiement.</div>
      } @else {
        <div class="champ">
          <label for="choix-sortie-session">Caisse d'ou sortent les especes *</label>
          <select id="choix-sortie-session" name="sessionSortie" [ngModel]="sessionId()" (ngModelChange)="sessionId.set($event)">
            <option [ngValue]="null" disabled>Choisissez la caisse</option>
            @for (s of liste; track s.id) {
              <option [ngValue]="s.id">{{ s.caisseNom }} · {{ s.numero }} — {{ s.especesAttendues | number: '1.0-0' }} F en caisse</option>
            }
          </select>
        </div>
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 0.6rem; }
    .moyens { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .moyen {
      padding: 0.45rem 0.8rem; border: 1px solid var(--couleur-bordure); border-radius: 999px;
      background: var(--couleur-surface); font: inherit; cursor: pointer;
    }
    .moyen.actif { border-color: #1e9e4a; background: #e3f5ea; color: #157a37; font-weight: 600; }
  `
})
export class ChoixSortie implements OnInit {
  private readonly sessions = inject(SessionTravailService);

  readonly mode = model<ModeSortie>('CAISSE');
  readonly sessionId = model<number | null>(null);

  readonly modes = MODES_SORTIE;
  readonly ouvertes = this.sessions.ouvertes;
  private readonly seule = computed(() => {
    const liste = this.ouvertes() ?? [];
    return liste.length === 1 ? liste[0].id : null;
  });

  constructor() {
    // Une seule caisse ouverte : elle est choisie d'office.
    effect(() => {
      const seule = this.seule();
      if (seule && this.sessionId() === null) {
        this.sessionId.set(seule);
      }
    });
  }

  ngOnInit(): void {
    this.sessions.rafraichir();
  }
}
