/** Plateforme SaaS : modules, inscription et console de l'exploitant. */
import { CodeModule, Role } from './models';

export interface ModuleCatalogue {
  code: CodeModule;
  libelle: string;
  description: string;
  /** Faux tant que le module est en preparation : il est annonce mais non activable. */
  disponible: boolean;
  /** Active pour l'entreprise connectee ; toujours faux pour un visiteur. */
  actif: boolean;
  /** Type d'activite retenu par l'entreprise, pour un module qui se decline. */
  variante?: string | null;
  variantes: VarianteModule[];
}

/** Declinaison d'un module : le point de vente d'une boutique ou d'un restaurant. */
export interface VarianteModule {
  code: string;
  libelle: string;
  description: string;
}

/** Icone de chaque type d'activite dans les ecrans de choix. */
export const ICONES_VARIANTES: Record<string, string> = {
  BOUTIQUE: '🛍️',
  RESTAURATION: '🍽️'
};

/** Presentation des modules dans l'interface : icone et page d'entree. */
export const PRESENTATION_MODULES: Record<CodeModule, { icone: string; accueil: string }> = {
  GESTION_PARC: { icone: '🚛', accueil: '/tableau-bord' },
  POINT_DE_VENTE: { icone: '🧾', accueil: '/pdv/tableau-bord' },
  MARKETPLACE: { icone: '🛒', accueil: '/marketplace/commandes' }
};

/**
 * Profils admis dans chaque module, comme cote serveur : un caissier ne voit
 * pas le parc et un chauffeur ne voit pas la caisse.
 */
export const PROFILS_MODULES: Record<CodeModule, Role[]> = {
  GESTION_PARC: ['ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR'],
  POINT_DE_VENTE: ['ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR', 'PREPARATEUR'],
  MARKETPLACE: ['ADMIN', 'GERANT', 'GESTIONNAIRE_PARC']
};

export interface DemandeInscription {
  nomEntreprise: string;
  pays?: string;
  telephone?: string;
  nom: string;
  prenom?: string;
  email: string;
  motDePasse: string;
  modules: CodeModule[];
  variantes?: Partial<Record<CodeModule, string>>;
}

export type StatutEntreprise = 'ACTIVE' | 'SUSPENDUE';

export interface EntrepriseResume {
  id: number;
  nom: string;
  pays?: string;
  email?: string;
  telephone?: string;
  statut: StatutEntreprise;
  statutLibelle: string;
  creeLe?: string;
  nombreUtilisateurs: number;
  modules: CodeModule[];
}

export interface StatistiquesPlateforme {
  entreprises: number;
  entreprisesActives: number;
  utilisateurs: number;
  activationsParModule: Record<CodeModule, number>;
}
