/** Options du point de vente a activer : benefices des associes et circuit des especes. */

export interface ReglagesPdv {
  associesActif: boolean;
  circuitCaisseActif: boolean;
  /** Ecart (en valeur absolue) au-dela duquel une justification est exigee. */
  ecartTolere: number;
  /** Depenses et sorties payees par la caisse : visa du gerant puis approbation de l'administrateur. */
  validationDepensesCaisse: boolean;
  /** Minutes sans geste avant que l'ecran de caisse se verrouille (code PIN). */
  minutesVerrouCaisse: number;
  /** Remise maximale (en % du ticket) qu'un caissier accorde seul ; 100 : sans plafond. */
  remiseMaxCaissierPourcent?: number;
}

// ------------------------------------------------------------------ associes

export interface PartAssocie {
  id?: number;
  articleId?: number | null;
  articleNom?: string;
  categorieId?: number | null;
  categorieNom?: string;
  taux: number;
}

export interface Associe {
  id: number;
  nom: string;
  email: string;
  actif: boolean;
  parts: PartAssocie[];
}

export interface LigneBenefice {
  articleId: number;
  produit: string;
  categorie?: string;
  quantite: number;
  chiffreAffaires: number;
  cout: number;
  marge: number;
  taux: number;
  part: number;
  origine: string;
}

export interface BeneficeAssocie {
  associeId: number;
  nom: string;
  email: string;
  chiffreAffaires: number;
  marge: number;
  part: number;
  lignes: LigneBenefice[];
}

export interface Benefices {
  debut: string;
  fin: string;
  chiffreAffaires: number;
  margeBrute: number;
  partsAssocies: number;
  partEntreprise?: number | null;
  associes: BeneficeAssocie[];
  /** Vue d'un associe : seulement ses produits et sa part. */
  vueAssocie: boolean;
}

// ---------------------------------------------------------- circuit especes

export interface CoupureTresorerie {
  valeur: number;
  quantite: number;
  montant: number;
}

export type StatutRemise = 'EN_ATTENTE' | 'VALIDEE' | 'REFUSEE';
export type StatutDegagement = 'EN_ATTENTE' | 'VALIDE' | 'REFUSE';

export interface Remise {
  id: number;
  numero: string;
  sessionId: number;
  sessionNumero: string;
  caisseNom: string;
  caissierId: number;
  caissierNom: string;
  montantAttendu: number;
  montantDeclare: number;
  montantValide?: number;
  ecartDeclare: number;
  ecartVerification?: number;
  justification?: string;
  statut: StatutRemise;
  envoyeeLe: string;
  traiteeParNom?: string;
  traiteeLe?: string;
  commentaire?: string;
  destination?: string;
  declare: CoupureTresorerie[];
  verifie: CoupureTresorerie[];
}

export interface Degagement {
  id: number;
  numero: string;
  gerantId: number;
  gerantNom: string;
  montantAttendu: number;
  montantDeclare: number;
  montantValide?: number;
  ecartDeclare: number;
  ecartVerification?: number;
  justification?: string;
  statut: StatutDegagement;
  envoyeLe: string;
  traiteParNom?: string;
  traiteLe?: string;
  commentaire?: string;
  declare: CoupureTresorerie[];
  verifie: CoupureTresorerie[];
  /** Depot en banque : banque, reference et date du bordereau, piece jointe. */
  banque?: string;
  referenceDepot?: string;
  dateDepot?: string;
  piece: boolean;
  pieceNom?: string;
}

export interface EtatRemiseSession {
  sessionId: number;
  sessionNumero: string;
  caisseNom: string;
  fondInitial: number;
  especesTiroir: number;
  aRemettre: number;
  enAttente?: Remise | null;
  remises: Remise[];
}

export interface MouvementCoffre {
  date: string;
  piece: string;
  libelle: string;
  entree?: number;
  sortie?: number;
}

export interface Coffre {
  gerantId: number;
  gerantNom: string;
  solde: number;
  degagementEnAttente?: Degagement | null;
  mouvements: MouvementCoffre[];
}

export interface CompteEntreprise {
  solde: number;
  parDegagements: number;
  parRemisesDirectes: number;
  /** Ventes et reglements par mobile money ou carte, arrives directement sur le compte. */
  ventesElectroniques: number;
  /** Depenses et paiements de fournisseurs payes par la banque. */
  sortiesBanque: number;
  coffres: { gerantId: number; gerantNom: string; solde: number }[];
  entrees: MouvementCoffre[];
}

export interface EnAttenteTresorerie {
  remises: number;
  degagements: number;
}

export const LIBELLES_STATUT_REMISE: Record<StatutRemise | StatutDegagement, string> = {
  EN_ATTENTE: 'En attente',
  VALIDEE: 'Validee',
  VALIDE: 'Valide',
  REFUSEE: 'Refusee',
  REFUSE: 'Refuse'
};
