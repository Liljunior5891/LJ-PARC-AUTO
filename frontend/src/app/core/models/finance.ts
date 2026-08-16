/** Types de la phase 3 : recettes, depenses, carburant, facturation, rentabilite. */

export type ModePaiement =
  | 'ESPECES'
  | 'VIREMENT'
  | 'CHEQUE'
  | 'MOBILE_MONEY'
  | 'CARTE'
  | 'COMPENSATION'
  | 'AUTRE';

export type TypeRecette =
  | 'LOCATION'
  | 'TRANSPORT'
  | 'LIVRAISON'
  | 'MISSION_SPECIALE'
  | 'VOYAGE'
  | 'CONTRAT'
  | 'FRAIS_SUPPLEMENTAIRE'
  | 'KILOMETRAGE_SUPPLEMENTAIRE'
  | 'AUTRE';

export interface Recette {
  id: number;
  numero: string;
  camionId: number;
  camionImmatriculation: string;
  clientId?: number;
  clientNom?: string;
  locationId?: number;
  locationNumero?: string;
  missionId?: number;
  missionNumero?: string;
  factureId?: number;
  factureNumero?: string;
  type: TypeRecette;
  typeLibelle: string;
  libelle: string;
  montant: number;
  dateRecette: string;
  modePaiement?: ModePaiement;
  reference?: string;
  justificatif?: string;
  observations?: string;
}

export interface RecetteRequete {
  camionId: number;
  clientId?: number | null;
  locationId?: number | null;
  missionId?: number | null;
  type: TypeRecette;
  libelle: string;
  montant: number;
  dateRecette: string;
  modePaiement?: ModePaiement | null;
  reference?: string;
  observations?: string;
}

export type CategorieDepense =
  | 'CARBURANT'
  | 'ENTRETIEN'
  | 'PNEUS'
  | 'EXPLOITATION'
  | 'ADMINISTRATIF'
  | 'PERSONNEL'
  | 'AUTRE';

export interface Depense {
  id: number;
  numero: string;
  camionId: number;
  camionImmatriculation: string;
  chauffeurId?: number;
  chauffeurNom?: string;
  missionId?: number;
  missionNumero?: string;
  locationId?: number;
  locationNumero?: string;
  categorie: CategorieDepense;
  categorieLibelle: string;
  sousCategorie: string;
  sousCategorieLibelle: string;
  libelle: string;
  montant: number;
  dateDepense: string;
  fournisseur?: string;
  modePaiement?: ModePaiement;
  reference?: string;
  kilometrage?: number;
  station?: string;
  quantiteLitres?: number;
  prixLitre?: number;
  aChargeClient: boolean;
  observations?: string;
}

export interface DepenseRequete {
  camionId: number;
  chauffeurId?: number | null;
  missionId?: number | null;
  sousCategorie: string;
  libelle: string;
  montant: number;
  dateDepense: string;
  fournisseur?: string;
  modePaiement?: ModePaiement | null;
  reference?: string;
  kilometrage?: number | null;
  station?: string;
  quantiteLitres?: number | null;
  prixLitre?: number | null;
  aChargeClient?: boolean;
  observations?: string;
}

export interface LigneCategorieDepense {
  categorie: CategorieDepense;
  libelle: string;
  total: number;
  nombre: number;
}

export interface LigneNatureDepense {
  nature: string;
  libelle: string;
  categorieLibelle: string;
  total: number;
  nombre: number;
}

export interface LigneTypeRecette {
  type: TypeRecette;
  libelle: string;
  total: number;
  nombre: number;
}

export interface Plein {
  depenseId: number;
  numero: string;
  date: string;
  station?: string;
  kilometrage: number;
  distanceDepuisPleinPrecedent?: number;
  litres: number;
  prixLitre?: number;
  montant: number;
  consommationL100km?: number;
  chauffeurNom?: string;
}

export interface SyntheseCarburant {
  camionId: number;
  camionImmatriculation: string;
  debut: string;
  fin: string;
  nombrePleins: number;
  totalLitres: number;
  totalMontant: number;
  distanceCouverte: number;
  consommationMoyenneL100km?: number;
  coutCarburantParKm?: number;
  prixMoyenLitre?: number;
  pleins: Plein[];
}

export type StatutFacture =
  | 'BROUILLON'
  | 'NON_PAYEE'
  | 'PARTIELLEMENT_PAYEE'
  | 'PAYEE'
  | 'EN_RETARD'
  | 'ANNULEE';

export interface LigneFacture {
  id?: number;
  camionId?: number | null;
  camionImmatriculation?: string;
  designation: string;
  quantite: number;
  unite?: string;
  prixUnitaire: number;
  montant?: number;
}

export interface PaiementFacture {
  id: number;
  montant: number;
  datePaiement: string;
  modePaiement: ModePaiement;
  modeLibelle: string;
  reference?: string;
  observations?: string;
}

export interface Facture {
  id: number;
  numero: string;
  clientId: number;
  clientNom: string;
  locationId?: number;
  locationNumero?: string;
  missionId?: number;
  missionNumero?: string;
  statut: StatutFacture;
  statutLibelle: string;
  dateEmission: string;
  dateEcheance?: string;
  periodeDebut?: string;
  periodeFin?: string;
  montantHt: number;
  tauxTva: number;
  montantTva: number;
  montantTtc: number;
  montantPaye: number;
  resteAPayer: number;
  enRetard: boolean;
  observations?: string;
  lignes: LigneFacture[];
  paiements: PaiementFacture[];
}

export interface Creances {
  nombreFacturesImpayees: number;
  montantFacture: number;
  montantPaye: number;
  montantRestant: number;
  nombreEnRetard: number;
}

export interface LigneRentabilite {
  camionId: number;
  immatriculation: string;
  marque?: string;
  statutLibelle: string;
  recettes: number;
  depenses: number;
  resultat: number;
  margePourcent: number;
  distanceKm: number;
  nombreMissions: number;
  coutParKm?: number;
  revenuParKm?: number;
}

export interface Palmares {
  plusRentable?: LigneRentabilite;
  moinsRentable?: LigneRentabilite;
  plusDeRecettes?: LigneRentabilite;
  plusDeDepenses?: LigneRentabilite;
  plusDeKilometres?: LigneRentabilite;
}

export interface RentabiliteGlobale {
  debut: string;
  fin: string;
  totalRecettes: number;
  totalDepenses: number;
  resultat: number;
  margePourcent: number;
  nombreCamions: number;
  recetteMoyenneParCamion: number;
  depenseMoyenneParCamion: number;
  camions: LigneRentabilite[];
  palmares: Palmares;
}

export interface UtilisationCamion {
  joursPeriode: number;
  joursExploites: number;
  joursDisponibles: number;
  joursImmobilises: number;
  distanceKm: number;
  nombreMissions: number;
  tauxUtilisationPourcent: number;
}

export interface FichePerformance {
  camionId: number;
  immatriculation: string;
  marque?: string;
  marqueModele?: string;
  statutLibelle: string;
  debut: string;
  fin: string;
  revenus: {
    jour: number;
    semaine: number;
    mois: number;
    annee: number;
    cumule: number;
  };
  totalRecettes: number;
  totalDepenses: number;
  resultat: number;
  margePourcent: number;
  coutParKm?: number;
  revenuParKm?: number;
  utilisation: UtilisationCamion;
  recettesParType: LigneTypeRecette[];
  depensesParCategorie: LigneCategorieDepense[];
  depensesParNature: LigneNatureDepense[];
}

export interface TableauBordFinances {
  debut: string;
  fin: string;
  chiffreAffaires: number;
  totalDepenses: number;
  resultat: number;
  margePourcent: number;
  encaisseSurPeriode: number;
  creancesRestantes: number;
  facturesImpayees: number;
  facturesEnRetard: number;
  recetteMoyenneParCamion: number;
  depenseMoyenneParCamion: number;
}

export const MODES_PAIEMENT: { valeur: ModePaiement; libelle: string }[] = [
  { valeur: 'ESPECES', libelle: 'Especes' },
  { valeur: 'VIREMENT', libelle: 'Virement bancaire' },
  { valeur: 'CHEQUE', libelle: 'Cheque' },
  { valeur: 'MOBILE_MONEY', libelle: 'Mobile money' },
  { valeur: 'CARTE', libelle: 'Carte bancaire' },
  { valeur: 'COMPENSATION', libelle: 'Compensation' },
  { valeur: 'AUTRE', libelle: 'Autre' }
];

export const TYPES_RECETTE: { valeur: TypeRecette; libelle: string }[] = [
  { valeur: 'LOCATION', libelle: 'Location' },
  { valeur: 'TRANSPORT', libelle: 'Transport' },
  { valeur: 'LIVRAISON', libelle: 'Livraison' },
  { valeur: 'MISSION_SPECIALE', libelle: 'Mission speciale' },
  { valeur: 'VOYAGE', libelle: 'Voyage' },
  { valeur: 'CONTRAT', libelle: 'Contrat' },
  { valeur: 'FRAIS_SUPPLEMENTAIRE', libelle: 'Frais supplementaires' },
  { valeur: 'KILOMETRAGE_SUPPLEMENTAIRE', libelle: 'Kilometrage supplementaire' },
  { valeur: 'AUTRE', libelle: 'Autre recette' }
];

export const CATEGORIES_DEPENSE: { valeur: CategorieDepense; libelle: string }[] = [
  { valeur: 'CARBURANT', libelle: 'Carburant' },
  { valeur: 'ENTRETIEN', libelle: 'Entretien' },
  { valeur: 'PNEUS', libelle: 'Pneus' },
  { valeur: 'EXPLOITATION', libelle: 'Exploitation' },
  { valeur: 'ADMINISTRATIF', libelle: 'Administratif' },
  { valeur: 'PERSONNEL', libelle: 'Personnel' },
  { valeur: 'AUTRE', libelle: 'Autres depenses' }
];

export const STATUTS_FACTURE: { valeur: StatutFacture; libelle: string }[] = [
  { valeur: 'BROUILLON', libelle: 'Brouillon' },
  { valeur: 'NON_PAYEE', libelle: 'Non payee' },
  { valeur: 'PARTIELLEMENT_PAYEE', libelle: 'Partiellement payee' },
  { valeur: 'PAYEE', libelle: 'Payee' },
  { valeur: 'EN_RETARD', libelle: 'En retard' },
  { valeur: 'ANNULEE', libelle: 'Annulee' }
];
