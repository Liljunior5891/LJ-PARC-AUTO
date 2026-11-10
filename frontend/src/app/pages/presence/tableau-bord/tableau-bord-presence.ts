import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ETIQUETTES_STATUT_PRESENCE,
  JourPresence,
  LIBELLES_MODE_POINTAGE,
  LIBELLES_STATUT_PRESENCE,
  LieuPresence,
  StatutPresence,
  dureeMinutes
} from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';

/** Rafraichissement de la journee en cours. */
const INTERVALLE = 30000;

/**
 * Presences du jour : qui est arrive, a quelle heure, avec quel retard, et qui
 * manque encore. La journee en cours se met a jour toute seule.
 */
@Component({
  selector: 'app-tableau-bord-presence',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './tableau-bord-presence.html',
  styleUrl: './tableau-bord-presence.scss'
})
export class TableauBordPresence implements OnInit {
  private readonly service = inject(PresenceService);
  private readonly destroyRef = inject(DestroyRef);

  /** Jour courant, avance a minuit : un ecran laisse ouvert passe tout seul au jour suivant. */
  readonly aujourdhui = signal(new Date().toISOString().slice(0, 10));
  readonly date = signal(this.aujourdhui());
  readonly lieuId = signal<number | null>(null);
  readonly filtre = signal<StatutPresence | 'TOUS'>('TOUS');
  readonly recherche = signal('');
  readonly jour = signal<JourPresence | null>(null);
  readonly lieux = signal<LieuPresence[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly libelles = LIBELLES_STATUT_PRESENCE;
  readonly etiquettes = ETIQUETTES_STATUT_PRESENCE;
  readonly modes = LIBELLES_MODE_POINTAGE;
  readonly duree = dureeMinutes;

  readonly lignes = computed(() => {
    const j = this.jour();
    const f = this.filtre();
    const q = this.recherche().trim().toLowerCase();
    if (!j) {
      return [];
    }
    return j.lignes.filter((l) => (f === 'TOUS' || l.statut === f)
      && (!q || `${l.nom} ${l.matricule} ${l.fonction ?? ''}`.toLowerCase().includes(q)));
  });

  /** Minutes de retard cumulees de la journee. */
  readonly retardTotal = computed(() => (this.jour()?.lignes ?? []).reduce((t, l) => t + (l.retardMinutes || 0), 0));

  /** Part d'un compteur parmi les employes attendus, en pourcentage entier. */
  part(n: number): number {
    const attendus = this.jour()?.compteurs.attendus ?? 0;
    return attendus ? Math.round((n * 100) / attendus) : 0;
  }

  ngOnInit(): void {
    this.charger();
    this.service.lieux().subscribe({ next: (l) => this.lieux.set(l) });
    const minuterie = setInterval(() => {
      const veille = this.aujourdhui();
      const jour = new Date().toISOString().slice(0, 10);
      if (jour !== veille) {
        this.aujourdhui.set(jour);
        if (this.date() === veille) {
          this.date.set(jour);
        }
      }
      if (this.date() === this.aujourdhui() && !document.hidden) {
        this.charger();
      }
    }, INTERVALLE);
    this.destroyRef.onDestroy(() => clearInterval(minuterie));
  }

  changerDate(date: string): void {
    this.date.set(date || this.aujourdhui());
    this.charger();
  }

  decaler(jours: number): void {
    const d = new Date(this.date() + 'T12:00:00');
    d.setDate(d.getDate() + jours);
    const iso = d.toISOString().slice(0, 10);
    if (iso <= this.aujourdhui()) {
      this.changerDate(iso);
    }
  }

  changerLieu(id: number | null): void {
    this.lieuId.set(id);
    this.charger();
  }

  charger(): void {
    this.service.jour(this.date(), this.lieuId()).subscribe({
      next: (j) => {
        this.erreur.set(null);
        this.jour.set(j);
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement impossible.')
    });
  }
}
