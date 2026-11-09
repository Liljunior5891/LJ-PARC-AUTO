import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EmployePresence, HistoriquePresence } from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';
import { HistoriqueEmploye } from '../historique-employe';

/** Fiche de presence d'un employe : son mois jour par jour, ses retards et ses absences. */
@Component({
  selector: 'app-fiche-employe',
  imports: [DatePipe, FormsModule, RouterLink, HistoriqueEmploye],
  template: `
    <div class="entete-page">
      <div>
        @if (employe(); as e) {
          <h1>{{ e.nomComplet }} @if (!e.actif) { <span class="etiquette inactif">Inactif</span> }</h1>
          <p class="sous-titre">
            {{ e.matricule }}{{ e.fonction ? ' · ' + e.fonction : '' }}{{ e.service ? ' · ' + e.service : '' }}
            @if (e.dateEmbauche) { · embauche le {{ e.dateEmbauche | date: 'dd/MM/yyyy' }} }
            @if (e.utilisateurEmail) { · 👤 {{ e.utilisateurEmail }} }
          </p>
        }
      </div>
      <div class="actions-entete">
        <a class="bouton secondaire" routerLink="/presence/employes">‹ Retour</a>
      </div>
    </div>
    <div class="carte barre-filtres">
      <div class="filtres-ligne">
        <button class="bouton secondaire" type="button" (click)="decaler(-1)" aria-label="Mois precedent">‹</button>
        <input type="month" [max]="moisCourant" [ngModel]="mois()" (ngModelChange)="changerMois($event)" aria-label="Mois" />
        <button class="bouton secondaire" type="button" (click)="decaler(1)" [disabled]="mois() >= moisCourant" aria-label="Mois suivant">›</button>
        @if (mois() !== moisCourant) {
          <button class="bouton secondaire info" type="button" (click)="changerMois(moisCourant)">Mois en cours</button>
        }
      </div>
    </div>
    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (historique(); as h) {
      <app-historique-employe [historique]="h" />
    } @else if (!erreur()) {
      <p class="message vide">Chargement...</p>
    }
  `,
  styles: [`
    .actions-entete a.bouton { text-decoration: none; }
    h1 .etiquette { vertical-align: middle; }
  `]
})
export class FicheEmploye implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(PresenceService);

  readonly moisCourant = new Date().toISOString().slice(0, 7);
  readonly mois = signal(this.moisCourant);
  readonly employe = signal<EmployePresence | null>(null);
  readonly historique = signal<HistoriquePresence | null>(null);
  readonly erreur = signal<string | null>(null);
  private id = 0;

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.service.employe(this.id).subscribe({
      next: (e) => this.employe.set(e),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Employe introuvable.')
    });
    this.charger();
  }

  changerMois(mois: string): void {
    this.mois.set(mois || this.moisCourant);
    this.charger();
  }

  decaler(n: number): void {
    const [a, m] = this.mois().split('-').map(Number);
    const d = new Date(a, m - 1 + n, 1);
    const suivant = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    if (suivant <= this.moisCourant) {
      this.changerMois(suivant);
    }
  }

  private charger(): void {
    this.service.historique(this.id, this.mois()).subscribe({
      next: (h) => this.historique.set(h),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Historique indisponible.')
    });
  }
}
