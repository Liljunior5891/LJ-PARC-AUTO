/** Types de la phase 5 : entretiens, pneumatiques et incidents. */

export type TypeEntretien =
  | 'VIDANGE'
  | 'FILTRES'
  | 'FREINS'
  | 'COURROIE'
  | 'REVISION_GENERALE'
  | 'VISITE_TECHNIQUE'
  | 'REPARATION'
  | 'CARROSSERIE'
  | 'AUTRE';

export type StatutEntretien = 'PLANIFIE' | 'EN_COURS' | 'REALISE' | 'ANNULE';

export interface Entretien {
  id: number;
  numero: string;
  camionId: number;
  camionImmatriculation: string;
  camionKilometrage: number;
  depenseId?: number;
  depenseNumero?: string;
  type: TypeEntretien;
  typeLibelle: string;
  statut: StatutEntretien;
  statutLibelle: string;
  libelle: string;
  datePrevue?: string;
  dateRealisation?: string;
  kilometragePrevu?: number;
  kilometrageRealise?: number;
  kilometresRestants?: number;
  joursRestants?: number;
  echeanceProche: boolean;
  enRetard: boolean;
  periodiciteKm?: number;
  periodiciteJours?: number;
  cout?: number;
  fournisseur?: string;
  intervenant?: string;
  piecesRemplacees?: string;
  immobilisationJours?: number;
  observations?: string;
}

export interface EntretienRequete {
  camionId: number;
  type: TypeEntretien;
  libelle: string;
  datePrevue?: string | null;
  kilometragePrevu?: number | null;
  periodiciteKm?: number | null;
  periodiciteJours?: number | null;
  fournisseur?: string;
  intervenant?: string;
  observations?: string;
}

export interface RealisationEntretien {
  dateRealisation: string;
  kilometrageRealise: number;
  cout?: number | null;
  fournisseur?: string;
  piecesRemplacees?: string;
  immobilisationJours?: number | null;
  observations?: string;
  genererDepense?: boolean;
  planifierSuivant?: boolean;
}

export type StatutPneu = 'EN_STOCK' | 'MONTE' | 'DEPOSE' | 'REBUTE';
export type EtatPneu = 'NEUF' | 'BON' | 'USE' | 'A_REMPLACER' | 'HORS_USAGE';

export interface Pneu {
  id: number;
  reference: string;
  camionId?: number;
  camionImmatriculation?: string;
  marque?: string;
  dimension?: string;
  numeroSerie?: string;
  position?: string;
  statut: StatutPneu;
  statutLibelle: string;
  etat: EtatPneu;
  etatLibelle: string;
  dateAchat?: string;
  prixAchat?: number;
  dateMontage?: string;
  kilometrageMontage?: number;
  dateDepose?: string;
  kilometrageDepose?: number;
  dureeViePrevueKm?: number;
  kilometresParcourus?: number;
  usurePourcent?: number;
  aRemplacer: boolean;
  coutParKm?: number;
  motifDepose?: string;
  observations?: string;
}

export interface PneuRequete {
  reference: string;
  marque?: string;
  dimension?: string;
  numeroSerie?: string;
  dateAchat?: string | null;
  prixAchat?: number | null;
  dureeViePrevueKm?: number | null;
  etat?: EtatPneu;
  observations?: string;
}

export type TypeIncident =
  | 'PANNE'
  | 'ACCIDENT'
  | 'CREVAISON'
  | 'PROBLEME_MECANIQUE'
  | 'RETARD'
  | 'VOL'
  | 'CONTROLE_ROUTIER'
  | 'AUTRE';

export type GraviteIncident = 'FAIBLE' | 'MOYENNE' | 'ELEVEE' | 'CRITIQUE';

export type StatutIncident =
  | 'DECLARE'
  | 'PRIS_EN_CHARGE'
  | 'EN_REPARATION'
  | 'RESOLU'
  | 'CLOS';

export interface Incident {
  id: number;
  numero: string;
  camionId: number;
  camionImmatriculation: string;
  chauffeurId?: number;
  chauffeurNom?: string;
  missionId?: number;
  missionNumero?: string;
  entretienId?: number;
  entretienNumero?: string;
  type: TypeIncident;
  typeLibelle: string;
  gravite: GraviteIncident;
  graviteLibelle: string;
  statut: StatutIncident;
  statutLibelle: string;
  description: string;
  lieu?: string;
  latitude?: number;
  longitude?: number;
  photoUrl?: string;
  survenuLe: string;
  resoluLe?: string;
  delaiResolutionHeures?: number;
  resolution?: string;
  coutEstime?: number;
  immobilisant: boolean;
  ouvert: boolean;
}

export interface IncidentRequete {
  camionId: number;
  chauffeurId?: number | null;
  missionId?: number | null;
  type: TypeIncident;
  gravite?: GraviteIncident;
  description: string;
  lieu?: string;
  latitude?: number | null;
  longitude?: number | null;
  photoUrl?: string;
  coutEstime?: number | null;
  immobilisant?: boolean;
}

export const TYPES_ENTRETIEN: { valeur: TypeEntretien; libelle: string }[] = [
  { valeur: 'VIDANGE', libelle: 'Vidange' },
  { valeur: 'FILTRES', libelle: 'Remplacement des filtres' },
  { valeur: 'FREINS', libelle: 'Revision des freins' },
  { valeur: 'COURROIE', libelle: 'Courroie de distribution' },
  { valeur: 'REVISION_GENERALE', libelle: 'Revision generale' },
  { valeur: 'VISITE_TECHNIQUE', libelle: 'Visite technique' },
  { valeur: 'REPARATION', libelle: 'Reparation' },
  { valeur: 'CARROSSERIE', libelle: 'Carrosserie' },
  { valeur: 'AUTRE', libelle: 'Autre intervention' }
];

export const ETATS_PNEU: { valeur: EtatPneu; libelle: string }[] = [
  { valeur: 'NEUF', libelle: 'Neuf' },
  { valeur: 'BON', libelle: 'Bon etat' },
  { valeur: 'USE', libelle: 'Use' },
  { valeur: 'A_REMPLACER', libelle: 'A remplacer' },
  { valeur: 'HORS_USAGE', libelle: 'Hors usage' }
];

export const TYPES_INCIDENT: { valeur: TypeIncident; libelle: string }[] = [
  { valeur: 'PANNE', libelle: 'Panne' },
  { valeur: 'ACCIDENT', libelle: 'Accident' },
  { valeur: 'CREVAISON', libelle: 'Crevaison' },
  { valeur: 'PROBLEME_MECANIQUE', libelle: 'Probleme mecanique' },
  { valeur: 'RETARD', libelle: 'Retard' },
  { valeur: 'VOL', libelle: 'Vol ou tentative de vol' },
  { valeur: 'CONTROLE_ROUTIER', libelle: 'Controle routier' },
  { valeur: 'AUTRE', libelle: 'Autre incident' }
];

export const GRAVITES_INCIDENT: { valeur: GraviteIncident; libelle: string }[] = [
  { valeur: 'FAIBLE', libelle: 'Faible' },
  { valeur: 'MOYENNE', libelle: 'Moyenne' },
  { valeur: 'ELEVEE', libelle: 'Elevee' },
  { valeur: 'CRITIQUE', libelle: 'Critique' }
];

export const STATUTS_INCIDENT: { valeur: StatutIncident; libelle: string }[] = [
  { valeur: 'DECLARE', libelle: 'Declare' },
  { valeur: 'PRIS_EN_CHARGE', libelle: 'Pris en charge' },
  { valeur: 'EN_REPARATION', libelle: 'En reparation' },
  { valeur: 'RESOLU', libelle: 'Resolu' },
  { valeur: 'CLOS', libelle: 'Clos' }
];
