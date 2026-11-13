import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { LIBELLES_STATUT_DEPENSE } from '../../../core/models/achats';
import { FondsReserve } from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';

/**
 * Fonds de reserve du point de vente : solde, dotations de chaque mois
 * (pourcentage du benefice net) et depenses payees par le fonds.
 */
@Component({
  selector: 'app-fonds-reserve-pdv',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './fonds-reserve.html',
  styleUrl: './fonds-reserve.scss'
})
export class FondsReservePdv implements OnInit {
  private readonly options = inject(OptionsPdvService);
  readonly estAdmin = inject(AuthService).aRole('ADMIN');
  readonly libelles = LIBELLES_STATUT_DEPENSE;

  readonly fonds = signal<FondsReserve | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.options.fondsReserve().subscribe({
      next: (f) => {
        this.fonds.set(f);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Chargement du fonds de reserve impossible.');
      }
    });
  }
}
