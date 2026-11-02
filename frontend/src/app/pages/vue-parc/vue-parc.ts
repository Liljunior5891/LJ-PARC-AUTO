import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LigneVueParc } from '../../core/models/exploitation';
import { LigneRentabilite } from '../../core/models/finance';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { COULEURS_STATUT_CAMION, StatutCamion } from '../../core/models/models';
import { TableauBordService } from '../../core/services/tableau-bord.service';
import { CamionService } from '../../core/services/camion.service';
import { DessinVehicule } from '../../shared/dessin-vehicule';
import { AuthService } from '../../core/auth/auth.service';
import { Camion } from '../../core/models/models';
import { FormulaireCamion } from '../../shared/formulaire-camion';
import { MarketplaceService } from '../../core/services/marketplace.service';

@Component({
  selector: 'app-vue-parc',
  imports: [FormsModule, RouterLink, DecimalPipe, FormulaireCamion, DessinVehicule],
  templateUrl: './vue-parc.html',
  styleUrl: './vue-parc.scss'
})
export class VueParc implements OnInit {
  private readonly service = inject(TableauBordService);
  private readonly rentabiliteService = inject(RentabiliteService);
  private readonly camionService = inject(CamionService);

  /** Creation de camion reservee aux profils qui gerent le parc. */
  readonly peutGerer = inject(AuthService).peutGererParc();

  // Gestion de parc avec Marketplace : vehicules proposes a la location en ligne.
  private readonly marketplace = inject(MarketplaceService);
  readonly avecEnLigne = inject(AuthService).aModule('MARKETPLACE');
  readonly enLigne = signal<Set<number>>(new Set());
  readonly filtreEnLigne = signal<'' | 'oui' | 'non'>('');

  readonly lignes = signal<LigneVueParc[]>([]);
  /** Camions archives : absents de la vue du parc, charges a la demande. */
  readonly archives = signal<LigneVueParc[]>([]);
  /** Rentabilite du mois en cours, indexee par camion (chapitre 5). */
  readonly rentabilite = signal<Map<number, LigneRentabilite>>(new Map());
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Fenetre de creation d'un camion, ouverte par-dessus la vue. */
  readonly creation = signal(false);

  readonly filtre = signal<Filtre>('tous');
  /** Recherche libre sur l'immatriculation, le chauffeur ou le client. */
  readonly recherche = signal('');
  /** Heure du dernier chargement, rappelee dans l'apercu. */
  readonly misAJour = signal<Date | null>(null);

  /** Options du filtre, avec la couleur de la carte d'apercu correspondante. */
  readonly filtres: { valeur: Filtre; libelle: string; couleur: string }[] = [
    { valeur: 'tous', libelle: 'Tout le parc', couleur: '#1565c0' },
    { valeur: 'engages', libelle: 'Engages', couleur: '#e07b00' },
    { valeur: 'disponibles', libelle: 'Disponibles', couleur: '#1e874b' },
    { valeur: 'archives', libelle: 'Archives', couleur: '#8a94a0' }
  ];

  /** Quatre chiffres de tete : effectif, activite, disponibilite, atelier. */
  readonly apercu = computed(() => {
    const lignes = this.lignes();
    const compter = (...statuts: StatutCamion[]) =>
      lignes.filter((ligne) => statuts.includes(ligne.statut)).length;
    const disponibles = compter('DISPONIBLE');
    return {
      total: lignes.length,
      engages: compter('EN_MISSION', 'EN_LOCATION', 'EN_LIVRAISON'),
      disponibles,
      tauxDisponibilite: lignes.length === 0 ? 0 : Math.round((disponibles / lignes.length) * 100),
      atelier: compter('EN_MAINTENANCE', 'IMMOBILISE', 'ACCIDENTE'),
      sansChauffeur: lignes.filter((ligne) => !ligne.chauffeurNom).length
    };
  });

  /** Camions filtres selon leur engagement commercial. */
  readonly lignesAffichees = computed(() => {
    const filtre = this.filtre();
    const recherche = this.recherche().trim().toLowerCase();
    const source = filtre === 'archives' ? this.archives() : this.lignes();
    return source.filter((ligne) => {
      if (recherche && ![ligne.immatriculation, ligne.chauffeurNom, ligne.clientNom, ligne.marqueModele]
        .some((texte) => texte?.toLowerCase().includes(recherche))) {
        return false;
      }
      const enLigne = this.filtreEnLigne();
      if (enLigne && (enLigne === 'oui') !== this.enLigne().has(ligne.camionId)) {
        return false;
      }
      if (filtre === 'engages') {
        return ligne.statut === 'EN_LOCATION' || ligne.statut === 'EN_MISSION'
          || ligne.statut === 'EN_LIVRAISON';
      }
      if (filtre === 'disponibles') {
        return ligne.statut === 'DISPONIBLE';
      }
      return true;
    });
  });

  ngOnInit(): void {
    this.charger();
    if (this.avecEnLigne) {
      this.marketplace.vehiculesEnLigne().subscribe({
        next: (liste) => this.enLigne.set(new Set(liste.filter((v) => v.publiee).map((v) => v.camionId)))
      });
    }
  }

  charger(): void {
    if (this.filtre() === 'archives') {
      this.chargerArchives();
    }
    this.chargement.set(true);
    this.rentabiliteService.global().subscribe({
      next: (global) =>
        this.rentabilite.set(new Map(global.camions.map((ligne) => [ligne.camionId, ligne])))
    });
    this.service.vueParc().subscribe({
      next: (lignes) => {
        this.lignes.set(lignes);
        this.misAJour.set(new Date());
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement de la vue du parc impossible.');
      }
    });
  }

  changerFiltre(valeur: Filtre): void {
    this.filtre.set(valeur);
    if (valeur === 'archives') {
      this.chargerArchives();
    }
  }

  /** Le nouveau camion rejoint le parc actif : on y revient pour le montrer. */
  camionCree(camion: Camion): void {
    this.creation.set(false);
    this.filtre.set('tous');
    this.recherche.set('');
    this.succes.set(`Vehicule ${camion.immatriculation} ajoute au parc.`);
    this.charger();
  }

  /** Les camions archives sortent de la vue du parc : on les lit dans le registre des camions. */
  private chargerArchives(): void {
    this.chargement.set(true);
    this.camionService.lister({ actif: false, size: 500 }).subscribe({
      next: (page) => {
        this.archives.set(page.content.map((camion) => ({
          camionId: camion.id,
          immatriculation: camion.immatriculation,
          marqueModele: [camion.marque, camion.modele].filter(Boolean).join(' ') || undefined,
          statut: camion.statut,
          statutLibelle: 'Archive',
          kilometrageActuel: camion.kilometrageActuel
        })));
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des vehicules archives impossible.');
      }
    });
  }

  /** Ligne de rentabilite du camion sur le mois en cours, si elle existe. */
  finances(camionId: number): LigneRentabilite | undefined {
    return this.rentabilite().get(camionId);
  }

  /** Part d'un effectif dans le parc, en pourcentage, pour les jauges de l'apercu. */
  part(nombre: number, total: number): number {
    return total === 0 ? 0 : Math.round((nombre / total) * 100);
  }

  /** Remplissage de la barre de marge, borne entre 0 et 100. */
  largeurMarge(marge: number): number {
    return Math.min(Math.max(Math.round(marge), 0), 100);
  }

  classeResultat(valeur?: number): string {
    if (valeur === undefined) {
      return '';
    }
    return valeur >= 0 ? 'resultat-positif' : 'resultat-negatif';
  }

  classeStatut(statut: StatutCamion): string {
    return 'etiquette ' + statut.toLowerCase();
  }

  /**
   * Couleur du bloc selon le statut du camion : c'est elle qui rend la vue
   * lisible d'un coup d'oeil. Elle reprend la legende de la carte du parc.
   */
  couleurStatut(statut: StatutCamion): string {
    return COULEURS_STATUT_CAMION[statut];
  }
}

/** Selection affichee : le parc actif, une partie de celui-ci, ou les archives. */
type Filtre = 'tous' | 'engages' | 'disponibles' | 'archives';
