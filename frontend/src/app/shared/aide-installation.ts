import { Component, inject } from '@angular/core';
import { InstallationService } from '../core/services/installation.service';
import { Modale } from './modale';

/** Explication de l'installation sur iPhone et iPad, ou Safari n'a pas de bouton d'installation. */
@Component({
  selector: 'app-aide-installation',
  imports: [Modale],
  template: `
    @if (installation.aideIos()) {
      <app-modale titre="Installer LJ SaaS sur l'iPhone" largeur="460px" (ferme)="installation.aideIos.set(false)">
        <ol class="etapes">
          <li>Ouvrez cette page dans <strong>Safari</strong>.</li>
          <li>Touchez le bouton <strong>Partager</strong> <span class="symbole" aria-hidden="true">⎋</span>
            (le carre avec une fleche vers le haut), en bas ou en haut de l'ecran.</li>
          <li>Choisissez <strong>Sur l'ecran d'accueil</strong>, puis <strong>Ajouter</strong>.</li>
        </ol>
        <p class="aide">L'application s'ouvre ensuite en plein ecran depuis son icone, comme une application du store.</p>
        <div class="actions-formulaire">
          <button class="bouton" type="button" (click)="installation.aideIos.set(false)">J'ai compris</button>
        </div>
      </app-modale>
    }
  `,
  styles: `
    .etapes { margin: 0 0 1rem; padding-left: 1.2rem; line-height: 1.7; }
    .symbole { display: inline-block; transform: rotate(-90deg); }
    .aide { color: var(--couleur-texte-doux); font-size: .88rem; }
  `
})
export class AideInstallation {
  readonly installation = inject(InstallationService);
}
