/** Fournisseurs et depenses du point de vente (toutes les variantes). */

/** ESPECES et MOBILE_MONEY ne sont plus proposes : ils restent lisibles sur les pieces anciennes. */
export type ModeSortie = 'CAISSE' | 'COFFRE' | 'BANQUE' | 'ESPECES' | 'MOBILE_MONEY';

/** Moyens proposes a la saisie : la caisse, le coffre du gerant ou la banque (compte de l'entreprise). */
export const MODES_SORTIE: { valeur: ModeSortie; libelle: string; icone: string; aide: string }[] = [
  { valeur: 'CAISSE', libelle: 'Caisse', icone: '🗄', aide: 'Sortie d\'especes du tiroir d\'une caisse ouverte' },
  { valeur: 'COFFRE', libelle: 'Coffre', icone: '🔐', aide: 'Especes du coffre du gerant (circuit des especes)' },
  { valeur: 'BANQUE', libelle: 'Banque', icone: '🏦', aide: 'Compte de l\'entreprise : virement ou cheque' }
];

// ---------------------------------------------------------------- fournisseurs

export interface FournisseurRequete {
  nom: string;
  personneContact?: string | null;
  telephone?: string | null;
  telephone2?: string | null;
  email?: string | null;
  adresse?: string | null;
  ville?: string | null;
  pays?: string | null;
  nif?: string | null;
  rccm?: string | null;
  delaiPaiementJours?: number | null;
  conditions?: string | null;
  note?: string | null;
}

export interface Fournisseur extends FournisseurRequete {
  id: number;
  code: string;
  actif: boolean;
  commandes: number;
  du: number;
  enRetard: number;
  prochaineEcheance?: string;
}

export interface DetteFournisseur {
  commandeId: number;
  numero: string;
  dateCommande: string;
  recueLe?: string;
  montantRecu: number;
  regle: number;
  reste: number;
  echeance?: string;
  enRetard: boolean;
}

export interface ReglementFournisseur {
  id: number;
  numero: string;
  dateReglement: string;
  commandeId: number;
  commandeNumero: string;
  montant: number;
  mode: ModeSortie;
  modeLibelle: string;
  reference?: string;
  sessionNumero?: string;
  auteurNom: string;
  note?: string;
  statut: 'VALIDE' | 'ANNULE';
  motifAnnulation?: string;
}

export interface CompteFournisseur {
  fournisseur: Fournisseur;
  dettes: DetteFournisseur[];
  reglements: ReglementFournisseur[];
  commandes: {
    id: number;
    numero: string;
    dateCommande: string;
    statut: 'EN_COURS' | 'RECUE' | 'ANNULEE';
    total: number;
    montantRecu?: number;
    recueLe?: string;
  }[];
}

export interface ReglementFournisseurRequete {
  montant: number;
  mode: ModeSortie;
  sessionId?: number | null;
  commandeId?: number | null;
  dateReglement?: string | null;
  reference?: string | null;
  note?: string | null;
}

export interface RecuReglementFournisseur {
  numero: string;
  dateReglement: string;
  fournisseurId: number;
  fournisseurNom: string;
  montant: number;
  modeLibelle: string;
  sessionNumero?: string;
  imputations: { commandeNumero: string; montant: number }[];
  resteDu: number;
  statut: 'VALIDE' | 'ANNULE';
}

/** Situation de paiement d'une commande fournisseur. */
export interface SituationPaiement {
  detteSuivie: boolean;
  montantRecu?: number;
  regle: number;
  reste: number;
  echeance?: string;
  enRetard: boolean;
  fournisseurId?: number;
  reglements: ReglementFournisseur[];
}

export interface TotauxFournisseurs {
  du: number;
  enRetard: number;
  fournisseursEnRetard: number;
}

// ------------------------------------------------------------------ depenses

export interface CategorieDepense {
  id: number;
  nom: string;
  icone?: string;
  ordre: number;
  actif: boolean;
  depenses: number;
}

export interface CategorieDepenseRequete {
  nom: string;
  icone?: string | null;
  ordre?: number | null;
  actif?: boolean;
}

export interface Depense {
  id: number;
  numero: string;
  categorieId: number;
  categorieNom: string;
  categorieIcone?: string;
  libelle: string;
  beneficiaire?: string;
  fournisseurId?: number;
  fournisseurNom?: string;
  montant: number;
  dateDepense: string;
  mode: ModeSortie;
  modeLibelle: string;
  reference?: string;
  sessionNumero?: string;
  caisseNom?: string;
  recurrenteId?: number;
  periode?: string;
  note?: string;
  justificatif: boolean;
  justificatifNom?: string;
  auteurNom: string;
  creeLe?: string;
  statut: 'VALIDE' | 'ANNULEE';
  annuleeLe?: string;
  annuleePar?: string;
  motifAnnulation?: string;
}

export interface DepenseRequete {
  categorieId: number;
  libelle: string;
  beneficiaire?: string | null;
  fournisseurId?: number | null;
  montant: number;
  dateDepense?: string | null;
  mode: ModeSortie;
  sessionId?: number | null;
  reference?: string | null;
  note?: string | null;
  recurrenteId?: number | null;
  periode?: string | null;
}

export interface DepenseModification {
  categorieId: number;
  libelle: string;
  beneficiaire?: string | null;
  fournisseurId?: number | null;
  dateDepense?: string | null;
  reference?: string | null;
  note?: string | null;
}

export interface SyntheseDepenses {
  debut: string;
  fin: string;
  total: number;
  nombre: number;
  parCategorie: { categorieId: number; categorie: string; icone?: string; total: number; nombre: number }[];
  parMode: { mode: ModeSortie; libelle: string; total: number; nombre: number }[];
}

export interface DepenseRecurrente {
  id: number;
  libelle: string;
  categorieId: number;
  categorieNom: string;
  categorieIcone?: string;
  beneficiaire?: string;
  montant: number;
  jourEcheance: number;
  mode?: ModeSortie;
  debut: string;
  fin?: string;
  actif: boolean;
  note?: string;
  paiements: number;
}

export interface DepenseRecurrenteRequete {
  libelle: string;
  categorieId: number;
  beneficiaire?: string | null;
  montant: number;
  jourEcheance?: number | null;
  mode?: ModeSortie | null;
  debut: string;
  fin?: string | null;
  actif?: boolean;
  note?: string | null;
}

/** Mois d'une depense recurrente encore a payer. */
export interface DepenseAPayer {
  recurrenteId: number;
  libelle: string;
  categorieId: number;
  categorieNom: string;
  categorieIcone?: string;
  beneficiaire?: string;
  montant: number;
  mode?: ModeSortie;
  periode: string;
  periodeLibelle: string;
  echeance: string;
  enRetard: boolean;
}
