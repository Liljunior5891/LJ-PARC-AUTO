import { MembrePersonnel } from './pdv';

export type TypeLieu = 'MAGASIN' | 'ENTREPOT';

export interface LieuCourt {
  id: number;
  nom: string;
  type: TypeLieu;
  principal: boolean;
}

export interface EtatLieux {
  /** Vrai des qu'un entrepot actif existe ; sinon, la gestion a un seul stock s'applique. */
  actif: boolean;
  lieux: LieuCourt[];
  mesLieux: LieuCourt[];
}

export interface Lieu extends LieuCourt {
  adresse?: string | null;
  telephone?: string | null;
  actif: boolean;
  visibleEnLigne: boolean;
  utilisateurs: MembrePersonnel[];
  caisses: string[];
  references: number;
  quantite: number;
  reserve: number;
  valeurAchat: number;
  valeurVente: number;
}

export interface LieuRequete {
  nom: string;
  type: TypeLieu;
  adresse?: string | null;
  telephone?: string | null;
  actif?: boolean;
  visibleEnLigne?: boolean;
  utilisateurIds: number[];
}

export interface DisponibiliteLieu {
  articleId: number;
  lieuId: number;
  lieuNom: string;
  type: TypeLieu;
  stock: number;
  reserve: number;
  disponible: number;
}

export interface LigneStockLieu {
  articleId: number;
  code: string;
  nom: string;
  unite?: string | null;
  stock: number;
  reserve: number;
  disponible: number;
  seuilAlerte?: number | null;
  sousSeuil: boolean;
  image?: string | null;
}

/** Produit a demander en transfert depuis un lieu dont on ne voit pas le stock. */
export interface ProduitCourt {
  articleId: number;
  code: string;
  nom: string;
  unite?: string | null;
  image?: string | null;
}

export type StatutBonRetrait = 'A_SERVIR' | 'SERVI' | 'ANNULE';

export interface LigneRetrait {
  ligneId: number;
  articleId: number;
  libelle: string;
  unite?: string | null;
  quantite: number;
  servi: number;
  reste: number;
  enStock: number;
}

export interface BonRetrait {
  id: number;
  numero: string;
  venteId: number;
  venteNumero: string;
  dateEmission: string;
  clientNom?: string | null;
  lieuId: number;
  lieuNom: string;
  lieuType: TypeLieu;
  statut: StatutBonRetrait;
  clientDoitEncore: boolean;
  jours: number;
  ancien: boolean;
  lignes: LigneRetrait[];
}

export interface LigneServie {
  ligneId: number;
  quantite: number;
}

export interface RemiseRetrait {
  lignes: LigneServie[];
  recuPar?: string | null;
  note?: string | null;
}

export interface RemiseDifferee extends RemiseRetrait {
  cle: string;
  bonId: number;
  faiteLe: string;
}

export interface ResultatSynchro {
  cle: string;
  statut: 'ENREGISTREE' | 'CONFLIT' | 'TRAITEE';
  message?: string | null;
  bonLivraisonId?: number | null;
  bonLivraisonNumero?: string | null;
}

export interface ConflitHorsLigne {
  id: number;
  bonNumero: string;
  venteNumero: string;
  lieuNom: string;
  auteur: string;
  faiteLe: string;
  recuPar?: string | null;
  detail: string;
}

export type StatutTransfert = 'DEMANDE' | 'VALIDE' | 'EXPEDIE' | 'ECART' | 'RECU' | 'ANNULE';
export type DecisionEcart = 'PERTE' | 'RETOUR_SOURCE' | 'ARRIVE';

export const LIBELLES_STATUT_TRANSFERT: Record<StatutTransfert, string> = {
  DEMANDE: 'A valider',
  VALIDE: 'A expedier',
  EXPEDIE: 'En route',
  ECART: 'Ecart a traiter',
  RECU: 'Recu',
  ANNULE: 'Annule'
};

export interface LigneTransfert {
  id: number;
  articleId: number;
  code: string;
  nom: string;
  unite?: string | null;
  quantite: number;
  quantiteExpediee?: number | null;
  quantiteRecue?: number | null;
  ecart: number;
  decisionEcart?: DecisionEcart | null;
}

export interface Transfert {
  id: number;
  numero: string;
  /** Vides tant que le gerant n'a pas choisi le lieu de depart. */
  sourceId?: number | null;
  sourceNom?: string | null;
  destinationId: number;
  destinationNom: string;
  statut: StatutTransfert;
  note?: string | null;
  demandeLe: string;
  demandePar: string;
  validePar?: string | null;
  expedieLe?: string | null;
  expediePar?: string | null;
  transporteur?: string | null;
  recuLe?: string | null;
  recuPar?: string | null;
  motif?: string | null;
  lignes: LigneTransfert[];
}
