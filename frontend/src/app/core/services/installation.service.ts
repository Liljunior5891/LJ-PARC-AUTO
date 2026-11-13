import { Injectable, computed, signal } from '@angular/core';

/** Evenement « beforeinstallprompt » de Chrome, Edge et Android (non type par TypeScript). */
interface InvitationInstallation extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * Application installable (PWA) : enregistre le service worker pour toute
 * l'application et garde l'invitation d'installation du navigateur, pour la
 * proposer au bon moment (menu du compte, page de connexion). Sur iPhone et
 * iPad, Safari n'a pas d'invitation : on explique « Partager > Sur l'ecran
 * d'accueil ».
 */
@Injectable({ providedIn: 'root' })
export class InstallationService {
  private invitation: InvitationInstallation | null = null;
  private readonly invitationDisponible = signal(false);
  readonly installee = signal(InstallationService.modeApplication());
  /** Fenetre d'explication pour iPhone et iPad. */
  readonly aideIos = signal(false);

  readonly ios = /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

  /** Le bouton « Installer l'application » a un sens ici. */
  readonly proposable = computed(() => !this.installee() && (this.invitationDisponible() || this.ios));

  demarrer(): void {
    if ('serviceWorker' in navigator && window.isSecureContext) {
      void navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    }
    window.addEventListener('beforeinstallprompt', (e) => {
      // Le navigateur ne montre pas sa propre banniere : l'application la propose dans ses menus.
      e.preventDefault();
      this.invitation = e as InvitationInstallation;
      this.invitationDisponible.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.invitation = null;
      this.invitationDisponible.set(false);
      this.installee.set(true);
    });
    window.matchMedia('(display-mode: standalone)').addEventListener('change', () =>
      this.installee.set(InstallationService.modeApplication()));
  }

  async installer(): Promise<void> {
    if (this.invitation) {
      const invitation = this.invitation;
      await invitation.prompt();
      const choix = await invitation.userChoice;
      this.invitation = null;
      this.invitationDisponible.set(false);
      if (choix.outcome === 'accepted') {
        this.installee.set(true);
      }
    } else if (this.ios) {
      this.aideIos.set(true);
    }
  }

  /** Ouverte comme application installee (et non dans un onglet). */
  private static modeApplication(): boolean {
    return window.matchMedia('(display-mode: standalone)').matches
      || window.matchMedia('(display-mode: window-controls-overlay)').matches
      || (navigator as Navigator & { standalone?: boolean }).standalone === true;
  }
}
