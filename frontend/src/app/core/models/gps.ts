/** Types de la phase 4 : geolocalisation, geofencing et alertes GPS. */
import { StatutCamion } from './models';

export interface MarqueurCarte {
  camionId: number;
  immatriculation: string;
  marque?: string;
  statut: StatutCamion;
  statutLibelle: string;
  chauffeurNom?: string;
  activite?: string;
  latitude: number;
  longitude: number;
  vitesseKmh?: number;
  direction?: number;
  derniereCommunication: string;
  horsConnexion: boolean;
}

export interface PositionGps {
  id: number;
  camionId: number;
  camionImmatriculation: string;
  latitude: number;
  longitude: number;
  vitesseKmh?: number;
  direction?: number;
  kilometrage?: number;
  moteurAllume?: boolean;
  horodatage: string;
  fournisseur?: string;
}

export interface Trajet {
  camionId: number;
  immatriculation: string;
  debut: string;
  fin: string;
  nombrePoints: number;
  distanceKm: number;
  vitesseMoyenne: number;
  vitesseMaximale: number;
  dureeMinutes: number;
  nombreArrets: number;
  points: PositionGps[];
}

export type TypeZone =
  | 'SIEGE'
  | 'DEPOT'
  | 'AGENCE'
  | 'ZONE_EXPLOITATION'
  | 'ZONE_INTERDITE'
  | 'CLIENT';

export interface ZoneGeofence {
  id: number;
  nom: string;
  type: TypeZone;
  typeLibelle: string;
  latitude: number;
  longitude: number;
  rayonMetres: number;
  alerteEntree: boolean;
  alerteSortie: boolean;
  dureeMaxMinutes?: number;
  couleur: string;
  observations?: string;
  actif: boolean;
}

export interface ZoneRequete {
  nom: string;
  type: TypeZone;
  latitude: number;
  longitude: number;
  rayonMetres: number;
  alerteEntree?: boolean;
  alerteSortie?: boolean;
  dureeMaxMinutes?: number | null;
  observations?: string;
  actif?: boolean;
}

export type TypeAlerteGps =
  | 'EXCES_VITESSE'
  | 'ENTREE_ZONE'
  | 'SORTIE_ZONE'
  | 'ZONE_INTERDITE'
  | 'STATIONNEMENT_PROLONGE'
  | 'MOUVEMENT_HORS_MISSION'
  | 'GPS_DECONNECTE'
  | 'HORS_HORAIRES';

export type GraviteAlerte = 'INFORMATION' | 'AVERTISSEMENT' | 'CRITIQUE';

export interface AlerteGps {
  id: number;
  camionId: number;
  camionImmatriculation: string;
  zoneId?: number;
  zoneNom?: string;
  type: TypeAlerteGps;
  typeLibelle: string;
  gravite: GraviteAlerte;
  graviteLibelle: string;
  message: string;
  latitude?: number;
  longitude?: number;
  vitesseKmh?: number;
  horodatage: string;
  traitee: boolean;
  traiteePar?: string;
  traiteeLe?: string;
}

export interface SyntheseAlertes {
  nonTraitees: number;
  excesVitesse: number;
  sortiesDeZone: number;
  zonesInterdites: number;
  gpsDeconnectes: number;
}

export const TYPES_ZONE: { valeur: TypeZone; libelle: string; couleur: string }[] = [
  { valeur: 'SIEGE', libelle: 'Siege', couleur: '#0f4c81' },
  { valeur: 'DEPOT', libelle: 'Depot', couleur: '#1e874b' },
  { valeur: 'AGENCE', libelle: 'Agence', couleur: '#1565c0' },
  { valeur: 'ZONE_EXPLOITATION', libelle: "Zone d'exploitation", couleur: '#f0a202' },
  { valeur: 'ZONE_INTERDITE', libelle: 'Zone interdite', couleur: '#c62828' },
  { valeur: 'CLIENT', libelle: 'Site client', couleur: '#6b3fa0' }
];

/** Couleurs des marqueurs, alignees sur la legende du chapitre 23. */
export const COULEURS_STATUT: Record<StatutCamion, string> = {
  DISPONIBLE: '#1e874b',
  EN_MISSION: '#1565c0',
  EN_LIVRAISON: '#1565c0',
  EN_LOCATION: '#f0a202',
  EN_MAINTENANCE: '#6b3fa0',
  IMMOBILISE: '#c62828',
  ACCIDENTE: '#c62828',
  HORS_SERVICE: '#6b7684',
  VENDU: '#6b7684'
};
