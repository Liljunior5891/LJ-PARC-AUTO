import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';

interface EntreeMenu {
  libelle: string;
  route: string;
  icone: string;
  /** Section du menu principal, chapitre 37 du cahier des charges. */
  section: string;
  disponible: boolean;
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
    { libelle: 'Locations', route: '/locations', icone: '📄', section: 'Exploitation', disponible: true },
    { libelle: 'Missions', route: '/missions', icone: '🧭', section: 'Exploitation', disponible: true },
    { libelle: 'Livraisons', route: '/livraisons', icone: '📦', section: 'Exploitation', disponible: true },
    { libelle: 'Recettes', route: '/recettes', icone: '💰', section: 'Finances', disponible: true },
    { libelle: 'Depenses', route: '/depenses', icone: '💸', section: 'Finances', disponible: true },
    { libelle: 'Carburant', route: '/carburant', icone: '⛽', section: 'Finances', disponible: true },
    { libelle: 'Facturation', route: '/facturation', icone: '🧾', section: 'Finances', disponible: true },
    { libelle: 'Rentabilite', route: '/rentabilite', icone: '📈', section: 'Pilotage', disponible: true },
    { libelle: 'Carte du parc', route: '/gps', icone: '🗺️', section: 'GPS', disponible: false },
    { libelle: 'Maintenance', route: '/maintenance', icone: '🔧', section: 'Maintenance', disponible: false },
    { libelle: 'Rapports', route: '/rapports', icone: '📈', section: 'Pilotage', disponible: false }
  ];

  get sections(): string[] {
    return [...new Set(this.menu.map((entree) => entree.section))];
  }

  entreesDe(section: string): EntreeMenu[] {
    return this.menu.filter((entree) => entree.section === section);
  }

  basculerMenu(): void {
    this.menuOuvert.update((ouvert) => !ouvert);
  }

  deconnexion(): void {
    this.auth.logout();
  }
}
