import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { AuthService } from '../core/auth/auth.service';
import { VerrouService } from '../core/services/verrou.service';

/**
 * Ecran de verrouillage de la caisse : le caissier saisit son code PIN sur
 * un pave numerique (clavier physique accepte). Sans code defini, il en cree
 * un, saisi deux fois.
 */
@Component({
  selector: 'app-verrou-caisse',
  host: { '(document:keydown)': 'clavier($event)' },
  template: `
    <div class="verrou" role="dialog" aria-modal="true" aria-label="Caisse verrouillee">
      <div class="boite">
        <span class="cadenas" aria-hidden="true">🔒</span>
        <h1>{{ titre() }}</h1>
        <p class="qui">{{ utilisateur()?.nomComplet }} · {{ utilisateur()?.entrepriseNom }}</p>

        <div class="points" [class.erreur]="!!erreur()" aria-live="polite">
          @for (i of [0, 1, 2, 3, 4, 5]; track i) {
            <span [class.plein]="i < saisie().length" [class.optionnel]="i >= 4"></span>
          }
        </div>
        <p class="message" [class.erreur]="!!erreur()">{{ erreur() || aide() }}</p>

        <div class="pave">
          @for (t of touches; track t) {
            <button type="button" (click)="taper(t)" [attr.aria-label]="t">{{ t }}</button>
          }
          <button type="button" class="secondaire" (click)="effacer()" aria-label="Effacer">⌫</button>
          <button type="button" (click)="taper('0')" aria-label="0">0</button>
          <button type="button" class="valider" [disabled]="saisie().length < 4 || envoi()" (click)="valider()"
                  aria-label="Valider">✓</button>
        </div>

        <button type="button" class="lien" (click)="deconnexion.emit()">Changer d'utilisateur</button>
      </div>
    </div>
  `,
  styles: `
    .verrou {
      position: fixed;
      inset: 0;
      z-index: 2000;
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
      gap: 0.6rem;
      width: min(360px, 100%);
      padding: 1.6rem 1.4rem;
      border-radius: 22px;
      background: #fff;
      box-shadow: 0 24px 60px rgba(0, 0, 0, 0.35);
      text-align: center;
    }

    .cadenas {
      font-size: 2.2rem;
    }

    h1 {
      margin: 0;
      font-size: 1.25rem;
    }

    .qui {
      margin: 0;
      color: var(--couleur-texte-doux);
      font-size: 0.9rem;
    }

    .points {
      display: flex;
      gap: 0.6rem;
      margin: 0.6rem 0 0.2rem;

      span {
        width: 16px;
        height: 16px;
        border: 2px solid #1e9e4a;
        border-radius: 50%;

        &.optionnel {
          border-style: dashed;
          opacity: 0.6;
        }

        &.plein {
          background: #1e9e4a;
          opacity: 1;
        }
      }

      &.erreur span {
        border-color: var(--couleur-danger);
        animation: secousse 0.3s;
      }
    }

    @keyframes secousse {
      25% { transform: translateX(-4px); }
      75% { transform: translateX(4px); }
    }

    .message {
      min-height: 1.3em;
      margin: 0;
      color: var(--couleur-texte-doux);
      font-size: 0.85rem;

      &.erreur {
        color: var(--couleur-danger);
        font-weight: 600;
      }
    }

    .pave {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.6rem;
      width: 100%;
      margin-top: 0.4rem;

      button {
        height: 62px;
        border: 1px solid var(--couleur-bordure);
        border-radius: 16px;
        background: #f3f6f4;
        font-size: 1.5rem;
        font-weight: 700;
        cursor: pointer;

        &:active {
          transform: scale(0.96);
        }

        &.secondaire {
          font-size: 1.2rem;
        }

        &.valider {
          border-color: #1e9e4a;
          background: #1e9e4a;
          color: #fff;

          &:disabled {
            opacity: 0.4;
          }
        }
      }
    }

    .lien {
      margin-top: 0.4rem;
      border: none;
      background: none;
      color: var(--couleur-primaire);
      font-weight: 600;
      cursor: pointer;
    }
  `
})
export class VerrouCaisse implements OnInit {
  private readonly verrou = inject(VerrouService);
  readonly utilisateur = inject(AuthService).utilisateur;
  readonly deconnexion = output<void>();

  readonly touches = ['1', '2', '3', '4', '5', '6', '7', '8', '9'];
  readonly saisie = signal('');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  /** Nul tant qu'on ne sait pas si le caissier a deja un code. */
  readonly defini = signal<boolean | null>(null);
  /** Creation d'un code : premiere saisie, a confirmer. */
  readonly premiere = signal<string | null>(null);

  readonly titre = computed(() => this.defini() === false
    ? (this.premiere() ? 'Confirmez votre code' : 'Creez votre code PIN')
    : 'Caisse verrouillee');
  readonly aide = computed(() => this.defini() === false
    ? 'De 4 a 6 chiffres, a garder pour vous'
    : 'Saisissez votre code PIN');

  ngOnInit(): void {
    this.verrou.etat().subscribe({
      next: (e) => this.defini.set(e.defini),
      error: () => this.defini.set(true)
    });
  }

  clavier(evenement: KeyboardEvent): void {
    if (/^\d$/.test(evenement.key)) {
      this.taper(evenement.key);
    } else if (evenement.key === 'Backspace') {
      this.effacer();
    } else if (evenement.key === 'Enter') {
      this.valider();
    }
  }

  taper(chiffre: string): void {
    this.erreur.set(null);
    if (this.saisie().length < 6) {
      this.saisie.update((s) => s + chiffre);
    }
  }

  effacer(): void {
    this.erreur.set(null);
    this.saisie.update((s) => s.slice(0, -1));
  }

  valider(): void {
    const pin = this.saisie();
    if (pin.length < 4 || this.envoi()) {
      return;
    }
    if (this.defini() === false) {
      if (!this.premiere()) {
        this.premiere.set(pin);
        this.saisie.set('');
        return;
      }
      if (this.premiere() !== pin) {
        this.premiere.set(null);
        this.saisie.set('');
        this.erreur.set('Les deux codes ne correspondent pas : recommencez');
        return;
      }
      this.envoyer(this.verrou.creer(pin));
      return;
    }
    this.envoyer(this.verrou.verifier(pin));
  }

  private envoyer(appel: ReturnType<VerrouService['verifier']>): void {
    this.envoi.set(true);
    appel.subscribe({
      next: () => this.envoi.set(false),
      error: (e) => {
        this.envoi.set(false);
        this.saisie.set('');
        const message = (e as { error?: { message?: string } })?.error?.message ?? 'Code PIN incorrect';
        this.erreur.set(message);
        if (message.includes('reconnectez')) {
          setTimeout(() => this.deconnexion.emit(), 1800);
        }
      }
    });
  }
}
