import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { switchMap } from 'rxjs';
import {
  ArticlePdv,
  CategoriePdv,
  MODES_PAIEMENT_PDV,
  ModePaiementPdv,
  Ticket
} from '../../../core/models/pdv';
import {
  CommandeDetail,
  CommandeResume,
  LIBELLES_STATUT_LIGNE,
  LigneCommande,
  TablePlan
} from '../../../core/models/restauration';
import { PdvService } from '../../../core/services/pdv.service';
import { RestaurationService } from '../../../core/services/restauration.service';
import { SocieteService } from '../../../core/services/societe.service';
import { SessionTravailService } from '../../../core/services/session-travail.service';
import { BandeauSession } from '../../../shared/bandeau-session';
import { BoutonEspeces } from '../../../shared/bouton-especes';
import { Modale } from '../../../shared/modale';
import { OperationService } from '../../../core/operation/operation.service';
import { FinDeSaisie, LecteurDouchette, normaliserCodeBarres } from '../../../shared/code-barres';
import { imprimerAddition, imprimerTicket } from '../../../shared/ticket-pdv';

/** Consignes proposees en un geste pour la cuisine ou le bar. */
const CONSIGNES = ['Sans piment', 'Peu pimente', 'Bien cuit', 'A point', 'Sans glace', 'Bien frais', 'A emporter'];

const BILLETS = [500, 1000, 2000, 5000, 10000];
const RAFRAICHISSEMENT_MS = 8000;

/**
 * Caisse du bar, du restaurant ou du cafe : la prise de commande d'une table,
 * pensee pour la tablette du serveur.
 * <p>
 * En haut les categories, au centre les plats en photo, a droite la table et
 * sa commande, en bas les autres commandes en cours pour passer de l'une a
 * l'autre. Un geste sur un plat l'ajoute en attente ; le serveur transmet sa
 * commande a la caisse, et seule la caisse l'envoie en preparation, d'un coup
 * les plats a la cuisine et les boissons au bar. L'addition
 * s'imprime pour la table et se regle en une fois ou par convive.
 * <p>
 * Sans commande dans l'adresse, l'ecran sert de caisse au comptoir : le premier
 * article choisi ouvre une commande sans table. L'encaissement exige une
 * session de caisse ouverte par un gerant ; les commandes se prennent et
 * partent en preparation meme caisse fermee.
 */
@Component({
  selector: 'app-commande-pdv',
  host: { '(document:keydown.escape)': 'tiroir.set(false)', '(document:keydown)': 'captureDouchette($event)' },
  imports: [DecimalPipe, FormsModule, RouterLink, Modale, BandeauSession, BoutonEspeces],
  templateUrl: './commande.html',
  styleUrl: './commande.scss'
})
export class CommandePdv implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly pdv = inject(PdvService);
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly libellesStatut = LIBELLES_STATUT_LIGNE;
  readonly consignes = CONSIGNES;
  readonly modes = MODES_PAIEMENT_PDV;
  readonly estGestionnaire = this.auth.aRole('ADMIN', 'GERANT');
  /** Le serveur prend et suit ses commandes ; l'encaissement, l'addition et les especes sont a la caisse. */
  readonly peutEncaisser = this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER');
  /** Seule la caisse envoie en preparation ; le serveur lui transmet ses commandes. */
  readonly peutEnvoyer = this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER');

  readonly commande = signal<CommandeDetail | null>(null);
  readonly articles = signal<ArticlePdv[]>([]);
  readonly categories = signal<CategoriePdv[]>([]);
  readonly categorieChoisie = signal<number | null>(null);
  readonly recherche = signal('');
  readonly erreur = signal<string | null>(null);
  readonly information = signal<string | null>(null);
  readonly occupe = signal(false);
  readonly maintenant = signal(Date.now());
  /** Les autres commandes en cours, en bas de l'ecran. */
  readonly enCours = signal<CommandeResume[]>([]);
  /** Sur telephone, une seule vue a la fois : la carte des plats ou la commande. */
  readonly vueMobile = signal<'carte' | 'commande'>('carte');
  readonly nombreLignes = computed(() =>
    (this.commande()?.lignes ?? []).filter((l) => l.statut !== 'ANNULEE').length
  );

  // Panneau des commandes en cours
  readonly tiroir = signal(false);
  readonly rechercheCommandes = signal('');
  readonly filtreCommandes = signal<'toutes' | 'urgentes' | 'salle' | 'comptoir' | 'en-ligne'>('toutes');
  readonly filtres: { valeur: 'toutes' | 'urgentes' | 'salle' | 'comptoir' | 'en-ligne'; libelle: string }[] = [
    { valeur: 'toutes', libelle: 'Toutes' },
    { valeur: 'urgentes', libelle: 'A traiter' },
    { valeur: 'salle', libelle: 'En salle' },
    { valeur: 'comptoir', libelle: 'Comptoir' },
    // Point de vente avec Marketplace seulement.
    ...(this.auth.aModule('MARKETPLACE') ? [{ valeur: 'en-ligne' as const, libelle: 'En ligne' }] : [])
  ];
  private readonly champRechercheTiroir = viewChild<ElementRef<HTMLInputElement>>('rechercheTiroir');

  // Consigne d'une ligne en attente
  readonly ligneNote = signal<LigneCommande | null>(null);
  note = '';

  // Transfert, couverts, annulation
  readonly transfert = signal<TablePlan[] | null>(null);
  readonly reglages = signal(false);
  couverts: number | null = null;

  // Encaissement
  readonly paiement = signal(false);
  readonly parConvive = signal(false);
  /** Quantite reglee par ligne, en partage par convive. */
  readonly parts = signal<Record<number, number>>({});
  readonly mode = signal<ModePaiementPdv>('ESPECES');
  readonly montantRecu = signal<number | null>(null);
  readonly remise = signal(0);
  reference = '';
  readonly dernierTicket = signal<Ticket | null>(null);

  // Vente au comptoir : pas encore de commande tant qu'aucun article n'est choisi
  readonly comptoir = signal(false);
  clientComptoir = '';

  // Session ouverte par un gerant sur laquelle le poste encaisse : indefinie tant qu'elle n'est pas lue, nulle si aucune
  private readonly sessions = inject(SessionTravailService);
  /** Confirmation des operations importantes (envoi, validation, deplacement, encaissement). */
  private readonly operation = inject(OperationService);
  readonly session = this.sessions.courante;
  /** Aucune caisse ouverte pour l'utilisateur : rien ne bouge en salle (le serveur le refuse aussi). */
  readonly salleFermee = computed(() => this.sessions.ouvertes()?.length === 0);

  private id = 0;

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

  /** Nombre d'articles a la carte par categorie, affiche sur les cartes de categorie. */
  readonly parCategorie = computed(() => {
    const nombres = new Map<number, number>();
    for (const a of this.articles()) {
      if (a.categorieId) {
        nombres.set(a.categorieId, (nombres.get(a.categorieId) ?? 0) + 1);
      }
    }
    return nombres;
  });

  /** Quantite en attente d'envoi par article : la carte du plat affiche alors son compteur. */
  readonly quantitesAttente = computed(() => {
    const quantites = new Map<number, LigneCommande>();
    for (const l of this.enAttente()) {
      if (!l.note && !quantites.has(l.articleId)) {
        quantites.set(l.articleId, l);
      }
    }
    return quantites;
  });

  private readonly parArticle = computed(() => new Map(this.articles().map((a) => [a.id, a])));

  readonly enAttente = computed(() => this.commande()?.lignes.filter((l) => l.statut === 'BROUILLON') ?? []);
  /** Articles commandes par le client depuis le code QR, en attente de validation. */
  readonly proposees = computed(() => this.commande()?.lignes.filter((l) => l.statut === 'PROPOSEE') ?? []);
  readonly totalProposees = computed(() => this.proposees().reduce((s, l) => s + l.total, 0));
  /** Articles transmis par le serveur (et non commandes par le client) parmi ceux a valider. */
  readonly transmisParServeur = computed(() => this.proposees().some((l) => l.origine === 'SERVEUR'));
  readonly envoyees = computed(() =>
    (this.commande()?.lignes.filter((l) => l.statut !== 'BROUILLON' && l.statut !== 'PROPOSEE') ?? [])
      .slice()
      .sort((a, b) => (b.envoi ?? 0) - (a.envoi ?? 0) || a.id - b.id)
  );
  readonly pretes = computed(() => this.envoyees().filter((l) => l.statut === 'PRETE'));
  /** Articles partis en preparation et pas encore servis : la commande ne s'encaisse qu'une fois tout servi. */
  readonly nonServies = computed(() =>
    this.envoyees().filter((l) => !l.payee && (l.statut === 'A_PREPARER' || l.statut === 'EN_PREPARATION' || l.statut === 'PRETE'))
  );
  /** Encaissement possible : rien en attente d'envoi, tout servi, une caisse choisie. */
  readonly encaissable = computed(() => !!this.session() && this.enAttente().length === 0 && this.nonServies().length === 0);
  readonly aPayer = computed(() => this.envoyees().filter((l) => !l.payee && l.statut !== 'ANNULEE'));
  readonly totalAttente = computed(() => this.enAttente().reduce((s, l) => s + l.total, 0));
  readonly ouverte = computed(() => this.commande()?.resume.statut === 'OUVERTE');

  readonly montantAPayer = computed(() => {
    const base = this.parConvive()
      ? this.aPayer().reduce((s, l) => s + (this.parts()[l.id] ?? 0) * l.prixUnitaire, 0)
      : this.commande()?.resume.resteAPayer ?? 0;
    return Math.max(0, Math.round(base - (this.remise() || 0)));
  });
  readonly monnaie = computed(() => Math.max(0, (this.montantRecu() ?? 0) - this.montantAPayer()));
  readonly billets = computed(() => {
    const total = this.montantAPayer();
    const suggestions = new Set<number>([total, Math.ceil(total / 1000) * 1000]);
    BILLETS.filter((b) => b > total).forEach((b) => suggestions.add(b));
    return [...suggestions].filter((v) => v >= total).sort((a, b) => a - b).slice(0, 5);
  });
  readonly paiementPossible = computed(
    () =>
      this.montantAPayer() > 0 &&
      (this.mode() !== 'ESPECES' || (this.montantRecu() ?? 0) >= this.montantAPayer())
  );

  ngOnInit(): void {
    // Sans le bandeau de caisse (serveur), l'etat de la caisse se lit ici.
    if (!this.peutEncaisser) {
      this.sessions.rafraichir();
    }
    // Passer d'une commande a l'autre par le bandeau du bas reutilise l'ecran.
    this.route.paramMap.subscribe((params) => {
      this.id = Number(params.get('id') ?? 0);
      this.comptoir.set(this.id === 0);
      this.commande.set(this.id === 0 ? this.nouvelleVente() : null);
      this.erreur.set(null);
      this.information.set(null);
      this.clientComptoir = '';
      this.charger();
      this.chargerEnCours();
    });
    this.pdv.catalogueCaisse().subscribe({ next: (a) => this.articles.set(a) });
    this.pdv.categories().subscribe({ next: (c) => this.categories.set(c.filter((x) => x.actif)) });
    // La cuisine fait avancer les lignes : la commande se relit regulierement.
    const minuterie = setInterval(() => {
      this.maintenant.set(Date.now());
      if (!this.occupe() && !this.paiement()) {
        this.charger();
        this.chargerEnCours();
      }
    }, RAFRAICHISSEMENT_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(minuterie);
      this.finDeSaisie.arreter();
      this.douchette.arreter();
    });
  }

  depuis(date?: string): string {
    if (!date) {
      return '';
    }
    const minutes = Math.max(0, Math.floor((this.maintenant() - new Date(date).getTime()) / 60000));
    return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
  }

  article(id: number): ArticlePdv | undefined {
    return this.parArticle().get(id);
  }

  /** Commandes qui attendent un geste : demande du client, plats prets a servir, addition a encaisser. */
  readonly urgentes = computed(() => this.enCours().filter((o) => this.rang(o) <= 2).length);

  /**
   * Commandes du panneau, filtrees puis regroupees par etat, de la plus
   * urgente a la plus calme ; dans un groupe, la plus ancienne d'abord.
   */
  readonly groupesCommandes = computed(() => {
    const motif = this.rechercheCommandes().trim().toLowerCase();
    const filtre = this.filtreCommandes();
    const retenues = this.enCours()
      .filter((o) => this.retenue(o, filtre))
      .filter((o) => !motif || o.repere.toLowerCase().includes(motif) || o.numero.toLowerCase().includes(motif)
        || o.serveurNom.toLowerCase().includes(motif))
      .sort((a, b) => this.rang(a) - this.rang(b) || a.ouverteLe.localeCompare(b.ouverteLe));
    const titres = ['A valider / appels', 'Prets a servir', 'Addition demandee', 'Articles a envoyer', 'En cuisine', 'Servies'];
    const classes = ['demande', 'pret', 'addition', 'attente', 'cuisine', 'servi'];
    return titres
      .map((titre, rang) => ({ titre, classe: classes[rang], commandes: retenues.filter((o) => this.rang(o) === rang) }))
      .filter((g) => g.commandes.length > 0);
  });

  ouvrirTiroir(): void {
    this.chargerEnCours();
    this.rechercheCommandes.set('');
    this.tiroir.set(true);
    setTimeout(() => this.champRechercheTiroir()?.nativeElement.focus());
  }

  /** Entree dans la recherche : on ouvre la premiere commande trouvee. */
  ouvrirPremiere(): void {
    const premiere = this.groupesCommandes()[0]?.commandes[0];
    if (premiere) {
      this.tiroir.set(false);
      void this.router.navigate(['/pdv/commandes', premiere.id]);
    }
  }

  compte(filtre: 'toutes' | 'urgentes' | 'salle' | 'comptoir' | 'en-ligne'): number {
    return this.enCours().filter((o) => this.retenue(o, filtre)).length;
  }

  /** Ce qui se passe sur la commande, en quelques mots. */
  detailEtat(o: CommandeResume): string {
    if (o.appel === 'ADDITION') {
      return 'Le client demande l\'addition';
    }
    if (o.appel === 'SERVEUR') {
      return 'Le client appelle un serveur';
    }
    if (o.aValider > 0) {
      return o.enLigne ? `Commande en ligne : ${o.aValider} article(s) a valider`
        : `${o.aValider} article(s) a valider par la caisse`;
    }
    if (o.pretes > 0) {
      return `${o.pretes} article(s) pret(s) a servir`;
    }
    if (o.additionLe) {
      return 'Addition imprimee, en attente du reglement';
    }
    if (o.aEnvoyer > 0) {
      return `${o.aEnvoyer} article(s) pas encore envoye(s)`;
    }
    if (o.enPreparation > 0) {
      return `${o.enPreparation} article(s) en preparation`;
    }
    return o.total > 0 ? `reste ${o.resteAPayer} F a regler` : 'aucun article';
  }

  private retenue(o: CommandeResume, filtre: 'toutes' | 'urgentes' | 'salle' | 'comptoir' | 'en-ligne'): boolean {
    switch (filtre) {
      case 'urgentes':
        return this.rang(o) <= 2;
      case 'salle':
        return !!o.tableId;
      case 'comptoir':
        return !o.tableId && !o.enLigne;
      case 'en-ligne':
        return !!o.enLigne;
      default:
        return true;
    }
  }

  /** Rang d'urgence : 0 demande du client, 1 pret a servir, 2 addition, 3 a envoyer, 4 en cuisine, 5 servi. */
  private rang(o: CommandeResume): number {
    if (o.aValider > 0 || o.appel) {
      return 0;
    }
    if (o.pretes > 0) {
      return 1;
    }
    if (o.additionLe) {
      return 2;
    }
    if (o.aEnvoyer > 0) {
      return 3;
    }
    return o.enPreparation > 0 ? 4 : 5;
  }

  /** Etat d'une autre commande, en badge. */
  etatCommande(c: CommandeResume): { libelle: string; classe: string } {
    if (c.aValider > 0 || c.appel) {
      return { libelle: c.appel === 'ADDITION' ? 'Addition' : c.appel ? 'Appel' : 'Client', classe: 'demande' };
    }
    if (c.pretes > 0) {
      return { libelle: 'Pret', classe: 'pret' };
    }
    if (c.additionLe) {
      return { libelle: 'Addition', classe: 'addition' };
    }
    if (c.aEnvoyer > 0) {
      return { libelle: 'A envoyer', classe: 'attente' };
    }
    return c.enPreparation > 0 ? { libelle: 'En cuisine', classe: 'cuisine' } : { libelle: 'Servi', classe: 'servi' };
  }

  // ------------------------------------------------------------ saisie

  ajouter(article: ArticlePdv): void {
    if (this.salleFermee()) {
      this.erreur.set('Caisse fermee : un gerant doit ouvrir la session avant toute commande.');
      return;
    }
    if (this.id === 0) {
      this.ouvrirAuComptoir(article);
      return;
    }
    this.executer(this.service.ajouter(this.id, article.id), () => this.information.set(null));
  }

  /** Premier article d'une vente au comptoir : la commande sans table nait avec lui. */
  private ouvrirAuComptoir(article: ArticlePdv): void {
    if (this.occupe()) {
      return;
    }
    const heure = new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    const libelle = this.clientComptoir.trim() || `Comptoir ${heure}`;
    this.occupe.set(true);
    this.erreur.set(null);
    this.service
      .ouvrir({ libelle, sessionId: this.session()?.id })
      .pipe(switchMap((c) => this.service.ajouter(c.resume.id, article.id)))
      .subscribe({
        next: (commande) => {
          this.occupe.set(false);
          void this.router.navigate(['/pdv/commandes', commande.resume.id], { replaceUrl: true });
        },
        error: (e) => {
          this.occupe.set(false);
          this.erreur.set(this.message(e, 'Ouverture de la vente impossible.'));
        }
      });
  }

  /** Douchette sans Entree : le code complet dans la recherche passe a la commande. */
  private readonly finDeSaisie = new FinDeSaisie(() => this.ajouterSiCodeComplet());
  /** Lectures faites alors que le focus n'est pas dans la recherche. */
  private readonly douchette = new LecteurDouchette((code, parEntree) => {
    this.recherche.set(code);
    if (parEntree) {
      this.valider();
    } else {
      this.ajouterSiCodeComplet();
    }
  });

  captureDouchette(e: KeyboardEvent): void {
    const fenetreOuverte = this.tiroir() || this.paiement() || this.reglages() ||
      this.ligneNote() !== null || this.transfert() !== null;
    this.douchette.touche(e, !fenetreOuverte);
  }

  saisir(valeur: string): void {
    this.recherche.set(valeur);
    this.finDeSaisie.relancer();
  }

  /** Ajoute l'article dont le code-barres est exactement dans la recherche. */
  private ajouterSiCodeComplet(): boolean {
    const valeur = normaliserCodeBarres(this.recherche());
    const article = valeur.length >= 4 ? this.articles().find((a) => a.codeBarres === valeur) : undefined;
    if (!article) {
      return false;
    }
    this.ajouter(article);
    this.recherche.set('');
    return true;
  }

  /** Entree dans la recherche : un code-barres exact, ou le seul article filtre. */
  valider(): void {
    this.finDeSaisie.arreter();
    if (this.ajouterSiCodeComplet()) {
      return;
    }
    const filtres = this.articlesAffiches();
    if (filtres.length === 1) {
      this.ajouter(filtres[0]);
      this.recherche.set('');
    } else if (filtres.length === 0 && normaliserCodeBarres(this.recherche()).length >= 4) {
      this.erreur.set(`Aucun article pour « ${normaliserCodeBarres(this.recherche())} »`);
    }
  }

  changerQuantite(ligne: LigneCommande, quantite: number): void {
    if (!quantite || quantite <= 0) {
      this.retirer(ligne);
      return;
    }
    this.executer(this.service.modifierLigne(this.id, ligne.id, quantite, ligne.note));
  }

  retirer(ligne: LigneCommande): void {
    this.executer(this.service.retirerLigne(this.id, ligne.id));
  }

  ouvrirNote(ligne: LigneCommande): void {
    this.note = ligne.note ?? '';
    this.ligneNote.set(ligne);
  }

  ajouterConsigne(consigne: string): void {
    this.note = this.note ? `${this.note}, ${consigne}` : consigne;
  }

  enregistrerNote(): void {
    const ligne = this.ligneNote();
    if (!ligne) {
      return;
    }
    this.executer(this.service.modifierLigne(this.id, ligne.id, ligne.quantite, this.note.trim() || undefined), () =>
      this.ligneNote.set(null)
    );
  }

  // ----------------------------------------------------------- service

  /** La caisse envoie en preparation ; le serveur transmet a la caisse. */
  async envoyer(): Promise<void> {
    const accepte = await this.operation.demander(this.peutEnvoyer
      ? { titre: 'Envoyer la commande en preparation ?', message: 'Les articles partent sur les ecrans de la cuisine et du bar.',
          libelleAction: 'Envoyer' }
      : { titre: 'Transmettre la commande a la caisse ?', message: 'La caisse la validera avant son envoi en preparation.',
          libelleAction: 'Transmettre' });
    if (!accepte) {
      return;
    }
    if (!this.peutEnvoyer) {
      this.executer(this.service.transmettre(this.id), () =>
        this.information.set('Commande transmise a la caisse : elle partira en preparation des sa validation.'));
      return;
    }
    this.executer(this.service.envoyer(this.id), () => this.information.set('Commande envoyee en preparation.'));
  }

  async validerDemandes(): Promise<void> {
    const accepte = await this.operation.demander({ titre: 'Valider la commande ?',
      message: 'Les articles demandes partent en preparation.', libelleAction: 'Valider' });
    if (!accepte) {
      return;
    }
    this.executer(this.service.validerDemandes(this.id), () =>
      this.information.set('Commande validee et envoyee en preparation.'));
  }

  refuserDemandes(): void {
    const serveur = this.transmisParServeur();
    this.executer(this.service.refuserDemandes(this.id), () => this.information.set(serveur
      ? 'Commande refusee : les articles du serveur reviennent a corriger.'
      : 'Commande du client refusee.'));
  }

  priseEnCompte(): void {
    this.executer(this.service.priseEnCompte(this.id));
  }

  servir(ligne?: LigneCommande): void {
    this.executer(this.service.servir(this.id, ligne ? [ligne.id] : undefined));
  }

  annulerLigne(ligne: LigneCommande): void {
    this.executer(this.service.annulerLigne(this.id, ligne.id));
  }

  imprimerAddition(): void {
    this.executer(this.service.addition(this.id), (commande) => {
      this.societeService.lire().subscribe({
        next: (societe) => imprimerAddition(commande, societe),
        error: () => imprimerAddition(commande, null)
      });
    });
  }

  ouvrirReglages(): void {
    this.couverts = this.commande()?.resume.couverts ?? null;
    this.erreur.set(null);
    this.reglages.set(true);
  }

  enregistrerCouverts(): void {
    this.executer(this.service.modifier(this.id, { couverts: this.couverts || null, note: this.commande()?.note }), () =>
      this.reglages.set(false)
    );
  }

  demanderTransfert(): void {
    this.reglages.set(false);
    this.service.plan().subscribe({
      next: (plan) => this.transfert.set(plan.salles.flatMap((s) => s.tables).filter((t) => !t.commande)),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des tables impossible.'))
    });
  }

  async transferer(t: TablePlan): Promise<void> {
    const accepte = await this.operation.demander({ titre: `Deplacer la commande sur la table ${t.table.nom} ?`,
      libelleAction: 'Deplacer' });
    if (!accepte) {
      return;
    }
    this.executer(this.service.transferer(this.id, t.table.id), () => {
      this.transfert.set(null);
      this.information.set(`Commande deplacee sur la table ${t.table.nom}.`);
    });
  }

  annulerCommande(): void {
    this.executer(this.service.annuler(this.id), (c) => {
      this.reglages.set(false);
      void this.router.navigateByUrl(c.resume.tableId ? '/pdv/salle' : '/pdv/comptoir');
    });
  }

  // ------------------------------------------------------ encaissement

  /** Des articles restent a envoyer : ils partent d'abord, puis la fenetre de paiement s'ouvre. */
  /** La commande se regle une fois preparee et entierement servie. */
  ouvrirPaiement(mode: ModePaiementPdv = 'ESPECES'): void {
    if (this.enAttente().length > 0) {
      this.erreur.set(this.peutEnvoyer
        ? "Envoyez d'abord les articles en preparation : ils devront etre servis avant l'encaissement."
        : "Transmettez d'abord les articles a la caisse : ils devront etre prepares et servis avant l'encaissement.");
      return;
    }
    if (this.proposees().length > 0) {
      this.erreur.set('Des articles attendent la validation de la caisse : ils devront etre prepares et servis avant l\'encaissement.');
      return;
    }
    if (this.nonServies().length > 0) {
      this.erreur.set(`${this.nonServies().length} article(s) pas encore servi(s) : la commande doit etre preparee et servie avant l'encaissement.`);
      return;
    }
    this.afficherPaiement(mode);
  }

  private afficherPaiement(mode: ModePaiementPdv): void {
    this.parConvive.set(false);
    this.parts.set({});
    this.remise.set(0);
    this.mode.set(mode);
    this.reference = '';
    this.erreur.set(null);
    this.montantRecu.set(this.commande()?.resume.resteAPayer ?? 0);
    this.paiement.set(true);
  }

  basculerConvive(actif: boolean): void {
    this.parConvive.set(actif);
    this.parts.set({});
    this.montantRecu.set(this.montantAPayer());
  }

  changerPart(ligne: LigneCommande, delta: number): void {
    const actuelle = this.parts()[ligne.id] ?? 0;
    const suivante = Math.min(ligne.quantite, Math.max(0, actuelle + delta));
    this.parts.update((p) => ({ ...p, [ligne.id]: suivante }));
    this.montantRecu.set(this.montantAPayer());
  }

  async encaisser(): Promise<void> {
    if (this.occupe() || !this.paiementPossible()) {
      return;
    }
    const accepte = await this.operation.demander({
      titre: `Encaisser ${Math.round(this.montantAPayer()).toLocaleString('fr-FR')} F ?`,
      message: 'Verifiez le montant et le moyen de paiement avant de valider.',
      libelleAction: 'Encaisser'
    });
    if (!accepte || this.occupe()) {
      return;
    }
    const lignes = this.parConvive()
      ? Object.entries(this.parts())
          .filter(([, q]) => q > 0)
          .map(([ligneId, quantite]) => ({ ligneId: Number(ligneId), quantite }))
      : undefined;
    this.occupe.set(true);
    this.erreur.set(null);
    this.service
      .encaisser(this.id, {
        lignes,
        remise: this.remise() || 0,
        modePaiement: this.mode(),
        montantRecu: this.mode() === 'ESPECES' ? this.montantRecu() : null,
        referencePaiement: this.reference || undefined,
        sessionId: this.session()?.id
      })
      .subscribe({
        next: (resultat) => {
          this.occupe.set(false);
          this.paiement.set(false);
          this.commande.set(resultat.commande);
          this.dernierTicket.set(resultat.ticket);
          this.sessions.rafraichir();
        },
        error: (e) => {
          this.occupe.set(false);
          this.erreur.set(this.message(e, 'Encaissement impossible.'));
        }
      });
  }

  imprimerTicket(ticket: Ticket): void {
    this.societeService.lire().subscribe({
      next: (societe) => imprimerTicket(ticket, societe),
      error: () => imprimerTicket(ticket, null)
    });
  }

  fermerTicket(): void {
    this.dernierTicket.set(null);
    if (!this.ouverte()) {
      // Vente au comptoir reglee : la caisse est prete pour le client suivant.
      void this.router.navigateByUrl(this.commande()?.resume.tableId ? '/pdv/salle' : '/pdv/comptoir');
    }
  }

  // ----------------------------------------------------------- interne

  /** Commande vide d'une vente au comptoir, avant le premier article. */
  private nouvelleVente(): CommandeDetail {
    return {
      resume: {
        id: 0, numero: '', repere: 'Vente au comptoir', serveurNom: this.auth.utilisateur()?.nomComplet ?? '',
        statut: 'OUVERTE', ouverteLe: new Date().toISOString(), total: 0, resteAPayer: 0,
        aEnvoyer: 0, enPreparation: 0, pretes: 0, aValider: 0
      },
      lignes: []
    };
  }

  private chargerEnCours(): void {
    this.service.plan().subscribe({
      next: (plan) => this.enCours.set([
        ...plan.salles.flatMap((s) => s.tables).map((t) => t.commande).filter((c): c is CommandeResume => !!c?.id),
        ...plan.sansTable
      ]),
      error: () => undefined
    });
  }

  private charger(): void {
    if (this.id === 0) {
      return;
    }
    this.service.commande(this.id).subscribe({
      next: (commande) => this.commande.set(commande),
      error: (e) => this.erreur.set(this.message(e, 'Commande introuvable.'))
    });
  }

  private executer(appel: ReturnType<RestaurationService['commande']>, ensuite?: (c: CommandeDetail) => void): void {
    this.occupe.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (commande) => {
        this.occupe.set(false);
        this.commande.set(commande);
        ensuite?.(commande);
      },
      error: (e) => {
        this.occupe.set(false);
        this.erreur.set(this.message(e, 'Operation impossible.'));
      }
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
