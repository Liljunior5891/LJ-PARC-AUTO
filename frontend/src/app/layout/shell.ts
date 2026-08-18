import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { Role } from '../core/models/models';

interface EntreeMenu {
  libelle: string;
  route: string;
  icone: string;
  /** Section du menu principal, chapitre 37 du cahier des charges. */
  section: string;
  disponible: boolean;
  /** Profils autorises ; absent, l'entree est visible de tous. */
  roles?: Role[];
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell {
  private readonly auth = inject(AuthService);

  readonly utilisateur = this.auth.utilisateur;
  readonly menuOuvert = signal(true);

  /** Menu du chapitre 37 : les entrees non encore developpees restent visibles mais desactivees. */
  readonly menu: EntreeMenu[] = [
    { libelle: 'Tableau de bord', route: '/tableau-bord', icone: '📊', section: 'Pilotage', disponible: true },
    { libelle: 'Vue du parc', route: '/vue-parc', icone: '🗂️', section: 'Pilotage', disponible: true },
    { libelle: 'Camions', route: '/camions', icone: '🚛', section: 'Parc automobile', disponible: true },
    { libelle: 'Affectations', route: '/affectations', icone: '🔗', section: 'Parc automobile', disponible: true },
    { libelle: 'Documents', route: '/documents', icone: '📁', section: 'Parc automobile', disponible: true },
    { libelle: 'Chauffeurs', route: '/chauffeurs', icone: '👷', section: 'Chauffeurs', disponible: true },
    { libelle: 'Clients', route: '/clients', icone: '🏢', section: 'Exploitation', disponible: true },
    { libelle: 'Recettes', route: '/recettes', icone: '💰', section: 'Finances', disponible: true },
    { libelle: 'Depenses', route: '/depenses', icone: '💸', section: 'Finances', disponible: true },
    { libelle: 'Carburant', route: '/carburant', icone: '⛽', section: 'Finances', disponible: true },
    { libelle: 'Facturation', route: '/facturation', icone: '🧾', section: 'Finances', disponible: true },
    { libelle: 'Rentabilite', route: '/rentabilite', icone: '📈', section: 'Pilotage', disponible: true },
    { libelle: 'Carte du parc', route: '/gps', icone: '🗺️', section: 'GPS', disponible: true },
    { libelle: 'Maintenance', route: '/maintenance', icone: '🔧', section: 'Maintenance', disponible: true },
    { libelle: 'Direction', route: '/direction', icone: '🎯', section: 'Pilotage', disponible: true },
    { libelle: 'Rapports PDF', route: '/rapports', icone: '📄', section: 'Rapports', disponible: true },
    { libelle: 'Utilisateurs', route: '/utilisateurs', icone: '👥', section: 'Administration',
      disponible: true, roles: ['ADMIN'] },
    { libelle: 'Mon compte', route: '/mon-compte', icone: '🔑', section: 'Administration',
      disponible: true }
  ];

  /** Entrees que le profil connecte a le droit d'ouvrir. */
  private get menuVisible(): EntreeMenu[] {
    return this.menu.filter((entree) => !entree.roles || this.auth.aRole(...entree.roles));
  }

  get sections(): string[] {
    return [...new Set(this.menuVisible.map((entree) => entree.section))];
  }

  entreesDe(section: string): EntreeMenu[] {
    return this.menuVisible.filter((entree) => entree.section === section);
  }

  basculerMenu(): void {
    this.menuOuvert.update((ouvert) => !ouvert);
  }

  deconnexion(): void {
    this.auth.logout();
  }
}
