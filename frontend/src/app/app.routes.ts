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
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
