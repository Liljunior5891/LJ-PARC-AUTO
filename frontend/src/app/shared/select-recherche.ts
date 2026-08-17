import {
  Component,
  ElementRef,
  computed,
  forwardRef,
  inject,
  input,
  signal
} from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';

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
 * Liste deroulante avec recherche, equivalent de select2.
 * <p>
 * Ecrite sans dependance : un composant de quelques dizaines de lignes evite
 * d'ajouter une bibliotheque tierce a maintenir, et permet d'afficher les
 * logos de marques et les pastilles de couleur du referentiel.
 */
@Component({
  selector: 'app-select-recherche',
  imports: [FormsModule],
  template: `
    <div class="select-recherche" [class.ouvert]="ouvert()" [class.desactive]="desactive()">
      <button
        type="button"
        class="declencheur"
        [disabled]="desactive()"
        (click)="basculer()"
        [attr.aria-expanded]="ouvert()"
      >
        @if (optionChoisie(); as choix) {
          @if (choix.couleur || choix.logoUrl) {
            <span class="pastille" [style.background]="choix.couleur || '#6b7684'">
              @if (choix.logoUrl) {
                <img [src]="choix.logoUrl" [alt]="choix.libelle" />
              } @else {
                {{ choix.monogramme || '' }}
              }
            </span>
          }
          <span class="libelle-choisi">{{ choix.libelle }}</span>
        } @else {
          <span class="libelle-vide">{{ placeholder() }}</span>
        }
        <span class="chevron">▾</span>
      </button>

      @if (ouvert()) {
        <div class="panneau">
          <input
            #champRecherche
            type="search"
            class="champ-recherche"
            [placeholder]="'Rechercher...'"
            [(ngModel)]="recherche"
            (ngModelChange)="filtrer($event)"
            (keydown.escape)="fermer()"
          />
          <ul class="options">
            @if (autoriserVide()) {
              <li>
                <button type="button" class="option" (click)="choisir(null)">
                  <span class="libelle-vide">{{ placeholder() }}</span>
                </button>
              </li>
            }
            @for (option of optionsFiltrees(); track option.valeur) {
              <li>
                <button
                  type="button"
                  class="option"
                  [class.selectionnee]="option.valeur === valeur()"
                  (click)="choisir(option.valeur)"
                >
                  @if (option.couleur || option.logoUrl) {
                    <span class="pastille" [style.background]="option.couleur || '#6b7684'">
                      @if (option.logoUrl) {
                        <img [src]="option.logoUrl" [alt]="option.libelle" />
                      } @else {
                        {{ option.monogramme || '' }}
                      }
                    </span>
                  }
                  <span class="textes">
                    <span>{{ option.libelle }}</span>
                    @if (option.detail) {
                      <small>{{ option.detail }}</small>
                    }
                  </span>
                </button>
              </li>
            } @empty {
              <li class="aucun-resultat">Aucun resultat</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styleUrl: './select-recherche.scss',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SelectRecherche),
      multi: true
    }
  ],
  host: {
    '(document:click)': 'clicExterieur($event)'
  }
})
export class SelectRecherche implements ControlValueAccessor {
  private readonly element: ElementRef<HTMLElement> = inject(ElementRef);

  readonly options = input<OptionSelect[]>([]);
  readonly placeholder = input('Selectionner');
  /** Autorise le choix « aucune valeur ». */
  readonly autoriserVide = input(true);

  readonly valeur = signal<string | number | null>(null);
  readonly ouvert = signal(false);
  readonly desactive = signal(false);
  readonly recherche = signal('');

  readonly optionChoisie = computed(() =>
    this.options().find((option) => option.valeur === this.valeur()) ?? null
  );

  /** Filtre sur le libelle et sur le detail, sans tenir compte des accents. */
  readonly optionsFiltrees = computed(() => {
    const terme = this.normaliser(this.recherche());
    if (!terme) {
      return this.options();
    }
    return this.options().filter((option) =>
      this.normaliser(option.libelle + ' ' + (option.detail ?? '')).includes(terme)
    );
  });

  private surChangement: (valeur: string | number | null) => void = () => undefined;
  private surContact: () => void = () => undefined;

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

  basculer(): void {
    if (this.desactive()) {
      return;
    }
    this.ouvert.update((ouvert) => !ouvert);
    if (this.ouvert()) {
      this.recherche.set('');
      // Le champ de recherche prend le focus des l'ouverture.
      setTimeout(() => {
        this.element.nativeElement.querySelector<HTMLInputElement>('.champ-recherche')?.focus();
      });
    }
  }

  filtrer(terme: string): void {
    this.recherche.set(terme);
  }

  choisir(valeur: string | number | null): void {
    this.valeur.set(valeur);
    this.surChangement(valeur);
    this.surContact();
    this.fermer();
  }

  fermer(): void {
    this.ouvert.set(false);
  }

  /** Un clic hors du composant referme la liste. */
  clicExterieur(evenement: Event): void {
    if (this.ouvert() && !this.element.nativeElement.contains(evenement.target as Node)) {
      this.fermer();
    }
  }

  private normaliser(texte: string): string {
    return texte
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .trim();
  }
}
