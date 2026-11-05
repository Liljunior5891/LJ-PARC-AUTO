import { ModePaiementPdv } from './pdv';

/** Point de vente « Quincaillerie & materiaux (BTP) » : reglages, clients, credit, devis, conditionnements. */

export interface ReglagesMateriaux {
  creditActif: boolean;
  creditCaissier: boolean;
  depassementGerant: boolean;
  bloquerSiRetard: boolean;
  plafondDefaut: number | null;
  delaiPaiementJours: number;
  acompteMinimum: number;
  clientObligatoire: boolean;
  devisActif: boolean;
  devisValiditeJours: number;
  mentionsDevis: string | null;
  factureA4: boolean;
  tvaActive: boolean;
  conditionnementsActifs: boolean;
  /** Le client peut emporter une partie de sa commande et revenir chercher le reste. */
  livraisonDifferee: boolean;
  /** Vendre plus que le disponible : le surplus reste a livrer a l'arrivage. */
  venteSurArrivage: boolean;
}

export type TypeClientPdv = 'PARTICULIER' | 'ARTISAN' | 'ENTREPRISE' | 'ADMINISTRATION' | 'ONG';

export const TYPES_CLIENT_PDV: { valeur: TypeClientPdv; libelle: string }[] = [
  { valeur: 'PARTICULIER', libelle: 'Particulier' },
  { valeur: 'ARTISAN', libelle: 'Artisan / macon' },
  { valeur: 'ENTREPRISE', libelle: 'Entreprise' },
  { valeur: 'ADMINISTRATION', libelle: 'Administration' },
  { valeur: 'ONG', libelle: 'ONG / Association' }
];

/** Rubriques de la fiche communes a la gestion de parc. */
export interface DetailsClientPdv {
  nomFamille?: string | null;
  prenoms?: string | null;
  dateNaissance?: string | null;
  profession?: string | null;
  formeJuridique?: string | null;
  rccm?: string | null;
  secteurActivite?: string | null;
  organismeTutelle?: string | null;
  numeroAgrement?: string | null;
  ville?: string | null;
  pays?: string | null;
  personneContact?: string | null;
  fonctionContact?: string | null;
  telephoneContact?: string | null;
  conditionsCommerciales?: string | null;
}

export interface ClientPdv extends DetailsClientPdv {
  id: number;
  code: string;
  type: TypeClientPdv;
  typeLibelle: string;
  nom: string;
  telephone?: string | null;
  email?: string | null;
  adresse?: string | null;
  nif?: string | null;
  /** Plafond propre au client ; absent : plafond par defaut des reglages. */
  plafondCredit?: number | null;
  delaiPaiementJours?: number | null;
  note?: string | null;
  actif: boolean;
  /** Tout ce que le client doit, et la part dont l'echeance est passee. */
  encours: number;
  encoursEchu: number;
  /** Plafond applique (le sien ou celui des reglages) ; absent : sans limite. */
  plafondRetenu?: number | null;
  creditDisponible?: number | null;
}

export interface ClientPdvRequete extends DetailsClientPdv {
  code?: string;
  type: TypeClientPdv;
  /** Raison sociale ; pour une personne, l'API la compose a partir de ses prenoms et de son nom. */
  nom?: string;
  telephone?: string | null;
  email?: string | null;
  adresse?: string | null;
  nif?: string | null;
  plafondCredit?: number | null;
  delaiPaiementJours?: number | null;
  note?: string | null;
  actif?: boolean;
}

export interface VenteDue {
  id: number;
  numero: string;
  dateVente: string;
  total: number;
  montantPaye: number;
  resteDu: number;
  echeance?: string | null;
  enRetard: boolean;
}

export interface ReglementLigne {
  id: number;
  numero: string;
  date: string;
  venteNumero: string;
  montant: number;
  modePaiement: ModePaiementPdv;
  modePaiementLibelle: string;
  referencePaiement?: string | null;
  caissierNom: string;
  sessionNumero: string;
  statut: 'VALIDE' | 'ANNULE';
  motifAnnulation?: string | null;
}

export interface CompteClient {
  client: ClientPdv;
  dues: VenteDue[];
  reglements: ReglementLigne[];
  /** Commandes dont il reste de la marchandise a remettre. */
  aLivrer: VenteALivrer[];
}

export interface LigneALivrer {
  ligneId: number;
  articleId: number;
  libelle: string;
  unite?: string | null;
  quantite: number;
  quantiteLivree: number;
  reste: number;
  /** Stock en magasin, dans l'unite de base du produit. */
  enMagasin: number;
  /** Valeur d'une unite pour un solde : apres remises de la ligne et de la vente. */
  valeurUnitaire: number;
}

export interface VenteALivrer {
  venteId: number;
  numero: string;
  dateVente: string;
  clientId?: number | null;
  clientNom?: string | null;
  clientTelephone?: string | null;
  resteDu: number;
  lignes: LigneALivrer[];
  statut: 'VALIDEE' | 'ANNULEE';
  modePaiement: ModePaiementPdv;
}

export interface SoldeRequete {
  lignes: { ligneId: number; quantite: number }[];
  motif: string;
  modeRemboursement?: ModePaiementPdv | null;
  referencePaiement?: string | null;
  sessionId?: number | null;
}

export interface SoldeReste {
  id: number;
  numero: string;
  venteId: number;
  venteNumero: string;
  dateSolde: string;
  auteurNom: string;
  motif: string;
  montant: number;
  montantDeduit: number;
  montantRembourse: number;
  modeRemboursementLibelle?: string | null;
  sessionNumero?: string | null;
  /** Le solde reste annulable tant que la session de son remboursement est ouverte. */
  sessionOuverte: boolean;
  statut: 'VALIDE' | 'ANNULE';
  motifAnnulation?: string | null;
  lignes: { libelle: string; unite?: string | null; quantite: number; total: number }[];
}

export interface RemiseRequete {
  lignes: { ligneId: number; quantite: number }[];
  recuPar?: string | null;
  note?: string | null;
}

export interface BonLivraison {
  id: number;
  numero: string;
  venteId: number;
  venteNumero: string;
  dateBon: string;
  remisPar: string;
  recuPar?: string | null;
  note?: string | null;
  statut: 'VALIDE' | 'ANNULE';
  motifAnnulation?: string | null;
  lignes: { libelle: string; unite?: string | null; quantite: number; resteApres: number }[];
}

export interface ReglementRequete {
  montant: number;
  modePaiement: ModePaiementPdv;
  montantRecu?: number | null;
  referencePaiement?: string | null;
  venteId?: number | null;
  sessionId?: number | null;
}

export interface RecuReglement {
  numero: string;
  date: string;
  clientId: number;
  clientNom: string;
  clientTelephone?: string | null;
  montant: number;
  modePaiement: ModePaiementPdv;
  modePaiementLibelle: string;
  montantRecu: number;
  monnaieRendue: number;
  referencePaiement?: string | null;
  caissierNom: string;
  sessionNumero: string;
  imputations: { venteNumero: string; montant: number; resteApres: number }[];
  soldeRestant: number;
  statut: 'VALIDE' | 'ANNULE';
}

export type StatutDevis = 'EN_COURS' | 'CONVERTI' | 'ANNULE';

export interface LigneDevis {
  id: number;
  articleId: number;
  conditionnementId?: number | null;
  libelle: string;
  unite?: string | null;
  prixUnitaire: number;
  quantite: number;
  remise: number;
  total: number;
}

export interface Devis {
  id: number;
  numero: string;
  clientId?: number | null;
  clientNom: string;
  clientTelephone?: string | null;
  dateDevis: string;
  valableJusqu: string;
  statut: StatutDevis;
  statutLibelle: string;
  expire: boolean;
  totalBrut: number;
  remise: number;
  total: number;
  note?: string | null;
  vendeurNom: string;
  venteId?: number | null;
  lignes: LigneDevis[];
}

export interface DevisRequete {
  clientId?: number | null;
  clientNom?: string | null;
  lignes: { articleId: number; conditionnementId?: number | null; quantite: number; remise?: number }[];
  remise?: number;
  valableJusqu?: string | null;
  note?: string | null;
}

export interface EncaissementDevis {
  modePaiement: ModePaiementPdv;
  montantRecu?: number | null;
  referencePaiement?: string | null;
  sessionId?: number | null;
  credit?: boolean;
  acompte?: number | null;
  /** Ce que le client emporte maintenant ; absent : tout. */
  emportes?: { articleId: number; conditionnementId?: number | null; quantite: number }[] | null;
}

export interface Conditionnement {
  id: number;
  articleId: number;
  nom: string;
  /** Nombre d'unites de base contenues (20 sacs dans une tonne...). */
  coefficient: number;
  prixVente: number;
  codeBarres?: string | null;
  actif: boolean;
}

export interface ConditionnementRequete {
  id?: number | null;
  nom: string;
  coefficient: number;
  prixVente: number;
  codeBarres?: string | null;
  actif?: boolean;
}
