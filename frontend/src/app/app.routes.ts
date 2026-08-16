import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'connexion',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Connexion — PARC AUTO'
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tableau-bord' },
      {
        path: 'tableau-bord',
        loadComponent: () => import('./pages/tableau-bord/tableau-bord').then((m) => m.TableauBord),
        title: 'Tableau de bord — PARC AUTO'
      },
      {
        path: 'vue-parc',
        loadComponent: () => import('./pages/vue-parc/vue-parc').then((m) => m.VueParc),
        title: 'Vue globale du parc — PARC AUTO'
      },

      // --- Parc automobile -------------------------------------------------
      {
        path: 'camions',
        loadComponent: () => import('./pages/camions/camions-liste').then((m) => m.CamionsListe),
        title: 'Camions — PARC AUTO'
      },
      {
        path: 'camions/nouveau',
        loadComponent: () => import('./pages/camions/camion-fiche').then((m) => m.CamionFiche),
        title: 'Nouveau camion — PARC AUTO'
      },
      {
        path: 'camions/:id',
        loadComponent: () => import('./pages/camions/camion-fiche').then((m) => m.CamionFiche),
        title: 'Fiche camion — PARC AUTO'
      },
      {
        path: 'chauffeurs',
        loadComponent: () => import('./pages/chauffeurs/chauffeurs-liste').then((m) => m.ChauffeursListe),
        title: 'Chauffeurs — PARC AUTO'
      },
      {
        path: 'chauffeurs/nouveau',
        loadComponent: () => import('./pages/chauffeurs/chauffeur-fiche').then((m) => m.ChauffeurFiche),
        title: 'Nouveau chauffeur — PARC AUTO'
      },
      {
        path: 'chauffeurs/:id',
        loadComponent: () => import('./pages/chauffeurs/chauffeur-fiche').then((m) => m.ChauffeurFiche),
        title: 'Fiche chauffeur — PARC AUTO'
      },
      {
        path: 'affectations',
        loadComponent: () => import('./pages/affectations/affectations').then((m) => m.Affectations),
        title: 'Affectations — PARC AUTO'
      },
      {
        path: 'documents',
        loadComponent: () => import('./pages/documents/documents').then((m) => m.Documents),
        title: 'Documents — PARC AUTO'
      },

      // --- Exploitation ----------------------------------------------------
      {
        path: 'clients',
        loadComponent: () => import('./pages/clients/clients-liste').then((m) => m.ClientsListe),
        title: 'Clients — PARC AUTO'
      },
      {
        path: 'clients/nouveau',
        loadComponent: () => import('./pages/clients/client-fiche').then((m) => m.ClientFiche),
        title: 'Nouveau client — PARC AUTO'
      },
      {
        path: 'clients/:id',
        loadComponent: () => import('./pages/clients/client-fiche').then((m) => m.ClientFiche),
        title: 'Fiche client — PARC AUTO'
      },
      {
        path: 'locations',
        loadComponent: () => import('./pages/locations/locations-liste').then((m) => m.LocationsListe),
        title: 'Contrats de location — PARC AUTO'
      },
      {
        path: 'locations/nouveau',
        loadComponent: () => import('./pages/locations/location-fiche').then((m) => m.LocationFiche),
        title: 'Nouveau contrat — PARC AUTO'
      },
      {
        path: 'locations/:id',
        loadComponent: () => import('./pages/locations/location-fiche').then((m) => m.LocationFiche),
        title: 'Contrat de location — PARC AUTO'
      },
      {
        path: 'missions',
        loadComponent: () => import('./pages/missions/missions-liste').then((m) => m.MissionsListe),
        title: 'Missions — PARC AUTO'
      },
      {
        path: 'missions/nouvelle',
        loadComponent: () => import('./pages/missions/mission-fiche').then((m) => m.MissionFiche),
        title: 'Nouvelle mission — PARC AUTO'
      },
      {
        path: 'missions/:id',
        loadComponent: () => import('./pages/missions/mission-fiche').then((m) => m.MissionFiche),
        title: 'Mission — PARC AUTO'
      },
      {
        path: 'recettes',
        loadComponent: () => import('./pages/recettes/recettes').then((m) => m.Recettes),
        title: 'Recettes — PARC AUTO'
      },
      {
        path: 'depenses',
        loadComponent: () => import('./pages/depenses/depenses').then((m) => m.Depenses),
        title: 'Depenses — PARC AUTO'
      },
      {
        path: 'carburant',
        loadComponent: () => import('./pages/carburant/carburant').then((m) => m.Carburant),
        title: 'Carburant — PARC AUTO'
      },
      {
        path: 'facturation',
        loadComponent: () => import('./pages/factures/factures').then((m) => m.Factures),
        title: 'Facturation — PARC AUTO'
      },
      {
        path: 'rentabilite',
        loadComponent: () => import('./pages/rentabilite/rentabilite').then((m) => m.Rentabilite),
        title: 'Rentabilite — PARC AUTO'
      },
      {
        path: 'gps',
        loadComponent: () => import('./pages/carte-parc/carte-parc').then((m) => m.CarteParc),
        title: 'Carte du parc — PARC AUTO'
      },
      {
        path: 'livraisons',
        loadComponent: () => import('./pages/livraisons/livraisons').then((m) => m.Livraisons),
        title: 'Livraisons — PARC AUTO'
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
