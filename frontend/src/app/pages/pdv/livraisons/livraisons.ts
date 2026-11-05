import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { BonLivraison, SoldeReste, VenteALivrer } from '../../../core/models/materiaux';
import { Page } from '../../../core/models/models';
import { MODES_PAIEMENT_PDV, ModePaiementPdv } from '../../../core/models/pdv';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { SessionTravailService } from '../../../core/services/session-travail.service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Reste a livrer : les commandes payees ou a credit dont le client n'a pas encore
 * tout emporte. Chaque remise sort le stock et produit un bon de livraison a
 * faire signer ; une remise saisie par erreur s'annule par un gerant. Si le client
 * renonce au reste, un gerant le solde : la vente est corrigee et le surplus rembourse.
 */
@Component({
  selector: 'app-livraisons-pdv',
  imports: [SuiteListe, FormsModule, DecimalPipe, DatePipe, Modale],
  templateUrl: './livraisons.html'
})
export class LivraisonsPdv implements OnInit {
  private readonly service = inject(MateriauxService);
  private readonly route = inject(ActivatedRoute);
  private readonly sessions = inject(SessionTravailService);
  readonly estGestionnaire = inject(AuthService).aRole('ADMIN', 'GERANT');
  readonly modes = MODES_PAIEMENT_PDV;
  readonly session = this.sessions.courante;
  readonly ouvertes = this.sessions.ouvertes;

  readonly page = signal<Page<VenteALivrer> | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Dernier bon emis, propose a l'impression. */
  readonly dernierBon = signal<BonLivraison | null>(null);
  readonly dernierSolde = signal<SoldeReste | null>(null);
  /** Ce qui reste a remettre, ou les commandes qui ont deja des documents. */
  readonly vue = signal<'reste' | 'historique'>('reste');
  recherche = '';
  /** Arrive du compte d'un client : seules ses commandes sont listees. */
  readonly clientId = signal<number | null>(null);
  private readonly suite = new SuiteDePages();

  // Remise
  readonly remise = signal<VenteALivrer | null>(null);
  readonly quantites = signal<Record<number, number | null>>({});
  readonly erreurRemise = signal<string | null>(null);
  readonly enCours = signal(false);
  recuPar = '';
  note = '';
  readonly remiseValide = computed(() => {
    const v = this.remise();
    if (!v) {
      return false;
    }
    const saisies = v.lignes.map((l) => this.quantites()[l.ligneId] ?? 0);
    return saisies.some((q) => q > 0) && v.lignes.every((l, i) => saisies[i] >= 0 && saisies[i] <= l.reste);
  });

  // Solde du reste : le client renonce a ce qu'il n'a pas emporte.
  readonly solde = signal<VenteALivrer | null>(null);
  readonly quantitesSolde = signal<Record<number, number | null>>({});
  readonly motifSolde = signal('');
  readonly modeSolde = signal<ModePaiementPdv>('ESPECES');
  readonly erreurSolde = signal<string | null>(null);
  referenceSolde = '';
  /** Valeur du reste abandonne : elle diminue ce que le client doit, le surplus lui est rendu. */
  readonly valeurSolde = computed(() =>
    (this.solde()?.lignes ?? []).reduce((total, l) => total + (this.quantitesSolde()[l.ligneId] ?? 0) * l.valeurUnitaire, 0));
  readonly deduitSolde = computed(() => Math.min(this.valeurSolde(), this.solde()?.resteDu ?? 0));
  readonly rembourseSolde = computed(() => this.valeurSolde() - this.deduitSolde());
  readonly soldeValide = computed(() => {
    const v = this.solde();
    if (!v || !this.motifSolde().trim()) {
      return false;
    }
    const saisies = v.lignes.map((l) => this.quantitesSolde()[l.ligneId] ?? 0);
    return saisies.some((q) => q > 0) && v.lignes.every((l, i) => saisies[i] >= 0 && saisies[i] <= l.reste)
      && (this.rembourseSolde() <= 0 || !!this.session());
  });

  // Documents d'une commande
  readonly venteDesBons = signal<VenteALivrer | null>(null);
  readonly bons = signal<BonLivraison[]>([]);
  readonly soldes = signal<SoldeReste[]>([]);
  readonly bonAAnnuler = signal<number | null>(null);
  readonly soldeAAnnuler = signal<number | null>(null);
  readonly erreurDocuments = signal<string | null>(null);
  motifAnnulation = '';

  ngOnInit(): void {
    const client = this.route.snapshot.queryParamMap.get('client');
    this.clientId.set(client ? Number(client) : null);
    this.charger();
    if (this.estGestionnaire && this.sessions.ouvertes() === undefined) {
      this.sessions.rafraichir();
    }
  }

  charger(): void {
    this.chargement.set(true);
    const filtre = { q: this.recherche.trim() || undefined, clientId: this.clientId(), page: this.suite.numero() };
    (this.vue() === 'reste' ? this.service.resteALivrer(filtre) : this.service.historiqueLivraisons(filtre)).subscribe({
      next: (p) => {
        this.page.set(cumulerPage(this.page(), p));
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Chargement du reste a livrer impossible.');
      }
    });
  }

  changerVue(vue: 'reste' | 'historique'): void {
    this.vue.set(vue);
    this.filtrer();
  }

  filtrer(): void {
    this.charger();
  }

  tousLesClients(): void {
    this.clientId.set(null);
    this.filtrer();
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  /** Par defaut, le client prend tout ce qui reste ; on baisse si besoin. */
  ouvrirRemise(v: VenteALivrer): void {
    this.erreurRemise.set(null);
    this.recuPar = v.clientNom ?? '';
    this.note = '';
    this.quantites.set(Object.fromEntries(v.lignes.map((l) => [l.ligneId, l.reste])));
    this.remise.set(v);
  }

  changerQuantite(ligneId: number, valeur: number | null): void {
    this.quantites.update((q) => ({ ...q, [ligneId]: valeur }));
  }

  remettre(): void {
    const v = this.remise();
    if (!v || this.enCours() || !this.remiseValide()) {
      return;
    }
    const lignes = v.lignes
      .map((l) => ({ ligneId: l.ligneId, quantite: this.quantites()[l.ligneId] ?? 0 }))
      .filter((l) => l.quantite > 0);
    this.enCours.set(true);
    this.service.remettre(v.venteId, { lignes, recuPar: this.recuPar.trim() || null, note: this.note.trim() || null }).subscribe({
      next: (bon) => {
        this.enCours.set(false);
        this.remise.set(null);
        this.dernierSolde.set(null);
        this.dernierBon.set(bon);
        this.succes.set(`Remise enregistree : bon ${bon.numero}.`);
        this.charger();
      },
      error: (e) => {
        this.enCours.set(false);
        this.erreurRemise.set(e?.error?.message ?? 'Remise impossible.');
      }
    });
  }

  /** Par defaut, le client renonce a tout ce qui reste ; on baisse si besoin. */
  ouvrirSolde(v: VenteALivrer): void {
    this.erreurSolde.set(null);
    this.motifSolde.set('');
    this.referenceSolde = '';
    this.modeSolde.set(v.modePaiement);
    this.quantitesSolde.set(Object.fromEntries(v.lignes.map((l) => [l.ligneId, l.reste])));
    this.solde.set(v);
  }

  changerQuantiteSolde(ligneId: number, valeur: number | null): void {
    this.quantitesSolde.update((q) => ({ ...q, [ligneId]: valeur }));
  }

  choisirSession(id: number | null): void {
    this.sessions.choisir(id);
  }

  solder(): void {
    const v = this.solde();
    if (!v || this.enCours() || !this.soldeValide()) {
      return;
    }
    const lignes = v.lignes
      .map((l) => ({ ligneId: l.ligneId, quantite: this.quantitesSolde()[l.ligneId] ?? 0 }))
      .filter((l) => l.quantite > 0);
    this.enCours.set(true);
    this.service.solder(v.venteId, {
      lignes,
      motif: this.motifSolde().trim(),
      modeRemboursement: this.modeSolde(),
      referencePaiement: this.referenceSolde.trim() || null,
      sessionId: this.session()?.id ?? null
    }).subscribe({
      next: (s) => {
        this.enCours.set(false);
        this.solde.set(null);
        this.dernierBon.set(null);
        this.dernierSolde.set(s);
        this.succes.set(s.montantRembourse > 0
          ? `Reste solde (${s.numero}) : rendez ${Math.round(s.montantRembourse)} F au client.`
          : `Reste solde (${s.numero}) : ${Math.round(s.montantDeduit)} F retires de ce que le client doit.`);
        this.sessions.rafraichir();
        this.charger();
      },
      error: (e) => {
        this.enCours.set(false);
        this.erreurSolde.set(e?.error?.message ?? 'Solde impossible.');
      }
    });
  }

  imprimerSolde(s: SoldeReste): void {
    this.service.soldePdf(s.id).subscribe({
      next: (pdf) => this.service.ouvrirPdf(pdf),
      error: () => this.erreur.set('Avoir indisponible.')
    });
  }

  annulerSolde(s: SoldeReste): void {
    this.service.annulerSolde(s.id, this.motifAnnulation.trim()).subscribe({
      next: () => {
        this.soldeAAnnuler.set(null);
        this.motifAnnulation = '';
        this.dernierSolde.set(null);
        this.succes.set(`Solde ${s.numero} annule : la vente est retablie et le reste de nouveau a livrer.`);
        this.chargerBons(s.venteId);
        this.sessions.rafraichir();
        this.charger();
      },
      error: (e) => {
        this.soldeAAnnuler.set(null);
        this.erreurDocuments.set(e?.error?.message ?? 'Annulation impossible.');
      }
    });
  }

  ouvrirBons(v: VenteALivrer): void {
    this.bonAAnnuler.set(null);
    this.soldeAAnnuler.set(null);
    this.erreurDocuments.set(null);
    this.venteDesBons.set(v);
    this.chargerBons(v.venteId);
  }

  private chargerBons(venteId: number): void {
    this.service.bonsDeLaVente(venteId).subscribe({ next: (liste) => this.bons.set(liste) });
    this.service.soldesDeLaVente(venteId).subscribe({ next: (liste) => this.soldes.set(liste) });
  }

  imprimer(bon: BonLivraison): void {
    this.service.bonPdf(bon.id).subscribe({
      next: (pdf) => this.service.ouvrirPdf(pdf),
      error: () => this.erreur.set('Bon de livraison indisponible.')
    });
  }

  annulerBon(bon: BonLivraison): void {
    this.service.annulerBon(bon.id, this.motifAnnulation.trim()).subscribe({
      next: () => {
        this.bonAAnnuler.set(null);
        this.motifAnnulation = '';
        this.dernierBon.set(null);
        this.succes.set(`Bon ${bon.numero} annule : la marchandise est de nouveau a livrer.`);
        this.chargerBons(bon.venteId);
        this.charger();
      },
      error: (e) => {
        this.bonAAnnuler.set(null);
        this.erreurDocuments.set(e?.error?.message ?? 'Annulation impossible.');
      }
    });
  }
}
