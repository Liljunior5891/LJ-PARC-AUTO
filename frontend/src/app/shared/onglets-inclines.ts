import { Component, input, output } from '@angular/core';

/** Un onglet : identifiant, libelle, icone facultative et compteur facultatif. */
export interface Onglet {
  id: string;
  libelle: string;
  icone?: string;
  compteur?: number | null;
}

/**
 * Onglets inclines, ceux des ecrans du PDV (depenses, tresorerie) : l'onglet
 * ouvert passe devant, les autres s'empilent de gauche a droite.
 */
@Component({
  selector: 'app-onglets-inclines',
  template: `
    <div class="onglets-inclines" role="tablist">
      @for (o of onglets(); track o.id; let i = $index) {
        <button type="button" role="tab" [class.actif]="actif() === o.id"
                [style.z-index]="actif() === o.id ? onglets().length + 1 : onglets().length - i"
                [attr.aria-selected]="actif() === o.id" (click)="choisir.emit(o.id)">
          @if (o.icone) { <span class="icone" aria-hidden="true">{{ o.icone }}</span> }
          <span>{{ o.libelle }}</span>
          @if (o.compteur) { <span class="compteur">{{ o.compteur }}</span> }
        </button>
      }
    </div>
  `
})
export class OngletsInclines {
  readonly onglets = input<Onglet[]>([]);
  readonly actif = input<string | null>(null);
  readonly choisir = output<string>();
}
