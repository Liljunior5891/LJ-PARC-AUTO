/** Types de la phase 6 : KPI, fiche synthetique, rapports et notifications. */
import { LigneCategorieDepense } from './finance';
import { StatutCamion } from './models';

export interface Kpi {
  debut: string;
  fin: string;
  nombreCamions: number;
  camionsActifs: number;
  tauxUtilisationPourcent: number;
  tauxDisponibilitePourcent: number;
  joursImmobilisation: number;
  chiffreAffaires: number;
  depenses: number;
  resultat: number;
  margePourcent: number;
  recetteMoyenneParCamion: number;
  depenseMoyenneParCamion: number;
  coutParKm: number;
  revenuParKm: number;
  coutMaintenance: number;
  kilometresParcourus: number;
  nombreMissions: number;
  consommationMoyenneL100km: number;
}

export interface FicheSynthese {
  camionId: number;
  immatriculation: string;
  marqueModele?: string;
  statut: StatutCamion;
  statutLibelle: string;
  debut: string;
  fin: string;
  chauffeurNom?: string;
  clientNom?: string;
  activite?: string;
  localisation?: string;
  dernierePositionLe?: string;
  kilometrageActuel: number;
  kilometresSurPeriode: number;
  nombreMissions: number;
  recettes: number;
  depenses: number;
  resultat: number;
  margePourcent: number;
  coutParKm?: number;
  revenuParKm?: number;
  depensesParCategorie: LigneCategorieDepense[];
  consommationMoyenneL100km?: number;
  coutCarburantParKm?: number;
  entretiensAVenir: number;
  entretiensEnRetard: number;
  pneusARemplacer: number;
  incidentsOuverts: number;
  documentsAExpirer: number;
  alertesGpsNonTraitees: number;
}

export type NiveauNotification = 'CRITIQUE' | 'AVERTISSEMENT' | 'INFORMATION';

export interface Notification {
  niveau: NiveauNotification;
  categorie: string;
  message: string;
  cible: string;
  refId?: number;
  horodatage?: string;
}

export interface SyntheseNotifications {
  total: number;
  critiques: number;
  avertissements: number;
  calculeLe: string;
}

export interface RapportDisponible {
  valeur: string;
  libelle: string;
}

/** Entree du catalogue des rapports PDF (chapitre 35). */
export interface RapportPdfDisponible {
  valeur: string;
  libelle: string;
  description: string;
  /** Vrai si le rapport peut etre restreint a une selection de camions. */
  selectionCamions: boolean;
}

/** Point de la serie mensuelle recettes / depenses (remarque 6). */
export interface EvolutionMensuelle {
  mois: string;
  moisLibelle: string;
  annee: number;
  recettes: number;
  depenses: number;
  resultat: number;
}

/**
 * Palette categorielle des graphiques.
 * Ordre fixe, jamais recycle : une categorie garde sa couleur quels que soient
 * les filtres. Valide par scripts/validate_palette.js (bande de clarte, chroma,
 * separation daltonisme, contraste).
 */
export const PALETTE_CATEGORIELLE = [
  '#2178b8',
  '#e07b00',
  '#8455c8',
  '#1e874b',
  '#d94a4a',
  '#0f9c9c',
  '#a8791f'
];

/** Recettes et depenses : deux mesures opposees, deux teintes de la palette. */
export const COULEUR_RECETTES = '#1e874b';
export const COULEUR_DEPENSES = '#d94a4a';
