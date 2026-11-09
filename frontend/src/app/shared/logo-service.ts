import { Component, computed, input } from '@angular/core';

/** Teintes des pastilles des services sans logo : une couleur stable par nom. */
const TEINTES = ['#f2b705', '#1f6fd1', '#e8590c', '#1f9d6b', '#7a4fd1', '#d9452b', '#0f9aa8', '#c2185b', '#5d6d1e'];

/** Couleur stable tiree du nom (meme service, meme couleur partout). */
export function couleurService(nom?: string | null): string {
  let h = 0;
  for (const c of nom ?? '') {
    h = (h * 31 + c.charCodeAt(0)) >>> 0;
  }
  return TEINTES[h % TEINTES.length];
}

/** Deux initiales : « Mixx by Yas » donne MY, « Western Union » donne WU. */
export function initialesService(nom?: string | null): string {
  const mots = (nom ?? '?').split(/[\s\-_/]+/).filter((m) => m && !/^(by|de|du|la|le|les|et)$/i.test(m));
  return (mots.length > 1 ? mots[0][0] + mots[1][0] : (mots[0] ?? '?').slice(0, 2)).toUpperCase();
}

/**
 * Attributs data-* d'une option de liste : Select2 y dessine le logo du service,
 * ou une pastille de sa couleur avec ses initiales.
 */
export function attributsOption(nom?: string | null, logo?: string | null): { couleur: string; logo: string | null; monogramme: string } {
  return { couleur: logo ? '#ffffff' : couleurService(nom), logo: logo || null, monogramme: initialesService(nom) };
}

/**
 * Logo d'un service ou d'un reseau : l'image choisie par l'administrateur, ou a
 * defaut une pastille coloree avec les initiales.
 */
@Component({
  selector: 'app-logo-service',
  template: `
    @if (logo()) {
      <img [src]="logo()" [alt]="nom() ?? ''" [style.width.px]="taille()" [style.height.px]="taille()" />
    } @else {
      <span class="pastille" [style.background]="couleur()" [style.width.px]="taille()" [style.height.px]="taille()"
            [style.font-size.px]="taille() * 0.38" aria-hidden="true">{{ initiales() }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      vertical-align: middle;
    }

    img,
    .pastille {
      display: inline-grid;
      place-items: center;
      border-radius: 28%;
      object-fit: cover;
      box-shadow: 0 0 0 1px rgba(15, 30, 50, 0.08);
    }

    img {
      background: #fff;
    }

    .pastille {
      color: #fff;
      font-weight: 800;
      letter-spacing: 0.02em;
    }
  `
})
export class LogoService {
  readonly nom = input<string | null | undefined>('');
  readonly logo = input<string | null | undefined>(null);
  readonly taille = input(28);

  readonly couleur = computed(() => couleurService(this.nom()));
  readonly initiales = computed(() => initialesService(this.nom()));
}
