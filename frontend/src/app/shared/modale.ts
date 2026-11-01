import { Component, OnDestroy, OnInit, input, output } from '@angular/core';

/**
 * Fenetre posee par-dessus la page, pour les formulaires d'enregistrement.
 * <p>
 * Le formulaire est projete tel quel : il garde sa soumission et ses boutons,
 * dont la barre d'actions reste visible en bas pendant le defilement. La
 * fenetre ne se ferme que par sa croix ou par le bouton Annuler du formulaire :
 * ni un clic a cote ni la touche Echap ne font perdre une saisie.
 * <p>
 * Ce n'est pas un element dialog natif : la couche superieure du navigateur
 * passerait devant les listes Select2, accrochees au body (z-index 1051).
 */
@Component({
  selector: 'app-modale',
  template: `
    <div class="voile-modale"></div>
    <section class="modale" role="dialog" aria-modal="true" [attr.aria-label]="titre()"
             [style.--largeur]="largeur()" [class.grande]="grande()">
      <header class="entete-modale">
        <div>
          <h2>{{ titre() }}</h2>
          @if (sousTitre()) {
            <p>{{ sousTitre() }}</p>
          }
        </div>
        <button class="fermer" type="button" (click)="ferme.emit()" aria-label="Fermer">×</button>
      </header>
      @if (erreur()) {
        <div class="message erreur">{{ erreur() }}</div>
      }
      <div class="corps-modale">
        <ng-content />
      </div>
    </section>
  `,
  styleUrl: './modale.scss'
})
export class Modale implements OnInit, OnDestroy {
  readonly titre = input.required<string>();
  readonly sousTitre = input<string>();
  /** Largeur maximale de la fenetre. */
  readonly largeur = input('880px');
  /** Toute la hauteur de l'ecran : pour les saisies de longues listes (commande fournisseur). */
  readonly grande = input(false);
  /** Erreur de l'ecran appelant, repetee ici : le message de la page est sous le voile. */
  readonly erreur = input<string | null>(null);
  readonly ferme = output<void>();

  /** La page ne defile plus sous la fenetre. */
  ngOnInit(): void {
    document.body.classList.add('modale-ouverte');
  }

  ngOnDestroy(): void {
    document.body.classList.remove('modale-ouverte');
  }
}
