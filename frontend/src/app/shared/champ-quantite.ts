import { Component, input, model } from '@angular/core';

/**
 * Champ de quantite des listes de saisie (commande fournisseur, reception,
 * inventaire) : les boutons − et + pour ajuster d'un geste, la saisie directe
 * pour une quantite precise. Valeur nulle tant que rien n'est saisi.
 */
@Component({
  selector: 'app-champ-quantite',
  template: `
    <div class="quantite" [class.vide]="valeur() === null" [class.compact]="compact()">
      <button type="button" tabindex="-1" [disabled]="(valeur() ?? 0) <= min()" (click)="ajuster(-pas())"
              [attr.aria-label]="'Diminuer ' + libelle()">−</button>
      <input type="number" inputmode="decimal" [min]="min()" step="any" [value]="valeur() ?? ''"
             [attr.placeholder]="placeholder()" [attr.aria-label]="libelle()"
             (input)="saisir($any($event.target).value)" (focus)="$any($event.target).select()" />
      <button type="button" tabindex="-1" (click)="ajuster(pas())" [attr.aria-label]="'Augmenter ' + libelle()">+</button>
    </div>
  `,
  styles: `
    :host {
      display: inline-block;
    }

    .quantite {
      display: inline-flex;
      align-items: stretch;
      height: 36px;
      overflow: hidden;
      border: 1px solid var(--couleur-bordure);
      border-radius: 9px;
      background: #fff;
      transition: border-color 0.15s ease, box-shadow 0.15s ease;

      &:focus-within {
        border-color: var(--couleur-primaire-clair);
        box-shadow: 0 0 0 3px color-mix(in srgb, var(--couleur-primaire-clair) 18%, transparent);
      }

      &.vide input {
        background: #fafbfc;
      }

      &.compact {
        height: 32px;
      }
    }

    button {
      width: 32px;
      border: none;
      background: var(--couleur-fond);
      color: var(--couleur-primaire);
      font-size: 1.05rem;
      font-weight: 700;
      line-height: 1;
      cursor: pointer;

      &:hover:not(:disabled) {
        background: color-mix(in srgb, var(--couleur-primaire) 12%, #fff);
      }

      &:disabled {
        color: var(--couleur-bordure);
        cursor: default;
      }
    }

    input {
      width: 68px;
      padding: 0 0.4rem;
      border: none;
      border-right: 1px solid var(--couleur-bordure);
      border-left: 1px solid var(--couleur-bordure);
      outline: none;
      font: inherit;
      font-weight: 600;
      text-align: center;
      -moz-appearance: textfield;

      &::-webkit-inner-spin-button,
      &::-webkit-outer-spin-button {
        -webkit-appearance: none;
        margin: 0;
      }
    }
  `
})
export class ChampQuantite {
  readonly valeur = model<number | null>(null);
  readonly min = input(0);
  readonly pas = input(1);
  readonly libelle = input('Quantite');
  readonly placeholder = input('');
  readonly compact = input(false);

  ajuster(delta: number): void {
    const suivante = Math.max(this.min(), Math.round(((this.valeur() ?? 0) + delta) * 1000) / 1000);
    this.valeur.set(suivante);
  }

  saisir(brut: string): void {
    if (brut === '' || brut === null) {
      this.valeur.set(null);
      return;
    }
    const nombre = Number(brut);
    this.valeur.set(Number.isNaN(nombre) ? null : Math.max(this.min(), nombre));
  }
}
