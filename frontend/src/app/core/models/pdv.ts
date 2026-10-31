/** Module Point de vente : catalogue, stock, caisses et ventes. */

/** Ecran qui recoit les articles d'une categorie commandes a table. */
export type PostePreparation = 'AUCUN' | 'CUISINE' | 'BAR';

export const POSTES_PREPARATION: { valeur: PostePreparation; libelle: string; icone: string }[] = [
  { valeur: 'AUCUN', libelle: 'Servi directement', icone: '🙋' },
  { valeur: 'CUISINE', libelle: 'Cuisine', icone: '🍳' },
  { valeur: 'BAR', libelle: 'Bar', icone: '🍹' }
];

export interface CategoriePdv {
  id: number;
  nom: string;
  couleur?: string;
  ordre: number;
  actif: boolean;
  poste: PostePreparation;
  posteLibelle: string;
  icone?: string;
}

/** Pictogrammes proposes pour les categories, du comptoir a la cuisine. */
export const ICONES_CATEGORIES = [
  '🍽️', '🍗', '🍖', '🐟', '🍤', '🥩', '🍝', '🍚', '🥘', '🍲', '🥗', '🍔', '🍕', '🌮', '🥪', '🍟',
  '🍳', '🥐', '🍰', '🍨', '🍍', '🍉', '☕', '🍵', '🧃', '🥤', '🍺', '🍷', '🍸', '🥃', '🧊', '💧',
  '🛒', '🧴', '🧼', '🍬', '🥫', '🧂', '📦', '🔌'
];

export interface CategorieRequete {
  nom: string;
  couleur?: string | null;
  ordre?: number;
  actif?: boolean;
  poste?: PostePreparation;
  icone?: string | null;
}

export interface ArticlePdv {
  id: number;
  code: string;
  codeBarres?: string;
  nom: string;
  categorieId?: number;
  categorieNom?: string;
  categorieCouleur?: string;
  prixVente: number;
  prixAchat?: number;
  unite?: string;
  suiviStock: boolean;
  stockActuel: number;
  seuilAlerte?: number;
  stockBas: boolean;
  actif: boolean;
  /** Photo en data URL, reduite avant l'envoi. */
  image?: string | null;
  categorieIcone?: string;
}

export interface ArticleRequete {
  code?: string;
  codeBarres?: string;
  nom: string;
  categorieId?: number | null;
  prixVente: number;
  prixAchat?: number | null;
  unite?: string;
  suiviStock?: boolean;
  stockInitial?: number | null;
  seuilAlerte?: number | null;
  actif?: boolean;
  image?: string | null;
}

export type TypeMouvementStock = 'ENTREE' | 'SORTIE' | 'PERTE' | 'INVENTAIRE' | 'VENTE' | 'RETOUR';

/** Mouvements saisis a la main ; ventes et retours viennent de la caisse, l'inventaire a son propre ecran. */
export const TYPES_MOUVEMENT_MANUELS: { valeur: TypeMouvementStock; libelle: string; aide: string }[] = [
  { valeur: 'ENTREE', libelle: 'Entree (reception)', aide: 'Quantite recue du fournisseur' },
  { valeur: 'SORTIE', libelle: 'Sortie', aide: 'Quantite retiree du stock' },
  { valeur: 'PERTE', libelle: 'Perte / casse', aide: 'Quantite perdue ou abimee' },
  { valeur: 'INVENTAIRE', libelle: 'Inventaire', aide: 'Quantite comptee en rayon : elle remplace le stock' }
];

export interface MouvementStock {
  id: number;
  articleId: number;
  articleCode: string;
  articleNom: string;
  type: TypeMouvementStock;
  typeLibelle: string;
  quantite: number;
  stockApres: number;
  coutUnitaire?: number;
  dateMouvement: string;
  motif?: string;
  venteId?: number;
  auteur?: string;
}

// ------------------------------------------------- commandes fournisseurs

export type StatutCommandeFournisseur = 'EN_COURS' | 'RECUE' | 'ANNULEE';

export const LIBELLES_STATUT_COMMANDE_FOURNISSEUR: Record<StatutCommandeFournisseur, string> = {
  EN_COURS: 'En attente de reception',
  RECUE: 'Receptionnee',
  ANNULEE: 'Annulee'
};

export interface CommandeFournisseurResume {
  id: number;
  numero: string;
  fournisseur: string;
  dateCommande: string;
  livraisonPrevue?: string;
  statut: StatutCommandeFournisseur;
  produits: number;
  total: number;
  recueLe?: string;
}

export interface LigneCommandeFournisseur {
  id: number;
  articleId: number;
  articleCode: string;
  articleNom: string;
  unite?: string;
  quantite: number;
  coutUnitaire?: number;
  total: number;
  quantiteRecue?: number;
  stockActuel: number;
}

export interface CommandeFournisseurDetail {
  resume: CommandeFournisseurResume;
  note?: string;
  creePar?: string;
  recuePar?: string;
  annuleeLe?: string;
  lignes: LigneCommandeFournisseur[];
}

export interface CommandeFournisseurRequete {
  fournisseur: string;
  dateCommande?: string;
  livraisonPrevue?: string | null;
  note?: string;
  lignes: { articleId: number; quantite: number; coutUnitaire?: number | null }[];
}

/** Bilan d'une feuille d'inventaire validee. */
export interface BilanInventaire {
  comptes: number;
  corriges: number;
  ecarts: MouvementStock[];
}

export interface SaisieStock {
  articleId: number;
  type: TypeMouvementStock;
  quantite: number;
  coutUnitaire?: number | null;
  motif?: string;
}

export type ModePaiementPdv = 'ESPECES' | 'MOBILE_MONEY' | 'CARTE' | 'AUTRE';

export const MODES_PAIEMENT_PDV: { valeur: ModePaiementPdv; libelle: string; icone: string }[] = [
  { valeur: 'ESPECES', libelle: 'Especes', icone: '💵' },
  { valeur: 'MOBILE_MONEY', libelle: 'Mobile money', icone: '📱' },
  { valeur: 'CARTE', libelle: 'Carte', icone: '💳' },
  { valeur: 'AUTRE', libelle: 'Autre', icone: '🧾' }
];

export interface TotalMode {
  mode: ModePaiementPdv;
  libelle: string;
  total: number;
  nombre: number;
}

/** Compte du point de vente, a qui l'on affecte caisses et ecrans. */
export interface MembrePersonnel {
  id: number;
  nomComplet: string;
  roles: string[];
  actif: boolean;
}

/** Caisse creee par le gerant et affectee a des utilisateurs. */
export interface CaissePdv {
  id: number;
  nom: string;
  description?: string;
  actif: boolean;
  utilisateurs: MembrePersonnel[];
  /** Session en cours : qui tient la caisse en ce moment. */
  session?: { id: number; numero: string; caissierId: number; caissierNom: string; ouverteLe: string } | null;
  sessions: number;
}

export interface CaisseRequete {
  nom: string;
  description?: string;
  actif?: boolean;
  utilisateurIds: number[];
}

/** Billets ou pieces d'une valeur, comptes a l'ouverture ou a la fermeture. */
export interface CoupureSession {
  valeur: number;
  nature: 'Billet' | 'Piece';
  quantite: number;
  montant: number;
}

/** Entree ou sortie d'especes hors vente. */
export interface MouvementCaisse {
  id: number;
  type: 'ENTREE' | 'SORTIE';
  montant: number;
  motif: string;
  auteurNom: string;
  date: string;
}

/** Quantite comptee par valeur de billet ou de piece. */
export type Billettage = { valeur: number; quantite: number }[];

/** Ventes d'une session pour un produit. */
export interface VenteProduitSession {
  produit: string;
  quantite: number;
  total: number;
  commandes: number;
}

/** Resultat du comptage a l'aveugle, avant la fermeture. */
export interface ControleCaisse {
  montantCompte: number;
  especesAttendues: number;
  ecart: number;
  commandesEnCours: string[];
}

export interface SessionCaisse {
  id: number;
  numero: string;
  caisseId: number;
  caisseNom: string;
  /** Gerant qui a ouvert la session. */
  caissierId: number;
  caissierNom: string;
  statut: 'OUVERTE' | 'FERMEE';
  ouverteLe: string;
  fermeeLe?: string;
  fermeeParNom?: string;
  fondInitial: number;
  montantCompte?: number;
  montantTheorique?: number;
  ecart?: number;
  justificationEcart?: string;
  observations?: string;
  chiffreAffaires: number;
  nombreVentes: number;
  ventesAnnulees: number;
  entrees: number;
  sorties: number;
  especesAttendues: number;
  parMode: TotalMode[];
  mouvements: MouvementCaisse[];
  billettageOuverture: CoupureSession[];
  billettageFermeture: CoupureSession[];
  /** Commandes de table encore a regler, qui empechent la fermeture. */
  commandesEnCours: number;
}

export interface LigneEncaissement {
  articleId: number;
  quantite: number;
  remise?: number;
}

export interface Encaissement {
  lignes: LigneEncaissement[];
  remise?: number;
  modePaiement: ModePaiementPdv;
  montantRecu?: number | null;
  referencePaiement?: string;
  clientNom?: string;
  /** Session de caisse choisie par le poste quand plusieurs sont ouvertes. */
  sessionId?: number | null;
}

export interface LigneTicket {
  articleId: number;
  libelle: string;
  prixUnitaire: number;
  quantite: number;
  remise: number;
  total: number;
}

export type StatutVente = 'VALIDEE' | 'ANNULEE';

export interface Ticket {
  id: number;
  numero: string;
  dateVente: string;
  caissierNom: string;
  clientNom?: string;
  sessionNumero: string;
  lignes: LigneTicket[];
  totalBrut: number;
  remise: number;
  total: number;
  modePaiement: ModePaiementPdv;
  modePaiementLibelle: string;
  montantRecu?: number;
  monnaieRendue?: number;
  referencePaiement?: string;
  statut: StatutVente;
  annuleeLe?: string;
  annuleePar?: string;
  motifAnnulation?: string;
  /** Addition d'une table : la commande et la table servies. */
  commandeId?: number;
  tableNom?: string;
}

export interface VenteResume {
  id: number;
  numero: string;
  dateVente: string;
  caissierNom: string;
  clientNom?: string;
  total: number;
  modePaiement: ModePaiementPdv;
  modePaiementLibelle: string;
  statut: StatutVente;
  sessionNumero: string;
  tableNom?: string;
}

export interface TableauBordPdv {
  debut: string;
  fin: string;
  chiffreAffaires: number;
  tickets: number;
  panierMoyen: number;
  ticketsAnnules: number;
  /** Cout d'achat des articles vendus. */
  coutVentes: number;
  margeBrute: number;
  margePourcent: number;
  /** Marchandises receptionnees des fournisseurs sur la periode. */
  achatsRecus: number;
  parMode: TotalMode[];
  meilleursArticles: { articleId: number; libelle: string; quantite: number; chiffreAffaires: number; marge: number }[];
  parJour: { date: string; chiffreAffaires: number; tickets: number }[];
  /** Un point par jour, ou par mois quand la periode depasse deux mois. */
  granularite: 'JOUR' | 'MOIS';
  evolution: { cle: string; libelle: string; chiffreAffaires: number; cout: number; marge: number; tickets: number }[];
  parHeure: { heure: number; chiffreAffaires: number; tickets: number }[];
  parCategorie: { libelle: string; couleur?: string; icone?: string; chiffreAffaires: number }[];
  derniersTickets: VenteResume[];
  articlesActifs: number;
  caissesOuvertes: number;
  caisses: number;
  ruptures: number;
  sousSeuil: number;
  commandesFournisseursEnCours: number;
  commandesFournisseursEnRetard: number;
  stockBas: ArticlePdv[];
  /** Service en salle, pour un point de vente en mode restaurant. */
  salle?: {
    tablesOccupees: number;
    tables: number;
    commandesOuvertes: number;
    enPreparation: number;
    pretesAServir: number;
    demandesClients: number;
  } | null;
}
