import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { ArticlePdv } from '../core/models/pdv';
import { NotificationPdv, NotificationsPdvService } from '../core/services/notifications-pdv.service';
import { PdvService } from '../core/services/pdv.service';

/** Le stock bouge a chaque vente : les alertes se relisent regulierement. */
const RAFRAICHISSEMENT_MS = 2 * 60 * 1000;

const ICONES: Record<NotificationPdv['type'], string> = {
  VALIDATION: '📱', BON: '🍳', PRET: '🔔', APPEL: '🙋', ADDITION: '🧾', CAISSE: '💰'
};

/**
 * Alertes du point de vente, a cote de l'avatar : les produits a
 * reapprovisionner (gerant et administrateur), en petites cartes qui ouvrent
 * directement la commande au fournisseur, et les dernieres notifications du
 * poste (commandes a valider, bons, plats prets, caisse ouverte).
 */
@Component({
  selector: 'app-alertes-pdv',
  imports: [DatePipe, DecimalPipe],
  host: {
    '(document:click)': 'clicDocument($event)',
    '(document:keydown.escape)': 'ouvert.set(false)'
  },
  template: `
    <button type="button" class="declencheur" [class.alerte]="total() > 0" (click)="basculer()"
            [attr.aria-expanded]="ouvert()" aria-haspopup="dialog"
            [title]="total() > 0 ? total() + ' alerte(s) du point de vente' : 'Alertes du point de vente'">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 8l8-4 8 4-8 4zM4 8v8l8 4 8-4V8" /><path d="M12 12v8" /></svg>
      @if (total() > 0) { <span class="pastille">{{ total() > 99 ? '99+' : total() }}</span> }
    </button>

    @if (ouvert()) {
      <section class="panneau" role="dialog" aria-label="Alertes du point de vente">
        @if (gestionnaire) {
          <header>
            <h3>📦 A reapprovisionner <span>{{ alertes().length }}</span></h3>
            @if (alertes().length > 0) {
              <button type="button" class="lien" (click)="commander('tout')">Tout commander</button>
            }
          </header>
          <div class="cartes">
            @for (a of alertes(); track a.id) {
              <button type="button" class="carte-mini" [class.rupture]="a.stockActuel <= 0" (click)="commander(a.id)"
                      [title]="'Commander ' + a.nom">
                <strong>{{ a.nom }}</strong>
                <small>{{ a.stockActuel <= 0 ? 'Rupture' : 'Stock ' + (a.stockActuel | number: '1.0-3') }} · seuil {{ a.seuilAlerte | number: '1.0-3' }}</small>
              </button>
            } @empty {
              <p class="vide">Aucun produit sous son seuil d'alerte.</p>
            }
          </div>
        }
        <header>
          <h3>🔔 Notifications <span>{{ notifications.historique().length }}</span></h3>
        </header>
        <ul class="notifications">
          @for (n of notifications.historique(); track n.cle) {
            <li>
              <button type="button" (click)="ouvrir(n)" [disabled]="!n.lien">
                <span class="icone" aria-hidden="true">{{ icone(n) }}</span>
                <span class="texte"><strong>{{ n.titre }}</strong><small>{{ n.message }}</small></span>
                @if (n.date) { <time>{{ n.date | date: 'HH:mm' }}</time> }
              </button>
            </li>
          } @empty {
            <li class="vide">Aucune notification depuis l'ouverture de la page.</li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host { position: relative; display: inline-flex; }
    .declencheur {
      position: relative; display: grid; place-items: center; width: 40px; height: 40px;
      border: 1px solid var(--couleur-bordure); border-radius: 50%; background: var(--couleur-surface);
      color: var(--couleur-texte-doux); cursor: pointer;
    }
    .declencheur.alerte { border-color: #f3d6a4; background: #fff4e3; color: #b8650a; }
    .declencheur svg { width: 21px; height: 21px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
    .pastille {
      position: absolute; top: -5px; right: -5px; min-width: 20px; height: 20px; padding: 0 5px;
      border: 2px solid var(--couleur-surface); border-radius: 10px; background: #e8870e; color: #fff;
      font-size: 0.68rem; font-weight: 700; line-height: 16px; text-align: center;
    }
    .panneau {
      position: absolute; top: calc(100% + 10px); right: 0; z-index: 900; width: min(400px, calc(100vw - 24px));
      max-height: 70vh; overflow-y: auto; padding: 0.75rem; border: 1px solid var(--couleur-bordure);
      border-radius: 14px; background: var(--couleur-surface); box-shadow: 0 16px 40px rgba(15, 30, 50, 0.2);
      color: var(--couleur-texte); text-align: left;
    }
    header { display: flex; align-items: center; justify-content: space-between; margin: 0.25rem 0 0.5rem; }
    header + .cartes, header + .notifications { margin-bottom: 0.75rem; }
    h3 { margin: 0; font-size: 0.85rem; }
    h3 span { margin-left: 0.3rem; padding: 0 0.45rem; border-radius: 999px; background: var(--couleur-fond); font-size: 0.72rem; }
    .lien { border: none; background: none; color: var(--couleur-primaire); font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer; }
    .cartes { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.45rem; }
    .carte-mini {
      display: flex; flex-direction: column; gap: 0.1rem; min-width: 0; padding: 0.45rem 0.55rem;
      border: 1px solid #f3d6a4; border-left: 3px solid #e8870e; border-radius: 10px; background: #fffaf2;
      font: inherit; text-align: left; cursor: pointer;
    }
    .carte-mini:hover { background: #fff1dc; }
    .carte-mini.rupture { border-color: #f3c7c7; border-left-color: var(--couleur-danger); background: #fff6f4; }
    .carte-mini strong { overflow: hidden; font-size: 0.8rem; text-overflow: ellipsis; white-space: nowrap; }
    .carte-mini small { color: #b8650a; font-size: 0.7rem; font-weight: 600; }
    .carte-mini.rupture small { color: var(--couleur-danger); }
    .notifications { display: grid; gap: 0.3rem; margin: 0; padding: 0; list-style: none; }
    .notifications button {
      display: flex; align-items: center; gap: 0.55rem; width: 100%; padding: 0.45rem 0.5rem; border: none;
      border-radius: 9px; background: none; font: inherit; text-align: left; cursor: pointer;
    }
    .notifications button:hover:not(:disabled) { background: var(--couleur-fond); }
    .notifications button:disabled { cursor: default; }
    .icone { font-size: 1.1rem; }
    .texte { display: flex; flex: 1; flex-direction: column; min-width: 0; }
    .texte strong { font-size: 0.8rem; }
    .texte small { overflow: hidden; color: var(--couleur-texte-doux); font-size: 0.75rem; text-overflow: ellipsis; white-space: nowrap; }
    time { color: var(--couleur-texte-doux); font-size: 0.72rem; }
    .vide { grid-column: 1 / -1; margin: 0; padding: 0.4rem; color: var(--couleur-texte-doux); font-size: 0.8rem; }
  `
})
export class AlertesPdv {
  private readonly pdv = inject(PdvService);
  private readonly router = inject(Router);
  private readonly element = inject(ElementRef<HTMLElement>);
  readonly notifications = inject(NotificationsPdvService);
  readonly gestionnaire = inject(AuthService).aRole('ADMIN', 'GERANT');

  readonly ouvert = signal(false);
  readonly alertes = signal<ArticlePdv[]>([]);
  /** Pastille : produits a reapprovisionner et notifications pas encore vues. */
  readonly total = computed(() => this.alertes().length + this.notifications.nonLues());

  constructor() {
    if (this.gestionnaire) {
      this.chargerAlertes();
      const minuterie = setInterval(() => this.chargerAlertes(), RAFRAICHISSEMENT_MS);
      inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
    }
  }

  basculer(): void {
    const ouvrir = !this.ouvert();
    this.ouvert.set(ouvrir);
    if (ouvrir) {
      this.notifications.marquerLues();
      if (this.gestionnaire) {
        this.chargerAlertes();
      }
    }
  }

  /** La commande au fournisseur s'ouvre preremplie sur la page de reapprovisionnement. */
  commander(cible: number | 'tout'): void {
    this.ouvert.set(false);
    void this.router.navigate(['/pdv/stock'], { queryParams: { commander: cible } });
  }

  ouvrir(n: NotificationPdv): void {
    this.ouvert.set(false);
    if (n.lien) {
      void this.router.navigateByUrl(n.lien);
    }
  }

  icone(n: NotificationPdv): string {
    return ICONES[n.type];
  }

  clicDocument(evenement: MouseEvent): void {
    if (this.ouvert() && !this.element.nativeElement.contains(evenement.target as Node)) {
      this.ouvert.set(false);
    }
  }

  private chargerAlertes(): void {
    this.pdv.articles({ stockBas: true, actif: true, size: 50 }).subscribe({
      next: (p) => this.alertes.set(p.content),
      error: () => undefined
    });
  }
}
