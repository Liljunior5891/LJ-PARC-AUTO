import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/*
 * Dessins de profil des vehicules, tous tournes vers la droite et poses sur le
 * meme sol (viewBox 230 x 110). Ils sont construits a partir de pieces
 * communes : roues, cabine et chassis des poids lourds.
 */

const CARROSSERIE = 'fill="#ffffff" stroke="#d5d9de" stroke-width="1.5"';
const VITRE = 'fill="#2c3e50" opacity="0.85"';
const JAUNE = 'fill="#f0a202" stroke="#c98500" stroke-width="1.2"';
const SOMBRE = '#3b4148';

const ombre = (rx = 108) => `<ellipse cx="115" cy="102" rx="${rx}" ry="5" fill="rgba(0,0,0,0.12)" />`;

/** Roue posee sur le sol (y = 102). */
const roue = (x: number, r = 12) => {
  const cy = 102 - r;
  return `<circle cx="${x}" cy="${cy}" r="${r}" fill="#22272c" /><circle cx="${x}" cy="${cy}" r="${(r * 0.42).toFixed(1)}" fill="#c9cfd4" />`;
};
const roues = (xs: number[], r?: number) => xs.map((x) => roue(x, r)).join('');

/** Feux : avant a droite, arriere a gauche. */
const feux = (avantX: number, avantY: number, arriereX: number, arriereY: number) =>
  `<rect x="${avantX - 6}" y="${avantY}" width="6" height="5" rx="2" fill="#f0a202" />`
  + `<rect x="${arriereX}" y="${arriereY}" width="4" height="6" rx="1.5" fill="#d9452b" />`;

const ligne = (x1: number, y1: number, x2: number, y2: number, couleur = '#e4e7ea', largeur = 1.2) =>
  `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${couleur}" stroke-width="${largeur}" />`;

/** Cabine et chassis des poids lourds (cabine a droite, plateau de 4 a 154). */
const CHASSIS = `<rect x="4" y="80" width="200" height="8" fill="${SOMBRE}" />`;
const CABINE = `<path d="M158 26h38c5 0 9 3 11 7l12 22c1 2 2 4 2 6v19h-63z" ${CARROSSERIE} />`
  + `<path d="M166 32h28c3 0 5 2 6 4l9 17h-43z" ${VITRE} />`
  + `<rect x="160" y="58" width="12" height="3" rx="1.5" fill="#aab2ba" />`
  + `<rect x="214" y="70" width="8" height="6" rx="2" fill="#f0a202" />`;
const poidsLourd = (caisse: string, essieux = [30, 58, 184]) =>
  ombre() + caisse + CHASSIS + CABINE + roues(essieux);

const caisseFourgon = (fond = '#f7f8f9') =>
  `<rect x="4" y="8" width="150" height="74" rx="3" fill="${fond}" stroke="#d5d9de" />`
  + [30, 56, 82, 108, 134].map((x) => ligne(x, 12, x, 78)).join('');

const voiture = (corps: string, vitres: string, essieux: number[], r = 13, extra = '') =>
  ombre(104) + extra + `<path d="${corps}" ${CARROSSERIE} />` + vitres + roues(essieux, r);

const vitres = (...chemins: string[]) => chemins.map((d) => `<path d="${d}" ${VITRE} />`).join('');

const DESSINS: Record<string, string> = {
  // ------------------------------------------------------------ voitures
  citadine: voiture(
    'M46 88V58q0-12 10-16l20-10q8-4 18-4h44q10 0 18 8l16 14 12 3q8 3 8 13v22z',
    vitres('M62 54l16-16q4-4 12-4h20v20z', 'M116 34h22q8 0 14 6l12 14h-48z'),
    [76, 166], 12,
  ) + ligne(113, 56, 113, 86) + feux(192, 64, 46, 58),

  berline: voiture(
    'M10 88V68q2-8 14-10l38-4 22-18q6-4 16-4h46q10 0 18 8l16 14 26 4q12 2 14 12v18z',
    vitres('M68 54l20-16q4-2 10-2h24v18z', 'M128 36h18q6 0 12 6l12 12h-42z'),
    [52, 180],
  ) + ligne(125, 56, 125, 86) + feux(220, 66, 10, 62),

  break: voiture(
    'M10 88V46q2-14 16-16h120q10 0 18 8l16 14 26 4q12 2 14 12v18z',
    vitres('M20 54l2-16q2-4 8-4h46v20z', 'M82 34h40v20h-40z', 'M128 34h18q6 0 12 6l12 14h-42z'),
    [52, 180],
  ) + ligne(125, 56, 125, 86) + feux(220, 66, 10, 50),

  coupe: voiture(
    'M10 88V70q2-8 16-10l44-4 30-16q6-2 14-2h26q10 0 18 8l18 10 32 4q12 2 12 12v16z',
    vitres('M80 56l22-12q4-2 10-2h26q8 0 14 6l10 8z'),
    [50, 182],
  ) + ligne(118, 58, 118, 86) + feux(220, 68, 10, 64),

  cabriolet: voiture(
    'M10 88V68q2-8 14-9h182q12 1 14 11v18z',
    `<path d="M152 59l-14-20h4l16 20z" ${VITRE} />`
      + `<rect x="98" y="44" width="14" height="16" rx="4" fill="#8a6d50" />`
      + `<rect x="64" y="44" width="14" height="16" rx="4" fill="#8a6d50" />`,
    [52, 180],
  ) + ligne(125, 62, 125, 86) + feux(220, 68, 10, 64),

  suv: voiture(
    'M12 88V44q2-14 18-16h120q10 0 18 8l20 14 20 4q12 2 12 12v22z',
    vitres('M24 50l2-14q2-4 10-4h44v18z', 'M86 32h40v18h-40z', 'M132 32h18q8 0 14 6l12 12h-44z'),
    [56, 182], 15,
    `<rect x="40" y="23" width="100" height="3" rx="1.5" fill="#8a94a0" />`,
  ) + ligne(129, 52, 129, 84) + feux(220, 62, 12, 50),

  '4x4': voiture(
    'M14 86V30q0-6 6-6h126q6 0 10 6l14 20 40 2q10 2 10 10v24z',
    vitres('M24 46V30h50v16z', 'M80 30h42v16h-42z', 'M128 30h18q4 0 6 4l10 12h-34z'),
    [58, 180], 16,
    `<rect x="30" y="18" width="110" height="4" rx="2" fill="${SOMBRE}" />`
      + `<path d="M164 34V14h8" fill="none" stroke="${SOMBRE}" stroke-width="4" />`
      + `<circle cx="10" cy="58" r="12" fill="#22272c" /><circle cx="10" cy="58" r="5" fill="#c9cfd4" />`,
  ) + ligne(125, 48, 125, 82) + feux(220, 58, 14, 40),

  pickup: voiture(
    'M110 86V30q0-6 8-6h38q6 0 10 6l14 20 28 4q12 2 12 12v20z',
    vitres('M118 46V31h20v15z', 'M144 31h12q4 0 6 4l8 11h-26z'),
    [50, 182], 15,
    `<path d="M10 86V54h100v32z" ${CARROSSERIE} />` + ligne(10, 58, 110, 58, '#d5d9de'),
  ) + feux(220, 62, 10, 58),

  monospace: voiture(
    'M12 88V42q2-14 18-16h100q12 0 22 8l38 18 18 4q12 2 12 12v20z',
    vitres('M22 50l2-16q2-4 10-4h36v20z', 'M76 30h40v20h-40z', 'M122 30h10q8 0 16 6l20 14h-46z'),
    [52, 178],
  ) + ligne(119, 52, 119, 86) + ligne(74, 52, 74, 86) + feux(220, 64, 12, 52),

  // ------------------------------------------------------------ utilitaires
  fourgonnette: voiture(
    'M12 88V28q0-6 8-6h116q10 0 16 8l24 22 28 4q12 2 12 12v20z',
    vitres('M118 50V30h18q8 0 12 6l12 14z'),
    [50, 178],
    13,
  ) + `<rect x="24" y="54" width="80" height="6" rx="2" fill="#f0a202" opacity="0.85" />`
    + ligne(114, 30, 114, 86) + feux(216, 64, 12, 48),

  'fourgon-leger': voiture(
    'M8 88V18q0-6 8-6h144q8 0 12 6l10 22 24 12q14 4 14 14v22z',
    vitres('M138 18h18v22h-18z', 'M160 18h6q4 0 6 4l8 18h-20z'),
    [44, 184],
  ) + `<rect x="18" y="52" width="110" height="6" rx="2" fill="#1f6fd1" opacity="0.8" />`
    + ligne(134, 18, 134, 86) + feux(220, 62, 8, 44),

  minibus: voiture(
    'M8 88V18q0-6 8-6h144q8 0 12 6l10 22 24 12q14 4 14 14v22z',
    [20, 44, 68, 92, 116].map((x) => `<rect x="${x}" y="20" width="18" height="20" rx="2" ${VITRE} />`).join('')
      + vitres('M140 18h18v22h-18z', 'M160 18h6q4 0 6 4l8 18h-20z'),
    [44, 184],
  ) + `<rect x="8" y="56" width="174" height="5" fill="#f0a202" opacity="0.85" />` + feux(220, 62, 8, 44),

  bus: voiture(
    'M6 88V16q0-8 8-8h198q10 0 10 10v70z',
    [14, 42, 70, 98, 126].map((x) => `<rect x="${x}" y="16" width="22" height="28" rx="2" ${VITRE} />`).join('')
      + vitres('M196 16h18q4 0 4 4v32h-22z')
      + `<rect x="160" y="16" width="30" height="66" rx="2" fill="#2c3e50" opacity="0.55" />`
      + `<rect x="198" y="9" width="18" height="5" rx="1" fill="#f0a202" />`,
    [42, 196], 13,
  ) + `<rect x="6" y="54" width="150" height="6" fill="#1f6fd1" />`,

  autocar: voiture(
    'M6 88V18q0-10 10-10h188q12 0 16 12l4 18v50z',
    `<rect x="14" y="14" width="176" height="28" rx="3" ${VITRE} />`
      + [44, 74, 104, 134, 164].map((x) => ligne(x, 14, x, 42, '#ffffff', 2)).join('')
      + vitres('M196 14h8q8 0 10 8l4 26h-22z')
      + [30, 74, 118].map((x) => `<rect x="${x}" y="60" width="38" height="20" rx="2" fill="none" stroke="#d5d9de" />`).join(''),
    [44, 70, 194], 13,
  ) + `<rect x="6" y="48" width="218" height="5" fill="#d9452b" />`,

  // ------------------------------------------------------------ poids lourds
  plateau: poidsLourd(
    `<rect x="4" y="70" width="150" height="10" fill="#a07c55" />`
      + [10, 40, 70, 100, 130].map((x) => `<rect x="${x}" y="62" width="4" height="8" fill="#7d5e3f" />`).join('')
      + `<rect x="20" y="40" width="44" height="30" fill="#e6c79c" stroke="#c9a26b" />`
      + `<rect x="70" y="48" width="38" height="22" fill="#e6c79c" stroke="#c9a26b" />`
      + ligne(42, 40, 42, 70, '#c9a26b') + ligne(89, 48, 89, 70, '#c9a26b'),
  ),

  benne: poidsLourd(
    `<path d="M10 34Q60 18 110 28Q132 22 150 34Z" fill="#d9b26f" />`
      + `<path d="M4 34H154L150 80H12Z" ${JAUNE} />`
      + [40, 78, 116].map((x) => ligne(x, 36, x - 1, 78, '#c98500', 2)).join(''),
  ),

  citerne: poidsLourd(
    `<rect x="6" y="30" width="148" height="48" rx="24" fill="#e9edf1" stroke="#b6bec6" stroke-width="1.5" />`
      + ligne(54, 31, 54, 77, '#b6bec6') + ligne(104, 31, 104, 77, '#b6bec6')
      + `<rect x="6" y="50" width="148" height="6" fill="#f0a202" opacity="0.85" />`
      + `<rect x="60" y="24" width="38" height="6" rx="2" fill="#aab2ba" />`,
  ),

  'porte-conteneur': poidsLourd(
    `<rect x="4" y="16" width="150" height="62" fill="#1f6fd1" stroke="#17539c" stroke-width="1.5" />`
      + Array.from({ length: 17 }, (_, i) => ligne(12 + i * 8.4, 20, 12 + i * 8.4, 74, '#17539c', 1.6)).join('')
      + `<rect x="4" y="78" width="150" height="3" fill="${SOMBRE}" />`,
  ),

  fourgon: poidsLourd(caisseFourgon()),

  frigorifique: poidsLourd(
    caisseFourgon('#f4f9ff')
      + `<rect x="136" y="2" width="18" height="24" rx="2" fill="#8a94a0" />`
      + `<g stroke="#1f6fd1" stroke-width="2.2" stroke-linecap="round">`
      + `<line x1="64" y1="32" x2="64" y2="58" /><line x1="52" y1="38" x2="76" y2="52" /><line x1="52" y1="52" x2="76" y2="38" /></g>`,
  ),

  betaillere: poidsLourd(
    `<rect x="4" y="14" width="150" height="66" rx="3" fill="#f7f8f9" stroke="#d5d9de" />`
      + [22, 36, 50, 64].map((y) => `<rect x="12" y="${y}" width="134" height="5" rx="2" fill="${SOMBRE}" opacity="0.55" />`).join(''),
  ),

  malaxeur: poidsLourd(
    `<path d="M14 50Q14 24 60 18L128 26Q150 30 150 50Q150 70 128 74L60 82Q14 76 14 50Z" fill="#e9edf1" stroke="#b6bec6" stroke-width="1.5" />`
      + `<path d="M40 22L70 80M70 19L100 78M100 22L126 74" stroke="#f0a202" stroke-width="5" fill="none" />`
      + `<path d="M14 50L4 66" stroke="${SOMBRE}" stroke-width="4" />`,
  ),

  'porte-engins': ombre() + `<rect x="4" y="74" width="150" height="8" fill="${SOMBRE}" />`
    + `<path d="M4 74L-4 84H4z" fill="${SOMBRE}" />`
    + `<rect x="44" y="64" width="62" height="10" rx="5" fill="${SOMBRE}" />`
    + `<rect x="52" y="44" width="46" height="20" rx="3" ${JAUNE} />`
    + `<rect x="76" y="26" width="22" height="20" rx="2" ${JAUNE} />`
    + `<rect x="80" y="30" width="14" height="10" ${VITRE} />`
    + `<path d="M60 46L38 22L18 42" fill="none" stroke="#c98500" stroke-width="6" stroke-linejoin="round" />`
    + `<path d="M12 40h14l-2 14H14z" fill="${SOMBRE}" />`
    + CHASSIS + CABINE + roues([30, 58, 184]),

  grue: ombre() + `<rect x="100" y="58" width="50" height="22" rx="2" ${JAUNE} />`
    + `<path d="M120 60L8 18L8 28L120 70Z" ${JAUNE} />`
    + ligne(10, 26, 10, 60, SOMBRE, 1.5) + `<path d="M6 60h8v6q-4 6-8 0z" fill="${SOMBRE}" />`
    + `<rect x="110" y="40" width="22" height="20" rx="2" ${JAUNE} /><rect x="114" y="44" width="14" height="9" ${VITRE} />`
    + CHASSIS + CABINE + roues([30, 58, 184]),

  tracteur: ombre(80) + `<rect x="110" y="80" width="110" height="8" fill="${SOMBRE}" />`
    + `<rect x="120" y="74" width="30" height="6" fill="${SOMBRE}" />`
    + `<rect x="150" y="10" width="4" height="30" fill="#8a94a0" />`
    + CABINE + roues([126, 152, 192]),

  remorque: ombre() + `<rect x="8" y="12" width="196" height="66" rx="3" fill="#f7f8f9" stroke="#d5d9de" />`
    + [40, 72, 104, 136, 168].map((x) => ligne(x, 16, x, 74)).join('')
    + `<rect x="8" y="78" width="196" height="6" fill="${SOMBRE}" />`
    + `<rect x="176" y="84" width="5" height="14" fill="${SOMBRE}" /><rect x="170" y="98" width="17" height="4" fill="${SOMBRE}" />`
    + roues([30, 56, 82]),

  depanneuse: poidsLourd(
    `<rect x="4" y="68" width="150" height="12" fill="#aab2ba" />`
      + `<path d="M24 68L10 40" stroke="#f0a202" stroke-width="7" stroke-linecap="round" />`
      + ligne(10, 42, 10, 64, SOMBRE, 1.5) + `<path d="M6 64h8v5q-4 5-8 0z" fill="${SOMBRE}" />`
      + `<rect x="180" y="20" width="14" height="6" rx="2" fill="#f0a202" />`,
  ),

  // ------------------------------------------------------------ engins
  pelleteuse: ombre(96) + `<rect x="36" y="78" width="124" height="22" rx="11" fill="${SOMBRE}" />`
    + [50, 76, 102, 128, 146].map((x) => `<circle cx="${x}" cy="89" r="6" fill="#6b7684" />`).join('')
    + `<rect x="52" y="50" width="104" height="28" rx="4" ${JAUNE} />`
    + `<rect x="118" y="18" width="38" height="34" rx="3" ${JAUNE} /><rect x="124" y="24" width="26" height="18" ${VITRE} />`
    + `<path d="M84 56L54 14L62 10L94 52Z" ${JAUNE} />`
    + `<path d="M54 14L22 54L30 58L62 18Z" ${JAUNE} />`
    + `<path d="M12 54h26l-4 20H16z" fill="${SOMBRE}" />`,

  chargeuse: ombre(100) + `<rect x="30" y="48" width="132" height="34" rx="4" ${JAUNE} />`
    + `<rect x="62" y="16" width="44" height="34" rx="3" ${JAUNE} /><rect x="68" y="22" width="32" height="18" ${VITRE} />`
    + `<rect x="34" y="34" width="20" height="14" rx="2" fill="${SOMBRE}" />`
    + `<path d="M150 56L196 60L196 70L150 68Z" ${JAUNE} />`
    + `<path d="M194 46L222 48L222 84L198 80Z" fill="${SOMBRE}" />`
    + roues([62, 140], 20),

  bulldozer: ombre(96) + `<rect x="28" y="74" width="136" height="26" rx="13" fill="${SOMBRE}" />`
    + [44, 72, 100, 128, 150].map((x) => `<circle cx="${x}" cy="87" r="7" fill="#6b7684" />`).join('')
    + `<rect x="38" y="46" width="110" height="28" rx="3" ${JAUNE} />`
    + `<rect x="48" y="16" width="44" height="32" rx="3" ${JAUNE} /><rect x="54" y="22" width="32" height="18" ${VITRE} />`
    + `<rect x="120" y="28" width="6" height="18" fill="${SOMBRE}" />`
    + `<path d="M148 66H172" stroke="${SOMBRE}" stroke-width="6" />`
    + `<path d="M170 36Q186 40 188 56V100L168 98V38Z" fill="#c98500" stroke="#a36c00" stroke-width="1.2" />`,

  chariot: ombre(90) + `<rect x="36" y="44" width="34" height="46" rx="8" fill="${SOMBRE}" />`
    + `<rect x="62" y="52" width="94" height="36" rx="4" ${JAUNE} />`
    + `<path d="M92 52V16h52v36" fill="none" stroke="${SOMBRE}" stroke-width="4" />`
    + `<rect x="160" y="6" width="9" height="92" fill="${SOMBRE}" />`
    + `<rect x="169" y="94" width="48" height="4" fill="${SOMBRE}" />`
    + `<rect x="172" y="72" width="44" height="6" fill="#a07c55" /><rect x="176" y="40" width="38" height="32" fill="#e6c79c" stroke="#c9a26b" />`
    + roues([82, 146], 12),
};
DESSINS['autre'] = DESSINS['fourgon'];

/** Dessins connus, pour les tests et le referentiel. */
export const DESSINS_VEHICULE = Object.keys(DESSINS);

/**
 * Dessin de profil d'un vehicule selon son type (berline, SUV, benne...).
 * Sans type connu, c'est le porteur fourgon d'origine.
 */
@Component({
  selector: 'app-dessin-vehicule',
  host: { '[innerHTML]': 'svg()', 'aria-hidden': 'true' },
  template: '',
  styles: `
    :host { display: block; }
    :host ::ng-deep svg { display: block; width: 100%; height: auto; overflow: visible; }
  `
})
export class DessinVehicule {
  private readonly sanitizer = inject(DomSanitizer);
  readonly dessin = input<string | null | undefined>(null);

  /** Les dessins sont des constantes du code : aucune donnee saisie n'y entre. */
  readonly svg = computed<SafeHtml>(() => {
    const contenu = DESSINS[this.dessin() ?? ''] ?? DESSINS['autre'];
    return this.sanitizer.bypassSecurityTrustHtml(`<svg viewBox="0 0 230 110">${contenu}</svg>`);
  });
}
