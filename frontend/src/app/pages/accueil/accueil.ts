import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { VitrineSociete } from '../../core/models/parametrage';
import { SocieteService } from '../../core/services/societe.service';
import { Apparition } from '../../shared/apparition';

/** Une prestation presentee aux visiteurs. */
interface Prestation {
  icone: string;
  titre: string;
  texte: string;
  points: string[];
}

/** Un vehicule type de la flotte, illustre par un detail d'une photo du parc. */
interface VehiculeFlotte {
  nom: string;
  usage: string;
  /** Photo d'origine et cadrage horizontal isolant le vehicule concerne. */
  image: string;
  cadrage: string;
  agrandissement: string;
}

/** Un ecran du carrousel d'ouverture. */
interface Diapositive {
  image: string;
  /** Vrai pour les visuels de faible definition, adoucis a l'agrandissement. */
  adoucir?: boolean;
  surtitre: string;
  titre: string;
  accroche: string;
  action: string;
  ancre: string;
}

/** Duree d'affichage d'une diapositive, en millisecondes. */
const DUREE_DIAPOSITIVE_MS = 6500;

/**
 * Site vitrine, page unique et publique (aucune authentification).
 * <p>
 * Le contenu editorial est fige ici ; seules l'identite et les coordonnees
 * viennent du parametrage, pour qu'un changement de raison sociale ou de
 * telephone se repercute sans toucher au code.
 */
@Component({
  selector: 'app-accueil',
  imports: [RouterLink, Apparition],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss'
})
export class Accueil implements OnInit, OnDestroy {
  private readonly societeService = inject(SocieteService);
  private readonly auth = inject(AuthService);

  readonly societe = signal<VitrineSociete | null>(null);
  readonly menuOuvert = signal(false);

  /** Un visiteur deja connecte rejoint directement son espace. */
  readonly connecte = this.auth.connecte;

  readonly annee = new Date().getFullYear();

  /** Le nom parametre remplace le jeton {nom} des textes du carrousel. */
  readonly diapositivesAffichees = computed(() =>
    this.diapositives.map((diapo) => ({
      ...diapo,
      surtitre: diapo.surtitre.replace('{nom}', this.societe()?.nom ?? 'PARC AUTO'),
      accroche: diapo.accroche.replace('{nom}', this.societe()?.nom ?? 'notre societe')
    }))
  );

  readonly diapositives: Diapositive[] = [
    {
      image: '/backlogin.jpg',
      surtitre: '{nom}',
      titre: "Votre marchandise part a l'heure,<br />et vous savez ou elle est.",
      accroche:
        '{nom} met a votre disposition une flotte entretenue, des chauffeurs professionnels et un suivi de chaque mission, du chargement jusqu’a la livraison.',
      action: 'Demander un devis',
      ancre: '#contact'
    },
    {
      image: '/howo.webp',
      surtitre: 'Une flotte pour chaque chantier',
      titre: 'Du tracteur routier<br />a la benne de chantier.',
      accroche:
        'Tracteurs, porteurs, bennes et fourgons frigorifiques : nous engageons le vehicule qui correspond a votre chargement, pas celui qui reste disponible.',
      action: 'Voir nos services',
      ancre: '#services'
    },
    {
      image: '/siontruk.jpg',
      adoucir: true,
      surtitre: 'Disponibles jour et nuit',
      titre: 'Les delais ne tiennent pas<br />aux horaires de bureau.',
      accroche:
        'Departs matinaux, trajets de nuit, tournees continues : nos affectations sont organisees pour tenir la date que vous nous avez donnee.',
      action: 'Nous contacter',
      ancre: '#contact'
    }
  ];

  readonly prestations: Prestation[] = [
    {
      icone: '🚛',
      titre: 'Transport de marchandises',
      texte:
        "Acheminement national et sous-regional, du chargement isole au trafic regulier, avec des vehicules adaptes a la nature de votre fret.",
      points: ['Porteurs, remorques et semi-remorques', 'Chauffeurs professionnels', 'Lettre de voiture et bon de livraison']
    },
    {
      icone: '🔑',
      titre: 'Location de vehicules',
      texte:
        "Mise a disposition de vehicules a la journee, au mois ou sur contrat de longue duree, avec ou sans chauffeur, selon vos pics d'activite.",
      points: ['Avec ou sans chauffeur', 'Contrats courts ou longue duree', 'Entretien et assurance a notre charge']
    },
    {
      icone: '📦',
      titre: 'Livraison et distribution',
      texte:
        "Organisation des tournees, distribution multi-points et remise des justificatifs signes a chaque etape de la chaine.",
      points: ['Tournees planifiees', 'Preuve de livraison', 'Traitement des ecarts et retours']
    },
    {
      icone: '📍',
      titre: 'Suivi et tracabilite',
      texte:
        "Chaque vehicule est equipe d'un traceur : position, itineraire, arrets et respect des zones sont enregistres et restituables.",
      points: ['Position en temps reel', 'Historique des trajets', 'Alertes vitesse et zones']
    }
  ];

  /**
   * Presentation de la flotte. Faute de photo individuelle par vehicule, chaque
   * carte cadre un camion precis des visuels panoramiques du parc.
   */
  readonly flotte: VehiculeFlotte[] = [
    {
      nom: 'Tracteur routier',
      usage: 'Semi-remorques sur longue distance, national et sous-regional.',
      image: '/howo.webp',
      cadrage: '13% 72%',
      agrandissement: '500%'
    },
    {
      nom: 'Tracteur grand routier',
      usage: 'Cabine couchette pour les liaisons de plusieurs jours.',
      image: '/howo.webp',
      cadrage: '31% 72%',
      agrandissement: '500%'
    },
    {
      nom: 'Porteur plateau',
      usage: 'Charges palettisees, materiaux longs et colis hors gabarit.',
      image: '/howo.webp',
      cadrage: '49% 90%',
      agrandissement: '330%'
    },
    {
      nom: 'Benne de chantier',
      usage: 'Sable, gravier, laterite et deblais de chantier.',
      image: '/howo.webp',
      cadrage: '73% 81%',
      agrandissement: '400%'
    },
    {
      nom: 'Fourgon frigorifique',
      usage: 'Denrees perissables et produits sous temperature dirigee.',
      image: '/howo.webp',
      cadrage: '91% 72%',
      agrandissement: '500%'
    },
    {
      nom: 'Camionnette de distribution',
      usage: 'Tournees urbaines et livraisons du dernier kilometre.',
      image: '/backlogin.jpg',
      cadrage: '100% 85%',
      agrandissement: '500%'
    }
  ];

  /** Chaque engagement porte sa couleur, pour animer la bande sous le carrousel. */
  readonly engagements = [
    {
      icone: '⏱️',
      couleur: '#1565c0',
      titre: 'Ponctualite',
      texte: "Des delais annonces a la commande et tenus, avec information immediate en cas d'alea."
    },
    {
      icone: '📍',
      couleur: '#6b3fa0',
      titre: 'Tracabilite',
      texte: 'Chaque mouvement est horodate et documente, du depart au dechargement.'
    },
    {
      icone: '🛡️',
      couleur: '#1e874b',
      titre: 'Securite',
      texte: 'Flotte entretenue selon un plan de maintenance suivi et conducteurs formes.'
    },
    {
      icone: '🧾',
      couleur: '#f0a202',
      titre: 'Transparence',
      texte: 'Une facturation detaillee, adossee aux missions realisees et aux justificatifs.'
    }
  ];

  readonly etapes = [
    { numero: '01', titre: 'Votre demande', texte: 'Vous nous decrivez le fret, le trajet et les dates ; nous repondons par une offre chiffree.' },
    { numero: '02', titre: 'Affectation', texte: 'Nous engageons le vehicule adapte et le chauffeur, et vous confirmons la mission.' },
    { numero: '03', titre: 'Execution suivie', texte: "Le vehicule est suivi jusqu'a la livraison, et vous recevez les documents de fin de mission." }
  ];

  readonly diapositiveActive = signal(0);

  /** Vrai des que la page a quitte le haut : l'entete se densifie alors. */
  readonly enteteCompacte = signal(false);

  @HostListener('window:scroll')
  surDefilement(): void {
    this.enteteCompacte.set(window.scrollY > 40);
  }

  private minuterie?: ReturnType<typeof setInterval>;
  private defilementAutomatique = true;

  ngOnInit(): void {
    // La page reste presentable si l'API ne repond pas : les libelles de repli
    // du gabarit prennent alors le relais.
    this.societeService.chargerVitrine().subscribe({
      next: (vitrine) => this.societe.set(vitrine),
      error: () => undefined
    });

    // Un carrousel qui defile seul est une animation non sollicitee : on s'en
    // abstient si l'utilisateur a demande a les reduire.
    this.defilementAutomatique = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.relancerDefilement();
    document.addEventListener('visibilitychange', this.surVisibilite);
  }

  ngOnDestroy(): void {
    this.arreterDefilement();
    document.removeEventListener('visibilitychange', this.surVisibilite);
  }

  /** Un onglet en arriere-plan n'a personne pour regarder le carrousel. */
  private readonly surVisibilite = (): void => {
    if (document.hidden) {
      this.arreterDefilement();
    } else {
      this.relancerDefilement();
    }
  };

  allerA(index: number): void {
    const total = this.diapositives.length;
    this.diapositiveActive.set((index + total) % total);
    // Toute action manuelle reporte le changement automatique suivant.
    this.relancerDefilement();
  }

  suivante(): void {
    this.allerA(this.diapositiveActive() + 1);
  }

  precedente(): void {
    this.allerA(this.diapositiveActive() - 1);
  }

  /** Le survol suspend le defilement, pour laisser le temps de lire. */
  suspendre(): void {
    this.arreterDefilement();
  }

  reprendre(): void {
    this.relancerDefilement();
  }

  private relancerDefilement(): void {
    this.arreterDefilement();
    if (!this.defilementAutomatique || document.hidden) {
      return;
    }
    this.minuterie = setInterval(
      () => this.diapositiveActive.update((index) => (index + 1) % this.diapositives.length),
      DUREE_DIAPOSITIVE_MS
    );
  }

  private arreterDefilement(): void {
    if (this.minuterie) {
      clearInterval(this.minuterie);
      this.minuterie = undefined;
    }
  }

  get nom(): string {
    return this.societe()?.nom || 'PARC AUTO';
  }

  /** Adresse postale complete, assemblee a partir des champs renseignes. */
  get adresseComplete(): string | null {
    const s = this.societe();
    if (!s) {
      return null;
    }
    const morceaux = [s.adresse, s.ville, s.pays].filter((m) => !!m && m.trim().length > 0);
    return morceaux.length > 0 ? morceaux.join(', ') : null;
  }

  /** Vrai tant qu'aucune coordonnee n'est renseignee dans le parametrage. */
  get sansCoordonnees(): boolean {
    const s = this.societe();
    return !this.adresseComplete && !s?.telephone && !s?.email && !s?.siteWeb;
  }

  basculerMenu(): void {
    this.menuOuvert.update((ouvert) => !ouvert);
  }

  fermerMenu(): void {
    this.menuOuvert.set(false);
  }
}
