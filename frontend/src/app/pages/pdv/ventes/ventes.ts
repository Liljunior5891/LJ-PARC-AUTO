import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { Page } from '../../../core/models/models';
import { SessionCaisse, Ticket, VenteResume } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { SocieteService } from '../../../core/services/societe.service';
import { Modale } from '../../../shared/modale';
import { imprimerTicket } from '../../../shared/ticket-pdv';

/** Date du jour au format des champs de saisie (aaaa-mm-jj), dans le fuseau du poste. */
function aujourdHui(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Historique des ventes, par periode ou par session de caisse : consultation, reimpression et annulation. */
@Component({
  selector: 'app-ventes-pdv',
  imports: [FormsModule, DecimalPipe, DatePipe, Modale],
  templateUrl: './ventes.html'
})
export class VentesPdv implements OnInit {
  private readonly service = inject(PdvService);
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);
  private readonly route = inject(ActivatedRoute);

  readonly peutAnnuler = this.auth.aRole('ADMIN', 'GERANT');
  /** Point de vente et Marketplace : les ventes en ligne et au comptoir se cotoient ici. */
  readonly avecEnLigne = this.auth.aModule('MARKETPLACE');
  /** Canal filtre : tous, en ligne ou comptoir. */
  canal: '' | 'en-ligne' | 'comptoir' = '';
  readonly page = signal<Page<VenteResume> | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  debut = aujourdHui();
  fin = aujourdHui();
  /** Session de caisse filtree : choisie dans la liste, ou passee par l'adresse depuis l'historique d'une caisse. */
  sessionId: number | null = null;
  /** Sessions recentes de toutes les caisses, pour le filtre (gerant et administrateur). */
  readonly sessions = signal<SessionCaisse[]>([]);
  private numeroPage = 0;

  readonly detail = signal<Ticket | null>(null);
  readonly annulation = signal(false);
  motif = '';

  /** Quincaillerie : facture A4 des ventes, si l'administrateur l'a activee. */
  private readonly materiauxService = inject(MateriauxService);
  readonly materiaux = this.auth.estMateriaux();
  readonly factureA4 = signal(false);

  facture(t: Ticket): void {
    this.materiauxService.facturePdf(t.id).subscribe({
      next: (pdf) => this.materiauxService.ouvrirPdf(pdf),
      error: () => this.erreur.set('Facture indisponible.')
    });
  }

  ngOnInit(): void {
    if (this.materiaux) {
      this.materiauxService.reglages().subscribe({ next: (r) => this.factureA4.set(r.factureA4) });
    }
    const session = this.route.snapshot.queryParamMap.get('session');
    this.sessionId = session ? Number(session) : null;
    if (this.peutAnnuler) {
      this.service.sessions(0, undefined, 100).subscribe({ next: (p) => this.sessions.set(p.content) });
    }
    this.charger();
  }

  choisirSession(id: number | null): void {
    this.sessionId = id;
    this.numeroPage = 0;
    this.charger();
  }

  filtrer(): void {
    this.sessionId = null;
    this.numeroPage = 0;
    this.charger();
  }

  choisirCanal(canal: '' | 'en-ligne' | 'comptoir'): void {
    this.canal = canal;
    this.numeroPage = 0;
    this.charger();
  }

  changerPage(sens: number): void {
    this.numeroPage += sens;
    this.charger();
  }

  charger(): void {
    // Bornes en heure locale : « le 31 » commence a minuit au comptoir, pas a minuit UTC.
    const debut = new Date(`${this.debut}T00:00:00`);
    const fin = new Date(`${this.fin}T00:00:00`);
    fin.setDate(fin.getDate() + 1);
    this.service
      .ventes({
        debut: debut.toISOString(),
        fin: fin.toISOString(),
        sessionId: this.sessionId ?? undefined,
        enLigne: this.canal === '' ? undefined : this.canal === 'en-ligne',
        page: this.numeroPage
      })
      .subscribe({
        next: (p) => this.page.set(p),
        error: () => this.erreur.set('Chargement des ventes impossible.')
      });
  }

  ouvrir(vente: VenteResume): void {
    this.service.ticket(vente.id).subscribe({ next: (t) => this.detail.set(t) });
  }

  imprimer(ticket: Ticket): void {
    this.societeService.lire().subscribe({
      next: (societe) => imprimerTicket(ticket, societe),
      error: () => imprimerTicket(ticket, null)
    });
  }

  annuler(ticket: Ticket): void {
    this.service.annuler(ticket.id, this.motif).subscribe({
      next: (t) => {
        this.detail.set(t);
        this.annulation.set(false);
        this.motif = '';
        this.succes.set(`Commande ${t.numero} annulee ; les articles sont revenus en stock.`);
        this.charger();
      },
      error: (e) => this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Annulation impossible.')
    });
  }
}
