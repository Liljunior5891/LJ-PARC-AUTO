import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationPdv, NotificationsPdvService } from '../core/services/notifications-pdv.service';

/** Pictogramme et couleur de chaque type d'evenement. */
const APPARENCES: Record<NotificationPdv['type'], { icone: string; couleur: string }> = {
  VALIDATION: { icone: '📱', couleur: '#7a4fd1' },
  BON: { icone: '🍳', couleur: '#e8870e' },
  PRET: { icone: '🔔', couleur: '#1f9d6b' },
  APPEL: { icone: '🙋', couleur: '#1f6fd1' },
  ADDITION: { icone: '🧾', couleur: '#d9452b' },
  CAISSE: { icone: '💰', couleur: '#0f9aa8' }
};

/**
 * Messages des notifications du point de vente, empiles en haut a droite :
 * un clic ouvre l'ecran concerne (la commande, l'ecran de preparation). Le
 * bouton de son coupe ou retablit les melodies.
 */
@Component({
  selector: 'app-notifications-pdv',
  template: `
    <div class="pile" aria-live="polite">
      @if (service.permission() === 'default' && !service.propositionRepoussee()) {
        <div class="message-pdv proposition" style="--accent: #1f6fd1">
          <span class="icone" aria-hidden="true">🔔</span>
          <div class="corps">
            <strong>Etre prevenu en arriere-plan</strong>
            <span>Recevez les commandes, bons et plats prets meme quand cet onglet n'est pas affiche.</span>
            <span class="choix">
              <button type="button" class="bouton petit succes" (click)="service.demanderPermissionSysteme()">Activer</button>
              <button type="button" class="bouton secondaire petit" (click)="service.repousserProposition()">Plus tard</button>
            </span>
          </div>
        </div>
      }
      @for (m of service.messages(); track m.cle) {
        <div class="message-pdv" [style.--accent]="apparence(m).couleur" role="status">
          <span class="icone" aria-hidden="true">{{ apparence(m).icone }}</span>
          <button type="button" class="corps" (click)="ouvrir(m)" [disabled]="!m.lien">
            <strong>{{ m.titre }}</strong>
            <span>{{ m.message }}</span>
          </button>
          <button type="button" class="fermer" (click)="service.fermer(m.cle)" aria-label="Fermer le message">×</button>
        </div>
      }
      @if (service.messages().length > 0) {
        <button type="button" class="son" (click)="service.basculerSon()"
                [attr.aria-label]="service.son() ? 'Couper le son des notifications' : 'Activer le son des notifications'">
          {{ service.son() ? '🔊 Son actif' : '🔇 Son coupe' }}
        </button>
      }
    </div>
  `,
  styles: `
    .pile {
      position: fixed; top: 70px; right: 16px; z-index: 1200;
      display: flex; flex-direction: column; align-items: flex-end; gap: 0.5rem;
      width: min(380px, calc(100vw - 32px)); pointer-events: none;
    }
    .message-pdv {
      display: flex; align-items: stretch; width: 100%; overflow: hidden; pointer-events: auto;
      border-left: 5px solid var(--accent); border-radius: 12px; background: #fff;
      box-shadow: 0 12px 30px rgba(15, 30, 50, 0.22); animation: arrivee 0.25s ease-out;
    }
    .icone {
      display: grid; place-items: center; width: 48px; flex-shrink: 0; font-size: 1.4rem;
      background: color-mix(in srgb, var(--accent) 12%, #fff);
    }
    .corps {
      display: flex; flex: 1; flex-direction: column; gap: 0.15rem; min-width: 0; padding: 0.65rem 0.75rem;
      border: none; background: none; font: inherit; text-align: left; cursor: pointer;
    }
    .corps:disabled { cursor: default; }
    .proposition .corps { cursor: default; }
    .choix { display: flex; gap: 0.4rem; margin-top: 0.4rem; }
    .corps strong { color: var(--accent); font-size: 0.9rem; }
    .corps span { color: var(--couleur-texte); font-size: 0.85rem; }
    .fermer { width: 34px; border: none; background: none; color: var(--couleur-texte-doux); font-size: 1.2rem; cursor: pointer; }
    .son {
      pointer-events: auto; padding: 0.25rem 0.7rem; border: 1px solid var(--couleur-bordure); border-radius: 999px;
      background: #fff; font: inherit; font-size: 0.75rem; cursor: pointer;
    }
    @keyframes arrivee { from { opacity: 0; transform: translateX(30px); } to { opacity: 1; transform: none; } }
    @media (prefers-reduced-motion: reduce) { .message-pdv { animation: none; } }
  `
})
export class NotificationsPdv implements OnInit, OnDestroy {
  readonly service = inject(NotificationsPdvService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.service.demarrer();
  }

  ngOnDestroy(): void {
    this.service.arreter();
  }

  apparence(m: NotificationPdv): { icone: string; couleur: string } {
    return APPARENCES[m.type];
  }

  ouvrir(m: NotificationPdv): void {
    if (m.lien) {
      void this.router.navigateByUrl(m.lien);
    }
    this.service.fermer(m.cle);
  }
}
