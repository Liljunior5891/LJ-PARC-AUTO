import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { LigneStockLieu } from '../../../core/models/lieux';
import { Page } from '../../../core/models/models';
import { LieuxService } from '../../../core/services/lieux.service';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/** Stock d'un lieu, en quantites : le magasinier voit ses lieux, le gerant les regle tous. */
@Component({
  selector: 'app-stock-lieu-pdv',
  imports: [FormsModule, DecimalPipe, SuiteListe],
  styles: `
    .filtres-lieu {
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.75rem;
      margin-bottom: 14px;
    }

    .cellule-produit {
      display: flex;
      align-items: center;
      gap: 0.7rem;
    }

    .vignette-produit {
      display: grid;
      flex: none;
      place-items: center;
      overflow: hidden;
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: #eef2f7;
      font-size: 1.2rem;
    }

    .vignette-produit img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    small {
      color: var(--couleur-texte-doux);
    }

    @media (max-width: 520px) {
      .filtres-lieu {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
  template: `
    <div class="entete-page">
      <div>
        <h1>Stock par lieu</h1>
        <p class="sous-titre">Ce qui est sur place, ce qui est deja vendu et ce qui reste disponible.</p>
      </div>
    </div>
    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    <div class="carte filtres-lieu">
      <div class="champ">
        <label for="lieu">Lieu</label>
        <select id="lieu" [ngModel]="lieuId()" (ngModelChange)="changerLieu($event)">
          @for (l of lieux(); track l.id) { <option [ngValue]="l.id">{{ l.nom }}</option> }
        </select>
      </div>
      <div class="champ">
        <label for="recherche">Rechercher</label>
        <input id="recherche" type="search" placeholder="Nom ou code..." [(ngModel)]="q" (ngModelChange)="rechercher()" />
      </div>
    </div>
    <div class="tableau-conteneur">
      <table class="tableau">
        <thead>
          <tr>
            <th>Produit</th>
            <th class="numerique">Sur place</th>
            <th class="numerique">Reserve</th>
            <th class="numerique">Disponible</th>
            <th class="numerique">Seuil d'alerte</th>
          </tr>
        </thead>
        <tbody>
          @for (s of page()?.content ?? []; track s.articleId) {
            <tr>
              <td>
                <div class="cellule-produit">
                  <div class="vignette-produit">
                    @if (s.image) { <img [src]="s.image" [alt]="s.nom" loading="lazy" /> } @else { <span aria-hidden="true">📦</span> }
                  </div>
                  <div><strong>{{ s.nom }}</strong><br /><small>{{ s.code }}</small></div>
                </div>
              </td>
              <td class="numerique">{{ s.stock | number: '1.0-3' }} {{ s.unite || '' }}</td>
              <td class="numerique">{{ s.reserve | number: '1.0-3' }}</td>
              <td class="numerique" [class.resultat-negatif]="s.sousSeuil || s.disponible < 0"><strong>{{ s.disponible | number: '1.0-3' }}</strong></td>
              <td class="numerique">
                @if (gerant) {
                  <input type="number" min="0" step="any" style="width: 90px; text-align: right"
                         [attr.aria-label]="'Seuil de ' + s.nom" [ngModel]="s.seuilAlerte" (change)="seuil(s, $any($event.target).value)" />
                } @else { {{ s.seuilAlerte ?? '—' }} }
              </td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="aucun">Aucun produit dans ce lieu.</td></tr>
          }
        </tbody>
      </table>
    </div>
    @if (page(); as p) {
      <app-suite-liste [page]="p" (suite)="suivante()" />
    }
  `
})
export class StockLieuPdv implements OnInit {
  private readonly service = inject(LieuxService);
  private readonly route = inject(ActivatedRoute);
  readonly gerant = inject(AuthService).aRole('ADMIN', 'GERANT');

  readonly lieuId = signal<number | null>(null);
  readonly lieux = computed(() => this.service.etat()?.mesLieux ?? []);
  readonly page = signal<Page<LigneStockLieu> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly erreur = signal<string | null>(null);
  q = '';
  private minuterie?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    const demande = Number(this.route.snapshot.queryParamMap.get('lieu'));
    const attendre = (essais = 0) => {
      const lieux = this.lieux();
      if (lieux.length) {
        this.lieuId.set(lieux.find((l) => l.id === demande)?.id ?? lieux[0].id);
        this.charger();
      } else if (essais < 20) {
        setTimeout(() => attendre(essais + 1), 150);
      }
    };
    if (!this.service.etat()) {
      this.service.chargerEtat();
    }
    attendre();
  }

  changerLieu(id: number): void {
    this.lieuId.set(id);
    this.charger();
  }

  rechercher(): void {
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(() => this.charger(), 250);
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  suivante(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  seuil(s: LigneStockLieu, valeur: string): void {
    const lieu = this.lieuId();
    if (lieu) {
      this.service.modifierSeuil(lieu, s.articleId, valeur === '' ? null : Number(valeur)).subscribe({
        next: () => this.charger(),
        error: (e) => this.erreur.set(e?.error?.message ?? 'Seuil non enregistre.')
      });
    }
  }

  private charger(): void {
    const lieu = this.lieuId();
    if (lieu) {
      this.service.stock(lieu, this.q.trim(), this.suite.numero()).subscribe({
        next: (p) => this.page.set(cumulerPage(this.page(), p)),
        error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement du stock impossible.')
      });
    }
  }
}
