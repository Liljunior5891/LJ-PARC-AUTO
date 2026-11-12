import { Component, DestroyRef, ElementRef, afterNextRender, computed, effect, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { SocieteService } from '../core/services/societe.service';
import { ClocheNotifications } from '../shared/cloche-notifications';
import { IconeDemandesEnLigne } from '../shared/icone-demandes-en-ligne';
import { VerrouService } from '../core/services/verrou.service';
import { AgencesService } from '../core/services/agences.service';
import { VerrouCaisse } from './verrou-caisse';
import { EtatHorsLigneAgences } from './etat-hors-ligne-agences';
import { BonsAImprimer } from './bons-a-imprimer';
import { SansCaisse } from './sans-caisse';
import { PdvService } from '../core/services/pdv.service';
import { NotificationsPdv } from './notifications-pdv';
import { AlertesPdv } from './alertes-pdv';
import { PanneauCompte } from './panneau-compte';
import { activerDefilementTableaux } from '../shared/defilement-tableaux';
import { CodeModule, Role } from '../core/models/models';
import { LieuxService } from '../core/services/lieux.service';
import { OptionsPdvService } from '../core/services/options-pdv.service';
import { environment } from '../../environments/environment';
import { BandeauAbonnement } from '../shared/bandeau-abonnement';

interface EntreeMenu {
  libelle: string;
  /** Page ouverte ; absente pour un groupe, qui deplie ses sous-menus. */
  route?: string;
  /** Ligne d'explication affichee sous le libelle, dans le panneau de l'onglet. */
  description: string;
  /** Visible seulement quand l'entreprise a un entrepot. */
  avecLieux?: boolean;
  /** Option du point de vente a activer dans ses reglages pour voir l'entree. */
  option?: 'associes' | 'circuit' | 'validation';
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ClocheNotifications, EtatHorsLigneAgences, IconeDemandesEnLigne, VerrouCaisse, SansCaisse, NotificationsPdv, AlertesPdv, PanneauCompte, BonsAImprimer, BandeauAbonnement],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);
  private readonly router = inject(Router);
  private readonly lieux = inject(LieuxService);
  private readonly optionsPdv = inject(OptionsPdvService);

  readonly nomPlateforme = environment.nomPlateforme;
  readonly estSuperAdmin = this.auth.estSuperAdmin();

  /**
   * Poste de travail (caissier, serveur, cuisinier) : son seul ecran, sous une
   * barre fine, sans le menu de l'application.
   */
  readonly estPoste = this.auth.estPoste();
  /** Le caissier et le serveur levent le verrou de leur ecran avec leur code PIN. */
  readonly avecVerrou = this.estPoste && this.auth.aRole('CAISSIER', 'SERVEUR');
  /** Le caissier d'agence aussi : verrou apres quelques minutes sans geste, leve par son code PIN. */
  /** Pastille du travail hors ligne des agences (caissiers et superviseurs). */
  readonly agencesHorsLigne = !this.estPoste && this.auth.peutUtiliser('AGENCES')
    && this.auth.aRole('CAISSIER_AGENCE', 'SUPERVISEUR_AGENCE', 'ADMIN');
  readonly verrouAgence = !this.estPoste && this.auth.peutUtiliser('AGENCES') && this.auth.aRole('CAISSIER_AGENCE')
    && !this.auth.aRole('ADMIN', 'SUPERVISEUR_AGENCE');
  /** Sons et messages du point de vente : commandes a valider, bons, plats prets, caisse ouverte. */
  readonly notificationsPdv = this.auth.peutUtiliser('POINT_DE_VENTE')
    && this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR', 'PREPARATEUR', 'MAGASINIER');
  readonly verrou = inject(VerrouService);
  readonly pleinEcran = signal(false);

  /** Poste de caisse sans caisse affectee : un ecran plein remplace celui de la caisse. */
  private readonly posteCaissier = this.estPoste && this.auth.aRole('CAISSIER');
  private readonly pdv = inject(PdvService);
  readonly sansCaisse = signal(false);
  readonly verificationCaisse = signal(false);

  verifierCaisse(): void {
    if (!this.posteCaissier || this.verificationCaisse()) {
      return;
    }
    this.verificationCaisse.set(true);
    this.pdv.caissesDisponibles().subscribe({
      next: (caisses) => {
        this.verificationCaisse.set(false);
        this.sansCaisse.set(caisses.length === 0);
      },
      error: () => this.verificationCaisse.set(false)
    });
  }

  /** Les alertes de la cloche viennent du parc : seuls ses profils les recoivent. */
  /** Gestion de parc avec Marketplace : icone des demandes de location en ligne, a cote de la cloche. */
  readonly demandesEnLigneVisibles = computed(() => {
    this.utilisateur();
    return !this.estSuperAdmin && this.auth.aModule('GESTION_PARC') && this.auth.peutUtiliser('MARKETPLACE');
  });

  readonly clocheVisible = computed(() => {
    this.utilisateur();
    return this.auth.aModule('GESTION_PARC') && this.auth.aRole('ADMIN', 'GESTIONNAIRE_PARC');
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
        { libelle: 'Tableau de bord', route: '/tableau-bord', description: 'Chiffre d\'affaires, depenses et resultat', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Direction', route: '/direction', description: 'Indicateurs de pilotage et decisions', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Rentabilite', route: '/rentabilite', description: 'Marge par vehicule sur la periode', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] }
      ]
    },
    {
      id: 'parc', module: 'GESTION_PARC', libelle: 'Parc',
      autresRoutes: ['/camions'],
      entrees: [
        { libelle: 'Vue du parc', route: '/vue-parc', description: 'Chaque vehicule, son etat et sa marge', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Affectations', route: '/affectations', description: 'Qui conduit quel vehicule', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Documents', route: '/documents', description: 'Assurances, visites et echeances', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Maintenance', route: '/maintenance', description: 'Entretiens, pannes et atelier', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Carte du parc', route: '/gps', description: 'Positions GPS et trajets du jour' }
      ]
    },
    {
      id: 'exploitation', module: 'GESTION_PARC', libelle: 'Exploitation',
      autresRoutes: ['/missions', '/livraisons'],
      entrees: [
        { libelle: 'Mes missions', route: '/missions', description: 'Missions confiees, demarrage et cloture', roles: ['CHAUFFEUR'] },
        { libelle: 'Chauffeurs', route: '/chauffeurs', description: 'Equipe, permis et disponibilite', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Clients', route: '/clients', description: 'Portefeuille et contrats en cours', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
      ]
    },
    {
      id: 'finances', module: 'GESTION_PARC', libelle: 'Finances',
      entrees: [
        { libelle: 'Recettes', route: '/recettes', description: 'Locations, transports et livraisons', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Depenses', route: '/depenses', description: 'Vehicules, charges generales, recurrentes', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Fournisseurs', route: '/fournisseurs-parc', description: 'Fiches, dettes et reglements', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Facturation', route: '/facturation', description: 'Factures clients et reglements', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
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
          libelle: 'Stock', description: 'Produits, categories, inventaire, magasins', roles: ['ADMIN', 'GERANT'],
          enfants: [
            { libelle: 'Produits', route: '/pdv/articles', description: 'Catalogue, prix et photos' },
            { libelle: 'Categories', route: '/pdv/categories', description: 'Familles de produits de la caisse' },
            { libelle: 'Inventaire', route: '/pdv/inventaire', description: 'Compter le stock et corriger les ecarts' },
            // Quincaillerie : magasins, entrepots, retraits et transferts (sans entrepot, seul le premier apparait).
            { libelle: 'Magasins et entrepots', route: '/pdv/lieux', description: 'Lieux, personnel et valeur du stock', variante: 'MATERIAUX' },
            { libelle: 'Stock par lieu', route: '/pdv/stock-lieu', description: 'Ce qui est sur place et disponible', variante: 'MATERIAUX', avecLieux: true },
            { libelle: 'Bons de retrait', route: '/pdv/retraits', description: 'Bons a servir et non retires', variante: 'MATERIAUX', avecLieux: true },
            { libelle: 'Transferts', route: '/pdv/transferts', description: 'Marchandise entre les lieux', variante: 'MATERIAUX', avecLieux: true }
          ]
        },
        {
          // Toutes les variantes : fournisseurs et leurs dettes, depenses et benefice net.
          libelle: 'Achats et depenses', description: 'Fournisseurs, commandes et depenses', roles: ['ADMIN', 'GERANT'],
          enfants: [
            { libelle: 'Fournisseurs', route: '/pdv/fournisseurs', description: 'Fiches, dettes, echeances et paiements' },
            { libelle: 'Reapprovisionnement', route: '/pdv/stock', description: 'Commandes, receptions, sorties et pertes' },
            { libelle: 'Depenses', route: '/pdv/depenses', description: 'Salaires, loyer, electricite, recurrentes' },
            { libelle: 'Depenses de caisse a valider', route: '/pdv/validations-caisse', description: 'Visa du gerant, puis approbation par l\'administrateur', option: 'validation' }
          ]
        },
        {
          libelle: 'Caisse', description: 'Caisses et vente au comptoir', roles: ['ADMIN', 'GERANT'], variante: ['BOUTIQUE', 'MATERIAUX'],
          enfants: [
            { libelle: 'Caisses', route: '/pdv/caisses', description: 'Caisses, affectations et historique' },
            { libelle: 'Remises et coffre', route: '/pdv/tresorerie', description: 'Recettes des caissiers, coffre, compte de l\'entreprise', option: 'circuit' },
            { libelle: 'Vente au comptoir', route: '/pdv/caisse', description: 'Encaisser a la caisse' }
          ]
        },
        {
          libelle: 'Caisse', description: 'Caisses, cuisine, salle et codes QR', roles: ['ADMIN', 'GERANT'], variante: 'RESTAURATION',
          enfants: [
            { libelle: 'Caisses', route: '/pdv/caisses', description: 'Caisses, affectations et historique' },
            { libelle: 'Remises et coffre', route: '/pdv/tresorerie', description: 'Recettes des caissiers, coffre, compte de l\'entreprise', option: 'circuit' },
            { libelle: 'Postes et imprimantes', route: '/pdv/ecrans', description: 'Ecrans, imprimantes de cuisine, categories et personnel', variante: 'RESTAURATION' },
            { libelle: 'Salle et tables', route: '/pdv/plan-salle', description: 'Plan du restaurant et service', variante: 'RESTAURATION' },
            { libelle: 'Codes QR', route: '/pdv/codes-qr', description: 'Commande des clients depuis leur table', variante: 'RESTAURATION' }
          ]
        },
        { libelle: 'Benefices associes', route: '/pdv/benefices', description: 'Part de chaque associe sur la marge de ses produits', roles: ['ADMIN', 'ASSOCIE'], option: 'associes' },
        { libelle: 'Rapports', route: '/pdv/rapports', description: 'Statistiques, exports et imports', roles: ['ADMIN', 'GERANT'] },
        { libelle: 'Reglages', route: '/pdv/reglages', description: 'Options et reglages du point de vente', roles: ['ADMIN'] }
      ]
    },
    {
      // Gestion de presence : pointage des employes par code QR et PIN.
      id: 'presence', libelle: 'Presence', module: 'PRESENCE',
      entrees: [
        { libelle: 'Presences du jour', route: '/presence/tableau-bord', description: 'Arrivees, retards et absents en direct' },
        { libelle: 'Employes', route: '/presence/employes', description: 'Fiches, PIN et historique de chaque employe' },
        { libelle: 'Lieux et bornes', route: '/presence/lieux', description: 'Lieux de travail, position GPS, bornes et codes QR' },
        { libelle: 'Horaire', route: '/presence/reglages', description: 'Heure d\'arrivee, tolerance, jours travailles', roles: ['ADMIN'] }
      ]
    },
    {
      // Agences multiservices : soldes centralises, agences, catalogue et reglages.
      id: 'agences', libelle: 'Agences', module: 'AGENCES',
      entrees: [
        { libelle: 'Tableau de bord', route: '/agences/tableau-bord', description: 'Tresorerie, commissions et activite de l\'agence' },
        { libelle: 'Transactions', route: '/agences/transactions', description: 'Liste, recherche, saisie et annulations' },
        {
          libelle: 'Agence', description: 'Agences, caisses des caissiers et cloture', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'],
          enfants: [
            { libelle: 'Agences', route: '/agences/agences', description: 'Agences, adresses et personnel rattache', roles: ['ADMIN'] },
            { libelle: 'Caisses des caissiers', route: '/agences/caisses', description: 'Ouverture, fermeture et billettage', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] },
            { libelle: 'Cloture de caisse', route: '/agences/cloture', description: 'Soldes reels, ecarts et verrouillage du jour' },
            { libelle: 'Operations hors ligne', route: '/agences/hors-ligne', description: 'Operations refusees a l’envoi, a reprendre', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] }
          ]
        },
        {
          libelle: 'Finance', description: 'Mouvements, credits, tresorerie et rapprochement', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'],
          enfants: [
            { libelle: 'Mouvements', route: '/agences/mouvements', description: 'Approvisionnements, banque, regularisations', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] },
            { libelle: 'Credits et dettes', route: '/agences/credits', description: 'Credits, apports, avances et remboursements', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] },
            { libelle: 'Soldes et tresorerie', route: '/agences/soldes', description: 'Cash, floats, banque et registre des mouvements', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] },
            { libelle: 'Rapprochement', route: '/agences/rapprochement', description: 'Commissions en attente et versements des operateurs', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] }
          ]
        },
        { libelle: 'Cartes prepayees', route: '/agences/cartes', description: 'Vendre, recharger, stock et rentabilite' },
        { libelle: 'Depenses', route: '/agences/depenses', description: 'Charges, suivi mensuel et charges recurrentes' },
        { libelle: 'Rapports', route: '/agences/rapports', description: 'Journalier a annuel, exports Excel, PDF, CSV', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] },
        {
          libelle: 'Reglages', description: 'Baremes, services et droits des profils', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'],
          enfants: [
            { libelle: 'Baremes de commission', route: '/agences/baremes', description: 'Fixe, pourcentage ou tranches, simulateur', roles: ['ADMIN', 'SUPERVISEUR_AGENCE'] },
            { libelle: 'Services et commissions', route: '/agences/services', description: 'Operateurs, reseaux et regles de mouvement', roles: ['ADMIN'] },
            { libelle: 'Reglage des droits', route: '/agences/reglages', description: 'Journee de caisse, plafonds, droits des profils', roles: ['ADMIN'] }
          ]
        }
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
        { libelle: 'Rapports PDF', route: '/rapports', description: 'Etats a imprimer ou a envoyer', module: 'GESTION_PARC', roles: ['ADMIN', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Modules', route: '/modules', description: 'Applications activees pour l\'entreprise', roles: ['ADMIN'] },
        // Point de vente + Marketplace : la boutique se regle ici ; ventes et produits en ligne sont dans le point de vente.
        { libelle: 'Ma boutique', route: '/marketplace/boutique', description: 'Boutique en ligne : vitrine, livraison, paiement et lien', module: 'MARKETPLACE', avecUnDesModules: ['POINT_DE_VENTE', 'GESTION_PARC'], roles: ['ADMIN', 'GERANT', 'GESTIONNAIRE_PARC'] },
        { libelle: 'Utilisateurs', route: '/utilisateurs', description: 'Comptes et profils d\'acces', roles: ['ADMIN'] },
        { libelle: 'Parametrage', route: '/parametrage', description: 'Societe, logo et referentiels', roles: ['ADMIN'] },
        { libelle: 'Mon abonnement', route: '/abonnement', description: 'Formule, echeance, paiements et factures', roles: ['ADMIN'] },
        { libelle: 'Journal d\'audit', route: '/journal-audit', description: 'Toute l\'entreprise : donnees et connexions', roles: ['ADMIN'] }
      ]
    }
  ];

  /** L'exploitant de la plateforme n'a que sa console : il ne travaille dans aucune entreprise. */
  private readonly menuPlateforme: Onglet[] = [
    {
      id: 'plateforme-bord', libelle: 'Tableau de bord',
      entrees: [
        { libelle: 'Vue d\'ensemble', route: '/plateforme/tableau-bord', description: 'Croissance, revenus, usage et echeances' }
      ]
    },
    {
      id: 'plateforme-entreprises', libelle: 'Entreprises',
      entrees: [
        { libelle: 'Entreprises', route: '/plateforme/entreprises', description: 'Fiches, creation, abonnement, modules et suspension' }
      ]
    },
    {
      id: 'plateforme-abonnements', libelle: 'Abonnements',
      entrees: [
        { libelle: 'Paiements', route: '/plateforme/abonnements/paiements', description: 'Paiements declares a valider, factures' },
        { libelle: 'Echeances', route: '/plateforme/abonnements/echeances', description: 'Etat et echeance de chaque entreprise' },
        { libelle: 'Formules', route: '/plateforme/abonnements/formules', description: 'Prix, modules compris et limites' },
        { libelle: 'Reglages', route: '/plateforme/abonnements/reglages', description: 'Essai, grace, remises et facture' }
      ]
    },
    {
      id: 'plateforme-journal', libelle: "Journal d'audit",
      entrees: [
        { libelle: 'Journal general', route: '/plateforme/journal', description: 'Actions de toutes les entreprises et de la plateforme' },
        { libelle: 'Notifications', route: '/plateforme/notifications', description: 'E-mails, SMS et WhatsApp envoyes (ou simules)' }
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
    const optionsActives = { associes: this.optionsPdv.associesActif(), circuit: this.optionsPdv.circuitActif(),
      validation: this.optionsPdv.validationDepensesActive() };
    const varianteCourante = this.auth.variante('POINT_DE_VENTE') ?? '';
    const autorise = (e: { roles?: Role[]; module?: CodeModule; variante?: string | string[]; sansModules?: CodeModule[];
                           avecModules?: CodeModule[]; avecUnDesModules?: CodeModule[]; avecLieux?: boolean;
                           option?: 'associes' | 'circuit' | 'validation' }) =>
      (!e.avecLieux || lieuxActifs) &&
      (!e.option || optionsActives[e.option]) &&
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
    // Profils et modules relus au serveur : a l'ouverture, toutes les 5 minutes et au retour sur l'onglet.
    this.auth.rafraichirProfil();
    const relire = () => document.visibilityState === 'visible' && this.auth.rafraichirProfil();
    const minuterie = setInterval(() => this.auth.rafraichirProfil(), 5 * 60_000);
    document.addEventListener('visibilitychange', relire);
    destroyRef.onDestroy(() => {
      clearInterval(minuterie);
      document.removeEventListener('visibilitychange', relire);
    });
    // Mot de passe provisoire : « Mon compte » s'ouvre tant qu'il n'est pas change.
    effect(() => {
      if (this.auth.utilisateur()?.motDePasseAChanger || this.auth.utilisateur()?.deuxFacteursAActiver) {
        this.compteOuvert.set(true);
      }
    });
    // Les ecrans de retrait et de transfert n'apparaissent qu'avec un entrepot.
    if (this.auth.estMateriaux() && this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER', 'MAGASINIER')) {
      this.lieux.chargerEtat();
    }
    // Options du point de vente (associes, circuit des especes) : elles montrent ou cachent des ecrans.
    if (this.auth.aModule('POINT_DE_VENTE') && this.auth.aRole('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR', 'ASSOCIE')) {
      this.optionsPdv.charger();
    }
    if (this.avecVerrou || this.verrouAgence) {
      this.lireDelaiVerrou();
      this.surveillerInactivite(destroyRef);
    }
    if (this.posteCaissier) {
      this.verifierCaisse();
      // Le gerant peut affecter la caisse pendant que le caissier attend devant l'ecran.
      const relecture = window.setInterval(() => this.sansCaisse() && this.verifierCaisse(), 20_000);
      destroyRef.onDestroy(() => clearInterval(relecture));
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

  /** Delai de verrouillage regle par l'administrateur : reglages des agences pour le caissier d'agence, du point de vente sinon. */
  private lireDelaiVerrou(): void {
    const appliquer = (m?: number | null) => {
      if (m && m > 0) {
        this.verrou.minutes.set(m);
      }
    };
    if (this.verrouAgence) {
      inject(AgencesService).reglages().subscribe({ next: (r) => appliquer(r.minutesVerrouCaisse) });
    } else {
      // Les reglages du point de vente sont deja lus au demarrage (options).
      effect(() => appliquer(this.optionsPdv.reglages()?.minutesVerrouCaisse));
    }
  }

  /** Sans geste pendant le delai regle, la caisse se verrouille d'elle-meme. */
  private surveillerInactivite(destroyRef: DestroyRef): void {
    let minuterie = 0;
    const relancer = () => {
      this.verrou.signalerActivite();
      clearTimeout(minuterie);
      minuterie = window.setTimeout(() => this.verrou.verrouiller(), this.verrou.minutes() * 60_000);
    };
    // Un nouveau delai s'applique tout de suite.
    effect(() => {
      this.verrou.minutes();
      relancer();
    });
    const evenements = ['pointerdown', 'keydown', 'touchstart'];
    evenements.forEach((e) => document.addEventListener(e, relancer, { passive: true }));
    relancer();
    destroyRef.onDestroy(() => {
      clearTimeout(minuterie);
      evenements.forEach((e) => document.removeEventListener(e, relancer));
    });
  }
}
