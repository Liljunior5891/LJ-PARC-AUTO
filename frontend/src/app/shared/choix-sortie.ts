import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, effect, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth/auth.service';
import { MODES_SORTIE, ModeSortie } from '../core/models/achats';
import { OptionsPdvService } from '../core/services/options-pdv.service';
import { SessionTravailService } from '../core/services/session-travail.service';

/**
 * Moyen de paiement d'une depense ou d'un fournisseur : la caisse (la caisse
 * ouverte d'ou sortent les especes est choisie ici), le coffre du gerant (avec
 * le circuit des especes, pour un gerant, dans la limite de son solde), la
 * banque, c'est-a-dire le compte de l'entreprise, ou le fonds de reserve
 * (depenses seulement, quand l'option est active).
 */
@Component({
  selector: 'app-choix-sortie',
  imports: [FormsModule, DecimalPipe],
  template: `
    <div class="moyens" role="radiogroup" aria-label="Moyen de paiement">
      @for (m of modes(); track m.valeur) {
        <button type="button" class="moyen" [class.actif]="mode() === m.valeur" [title]="m.aide"
                role="radio" [attr.aria-checked]="mode() === m.valeur" (click)="mode.set(m.valeur)">
          <span aria-hidden="true">{{ m.icone }}</span> {{ m.libelle }}
        </button>
      }
    </div>
    @switch (mode()) {
      @case ('CAISSE') {
        @let liste = ouvertes() ?? [];
        @if (liste.length === 0) {
          <div class="message avertissement">Aucune caisse ouverte : ouvrez une session ou choisissez un autre moyen de paiement.</div>
        } @else {
          <div class="champ">
            <label for="choix-sortie-session">Caisse d'ou sortent les especes *</label>
            <select id="choix-sortie-session" name="sessionSortie" [ngModel]="sessionId()" (ngModelChange)="sessionId.set($event)">
              <option [ngValue]="null" disabled>Choisissez la caisse</option>
              @for (s of liste; track s.id) {
                <option [ngValue]="s.id">{{ s.caisseNom }} · {{ s.numero }} — {{ s.especesAttendues | number: '1.0-0' }} F en caisse</option>
              }
            </select>
          </div>
        }
      }
      @case ('COFFRE') {
        <p class="info-moyen">🔐 Paye avec les especes de votre coffre
          @if (soldeCoffre() !== null) { — solde : <strong>{{ soldeCoffre() | number: '1.0-0' }} F</strong> }</p>
      }
      @case ('BANQUE') {
        <p class="info-moyen">🏦 Paye par le compte de l'entreprise (virement ou cheque) : indiquez la reference.</p>
      }
      @case ('RESERVE') {
        <p class="info-moyen">🛟 Paye par le fonds de reserve
          @if (disponibleFonds() !== null) { — disponible : <strong>{{ disponibleFonds() | number: '1.0-0' }} F</strong>
            <br /><small>Le versement du mois en cours devient utilisable a la fin du mois.</small> }
          @if (!auth.aRole('ADMIN')) { <br />La depense attend l'approbation de l'administrateur avant de sortir du fonds. }</p>
      }
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 0.6rem; }
    .moyens { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .moyen {
      padding: 0.45rem 0.8rem; border: 1px solid var(--couleur-bordure); border-radius: 999px;
      background: var(--couleur-surface); font: inherit; cursor: pointer;
    }
    .moyen.actif { border-color: #1e9e4a; background: #e3f5ea; color: #157a37; font-weight: 600; }
    .info-moyen { margin: 0; padding: 0.55rem 0.75rem; border-radius: 10px; background: #f5f7fa;
      color: var(--couleur-texte-doux); font-size: 0.85rem; }
  `
})
export class ChoixSortie implements OnInit {
  private readonly sessions = inject(SessionTravailService);
  private readonly options = inject(OptionsPdvService);
  readonly auth = inject(AuthService);

  readonly mode = model<ModeSortie>('CAISSE');
  readonly sessionId = model<number | null>(null);
  /** Le fonds de reserve paie les depenses, pas les fournisseurs. */
  readonly avecReserve = input(false);

  /** Le coffre n'existe qu'avec le circuit des especes, et seul un gerant paie depuis le sien. */
  private readonly aUnCoffre = computed(() =>
    this.options.circuitActif() && this.auth.aRole('GERANT') && !this.auth.aRole('ADMIN'));
  /** Seuls l'administrateur et le gerant paient par le fonds de reserve (le serveur refuse les autres). */
  private readonly payeParFonds = this.auth.aRole('ADMIN') || this.auth.aRole('GERANT');
  readonly modes = computed(() => MODES_SORTIE.filter((m) =>
    (m.valeur !== 'COFFRE' || this.aUnCoffre())
    && (m.valeur !== 'RESERVE' || (this.avecReserve() && this.payeParFonds && this.options.fondsReserveActif()))));
  readonly soldeCoffre = signal<number | null>(null);
  readonly disponibleFonds = signal<number | null>(null);
  readonly ouvertes = this.sessions.ouvertes;
  private readonly seule = computed(() => {
    const liste = this.ouvertes() ?? [];
    return liste.length === 1 ? liste[0].id : null;
  });

  constructor() {
    // Une seule caisse ouverte : elle est choisie d'office.
    effect(() => {
      const seule = this.seule();
      if (seule && this.sessionId() === null) {
        this.sessionId.set(seule);
      }
    });
    // Le solde du coffre, lu quand le coffre est choisi.
    effect(() => {
      if (this.mode() === 'COFFRE' && this.soldeCoffre() === null) {
        this.options.coffre().subscribe({ next: (c) => this.soldeCoffre.set(c.solde), error: () => this.soldeCoffre.set(null) });
      }
    });
    // Le disponible du fonds de reserve, relu chaque fois qu'il est choisi : il change a chaque depense.
    let precedent: ModeSortie | null = null;
    effect(() => {
      const mode = this.mode();
      if (mode === 'RESERVE' && precedent !== 'RESERVE') {
        this.actualiserFonds();
      }
      precedent = mode;
    });
  }

  /** Relit le disponible du fonds de reserve (apres une depense, par exemple). */
  actualiserFonds(): void {
    this.disponibleFonds.set(null);
    this.options.fondsReserve().subscribe({ next: (f) => this.disponibleFonds.set(f.disponible), error: () => this.disponibleFonds.set(null) });
  }

  ngOnInit(): void {
    this.sessions.rafraichir();
    if (!this.options.reglages()) {
      this.options.charger();
    }
  }
}
