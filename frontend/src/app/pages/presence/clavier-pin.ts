import { Component, HostListener, input, output, signal } from '@angular/core';

/**
 * Clavier PIN a 6 chiffres, gros boutons pour le doigt : le PIN part des que
 * le sixieme chiffre est tape. Le clavier physique marche aussi.
 */
@Component({
  selector: 'app-clavier-pin',
  template: `
    <div class="pin" aria-live="polite" [attr.aria-label]="saisie().length + ' chiffre(s) sur 6'">
      @for (i of positions; track i) {
        <span class="point" [class.rempli]="i < saisie().length"></span>
      }
    </div>
    <div class="touches">
      @for (t of touches; track t) {
        <button type="button" class="touche" [disabled]="desactive()" (click)="taper(t)">{{ t }}</button>
      }
      <button type="button" class="touche secondaire" [disabled]="desactive() || !saisie().length" (click)="effacer()" aria-label="Effacer">⌫</button>
      <button type="button" class="touche" [disabled]="desactive()" (click)="taper('0')">0</button>
      <button type="button" class="touche secondaire" [disabled]="desactive() || !saisie().length" (click)="vider()" aria-label="Tout effacer">✕</button>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; align-items: center; gap: 1.25rem; }
    .pin { display: flex; gap: 0.9rem; }
    .point { width: 16px; height: 16px; border-radius: 50%; border: 2px solid var(--couleur-primaire); transition: background 0.1s; }
    .point.rempli { background: var(--couleur-primaire); }
    .touches { display: grid; grid-template-columns: repeat(3, 76px); gap: 0.8rem; }
    .touche {
      height: 76px; border: 1px solid var(--couleur-bordure); border-radius: 50%;
      background: var(--couleur-surface); color: var(--couleur-texte);
      font-size: 1.7rem; font-weight: 600; cursor: pointer; touch-action: manipulation;
      box-shadow: 0 2px 6px rgba(16, 40, 30, 0.08);
    }
    .touche:active:not(:disabled) { background: color-mix(in srgb, var(--couleur-primaire) 14%, var(--couleur-surface)); }
    .touche.secondaire { font-size: 1.3rem; color: var(--couleur-texte-doux); }
    .touche:disabled { opacity: 0.45; cursor: default; }
  `]
})
export class ClavierPin {
  readonly desactive = input(false);
  readonly valide = output<string>();

  readonly saisie = signal('');
  readonly positions = [0, 1, 2, 3, 4, 5];
  readonly touches = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];

  taper(chiffre: string): void {
    if (this.desactive() || this.saisie().length >= 6) {
      return;
    }
    this.saisie.update((s) => s + chiffre);
    if (this.saisie().length === 6) {
      const pin = this.saisie();
      // Le dernier point se remplit avant l'envoi.
      setTimeout(() => this.valide.emit(pin), 120);
    }
  }

  effacer(): void {
    this.saisie.update((s) => s.slice(0, -1));
  }

  vider(): void {
    this.saisie.set('');
  }

  @HostListener('document:keydown', ['$event'])
  clavier(e: KeyboardEvent): void {
    if (/^\d$/.test(e.key)) {
      this.taper(e.key);
    } else if (e.key === 'Backspace') {
      this.effacer();
    }
  }
}
