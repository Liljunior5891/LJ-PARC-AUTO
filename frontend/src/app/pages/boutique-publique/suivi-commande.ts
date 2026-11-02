import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { StatutCommandeEnLigne, SuiviEnLigne } from '../../core/models/marketplace';
import { BoutiquePubliqueService } from '../../core/services/marketplace.service';

/** Le client garde souvent la page ouverte en attendant : elle se met a jour seule. */
const RAFRAICHISSEMENT_MS = 20_000;

/**
 * Suivi d'une commande ou d'une demande de location, ouvert par le lien remis
 * au client apres sa commande. Poursuit aussi le paiement en ligne attendu.
 */
@Component({
  selector: 'app-suivi-commande',
  imports: [DecimalPipe, DatePipe, RouterLink],
  templateUrl: './suivi-commande.html',
  styleUrl: './suivi-commande.scss'
})
export class SuiviCommande implements OnInit, OnDestroy {
  private readonly service = inject(BoutiquePubliqueService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly titre = inject(Title);
  private minuterie?: ReturnType<typeof setInterval>;

  readonly jeton = this.route.snapshot.paramMap.get('jeton') ?? '';
  readonly suivi = signal<SuiviEnLigne | null>(null);
  readonly introuvable = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly paiementEnCours = signal(false);
  readonly copie = signal(false);

  /** Etapes affichees pour une commande, selon le mode de remise. */
  readonly etapes = computed(() => {
    const c = this.suivi()?.commande;
    if (!c) {
      return [];
    }
    const liste: { statut: StatutCommandeEnLigne; libelle: string }[] = [
      { statut: 'NOUVELLE', libelle: 'Recue' },
      { statut: 'CONFIRMEE', libelle: 'Confirmee' },
      { statut: 'PRETE', libelle: c.modeRemise === 'LIVRAISON' ? 'Preparee' : 'Prete a retirer' }
    ];
    if (c.modeRemise === 'LIVRAISON') {
      liste.push({ statut: 'EN_LIVRAISON', libelle: 'En route' });
    }
    liste.push({ statut: 'LIVREE', libelle: c.modeRemise === 'LIVRAISON' ? 'Livree' : 'Retiree' });
    return liste;
  });

  readonly rangActuel = computed(() => {
    const statut = this.suivi()?.commande?.statut;
    const ordre: StatutCommandeEnLigne[] = ['NOUVELLE', 'CONFIRMEE', 'PRETE', 'EN_LIVRAISON', 'LIVREE'];
    return statut ? ordre.indexOf(statut) : -1;
  });

  readonly attendPaiement = computed(() => {
    const c = this.suivi()?.commande;
    return !!c && c.modePaiement === 'MOBILE_MONEY' && c.statutPaiement !== 'PAYEE' && c.statut !== 'ANNULEE';
  });

  ngOnInit(): void {
    this.charger();
    this.minuterie = setInterval(() => this.charger(), RAFRAICHISSEMENT_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
  }

  rang(statut: StatutCommandeEnLigne): number {
    return ['NOUVELLE', 'CONFIRMEE', 'PRETE', 'EN_LIVRAISON', 'LIVREE'].indexOf(statut);
  }

  /** Paiement de demonstration : le client confirme lui-meme. */
  confirmerSimulation(): void {
    this.paiementEnCours.set(true);
    this.service.confirmerPaiementSimule(this.jeton).subscribe({
      next: (suivi) => {
        this.paiementEnCours.set(false);
        this.suivi.set(suivi);
        void this.router.navigate([], { queryParams: {}, replaceUrl: true });
      },
      error: (reponse) => {
        this.paiementEnCours.set(false);
        this.erreur.set(reponse.error?.message ?? 'Paiement impossible.');
      }
    });
  }

  /** Relance le paiement en ligne : page du fournisseur, ou paiement de demonstration. */
  payer(): void {
    this.paiementEnCours.set(true);
    this.erreur.set(null);
    this.service.relancerPaiement(this.jeton).subscribe({
      next: (suivi) => {
        this.paiementEnCours.set(false);
        this.suivi.set(suivi);
        const url = suivi.urlPaiement;
        if (url && /^https?:\/\//.test(url)) {
          window.location.href = url;
        } else if (url) {
          void this.router.navigateByUrl(url);
        }
      },
      error: (reponse) => {
        this.paiementEnCours.set(false);
        this.erreur.set(reponse.error?.message ?? 'Paiement impossible pour le moment.');
      }
    });
  }

  copierLien(): void {
    void navigator.clipboard?.writeText(window.location.origin + '/suivi/' + this.jeton).then(() => {
      this.copie.set(true);
      setTimeout(() => this.copie.set(false), 2000);
    });
  }

  whatsapp(numero: string): string {
    return `https://wa.me/${numero.replace(/[^0-9]/g, '')}`;
  }

  private charger(): void {
    this.service.suivi(this.jeton).subscribe({
      next: (suivi) => {
        this.suivi.set(suivi);
        const numero = suivi.commande?.numero ?? suivi.location?.numero;
        this.titre.setTitle(`Suivi ${numero} — ${suivi.boutique}`);
      },
      error: (reponse) => {
        clearInterval(this.minuterie);
        this.introuvable.set(reponse.error?.message ?? 'Ce lien de suivi n\'est pas reconnu.');
      }
    });
  }
}
