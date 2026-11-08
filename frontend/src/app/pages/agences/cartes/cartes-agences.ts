import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApercuTransaction, CompteAgence, ProduitCarte, ServiceFinancier, StockCarte, SuiviCartes, TypeOperation, VenteCarte } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

type Onglet = 'ventes' | 'stock' | 'suivi';

/**
 * Cartes prepayees : VENDRE UNE CARTE (stock par lot, prix encaisse en
 * caisse, marge sur le cout reel) et RECHARGER UNE CARTE (le compte de
 * recharge diminue, la commission va au sous-compte). Stock, lots et suivi
 * de la recharge.
 */
@Component({
  selector: 'app-cartes-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, SuiteListe],
  templateUrl: './cartes-agences.html',
  styleUrl: './cartes-agences.scss'
})
export class CartesAgences implements OnInit {
  readonly service = inject(AgencesService);

  onglet: Onglet = 'ventes';
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));
  agenceId: number | null = null;
  readonly produits = signal<ProduitCarte[]>([]);
  readonly stock = signal<StockCarte[]>([]);
  readonly comptes = signal<CompteAgence[]>([]);
  readonly ventes = signal<Page<VenteCarte> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly suivi = signal<SuiviCartes | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  du = new Date().toISOString().slice(0, 8) + '01';
  au = new Date().toISOString().slice(0, 10);

  /** Service des cartes et son operation de recharge, pris dans le catalogue. */
  private readonly serviceCartes = signal<ServiceFinancier | null>(null);
  readonly typeRecharge = computed<TypeOperation | null>(() =>
    this.serviceCartes()?.types.find((t) => t.actif && t.compteVirtuel === 'RECHARGE') ?? null);

  // Vente
  readonly vente = signal(false);
  fv = { produitId: null as number | null, quantite: 1, clientNom: '', clientTelephone: '' };
  readonly stockChoisi = computed(() => this.stock().find((s) => s.produitId === this.produitVente()));
  private readonly produitVente = signal<number | null>(null);

  // Recharge
  readonly recharge = signal(false);
  fr = { montant: null as number | null, carte: '', referenceOperateur: '', clientTelephone: '' };
  readonly apercu = signal<ApercuTransaction | null>(null);
  private minuterie?: ReturnType<typeof setTimeout>;

  // Stock
  readonly entree = signal(false);
  fe = { produitId: null as number | null, quantite: null as number | null, coutUnitaire: null as number | null, compteId: null as number | null,
         fournisseur: '', reference: '' };

  readonly detail = signal<VenteCarte | null>(null);
  readonly aAnnuler = signal<VenteCarte | null>(null);
  motif = '';

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        this.agenceId = m.agences.find((a) => a.actif)?.id ?? null;
        this.chargerTout();
      }
    });
    this.service.produitsCartes().subscribe({ next: (p) => this.produits.set(p) });
    this.service.catalogue().subscribe({
      next: (c) => this.serviceCartes.set(c.find((s) => s.actif && s.types.some((t) => t.compteVirtuel === 'RECHARGE')) ?? null)
    });
  }

  chargerTout(): void {
    if (!this.agenceId) {
      return;
    }
    this.service.stockCartes(this.agenceId).subscribe({ next: (s) => this.stock.set(s) });
    if (this.service.peut('CARTE_STOCK') && this.service.peut('SOLDES_CONSULTER')) {
      this.service.soldes(this.agenceId).subscribe({
        next: (e) => this.comptes.set((e.agences[0]?.comptes ?? []).filter((c) => c.actif && c.type !== 'CAISSE_CAISSIER'))
      });
    }
    this.chargerOnglet();
  }

  changerOnglet(o: Onglet): void {
    this.onglet = o;
    this.chargerOnglet();
  }

  chargerOnglet(): void {
    if (this.onglet === 'ventes') {
      this.ventes.set(null);
      this.chargerVentes();
    } else if (this.onglet === 'suivi') {
      this.service.suiviCartes(this.agenceId, this.du, this.au).subscribe({ next: (s) => this.suivi.set(s) });
    }
  }

  chargerVentes(): void {
    this.service.ventesCartes(this.agenceId, this.suite.numero()).subscribe({
      next: (p) => this.ventes.set(cumulerPage(this.ventes(), p))
    });
  }

  suiteVentes(): void {
    if (this.suite.demander(this.ventes())) {
      this.chargerVentes();
    }
  }

  // ---------------------------------------------------------------- vente
  ouvrirVente(): void {
    const premier = this.stock().find((s) => s.disponible > 0) ?? this.stock()[0];
    this.fv = { produitId: premier?.produitId ?? null, quantite: 1, clientNom: '', clientTelephone: '' };
    this.produitVente.set(this.fv.produitId);
    this.erreurModale.set(null);
    this.succes.set(null);
    this.vente.set(true);
  }

  changerProduitVente(): void {
    this.produitVente.set(this.fv.produitId);
  }

  vendre(): void {
    if (!this.agenceId || !this.fv.produitId || !this.fv.quantite) {
      return;
    }
    this.service.vendreCartes({ agenceId: this.agenceId, produitId: this.fv.produitId, quantite: this.fv.quantite,
      clientNom: this.fv.clientNom.trim() || undefined, clientTelephone: this.fv.clientTelephone.trim() || undefined }).subscribe({
      next: (v) => {
        this.vente.set(false);
        this.succes.set(`${v.reference} : ${v.quantite} ${v.produitNom} vendue(s), ${v.montant.toLocaleString('fr-FR')} F encaisses (marge ${v.marge.toLocaleString('fr-FR')} F).`);
        this.chargerTout();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Vente impossible.'))
    });
  }

  // -------------------------------------------------------------- recharge
  ouvrirRecharge(): void {
    this.fr = { montant: null, carte: '', referenceOperateur: '', clientTelephone: '' };
    this.apercu.set(null);
    this.erreurModale.set(null);
    this.succes.set(null);
    this.recharge.set(true);
  }

  private saisieRecharge() {
    const s = this.serviceCartes();
    const t = this.typeRecharge();
    if (!this.agenceId || !s || !t || !this.fr.montant || this.fr.montant <= 0) {
      return null;
    }
    return {
      agenceId: this.agenceId, serviceId: s.id, typeOperationId: t.id, montant: Math.round(this.fr.montant),
      carte: this.fr.carte.trim() || undefined, referenceOperateur: this.fr.referenceOperateur.trim() || undefined,
      clientTelephone: this.fr.clientTelephone.trim() || undefined, confirmerDoublon: true
    };
  }

  recalculer(): void {
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(() => {
      const s = this.saisieRecharge();
      if (!s) {
        this.apercu.set(null);
        return;
      }
      this.service.apercuTransaction(s).subscribe({
        next: (a) => {
          this.apercu.set(a);
          this.erreurModale.set(null);
        },
        error: (e) => {
          this.apercu.set(null);
          this.erreurModale.set(this.message(e, 'Calcul impossible.'));
        }
      });
    }, 300);
  }

  recharger(): void {
    const s = this.saisieRecharge();
    if (!s) {
      return;
    }
    this.service.enregistrerTransaction(s).subscribe({
      next: (t) => {
        this.recharge.set(false);
        this.succes.set(`${t.reference} : recharge de ${t.montant.toLocaleString('fr-FR')} F${t.carteMasquee ? ' sur la carte ' + t.carteMasquee : ''}, commission ${t.commission.toLocaleString('fr-FR')} F.`);
        this.chargerOnglet();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Recharge impossible.'))
    });
  }

  // ----------------------------------------------------------------- stock
  ouvrirEntree(): void {
    const p = this.produits()[0];
    this.fe = { produitId: p?.id ?? null, quantite: null, coutUnitaire: null,
      compteId: this.comptes().find((c) => c.type === 'CAISSE_GENERALE')?.id ?? null, fournisseur: '', reference: '' };
    this.erreurModale.set(null);
    this.entree.set(true);
  }

  entrer(): void {
    if (!this.agenceId || !this.fe.produitId || !this.fe.quantite || this.fe.coutUnitaire == null) {
      return;
    }
    this.service.entrerLotCartes({ agenceId: this.agenceId, produitId: this.fe.produitId, quantite: this.fe.quantite,
      coutUnitaire: Math.round(this.fe.coutUnitaire), compteId: this.fe.compteId,
      fournisseur: this.fe.fournisseur.trim() || undefined, reference: this.fe.reference.trim() || undefined }).subscribe({
      next: (s) => {
        this.stock.set(s);
        this.entree.set(false);
        this.succes.set(`${this.fe.quantite} carte(s) entree(s) en stock${this.fe.compteId ? '' : ' (stock initial, sans paiement)'}.`);
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Entree impossible.'))
    });
  }

  demanderAnnulation(v: VenteCarte): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aAnnuler.set(v);
  }

  annuler(): void {
    const v = this.aAnnuler();
    if (!v || !this.motif.trim()) {
      return;
    }
    this.service.annulerVenteCartes(v.id, this.motif.trim()).subscribe({
      next: (r) => {
        this.aAnnuler.set(null);
        this.detail.set(null);
        this.succes.set(`${r.reference} annulee : ${r.quantite} carte(s) remise(s) en stock.`);
        this.chargerTout();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Annulation impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
