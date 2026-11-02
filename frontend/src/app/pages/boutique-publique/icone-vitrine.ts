import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Pictogrammes au trait des pages publiques (place de marche, boutique). */
const ICONES: Record<string, string> = {
  recherche: 'M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM21 21l-4.3-4.3',
  panier: 'M2 3h2.5l2.4 11.2a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 2-1.5L21 7H6M9 21a1 1 0 1 0 0-2a1 1 0 0 0 0 2zM18 21a1 1 0 1 0 0-2a1 1 0 0 0 0 2z',
  boutique: 'M3 9l1.5-5h15L21 9M4 9v11h16V9M3 9h18M9 20v-6h6v6',
  livraison: 'M2 6h12v10H2zM14 9h4l3 3v4h-7M6 19a2 2 0 1 0 0-4a2 2 0 0 0 0 4zM17 19a2 2 0 1 0 0-4a2 2 0 0 0 0 4z',
  mobile: 'M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM11 18h2',
  bouclier: 'M12 3l8 3v6c0 5-3.5 8-8 9c-4.5-1-8-4-8-9V6zM9 12l2 2l4-4',
  personne: 'M12 12a4 4 0 1 0 0-8a4 4 0 0 0 0 8zM4 21c0-4 4-6 8-6s8 2 8 6',
  horloge: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2',
  position: 'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12zM12 11.5a2.5 2.5 0 1 0 0-5a2.5 2.5 0 0 0 0 5z',
  telephone: 'M5 4h4l2 5l-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2',
  fleche: 'M5 12h14M13 6l6 6l-6 6',
  retour: 'M19 12H5M11 6l-6 6l6 6',
  etiquette: 'M3 12V4a1 1 0 0 1 1-1h8l9 9l-9 9zM7.5 7.5h.01',
  voiture: 'M5 17h14v-5l-2-5H7l-2 5zM5 12h14M7 17v2M17 17v2M8 14.5h.01M16 14.5h.01',
  grille: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
  photo: 'M4 8h3l2-3h6l2 3h3v11H4zM12 17a4 4 0 1 0 0-8a4 4 0 0 0 0 8z',
  suivi: 'M9 11l3 3l8-8M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9',
  cadenas: 'M6 11h12v10H6zM8 11V7a4 4 0 0 1 8 0v4',
  plus: 'M12 5v14M5 12h14'
};

@Component({
  selector: 'app-icone-vitrine',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="chemin()" /></svg>`,
  styles: `
    :host { display: inline-flex; width: 1.15em; height: 1.15em; flex: none; }
    svg { width: 100%; height: 100%; fill: none; stroke: currentColor; stroke-width: 1.8;
      stroke-linecap: round; stroke-linejoin: round; }
  `
})
export class IconeVitrine {
  readonly nom = input.required<string>();
  readonly chemin = computed(() => ICONES[this.nom()] ?? '');
}
