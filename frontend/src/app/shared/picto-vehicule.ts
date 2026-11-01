/*
 * Pictogrammes des reperes de la carte GPS (24 x 24, couleur du texte) : une
 * silhouette par famille de dessins, lisible a la taille d'un repere.
 */

const ROUES = 'M6.5 19a1.8 1.8 0 1 1 0-3.6 1.8 1.8 0 0 1 0 3.6zm11 0a1.8 1.8 0 1 1 0-3.6 1.8 1.8 0 0 1 0 3.6z';

const picto = (d: string) =>
  `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" fill-rule="evenodd" d="${d}"/></svg>`;

const PICTOS = {
  voiture: picto('M4 11l2.2-3.9A2 2 0 0 1 8 6h7a2 2 0 0 1 1.5.7L19.5 11H20a2 2 0 0 1 2 2v3h-2.2a2.5 2.5 0 0 0-4.6 0H8.8a2.5 2.5 0 0 0-4.6 0H2v-3a2 2 0 0 1 2-2zm3.3-.2h4.2V7.8H9zm6 0h4l-1.9-2.5a1 1 0 0 0-.8-.5H13.3z' + ROUES),
  'tout-terrain': picto('M3 6.5A1.5 1.5 0 0 1 4.5 5h11.2a1.5 1.5 0 0 1 1.2.6L19.5 9H21a1 1 0 0 1 1 1v6h-2.2a2.5 2.5 0 0 0-4.6 0H8.8a2.5 2.5 0 0 0-4.6 0H3zm2 2.5h4V7H5zm6 0h6.6l-1.4-2H11z' + ROUES),
  utilitaire: picto('M2 6a1 1 0 0 1 1-1h12.5a1 1 0 0 1 .8.4l3.2 4.1 1.8.7a1 1 0 0 1 .7.9V16h-2.2a2.5 2.5 0 0 0-4.6 0H8.8a2.5 2.5 0 0 0-4.6 0H2zm13 4h3.4L16 7h-1z' + ROUES),
  bus: picto('M2 5a1 1 0 0 1 1-1h17a2 2 0 0 1 2 2v10h-2.2a2.5 2.5 0 0 0-4.6 0H8.8a2.5 2.5 0 0 0-4.6 0H2zm2 2v4h3V7zm4.5 0v4h3V7zm4.5 0v4h3V7zm4.5 0v5H21V7z' + ROUES),
  camion: picto('M3 6h11v9H3zM15 9h3.5l2.5 3.2V15h-6z' + ROUES),
  engin: picto('M2 17.5A2.5 2.5 0 0 1 4.5 15h14a2.5 2.5 0 0 1 0 5h-14A2.5 2.5 0 0 1 2 17.5zM9 14V9h4V4h6a1 1 0 0 1 1 1v9zM15 9.5h3.5V6H15zM9.6 13L4.6 5.2 1 10.8h3l1.4-2.1 3 4.3z')
};

/** Famille de pictogramme de chaque dessin de carte ; le reste est un camion. */
const FAMILLE: Record<string, keyof typeof PICTOS> = {
  citadine: 'voiture', berline: 'voiture', break: 'voiture', coupe: 'voiture', cabriolet: 'voiture', monospace: 'voiture',
  suv: 'tout-terrain', '4x4': 'tout-terrain', pickup: 'tout-terrain',
  fourgonnette: 'utilitaire', 'fourgon-leger': 'utilitaire', minibus: 'utilitaire',
  bus: 'bus', autocar: 'bus',
  pelleteuse: 'engin', chargeuse: 'engin', bulldozer: 'engin', chariot: 'engin'
};

/** Pictogramme du repere d'un vehicule, selon le dessin de son type. */
export function pictoVehicule(dessin: string | null | undefined): string {
  return PICTOS[FAMILLE[dessin ?? ''] ?? 'camion'];
}
