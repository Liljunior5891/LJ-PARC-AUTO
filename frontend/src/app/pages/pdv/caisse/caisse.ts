import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommandeEnLigne } from '../../../core/models/marketplace';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import {
  ArticlePdv,
  CategoriePdv,
  MODES_PAIEMENT_PDV,
  ModePaiementPdv,
  Ticket
} from '../../../core/models/pdv';
import { ClientPdv, Conditionnement, Devis, DevisRequete, ReglagesMateriaux } from '../../../core/models/materiaux';
import { PdvService } from '../../../core/services/pdv.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { LieuxService } from '../../../core/services/lieux.service';
import { BonRetrait, DisponibiliteLieu, LieuCourt } from '../../../core/models/lieux';
import { SessionTravailService } from '../../../core/services/session-travail.service';
import { FinDeSaisie, LecteurDouchette, normaliserCodeBarres } from '../../../shared/code-barres';
import { BandeauSession } from '../../../shared/bandeau-session';
import { BoutonEspeces } from '../../../shared/bouton-especes';
import { SocieteService } from '../../../core/services/societe.service';
import { Modale } from '../../../shared/modale';
import { imprimerTicket } from '../../../shared/ticket-pdv';
import { CompteClientPdv } from '../../../shared/compte-client-pdv';
import { FormulaireClientPdv } from '../../../shared/formulaire-client-pdv';

/** Ligne du panier en cours de constitution. */
interface LignePanier {
  article: ArticlePdv;
  /** Quincaillerie : conditionnement vendu (tonne, botte, carton) ; absent pour l'unite de base. */
  conditionnement?: Conditionnement | null;
  quantite: number;
}

/** Billets proposes en raccourci pour le montant recu en especes. */
const BILLETS = [500, 1000, 2000, 5000, 10000];

/** Cle d'une ligne : le produit et son conditionnement. */
const cle = (l: { article: ArticlePdv; conditionnement?: Conditionnement | null }) =>
  `${l.article.id}:${l.conditionnement?.id ?? 0}`;

/** Prix unitaire d'une ligne : celui du conditionnement, sinon celui du produit. */
const prix = (l: LignePanier) => l.conditionnement?.prixVente ?? l.article.prixVente;

/** Unites de base sorties du stock par une ligne. */
const unitesDeBase = (l: LignePanier) => l.quantite * (l.conditionnement?.coefficient ?? 1);

/**
 * Caisse de la boutique, au meme format que celle du restaurant mais sans
 * table ni cuisine : les produits en photo a gauche, la commande du client a
 * droite, l'encaissement en une fenetre.
 * <p>
 * La recherche garde le focus pour la douchette (Entree valide le code lu).
 * Le panier reste dans le navigateur jusqu'a l'encaissement ; les prix
 * affiches viennent du catalogue, le serveur recalcule tout et fait foi.
 * <p>
 * En quincaillerie (type d'activite « materiaux »), la meme caisse choisit
 * le compte client, vend a credit ou avec acompte, propose les
 * conditionnements d'un produit (sac ou tonne), enregistre le panier en devis
 * et imprime la facture A4 ; chaque fonction suit les reglages de l'administrateur.
 */
@Component({
  selector: 'app-caisse',
  imports: [FormsModule, DecimalPipe, DatePipe, Modale, BandeauSession, BoutonEspeces, CompteClientPdv, FormulaireClientPdv],
  templateUrl: './caisse.html',
  styleUrls: ['../commande/commande.scss', './caisse.scss'],
  host: { '(document:keydown)': 'captureDouchette($event)' }
})
export class Caisse implements OnInit, OnDestroy {
  private readonly service = inject(PdvService);
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);
  private readonly materiauxService = inject(MateriauxService);
  private readonly router = inject(Router);

  readonly modes = MODES_PAIEMENT_PDV;
  readonly estGestionnaire = this.auth.aRole('ADMIN', 'GERANT');
  readonly vendeur = this.auth.utilisateur()?.nomComplet ?? '';

  /** Session ouverte par un gerant sur laquelle le poste encaisse : indefinie tant qu'elle n'est pas lue, nulle si aucune. */
  private readonly sessions = inject(SessionTravailService);
  readonly session = this.sessions.courante;
  readonly articles = signal<ArticlePdv[]>([]);
  readonly categories = signal<CategoriePdv[]>([]);
  readonly categorieChoisie = signal<number | null>(null);
  readonly recherche = signal('');
  readonly panier = signal<LignePanier[]>([]);
  readonly remise = signal(0);
  readonly erreur = signal<string | null>(null);
  readonly information = signal<string | null>(null);
  /** Sur telephone, une seule vue a la fois : les produits ou la commande. */
  readonly vueMobile = signal<'carte' | 'commande'>('carte');
  clientNom = '';

  // Encaissement
  readonly paiement = signal(false);
  readonly mode = signal<ModePaiementPdv>('ESPECES');
  readonly montantRecu = signal<number | null>(null);
  reference = '';
  readonly enCours = signal(false);
  readonly dernierTicket = signal<Ticket | null>(null);

  private readonly saisie = viewChild<ElementRef<HTMLInputElement>>('saisie');
  private readonly champRecu = viewChild<ElementRef<HTMLInputElement>>('champRecu');
  private readonly route = inject(ActivatedRoute);

  // Commandes de la boutique en ligne : elles arrivent a la caisse, qui les accepte puis les encaisse.
  readonly marketplace = this.auth.aModule('MARKETPLACE');
  readonly commandesEnLigne = signal<CommandeEnLigne[]>([]);
  readonly fenetreEnLigne = signal(false);
  readonly nouvellesEnLigne = computed(() => this.commandesEnLigne().filter((c) => c.statut === 'NOUVELLE').length);
  /** Commande en ligne chargee dans le panier : le ticket la reglera et la clora. */
  readonly commandeEnLigne = signal<CommandeEnLigne | null>(null);
  motifRefus = '';
  readonly refusEnCours = signal<number | null>(null);
  private minuterie?: ReturnType<typeof setInterval>;

  // ------------------------------------------------ quincaillerie et materiaux
  readonly materiaux = this.auth.estMateriaux();
  readonly reglages = signal<ReglagesMateriaux | null>(null);
  /** Conditionnements vendables, par produit. */
  readonly conditionnements = signal<Map<number, Conditionnement[]>>(new Map());
  /** Produit dont on choisit l'unite de vente (sac, tonne...). */
  readonly choixUnite = signal<ArticlePdv | null>(null);
  /** Compte client de la vente. */
  readonly client = signal<ClientPdv | null>(null);
  readonly rechercheClient = signal('');
  readonly clientsTrouves = signal<ClientPdv[]>([]);
  /** Fiche client ouverte en creation depuis le comptoir. */
  readonly nouveauClient = signal(false);
  /** Compte client ouvert (versements). */
  readonly compteOuvert = signal<{ id: number; versement: boolean } | null>(null);
  private minuterieClient?: ReturnType<typeof setTimeout>;
  // Vente a credit
  readonly credit = signal(false);
  readonly acompte = signal<number | null>(0);
  // Livraison en plusieurs fois : ce que le client emporte maintenant, par ligne du panier.
  readonly resteALivrer = signal(false);
  readonly emportes = signal<Record<string, number | null>>({});
  // Magasins et entrepots : chaque ligne se retire dans un lieu, le client part avec un bon.
  private readonly lieuxService = inject(LieuxService);
  readonly multiLieux = this.lieuxService.actif;
  readonly lieux = computed(() => this.lieuxService.etat()?.lieux ?? []);
  readonly magasin = signal<LieuCourt | null>(null);
  readonly disponibilites = signal<DisponibiliteLieu[]>([]);
  readonly lieuxLignes = signal<Record<string, number>>({});
  readonly bonsRetrait = signal<BonRetrait[]>([]);
  /** Le choix des lieux s'applique, sauf a une commande en ligne, remise en une fois au comptoir. */
  readonly choixLieux = computed(() => this.multiLieux() && !this.commandeEnLigne());
  // Devis
  readonly devisCharge = signal<Devis | null>(null);
  /** Le panier a change depuis le chargement du devis : il faut l'enregistrer avant de l'encaisser. */
  readonly devisModifie = signal(false);
  readonly fenetreDevis = signal(false);
  readonly devisOuverts = signal<Devis[]>([]);
  readonly enregistrementDevis = signal(false);
  noteDevis = '';

  readonly articlesAffiches = computed(() => {
    const motif = normaliserCodeBarres(this.recherche()).toLowerCase();
    const categorie = this.categorieChoisie();
    return this.articles().filter(
      (a) =>
        (categorie === null || a.categorieId === categorie) &&
        (!motif || a.nom.toLowerCase().includes(motif) || a.code.toLowerCase().includes(motif) ||
          (a.codeBarres ?? '').includes(motif))
    );
  });

  /** Nombre de produits par categorie, affiche sur les cartes de categorie. */
  readonly parCategorie = computed(() => {
    const nombres = new Map<number, number>();
    for (const a of this.articles()) {
      if (a.categorieId) {
        nombres.set(a.categorieId, (nombres.get(a.categorieId) ?? 0) + 1);
      }
    }
    return nombres;
  });

  /** Ligne du panier a l'unite de base, par produit : la carte du produit affiche alors son compteur. */
  readonly dansLePanier = computed(
    () => new Map(this.panier().filter((l) => !l.conditionnement).map((l) => [l.article.id, l]))
  );

  readonly totalBrut = computed(() => this.panier().reduce((somme, l) => somme + prix(l) * l.quantite, 0));
  readonly total = computed(() => Math.max(0, this.totalBrut() - (this.remise() || 0)));
  readonly nombreArticles = computed(() => this.panier().reduce((somme, l) => somme + l.quantite, 0));
  /** Paye maintenant : le total, ou l'acompte d'une vente a credit. */
  readonly aPayer = computed(() => (this.credit() ? Math.min(this.acompte() ?? 0, this.total()) : this.total()));
  readonly monnaie = computed(() => Math.max(0, (this.montantRecu() ?? 0) - this.aPayer()));
  /** Part de la somme due deja recue, pour la jauge de la fenetre d'encaissement. */
  readonly progression = computed(() =>
    this.aPayer() <= 0 ? 100 : Math.min(100, Math.round(((this.montantRecu() ?? 0) / this.aPayer()) * 100)));
  readonly billets = computed(() => {
    const total = this.aPayer();
    const suggestions = new Set<number>([total, Math.ceil(total / 1000) * 1000]);
    BILLETS.filter((b) => b > total).forEach((b) => suggestions.add(b));
    return [...suggestions].filter((v) => v >= total).sort((a, b) => a - b).slice(0, 5);
  });
  readonly paiementPossible = computed(() => {
    if (this.panier().length === 0) {
      return false;
    }
    if (this.credit() && (!this.client() || (this.acompte() ?? 0) < 0 || (this.acompte() ?? 0) >= this.total())) {
      return false;
    }
    if (this.choixLieux()) {
      if (!this.lignesLieuxValides()) {
        return false;
      }
    } else if (this.resteALivrer() && (!this.client() || !this.emportesValides())) {
      return false;
    }
    return this.mode() !== 'ESPECES' || (this.montantRecu() ?? 0) >= this.aPayer();
  });
  /** Livraison en plusieurs fois proposee a l'encaissement, si l'administrateur l'a activee. */
  readonly livraisonPossible = computed(() =>
    this.materiaux && !!this.reglages()?.livraisonDifferee && !this.commandeEnLigne()
      && this.panier().some((l) => l.article.suiviStock));
  /** Reglage de l'administrateur : on peut vendre au-dela du disponible, le surplus etant livre a l'arrivage. */
  readonly surArrivagePermis = computed(() =>
    this.livraisonPossible() && !!this.reglages()?.venteSurArrivage);
  /** Le panier depasse le disponible d'un produit : le client ne peut pas tout emporter. */
  readonly surVente = computed(() =>
    this.panier().some((l) => l.article.suiviStock && this.unitesPrises(l.article.id) > l.article.stockDisponible));
  /** Seuls les produits en stock se remettent ; un service compte comme livre. */
  readonly lignesARemettre = computed(() => this.panier().filter((l) => l.article.suiviStock));
  /** Chaque quantite est possible et il reste bien quelque chose a livrer. */
  readonly emportesValides = computed(() => {
    const lignes = this.lignesARemettre();
    const valeurs = lignes.map((l) => this.emporte(l));
    return lignes.every((l, i) => valeurs[i] >= 0 && valeurs[i] <= this.maximumEmporte(l))
      && lignes.some((l, i) => valeurs[i] < l.quantite);
  });
  /** Chaque lieu a la marchandise (sauf vente sur arrivage) et on n'emporte que depuis le magasin de la caisse. */
  readonly lignesLieuxValides = computed(() => {
    const lignes = this.lignesARemettre();
    if (lignes.some((l) => !this.lieuLigne(l))) {
      return false;
    }
    const parLieu = new Map<string, number>();
    for (const l of lignes) {
      const c = `${l.article.id}:${this.lieuLigne(l)}`;
      parLieu.set(c, (parLieu.get(c) ?? 0) + unitesDeBase(l));
    }
    const dispoOk = this.reglages()?.venteSurArrivage || lignes.every((l) =>
      (parLieu.get(`${l.article.id}:${this.lieuLigne(l)}`) ?? 0) <= this.dispoAuLieu(l.article.id, this.lieuLigne(l)));
    return !!dispoOk && lignes.every((l) => this.emporte(l) >= 0 && this.emporte(l) <= this.maximumEmporteAuLieu(l));
  });
  /** Credit propose a l'encaissement : active par l'administrateur, et permis a ce profil. */
  readonly creditPossible = computed(() => {
    const r = this.reglages();
    return this.materiaux && !!r?.creditActif && (r.creditCaissier || this.estGestionnaire) && !this.commandeEnLigne();
  });

  ngOnInit(): void {
    this.service.catalogueCaisse().subscribe({ next: (a) => this.articles.set(a) });
    this.service.categories().subscribe({ next: (c) => this.categories.set(c.filter((x) => x.actif)) });
    this.refocaliser();
    if (this.marketplace) {
      this.chargerEnLigne();
      this.minuterie = setInterval(() => this.chargerEnLigne(), 30_000);
      // Ouverte depuis la notification d'une nouvelle commande en ligne.
      if (this.route.snapshot.queryParamMap.has('enLigne')) {
        this.fenetreEnLigne.set(true);
      }
    }
    if (this.materiaux) {
      this.initialiserMateriaux();
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
    clearTimeout(this.minuterieClient);
    this.finDeSaisie.arreter();
    this.douchette.arreter();
  }

  // ---------------------------------------------------- commandes en ligne

  chargerEnLigne(): void {
    this.service.commandesEnLigne().subscribe({
      next: (commandes) => this.commandesEnLigne.set(commandes),
      error: () => this.commandesEnLigne.set([])
    });
  }

  avancerEnLigne(commande: CommandeEnLigne, statut: 'CONFIRMEE' | 'PRETE' | 'EN_LIVRAISON' | 'ANNULEE'): void {
    this.erreur.set(null);
    this.service.changerStatutEnLigne(commande.id, statut, statut === 'ANNULEE' ? this.motifRefus.trim() || undefined : undefined)
      .subscribe({
        next: () => {
          this.refusEnCours.set(null);
          this.motifRefus = '';
          this.chargerEnLigne();
        },
        error: (e) => this.erreur.set(this.message(e, 'Operation impossible.'))
      });
  }

  /** Remise au client : la commande en ligne passe dans le panier, puis s'encaisse comme une vente. */
  encaisserEnLigne(commande: CommandeEnLigne): void {
    const lignes: LignePanier[] = [];
    for (const l of commande.lignes) {
      const article = this.articles().find((a) => a.id === l.articleId);
      if (!article) {
        this.erreur.set(`${l.libelle} n'est plus au catalogue de la caisse : encaissez cette commande a la main.`);
        return;
      }
      lignes.push({ article, quantite: l.quantite });
    }
    this.panier.set(lignes);
    this.remise.set(0);
    this.clientNom = commande.clientNom;
    this.commandeEnLigne.set(commande);
    this.fenetreEnLigne.set(false);
    this.vueMobile.set('commande');
    // Deja payee en ligne : le reglement est du mobile money, reference de la commande.
    this.ouvrirPaiement(commande.statutPaiement === 'PAYEE' ? 'MOBILE_MONEY' : 'ESPECES');
    if (commande.statutPaiement === 'PAYEE') {
      this.reference = commande.numero;
    }
  }

  // -------------------------------------------------------------- panier

  /** Douchette sans Entree : le code complet dans la recherche passe au panier. */
  private readonly finDeSaisie = new FinDeSaisie(() => this.ajouterSiCodeComplet());
  /** Lectures faites alors que le focus n'est pas dans la recherche. */
  private readonly douchette = new LecteurDouchette((code, parEntree) => {
    this.recherche.set(code);
    if (parEntree) {
      this.valider();
    } else if (!this.ajouterSiCodeComplet()) {
      this.refocaliser();
    }
  });

  /**
   * Saisie dans la recherche. Beaucoup de douchettes n'envoient pas Entree
   * apres le code : des que la frappe s'arrete et que le champ contient
   * exactement le code-barres d'un produit, celui-ci passe au panier.
   */
  saisir(valeur: string): void {
    this.recherche.set(valeur);
    this.finDeSaisie.relancer();
  }

  /** Ajoute le produit (ou le conditionnement) dont le code-barres est exactement dans la recherche. */
  private ajouterSiCodeComplet(): boolean {
    const valeur = normaliserCodeBarres(this.recherche());
    if (valeur.length < 4) {
      return false;
    }
    if (this.ajouterConditionnementParCode(valeur)) {
      return true;
    }
    const article = this.articles().find((a) => a.codeBarres === valeur);
    if (!article) {
      return false;
    }
    this.ajouter(article);
    this.recherche.set('');
    return true;
  }

  /** Entree dans la recherche : un code lu a la douchette, ou le seul produit filtre. */
  valider(): void {
    this.finDeSaisie.arreter();
    const valeur = normaliserCodeBarres(this.recherche());
    if (!valeur) {
      return;
    }
    if (this.ajouterConditionnementParCode(valeur)) {
      return;
    }
    const exact = this.articles().find(
      (a) => a.codeBarres === valeur || a.code.toLowerCase() === valeur.toLowerCase()
    );
    if (exact) {
      this.ajouter(exact);
      this.recherche.set('');
      return;
    }
    const filtres = this.articlesAffiches();
    if (filtres.length === 1) {
      this.ajouter(filtres[0]);
      this.recherche.set('');
      return;
    }
    // Code absent du catalogue charge (produit cree depuis) : le serveur tranche.
    this.service.articleParCode(valeur).subscribe({
      next: (article) => {
        this.articles.update((liste) => [...liste, article]);
        this.ajouter(article);
        this.recherche.set('');
      },
      error: () => this.signaler(`Aucun produit pour « ${valeur} »`)
    });
  }

  /**
   * Produit touche ou lu. En quincaillerie, un produit vendu sous plusieurs
   * unites (sac, tonne) fait d'abord choisir l'unite.
   */
  ajouter(article: ArticlePdv): void {
    if ((this.conditionnements().get(article.id)?.length ?? 0) > 0) {
      this.choixUnite.set(article);
      return;
    }
    this.ajouterLigne(article, null);
  }

  /** Ajoute une unite (ou un conditionnement) au panier, si le stock le permet. */
  ajouterLigne(article: ArticlePdv, conditionnement: Conditionnement | null): void {
    this.choixUnite.set(null);
    const besoin = (conditionnement?.coefficient ?? 1) + this.unitesPrises(article.id);
    if (article.suiviStock && besoin > article.stockDisponible && !this.surArrivagePermis()) {
      this.signaler(`Stock insuffisant pour ${article.nom}${conditionnement ? ' (' + conditionnement.nom + ')' : ''}`);
      return;
    }
    const nouvelle: LignePanier = { article, conditionnement, quantite: 1 };
    this.panier.update((lignes) => {
      const existante = lignes.find((l) => cle(l) === cle(nouvelle));
      return existante
        ? lignes.map((l) => (l === existante ? { ...l, quantite: l.quantite + 1 } : l))
        : [...lignes, nouvelle];
    });
    this.devisModifie.set(true);
    this.information.set(null);
    this.refocaliser();
  }

  changerQuantite(ligne: LignePanier, quantite: number): void {
    if (!quantite || quantite <= 0) {
      this.retirer(ligne);
      return;
    }
    if (ligne.article.suiviStock && !this.surArrivagePermis()) {
      const coefficient = ligne.conditionnement?.coefficient ?? 1;
      const autres = this.unitesPrises(ligne.article.id) - unitesDeBase(ligne);
      const maximum = Math.floor(((ligne.article.stockDisponible - autres) / coefficient) * 1000) / 1000;
      if (quantite > maximum) {
        this.signaler(`Seulement ${ligne.article.stockDisponible} ${ligne.article.unite ?? ''} de ${ligne.article.nom} disponible(s)`);
        quantite = Math.max(0, maximum);
        if (quantite <= 0) {
          this.retirer(ligne);
          return;
        }
      }
    }
    this.panier.update((lignes) => lignes.map((l) => (cle(l) === cle(ligne) ? { ...l, quantite } : l)));
    this.devisModifie.set(true);
  }

  retirer(ligne: LignePanier): void {
    this.panier.update((lignes) => lignes.filter((l) => cle(l) !== cle(ligne)));
    this.devisModifie.set(true);
    this.refocaliser();
  }

  viderPanier(): void {
    this.commandeEnLigne.set(null);
    this.panier.set([]);
    this.remise.set(0);
    this.clientNom = '';
    this.client.set(null);
    this.devisCharge.set(null);
    this.devisModifie.set(false);
    this.noteDevis = '';
    this.refocaliser();
  }

  prixLigne(l: LignePanier): number {
    return prix(l);
  }

  libelleUnite(l: LignePanier): string {
    return l.conditionnement?.nom ?? l.article.unite ?? 'unite';
  }

  // -------------------------------------------------------- encaissement

  ouvrirPaiement(mode: ModePaiementPdv = 'ESPECES'): void {
    if (this.panier().length === 0) {
      return;
    }
    if (!this.session()) {
      // Pas de vente sans session : seul un gerant l'ouvre.
      this.signaler(this.sessions.choixRequis()
        ? 'Choisissez d\'abord la caisse sur laquelle vous encaissez.'
        : 'Caisse fermee : un gerant doit ouvrir la session avant toute vente.');
      return;
    }
    if (this.materiaux && this.reglages()?.clientObligatoire && !this.client()) {
      this.signaler('Choisissez d\'abord le client : chaque vente doit etre rattachee a une fiche client.');
      return;
    }
    this.mode.set(mode);
    this.credit.set(false);
    this.acompte.set(0);
    this.resteALivrer.set(false);
    this.emportes.set({});
    if (this.choixLieux()) {
      this.preparerLieux();
    } else if (this.surVente()) {
      if (!this.client()) {
        this.signaler('Le panier depasse le stock disponible : choisissez la fiche du client, le surplus lui sera livre a l\'arrivage.');
        return;
      }
      this.basculerLivraison(true);
    }
    this.montantRecu.set(this.total());
    this.reference = '';
    this.erreur.set(null);
    this.paiement.set(true);
    this.selectionnerRecu();
  }

  cleLigne(l: LignePanier): string {
    return cle(l);
  }

  choisirMode(mode: ModePaiementPdv): void {
    this.mode.set(mode);
    this.selectionnerRecu();
  }

  /** Le montant recu est pret a etre retape ; sur telephone, le clavier ne surgit pas tout seul. */
  private selectionnerRecu(): void {
    if (window.matchMedia('(min-width: 821px)').matches) {
      setTimeout(() => this.champRecu()?.nativeElement.select());
    }
  }

  /** Lit le magasin de la caisse et les stocks, puis propose un lieu pour chaque ligne. */
  private preparerLieux(): void {
    const session = this.session();
    this.bonsRetrait.set([]);
    this.resteALivrer.set(true);
    const articles = [...new Set(this.lignesARemettre().map((l) => l.article.id))];
    if (!session || articles.length === 0) {
      return;
    }
    this.lieuxService.lieuDeCaisse(session.caisseId).subscribe({
      next: (magasin) => {
        this.magasin.set(magasin);
        this.lieuxService.disponibilites(articles).subscribe({
          next: (d) => {
            this.disponibilites.set(d);
            const lieux: Record<string, number> = {};
            const emportes: Record<string, number | null> = {};
            for (const l of this.lignesARemettre()) {
              const besoin = unitesDeBase(l);
              // Au comptoir s'il y a de quoi servir, sinon le premier lieu qui a tout.
              const ailleurs = d.find((x) => x.articleId === l.article.id && x.disponible >= besoin);
              const ici = this.dispoAuLieu(l.article.id, magasin.id) >= besoin;
              lieux[cle(l)] = ici ? magasin.id : ailleurs?.lieuId ?? magasin.id;
              emportes[cle(l)] = ici ? l.quantite : 0;
            }
            this.lieuxLignes.set(lieux);
            this.emportes.set(emportes);
          },
          error: () => this.erreur.set('Lecture du stock des lieux impossible.')
        });
      },
      error: () => this.erreur.set('Lecture du magasin de la caisse impossible.')
    });
  }

  lieuLigne(l: LignePanier): number {
    return this.lieuxLignes()[cle(l)];
  }

  dispoAuLieu(articleId: number, lieuId: number): number {
    return this.disponibilites().find((d) => d.articleId === articleId && d.lieuId === lieuId)?.disponible ?? 0;
  }

  changerLieuLigne(l: LignePanier, lieuId: number): void {
    this.lieuxLignes.update((v) => ({ ...v, [cle(l)]: lieuId }));
    const ici = lieuId === this.magasin()?.id;
    const disponible = Math.floor((this.dispoAuLieu(l.article.id, lieuId) / (l.conditionnement?.coefficient ?? 1)) * 1000) / 1000;
    this.changerEmporte(l, ici ? Math.max(0, Math.min(l.quantite, disponible)) : 0);
  }

  /** On n'emporte que ce qui est au magasin de la caisse ; ailleurs, tout part avec le bon de retrait. */
  maximumEmporteAuLieu(l: LignePanier): number {
    if (this.lieuLigne(l) !== this.magasin()?.id) {
      return 0;
    }
    const disponible = Math.floor((this.dispoAuLieu(l.article.id, this.lieuLigne(l)) / (l.conditionnement?.coefficient ?? 1)) * 1000) / 1000;
    return Math.max(0, Math.min(l.quantite, disponible));
  }

  imprimerRetrait(b: BonRetrait): void {
    this.lieuxService.retraitPdf(b.id).subscribe({
      next: (pdf) => this.materiauxService.ouvrirPdf(pdf),
      error: () => this.signaler('Bon de retrait indisponible.')
    });
  }

  /** Bascule : le client emporte tout, ou seulement une partie (rien par defaut, a ajuster ligne par ligne). */
  basculerLivraison(reste: boolean): void {
    this.resteALivrer.set(reste);
    this.emportes.set(reste ? Object.fromEntries(this.lignesARemettre().map((l) => [cle(l), 0])) : {});
  }

  /** Le client n'emporte que ce qui est disponible, dans l'unite de la ligne. */
  maximumEmporte(l: LignePanier): number {
    const disponible = Math.floor((l.article.stockDisponible / (l.conditionnement?.coefficient ?? 1)) * 1000) / 1000;
    return Math.max(0, Math.min(l.quantite, disponible));
  }

  emporte(l: LignePanier): number {
    return this.emportes()[cle(l)] ?? 0;
  }

  changerEmporte(l: LignePanier, valeur: number | null): void {
    this.emportes.update((e) => ({ ...e, [cle(l)]: valeur }));
  }

  /** Reste a livrer d'un ticket, pour la fenetre de fin de vente. */
  resteDuTicket(t: Ticket): { libelle: string; reste: number }[] {
    return t.lignes
      .map((l) => ({ libelle: l.libelle, reste: l.quantite - (l.quantiteLivree ?? l.quantite) }))
      .filter((l) => l.reste > 0);
  }

  /** Une partie a deja ete emportee : un premier bon existe. */
  aUnBon(t: Ticket): boolean {
    return this.resteDuTicket(t).length > 0 && t.lignes.some((l) => (l.quantiteLivree ?? l.quantite) > 0);
  }

  bonLivraison(t: Ticket): void {
    this.materiauxService.bonsDeLaVente(t.id).subscribe({
      next: (bons) => {
        if (bons.length) {
          this.materiauxService.bonPdf(bons[0].id).subscribe({ next: (pdf) => this.materiauxService.ouvrirPdf(pdf) });
        }
      },
      error: () => this.signaler('Bon de livraison indisponible.')
    });
  }

  /** Bascule comptant / credit : le montant recu suit ce qui est a payer maintenant. */
  basculerCredit(credit: boolean): void {
    this.credit.set(credit);
    if (credit) {
      const minimum = Math.ceil((this.total() * (this.reglages()?.acompteMinimum ?? 0)) / 100);
      this.acompte.set(minimum);
    }
    this.montantRecu.set(this.aPayer());
  }

  changerAcompte(valeur: number | null): void {
    this.acompte.set(valeur);
    this.montantRecu.set(this.aPayer());
  }

  encaisser(): void {
    if (this.enCours() || !this.paiementPossible()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    const devis = this.devisCharge();
    if (devis) {
      this.encaisserDevis(devis);
      return;
    }
    this.service
      .encaisser({
        lignes: this.panier().map((l) => ({
          articleId: l.article.id,
          quantite: l.quantite,
          conditionnementId: l.conditionnement?.id ?? null,
          quantiteLivree: (this.resteALivrer() || this.choixLieux()) && l.article.suiviStock ? this.emporte(l) : null,
          lieuId: this.choixLieux() && l.article.suiviStock ? this.lieuLigne(l) : null
        })),
        remise: this.remise() || 0,
        modePaiement: this.mode(),
        montantRecu: this.mode() === 'ESPECES' ? this.montantRecu() : null,
        referencePaiement: this.reference || undefined,
        clientNom: this.clientNom.trim() || undefined,
        sessionId: this.session()?.id,
        commandeEnLigneId: this.commandeEnLigne()?.id ?? null,
        clientId: this.client()?.id ?? null,
        credit: this.credit(),
        acompte: this.credit() ? this.acompte() ?? 0 : null
      })
      .subscribe({
        next: (ticket) => this.venteTerminee(ticket),
        error: (e) => {
          this.enCours.set(false);
          this.erreur.set(this.message(e, 'Encaissement impossible.'));
        }
      });
  }

  /** Devis charge : enregistre d'abord les modifications du panier, puis l'encaisse a ses prix. */
  private encaisserDevis(devis: Devis): void {
    const encaisser = (id: number) =>
      this.materiauxService
        .encaisserDevis(id, {
          modePaiement: this.mode(),
          montantRecu: this.mode() === 'ESPECES' ? this.montantRecu() : null,
          referencePaiement: this.reference || null,
          sessionId: this.session()?.id,
          credit: this.credit(),
          acompte: this.credit() ? this.acompte() ?? 0 : null,
          emportes: this.resteALivrer() || this.choixLieux()
            ? this.panier().map((l) => ({
                articleId: l.article.id,
                conditionnementId: l.conditionnement?.id ?? null,
                quantite: l.article.suiviStock ? this.emporte(l) : l.quantite,
                lieuId: this.choixLieux() && l.article.suiviStock ? this.lieuLigne(l) : null
              }))
            : null
        })
        .subscribe({
          next: (ticket) => this.venteTerminee(ticket),
          error: (e) => {
            this.enCours.set(false);
            this.erreur.set(this.message(e, 'Encaissement du devis impossible.'));
          }
        });
    if (!this.devisModifie()) {
      encaisser(devis.id);
      return;
    }
    this.materiauxService.modifierDevis(devis.id, this.requeteDevis()).subscribe({
      next: (maj) => {
        this.devisCharge.set(maj);
        this.devisModifie.set(false);
        encaisser(maj.id);
      },
      error: (e) => {
        this.enCours.set(false);
        this.erreur.set(this.message(e, 'Mise a jour du devis impossible.'));
      }
    });
  }

  private venteTerminee(ticket: Ticket): void {
    this.enCours.set(false);
    this.paiement.set(false);
    this.dernierTicket.set(ticket);
    this.bonsRetrait.set([]);
    if (this.multiLieux()) {
      this.lieuxService.retraitsDeLaVente(ticket.id).subscribe({ next: (b) => this.bonsRetrait.set(b) });
    }
    this.decompterStock();
    if (this.commandeEnLigne()) {
      this.commandeEnLigne.set(null);
      this.chargerEnLigne();
    }
    this.panier.set([]);
    this.remise.set(0);
    this.clientNom = '';
    this.client.set(null);
    this.credit.set(false);
    this.resteALivrer.set(false);
    this.devisCharge.set(null);
    this.devisModifie.set(false);
    this.noteDevis = '';
    this.vueMobile.set('carte');
    this.sessions.rafraichir();
  }

  imprimer(ticket: Ticket): void {
    // Le parametrage complet porte l'adresse et le NIF imprimes en tete du recu.
    this.societeService.lire().subscribe({
      next: (societe) => imprimerTicket(ticket, societe),
      error: () => imprimerTicket(ticket, null)
    });
  }

  /** Facture A4 de la vente, ouverte dans un nouvel onglet pour l'imprimer ou l'enregistrer. */
  facture(ticket: Ticket): void {
    this.materiauxService.facturePdf(ticket.id).subscribe({
      next: (pdf) => this.materiauxService.ouvrirPdf(pdf),
      error: () => this.signaler('Facture indisponible.')
    });
  }

  nouvelleVente(): void {
    this.dernierTicket.set(null);
    this.information.set(null);
    this.refocaliser();
  }

  // --------------------------------------------- quincaillerie : initialisation

  private initialiserMateriaux(): void {
    this.lieuxService.chargerEtat();
    this.materiauxService.reglages().subscribe({
      next: (r) => {
        this.reglages.set(r);
        if (r.conditionnementsActifs) {
          this.materiauxService.conditionnementsVendables().subscribe({
            next: (liste) => {
              const parProduit = new Map<number, Conditionnement[]>();
              for (const c of liste) {
                parProduit.set(c.articleId, [...(parProduit.get(c.articleId) ?? []), c]);
              }
              this.conditionnements.set(parProduit);
            }
          });
        }
      }
    });
    const parametres = this.route.snapshot.queryParamMap;
    const devisId = Number(parametres.get('devis'));
    if (devisId) {
      this.materiauxService.unDevis(devisId).subscribe({
        next: (d) => this.chargerDevisQuandPret(d),
        error: (e) => this.signaler(this.message(e, 'Devis introuvable.'))
      });
    } else if (parametres.has('nouveauDevis')) {
      this.signaler('Composez le panier, choisissez le client, puis « Enregistrer en devis ».');
    }
  }

  /** Le devis se charge une fois le catalogue lu (ses lignes renvoient aux produits). */
  private chargerDevisQuandPret(d: Devis, essais = 0): void {
    if (this.articles().length === 0 && essais < 20) {
      setTimeout(() => this.chargerDevisQuandPret(d, essais + 1), 250);
      return;
    }
    this.chargerDevis(d);
  }

  // ------------------------------------------------ quincaillerie : conditionnements

  /** Unites de base deja prises dans le panier pour un produit, toutes unites confondues. */
  private unitesPrises(articleId: number): number {
    return this.panier().filter((l) => l.article.id === articleId).reduce((s, l) => s + unitesDeBase(l), 0);
  }

  conditionnementsDe(article: ArticlePdv): Conditionnement[] {
    return this.conditionnements().get(article.id) ?? [];
  }

  /** Un code-barres de conditionnement (le carton, la botte) ajoute directement cette unite. */
  private ajouterConditionnementParCode(code: string): boolean {
    for (const liste of this.conditionnements().values()) {
      const c = liste.find((x) => x.codeBarres === code);
      if (c) {
        const article = this.articles().find((a) => a.id === c.articleId);
        if (article) {
          this.ajouterLigne(article, c);
          this.recherche.set('');
          return true;
        }
      }
    }
    return false;
  }

  // ---------------------------------------------------- quincaillerie : client

  chercherClient(valeur: string): void {
    this.rechercheClient.set(valeur);
    clearTimeout(this.minuterieClient);
    if (valeur.trim().length < 2) {
      this.clientsTrouves.set([]);
      return;
    }
    this.minuterieClient = setTimeout(() => {
      this.materiauxService.chercherClients(valeur.trim()).subscribe({
        next: (liste) => this.clientsTrouves.set(liste),
        error: () => this.clientsTrouves.set([])
      });
    }, 250);
  }

  choisirClient(client: ClientPdv): void {
    this.client.set(client);
    this.clientNom = client.nom;
    this.rechercheClient.set('');
    this.clientsTrouves.set([]);
    this.devisModifie.set(true);
    this.refocaliser();
  }

  retirerClient(): void {
    this.client.set(null);
    this.clientNom = '';
    this.credit.set(false);
    this.devisModifie.set(true);
  }

  clientCree(client: ClientPdv): void {
    this.nouveauClient.set(false);
    this.choisirClient(client);
  }

  /** Apres un versement, la situation du client choisi se met a jour. */
  rafraichirClient(): void {
    const c = this.client();
    if (c) {
      this.materiauxService.client(c.id).subscribe({ next: (maj) => this.client.set(maj) });
    }
  }

  // ----------------------------------------------------- quincaillerie : devis

  private requeteDevis(): DevisRequete {
    const devis = this.devisCharge();
    return {
      clientId: this.client()?.id ?? null,
      clientNom: this.client() ? null : this.clientNom.trim() || null,
      lignes: this.panier().map((l) => ({
        articleId: l.article.id,
        conditionnementId: l.conditionnement?.id ?? null,
        quantite: l.quantite
      })),
      remise: this.remise() || 0,
      valableJusqu: devis?.valableJusqu ?? null,
      note: this.noteDevis.trim() || null
    };
  }

  /** Enregistre le panier en devis (ou met a jour le devis charge) et propose son impression. */
  enregistrerDevis(): void {
    if (this.panier().length === 0 || this.enregistrementDevis()) {
      return;
    }
    if (!this.client() && !this.clientNom.trim()) {
      this.signaler('Indiquez le client du devis (fiche ou nom).');
      return;
    }
    this.enregistrementDevis.set(true);
    const devis = this.devisCharge();
    const requete = this.requeteDevis();
    (devis ? this.materiauxService.modifierDevis(devis.id, requete) : this.materiauxService.creerDevis(requete)).subscribe({
      next: (enregistre) => {
        this.enregistrementDevis.set(false);
        this.materiauxService.devisPdf(enregistre.id).subscribe({
          next: (pdf) => this.materiauxService.ouvrirPdf(pdf)
        });
        this.viderPanier();
        this.signaler(`Devis ${enregistre.numero} enregistre (${enregistre.total.toLocaleString('fr-FR')} F), valable jusqu'au `
          + `${new Date(enregistre.valableJusqu).toLocaleDateString('fr-FR')}.`);
      },
      error: (e) => {
        this.enregistrementDevis.set(false);
        this.signaler(this.message(e, 'Enregistrement du devis impossible.'));
      }
    });
  }

  ouvrirDevis(): void {
    this.fenetreDevis.set(true);
    this.materiauxService.devis({ statut: 'EN_COURS' }).subscribe({
      next: (p) => this.devisOuverts.set(p.content),
      error: () => this.devisOuverts.set([])
    });
  }

  /** Charge un devis dans le panier, a ses prix : il se modifie ou s'encaisse. */
  chargerDevis(resume: Devis): void {
    const appliquer = (d: Devis) => {
      const lignes: LignePanier[] = [];
      for (const l of d.lignes) {
        const article = this.articles().find((a) => a.id === l.articleId);
        if (!article) {
          this.signaler(`${l.libelle} n'est plus en vente : refaites le devis.`);
          return;
        }
        const conditionnement = l.conditionnementId
          ? this.conditionnementsDe(article).find((c) => c.id === l.conditionnementId) ?? null
          : null;
        if (l.conditionnementId && !conditionnement) {
          this.signaler(`Le conditionnement de ${l.libelle} n'est plus vendu : refaites le devis.`);
          return;
        }
        // Le panier affiche le prix garanti par le devis.
        lignes.push({
          article: conditionnement ? article : { ...article, prixVente: l.prixUnitaire },
          conditionnement: conditionnement ? { ...conditionnement, prixVente: l.prixUnitaire } : null,
          quantite: l.quantite
        });
      }
      this.panier.set(lignes);
      this.remise.set(d.remise);
      this.noteDevis = d.note ?? '';
      this.clientNom = d.clientNom;
      this.client.set(null);
      if (d.clientId) {
        this.materiauxService.client(d.clientId).subscribe({ next: (c) => this.client.set(c) });
      }
      this.devisCharge.set(d);
      this.devisModifie.set(false);
      this.fenetreDevis.set(false);
      this.vueMobile.set('commande');
      if (d.expire) {
        this.signaler(`Le devis ${d.numero} a expire : un gerant doit le prolonger avant l'encaissement.`);
      }
    };
    if (resume.lignes?.length) {
      appliquer(resume);
    } else {
      this.materiauxService.unDevis(resume.id).subscribe({ next: appliquer });
    }
  }

  /** Quitte le devis charge sans le modifier. */
  quitterDevis(): void {
    this.viderPanier();
    this.router.navigate([], { queryParams: {} });
  }

  // ------------------------------------------------------------- interne

  /** Le stock affiche suit les ventes du poste sans recharger tout le catalogue. */
  private decompterStock(): void {
    const vendus = new Map<number, number>();
    for (const l of this.panier()) {
      vendus.set(l.article.id, (vendus.get(l.article.id) ?? 0) + unitesDeBase(l));
    }
    this.articles.update((liste) =>
      liste.map((a) =>
        a.suiviStock && vendus.has(a.id) ? { ...a, stockDisponible: a.stockDisponible - (vendus.get(a.id) ?? 0) } : a
      )
    );
  }

  private signaler(message: string): void {
    this.information.set(message);
    this.refocaliser();
  }

  captureDouchette(e: KeyboardEvent): void {
    const fenetreOuverte = this.paiement() || this.fenetreEnLigne() || !!this.choixUnite() || this.fenetreDevis()
      || this.nouveauClient() || !!this.compteOuvert();
    this.douchette.touche(e, !fenetreOuverte);
  }

  private refocaliser(): void {
    // Sur telephone, le clavier ne doit pas surgir a chaque produit touche.
    if (window.matchMedia('(min-width: 821px)').matches) {
      setTimeout(() => this.saisie()?.nativeElement.focus());
    }
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
