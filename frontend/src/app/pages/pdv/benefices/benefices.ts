import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { Benefices } from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { PERIODES, Periode, bornesPeriode, datesDePeriode } from '../../../shared/periodes';

/**
 * Benefices des associes (option des reglages) : part de chacun sur la marge
 * brute des produits auxquels il est rattache, pour la periode choisie, et ce
 * qui revient a l'entreprise.
 */
@Component({
  selector: 'app-benefices-pdv',
  imports: [DecimalPipe, FormsModule],
  templateUrl: './benefices.html',
  styleUrl: './benefices.scss'
})
export class BeneficesPdv implements OnInit {
  private readonly service = inject(OptionsPdvService);
  private readonly auth = inject(AuthService);
  readonly moi = this.auth.utilisateur()?.id;

  readonly portees = PERIODES;
  readonly portee = signal<Periode>('CE_MOIS');
  readonly dateDebut = signal(datesDePeriode('CE_MOIS').du);
  readonly dateFin = signal(datesDePeriode('CE_MOIS').au);
  readonly datesValides = computed(() => this.portee() !== 'PERSONNALISEE' || this.dateDebut() <= this.dateFin());

  readonly donnees = signal<Benefices | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  /** Associes dont le detail par produit est deplie. */
  readonly deplies = signal<Set<number>>(new Set());

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    if (!this.datesValides()) {
      this.erreur.set('La date de debut doit preceder la date de fin.');
      return;
    }
    const { debut, fin } = bornesPeriode(this.portee(), this.dateDebut(), this.dateFin());
    this.chargement.set(true);
    this.service.benefices(debut, fin).subscribe({
      next: (b) => {
        this.donnees.set(b);
        this.chargement.set(false);
        this.erreur.set(null);
        // L'associe connecte voit d'emblee le detail de sa part.
        if (this.moi && b.associes.some((a) => a.associeId === this.moi)) {
          this.deplies.update((s) => new Set([...s, this.moi!]));
        }
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Calcul des benefices impossible.');
      }
    });
  }

  changerPortee(portee: Periode): void {
    if (portee === 'PERSONNALISEE' && this.portee() !== 'PERSONNALISEE') {
      const { du, au } = datesDePeriode(this.portee());
      this.dateDebut.set(du);
      this.dateFin.set(au);
    }
    this.portee.set(portee);
    this.charger();
  }

  changerDate(borne: 'debut' | 'fin', valeur: string): void {
    (borne === 'debut' ? this.dateDebut : this.dateFin).set(valeur);
    this.charger();
  }

  basculer(id: number): void {
    this.deplies.update((s) => {
      const n = new Set(s);
      if (n.has(id)) {
        n.delete(id);
      } else {
        n.add(id);
      }
      return n;
    });
  }

  part(valeur: number, total: number): number {
    return total ? Math.round((valeur / total) * 1000) / 10 : 0;
  }
}
