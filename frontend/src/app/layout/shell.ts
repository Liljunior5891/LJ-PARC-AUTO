import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { SocieteService } from '../core/services/societe.service';
import { ClocheNotifications } from '../shared/cloche-notifications';
import { IconeDemandesEnLigne } from '../shared/icone-demandes-en-ligne';
import { MINUTES_INACTIVITE, VerrouService } from '../core/services/verrou.service';
import { VerrouCaisse } from './verrou-caisse';
import { NotificationsPdv } from './notifications-pdv';
import { AlertesPdv } from './alertes-pdv';
import { PanneauCompte } from './panneau-compte';
import { activerDefilementTableaux } from '../shared/defilement-tableaux';
import { CodeModule, Role } from '../core/models/models';
import { LieuxService } from '../core/services/lieux.service';
import { environment } from '../../environments/environment';

interface EntreeMenu {
  libelle: string;
  /** Page ouverte ; absente pour un groupe, qui deplie ses sous-menus. */
  route?: string;
  /** Ligne d'explication affichee sous le libelle, dans le panneau de l'onglet. */
  description: string;
  /** Visible seulement quand l'entreprise a un entrepot. */
  avecLieux?: boolean;
  /** Profils autorises ; absent, l'entree est visible de tous. */
  roles?: Role[];
  /** Module dont depend l'entree ; absent, elle fait partie du socle commun. */
  module?: CodeModule;
  /** Type(s) d'activite du point de vente exige(s) (les tables n'existent qu'en restauration). */
  variante?: string | string[];
  /** Masquee si l'entreprise a l'un de ces modules (ses ecrans y sont deja integres). */
  sansModules?: CodeModule[];
  /** Visible seulement si l'entreprise a aussi tous ces modules. */
  avecModules?: CodeModule[];
  /** Visible seulement si l'entreprise a aussi au moins l'un de ces modules. */
  avecUnDesModules?: CodeModule[];
  /** Sous-menus d'un groupe (Stock, Caisse), affiches sur une seconde rangee. */
  enfants?: EntreeMenu[];
}

interface Onglet {
  id: string;
  libelle: string;
  entrees: EntreeMenu[];
  /** Routes de detail rattachees a l'onglet sans figurer dans son panneau. */
  autresRoutes?: string[];
  /** Module dont depend l'onglet entier. */
  module?: CodeModule;
  /** Masque si l'entreprise a l'un de ces modules (ses ecrans y sont deja integres). */
  sansModules?: CodeModule[];
}

@Component({
  selector: 'app-shell',
  host: { '(document:click)': 'clicDocument($event)' },
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ClocheNotifications, IconeDemandesEnLigne, VerrouCaisse, NotificationsPdv, AlertesPdv, PanneauCompte],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);
  private readonly router = inject(Router);
  private readonly lieux = inject(LieuxService);

  readonly nomPlateforme = environment.nomPlateforme;
  readonly estSuperAdmin = this.auth.estSuperAdmin();

  /**
   * Poste de travail (caissier, serveur, cuisinier) : son seul ecran, sous une
   * barre fine, sans le menu de l'application.
   */
  readonly estPoste = this.auth.estPoste();
  /** Le caissier et le serveur levent le verrou de leur ecran avec leur code PIN. */
  readonly avecVerrou = this.estPoste && this.auth.aRole('CAISSIER', 'SERVEUR');
  /** Sons et messages du point de vente : commandes a valider, bons, plats prets, caisse ouverte. */
  readonly notificationsPdv = this.auth.peutUtiliser('POINT_DE_VENTE')
    && this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR', 'PREPARATEUR', 'MAGASINIER');
  readonly verrou = inject(VerrouService);
  readonly pleinEcran = signal(false);

  /** Les alertes de la cloche viennent du parc : seuls ses profils les recoivent. */
  /** Gestion de parc avec Marketplace : icone des demandes de location en ligne, a cote de la cloche. */
  readonly demandesEnLigneVisibles = computed(() => {
    this.utilisateur();
    return !this.estSuperAdmin && this.auth.aModule('GESTION_PARC') && this.auth.peutUtiliser('MARKETPLACE');
  });

  readonly clocheVisible = computed(() => {
    this.utilisateur();
    return this.auth.peutUtiliser('GESTION_PARC');
  });

  /** Nom et logo parametres, affiches en tete de page. */
  readonly societe = this.societeService.identite;

  readonly utilisateur = this.auth.utilisateur;
  /** Le panneau sous les onglets peut etre replie pour gagner de la hauteur. */
  readonly panneauOuvert = signal(true);

  /** Deux lettres du nom, a defaut de photo de profil. */
  readonly initiales = computed(() =>
    (this.utilisateur()?.nomComplet ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((mot) => mot[0].toUpperCase())
      .join('')
  );

  /** Destination du logo : l'accueil du profil connecte. */
  readonly accueil = computed(() => {
    this.utilisateur();
    return this.auth.pageAccueil();
  });

  /**
   * Menu du chapitre 37 pour le module Gestion de parc, puis le socle commun
   * a tous les modules. Les onglets d'un module non active disparaissent.
   */
  private readonly menu: Onglet[] = [
    {
      id: 'accueil', module: 'GESTION_PARC', libelle: 'Accueil',
      entrees: [
        { libelle: 'Tableau de bord', route: '/tableau-bord', description: 'Chiffre d\'affaires, depenses et resultat' },
        { libelle: 'Direction', route: '/direction', description: 'Indicateurs de pilotage et decisions' },
        { libelle: 'Rentabilite', route: '/rentabilite', description: 'Marge par vehicule sur la periode' }
      ]
    },
    {
      id: 'parc', module: 'GESTION_PARC', libelle: 'Parc',
      autresRoutes: ['/camions'],
      entrees: [
        { libelle: 'Vue du parc', route: '/vue-parc', description: 'Chaque vehicule, son etat et sa marge' },
        { libelle: 'Affectations', route: '/affectations', description: 'Qui conduit quel vehicule' },
        { libelle: 'Documents', route: '/documents', description: 'Assurances, visites et echeances' },
        { libelle: 'Maintenance', route: '/maintenance', description: 'Entretiens, pannes et atelier' },
        { libelle: 'Carte du parc', route: '/gps', description: 'Positions GPS et trajets du jour' }
      ]
    },
    {
      id: 'exploitation', module: 'GESTION_PARC', libelle: 'Exploitation',
      autresRoutes: ['/missions', '/livraisons'],
      entrees: [
        { libelle: 'Chauffeurs', route: '/chauffeurs', description: 'Equipe, permis et disponibilite' },
        { libelle: 'Clients', route: '/clients', description: 'Portefeuille et contrats en cours' },
      ]
    },
    {
      id: 'finances', module: 'GESTION_PARC', libelle: 'Finances',
      entrees: [
        { libelle: 'Recettes', route: '/recettes', description: 'Locations, transports et livraisons' },
        { libelle: 'Depenses', route: '/depenses', description: 'Charges rattachees a chaque vehicule' },
        { libelle: 'Carburant', route: '/carburant', description: 'Pleins et consommation' },
        { libelle: 'Facturation', route: '/facturation', description: 'Factures clients et reglements' },
        // Gestion de parc avec Marketplace : les vehicules reserves en ligne, a valider.
        { libelle: 'Demandes en ligne', route: '/marketplace/locations', description: 'Vehicules reserves dans la boutique en ligne', module: 'MARKETPLACE', roles: ['ADMIN', 'GERANT', 'GESTIONNAIRE_PARC'] }
      ]
    },
    {
      // Le personnel de salle et de cuisine va droit a son ecran ; le gerant
      // et l'administrateur pilotent : tableau de bord, ventes, stock, caisse.
      id: 'pdv', libelle: 'Point de vente', module: 'POINT_DE_VENTE',
      autresRoutes: ['/pdv/commandes', '/pdv/salle', '/pdv/comptoir', '/pdv/caisse', '/pdv/cuisine'],
      entrees: [
        { libelle: 'Plan de salle', route: '/pdv/salle', description: 'Tables, commandes et additions', roles: ['CAISSIER', 'SERVEUR'], variante: 'RESTAURATION' },
        { libelle: 'Ecran de preparation', route: '/pdv/cuisine', description: 'Bons a preparer, en cours et prets', roles: ['PREPARATEUR'], variante: 'RESTAURATION' },
        { libelle: 'Caisse', route: '/pdv/caisse', description: 'Ouvrir la caisse et encaisser au comptoir', roles: ['CAISSIER', 'SERVEUR'], variante: ['BOUTIQUE', 'MATERIAUX'] },
        { libelle: 'Caisse', route: '/pdv/comptoir', description: 'Vente au comptoir, ouverture et fermeture de caisse', roles: ['CAISSIER', 'SERVEUR'], variante: 'RESTAURATION' },
        { libelle: 'Tableau de bord', route: '/pdv/tableau-bord', description: 'Chiffre du jour, marge et meilleures ventes', roles: ['ADMIN', 'GERANT'] },
        { libelle: 'Ventes', route: '/pdv/ventes', description: 'Commandes, reimpression et annulations', roles: ['ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR'] },
        { libelle: 'Reste a livrer', route: '/pdv/livraisons', description: 'Commandes a remettre et bons de livraison', roles: ['CAISSIER'], variante: 'MATERIAUX' },
        { libelle: 'Bons de retrait', route: '/pdv/retraits', description: 'Servir les bons presentes par les clients', roles: ['MAGASINIER'], variante: 'MATERIAUX' },
        { libelle: 'Transferts', route: '/pdv/transferts', description: 'Expedier et receptionner la marchandise', roles: ['MAGASINIER'], variante: 'MATERIAUX' },
        { libelle: 'Stock par lieu', route: '/pdv/stock-lieu', description: 'Ce qui est sur place et disponible', roles: ['MAGASINIER'], variante: 'MATERIAUX' },
        {
          // Quincaillerie et materiaux : comptes clients, credit et devis.
          libelle: 'Clients', description: 'Comptes clients, credit et devis', roles: ['ADMIN', 'GERANT'], variante: 'MATERIAUX',
          enfants: [
            { libelle: 'Clients', route: '/pdv/clients', description: 'Fiches, pieces, encours et reglements' },
            { libelle: 'Reste a livrer', route: '/pdv/livraisons', description: 'Commandes a remettre et bons de livraison' },
            { libelle: 'Devis', route: '/pdv/devis', description: 'Pro forma, validite et transformation en vente' }
          ]
        },
        {
          libelle: 'Stock', description: 'Produits, reapprovisionnement, inventaire', roles: ['ADMIN', 'GERANT'],
          enfants: [
            { libelle: 'Produits', route: '/pdv/articles', description: 'Catalogue, prix et photos' },
            { libelle: 'Categories', route: '/pdv/categories', description: 'Familles de produits de la caisse' },
            { libelle: 'Reapprovisionnement', route: '/pdv/stock', description: 'Receptions, sorties et pertes' },
            { libelle: 'Inventaire', route: '/pdv/inventaire', description: 'Compter le stock et corriger les ecarts' }
          ]
        },
        {
          libelle: 'Magasins', description: 'Magasins, entrepots, retraits et transferts', roles: ['ADMIN', 'GERANT'], variante: 'MATERIAUX',
          enfants: [
            { libelle: 'Magasins et entrepots', route: '/pdv/lieux', description: 'Lieux, personnel et valeur du stock' },
            { libelle: 'Stock par lieu', route: '/pdv/stock-lieu', description: 'Ce qui est sur place et disponible', avecLieux: true },
            { libelle: 'Bons de retrait', route: '/pdv/retraits', description: 'Bons a servir et non retires', avecLieux: true },
            { libelle: 'Transferts', route: '/pdv/transferts', description: 'Marchandise entre les lieux', avecLieux: true }
          ]
        },
        {
          libelle: 'Caisse', description: 'Caisses et vente au comptoir', roles: ['ADMIN', 'GERANT'], variante: ['BOUTIQUE', 'MATERIAUX'],
          enfants: [
            { libelle: 'Caisses', route: '/pdv/caisses', description: 'Caisses, affectations et historique' },
            { libelle: 'Vente au comptoir', route: '/pdv/caisse', description: 'Encaisser a la caisse' }
          ]
        },
        {
          libelle: 'Caisse', description: 'Caisses, cuisine, salle et codes QR', roles: ['ADMIN', 'GERANT'], variante: 'RESTAURATION',
          enfants: [
            { libelle: 'Caisses', route: '/pdv/caisses', description: 'Caisses, affectations et historique' },
            { libelle: 'Ecrans de preparation', route: '/pdv/ecrans', description: 'Ecrans, categories et personnel', variante: 'RESTAURATION' },
            { libelle: 'Salle et tables', route: '/pdv/plan-salle', description: 'Plan du restaurant et service', variante: 'RESTAURATION' },
            { libelle: 'Codes QR', route: '/pdv/codes-qr', description: 'Commande des clients depuis leur table', variante: 'RESTAURATION' }
          ]
        },
        { libelle: 'Rapports', route: '/pdv/rapports', description: 'Statistiques, exports et imports', roles: ['ADMIN', 'GERANT'] },
        { libelle: 'Reglages', route: '/pdv/reglages-materiaux', description: 'Credit, devis, facture A4 et conditionnements', roles: ['ADMIN'], variante: 'MATERIAUX' }
      ]
    },
    {
      // Seul pour une entreprise qui n'a que la Marketplace ; sinon ses ecrans rejoignent le module de base.
      id: 'marketplace', libelle: 'Marketplace', module: 'MARKETPLACE', sansModules: ['POINT_DE_VENTE', 'GESTION_PARC'],
      entrees: [
        { libelle: 'Commandes', route: '/marketplace/commandes', description: 'Commandes passees en ligne', roles: ['ADMIN', 'GERANT'] },
        { libelle: 'Demandes de location', route: '/marketplace/locations', description: 'Vehicules reserves en ligne', roles: ['ADMIN', 'GERANT'] },
        { libelle: 'Offres', route: '/marketplace/offres', description: 'Produits et vehicules publies', roles: ['ADMIN', 'GERANT'] },
        { libelle: 'Ma boutique', route: '/marketplace/boutique', description: 'Vitrine, livraison, paiement et lien', roles: ['ADMIN', 'GERANT'] }
      ]
    },
    {
      id: 'administration', libelle: 'Administration',
      entrees: [
        { libelle: 'Rapports PDF', route: '/rapports', description: 'Etats a imprimer ou a envoyer', module: 'GESTION_PARC' },
        { libelle: 'Modules', route: '/modules', description: 'Applications activees pour l\'entreprise' },
        // Point de vente + Marketplace : la boutique se regle ici ; ventes et produits en ligne sont dans le point de vente.
        { libelle: 'Ma boutique', route: '/marketplace/boutique', description: 'Boutique en ligne : vitrine, livraison, paiement et lien', module: 'MARKETPLACE', avecUnDesModules: ['POINT_DE_VENTE', 'GESTION_PARC'], roles: ['ADMIN', 'GERANT', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Utilisateurs', route: '/utilisateurs', description: 'Comptes et profils d\'acces', roles: ['ADMIN'] },
        { libelle: 'Parametrage', route: '/parametrage', description: 'Societe, logo et referentiels', roles: ['ADMIN'] }
      ]
    }
  ];

  /** L'exploitant de la plateforme n'a que sa console : il ne travaille dans aucune entreprise. */
  private readonly menuPlateforme: Onglet[] = [
    {
      id: 'plateforme', libelle: 'Plateforme',
      entrees: [
        { libelle: 'Entreprises', route: '/plateforme', description: 'Abonnes, modules et suspensions' }
      ]
    }
  ];

  /**
   * Onglets reduits aux modules actives et aux entrees que le profil a le droit
   * d'ouvrir. Calcules a partir de la session, ils suivent une activation de module.
   */
  readonly onglets = computed<Onglet[]>(() => {
    this.utilisateur();
    if (this.estSuperAdmin) {
      return this.menuPlateforme;
    }
    const lieuxActifs = this.lieux.actif();
    const varianteCourante = this.auth.variante('POINT_DE_VENTE') ?? '';
    const autorise = (e: { roles?: Role[]; module?: CodeModule; variante?: string | string[]; sansModules?: CodeModule[];
                           avecModules?: CodeModule[]; avecUnDesModules?: CodeModule[]; avecLieux?: boolean }) =>
      (!e.avecLieux || lieuxActifs) &&
      (!e.roles || this.auth.aRole(...e.roles)) &&
      (!e.sansModules || !e.sansModules.some((m) => this.auth.aModule(m))) &&
      (!e.avecModules || e.avecModules.every((m) => this.auth.aModule(m))) &&
      (!e.avecUnDesModules || e.avecUnDesModules.some((m) => this.auth.aModule(m))) &&
      (!e.module || this.auth.peutUtiliser(e.module)) &&
      (!e.variante || (Array.isArray(e.variante) ? e.variante.includes(varianteCourante) : e.variante === varianteCourante));
    const filtrer = (entrees: EntreeMenu[]): EntreeMenu[] =>
      entrees
        .filter(autorise)
        .map((e) => (e.enfants ? { ...e, enfants: e.enfants.filter(autorise) } : e))
        .filter((e) => !e.enfants || e.enfants.length > 0);
    return this.menu
      .filter(autorise)
      .map((onglet) => ({ ...onglet, entrees: filtrer(onglet.entrees) }))
      .filter((onglet) => onglet.entrees.length > 0);
  });

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evenement) => evenement instanceof NavigationEnd),
      map((evenement) => evenement.urlAfterRedirects)
    ),
    { initialValue: this.router.url }
  );

  private correspond(route: string | undefined): boolean {
    const url = this.url();
    return !!route && (url === route || url.startsWith(route + '/') || url.startsWith(route + '?'));
  }

  /** Onglet de la page affichee. */
  private readonly ongletDeLaPage = computed(() => {
    this.url();
    return this.onglets().find((onglet) =>
      [
        ...onglet.entrees.flatMap((entree) => [entree.route, ...(entree.enfants ?? []).map((e) => e.route)]),
        ...(onglet.autresRoutes ?? [])
      ].some((route) => this.correspond(route))
    ) ?? this.onglets()[0];
  });

  /** Groupe deplie au clic ; une navigation le remet sur celui de la page. */
  private readonly groupeChoisi = signal<string | null>(null);

  /** Groupe dont les sous-menus s'affichent : celui choisi, sinon celui de la page ouverte. */
  readonly groupeOuvert = computed<EntreeMenu | null>(() => {
    const groupes = this.ongletOuvert().entrees.filter((e) => e.enfants);
    return groupes.find((g) => g.libelle === this.groupeChoisi())
      ?? groupes.find((g) => this.groupeActif(g))
      ?? null;
  });

  /** Un groupe est actif quand la page ouverte est l'un de ses sous-menus. */
  groupeActif(groupe: EntreeMenu): boolean {
    return (groupe.enfants ?? []).some((e) => this.correspond(e.route));
  }

  basculerGroupe(groupe: EntreeMenu): void {
    this.groupeChoisi.set(this.groupeOuvert()?.libelle === groupe.libelle ? '' : groupe.libelle);
  }

  /** Onglet choisi au clic ; une navigation le remet sur celui de la page. */
  private readonly ongletChoisi = signal<string | null>(null);

  readonly ongletOuvert = computed(() =>
    this.onglets().find((onglet) => onglet.id === this.ongletChoisi()) ?? this.ongletDeLaPage()
  );

  private readonly contenu = viewChild.required<ElementRef<HTMLElement>>('contenu');

  /** Intitule de l'ecran du poste, dans la barre fine. */
  readonly ecranPoste = computed(() => {
    const url = this.url();
    if (url.startsWith('/pdv/cuisine')) {
      return 'Ecran de preparation';
    }
    if (url.startsWith('/pdv/salle')) {
      return 'Plan de salle';
    }
    if (url.startsWith('/pdv/commandes')) {
      return 'Commande';
    }
    return 'Caisse';
  });

  constructor() {
    // Tableaux de tous les ecrans : les lignes defilent dans la hauteur restante de la fenetre.
    const destroyRef = inject(DestroyRef);
    // Les ecrans de retrait et de transfert n'apparaissent qu'avec un entrepot.
    if (this.auth.estMateriaux() && this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER', 'MAGASINIER')) {
      this.lieux.chargerEtat();
    }
    if (this.avecVerrou) {
      this.surveillerInactivite(destroyRef);
    }
    const suivrePleinEcran = () => this.pleinEcran.set(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', suivrePleinEcran);
    destroyRef.onDestroy(() => document.removeEventListener('fullscreenchange', suivrePleinEcran));
    afterNextRender(() => activerDefilementTableaux(this.contenu().nativeElement, destroyRef));
    // Rechargee a chaque ouverture : la session precedente du navigateur
    // pouvait appartenir a une autre entreprise.
    this.societeService.identite.set(null);
    if (!this.estSuperAdmin) {
      this.societeService.chargerIdentite().subscribe({ error: () => undefined });
    }
    this.router.events
      .pipe(filter((evenement) => evenement instanceof NavigationEnd))
      .subscribe(() => {
        this.ongletChoisi.set(null);
        this.groupeChoisi.set(null);
      });
  }

  /** Un clic ouvre le panneau de l'onglet ; un second clic sur le meme le replie. */
  ouvrirOnglet(onglet: Onglet): void {
    if (onglet.id === this.ongletOuvert().id && this.panneauOuvert()) {
      this.panneauOuvert.set(false);
      return;
    }
    this.ongletChoisi.set(onglet.id);
    this.groupeChoisi.set(null);
    this.panneauOuvert.set(true);
  }

  /** L'onglet de la page reste marque, meme quand on en consulte un autre. */
  estOngletDeLaPage(onglet: Onglet): boolean {
    return onglet.id === this.ongletDeLaPage().id;
  }

  basculerPanneau(): void {
    this.panneauOuvert.update((ouvert) => !ouvert);
  }

  /** Menu du compte, ouvert par le nom ou l'avatar : « Mon compte » et deconnexion. */
  readonly menuCompte = signal(false);
  readonly compteOuvert = signal(false);

  basculerMenuCompte(): void {
    this.menuCompte.update((ouvert) => !ouvert);
  }

  ouvrirCompte(): void {
    this.menuCompte.set(false);
    this.compteOuvert.set(true);
  }

  /** Un clic ailleurs referme le menu du compte. */
  clicDocument(evenement: MouseEvent): void {
    if (this.menuCompte() && !(evenement.target as HTMLElement)?.closest?.('.zone-compte')) {
      this.menuCompte.set(false);
    }
  }

  deconnexion(): void {
    this.menuCompte.set(false);
    this.verrou.verrouiller();
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    }
    this.auth.logout();
  }

  /** Plein ecran du navigateur : il ne s'obtient que d'un geste de l'utilisateur. */
  basculerPleinEcran(): void {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      void document.documentElement.requestFullscreen?.().catch(() => undefined);
    }
  }

  /** Sans geste pendant quelques minutes, la caisse se verrouille d'elle-meme. */
  private surveillerInactivite(destroyRef: DestroyRef): void {
    let minuterie = 0;
    const relancer = () => {
      clearTimeout(minuterie);
      minuterie = window.setTimeout(() => this.verrou.verrouiller(), MINUTES_INACTIVITE * 60_000);
    };
    const evenements = ['pointerdown', 'keydown', 'touchstart'];
    evenements.forEach((e) => document.addEventListener(e, relancer, { passive: true }));
    relancer();
    destroyRef.onDestroy(() => {
      clearTimeout(minuterie);
      evenements.forEach((e) => document.removeEventListener(e, relancer));
    });
  }
}
