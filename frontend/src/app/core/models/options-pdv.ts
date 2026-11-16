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
  /** Fonds de reserve commun : alimente par les apports des associes et de l'entreprise, compte separe. */
  fondsReserveActif?: boolean;
}

// ------------------------------------------------------------ fonds de reserve

/** Apport au fonds : d'un associe (depuis son sous-compte) ou de l'entreprise (apport direct de l'administrateur). */
export interface ApportFonds {
  id: number;
  numero: string;
  date: string;
  origine: 'ASSOCIE' | 'ENTREPRISE';
  associeId?: number | null;
  associeNom?: string | null;
  montant: number;
  note?: string | null;
  auteurNom: string;
}

export interface SortieFonds {
  id: number;
  numero: string;
  date: string;
  categorie: string;
  libelle: string;
  beneficiaire?: string;
  montant: number;
  statut: 'ATTENTE_GERANT' | 'ATTENTE_ADMIN' | 'VALIDE' | 'REFUSEE' | 'ANNULEE';
  auteurNom: string;
}

export interface FondsReserve {
  actif: boolean;
  totalApports: number;
  totalUtilise: number;
  enAttente: number;
  solde: number;
  disponible: number;
  /** Cumul des apports par associe, et de l'entreprise (associeId nul). */
  contributeurs: { associeId?: number | null; nom: string; total: number }[];
  /** Les plus recents d'abord. */
  apports: ApportFonds[];
  sorties: SortieFonds[];
}

/** Le fonds vu par un associe : solde, ses propres apports, toutes les depenses. */
export interface FondsAssocie {
  actif: boolean;
  solde: number;
  disponible: number;
  totalApports: number;
  mesApports: number;
  totalUtilise: number;
  enAttente: number;
  apports: ApportFonds[];
  depenses: SortieFonds[];
}

// ------------------------------------------------- sous-compte des associes

export type StatutDepotAssocie = 'EN_ATTENTE' | 'VALIDE' | 'REFUSE' | 'ANNULE';
export type SourceDepotAssocie = 'COFFRE' | 'BANQUE';

export const LIBELLES_STATUT_DEPOT: Record<StatutDepotAssocie, string> = {
  EN_ATTENTE: "Attend l'associe",
  VALIDE: 'Valide',
  REFUSE: 'Refuse, a corriger',
  ANNULE: 'Annule'
};

/** Versement de sa part a un associe : depot en banque, bordereau joint. */
export interface DepotAssocie {
  id: number;
  numero: string;
  associeId: number;
  associeNom: string;
  montant: number;
  source: SourceDepotAssocie;
  banque?: string | null;
  reference: string;
  dateDepot: string;
  periode?: string | null;
  note?: string | null;
  piece: boolean;
  pieceNom?: string | null;
  statut: StatutDepotAssocie;
  motifRefus?: string | null;
  auteurId: number;
  auteurNom: string;
  soumisLe: string;
  traiteLe?: string | null;
}

export interface DemandeDepotAssocie {
  associeId: number;
  montant: number;
  source: SourceDepotAssocie;
  banque?: string | null;
  reference: string;
  dateDepot: string;
  periode?: string | null;
  note?: string | null;
}

export interface PartMois {
  mois: string;
  libelle: string;
  chiffreAffaires: number;
  marge: number;
  part: number;
  /** Mois en cours : estimation, versable a sa fin. */
  enCours: boolean;
}

export interface CompteAssocie {
  associeId: number;
  nom: string;
  /** Parts figees des mois termines. */
  partsCloses: number;
  /** Part estimee du mois en cours. */
  estimation: number;
  /** Depots valides par l'associe. */
  verse: number;
  /** Depots en attente ou refuses (a corriger). */
  enCours: number;
  resteAVerser: number;
  apportsFonds: number;
  /** Solde du sous-compte : depots valides - apports au fonds. */
  solde: number;
  depotsAValider: number;
  mois: PartMois[];
  depots: DepotAssocie[];
  apports: ApportFonds[];
}

export interface LigneVersementAssocie {
  associeId: number;
  nom: string;
  email: string;
  actif: boolean;
  partsCloses: number;
  estimation: number;
  verse: number;
  enCours: number;
  resteAVerser: number;
  solde: number;
}

export interface SyntheseVersements {
  associes: LigneVersementAssocie[];
  depots: DepotAssocie[];
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
  /** Apports directs de l'entreprise au fonds de reserve, compte separe. */
  versFondsReserve?: number;
  /** Versements aux associes payes par le compte de l'entreprise. */
  versAssocies?: number;
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
