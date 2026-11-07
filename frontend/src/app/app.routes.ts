import { Routes } from '@angular/router';
import {
  authGuard,
  caisseBoutiqueGuard,
  entrepriseGuard,
  moduleGuard,
  posteGuard,
  roleGuard,
  varianteGuard
} from './core/auth/auth.guard';

export const routes: Routes = [
  // Presentation de la plateforme, ouverte aux visiteurs, servie a la racine.
  {
    path: '',
    pathMatch: 'full',
    title: 'LJ SaaS — Logiciels de gestion pour entreprises',
    loadComponent: () => import('./pages/accueil/accueil').then((m) => m.Accueil)
  },
  {
    path: 'inscription',
    loadComponent: () => import('./pages/inscription/inscription').then((m) => m.Inscription),
    title: 'Creer un compte entreprise — LJ SaaS'
  },
  {
    // Carte en ligne ouverte par le code QR d'une table : sans compte, sans menu.
    path: 't/:jeton',
    loadComponent: () => import('./pages/carte-en-ligne/carte-en-ligne').then((m) => m.CarteEnLignePage),
    title: 'La carte'
  },
  // Marketplace : place de marche, boutiques et suivi de commande, sans compte ni menu.
  {
    path: 'marche',
    loadComponent: () => import('./pages/boutique-publique/marche').then((m) => m.MarchePage),
    title: 'Place de marche — LJ SaaS'
  },
  {
    path: 'boutique/:slug',
    loadComponent: () => import('./pages/boutique-publique/boutique-publique').then((m) => m.BoutiquePublique),
    title: 'Boutique en ligne'
  },
  {
    path: 'suivi/:jeton',
    loadComponent: () => import('./pages/boutique-publique/suivi-commande').then((m) => m.SuiviCommande),
    title: 'Suivi de commande'
  },
  {
    path: 'connexion',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Connexion — LJ SaaS'
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    canActivateChild: [posteGuard],
    children: [
      // --- Module Gestion de parc : reserve aux entreprises qui l'ont active ---
      {
        path: '',
        canActivate: [moduleGuard('GESTION_PARC')],
        children: [
          {
            path: 'tableau-bord',
            loadComponent: () => import('./pages/tableau-bord/tableau-bord').then((m) => m.TableauBord),
            title: 'Tableau de bord — LJ SaaS'
          },
          {
            path: 'vue-parc',
            loadComponent: () => import('./pages/vue-parc/vue-parc').then((m) => m.VueParc),
            title: 'Vue globale du parc — LJ SaaS'
          },

          // --- Parc automobile -------------------------------------------------
          {
            // La liste des camions est fondue dans la vue du parc : l'ancienne adresse y mene.
            path: 'camions',
            pathMatch: 'full',
            redirectTo: 'vue-parc'
          },
          {
            path: 'camions/nouveau',
            loadComponent: () => import('./pages/camions/camion-fiche').then((m) => m.CamionFiche),
            title: 'Nouveau vehicule — LJ SaaS'
          },
          {
            path: 'camions/:id',
            loadComponent: () => import('./pages/camions/camion-fiche').then((m) => m.CamionFiche),
            title: 'Fiche vehicule — LJ SaaS'
          },
          {
            path: 'chauffeurs',
            loadComponent: () => import('./pages/chauffeurs/chauffeurs-liste').then((m) => m.ChauffeursListe),
            title: 'Chauffeurs — LJ SaaS'
          },
          {
            path: 'chauffeurs/nouveau',
            loadComponent: () => import('./pages/chauffeurs/chauffeur-fiche').then((m) => m.ChauffeurFiche),
            title: 'Nouveau chauffeur — LJ SaaS'
          },
          {
            path: 'chauffeurs/:id',
            loadComponent: () => import('./pages/chauffeurs/chauffeur-fiche').then((m) => m.ChauffeurFiche),
            title: 'Fiche chauffeur — LJ SaaS'
          },
          {
            path: 'affectations',
            loadComponent: () => import('./pages/affectations/affectations').then((m) => m.Affectations),
            title: 'Affectations — LJ SaaS'
          },
          {
            path: 'documents',
            loadComponent: () => import('./pages/documents/documents').then((m) => m.Documents),
            title: 'Documents — LJ SaaS'
          },

          // --- Exploitation ----------------------------------------------------
          {
            path: 'clients',
            loadComponent: () => import('./pages/clients/clients-liste').then((m) => m.ClientsListe),
            title: 'Clients — LJ SaaS'
          },
          {
            path: 'clients/nouveau',
            loadComponent: () => import('./pages/clients/client-fiche').then((m) => m.ClientFiche),
            title: 'Nouveau client — LJ SaaS'
          },
          {
            path: 'clients/:id',
            loadComponent: () => import('./pages/clients/client-fiche').then((m) => m.ClientFiche),
            title: 'Fiche client — LJ SaaS'
          },
          {
            path: 'locations',
            redirectTo: '/recettes',
            title: 'Contrats de location — LJ SaaS'
          },
          {
            path: 'locations/nouveau',
            redirectTo: '/recettes',
            title: 'Nouveau contrat — LJ SaaS'
          },
          {
            path: 'locations/:id',
            redirectTo: '/recettes',
            title: 'Contrat de location — LJ SaaS'
          },
          {
            path: 'missions',
            loadComponent: () => import('./pages/missions/missions-liste').then((m) => m.MissionsListe),
            title: 'Missions — LJ SaaS'
          },
          {
            path: 'missions/nouvelle',
            loadComponent: () => import('./pages/missions/mission-fiche').then((m) => m.MissionFiche),
            title: 'Nouvelle mission — LJ SaaS'
          },
          {
            path: 'missions/:id',
            loadComponent: () => import('./pages/missions/mission-fiche').then((m) => m.MissionFiche),
            title: 'Mission — LJ SaaS'
          },
          {
            path: 'recettes',
            loadComponent: () => import('./pages/recettes/recettes').then((m) => m.Recettes),
            title: 'Recettes — LJ SaaS'
          },
          {
            path: 'depenses',
            loadComponent: () => import('./pages/depenses/depenses').then((m) => m.Depenses),
            title: 'Depenses — LJ SaaS'
          },
          {
            path: 'carburant',
            loadComponent: () => import('./pages/carburant/carburant').then((m) => m.Carburant),
            title: 'Carburant — LJ SaaS'
          },
          {
            path: 'facturation',
            loadComponent: () => import('./pages/factures/factures').then((m) => m.Factures),
            title: 'Facturation — LJ SaaS'
          },
          {
            path: 'rentabilite',
            loadComponent: () => import('./pages/rentabilite/rentabilite').then((m) => m.Rentabilite),
            title: 'Rentabilite — LJ SaaS'
          },
          {
            path: 'gps',
            loadComponent: () => import('./pages/carte-parc/carte-parc').then((m) => m.CarteParc),
            title: 'Carte du parc — LJ SaaS'
          },
          {
            path: 'maintenance',
            loadComponent: () => import('./pages/maintenance/maintenance').then((m) => m.Maintenance),
            title: 'Maintenance — LJ SaaS'
          },
          {
            path: 'direction',
            loadComponent: () => import('./pages/direction/direction').then((m) => m.Direction),
            title: 'Pilotage — LJ SaaS'
          },
          {
            path: 'rapports',
            loadComponent: () => import('./pages/rapports/rapports').then((m) => m.Rapports),
            title: 'Rapports — LJ SaaS'
          },
          {
            path: 'livraisons',
            loadComponent: () => import('./pages/livraisons/livraisons').then((m) => m.Livraisons),
            title: 'Livraisons — LJ SaaS'
          }
        ]
      },

      // --- Module Point de vente --------------------------------------------
      {
        path: 'pdv',
        canActivate: [moduleGuard('POINT_DE_VENTE')],
        children: [
          {
            // En restauration, la caisse est celle du comptoir, au nouveau format.
            path: 'caisse',
            canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR'), caisseBoutiqueGuard],
            loadComponent: () => import('./pages/pdv/caisse/caisse').then((m) => m.Caisse),
            title: 'Caisse — LJ SaaS'
          },
          {
            // Magasins et entrepots : quincaillerie uniquement, le serveur le verifie aussi.
            path: '',
            canActivate: [varianteGuard('POINT_DE_VENTE', 'MATERIAUX')],
            children: [
              {
                path: 'lieux',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/lieux/lieux').then((m) => m.LieuxPdv),
                title: 'Magasins et entrepots — LJ SaaS'
              },
              {
                path: 'stock-lieu',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'MAGASINIER')],
                loadComponent: () => import('./pages/pdv/stock-lieu/stock-lieu').then((m) => m.StockLieuPdv),
                title: 'Stock par lieu — LJ SaaS'
              },
              {
                path: 'retraits',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'MAGASINIER')],
                loadComponent: () => import('./pages/pdv/retraits/retraits').then((m) => m.RetraitsPdv),
                title: 'Bons de retrait — LJ SaaS'
              },
              {
                path: 'transferts',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'MAGASINIER')],
                loadComponent: () => import('./pages/pdv/transferts/transferts').then((m) => m.TransfertsPdv),
                title: 'Transferts — LJ SaaS'
              }
            ]
          },
          {
            // Benefices des associes : l'administrateur voit tout, un associe sa seule part.
            path: 'benefices',
            canActivate: [roleGuard('ADMIN', 'ASSOCIE')],
            loadComponent: () => import('./pages/pdv/benefices/benefices').then((m) => m.BeneficesPdv),
            title: 'Benefices des associes — LJ SaaS'
          },
          {
            path: 'reglages-materiaux',
            pathMatch: 'full',
            redirectTo: 'reglages'
          },
          {
            path: 'ventes',
            canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR')],
            loadComponent: () => import('./pages/pdv/ventes/ventes').then((m) => m.VentesPdv),
            title: 'Ventes — LJ SaaS'
          },
          {
            // Bar, restaurant, cafe : service en salle et ecrans de preparation.
            path: '',
            canActivate: [varianteGuard('POINT_DE_VENTE', 'RESTAURATION')],
            children: [
              {
                path: 'salle',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR')],
                loadComponent: () => import('./pages/pdv/salle/salle').then((m) => m.SallePdv),
                title: 'Plan de salle — LJ SaaS'
              },
              {
                path: 'comptoir',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR')],
                loadComponent: () => import('./pages/pdv/commande/commande').then((m) => m.CommandePdv),
                title: 'Caisse — LJ SaaS'
              },
              {
                path: 'commandes/:id',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR')],
                loadComponent: () => import('./pages/pdv/commande/commande').then((m) => m.CommandePdv),
                title: 'Commande — LJ SaaS'
              },
              {
                path: 'cuisine',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'PREPARATEUR')],
                loadComponent: () => import('./pages/pdv/cuisine/cuisine').then((m) => m.CuisinePdv),
                title: 'Cuisine et bar — LJ SaaS'
              },
              {
                path: 'ecrans',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/ecrans/ecrans').then((m) => m.EcransPdv),
                title: 'Ecrans de preparation — LJ SaaS'
              },
              {
                path: 'codes-qr',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/codes-qr/codes-qr').then((m) => m.CodesQrPdv),
                title: 'Codes QR des tables — LJ SaaS'
              },
              {
                path: 'plan-salle',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/plan-salle/plan-salle').then((m) => m.PlanSallePdv),
                title: 'Salles et tables — LJ SaaS'
              }
            ]
          },
          {
            // Pilotage et catalogue : reserves au gerant et a l'administrateur.
            path: '',
            canActivate: [roleGuard('ADMIN', 'GERANT')],
            children: [
              {
                path: 'tableau-bord',
                loadComponent: () =>
                  import('./pages/pdv/tableau-bord/tableau-bord-pdv').then((m) => m.TableauBordPdvPage),
                title: 'Tableau de bord des ventes — LJ SaaS'
              },
              {
                path: 'articles',
                loadComponent: () => import('./pages/pdv/articles/articles').then((m) => m.ArticlesPdv),
                title: 'Produits — LJ SaaS'
              },
              {
                path: 'categories',
                loadComponent: () => import('./pages/pdv/categories/categories').then((m) => m.CategoriesPdv),
                title: 'Categories — LJ SaaS'
              },
              {
                path: 'stock/commandes/:id',
                loadComponent: () => import('./pages/pdv/stock/commande-fournisseur').then((m) => m.CommandeFournisseurPdv),
                title: 'Commande fournisseur — LJ SaaS'
              },
              {
                path: 'rapports',
                loadComponent: () => import('./pages/pdv/rapports/rapports').then((m) => m.RapportsPdv),
                title: 'Rapports du point de vente — LJ SaaS'
              },
              {
                path: 'inventaire',
                loadComponent: () => import('./pages/pdv/inventaire/inventaire').then((m) => m.InventairePdv),
                title: 'Inventaire — LJ SaaS'
              },
              {
                path: 'stock',
                loadComponent: () => import('./pages/pdv/stock/stock').then((m) => m.StockPdv),
                title: 'Reapprovisionnement — LJ SaaS'
              },
              {
                // Circuit des especes (option des reglages) : remises a recompter, coffre, compte de l'entreprise.
                path: 'tresorerie',
                loadComponent: () => import('./pages/pdv/tresorerie/tresorerie').then((m) => m.TresoreriePdv),
                title: 'Remises et coffre — LJ SaaS'
              },
              {
                path: 'reglages',
                canActivate: [roleGuard('ADMIN')],
                loadComponent: () => import('./pages/pdv/reglages/reglages').then((m) => m.ReglagesPdvPage),
                title: 'Reglages du point de vente — LJ SaaS'
              },
              {
                path: 'fournisseurs',
                loadComponent: () => import('./pages/pdv/fournisseurs/fournisseurs').then((m) => m.FournisseursPdv),
                title: 'Fournisseurs — LJ SaaS'
              },
              {
                path: 'depenses',
                loadComponent: () => import('./pages/pdv/depenses/depenses').then((m) => m.DepensesPdv),
                title: 'Depenses — LJ SaaS'
              },
              {
                path: 'caisses',
                loadComponent: () => import('./pages/pdv/caisses/caisses').then((m) => m.CaissesPdv),
                title: 'Caisses — LJ SaaS'
              },
              {
                path: 'caisses/:id',
                loadComponent: () => import('./pages/pdv/sessions/sessions').then((m) => m.SessionsPdv),
                title: 'Historique de la caisse — LJ SaaS'
              },
              {
                path: 'caisses/:id/sessions/:session',
                loadComponent: () => import('./pages/pdv/session-detail/session-detail').then((m) => m.SessionDetailPdv),
                title: 'Session de caisse — LJ SaaS'
              },
              { path: 'sessions', pathMatch: 'full', redirectTo: 'caisses' }
            ]
          },
          {
            // Quincaillerie et materiaux : comptes clients, credit, devis et reglages.
            path: '',
            canActivate: [varianteGuard('POINT_DE_VENTE', 'MATERIAUX')],
            children: [
              {
                path: 'clients',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/clients/clients').then((m) => m.ClientsPdv),
                title: 'Comptes clients — LJ SaaS'
              },
              {
                path: 'livraisons',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER')],
                loadComponent: () => import('./pages/pdv/livraisons/livraisons').then((m) => m.LivraisonsPdv),
                title: 'Reste a livrer — LJ SaaS'
              },
              {
                path: 'devis',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/devis/devis').then((m) => m.DevisPdv),
                title: 'Devis — LJ SaaS'
              }
            ]
          },
          { path: '', pathMatch: 'full', redirectTo: 'caisse' }
        ]
      },

      // --- Module Marketplace ------------------------------------------------
      {
        path: 'marketplace',
        canActivate: [moduleGuard('MARKETPLACE'), roleGuard('ADMIN', 'GERANT', 'GESTIONNAIRE_PARC')],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'commandes' },
          {
            path: 'commandes',
            loadComponent: () => import('./pages/marketplace/commandes-en-ligne').then((m) => m.CommandesEnLigne),
            title: 'Commandes en ligne — LJ SaaS'
          },
          {
            path: 'locations',
            loadComponent: () => import('./pages/marketplace/demandes-location').then((m) => m.DemandesLocation),
            title: 'Demandes de location — LJ SaaS'
          },
          {
            path: 'offres',
            loadComponent: () => import('./pages/marketplace/offres').then((m) => m.OffresMarketplace),
            title: 'Offres en ligne — LJ SaaS'
          },
          {
            path: 'boutique',
            loadComponent: () => import('./pages/marketplace/ma-boutique').then((m) => m.MaBoutique),
            title: 'Ma boutique en ligne — LJ SaaS'
          }
        ]
      },

      // --- Socle commun a tous les modules ------------------------------------
      {
        path: 'modules',
        loadComponent: () => import('./pages/modules/modules').then((m) => m.Modules),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Modules — LJ SaaS'
      },
      {
        path: 'utilisateurs',
        loadComponent: () => import('./pages/utilisateurs/utilisateurs').then((m) => m.Utilisateurs),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Utilisateurs — LJ SaaS'
      },
      {
        path: 'parametrage',
        loadComponent: () => import('./pages/parametrage/parametrage').then((m) => m.Parametrage),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Parametrage — LJ SaaS'
      },
      {
        // « Mon compte » s'ouvre sous l'avatar : l'ancienne adresse ramene a l'accueil.
        path: 'mon-compte',
        pathMatch: 'full',
        redirectTo: ''
      },

      // --- Console de l'exploitant de la plateforme --------------------------
      {
        path: 'plateforme',
        loadComponent: () => import('./pages/plateforme/plateforme').then((m) => m.Plateforme),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'Console plateforme — LJ SaaS'
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
