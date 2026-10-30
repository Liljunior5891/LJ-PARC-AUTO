import {
  AfterViewInit,
  DestroyRef,
  Directive,
  ElementRef,
  Renderer2,
  inject
} from '@angular/core';
import { FormGroupDirective, NgControl, Validators } from '@angular/forms';

/** Libelle d'un champ, sans l'asterisque ni les boutons places dans l'etiquette. */
function libelleDuChamp(element: Element): string | null {
  const etiquette = element.closest('.champ')?.querySelector('label');
  if (!etiquette) {
    return null;
  }
  const copie = etiquette.cloneNode(true) as HTMLElement;
  copie.querySelectorAll('button, .requis').forEach((e) => e.remove());
  return copie.textContent?.replace(/\*/g, '').replace(/\s+/g, ' ').trim() || null;
}

/**
 * Marque d'un asterisque rouge l'etiquette d'un champ obligatoire.
 * <p>
 * Un champ est obligatoire quand son controle porte {@link Validators.required} :
 * l'etiquette le dit sans qu'on ait a l'ecrire dans chaque gabarit. Une etiquette
 * qui porte deja son asterisque n'est pas modifiee.
 */
@Directive({ selector: '[formControlName]' })
export class ChampObligatoire implements AfterViewInit {
  private readonly controle = inject(NgControl, { self: true });
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);

  ngAfterViewInit(): void {
    const controle = this.controle.control;
    if (!controle?.hasValidator(Validators.required)) {
      return;
    }
    const etiquette = this.element.nativeElement.closest('.champ')?.querySelector('label');
    if (!etiquette || etiquette.querySelector('.requis') || etiquette.textContent?.includes('*')) {
      return;
    }
    const marque = this.renderer.createElement('span');
    this.renderer.addClass(marque, 'requis');
    this.renderer.setAttribute(marque, 'aria-hidden', 'true');
    this.renderer.appendChild(marque, this.renderer.createText(' *'));
    this.renderer.appendChild(etiquette, marque);
  }
}

/**
 * Bloque l'enregistrement d'un formulaire incomplet et dit pourquoi.
 * <p>
 * A la soumission d'un formulaire invalide, chaque champ fautif passe en
 * rouge, un message liste ceux a remplir ou a corriger, a cote du bouton
 * d'enregistrement, et le curseur va au premier. Le message disparait des que
 * le formulaire devient valide. Les ecrans gardent leur propre garde : la
 * requete n'est de toute facon pas envoyee.
 */
@Directive({
  selector: 'form[formGroup]',
  host: { '(submit)': 'verifier()' }
})
export class ValidationFormulaire {
  private readonly groupe = inject(FormGroupDirective, { self: true });
  private readonly element = inject<ElementRef<HTMLFormElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private message?: HTMLElement;

  constructor() {
    const abonnement = this.groupe.statusChanges?.subscribe(() => {
      if (this.groupe.valid) {
        this.retirerMessage();
      } else if (this.message) {
        this.afficherMessage();
      }
    });
    inject(DestroyRef).onDestroy(() => abonnement?.unsubscribe());
  }

  verifier(): void {
    if (this.groupe.valid) {
      this.retirerMessage();
      return;
    }
    this.groupe.control.markAllAsTouched();
    this.afficherMessage();
    // Le premier champ fautif recoit le focus (pour une liste Select2, son selecteur visible).
    const premier = this.element.nativeElement.querySelector<HTMLElement>(
      'input.ng-invalid, select.ng-invalid, textarea.ng-invalid, app-select-recherche.ng-invalid select'
    );
    const cible = premier?.closest('.champ')?.querySelector<HTMLElement>('.select2-selection') ?? premier;
    cible?.focus();
    cible?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  }

  /** Message place juste avant les boutons : la ou l'utilisateur vient de cliquer. */
  private afficherMessage(): void {
    const { aRemplir, aCorriger } = this.champsFautifs();
    if (!aRemplir.length && !aCorriger.length) {
      this.retirerMessage();
      return;
    }
    if (!this.message) {
      this.message = this.renderer.createElement('div');
      this.renderer.addClass(this.message, 'message');
      this.renderer.addClass(this.message, 'erreur');
      this.renderer.addClass(this.message, 'alerte-champs');
      this.renderer.setAttribute(this.message, 'role', 'alert');
      const form = this.element.nativeElement;
      const actions = Array.from(form.children).reverse()
        .find((e) => (e as HTMLElement).classList.contains('actions-formulaire'));
      this.renderer.insertBefore(form, this.message, actions ?? null);
    }
    const phrases: string[] = [];
    if (aRemplir.length) {
      phrases.push(`Champs obligatoires a remplir : ${aRemplir.join(', ')}.`);
    }
    if (aCorriger.length) {
      phrases.push(`Valeurs a corriger : ${aCorriger.join(', ')}.`);
    }
    this.message!.textContent = phrases.join(' ');
  }

  private retirerMessage(): void {
    this.message?.remove();
    this.message = undefined;
  }

  /** Libelles des champs invalides, separes entre vides et mal remplis. */
  private champsFautifs(): { aRemplir: string[]; aCorriger: string[] } {
    const aRemplir: string[] = [];
    const aCorriger: string[] = [];
    const form = this.element.nativeElement;
    Object.entries(this.groupe.control.controls).forEach(([nom, controle]) => {
      if (controle.valid || controle.disabled) {
        return;
      }
      const element = form.querySelector(`[formcontrolname="${nom}"]`);
      const libelle = (element && libelleDuChamp(element)) ?? nom;
      (controle.hasError('required') ? aRemplir : aCorriger).push(libelle);
    });
    return { aRemplir: [...new Set(aRemplir)], aCorriger: [...new Set(aCorriger)] };
  }
}

/**
 * Pour les formulaires sans validateurs (saisie libre et bouton d'action) :
 * message listant les champs obligatoires restes vides, ou null s'il n'en manque pas.
 */
export function messageChampsManquants(champs: Record<string, unknown>): string | null {
  const vides = Object.entries(champs)
    .filter(([, valeur]) => valeur === null || valeur === undefined
      || (typeof valeur === 'string' && valeur.trim() === '')
      || (typeof valeur === 'number' && Number.isNaN(valeur)))
    .map(([libelle]) => libelle);
  return vides.length ? `Champs obligatoires a remplir : ${vides.join(', ')}.` : null;
}

/** A importer dans tout composant qui porte un formulaire reactif. */
export const VALIDATION_FORMULAIRE = [ChampObligatoire, ValidationFormulaire] as const;
