import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { LIBELLES_STATUT_DEPENSE } from '../../../core/models/achats';
import { FondsReserve } from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { Modale } from '../../../shared/modale';

/**
 * Fonds de reserve commun : alimente par les apports des associes (depuis leur
 * sous-compte) et les apports directs de l'administrateur (depuis le compte de
 * l'entreprise) ; il paie les depenses communes.
 */
@Component({
  selector: 'app-fonds-reserve-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, RouterLink],
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
  readonly succes = signal<string | null>(null);
  readonly apport = signal<{ montant: number | null; note: string } | null>(null);
  readonly envoi = signal(false);

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

  apporter(): void {
    const a = this.apport();
    if (!a || !(Number(a.montant) > 0) || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.options.apportDirectFonds(Number(a.montant), a.note.trim() || null).subscribe({
      next: (r) => {
        this.envoi.set(false);
        this.apport.set(null);
        this.succes.set(`Apport ${r.numero} de ${r.montant.toLocaleString('fr-FR')} F verse au fonds depuis le compte de l'entreprise.`);
        this.charger();
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.error?.message ?? 'Apport impossible.');
      }
    });
  }
}
