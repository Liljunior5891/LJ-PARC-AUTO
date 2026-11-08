import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FiltresTransactions, ResumeTransactions, ServiceFinancier, Transaction } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Historique des transactions avec recherche et filtres (periode, service,
 * reseau, operation, statut, montant, reference). Un caissier ne voit que ses
 * operations ; l'annulation, motivee, passe par l'ecriture inverse.
 */
@Component({
  selector: 'app-transactions-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, SuiteListe],
  templateUrl: './transactions.html',
  styleUrl: './transactions.scss'
})
export class TransactionsAgences implements OnInit {
  readonly service = inject(AgencesService);

  readonly catalogue = signal<ServiceFinancier[]>([]);
  readonly page = signal<Page<Transaction> | null>(null);
  readonly resume = signal<ResumeTransactions | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  private readonly suite = new SuiteDePages();

  f: FiltresTransactions = { du: this.jour(0), au: this.jour(0), statut: '' };
  readonly serviceFiltre = computed(() => this.catalogue().find((s) => s.id === this.serviceFiltreId()));
  private readonly serviceFiltreId = signal<number | null>(null);

  readonly detail = signal<Transaction | null>(null);
  readonly aAnnuler = signal<Transaction | null>(null);
  motif = '';

  ngOnInit(): void {
    this.service.chargerMoi().subscribe();
    this.service.catalogue().subscribe({ next: (c) => this.catalogue.set(c) });
    this.charger();
  }

  charger(): void {
    this.service.transactions(this.f, this.suite.numero()).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set(this.message(e, 'Chargement de l\'historique impossible.'))
    });
    if (this.page() == null || this.page()!.number === 0) {
      this.service.resumeTransactions(this.f).subscribe({ next: (r) => this.resume.set(r) });
    }
  }

  filtrer(): void {
    this.page.set(null);
    this.serviceFiltreId.set(this.f.serviceId ?? null);
    this.charger();
  }

  changerService(): void {
    this.f.typeOperationId = null;
    this.f.reseauId = null;
    this.filtrer();
  }

  periode(jours: number | 'mois'): void {
    if (jours === 'mois') {
      const d = new Date();
      this.f.du = new Date(d.getFullYear(), d.getMonth(), 1, 12).toISOString().slice(0, 10);
      this.f.au = this.jour(0);
    } else {
      this.f.du = this.jour(jours);
      this.f.au = jours === -1 ? this.jour(-1) : this.jour(0);
    }
    this.filtrer();
  }

  suiteListe(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  demanderAnnulation(t: Transaction): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aAnnuler.set(t);
  }

  annuler(): void {
    const t = this.aAnnuler();
    if (!t || !this.motif.trim()) {
      return;
    }
    this.service.annulerTransaction(t.id, this.motif.trim()).subscribe({
      next: (r) => {
        this.aAnnuler.set(null);
        this.detail.set(null);
        this.succes.set(`${r.reference} annulee : l'ecriture inverse a ete passee au registre.`);
        this.filtrer();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Annulation impossible.'))
    });
  }

  private jour(decalage: number): string {
    const d = new Date();
    d.setDate(d.getDate() + decalage);
    return d.toISOString().slice(0, 10);
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
