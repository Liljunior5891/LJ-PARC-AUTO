import { Component, input, output } from '@angular/core';

/**
 * Caissier a qui aucune caisse n'est affectee : a la place de l'ecran de caisse,
 * un ecran plein (comme le verrou PIN) l'invite a voir son gerant. La relecture
 * est automatique : il disparait des que le gerant lui affecte une caisse.
 */
@Component({
  selector: 'app-sans-caisse',
  template: `
    <div class="ecran" role="dialog" aria-modal="true" aria-labelledby="titre-sans-caisse">
      <div class="boite">
        <span class="icone" aria-hidden="true">🧾</span>
        <h1 id="titre-sans-caisse">Aucune caisse affectee</h1>
        <p class="qui">{{ nom() }}</p>
        <p class="message">
          Aucune caisse ne vous a encore ete affectee. Demandez a votre gerant de vous affecter une caisse :
          cet ecran s'ouvrira de lui-meme des que ce sera fait.
        </p>
        <button type="button" class="principal" [disabled]="verification()" (click)="reverifier.emit()">
          {{ verification() ? 'Verification...' : 'Verifier a nouveau' }}
        </button>
        <button type="button" class="lien" (click)="deconnexion.emit()">Changer d'utilisateur</button>
      </div>
    </div>
  `,
  styles: `
    .ecran {
      position: fixed;
      inset: 0;
      z-index: 2100;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      background: radial-gradient(circle at 30% 20%, #1f6b52, #0f2b22 70%);
    }

    .boite {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.7rem;
      width: min(400px, 100%);
      padding: 1.8rem 1.5rem;
      border-radius: 22px;
      background: #fff;
      box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
      text-align: center;
    }

    .icone { font-size: 2.4rem; }
    h1 { margin: 0; font-size: 1.25rem; }
    .qui { margin: 0; color: var(--couleur-texte-doux); font-size: 0.9rem; }
    .message { margin: 0.2rem 0 0.4rem; color: var(--couleur-texte); line-height: 1.5; }

    .principal {
      width: 100%;
      height: 48px;
      border: none;
      border-radius: 14px;
      background: #1e9e4a;
      color: #fff;
      font-size: 1rem;
      font-weight: 700;
      cursor: pointer;

      &:disabled { opacity: 0.6; }
    }

    .lien {
      border: none;
      background: none;
      color: var(--couleur-primaire);
      font-weight: 600;
      cursor: pointer;
    }
  `
})
export class SansCaisse {
  readonly nom = input('');
  readonly verification = input(false);
  readonly reverifier = output<void>();
  readonly deconnexion = output<void>();
}
