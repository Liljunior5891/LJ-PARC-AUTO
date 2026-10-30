import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { SocieteService } from '../core/services/societe.service';
import { ClocheNotifications } from '../shared/cloche-notifications';
import { activerDefilementTableaux } from '../shared/defilement-tableaux';
import { Role } from '../core/models/models';

interface EntreeMenu {
  libelle: string;
  route: string;
  /** Ligne d'explication affichee sous le libelle, dans le panneau de l'onglet. */
  description: string;
  /** Profils autorises ; absent, l'entree est visible de tous. */
  roles?: Role[];
}

interface Onglet {
  id: string;
  libelle: string;
  entrees: EntreeMenu[];
  /** Routes de detail rattachees a l'onglet sans figurer dans son panneau. */
  autresRoutes?: string[];
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ClocheNotifications],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);
  private readonly router = inject(Router);

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

  /** Menu du chapitre 37, regroupe en cinq onglets. */
  private readonly menu: Onglet[] = [
    {
      id: 'accueil', libelle: 'Accueil',
      entrees: [
        { libelle: 'Tableau de bord', route: '/tableau-bord', description: 'Chiffre d\'affaires, depenses et resultat' },
        { libelle: 'Direction', route: '/direction', description: 'Indicateurs de pilotage et decisions' },
        { libelle: 'Rentabilite', route: '/rentabilite', description: 'Marge par vehicule sur la periode' }
      ]
    },
    {
      id: 'parc', libelle: 'Parc',
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
      id: 'exploitation', libelle: 'Exploitation',
      autresRoutes: ['/missions', '/locations', '/livraisons'],
      entrees: [
        { libelle: 'Chauffeurs', route: '/chauffeurs', description: 'Equipe, permis et disponibilite' },
        { libelle: 'Clients', route: '/clients', description: 'Portefeuille et contrats en cours' }
      ]
    },
    {
      id: 'finances', libelle: 'Finances',
      entrees: [
        { libelle: 'Recettes', route: '/recettes', description: 'Locations, transports et livraisons' },
        { libelle: 'Depenses', route: '/depenses', description: 'Charges rattachees a chaque vehicule' },
        { libelle: 'Carburant', route: '/carburant', description: 'Pleins et consommation' },
        { libelle: 'Facturation', route: '/facturation', description: 'Factures clients et reglements' }
      ]
    },
    {
      id: 'administration', libelle: 'Administration',
      entrees: [
        { libelle: 'Rapports PDF', route: '/rapports', description: 'Etats a imprimer ou a envoyer' },
        { libelle: 'Utilisateurs', route: '/utilisateurs', description: 'Comptes et profils d\'acces', roles: ['ADMIN'] },
        { libelle: 'Parametrage', route: '/parametrage', description: 'Societe, logo et referentiels', roles: ['ADMIN'] },
        { libelle: 'Mon compte', route: '/mon-compte', description: 'Identite et mot de passe' }
      ]
    }
  ];

  /** Onglets reduits aux entrees que le profil connecte a le droit d'ouvrir. */
  readonly onglets: Onglet[] = this.menu
    .map((onglet) => ({
      ...onglet,
      entrees: onglet.entrees.filter((entree) => !entree.roles || this.auth.aRole(...entree.roles))
    }))
    .filter((onglet) => onglet.entrees.length > 0);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((evenement) => evenement instanceof NavigationEnd),
      map((evenement) => evenement.urlAfterRedirects)
    ),
    { initialValue: this.router.url }
  );

  /** Onglet de la page affichee. */
  private readonly ongletDeLaPage = computed(() => {
    const url = this.url();
    const correspond = (route: string) => url === route || url.startsWith(route + '/') || url.startsWith(route + '?');
    return this.onglets.find((onglet) =>
      [...onglet.entrees.map((entree) => entree.route), ...(onglet.autresRoutes ?? [])].some(correspond)
    ) ?? this.onglets[0];
  });

  /** Onglet choisi au clic ; une navigation le remet sur celui de la page. */
  private readonly ongletChoisi = signal<string | null>(null);

  readonly ongletOuvert = computed(() =>
    this.onglets.find((onglet) => onglet.id === this.ongletChoisi()) ?? this.ongletDeLaPage()
  );

  private readonly contenu = viewChild.required<ElementRef<HTMLElement>>('contenu');

  constructor() {
    // Tableaux de tous les ecrans : les lignes defilent dans la hauteur restante de la fenetre.
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => activerDefilementTableaux(this.contenu().nativeElement, destroyRef));
    // L'identite peut deja avoir ete chargee par la page de connexion.
    if (this.societe() === null) {
      this.societeService.chargerIdentite().subscribe({ error: () => undefined });
    }
    this.router.events
      .pipe(filter((evenement) => evenement instanceof NavigationEnd))
      .subscribe(() => this.ongletChoisi.set(null));
  }

  /** Un clic ouvre le panneau de l'onglet ; un second clic sur le meme le replie. */
  ouvrirOnglet(onglet: Onglet): void {
    if (onglet.id === this.ongletOuvert().id && this.panneauOuvert()) {
      this.panneauOuvert.set(false);
      return;
    }
    this.ongletChoisi.set(onglet.id);
    this.panneauOuvert.set(true);
  }

  /** L'onglet de la page reste marque, meme quand on en consulte un autre. */
  estOngletDeLaPage(onglet: Onglet): boolean {
    return onglet.id === this.ongletDeLaPage().id;
  }

  basculerPanneau(): void {
    this.panneauOuvert.update((ouvert) => !ouvert);
  }

  deconnexion(): void {
    this.auth.logout();
  }
}
