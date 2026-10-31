import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Page } from '../../../core/models/models';
import { SessionCaisse, Ticket, VenteProduitSession, VenteResume } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { SocieteService } from '../../../core/services/societe.service';
import { Modale } from '../../../shared/modale';
import { COUPURES_FCFA, FermetureSession, MouvementSession, telechargerRapportSession } from '../../../shared/session-caisse';
import { imprimerTicket } from '../../../shared/ticket-pdv';

type Onglet = 'commandes' | 'produits' | 'mouvements' | 'billettage';

/**
 * Detail d'une session de caisse : chiffres cles, ventes par commande et par
 * produit, entrees et sorties d'especes, billettage d'ouverture et de
 * fermeture, avec le rapport PDF. Une session ouverte se ferme d'ici.
 */
@Component({
  selector: 'app-session-detail-pdv',
  imports: [DatePipe, DecimalPipe, RouterLink, Modale, FermetureSession, MouvementSession],
  templateUrl: './session-detail.html',
  styleUrl: './session-detail.scss'
})
export class SessionDetailPdv implements OnInit {
  private readonly service = inject(PdvService);
  private readonly societeService = inject(SocieteService);
  private readonly route = inject(ActivatedRoute);

  readonly session = signal<SessionCaisse | null>(null);
  readonly onglet = signal<Onglet>('commandes');
  readonly ventes = signal<Page<VenteResume> | null>(null);
  readonly produits = signal<VenteProduitSession[]>([]);
  readonly ticket = signal<Ticket | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly aFermer = signal<SessionCaisse | null>(null);
  readonly mouvement = signal<SessionCaisse | null>(null);
  private numeroPage = 0;
  private sessionId = 0;

  readonly totalProduits = computed(() => this.produits().reduce((s, p) => s + p.total, 0));
  readonly quantiteProduits = computed(() => this.produits().reduce((s, p) => s + p.quantite, 0));

  /** Billettage : une ligne par coupure comptee a l'ouverture ou a la fermeture. */
  readonly billettage = computed(() => {
    const s = this.session();
    if (!s) {
      return [];
    }
    const quantite = (liste: { valeur: number; quantite: number }[], v: number) =>
      liste.find((c) => c.valeur === v)?.quantite ?? 0;
    return COUPURES_FCFA
      .map((v) => ({ valeur: v, ouverture: quantite(s.billettageOuverture, v), fermeture: quantite(s.billettageFermeture, v) }))
      .filter((l) => l.ouverture > 0 || l.fermeture > 0);
  });

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('session'));
    this.chargerSession();
    this.chargerVentes();
    this.service.ventesParProduit(this.sessionId).subscribe({ next: (p) => this.produits.set(p) });
  }

  changerPage(sens: number): void {
    this.numeroPage += sens;
    this.chargerVentes();
  }

  ouvrirTicket(vente: VenteResume): void {
    this.service.ticket(vente.id).subscribe({ next: (t) => this.ticket.set(t) });
  }

  imprimer(ticket: Ticket): void {
    this.societeService.lire().subscribe({
      next: (societe) => imprimerTicket(ticket, societe),
      error: () => imprimerTicket(ticket, null)
    });
  }

  telecharger(s: SessionCaisse): void {
    telechargerRapportSession(this.service, s, (m) => this.erreur.set(m));
  }

  /** Totaux et commandes en cours relus au moment de fermer. */
  fermer(s: SessionCaisse): void {
    this.service.session(s.id).subscribe({ next: (session) => this.aFermer.set(session) });
  }

  apresChangement(s: SessionCaisse): void {
    this.mouvement.set(null);
    this.session.set(s);
  }

  private chargerSession(): void {
    this.service.session(this.sessionId).subscribe({
      next: (s) => this.session.set(s),
      error: () => this.erreur.set('Session introuvable.')
    });
  }

  private chargerVentes(): void {
    this.service.ventes({ sessionId: this.sessionId, page: this.numeroPage }).subscribe({
      next: (p) => this.ventes.set(p),
      error: () => this.erreur.set('Chargement des ventes impossible.')
    });
  }
}
