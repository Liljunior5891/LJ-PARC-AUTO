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
            loadComponent: () => import('./pages/locations/locations-liste').then((m) => m.LocationsListe),
            title: 'Contrats de location — LJ SaaS'
          },
          {
            path: 'locations/nouveau',
            loadComponent: () => import('./pages/locations/location-fiche').then((m) => m.LocationFiche),
            title: 'Nouveau contrat — LJ SaaS'
          },
          {
            path: 'locations/:id',
            loadComponent: () => import('./pages/locations/location-fiche').then((m) => m.LocationFiche),
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
          { path: '', pathMatch: 'full', redirectTo: 'caisse' }
        ]
      },

      // --- Socle commun a tous les modules ------------------------------------
      {
        path: 'modules',
        loadComponent: () => import('./pages/modules/modules').then((m) => m.Modules),
        canActivate: [entrepriseGuard],
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
        path: 'mon-compte',
        loadComponent: () => import('./pages/mon-compte/mon-compte').then((m) => m.MonCompte),
        title: 'Mon compte — LJ SaaS'
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
