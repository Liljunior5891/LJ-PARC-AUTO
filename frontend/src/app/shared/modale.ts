import { Component, OnDestroy, OnInit, input, output } from '@angular/core';

/**
 * Fenetre posee par-dessus la page, pour les formulaires d'enregistrement.
 * <p>
 * Le formulaire est projete tel quel : il garde sa soumission et ses boutons,
 * dont la barre d'actions reste visible en bas pendant le defilement. La
 * fenetre se ferme par la croix, la touche Echap ou un clic a cote.
 * <p>
 * Ce n'est pas un element dialog natif : la couche superieure du navigateur
 * passerait devant les listes Select2, accrochees au body (z-index 1051).
 */
@Component({
  selector: 'app-modale',
  host: { '(document:keydown.escape)': 'ferme.emit()' },
  template: `
    <div class="voile-modale" (click)="ferme.emit()"></div>
    <section class="modale" role="dialog" aria-modal="true" [attr.aria-label]="titre()"
             [style.--largeur]="largeur()">
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
