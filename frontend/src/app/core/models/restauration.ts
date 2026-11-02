/** Point de vente en mode bar / restaurant / cafe : salles, tables, commandes et preparation. */
import { ModePaiementPdv, PostePreparation, Ticket } from './pdv';

/** Dimensions du plan, en unites : les tables y sont placees en proportion. */
export const LARGEUR_PLAN = 100;
export const HAUTEUR_PLAN = 60;

export type FormeTable = 'CARREE' | 'RONDE' | 'RECTANGLE';

export interface Salle {
  id: number;
  nom: string;
  ordre: number;
  actif: boolean;
}

export interface TableRestaurant {
  id: number;
  salleId: number;
  nom: string;
  places: number;
  forme: FormeTable;
  posX: number;
  posY: number;
  largeur: number;
  hauteur: number;
  actif: boolean;
  /** Jeton imprime dans le code QR de la table. */
  jetonQr: string;
}

export interface TableRequete {
  salleId: number;
  nom: string;
  places?: number;
  forme?: FormeTable;
  posX?: number;
  posY?: number;
  largeur?: number;
  hauteur?: number;
  actif?: boolean;
}

export type StatutCommande = 'OUVERTE' | 'ENCAISSEE' | 'ANNULEE';

export type StatutLigneCommande =
  | 'PROPOSEE'
  | 'BROUILLON'
  | 'A_PREPARER'
  | 'EN_PREPARATION'
  | 'PRETE'
  | 'SERVIE'
  | 'ANNULEE';

/** Appel du client depuis le code QR de sa table. */
export type AppelClient = 'SERVEUR' | 'ADDITION';

export const LIBELLES_STATUT_LIGNE: Record<StatutLigneCommande, string> = {
  PROPOSEE: 'A valider',
  BROUILLON: 'A envoyer',
  A_PREPARER: 'Envoye',
  EN_PREPARATION: 'En preparation',
  PRETE: 'Pret a servir',
  SERVIE: 'Servi',
  ANNULEE: 'Annule'
};

export interface CommandeResume {
  /** Nul (0 ou null) pour une table tenue par un autre serveur : occupee, sans detail ni numero. */
  id: number;
  numero: string;
  tableId?: number;
  repere: string;
  serveurNom: string;
  couverts?: number;
  statut: StatutCommande;
  ouverteLe: string;
  additionLe?: string;
  total: number;
  resteAPayer: number;
  aEnvoyer: number;
  enPreparation: number;
  pretes: number;
  /** Articles commandes par le client depuis le code QR, en attente de validation. */
  aValider: number;
  appel?: AppelClient | null;
  appelLe?: string | null;
  /** Passee dans la boutique en ligne. */
  enLigne?: boolean;
}

export interface LigneCommande {
  id: number;
  articleId: number;
  libelle: string;
  prixUnitaire: number;
  quantite: number;
  total: number;
  note?: string;
  poste: PostePreparation;
  statut: StatutLigneCommande;
  envoi?: number;
  envoyeeLe?: string;
  preteLe?: string;
  servieLe?: string;
  payee: boolean;
  annuleePar?: string;
  origine: 'SERVEUR' | 'CLIENT';
}

export interface CommandeDetail {
  resume: CommandeResume;
  note?: string;
  fermeeLe?: string;
  lignes: LigneCommande[];
}

export interface TablePlan {
  table: TableRestaurant;
  commande?: CommandeResume | null;
}

export interface PlanSalles {
  salles: { salle: Salle; tables: TablePlan[] }[];
  sansTable: CommandeResume[];
}

export interface EncaissementCommande {
  /** Absent : tout ce qui reste a payer. Sinon, la part d'un convive. */
  lignes?: { ligneId: number; quantite: number }[];
  remise?: number;
  modePaiement: ModePaiementPdv;
  montantRecu?: number | null;
  referencePaiement?: string;
  clientNom?: string;
  /** Session de caisse choisie par le poste quand plusieurs sont ouvertes. */
  sessionId?: number | null;
}

export interface ResultatEncaissement {
  ticket: Ticket;
  commande: CommandeDetail;
}

export interface LigneBon {
  id: number;
  libelle: string;
  quantite: number;
  note?: string;
  statut: StatutLigneCommande;
}

export interface Bon {
  commandeId: number;
  numero: string;
  repere: string;
  serveurNom: string;
  couverts?: number;
  envoi: number;
  envoyeLe: string;
  statut: StatutLigneCommande;
  lignes: LigneBon[];
}

// ------------------------------------------------- ecrans de preparation

export interface EcranPreparation {
  id: number;
  nom: string;
  actif: boolean;
  categories: { id: number; nom: string; couleur?: string; icone?: string }[];
  utilisateurs: { id: number; nomComplet: string; roles: string[]; actif: boolean }[];
}

export interface ListeEcrans {
  ecrans: EcranPreparation[];
  /** Categories a preparer qu'aucun ecran actif ne recoit : leurs articles n'apparaitraient nulle part. */
  categoriesSansEcran: { id: number; nom: string; couleur?: string; icone?: string }[];
}

export interface EcranRequete {
  nom: string;
  actif?: boolean;
  categorieIds: number[];
  utilisateurIds: number[];
}

// ------------------------------------------------------------ code QR

export interface ReglagesRestauration {
  qrActif: boolean;
  qrValidation: boolean;
  messageAccueil?: string | null;
}

/** Carte en ligne vue par le client qui a scanne le code QR de sa table. */
export interface CarteEnLigne {
  etablissement: string;
  logo?: string | null;
  messageAccueil?: string | null;
  table: string;
  commandeOuverte: boolean;
  /** Faux tant qu'aucune caisse n'est ouverte : ni commande ni appel depuis la table. */
  caisseOuverte: boolean;
  validationServeur: boolean;
  categories: { id: number; nom: string; couleur?: string; icone?: string }[];
  articles: { id: number; nom: string; categorieId?: number; prix: number; image?: string | null; disponible: boolean }[];
  suivi?: SuiviClient | null;
}

export interface SuiviClient {
  numero: string;
  lignes: { libelle: string; quantite: number; total: number; note?: string; statut: StatutLigneCommande }[];
  total: number;
  resteAPayer: number;
  appel?: AppelClient | null;
  appelLe?: string | null;
}
