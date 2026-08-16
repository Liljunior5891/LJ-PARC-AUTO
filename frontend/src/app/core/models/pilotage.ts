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
