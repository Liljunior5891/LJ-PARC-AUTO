import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionCaisseAgence } from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { COUPURES_FCFA, SaisieBillettage, versBillettage } from '../../../shared/session-caisse';

/**
 * Bandeau « Ma caisse » du caissier : ce qu'il doit avoir dans son tiroir et
 * la fermeture en deux temps. Il compte son tiroir, le montant attendu sous
 * les yeux, justifie un ecart et soumet ; le superviseur valide ou rejette.
 * Rien ne s'affiche pour un utilisateur sans caisse en cours.
 */
@Component({
  selector: 'app-ma-caisse-agence',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, SaisieBillettage],
  template: `
    @if (session(); as s) {
      <section class="carte ma-caisse" [class.soumise]="s.statut === 'SOUMISE'">
        <div class="infos">
          <span class="titre">Ma caisse <small>{{ s.numero }}{{ s.agenceNom ? ' · ' + s.agenceNom : '' }}</small></span>
          @if (s.statut === 'SOUMISE') {
            <span class="etat attente">⏳ Comptage soumis le {{ s.soumiseLe | date: 'dd/MM HH:mm' }} : {{ s.montantSoumis | number: '1.0-0' }} F
              @if (s.ecartSoumis) { (ecart {{ s.ecartSoumis > 0 ? '+' : '' }}{{ s.ecartSoumis | number: '1.0-0' }} F) }
              — en attente de validation par le superviseur. Aucune operation possible.</span>
          } @else {
            <span class="etat">● Ouverte depuis {{ s.ouverteLe | date: 'dd/MM HH:mm' }} · {{ s.nombreTransactions }} transaction(s)</span>
            @if (s.motifRejet) {
              <span class="rejet">Comptage rejete par {{ s.rejeteeParNom }} le {{ s.rejeteeLe | date: 'dd/MM HH:mm' }} : {{ s.motifRejet }}. Recomptez votre tiroir.</span>
            }
          }
        </div>
        <div class="montant">
          <small>Attendu en caisse</small>
          <strong>{{ s.soldeCaisse | number: '1.0-0' }} F</strong>
        </div>
        @if (s.statut === 'OUVERTE') {
          <button type="button" class="bouton danger" (click)="ouvrirComptage()">🔒 Fermer ma caisse</button>
        }
      </section>
    }

    @if (comptage() && session(); as s) {
      <app-modale titre="Fermer ma caisse" [sousTitre]="s.numero + ' : comptez votre tiroir'" largeur="760px"
                  [erreur]="erreurModale()" (ferme)="comptage.set(false)">
        <div class="attendu">
          <span>Fond {{ s.fondInitial | number: '1.0-0' }}</span>
          <span>+ encaisse {{ s.encaisse | number: '1.0-0' }}</span>
          <span>− decaisse {{ s.decaisse | number: '1.0-0' }}</span>
          @if (s.autresMouvements) {
            <span>{{ s.autresMouvements > 0 ? '+' : '−' }} autres {{ (s.autresMouvements < 0 ? -s.autresMouvements : s.autresMouvements) | number: '1.0-0' }}</span>
          }
          <strong>= attendu {{ s.soldeCaisse | number: '1.0-0' }} F</strong>
        </div>
        <app-saisie-billettage [(quantites)]="quantites" />
        <div class="ecart" [class.nul]="ecart() === 0" [class.negatif]="ecart() < 0">
          Ecart : <strong>{{ ecart() > 0 ? '+' : '' }}{{ ecart() | number: '1.0-0' }} F</strong>
          {{ ecart() === 0 ? '(la caisse est juste)' : ecart() < 0 ? '(il manque de l\\'argent)' : '(il y a trop d\\'argent)' }}
        </div>
        @if (ecart() !== 0) {
          <div class="champ">
            <label for="justif-caisse">Justification de l'ecart *</label>
            <input id="justif-caisse" maxlength="1000" [(ngModel)]="justification" placeholder="Ex. Erreur de monnaie rendue" />
          </div>
        }
        <p class="aide">Apres l'envoi, votre caisse est bloquee jusqu'a la validation du superviseur, qui recoit le tiroir.</p>
        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="comptage.set(false)">Annuler</button>
          <button class="bouton" type="button" [disabled]="ecart() !== 0 && !justification.trim()" (click)="soumettre()">
            Soumettre {{ total() | number: '1.0-0' }} F au superviseur
          </button>
        </div>
      </app-modale>
    }
  `,
  styles: `
    .ma-caisse {
      display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem 1.25rem;
      margin-bottom: 1rem; border-left: 4px solid var(--couleur-succes);
    }
    .ma-caisse.soumise { border-left-color: #e0a100; background: #fffaf0; }
    .infos { display: flex; flex: 1; flex-direction: column; gap: 0.2rem; min-width: 240px; }
    .titre { font-weight: 700; }
    .titre small { margin-left: 0.4rem; color: var(--couleur-texte-doux); font-weight: normal; }
    .etat { color: var(--couleur-succes); font-size: 0.88rem; }
    .etat.attente { color: #8a6200; }
    .rejet { color: var(--couleur-danger); font-size: 0.85rem; }
    .montant { display: flex; flex-direction: column; align-items: flex-end; }
    .montant small { color: var(--couleur-texte-doux); font-size: 0.75rem; text-transform: uppercase; }
    .montant strong { font-size: 1.3rem; font-variant-numeric: tabular-nums; }
    .attendu {
      display: flex; flex-wrap: wrap; gap: 0.4rem 0.9rem; margin-bottom: 0.8rem; padding: 0.6rem 0.9rem;
      border-radius: 8px; background: #eef3f9; font-variant-numeric: tabular-nums;
    }
    .ecart { margin: 0.8rem 0; padding: 0.6rem 0.9rem; border-radius: 8px; background: #fff6e0; color: #7a5200; }
    .ecart.nul { background: #e3f5ea; color: var(--couleur-succes); }
    .ecart.negatif { background: #fce9e9; color: var(--couleur-danger); }
    .aide { color: var(--couleur-texte-doux); font-size: 0.85rem; }
  `
})
export class MaCaisseAgence implements OnInit {
  private readonly service = inject(AgencesService);

  /** Emis apres la soumission, pour que la page recharge ce qui en depend. */
  readonly change = output<SessionCaisseAgence>();

  readonly session = signal<SessionCaisseAgence | null>(null);
  readonly comptage = signal(false);
  readonly erreurModale = signal<string | null>(null);
  readonly quantites = signal<Record<number, number | null>>({});
  justification = '';

  readonly total = computed(() =>
    COUPURES_FCFA.reduce((s, v) => s + v * Math.max(0, Math.floor(this.quantites()[v] ?? 0)), 0));
  readonly ecart = computed(() => this.total() - (this.session()?.soldeCaisse ?? 0));

  ngOnInit(): void {
    this.charger();
  }

  /** Premiere caisse en cours de l'utilisateur parmi ses agences. */
  charger(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        const agences = m.agences.filter((a) => a.actif);
        const suivante = (i: number): void => {
          if (i >= agences.length) {
            this.session.set(null);
            return;
          }
          this.service.maSession(agences[i].id).subscribe({
            next: (s) => (s ? this.session.set(s) : suivante(i + 1)),
            error: () => suivante(i + 1)
          });
        };
        suivante(0);
      }
    });
  }

  ouvrirComptage(): void {
    this.quantites.set({});
    this.justification = '';
    this.erreurModale.set(null);
    this.charger();
    this.comptage.set(true);
  }

  soumettre(): void {
    const s = this.session();
    if (!s) {
      return;
    }
    this.service.soumettreCaisse(s.id, versBillettage(this.quantites()), this.justification.trim() || undefined).subscribe({
      next: (r) => {
        this.comptage.set(false);
        this.session.set(r);
        this.change.emit(r);
      },
      error: (e) => this.erreurModale.set((e as { error?: { message?: string } })?.error?.message ?? 'Envoi impossible.')
    });
  }
}
