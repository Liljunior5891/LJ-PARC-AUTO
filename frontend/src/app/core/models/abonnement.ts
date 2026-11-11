/** Abonnements SaaS : formules (prix par module), paiements declares puis valides. */
import { CodeModule } from './models';

/** Modules facturables, dans l'ordre d'affichage des grilles de prix. */
export const MODULES_FACTURABLES: { code: CodeModule; libelle: string }[] = [
  { code: 'GESTION_PARC', libelle: 'Gestion de parc' },
  { code: 'POINT_DE_VENTE', libelle: 'Point de vente' },
  { code: 'MARKETPLACE', libelle: 'Marketplace' },
  { code: 'AGENCES', libelle: 'Agences multiservices' },
  { code: 'PRESENCE', libelle: 'Gestion de presence' }
];

export type Periodicite = 'MENSUEL' | 'TRIMESTRIEL' | 'SEMESTRIEL' | 'ANNUEL';
export type MoyenPaiementAbonnement = 'MOBILE_MONEY' | 'PAYGATE' | 'VIREMENT' | 'ESPECES' | 'CHEQUE' | 'AUTRE';
/** Reseaux mobile money payables en ligne par PayGate Global. */
export type ReseauPayGate = 'FLOOZ' | 'TMONEY';
export const RESEAUX_PAYGATE: { code: ReseauPayGate; libelle: string }[] = [
  { code: 'TMONEY', libelle: 'T-Money (Togocom)' },
  { code: 'FLOOZ', libelle: 'Flooz (Moov Africa)' }
];
export type StatutPaiementAbonnement = 'EN_ATTENTE' | 'VALIDE' | 'REJETE';
export type EtatAbonnement = 'ESSAI' | 'ACTIF' | 'A_RENOUVELER' | 'GRACE' | 'EXPIRE';
export type RessourceLimitee = 'UTILISATEURS' | 'VEHICULES' | 'AGENCES' | 'CAISSES' | 'EMPLOYES';

export const PERIODICITES: { code: Periodicite; libelle: string; mois: number }[] = [
  { code: 'MENSUEL', libelle: 'Mensuel', mois: 1 },
  { code: 'TRIMESTRIEL', libelle: 'Trimestriel', mois: 3 },
  { code: 'SEMESTRIEL', libelle: 'Semestriel', mois: 6 },
  { code: 'ANNUEL', libelle: 'Annuel', mois: 12 }
];

export const MOYENS_PAIEMENT: { code: MoyenPaiementAbonnement; libelle: string }[] = [
  { code: 'MOBILE_MONEY', libelle: 'Mobile Money' },
  { code: 'VIREMENT', libelle: 'Virement bancaire' },
  { code: 'ESPECES', libelle: 'Especes' },
  { code: 'CHEQUE', libelle: 'Cheque' },
  { code: 'AUTRE', libelle: 'Autre' }
];

export const LIBELLES_RESSOURCES: Record<RessourceLimitee, string> = {
  UTILISATEURS: 'Comptes utilisateurs',
  VEHICULES: 'Vehicules',
  AGENCES: 'Agences',
  CAISSES: 'Caisses de point de vente',
  EMPLOYES: 'Employes (presence)'
};

/** Teinte de l'etiquette de chaque etat. */
export const TEINTES_ETATS: Record<EtatAbonnement, string> = {
  ESSAI: 'bleu',
  ACTIF: 'vert',
  A_RENOUVELER: 'orange',
  GRACE: 'orange',
  EXPIRE: 'rouge'
};

export interface Formule {
  id: number;
  nom: string;
  description?: string | null;
  /** Prix mensuel de chaque module dans la formule. */
  prix: Partial<Record<CodeModule, number>>;
  maxUtilisateurs?: number | null;
  maxVehicules?: number | null;
  maxAgences?: number | null;
  maxCaisses?: number | null;
  maxEmployes?: number | null;
  active: boolean;
  miseEnAvant: boolean;
  ordre: number;
  abonnes: number;
}

export type FormuleRequete = Omit<Formule, 'id' | 'abonnes'>;

/** Un module et son prix mensuel dans la formule (devis, facture). */
export interface LigneAbonnement {
  module: CodeModule;
  libelle: string;
  prixMensuel: number;
}

export interface ParametresAbonnement {
  devise: string;
  dureeEssaiJours: number;
  joursGrace: number;
  joursRappel: number;
  remiseTrimestrielle: number;
  remiseSemestrielle: number;
  remiseAnnuelle: number;
  instructionsPaiement?: string | null;
  mentionsFacture?: string | null;
}

export interface UsageRessource {
  ressource: RessourceLimitee;
  libelle: string;
  utilise: number;
  limite?: number | null;
}

export interface EtatCourtAbonnement {
  etat: EtatAbonnement;
  etatLibelle: string;
  essai: boolean;
  lectureSeule: boolean;
  rappel: boolean;
  fin: string;
  finGrace: string;
  joursRestants: number;
  formuleNom?: string | null;
}

export interface AbonnementVue {
  entrepriseId: number;
  entrepriseNom?: string | null;
  etat: EtatAbonnement;
  etatLibelle: string;
  essai: boolean;
  lectureSeule: boolean;
  rappel: boolean;
  debut: string;
  fin: string;
  finGrace: string;
  joursRestants: number;
  formuleId?: number | null;
  formuleNom?: string | null;
  periodicite?: Periodicite | null;
  periodiciteLibelle?: string | null;
  modules: CodeModule[];
  usages: UsageRessource[];
  paiementEnAttente: boolean;
  mensualite: number;
}

export interface DemandeDevis {
  formuleId: number;
  periodicite: Periodicite;
  modules: CodeModule[];
}

export interface Devis {
  formuleId: number;
  formuleNom: string;
  periodicite: Periodicite;
  mois: number;
  modules: CodeModule[];
  lignes: LigneAbonnement[];
  mensualite: number;
  sousTotal: number;
  remisePourcent: number;
  remise: number;
  montant: number;
  devise: string;
}

export interface DeclarationPaiement extends DemandeDevis {
  moyen: MoyenPaiementAbonnement;
  reference?: string | null;
  note?: string | null;
}

export interface SaisiePaiement extends DeclarationPaiement {
  /** Absent : le montant du devis. */
  montant?: number | null;
  /** Duree libre en mois (1 a 36) ; absente : celle de la periodicite. */
  mois?: number | null;
  /** Entreprise en essai : la periode payee commence aujourd'hui. */
  debutImmediat?: boolean;
}

export interface PaiementAbonnement {
  id: number;
  entrepriseId: number;
  entrepriseNom?: string | null;
  formuleId: number;
  formuleNom: string;
  periodicite: Periodicite;
  periodiciteLibelle: string;
  modules: CodeModule[];
  /** Prix mensuel de chaque module au moment du paiement. */
  lignes: LigneAbonnement[];
  montant: number;
  devise: string;
  moyen: MoyenPaiementAbonnement;
  moyenLibelle: string;
  reference?: string | null;
  note?: string | null;
  preuve: boolean;
  preuveNom?: string | null;
  statut: StatutPaiementAbonnement;
  motifRejet?: string | null;
  saisiPlateforme: boolean;
  traitePar?: string | null;
  traiteLe?: string | null;
  periodeDebut?: string | null;
  periodeFin?: string | null;
  numeroFacture?: string | null;
  creeLe?: string | null;
  creePar?: string | null;
}

export interface OffreAbonnement {
  formules: Formule[];
  parametres: ParametresAbonnement;
  /** Vrai si le paiement en ligne (PayGate : Flooz, T-Money) est configure sur la plateforme. */
  paiementEnLigne?: boolean;
}

// ------------------------------------------------------ tableau de bord

export interface PointSerie {
  cle: string;
  libelle: string;
  valeur: number;
}

export interface ActivitePlateforme {
  code: string;
  libelle: string;
  total: number;
  ceMois: number;
}

export interface EcheanceAbonnement {
  entrepriseId: number;
  nom: string;
  etat: EtatAbonnement;
  etatLibelle: string;
  fin: string;
  joursRestants: number;
  formuleNom?: string | null;
  essai: boolean;
  mensualite: number;
  paiementEnAttente: boolean;
}

export interface EntrepriseCourte {
  id: number;
  nom: string;
  pays?: string | null;
  creeLe?: string | null;
  utilisateurs: number;
  etatLibelle?: string | null;
}

export interface TableauBordPlateforme {
  devise: string;
  entreprises: number;
  actives: number;
  suspendues: number;
  enEssai: number;
  payantes: number;
  aRenouveler: number;
  enGrace: number;
  expirees: number;
  inscritesCeMois: number;
  inscritesMoisPrecedent: number;
  utilisateurs: number;
  utilisateursActifs: number;
  connectes7j: number;
  entreprisesActives30j: number;
  revenuMensuelRecurrent: number;
  revenuAnnuelRecurrent: number;
  encaisseCeMois: number;
  encaisseMoisPrecedent: number;
  encaisseAnnee: number;
  encaisseTotal: number;
  panierMoyen: number;
  paiementsEnAttente: number;
  montantEnAttente: number;
  tauxConversion: number;
  inscriptionsParMois: PointSerie[];
  revenusParMois: PointSerie[];
  parEtat: PointSerie[];
  parFormule: PointSerie[];
  parModule: PointSerie[];
  parPays: PointSerie[];
  parTypeActivite: PointSerie[];
  parMoyen: PointSerie[];
  activite: ActivitePlateforme[];
  echeances: EcheanceAbonnement[];
  dernieresInscrites: EntrepriseCourte[];
  plusGrandes: EntrepriseCourte[];
}

/** Paiement en ligne : formule choisie et numero mobile money qui paie. */
export interface DemandePaiementEnLigne extends DemandeDevis {
  reseau: ReseauPayGate;
  telephone: string;
}
