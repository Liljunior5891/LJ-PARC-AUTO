import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
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
    title: 'Nova — Logiciels de gestion pour entreprises',
    loadComponent: () => import('./pages/accueil/accueil').then((m) => m.Accueil)
  },
  {
    path: 'inscription',
    loadComponent: () => import('./pages/inscription/inscription').then((m) => m.Inscription),
    title: 'Creer un compte entreprise — Nova'
  },
  {
    // Lien envoye par e-mail a l'inscription : cree le compte et ouvre la session.
    path: 'inscription/confirmation',
    loadComponent: () => import('./pages/inscription/confirmation-inscription').then((m) => m.ConfirmationInscription),
    title: 'Confirmation de l\'inscription — Nova'
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
    title: 'Place de marche — Nova'
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
  // Gestion de presence : pointage depuis le telephone (code QR) et borne du lieu, sans compte ni menu.
  {
    path: 'pointage/:jeton',
    loadComponent: () => import('./pages/presence/pointage/pointage').then((m) => m.PointagePage),
    title: 'Pointage'
  },
  {
    path: 'borne-presence',
    loadComponent: () => import('./pages/presence/borne/borne').then((m) => m.BornePresencePage),
    title: 'Borne de pointage'
  },
  {
    path: 'connexion',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Connexion — Nova'
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
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/tableau-bord/tableau-bord').then((m) => m.TableauBord),
            title: 'Tableau de bord — Nova'
          },
          {
            path: 'vue-parc',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/vue-parc/vue-parc').then((m) => m.VueParc),
            title: 'Vue globale du parc — Nova'
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
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            title: 'Nouveau vehicule — Nova'
          },
          {
            path: 'camions/:id',
            loadComponent: () => import('./pages/camions/camion-fiche').then((m) => m.CamionFiche),
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            title: 'Fiche vehicule — Nova'
          },
          {
            path: 'chauffeurs',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/chauffeurs/chauffeurs-liste').then((m) => m.ChauffeursListe),
            title: 'Chauffeurs — Nova'
          },
          {
            path: 'chauffeurs/nouveau',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/chauffeurs/chauffeur-fiche').then((m) => m.ChauffeurFiche),
            title: 'Nouveau chauffeur — Nova'
          },
          {
            path: 'chauffeurs/:id',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/chauffeurs/chauffeur-fiche').then((m) => m.ChauffeurFiche),
            title: 'Fiche chauffeur — Nova'
          },
          {
            path: 'affectations',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/affectations/affectations').then((m) => m.Affectations),
            title: 'Affectations — Nova'
          },
          {
            path: 'documents',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/documents/documents').then((m) => m.Documents),
            title: 'Documents — Nova'
          },

          // --- Exploitation ----------------------------------------------------
          {
            path: 'clients',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/clients/clients-liste').then((m) => m.ClientsListe),
            title: 'Clients — Nova'
          },
          {
            path: 'clients/nouveau',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/clients/client-fiche').then((m) => m.ClientFiche),
            title: 'Nouveau client — Nova'
          },
          {
            path: 'clients/:id',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/clients/client-fiche').then((m) => m.ClientFiche),
            title: 'Fiche client — Nova'
          },
          // Anciennes adresses des contrats : une redirection ne porte pas de garde
          // (Angular la refuse), c'est /recettes qui reserve l'ecran a la gestion.
          { path: 'locations', pathMatch: 'full', redirectTo: '/recettes' },
          { path: 'locations/nouveau', redirectTo: '/recettes' },
          { path: 'locations/:id', redirectTo: '/recettes' },
          {
            path: 'missions',
            loadComponent: () => import('./pages/missions/missions-liste').then((m) => m.MissionsListe),
            title: 'Missions — Nova'
          },
          {
            path: 'missions/nouvelle',
            loadComponent: () => import('./pages/missions/mission-fiche').then((m) => m.MissionFiche),
            title: 'Nouvelle mission — Nova'
          },
          {
            path: 'missions/:id',
            loadComponent: () => import('./pages/missions/mission-fiche').then((m) => m.MissionFiche),
            title: 'Mission — Nova'
          },
          {
            path: 'recettes',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/recettes/recettes').then((m) => m.Recettes),
            title: 'Recettes — Nova'
          },
          {
            path: 'depenses',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/depenses/depenses').then((m) => m.Depenses),
            title: 'Depenses — Nova'
          },
          {
            path: 'fournisseurs-parc',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/fournisseurs-parc/fournisseurs-parc').then((m) => m.FournisseursParc),
            title: 'Fournisseurs — Nova'
          },
          {
            path: 'carburant',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/carburant/carburant').then((m) => m.Carburant),
            title: 'Carburant — Nova'
          },
          {
            path: 'facturation',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/factures/factures').then((m) => m.Factures),
            title: 'Facturation — Nova'
          },
          {
            path: 'rentabilite',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/rentabilite/rentabilite').then((m) => m.Rentabilite),
            title: 'Rentabilite — Nova'
          },
          {
            path: 'gps',
            loadComponent: () => import('./pages/carte-parc/carte-parc').then((m) => m.CarteParc),
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            title: 'Carte du parc — Nova'
          },
          {
            path: 'maintenance',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/maintenance/maintenance').then((m) => m.Maintenance),
            title: 'Maintenance — Nova'
          },
          {
            path: 'direction',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/direction/direction').then((m) => m.Direction),
            title: 'Pilotage — Nova'
          },
          {
            path: 'rapports',
            canActivate: [roleGuard('ADMIN', 'GESTIONNAIRE_PARC')],
            loadComponent: () => import('./pages/rapports/rapports').then((m) => m.Rapports),
            title: 'Rapports — Nova'
          },
          {
            path: 'livraisons',
            loadComponent: () => import('./pages/livraisons/livraisons').then((m) => m.Livraisons),
            title: 'Livraisons — Nova'
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
            title: 'Caisse — Nova'
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
                title: 'Magasins et entrepots — Nova'
              },
              {
                path: 'stock-lieu',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'MAGASINIER')],
                loadComponent: () => import('./pages/pdv/stock-lieu/stock-lieu').then((m) => m.StockLieuPdv),
                title: 'Stock par lieu — Nova'
              },
              {
                path: 'retraits',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'MAGASINIER')],
                loadComponent: () => import('./pages/pdv/retraits/retraits').then((m) => m.RetraitsPdv),
                title: 'Bons de retrait — Nova'
              },
              {
                path: 'transferts',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'MAGASINIER')],
                loadComponent: () => import('./pages/pdv/transferts/transferts').then((m) => m.TransfertsPdv),
                title: 'Transferts — Nova'
              }
            ]
          },
          {
            // Benefices des associes : l'administrateur voit tout, un associe sa seule part.
            path: 'benefices',
            canActivate: [roleGuard('ADMIN', 'ASSOCIE')],
            loadComponent: () => import('./pages/pdv/benefices/benefices').then((m) => m.BeneficesPdv),
            title: 'Benefices des associes — Nova'
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
            title: 'Ventes — Nova'
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
                title: 'Plan de salle — Nova'
              },
              {
                path: 'comptoir',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR')],
                loadComponent: () => import('./pages/pdv/commande/commande').then((m) => m.CommandePdv),
                title: 'Caisse — Nova'
              },
              {
                path: 'commandes/:id',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR')],
                loadComponent: () => import('./pages/pdv/commande/commande').then((m) => m.CommandePdv),
                title: 'Commande — Nova'
              },
              {
                path: 'cuisine',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'PREPARATEUR')],
                loadComponent: () => import('./pages/pdv/cuisine/cuisine').then((m) => m.CuisinePdv),
                title: 'Cuisine et bar — Nova'
              },
              {
                path: 'ecrans',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/ecrans/ecrans').then((m) => m.EcransPdv),
                title: 'Postes et imprimantes — Nova'
              },
              {
                path: 'codes-qr',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/codes-qr/codes-qr').then((m) => m.CodesQrPdv),
                title: 'Codes QR des tables — Nova'
              },
              {
                path: 'plan-salle',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/plan-salle/plan-salle').then((m) => m.PlanSallePdv),
                title: 'Salles et tables — Nova'
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
                title: 'Tableau de bord des ventes — Nova'
              },
              {
                path: 'articles',
                loadComponent: () => import('./pages/pdv/articles/articles').then((m) => m.ArticlesPdv),
                title: 'Produits — Nova'
              },
              {
                path: 'categories',
                loadComponent: () => import('./pages/pdv/categories/categories').then((m) => m.CategoriesPdv),
                title: 'Categories — Nova'
              },
              {
                path: 'stock/commandes/:id',
                loadComponent: () => import('./pages/pdv/stock/commande-fournisseur').then((m) => m.CommandeFournisseurPdv),
                title: 'Commande fournisseur — Nova'
              },
              {
                path: 'rapports',
                loadComponent: () => import('./pages/pdv/rapports/rapports').then((m) => m.RapportsPdv),
                title: 'Rapports du point de vente — Nova'
              },
              {
                path: 'inventaire',
                loadComponent: () => import('./pages/pdv/inventaire/inventaire').then((m) => m.InventairePdv),
                title: 'Inventaire — Nova'
              },
              {
                path: 'stock',
                loadComponent: () => import('./pages/pdv/stock/stock').then((m) => m.StockPdv),
                title: 'Reapprovisionnement — Nova'
              },
              {
                // Circuit des especes (option des reglages) : remises a recompter, coffre, compte de l'entreprise.
                path: 'tresorerie',
                loadComponent: () => import('./pages/pdv/tresorerie/tresorerie').then((m) => m.TresoreriePdv),
                title: 'Remises et coffre — Nova'
              },
              {
                path: 'reglages',
                canActivate: [roleGuard('ADMIN')],
                loadComponent: () => import('./pages/pdv/reglages/reglages').then((m) => m.ReglagesPdvPage),
                title: 'Reglages du point de vente — Nova'
              },
              {
                path: 'fournisseurs',
                loadComponent: () => import('./pages/pdv/fournisseurs/fournisseurs').then((m) => m.FournisseursPdv),
                title: 'Fournisseurs — Nova'
              },
              {
                path: 'depenses',
                loadComponent: () => import('./pages/pdv/depenses/depenses').then((m) => m.DepensesPdv),
                title: 'Depenses — Nova'
              },
              {
                path: 'hors-ligne',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/hors-ligne/hors-ligne-pdv').then((m) => m.HorsLignePdv),
                title: 'Ventes hors ligne — Nova'
              },
              {
                path: 'fonds-reserve',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/fonds-reserve/fonds-reserve').then((m) => m.FondsReservePdv),
                title: 'Fonds de reserve — Nova'
              },
              {
                path: 'validations-caisse',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/validations-caisse/validations-caisse').then((m) => m.ValidationsCaissePdv),
                title: 'Depenses de caisse a valider — Nova'
              },
              {
                path: 'caisses',
                loadComponent: () => import('./pages/pdv/caisses/caisses').then((m) => m.CaissesPdv),
                title: 'Caisses — Nova'
              },
              {
                path: 'caisses/:id',
                loadComponent: () => import('./pages/pdv/sessions/sessions').then((m) => m.SessionsPdv),
                title: 'Historique de la caisse — Nova'
              },
              {
                path: 'caisses/:id/sessions/:session',
                loadComponent: () => import('./pages/pdv/session-detail/session-detail').then((m) => m.SessionDetailPdv),
                title: 'Session de caisse — Nova'
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
                title: 'Comptes clients — Nova'
              },
              {
                path: 'livraisons',
                canActivate: [roleGuard('ADMIN', 'GERANT', 'CAISSIER')],
                loadComponent: () => import('./pages/pdv/livraisons/livraisons').then((m) => m.LivraisonsPdv),
                title: 'Reste a livrer — Nova'
              },
              {
                path: 'devis',
                canActivate: [roleGuard('ADMIN', 'GERANT')],
                loadComponent: () => import('./pages/pdv/devis/devis').then((m) => m.DevisPdv),
                title: 'Devis — Nova'
              }
            ]
          },
          { path: '', pathMatch: 'full', redirectTo: 'caisse' }
        ]
      },

      // --- Module Gestion de presence -------------------------------------------
      {
        path: 'presence',
        canActivate: [moduleGuard('PRESENCE')],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'tableau-bord' },
          {
            path: 'tableau-bord',
            loadComponent: () => import('./pages/presence/tableau-bord/tableau-bord-presence').then((m) => m.TableauBordPresence),
            title: 'Presences du jour — Nova'
          },
          {
            path: 'employes',
            loadComponent: () => import('./pages/presence/employes/employes').then((m) => m.EmployesPresence),
            title: 'Employes — Nova'
          },
          {
            path: 'employes/:id',
            loadComponent: () => import('./pages/presence/fiche/fiche-employe').then((m) => m.FicheEmploye),
            title: 'Fiche de presence — Nova'
          },
          {
            path: 'lieux',
            loadComponent: () => import('./pages/presence/lieux/lieux').then((m) => m.LieuxPresence),
            title: 'Lieux et bornes — Nova'
          },
          {
            path: 'reglages',
            loadComponent: () => import('./pages/presence/reglages/reglages-presence').then((m) => m.ReglagesPresence),
            title: 'Horaire — Nova'
          }
        ]
      },

      // --- Module Agences multiservices -----------------------------------------
      {
        path: 'agences',
        canActivate: [moduleGuard('AGENCES')],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'tableau-bord' },
          {
            path: 'tableau-bord',
            loadComponent: () => import('./pages/agences/tableau-bord/tableau-bord-agences').then((m) => m.TableauBordAgencesPage),
            title: 'Tableau de bord des agences — Nova'
          },
          {
            path: 'cartes',
            loadComponent: () => import('./pages/agences/cartes/cartes-agences').then((m) => m.CartesAgences),
            title: 'Cartes prepayees — Nova'
          },
          {
            path: 'depenses',
            loadComponent: () => import('./pages/agences/depenses/depenses-agences').then((m) => m.DepensesAgences),
            title: 'Depenses des agences — Nova'
          },
          {
            path: 'credits',
            loadComponent: () => import('./pages/agences/credits/credits-agences').then((m) => m.CreditsAgences),
            title: 'Credits et dettes — Nova'
          },
          {
            path: 'rapprochement',
            loadComponent: () => import('./pages/agences/rapprochement/rapprochement').then((m) => m.RapprochementCommissions),
            title: 'Rapprochement des commissions — Nova'
          },
          {
            path: 'rapports',
            loadComponent: () => import('./pages/agences/rapports/rapports-agences').then((m) => m.RapportsAgences),
            title: 'Rapports des agences — Nova'
          },
          {
            path: 'cloture',
            loadComponent: () => import('./pages/agences/cloture/cloture').then((m) => m.ClotureAgencePage),
            title: 'Cloture de caisse — Nova'
          },
          {
            path: 'audit',
            loadComponent: () => import('./pages/agences/audit/audit-agences').then((m) => m.AuditAgences),
            title: 'Journal d\'audit — Nova'
          },
          {
            // La saisie se fait dans une fenetre de l'ecran des transactions.
            path: 'operation',
            pathMatch: 'full',
            redirectTo: () => inject(Router).createUrlTree(['/agences/transactions'], { queryParams: { nouvelle: 1 } })
          },
          {
            path: 'transactions',
            loadComponent: () => import('./pages/agences/transactions/transactions').then((m) => m.TransactionsAgences),
            title: 'Transactions des agences — Nova'
          },
          {
            path: 'caisses',
            loadComponent: () => import('./pages/agences/caisses/caisses-agences').then((m) => m.CaissesAgences),
            title: 'Caisses des caissiers — Nova'
          },
          {
            path: 'mouvements',
            loadComponent: () => import('./pages/agences/mouvements/mouvements').then((m) => m.MouvementsAgences),
            title: 'Mouvements entre comptes — Nova'
          },
          {
            path: 'soldes',
            loadComponent: () => import('./pages/agences/soldes/soldes').then((m) => m.SoldesAgences),
            title: 'Soldes et tresorerie — Nova'
          },
          {
            path: 'hors-ligne',
            canActivate: [roleGuard('ADMIN', 'SUPERVISEUR_AGENCE')],
            loadComponent: () => import('./pages/agences/hors-ligne/conflits-hors-ligne').then((m) => m.ConflitsHorsLigne),
            title: 'Operations hors ligne — Nova'
          },
          {
            path: 'agences',
            loadComponent: () => import('./pages/agences/liste/agences-liste').then((m) => m.AgencesListe),
            title: 'Agences — Nova'
          },
          {
            path: 'services',
            loadComponent: () => import('./pages/agences/services/services-agences').then((m) => m.ServicesAgences),
            title: 'Services et operations — Nova'
          },
          {
            path: 'baremes',
            loadComponent: () => import('./pages/agences/baremes/baremes').then((m) => m.BaremesAgences),
            title: 'Baremes de commission — Nova'
          },
          {
            path: 'reglages',
            canActivate: [roleGuard('ADMIN')],
            loadComponent: () => import('./pages/agences/reglages/reglages-agences').then((m) => m.ReglagesAgencesPage),
            title: 'Reglages des agences — Nova'
          }
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
            title: 'Commandes en ligne — Nova'
          },
          {
            path: 'locations',
            loadComponent: () => import('./pages/marketplace/demandes-location').then((m) => m.DemandesLocation),
            title: 'Demandes de location — Nova'
          },
          {
            path: 'offres',
            loadComponent: () => import('./pages/marketplace/offres').then((m) => m.OffresMarketplace),
            title: 'Offres en ligne — Nova'
          },
          {
            path: 'boutique',
            loadComponent: () => import('./pages/marketplace/ma-boutique').then((m) => m.MaBoutique),
            title: 'Ma boutique en ligne — Nova'
          }
        ]
      },

      // --- Socle commun a tous les modules ------------------------------------
      {
        path: 'modules',
        loadComponent: () => import('./pages/modules/modules').then((m) => m.Modules),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Modules — Nova'
      },
      // --- Journal d'audit des modules (hors Agences, qui a le sien) ------------
      {
        path: 'journal-audit',
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        children: [
          {
            path: '',
            loadComponent: () => import('./pages/journal-audit/journal-audit').then((m) => m.JournalAudit),
            title: 'Journal d\'audit — Nova'
          },
          {
            path: 'parc',
            canActivate: [moduleGuard('GESTION_PARC')],
            data: { modules: 'PARC,EXPLOITATION,FINANCES,MAINTENANCE,GPS', titre: 'Journal d\'audit de la gestion de parc' },
            loadComponent: () => import('./pages/journal-audit/journal-audit').then((m) => m.JournalAudit),
            title: 'Journal d\'audit du parc — Nova'
          },
          {
            path: 'pdv',
            canActivate: [moduleGuard('POINT_DE_VENTE')],
            data: { modules: 'POINT_DE_VENTE', titre: 'Journal d\'audit du point de vente' },
            loadComponent: () => import('./pages/journal-audit/journal-audit').then((m) => m.JournalAudit),
            title: 'Journal d\'audit du point de vente — Nova'
          },
          {
            path: 'marketplace',
            canActivate: [moduleGuard('MARKETPLACE')],
            data: { modules: 'MARKETPLACE', titre: 'Journal d\'audit de la marketplace' },
            loadComponent: () => import('./pages/journal-audit/journal-audit').then((m) => m.JournalAudit),
            title: 'Journal d\'audit de la marketplace — Nova'
          }
        ]
      },
      {
        path: 'notifications',
        loadComponent: () => import('./pages/notifications/preferences-notifications').then((m) => m.PreferencesNotifications),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Notifications — Nova'
      },
      {
        path: 'utilisateurs',
        loadComponent: () => import('./pages/utilisateurs/utilisateurs').then((m) => m.Utilisateurs),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Utilisateurs — Nova'
      },
      {
        path: 'parametrage',
        loadComponent: () => import('./pages/parametrage/parametrage').then((m) => m.Parametrage),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Parametrage — Nova'
      },
      {
        // « Mon compte » s'ouvre sous l'avatar : l'ancienne adresse ramene a l'accueil.
        path: 'mon-compte',
        pathMatch: 'full',
        redirectTo: ''
      },

      // --- Console de l'exploitant de la plateforme --------------------------
      { path: 'plateforme', pathMatch: 'full', redirectTo: 'plateforme/tableau-bord' },
      {
        path: 'plateforme/tableau-bord',
        loadComponent: () => import('./pages/plateforme/tableau-bord-plateforme').then((m) => m.TableauBordPlateformeComponent),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'Tableau de bord — Nova'
      },
      {
        path: 'plateforme/entreprises',
        loadComponent: () => import('./pages/plateforme/plateforme').then((m) => m.Plateforme),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'Entreprises — Nova'
      },
      {
        path: 'plateforme/journal',
        loadComponent: () => import('./pages/plateforme/journal-plateforme').then((m) => m.JournalPlateforme),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: "Journal d'audit — Nova"
      },
      {
        path: 'plateforme/notifications',
        loadComponent: () => import('./pages/plateforme/notifications-plateforme').then((m) => m.NotificationsPlateforme),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'Notifications — Nova'
      },
      {
        path: 'plateforme/whatsapp',
        loadComponent: () => import('./pages/plateforme/whatsapp-plateforme').then((m) => m.WhatsAppPlateforme),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'WhatsApp — Nova'
      },
      { path: 'plateforme/maintenance', pathMatch: 'full', redirectTo: 'plateforme/maintenance/maintenances' },
      {
        path: 'plateforme/maintenance/:vue',
        loadComponent: () => import('./pages/plateforme/maintenance-plateforme').then((m) => m.ConsoleMaintenance),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'Maintenance — Nova'
      },
      { path: 'plateforme/abonnements', pathMatch: 'full', redirectTo: 'plateforme/abonnements/paiements' },
      {
        path: 'plateforme/abonnements/:vue',
        loadComponent: () => import('./pages/plateforme/abonnements-plateforme').then((m) => m.AbonnementsPlateforme),
        canActivate: [roleGuard('SUPER_ADMIN')],
        title: 'Abonnements — Nova'
      },
      {
        path: 'abonnement',
        loadComponent: () => import('./pages/abonnement/mon-abonnement').then((m) => m.MonAbonnement),
        canActivate: [entrepriseGuard, roleGuard('ADMIN')],
        title: 'Mon abonnement — Nova'
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
