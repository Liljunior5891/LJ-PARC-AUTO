import { Directive, ElementRef, OnDestroy, OnInit, inject, input } from '@angular/core';

/**
 * Revele un element lorsqu'il entre dans la fenetre, une seule fois.
 * <p>
 * L'observation est preferee a un ecouteur de defilement : le navigateur ne
 * reveille le code que lorsque l'element franchit le seuil, et l'element est
 * relache des qu'il est apparu. Si l'utilisateur a demande a reduire les
 * animations, la classe est posee immediatement et rien n'est observe.
 */
@Directive({
  selector: '[appApparition]'
})
export class Apparition implements OnInit, OnDestroy {
  private readonly element = inject(ElementRef<HTMLElement>);

  /** Retard applique a l'apparition, en millisecondes, pour echelonner une grille. */
  readonly retardApparition = input(0);

  private observateur?: IntersectionObserver;

  ngOnInit(): void {
    const noeud = this.element.nativeElement as HTMLElement;
    noeud.classList.add('a-reveler');
    noeud.style.transitionDelay = `${this.retardApparition()}ms`;

    const animationsReduites = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (animationsReduites || typeof IntersectionObserver === 'undefined') {
      noeud.classList.add('revele');
      return;
    }

    this.observateur = new IntersectionObserver(
      (entrees) => {
        for (const entree of entrees) {
          if (entree.isIntersecting) {
            noeud.classList.add('revele');
            this.deconnecter();
          }
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -40px 0px' }
    );
    this.observateur.observe(noeud);
  }

  ngOnDestroy(): void {
    this.deconnecter();
  }

  private deconnecter(): void {
    this.observateur?.disconnect();
    this.observateur = undefined;
  }
}
