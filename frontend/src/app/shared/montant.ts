import { formatNumber } from '@angular/common';

/**
 * Montant affiche en grand corps. Le format francais separe les milliers par
 * une espace fine insecable (U+202F) qui se perd a cette taille ; l'espace
 * insecable ordinaire (U+00A0) se laisse elargir par word-spacing.
 */
export function grosMontant(valeur: number, locale: string): string {
  return formatNumber(valeur, locale, '1.0-0').replace(/ /g, ' ');
}
