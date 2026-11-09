/** Gestion de presence : employes, lieux, bornes, horaire et pointages. */

export interface LieuCourt {
  id: number;
  nom: string;
}

export interface EmployePresence {
  id: number;
  matricule: string;
  nom: string;
  prenom?: string;
  nomComplet: string;
  telephone?: string;
  email?: string;
  fonction?: string;
  service?: string;
  dateEmbauche?: string;
  utilisateurId?: number;
  utilisateurEmail?: string;
  actif: boolean;
  lieux: LieuCourt[];
}

export interface EmployeRequete {
  nom: string;
  prenom?: string | null;
  telephone?: string | null;
  email?: string | null;
  fonction?: string | null;
  service?: string | null;
  dateEmbauche?: string | null;
  lieuIds: number[];
  utilisateurId?: number | null;
  actif?: boolean;
}

/** Employe enregistre avec son PIN en clair : montre une seule fois. */
export interface EmployeAvecPin {
  employe: EmployePresence;
  pin: string;
}

export interface CompteConvertible {
  id: number;
  nom: string;
  prenom?: string;
  email: string;
  telephone?: string;
  roles: string[];
}

export interface BornePresence {
  id: number;
  lieuId: number;
  nom: string;
  appairee: boolean;
  codeAppairage?: string;
  codeExpireLe?: string;
  dernierContact?: string;
  actif: boolean;
}

export interface LieuPresence {
  id: number;
  nom: string;
  adresse?: string;
  latitude?: number;
  longitude?: number;
  rayonMetres: number;
  jetonPublic: string;
  actif: boolean;
  responsableIds: number[];
  employes: number;
  bornes: BornePresence[];
}

export interface LieuRequete {
  nom: string;
  adresse?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  rayonMetres: number;
  responsableIds: number[];
  actif: boolean;
}

export interface ReglagePresence {
  heureArrivee: string;
  toleranceMinutes: number;
  /** 1 = lundi ... 7 = dimanche. */
  joursTravailles: number[];
  heureLimite: string;
}

export type StatutPresence = 'A_L_HEURE' | 'EN_RETARD' | 'PAS_ENCORE' | 'ABSENT' | 'REPOS' | 'NON_EMBAUCHE';
export type ModePointage = 'TELEPHONE' | 'BORNE' | 'MANUEL';

export const LIBELLES_STATUT_PRESENCE: Record<StatutPresence, string> = {
  A_L_HEURE: 'A l\'heure',
  EN_RETARD: 'En retard',
  PAS_ENCORE: 'Pas encore arrive',
  ABSENT: 'Absent',
  REPOS: 'Repos',
  NON_EMBAUCHE: '—'
};

/** Teinte de l'etiquette commune (.etiquette) pour chaque etat : vert, ambre, bleu, rouge ou gris. */
export const ETIQUETTES_STATUT_PRESENCE: Record<StatutPresence, string> = {
  A_L_HEURE: 'disponible',
  EN_RETARD: 'en_location',
  PAS_ENCORE: 'en_mission',
  ABSENT: 'inactif',
  REPOS: '',
  NON_EMBAUCHE: ''
};

export const LIBELLES_MODE_POINTAGE: Record<ModePointage, string> = {
  TELEPHONE: 'Telephone',
  BORNE: 'Borne',
  MANUEL: 'Saisie manuelle'
};

export interface LigneJour {
  employeId: number;
  matricule: string;
  nom: string;
  fonction?: string;
  statut: StatutPresence;
  arriveeLe?: string;
  retardMinutes: number;
  apresLimite: boolean;
  lieu?: string;
  mode?: ModePointage;
  distanceMetres?: number;
}

export interface JourPresence {
  date: string;
  jourTravaille: boolean;
  heureArrivee: string;
  toleranceMinutes: number;
  heureLimite: string;
  compteurs: { attendus: number; presents: number; aLHeure: number; enRetard: number; pasEncore: number; absents: number };
  lignes: LigneJour[];
}

export interface LigneHistorique {
  date: string;
  statut: StatutPresence;
  arriveeLe?: string;
  retardMinutes: number;
  apresLimite: boolean;
  lieu?: string;
  mode?: ModePointage;
  distanceMetres?: number;
}

export interface HistoriquePresence {
  employeId: number;
  nom: string;
  matricule: string;
  fonction?: string;
  mois: string;
  totaux: { joursAttendus: number; presents: number; retards: number; minutesRetard: number; absences: number; ponctualite: number };
  jours: LigneHistorique[];
}

export interface ResultatPointage {
  issue: 'ENREGISTRE' | 'DEJA_POINTE';
  nom: string;
  matricule: string;
  arriveeLe: string;
  retardMinutes: number;
  apresLimite: boolean;
  lieu?: string;
  message: string;
}

/** Duree lisible : « 15 min », « 1 h 05 ». */
export function dureeMinutes(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }
  return `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
}
