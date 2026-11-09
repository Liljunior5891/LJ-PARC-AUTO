import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FiltresTransactions, ResumeTransactions, ServiceFinancier, Transaction } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { ActivatedRoute, Router } from '@angular/router';
import { LogoService, attributsOption } from '../../../shared/logo-service';
import { MaCaisseAgence } from '../caisses/ma-caisse-agence';
import { Modale } from '../../../shared/modale';
import { FormulaireTransaction } from './formulaire-transaction';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Historique des transactions avec recherche et filtres (periode, service,
 * reseau, operation, statut, montant, reference). Un caissier ne voit que ses
 * operations ; l'annulation, motivee, passe par l'ecriture inverse.
 */
@Component({
  selector: 'app-transactions-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, FormulaireTransaction, LogoService, MaCaisseAgence, Modale, SuiteListe],
  templateUrl: './transactions.html',
  styleUrl: './transactions.scss'
})
export class TransactionsAgences implements OnInit {
  readonly service = inject(AgencesService);

  readonly catalogue = signal<ServiceFinancier[]>([]);
  readonly opt = attributsOption;

  /** Logo du reseau de la transaction, sinon celui de son service. */
  logoDe(t: Transaction): string | null | undefined {
    const s = this.catalogue().find((x) => x.id === t.serviceId);
    return s?.reseaux.find((r) => r.id === t.reseauId)?.logo ?? s?.logo;
  }
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

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  /** Fenetre de saisie d'une nouvelle transaction. */
  readonly saisieOuverte = signal(false);

  ngOnInit(): void {
    // « Nouvelle transaction » du menu arrive ici avec ?nouvelle=1 : la fenetre s'ouvre, le parametre s'efface.
    this.route.queryParamMap.subscribe((p) => {
      if (p.get('nouvelle')) {
        this.saisieOuverte.set(true);
        this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
      }
    });
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

  /** Periode choisie dans la liste, comme au PDV ; « Entre deux dates » montre les deux dates. */
  portee = 'AUJOURDHUI';
  readonly portees = [
    { valeur: 'AUJOURDHUI', libelle: 'Aujourd\'hui' },
    { valeur: 'HIER', libelle: 'Hier' },
    { valeur: '7J', libelle: '7 derniers jours' },
    { valeur: 'MOIS', libelle: 'Ce mois' },
    { valeur: 'PERSONNALISEE', libelle: 'Entre deux dates...' }
  ];

  changerPortee(valeur: string): void {
    this.portee = valeur;
    const jours: Record<string, number | 'mois'> = { AUJOURDHUI: 0, HIER: -1, '7J': -6, MOIS: 'mois' };
    if (valeur in jours) {
      this.periode(jours[valeur]);
    }
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

  /** Transaction validee dans la fenetre : message et liste a jour. */
  transactionEnregistree(t: Transaction): void {
    this.succes.set(`${t.reference} validee : ${t.typeLibelle} ${t.reseauNom ?? t.serviceNom} de ${t.montant.toLocaleString('fr-FR')} F, `
      + `commission ${t.commission.toLocaleString('fr-FR')} F.`);
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
