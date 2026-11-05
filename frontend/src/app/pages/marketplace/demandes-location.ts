import { DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DemandeLocationEnLigne, StatutDemandeLocation } from '../../core/models/marketplace';
import { Page } from '../../core/models/models';
import { MarketplaceService } from '../../core/services/marketplace.service';
import { Modale } from '../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';

/** Reponse en cours de saisie : acceptation (contrat) ou refus. */
interface Reponse {
  demande: DemandeLocationEnLigne;
  accepter: boolean;
  tarifJour: number | null;
  chauffeurId: number | null;
  observations: string;
  motif: string;
}

/**
 * Demandes de location de vehicules faites en ligne. Acceptee, une demande
 * reserve le vehicule ; quand le client se presente en agence, on enregistre
 * sa recette (bouton « Enregistrer la recette »), ce qui la conclut.
 */
@Component({
  selector: 'app-demandes-location',
  imports: [SuiteListe, FormsModule, DecimalPipe, DatePipe, LowerCasePipe, RouterLink, Modale],
  templateUrl: './demandes-location.html',
  styleUrl: './marketplace.scss'
})
export class DemandesLocation implements OnInit {
  private readonly service = inject(MarketplaceService);

  readonly page = signal<Page<DemandeLocationEnLigne> | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Toutes les demandes par defaut, chacune avec son statut ; le filtre restreint a un statut. */
  readonly filtre = signal<StatutDemandeLocation | ''>('');
  private readonly suite = new SuiteDePages();

  readonly reponse = signal<Reponse | null>(null);
  /** Demande acceptee dont le client se desiste. */
  readonly annulation = signal<DemandeLocationEnLigne | null>(null);
  motifAnnulation = '';
  readonly envoi = signal(false);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.demandes(this.filtre(), this.suite.numero()).subscribe({
      next: (page) => {
        this.page.set(cumulerPage(this.page(), page));
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des demandes impossible.');
      }
    });
  }

  changerFiltre(statut: StatutDemandeLocation | ''): void {
    this.filtre.set(statut);
    this.charger();
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  repondre(demande: DemandeLocationEnLigne, accepter: boolean): void {
    this.erreur.set(null);
    this.reponse.set({
      demande, accepter, tarifJour: demande.tarifJour, chauffeurId: null, observations: '', motif: ''
    });
  }

  confirmer(): void {
    const r = this.reponse();
    if (!r || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const appel = r.accepter
      ? this.service.accepterDemande(r.demande.id, r.tarifJour, r.chauffeurId, r.observations)
      : this.service.refuserDemande(r.demande.id, r.motif);
    appel.subscribe({
      next: (demande) => {
        this.envoi.set(false);
        this.reponse.set(null);
        this.succes.set(r.accepter
          ? `Demande ${demande.numero} acceptee : le vehicule est reserve en attendant le client.`
          : 'Demande refusee : le client le voit sur son lien de suivi.');
        this.charger();
      },
      error: (reponse) => {
        this.envoi.set(false);
        this.erreur.set(reponse.error?.message ?? 'Operation impossible.');
      }
    });
  }

  annuler(demande: DemandeLocationEnLigne): void {
    this.envoi.set(true);
    this.service.annulerDemande(demande.id, this.motifAnnulation.trim()).subscribe({
      next: () => {
        this.envoi.set(false);
        this.annulation.set(null);
        this.motifAnnulation = '';
        this.succes.set(`Demande ${demande.numero} annulee : le vehicule est libere.`);
        this.charger();
      },
      error: (reponse) => {
        this.envoi.set(false);
        this.erreur.set(reponse.error?.message ?? 'Annulation impossible.');
      }
    });
  }

  whatsapp(telephone: string): string {
    return `https://wa.me/${telephone.replace(/[^0-9]/g, '')}`;
  }
}
