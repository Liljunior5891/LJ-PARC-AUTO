import { DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CommandeEnLigne, ETAPES_COMMANDE, ResumeMarketplace, StatutCommandeEnLigne } from '../../core/models/marketplace';
import { Page } from '../../core/models/models';
import { MarketplaceService } from '../../core/services/marketplace.service';
import { Modale } from '../../shared/modale';

/** Les nouvelles commandes arrivent sans prevenir : la liste se rafraichit seule. */
const RAFRAICHISSEMENT_MS = 30_000;

/**
 * Commandes passees dans la boutique en ligne : confirmation, preparation,
 * livraison ou retrait, paiement, annulation.
 */
@Component({
  selector: 'app-commandes-en-ligne',
  imports: [FormsModule, DecimalPipe, DatePipe, LowerCasePipe, Modale, RouterLink],
  templateUrl: './commandes-en-ligne.html',
  styleUrl: './marketplace.scss'
})
export class CommandesEnLigne implements OnInit, OnDestroy {
  private readonly service = inject(MarketplaceService);
  private minuterie?: ReturnType<typeof setInterval>;

  readonly etapes = ETAPES_COMMANDE;
  readonly resume = signal<ResumeMarketplace | null>(null);
  readonly page = signal<Page<CommandeEnLigne> | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly filtre = signal<'en-cours' | 'terminees' | 'toutes'>('en-cours');
  private numeroPage = 0;

  readonly detail = signal<CommandeEnLigne | null>(null);
  readonly annulation = signal(false);
  motifAnnulation = '';
  readonly action = signal(false);
  readonly copie = signal(false);

  ngOnInit(): void {
    this.charger();
    this.minuterie = setInterval(() => this.charger(true), RAFRAICHISSEMENT_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
  }

  charger(silencieux = false): void {
    if (!silencieux) {
      this.chargement.set(true);
    }
    this.service.resume().subscribe({ next: (r) => this.resume.set(r) });
    this.service.commandes(this.filtre(), this.numeroPage).subscribe({
      next: (page) => {
        this.page.set(page);
        this.chargement.set(false);
        // La fenetre ouverte suit les changements faits ailleurs.
        const ouverte = this.detail();
        if (ouverte) {
          const fraiche = page.content.find((c) => c.id === ouverte.id);
          if (fraiche) {
            this.detail.set(fraiche);
          }
        }
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des commandes impossible.');
      }
    });
  }

  changerFiltre(filtre: 'en-cours' | 'terminees' | 'toutes'): void {
    this.filtre.set(filtre);
    this.numeroPage = 0;
    this.charger();
  }

  changerPage(delta: number): void {
    this.numeroPage = Math.max(0, this.numeroPage + delta);
    this.charger();
  }

  ouvrir(commande: CommandeEnLigne): void {
    this.erreur.set(null);
    this.annulation.set(false);
    this.motifAnnulation = '';
    this.detail.set(commande);
  }

  avancer(commande: CommandeEnLigne, statut: StatutCommandeEnLigne): void {
    this.executer(this.service.changerStatut(commande.id, statut));
  }

  annuler(commande: CommandeEnLigne): void {
    this.executer(this.service.changerStatut(commande.id, 'ANNULEE', this.motifAnnulation.trim() || undefined));
  }

  marquerPayee(commande: CommandeEnLigne): void {
    this.executer(this.service.marquerPayee(commande.id));
  }

  copierSuivi(commande: CommandeEnLigne): void {
    void navigator.clipboard?.writeText(`${window.location.origin}/suivi/${commande.jeton}`).then(() => {
      this.copie.set(true);
      setTimeout(() => this.copie.set(false), 2000);
    });
  }

  /** Lien WhatsApp vers le client, numero nettoye. */
  whatsapp(telephone: string): string {
    return `https://wa.me/${telephone.replace(/[^0-9]/g, '')}`;
  }

  private executer(appel: ReturnType<MarketplaceService['marquerPayee']>): void {
    if (this.action()) {
      return;
    }
    this.action.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (commande) => {
        this.action.set(false);
        this.annulation.set(false);
        this.detail.set(commande);
        this.charger(true);
      },
      error: (reponse) => {
        this.action.set(false);
        this.erreur.set(reponse.error?.message ?? 'Operation impossible.');
      }
    });
  }
}
