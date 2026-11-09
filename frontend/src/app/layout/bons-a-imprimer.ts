import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { TravailImpression } from '../core/models/restauration';
import { ImpressionService } from '../core/services/impression.service';
import { PdvService } from '../core/services/pdv.service';
import { imprimerBonsCuisine } from '../shared/bon-cuisine';

/** Pages de caisse ou les bons de preparation sont proposes a l'impression. */
const PAGES_CAISSE = ['/pdv/comptoir', '/pdv/caisse', '/pdv/salle', '/pdv/commandes'];
const CLE_AUTO = 'lj-impression-auto';
const INTERVALLE = 5000;

/**
 * Bons de cuisine a imprimer par le navigateur : la caisse les voit arriver
 * (validation d'une commande, vente au comptoir, annulation) et les imprime
 * d'un clic, ou automatiquement si elle l'a choisi. Un bon pris par une caisse
 * n'est plus propose aux autres.
 */
@Component({
  selector: 'app-bons-a-imprimer',
  template: `
    @if (actif() && bons().length > 0) {
      <aside class="bons-a-imprimer" role="status" aria-live="polite">
        <span class="icone" aria-hidden="true">🖨</span>
        <div class="texte">
          <strong>{{ bons().length }} bon{{ bons().length > 1 ? 's' : '' }} de preparation a imprimer</strong>
          <small>{{ resume() }}</small>
        </div>
        <button class="bouton succes" type="button" [disabled]="enCours()" (click)="imprimer()">
          {{ enCours() ? 'Impression...' : 'Imprimer' }}
        </button>
        <label class="auto" title="Ouvrir l'impression des qu'un bon arrive, sur ce poste">
          <input type="checkbox" [checked]="auto()" (change)="basculerAuto()" /> auto
        </label>
      </aside>
    }
  `,
  styles: [`
    .bons-a-imprimer {
      position: fixed; right: 16px; bottom: 16px; z-index: 900;
      display: flex; align-items: center; gap: 0.75rem;
      max-width: calc(100vw - 32px); padding: 0.7rem 0.9rem;
      border: 1px solid color-mix(in srgb, var(--couleur-succes) 35%, transparent);
      border-left: 4px solid var(--couleur-succes); border-radius: 12px;
      background: var(--couleur-surface); box-shadow: 0 12px 32px rgba(16, 40, 30, 0.22);
      animation: arrivee 0.2s ease-out;
    }
    .icone { font-size: 1.5rem; }
    .texte { display: flex; flex-direction: column; min-width: 0; }
    .texte small { color: var(--couleur-texte-doux); font-size: 0.78rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .auto { display: flex; align-items: center; gap: 0.25rem; color: var(--couleur-texte-doux); font-size: 0.78rem; cursor: pointer; }
    @keyframes arrivee { from { opacity: 0; transform: translateY(8px); } }
  `]
})
export class BonsAImprimer implements OnInit {
  private readonly service = inject(ImpressionService);
  private readonly pdv = inject(PdvService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly autorise = this.auth.aModule('POINT_DE_VENTE') && this.auth.variante('POINT_DE_VENTE') === 'RESTAURATION'
    && this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER');
  private readonly surPageCaisse = signal(false);
  readonly actif = computed(() => this.autorise && this.surPageCaisse());

  readonly bons = signal<TravailImpression[]>([]);
  readonly enCours = signal(false);
  readonly auto = signal(this.lireAuto());
  /** Caisse de la session ouverte du caissier : il ne voit que les imprimantes de sa caisse (ou communes). */
  private caisseId: number | null = null;
  private minuterie: ReturnType<typeof setInterval> | null = null;
  private dejaVus = new Set<number>();

  readonly resume = computed(() => {
    const parImprimante = new Map<string, number>();
    for (const b of this.bons()) {
      parImprimante.set(b.imprimanteNom, (parImprimante.get(b.imprimanteNom) ?? 0) + 1);
    }
    return [...parImprimante.entries()].map(([nom, n]) => `${nom} ${n}`).join(' · ');
  });

  ngOnInit(): void {
    if (!this.autorise) {
      return;
    }
    this.suivreUrl(this.router.url);
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe((e) => this.suivreUrl(e.urlAfterRedirects));
    if (this.auth.aRole('CAISSIER') && !this.auth.aRole('ADMIN', 'GERANT')) {
      this.pdv.sessionsOuvertes().subscribe({
        next: (sessions) => (this.caisseId = sessions.length === 1 ? sessions[0].caisseId : null)
      });
    }
    this.destroyRef.onDestroy(() => this.arreter());
  }

  imprimer(): void {
    const ids = this.bons().map((b) => b.id);
    if (!ids.length || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.service.prendre(ids).subscribe({
      next: (pris) => {
        this.enCours.set(false);
        imprimerBonsCuisine(pris.map((t) => t.bon));
        this.bons.update((liste) => liste.filter((b) => !ids.includes(b.id)));
      },
      error: () => this.enCours.set(false)
    });
  }

  basculerAuto(): void {
    this.auto.update((v) => !v);
    try {
      localStorage.setItem(CLE_AUTO, this.auto() ? '1' : '0');
    } catch {
      // Stockage indisponible (navigation privee) : le choix vaut pour cette page seulement.
    }
  }

  private suivreUrl(url: string): void {
    const surCaisse = PAGES_CAISSE.some((p) => url.startsWith(p));
    this.surPageCaisse.set(surCaisse);
    if (surCaisse) {
      this.demarrer();
    } else {
      this.arreter();
    }
  }

  private demarrer(): void {
    if (this.minuterie) {
      return;
    }
    this.relever();
    this.minuterie = setInterval(() => this.relever(), INTERVALLE);
  }

  private arreter(): void {
    if (this.minuterie) {
      clearInterval(this.minuterie);
      this.minuterie = null;
    }
  }

  private relever(): void {
    if (document.hidden || this.enCours()) {
      return;
    }
    this.service.aImprimer(this.caisseId).subscribe({
      next: (bons) => {
        const nouveaux = bons.some((b) => !this.dejaVus.has(b.id));
        bons.forEach((b) => this.dejaVus.add(b.id));
        this.bons.set(bons);
        if (nouveaux && this.auto()) {
          this.imprimer();
        }
      },
      error: () => undefined
    });
  }

  private lireAuto(): boolean {
    try {
      return localStorage.getItem(CLE_AUTO) === '1';
    } catch {
      return false;
    }
  }
}
