import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map } from 'rxjs';
import { EtatPlateformeService, moduleDeLaPage } from '../core/services/etat-plateforme.service';

/**
 * Page posee sur l'ecran quand l'API a repondu que son module est en
 * maintenance : message de l'exploitant et heure de retour prevue. Elle
 * disparait en changeant de page, et la page se recharge d'elle-meme a la fin
 * de la maintenance.
 */
@Component({
  selector: 'app-page-maintenance',
  imports: [DatePipe],
  template: `
    @if (affichee(); as m) {
      <div class="voile" role="alertdialog" aria-modal="true" aria-labelledby="titre-maintenance">
        <section class="carte-maintenance">
          <div class="icone" aria-hidden="true">🛠</div>
          <h1 id="titre-maintenance">Maintenance en cours</h1>
          <p class="module">{{ m.moduleLibelle }}</p>
          @if (m.message) { <p class="message-exploitant">{{ m.message }}</p> }
          @if (m.finPrevue) {
            <p>Retour prevu a <strong>{{ m.finPrevue | date: 'HH:mm' }}</strong>
              le {{ m.finPrevue | date: 'dd/MM/yyyy' }}.</p>
          }
          <p class="aide">Cette page se rouvrira d'elle-meme a la fin de la maintenance. Vos autres modules restent disponibles.</p>
          <div class="actions">
            <button class="bouton" type="button" (click)="reessayer()">Reessayer</button>
            <button class="bouton secondaire" type="button" (click)="accueil()">Retour a l'accueil</button>
          </div>
        </section>
      </div>
    }
  `,
  styles: `
    .voile { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: 16px;
      background: color-mix(in srgb, var(--couleur-fond, #f4f6f9) 92%, transparent); backdrop-filter: blur(3px); }
    .carte-maintenance { width: min(480px, 100%); padding: 2rem 1.5rem; border-radius: 16px; text-align: center;
      background: var(--couleur-surface, #fff); border: 1px solid var(--couleur-bordure, #e2e6ec);
      box-shadow: 0 18px 48px rgba(15, 23, 42, .12); }
    .icone { font-size: 2.6rem; }
    h1 { margin: .4rem 0 .2rem; font-size: 1.4rem; }
    .module { margin: 0 0 1rem; font-weight: 600; color: var(--couleur-texte-doux, #5b6472); }
    .message-exploitant { padding: .7rem .9rem; border-radius: 10px; background: #fdf4e1; color: #6b4a05; }
    .aide { font-size: .85rem; color: var(--couleur-texte-doux, #5b6472); }
    .actions { display: flex; flex-wrap: wrap; justify-content: center; gap: .5rem; margin-top: 1rem; }
  `
})
export class PageMaintenance {
  readonly etat = inject(EtatPlateformeService);
  private readonly router = inject(Router);
  private readonly url = toSignal(this.router.events.pipe(
    filter((e) => e instanceof NavigationEnd), map((e) => e.urlAfterRedirects)), { initialValue: this.router.url });

  /**
   * Seulement si la page ouverte appartient au module en maintenance : un appel de
   * fond vers ce module (alertes, options) ne bloque pas un autre module.
   */
  readonly affichee = computed(() => {
    const m = this.etat.rencontree();
    return m && moduleDeLaPage(this.url()) === m.module ? m : null;
  });

  constructor() {
    // Une autre page peut appartenir a un module disponible : la page de maintenance s'efface.
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.etat.oublier());
  }

  reessayer(): void {
    location.reload();
  }

  accueil(): void {
    this.etat.oublier();
    this.router.navigateByUrl('/');
  }
}
