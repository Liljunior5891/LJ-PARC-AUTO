import { Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

/** Une entree de la liste, telle que fournie par l'appelant. */
export interface OptionSelect {
  valeur: string | number | null;
  libelle: string;
  /** Ligne secondaire, affichee en plus petit sous le libelle. */
  detail?: string;
  /** Pastille de couleur affichee devant le libelle. */
  couleur?: string;
  /** Initiales affichees dans la pastille, a defaut d'image. */
  monogramme?: string;
  logoUrl?: string;
}

/**
 * Liste deroulante avec recherche, rendue par Select2.
 * <p>
 * Le composant pose un select natif que Select2Service habille, comme toutes
 * les listes de l'application. Pastille, logo et ligne de detail passent par
 * des attributs data-* que Select2 affiche. Les valeurs typees (nombre, texte,
 * null) sont retrouvees par leur rang, le select ne connaissant que du texte.
 */
@Component({
  selector: 'app-select-recherche',
  template: `
    <select
      data-recherche="toujours"
      [disabled]="desactive()"
      (change)="surSelection($event)"
      (blur)="surContact()"
    >
      @if (autoriserVide() || indexChoisi() < 0) {
        <option value="" [selected]="indexChoisi() < 0">{{ placeholder() }}</option>
      }
      @for (option of options(); track $index) {
        <option
          [value]="$index"
          [selected]="$index === indexChoisi()"
          [attr.data-couleur]="option.couleur || (option.logoUrl || option.monogramme ? '#6b7684' : null)"
          [attr.data-logo]="option.logoUrl || null"
          [attr.data-monogramme]="option.monogramme || null"
          [attr.data-detail]="option.detail || null"
        >{{ option.libelle }}</option>
      }
    </select>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
    }
  `,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectRecherche),
      multi: true
    }
  ]
})
export class SelectRecherche implements ControlValueAccessor {
  readonly options = input<OptionSelect[]>([]);
  readonly placeholder = input('Selectionner');
  /** Autorise le choix « aucune valeur ». */
  readonly autoriserVide = input(true);

  readonly valeur = signal<string | number | null>(null);
  readonly desactive = signal(false);

  /** Rang de l'option choisie, -1 sans choix. */
  readonly indexChoisi = computed(() =>
    this.options().findIndex((option) => option.valeur === this.valeur())
  );

  private surChangement: (valeur: string | number | null) => void = () => undefined;
  surContact: () => void = () => undefined;

  writeValue(valeur: string | number | null): void {
    this.valeur.set(valeur ?? null);
  }

  registerOnChange(fn: (valeur: string | number | null) => void): void {
    this.surChangement = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.surContact = fn;
  }

  setDisabledState(desactive: boolean): void {
    this.desactive.set(desactive);
  }

  surSelection(evenement: Event): void {
    const rang = (evenement.target as HTMLSelectElement).value;
    const valeur = rang === '' ? null : (this.options()[Number(rang)]?.valeur ?? null);
    this.valeur.set(valeur);
    this.surChangement(valeur);
  }
}
