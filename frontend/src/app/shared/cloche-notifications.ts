import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NiveauNotification, Notification } from '../core/models/pilotage';
import { PilotageService } from '../core/services/pilotage.service';

/** Les notifications sont recalculees cote API : on les relit regulierement. */
const INTERVALLE_RAFRAICHISSEMENT = 5 * 60 * 1000;
const RANG: Record<NiveauNotification, number> = { CRITIQUE: 0, AVERTISSEMENT: 1, INFORMATION: 2 };

/**
 * Cloche de la barre du haut : ce qui attend une decision, tous modules
 * confondus (documents, entretiens, incidents, alertes GPS). La pastille
 * donne le nombre en attente, rouge s'il y a du critique ; le panneau les
 * liste, les plus urgentes d'abord, chacune menant a l'ecran qui la traite.
 */
@Component({
  selector: 'app-cloche-notifications',
  imports: [RouterLink, DatePipe],
  templateUrl: './cloche-notifications.html',
  styleUrl: './cloche-notifications.scss',
  host: {
    '(document:click)': 'clicDocument($event)',
    '(document:keydown.escape)': 'ouvert.set(false)'
  }
})
export class ClocheNotifications {
  private readonly service = inject(PilotageService);
  private readonly element = inject(ElementRef<HTMLElement>);

  readonly notifications = signal<Notification[]>([]);
  readonly ouvert = signal(false);
  readonly chargement = signal(false);

  readonly triees = computed(() =>
    [...this.notifications()].sort((a, b) => RANG[a.niveau] - RANG[b.niveau])
  );
  readonly critiques = computed(() => this.compte('CRITIQUE'));

  constructor() {
    this.charger();
    const minuteur = setInterval(() => this.charger(), INTERVALLE_RAFRAICHISSEMENT);
    inject(DestroyRef).onDestroy(() => clearInterval(minuteur));
  }

  charger(): void {
    this.chargement.set(true);
    this.service.notifications().subscribe({
      next: (notifications) => {
        this.notifications.set(notifications);
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false)
    });
  }

  /** L'ouverture relit la liste : elle doit refleter l'etat du moment. */
  basculer(): void {
    const ouvrir = !this.ouvert();
    this.ouvert.set(ouvrir);
    if (ouvrir) {
      this.charger();
    }
  }

  compte(niveau: NiveauNotification): number {
    return this.notifications().filter((n) => n.niveau === niveau).length;
  }

  /** Un clic hors de la cloche et de son panneau referme le panneau. */
  clicDocument(evenement: MouseEvent): void {
    if (this.ouvert() && !this.element.nativeElement.contains(evenement.target as Node)) {
      this.ouvert.set(false);
    }
  }
}
