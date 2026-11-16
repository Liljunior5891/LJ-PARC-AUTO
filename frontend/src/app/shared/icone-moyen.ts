import { Component, input } from '@angular/core';

/**
 * Pictogramme d'un moyen de paiement ou de sortie d'argent (especes, mobile
 * money, carte, caisse, coffre, banque, fonds de reserve, avance), dessine
 * comme ceux de l'ecran de caisse, dans une pastille a la couleur du moyen.
 */
@Component({
  selector: 'app-icone-moyen',
  template: `
    <span class="pastille" [attr.data-moyen]="code()" aria-hidden="true">
      @switch (code()) {
        @case ('ESPECES') {
          <svg viewBox="0 0 24 24"><rect x="2.5" y="6" width="19" height="12" rx="2" /><circle cx="12" cy="12" r="2.8" /><path d="M6 9.5v5M18 9.5v5" /></svg>
        }
        @case ('MOBILE_MONEY') {
          <svg viewBox="0 0 24 24"><rect x="6.5" y="2.5" width="11" height="19" rx="2.2" /><path d="M10.5 18.5h3" /><path d="M9.5 9.5l2 2 3.5-3.5" /></svg>
        }
        @case ('CARTE') {
          <svg viewBox="0 0 24 24"><rect x="2.5" y="5.5" width="19" height="13" rx="2" /><path d="M2.5 9.5h19" /><path d="M6 14.5h4" /></svg>
        }
        @case ('CAISSE') {
          <svg viewBox="0 0 24 24"><path d="M4 10h16v9.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19.5z" /><path d="M4 14h16" /><path d="M10.5 17h3" /><path d="M7 10V6.5A1.5 1.5 0 0 1 8.5 5h7A1.5 1.5 0 0 1 17 6.5V10" /><path d="M9.5 7.5h5" /></svg>
        }
        @case ('COFFRE') {
          <svg viewBox="0 0 24 24"><rect x="3.5" y="4" width="17" height="15" rx="2" /><circle cx="12" cy="11.5" r="3.2" /><path d="M12 8.3v1.2M12 13.5v1.2M8.8 11.5h1.2M14 11.5h1.2" /><path d="M6.5 19v1.5M17.5 19v1.5" /></svg>
        }
        @case ('BANQUE') {
          <svg viewBox="0 0 24 24"><path d="M3 9.5L12 4l9 5.5" /><path d="M5 10v7.5M9.5 10v7.5M14.5 10v7.5M19 10v7.5" /><path d="M3 20h18" /></svg>
        }
        @case ('RESERVE') {
          <svg viewBox="0 0 24 24"><path d="M12 3l7.5 3v5.5c0 4.5-3.2 8-7.5 9.5-4.3-1.5-7.5-5-7.5-9.5V6z" /><path d="M9 12l2 2 4-4" /></svg>
        }
        @case ('COMPTE_CLIENT') {
          <svg viewBox="0 0 24 24"><circle cx="9" cy="8" r="3.2" /><path d="M3.5 19c.6-3.3 2.8-5.2 5.5-5.2 1.3 0 2.5.4 3.4 1.2" /><rect x="13" y="12.5" width="8.5" height="7" rx="1.5" /><path d="M13 15.3h8.5" /></svg>
        }
        @case ('AVANCE') {
          <svg viewBox="0 0 24 24"><path d="M3 12.5l3.5-3.5 3 1.5 3.5-3 4 2 4-2" /><path d="M3 12.5l5 5 2.5-1.5 2 1.5 2.5-1.5 2 1 4-4.5" /><path d="M8 17.5l2-2M10.5 16l2-2" /></svg>
        }
        @default {
          <svg viewBox="0 0 24 24"><path d="M6 2.5h12v19l-2.4-1.6-2.4 1.6-2.4-1.6L8.4 21.5 6 19.9z" /><path d="M9 7.5h6M9 11h6M9 14.5h4" /></svg>
        }
      }
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .pastille {
      --teinte: #6b7280;
      display: grid;
      place-items: center;
      width: 30px;
      height: 30px;
      border-radius: 9px;
      background: color-mix(in srgb, var(--teinte) 14%, #fff);
    }
    .pastille[data-moyen='ESPECES'], .pastille[data-moyen='CAISSE'] { --teinte: #1e9e4a; }
    .pastille[data-moyen='MOBILE_MONEY'] { --teinte: #e67e22; }
    .pastille[data-moyen='CARTE'], .pastille[data-moyen='BANQUE'] { --teinte: #1f6fd1; }
    .pastille[data-moyen='COFFRE'] { --teinte: #7c3aed; }
    .pastille[data-moyen='RESERVE'] { --teinte: #0f9488; }
    .pastille[data-moyen='AVANCE'], .pastille[data-moyen='COMPTE_CLIENT'] { --teinte: #b45309; }
    svg {
      width: 18px;
      height: 18px;
      fill: none;
      stroke: var(--teinte);
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
  `
})
export class IconeMoyen {
  /** Code du moyen : ESPECES, MOBILE_MONEY, CARTE, AUTRE, COMPTE_CLIENT, CAISSE, COFFRE, BANQUE, RESERVE, AVANCE. */
  readonly code = input.required<string>();
}
