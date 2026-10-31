import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { ModuleCatalogue, PRESENTATION_MODULES } from '../../core/models/plateforme';
import { PlateformeService } from '../../core/services/plateforme.service';
import { Apparition } from '../../shared/apparition';

interface Offre {
  nom: string;
  prix: string;
  mention: string;
  points: string[];
  action: string;
  vedette?: boolean;
}

/** Une diapositive du carrousel d'ouverture. */
interface Diapositive {
  etiquette: string;
  /** Peut contenir une balise <span class="accent"> pour le mot mis en valeur. */
  titre: string;
  texte: string;
  action: string;
  lien: string;
  image: string;
  /** Cadrage de la photo dans sa carte (object-position). */
  cadrage: string;
  carteHaut: { titre: string; texte: string };
  carteBas: { icone: string; titre: string; texte: string };
}

/** Duree d'affichage d'une diapositive, en millisecondes. */
const DUREE_DIAPOSITIVE_MS = 6500;

interface Fonction {
  icone: string;
  titre: string;
  texte: string;
}

/**
 * Page publique de la plateforme : promesse, modules, offres et appel a creer
 * un compte entreprise. Le catalogue vient de l'API, pour qu'un module qui
 * devient disponible apparaisse sans toucher a cette page.
 * <p>
 * Les chiffres affiches decrivent le produit, jamais une clientele : rien
 * n'est annonce qui ne soit verifiable.
 */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, Apparition],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss'
})
export class Accueil implements OnInit, OnDestroy {
  private readonly service = inject(PlateformeService);
  private readonly auth = inject(AuthService);

  readonly nomPlateforme = environment.nomPlateforme;
  readonly presentation = PRESENTATION_MODULES;
  readonly annee = new Date().getFullYear();

  /** Un visiteur deja connecte rejoint directement son espace. */
  readonly connecte = this.auth.connecte;
  readonly accueilConnecte = () => this.auth.pageAccueil();

  readonly menuOuvert = signal(false);

  readonly duree = DUREE_DIAPOSITIVE_MS;
  readonly active = signal(0);
  /** Vrai pendant un survol ou une navigation au clavier : le defilement attend. */
  readonly enPause = signal(false);
  /** Incremente a chaque relance du minuteur : la barre de progression repart avec lui. */
  readonly cycle = signal(0);

  readonly diapositives: Diapositive[] = [
    {
      etiquette: 'Plateforme',
      titre: 'Simplifiez votre gestion.<br />Pilotez <span class="accent">votre entreprise</span><br />en ligne.',
      texte: "Une seule plateforme pour vos vehicules, vos equipes, vos finances et bientot votre point de vente — creee pour les entreprises d'Afrique de l'Ouest.",
      action: 'Creer mon compte gratuit',
      lien: '/inscription',
      image: '/howo.webp',
      cadrage: '45% 70%',
      carteHaut: { titre: 'Un seul compte', texte: 'pour toute votre equipe' },
      carteBas: { icone: '🧩', titre: 'Modules a la carte', texte: 'activez ce dont vous avez besoin' }
    },
    {
      etiquette: 'Gestion de parc',
      titre: 'Sachez <span class="accent">ou sont</span><br />vos vehicules,<br />a chaque instant.',
      texte: 'Carte du parc en temps reel, historique des trajets, zones et alertes de vitesse : votre flotte sous les yeux, ou que vous soyez.',
      action: 'Suivre ma flotte',
      lien: '/inscription',
      image: '/backlogin.jpg',
      cadrage: '60% 80%',
      carteHaut: { titre: 'Suivi GPS', texte: 'positions en temps reel' },
      carteBas: { icone: '🛰️', titre: 'Alertes et geofencing', texte: 'vitesse, zones, arrets' }
    },
    {
      etiquette: 'Rentabilite',
      titre: 'Combien rapporte<br /><span class="accent">chaque vehicule</span> ?<br />Enfin la reponse.',
      texte: 'Recettes, carburant, entretien et factures rattaches a chaque vehicule : marge, cout au kilometre et rapports PDF calcules pour vous.',
      action: 'Mesurer ma rentabilite',
      lien: '/inscription',
      image: '/siontruk.jpg',
      cadrage: '50% 60%',
      carteHaut: { titre: 'Facturation', texte: 'creances et reglements suivis' },
      carteBas: { icone: '📈', titre: 'Marge par vehicule', texte: 'calculee automatiquement' }
    }
  ];

  private minuterie?: ReturnType<typeof setInterval>;
  /** Un carrousel qui defile seul est une animation non sollicitee : on s'en abstient sur demande. */
  private readonly defilementAutorise =
    typeof window !== 'undefined' && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  readonly modules = signal<ModuleCatalogue[]>([]);

  /** Bandeau sous l'ecran d'ouverture : les metiers pour lesquels la plateforme est pensee. */
  readonly secteurs = [
    { icone: '🚛', nom: 'Transport' },
    { icone: '🔑', nom: 'Location' },
    { icone: '📦', nom: 'Logistique' },
    { icone: '🏗️', nom: 'BTP' },
    { icone: '🏪', nom: 'Commerce' },
    { icone: '🛒', nom: 'Distribution' }
  ];

  readonly chiffres = [
    { valeur: '1 min', libelle: 'pour creer votre espace' },
    { valeur: '100 %', libelle: 'donnees isolees par entreprise' },
    { valeur: '24/7', libelle: 'accessible en ligne' },
    { valeur: '0', libelle: 'installation a prevoir' }
  ];

  readonly offres: Offre[] = [
    {
      nom: 'Decouverte',
      prix: 'Gratuit',
      mention: 'pour demarrer',
      points: ['1 module au choix', "Jusqu'a 3 utilisateurs", 'Donnees isolees et sauvegardees', 'Assistance par email'],
      action: 'Commencer'
    },
    {
      nom: 'Pro',
      prix: 'Gestion de parc',
      mention: 'le module complet',
      points: ['Utilisateurs illimites', 'GPS temps reel et geofencing', 'Facturation et rentabilite', 'Rapports PDF', 'Assistance prioritaire'],
      action: 'Essayer maintenant',
      vedette: true
    },
    {
      nom: 'Entreprise',
      prix: 'Sur devis',
      mention: 'plusieurs modules',
      points: ['Tous les modules disponibles', 'Accompagnement a la prise en main', 'Reprise de vos donnees', 'Interlocuteur dedie'],
      action: 'Nous contacter'
    }
  ];

  readonly fonctions: Fonction[] = [
    { icone: '📊', titre: 'Tableaux de bord et rentabilite', texte: 'Chiffre d\'affaires, depenses et marge suivis en temps reel, vehicule par vehicule.' },
    { icone: '👥', titre: 'Equipe et droits d\'acces', texte: 'Administrateurs, gestionnaires et chauffeurs, chacun avec les droits de son role.' },
    { icone: '🧩', titre: 'Modules a la carte', texte: 'Activez ou retirez un module en un clic ; vos donnees sont conservees.' }
  ];

  ngOnInit(): void {
    // La page reste presentable sans l'API : seule la grille des modules attend la reponse.
    this.service.cataloguePublic().subscribe({
      next: (modules) => this.modules.set(modules),
      error: () => undefined
    });
    this.relancer();
    document.addEventListener('visibilitychange', this.surVisibilite);
  }

  ngOnDestroy(): void {
    this.arreter();
    document.removeEventListener('visibilitychange', this.surVisibilite);
  }

  allerA(index: number): void {
    const total = this.diapositives.length;
    this.active.set((index + total) % total);
    // Toute action manuelle repart d'une duree complete.
    this.relancer();
  }

  suivante(): void {
    this.allerA(this.active() + 1);
  }

  precedente(): void {
    this.allerA(this.active() - 1);
  }

  suspendre(): void {
    this.enPause.set(true);
    this.arreter();
  }

  reprendre(): void {
    this.enPause.set(false);
    this.relancer();
  }

  /** Un onglet en arriere-plan n'a personne pour regarder le carrousel. */
  private readonly surVisibilite = (): void => {
    if (document.hidden) {
      this.arreter();
    } else if (!this.enPause()) {
      this.relancer();
    }
  };

  private relancer(): void {
    this.arreter();
    this.cycle.update((c) => c + 1);
    if (!this.defilementAutorise || this.enPause() || document.hidden) {
      return;
    }
    this.minuterie = setInterval(
      () => {
        this.active.update((i) => (i + 1) % this.diapositives.length);
        this.cycle.update((c) => c + 1);
      },
      DUREE_DIAPOSITIVE_MS
    );
  }

  private arreter(): void {
    if (this.minuterie) {
      clearInterval(this.minuterie);
      this.minuterie = undefined;
    }
  }

  basculerMenu(): void {
    this.menuOuvert.update((ouvert) => !ouvert);
  }
}
