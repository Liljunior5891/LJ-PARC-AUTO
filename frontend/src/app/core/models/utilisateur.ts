/** Comptes utilisateurs et profils (chapitre 40). */
import { Role } from './models';

export interface Utilisateur {
  id: number;
  email: string;
  nom: string;
  prenom?: string;
  nomComplet: string;
  telephone?: string;
  actif: boolean;
  roles: Role[];
  rolesLibelles: string[];
  creeLe?: string;
  modifieLe?: string;
}

export interface CreationUtilisateur {
  email: string;
  nom: string;
  prenom?: string;
  telephone?: string;
  roles: Role[];
  motDePasse: string;
}

export interface ModificationUtilisateur {
  email: string;
  nom: string;
  prenom?: string;
  telephone?: string;
  roles: Role[];
}

export interface ChangementMotDePasse {
  motDePasseActuel: string;
  nouveauMotDePasse: string;
}

export interface ProfilRole {
  valeur: Role;
  libelle: string;
}

/** Longueur minimale imposee par l'API. */
export const LONGUEUR_MOT_DE_PASSE = 8;
