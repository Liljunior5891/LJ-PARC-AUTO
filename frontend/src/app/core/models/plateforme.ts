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
  RESTAURATION: '🍽️',
  MATERIAUX: '🧱'
};

/** Presentation des modules dans l'interface : icone, page d'entree et fonctionnalites. */
export const PRESENTATION_MODULES: Record<CodeModule, { icone: string; accueil: string; fonctionnalites: string[] }> = {
  GESTION_PARC: {
    icone: '🚛',
    accueil: '/tableau-bord',
    fonctionnalites: [
      'Tableau de bord, indicateurs de direction et rentabilite par vehicule',
      'Parc de vehicules, affectations et documents avec echeances',
      'Maintenance : entretiens, pannes et atelier',
      'Carte GPS : positions et trajets du jour',
      'Chauffeurs, clients, contrats de location, missions et livraisons',
      'Recettes, depenses et carburant rattaches a chaque vehicule',
      'Facturation et reglements, rapports PDF'
    ]
  },
  POINT_DE_VENTE: {
    icone: '🧾',
    accueil: '/pdv/tableau-bord',
    fonctionnalites: [
      'Caisse, ouverture et fermeture de session, mouvements d\'especes',
      'Produits, categories, photos et codes-barres',
      'Stock : receptions, sorties, pertes, commandes fournisseurs et inventaire',
      'Historique des ventes, reimpression et annulations',
      'Tableau de bord : chiffre du jour, marge et meilleures ventes',
      'Rapports, exports et imports (Excel, PDF, CSV)'
    ]
  },
  MARKETPLACE: {
    icone: '🛒',
    accueil: '/marketplace/commandes',
    fonctionnalites: [
      'Boutique en ligne : vitrine, livraison, paiement et lien a partager',
      'Place de marche commune aux entreprises',
      'Publication de produits et de vehicules a louer',
      'Commandes payees au retrait, a la livraison ou par mobile money',
      'Demandes de location a accepter ou refuser',
      'Achat sans compte, avec un lien de suivi pour le client'
    ]
  },
  AGENCES: {
    icone: '🏦',
    accueil: '/agences/tableau-bord',
    fonctionnalites: [
      'Plusieurs agences, gerees chacune a part et centralisees chez l\'administrateur',
      'Mobile Money, transferts d\'argent, services financiers, Rapid Transfer et cartes prepayees',
      'Services, reseaux et operations parametrables, avec leur effet sur le cash et le virtuel',
      'Soldes cash et virtuels tires d\'un registre de mouvements que personne ne peut modifier',
      'Tresorerie disponible par agence et pour toute l\'entreprise',
      'Droits du superviseur et du caissier reglables, journal d\'audit'
    ]
  }
};

/** Ce qu'ajoute chaque type d'activite aux fonctionnalites du module. */
export const FONCTIONNALITES_VARIANTES: Record<string, string[]> = {
  BOUTIQUE: ['Caisse rapide au comptoir : douchette, panier et recu'],
  RESTAURATION: [
    'Plan de salle, commandes par table et additions',
    'Ecrans de preparation en cuisine et au bar',
    'Commande des clients par code QR depuis leur table'
  ],
  MATERIAUX: [
    'Comptes clients, pieces justificatives, vente a credit et acomptes',
    'Devis et facture A4 avec TVA',
    'Vente sous plusieurs unites : sac, tonne, botte, carton',
    'Reglages du credit par l\'administrateur'
  ]
};

/**
 * Profils admis dans chaque module, comme cote serveur : un caissier ne voit
 * pas le parc et un chauffeur ne voit pas la caisse.
 */
export const PROFILS_MODULES: Record<CodeModule, Role[]> = {
  GESTION_PARC: ['ADMIN', 'GESTIONNAIRE_PARC', 'CHAUFFEUR'],
  POINT_DE_VENTE: ['ADMIN', 'GERANT', 'CAISSIER', 'SERVEUR', 'PREPARATEUR', 'MAGASINIER', 'ASSOCIE'],
  MARKETPLACE: ['ADMIN', 'GERANT', 'GESTIONNAIRE_PARC'],
  AGENCES: ['ADMIN', 'SUPERVISEUR_AGENCE', 'CAISSIER_AGENCE']
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
  /** Module Agences : plusieurs agences autorisees (une seule par defaut). */
  agencesMultiples?: boolean;
}

export interface StatistiquesPlateforme {
  entreprises: number;
  entreprisesActives: number;
  utilisateurs: number;
  activationsParModule: Record<CodeModule, number>;
}
