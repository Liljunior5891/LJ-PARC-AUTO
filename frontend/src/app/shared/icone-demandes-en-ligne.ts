import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DemandeLocationEnLigne } from '../core/models/marketplace';
import { MarketplaceService } from '../core/services/marketplace.service';

/** Les demandes arrivent a toute heure : l'icone se met a jour seule. */
const RAFRAICHISSEMENT_MS = 30_000;

/**
 * Icone des demandes de location faites en ligne, a cote de la cloche des
 * notifications (gestion de parc avec Marketplace). La pastille compte les
 * demandes qui attendent une reponse ; un clic les liste, un autre ouvre
 * l'ecran des demandes pour les accepter ou les refuser.
 */
@Component({
  selector: 'app-icone-demandes-en-ligne',
  host: { '(document:click)': 'clicDocument($event)' },
  imports: [DatePipe, DecimalPipe],
  template: `
    <button class="icone" type="button" (click)="basculer()" [class.active]="ouvert()" [class.nouvelle]="nouvelle()"
            [attr.aria-expanded]="ouvert()"
            [attr.aria-label]="total() + ' demande(s) de location en ligne en attente'"
            title="Demandes de location en ligne">
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M3.5 9h17M3.5 15h17M12 3c2.5 2.6 3.6 5.6 3.6 9s-1.1 6.4-3.6 9c-2.5-2.6-3.6-5.6-3.6-9s1.1-6.4 3.6-9z" />
      </svg>
      @if (total()) {
        <span class="badge">{{ total() > 99 ? '99+' : total() }}</span>
      }
    </button>

    @if (ouvert()) {
      <section class="panneau" role="dialog" aria-label="Demandes de location en ligne">
        <header>
          <strong>Demandes en ligne</strong>
          <small>{{ total() ? total() + ' en attente de reponse' : 'Aucune demande en attente' }}</small>
        </header>
        <ul>
          @for (d of demandes(); track d.id) {
            <li>
              <button type="button" (click)="ouvrirEcran()">
                <span class="ligne">
                  <strong>{{ d.clientNom }}</strong>
                  <b>{{ d.montantEstime | number: '1.0-0' }} F</b>
                </span>
                <span class="detail">{{ d.vehicule }}</span>
                <span class="detail">
                  {{ d.dateDebut | date: 'dd/MM' }} → {{ d.dateFin | date: 'dd/MM' }} · {{ d.jours }} jour(s)
                  · recue {{ d.dateDemande | date: 'dd/MM HH:mm' }}
                </span>
              </button>
            </li>
          } @empty {
            <li class="vide">Les reservations faites dans la boutique en ligne apparaitront ici.</li>
          }
        </ul>
        <footer>
          <button class="bouton petit info" type="button" (click)="ouvrirEcran()">Voir et repondre</button>
        </footer>
      </section>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-flex;
    }

    .icone {
      position: relative;
      display: grid;
      place-items: center;
      width: 40px;
      height: 40px;
      border: 1px solid var(--couleur-bordure);
      border-radius: 50%;
      background: #fff;
      color: var(--couleur-texte);
      cursor: pointer;
      transition: background 0.15s ease, border-color 0.15s ease;

      svg {
        width: 20px;
        height: 20px;
        fill: none;
        stroke: currentColor;
        stroke-width: 1.7;
        stroke-linecap: round;
        stroke-linejoin: round;
      }

      &:hover,
      &.active {
        border-color: #8455c8;
        background: #f3edfc;
        color: #6b3fb5;
      }

      /* Nouvelle demande depuis le dernier regard : l'icone pulse jusqu'a l'ouverture. */
      &.nouvelle {
        animation: pulsation 1.6s ease-in-out infinite;
      }
    }

    @keyframes pulsation {
      50% {
        box-shadow: 0 0 0 6px rgba(132, 85, 200, 0.22);
      }
    }

    .badge {
      position: absolute;
      top: -5px;
      right: -6px;
      min-width: 20px;
      height: 20px;
      padding: 0 5px;
      border: 2px solid #fff;
      border-radius: 999px;
      background: #8455c8;
      color: #fff;
      font-size: 0.66rem;
      font-weight: 800;
      line-height: 16px;
      text-align: center;
    }

    .panneau {
      position: absolute;
      top: calc(100% + 10px);
      right: 0;
      z-index: 60;
      width: min(360px, 92vw);
      border: 1px solid var(--couleur-bordure);
      border-radius: 14px;
      background: #fff;
      box-shadow: 0 14px 36px rgba(16, 24, 40, 0.18);
      overflow: hidden;

      header {
        display: flex;
        flex-direction: column;
        padding: 0.8rem 1rem;
        border-bottom: 1px solid var(--couleur-bordure);

        small {
          font-size: 0.76rem;
          color: var(--couleur-texte-doux);
        }
      }

      ul {
        max-height: 50vh;
        margin: 0;
        padding: 0;
        overflow-y: auto;
        list-style: none;
      }

      li {
        border-bottom: 1px solid var(--couleur-bordure);

        &.vide {
          padding: 1rem;
          font-size: 0.82rem;
          color: var(--couleur-texte-doux);
          text-align: center;
        }

        button {
          display: flex;
          flex-direction: column;
          gap: 0.15rem;
          width: 100%;
          padding: 0.65rem 1rem;
          border: 0;
          background: none;
          font: inherit;
          text-align: left;
          cursor: pointer;

          &:hover {
            background: #f7f4fd;
          }
        }
      }

      .ligne {
        display: flex;
        justify-content: space-between;
        gap: 0.5rem;
        font-size: 0.86rem;
      }

      .detail {
        font-size: 0.74rem;
        color: var(--couleur-texte-doux);
      }

      footer {
        padding: 0.7rem 1rem;
        text-align: right;
      }
    }
  `
})
export class IconeDemandesEnLigne implements OnInit, OnDestroy {
  private readonly service = inject(MarketplaceService);
  private readonly router = inject(Router);
  private readonly hote = inject(ElementRef<HTMLElement>);
  private minuterie?: ReturnType<typeof setInterval>;
  /** Plus haute demande deja vue : au-dela, la demande est nouvelle. */
  private derniereVue = 0;

  readonly demandes = signal<DemandeLocationEnLigne[]>([]);
  readonly total = signal(0);
  readonly ouvert = signal(false);
  readonly nouvelle = signal(false);

  ngOnInit(): void {
    this.charger(true);
    this.minuterie = setInterval(() => this.charger(false), RAFRAICHISSEMENT_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
  }

  basculer(): void {
    this.ouvert.update((ouvert) => !ouvert);
    if (this.ouvert()) {
      this.nouvelle.set(false);
      this.charger(false);
    }
  }

  ouvrirEcran(): void {
    this.ouvert.set(false);
    this.nouvelle.set(false);
    void this.router.navigate(['/marketplace/locations']);
  }

  /** Un clic ailleurs referme le panneau. */
  clicDocument(evenement: MouseEvent): void {
    if (this.ouvert() && !this.hote.nativeElement.contains(evenement.target as Node)) {
      this.ouvert.set(false);
    }
  }

  private charger(premierChargement: boolean): void {
    this.service.demandes('NOUVELLE', 0).subscribe({
      next: (page) => {
        this.demandes.set(page.content.slice(0, 6));
        this.total.set(page.totalElements);
        const plusRecente = page.content.reduce((max, d) => Math.max(max, d.id), 0);
        // Au premier chargement, rien n'est « nouveau » : seules les arrivees suivantes font pulser l'icone.
        if (!premierChargement && plusRecente > this.derniereVue && !this.ouvert()) {
          this.nouvelle.set(true);
        }
        this.derniereVue = Math.max(this.derniereVue, plusRecente);
      },
      error: () => {
        this.demandes.set([]);
        this.total.set(0);
      }
    });
  }
}
