import { DestroyRef } from '@angular/core';

/** Hauteur en dessous de laquelle un tableau n'est plus reduit : la page defile alors un peu. */
const HAUTEUR_MINIMALE = 280;
/** Air laisse sous la carte du tableau, jusqu'au bas de la fenetre. */
const MARGE_BASSE = 24;

/**
 * Defilement interne des tableaux, sur tous les ecrans.
 * <p>
 * Chaque `.tableau-conteneur` recoit la hauteur qui reste dans la fenetre sous
 * ce qui le precede (filtres, totaux), moins ce qui le suit dans sa carte
 * (pagination) : la page tient a l'ecran et seules les lignes defilent, sous un
 * entete de colonnes fige. La hauteur est recalculee quand le contenu change
 * (navigation, chargement des donnees, onglet de fiche) et quand la fenetre est
 * redimensionnee. Dans une fenetre modale, la regle CSS par defaut s'applique.
 */
export function activerDefilementTableaux(racine: HTMLElement, destroyRef: DestroyRef): void {
  let demande = 0;
  const planifier = () => {
    if (!demande) {
      demande = requestAnimationFrame(() => {
        demande = 0;
        ajuster(racine);
      });
    }
  };

  const observateur = new MutationObserver(planifier);
  observateur.observe(racine, { childList: true, subtree: true, characterData: true });
  const redimensionnement = new ResizeObserver(planifier);
  redimensionnement.observe(racine);
  window.addEventListener('resize', planifier);
  planifier();

  destroyRef.onDestroy(() => {
    observateur.disconnect();
    redimensionnement.disconnect();
    window.removeEventListener('resize', planifier);
    cancelAnimationFrame(demande);
  });
}

function ajuster(racine: HTMLElement): void {
  racine.querySelectorAll<HTMLElement>('.tableau-conteneur').forEach((conteneur) => {
    if (conteneur.closest('.modale')) {
      return;
    }
    const haut = conteneur.getBoundingClientRect().top + window.scrollY;
    const hauteur = Math.max(HAUTEUR_MINIMALE, window.innerHeight - haut - espaceSous(conteneur) - MARGE_BASSE);
    const valeur = `${Math.floor(hauteur)}px`;
    if (conteneur.style.maxHeight !== valeur) {
      conteneur.style.maxHeight = valeur;
    }
  });
}

/** Ce qui suit le tableau dans sa carte : elements freres, puis marge interne du parent. */
function espaceSous(conteneur: HTMLElement): number {
  let espace = 0;
  for (let frere = conteneur.nextElementSibling; frere; frere = frere.nextElementSibling) {
    const style = getComputedStyle(frere);
    if (style.position !== 'absolute' && style.position !== 'fixed') {
      espace += (frere as HTMLElement).offsetHeight + parseFloat(style.marginTop) + parseFloat(style.marginBottom);
    }
  }
  const parent = conteneur.parentElement;
  if (parent) {
    const style = getComputedStyle(parent);
    espace += parseFloat(style.paddingBottom) + parseFloat(style.borderBottomWidth);
  }
  return espace;
}
