import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { AbonnementService } from '../core/services/abonnement.service';

const MASQUE_JUSQUA = 'parcauto.abonnement.masque';

/**
 * Bandeau de rappel en haut des pages de l'entreprise : fin d'essai proche,
 * echeance proche ou depassee (grace), lecture seule. Le rappel simple peut
 * etre masque pour la journee ; la lecture seule reste toujours affichee.
 */
@Component({
  selector: 'app-bandeau-abonnement',
  imports: [DatePipe, RouterLink],
  template: `
    @if (service.etatCourant(); as e) {
      @if (e.rappel && (e.lectureSeule || !masque())) {
        <div class="bandeau" [class.grave]="e.lectureSeule || e.etat === 'GRACE'" role="status">
          <span class="icone" aria-hidden="true">{{ e.lectureSeule ? '🔒' : '⏳' }}</span>
          <span class="texte">
            @if (e.lectureSeule) {
              Abonnement expire : l'application est en <strong>lecture seule</strong> jusqu'au paiement.
            } @else if (e.etat === 'GRACE') {
              Echeance depassee : tout fonctionne encore jusqu'au <strong>{{ e.finGrace | date: 'dd/MM/yyyy' }}</strong>, puis lecture seule.
            } @else if (e.essai) {
              Votre essai gratuit se termine {{ e.joursRestants === 0 ? 'aujourd\\'hui' : 'dans ' + e.joursRestants + ' jour(s)' }}.
            } @else {
              Votre abonnement {{ e.formuleNom }} arrive a echeance le <strong>{{ e.fin | date: 'dd/MM/yyyy' }}</strong>.
            }
          </span>
          @if (estAdmin) {
            <a class="action" routerLink="/abonnement">{{ e.essai ? 'Choisir ma formule' : 'Renouveler' }}</a>
          } @else {
            <span class="discret">Prevenez votre administrateur.</span>
          }
          @if (!e.lectureSeule) {
            <button type="button" class="fermer" (click)="masquer()" aria-label="Masquer pour aujourd'hui">×</button>
          }
        </div>
      }
    }
  `,
  styles: `
    .bandeau { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; margin: 0 0 1rem; padding: .6rem .9rem;
      border-radius: 10px; background: #fdf4e1; border: 1px solid #f1d8a2; color: #6b4a05; font-size: .88rem; }
    .bandeau.grave { background: #fdecec; border-color: #f1c4c4; color: #8b1c1c; }
    .texte { flex: 1; min-width: 200px; }
    .action { font-weight: 700; color: inherit; text-decoration: underline; white-space: nowrap; }
    .discret { opacity: .8; }
    .fermer { border: 0; background: transparent; font-size: 1.2rem; line-height: 1; cursor: pointer; color: inherit; }
  `
})
export class BandeauAbonnement implements OnInit, OnDestroy {
  readonly service = inject(AbonnementService);
  private readonly auth = inject(AuthService);
  readonly estAdmin = this.auth.aRole('ADMIN');
  readonly masque = signal(this.lireMasque());
  private minuterie?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.actualiser();
    // L'etat change rarement : une verification par demi-heure suffit.
    this.minuterie = setInterval(() => this.actualiser(), 30 * 60 * 1000);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
  }

  private actualiser(): void {
    this.service.etat().subscribe({ error: () => undefined });
  }

  masquer(): void {
    try {
      localStorage.setItem(MASQUE_JUSQUA, new Date().toISOString().slice(0, 10));
    } catch {
      // Stockage indisponible : le bandeau se masque seulement pour cette page.
    }
    this.masque.set(true);
  }

  private lireMasque(): boolean {
    try {
      return localStorage.getItem(MASQUE_JUSQUA) === new Date().toISOString().slice(0, 10);
    } catch {
      return false;
    }
  }
}
