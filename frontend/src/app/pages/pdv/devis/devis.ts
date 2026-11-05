import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Page } from '../../../core/models/models';
import { Devis, StatutDevis } from '../../../core/models/materiaux';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Devis (pro forma) de la quincaillerie. Ils s'etablissent depuis la caisse
 * (le panier devient un devis) ; ici on les retrouve, on les imprime, on les
 * prolonge ou les annule, et on les rouvre en caisse pour les modifier ou les
 * encaisser.
 */
@Component({
  selector: 'app-devis-pdv',
  imports: [SuiteListe, FormsModule, DecimalPipe, DatePipe, Modale],
  templateUrl: './devis.html'
})
export class DevisPdv implements OnInit {
  private readonly service = inject(MateriauxService);
  private readonly router = inject(Router);

  readonly page = signal<Page<Devis> | null>(null);
  readonly detail = signal<Devis | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  statut: StatutDevis | '' = 'EN_COURS';
  recherche = '';
  nouvelleValidite = '';
  readonly prolongation = signal(false);
  private readonly suite = new SuiteDePages();

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.devis({ q: this.recherche.trim() || undefined, statut: this.statut, page: this.suite.numero() }).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Devis illisibles.')
    });
  }

  filtrer(): void {
    this.charger();
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  ouvrir(d: Devis): void {
    this.erreur.set(null);
    this.prolongation.set(false);
    this.service.unDevis(d.id).subscribe({
      next: (complet) => this.detail.set(complet),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Devis illisible.')
    });
  }

  imprimer(d: Devis): void {
    this.service.devisPdf(d.id).subscribe({
      next: (pdf) => this.service.ouvrirPdf(pdf),
      error: () => this.erreur.set('PDF du devis indisponible.')
    });
  }

  /** Rouvre le devis dans la caisse : modification du panier, ou encaissement. */
  enCaisse(d: Devis): void {
    this.router.navigate(['/pdv/caisse'], { queryParams: { devis: d.id } });
  }

  /** Un devis se compose dans le panier de la caisse. */
  nouveau(): void {
    this.router.navigate(['/pdv/caisse'], { queryParams: { nouveauDevis: 1 } });
  }

  ouvrirProlongation(d: Devis): void {
    const date = new Date();
    date.setDate(date.getDate() + 15);
    this.nouvelleValidite = date.toISOString().slice(0, 10);
    this.prolongation.set(true);
    this.detail.set(d);
  }

  prolonger(d: Devis): void {
    this.service.prolongerDevis(d.id, this.nouvelleValidite).subscribe({
      next: (maj) => {
        this.detail.set(maj);
        this.prolongation.set(false);
        this.succes.set(`Devis ${maj.numero} valable jusqu'au ${new Date(maj.valableJusqu).toLocaleDateString('fr-FR')}.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Prolongation impossible.')
    });
  }

  annuler(d: Devis): void {
    this.service.annulerDevis(d.id).subscribe({
      next: (maj) => {
        this.detail.set(null);
        this.succes.set(`Devis ${maj.numero} annule.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  classeStatut(d: Devis): string {
    if (d.statut === 'CONVERTI') {
      return 'disponible';
    }
    if (d.statut === 'ANNULE' || d.expire) {
      return 'immobilise';
    }
    return 'en_mission';
  }
}
