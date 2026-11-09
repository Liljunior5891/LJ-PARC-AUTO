/** Module Agences multiservices : agences, catalogue des services, comptes et registre. */
import { MembrePersonnel } from './pdv';

export interface DroitsMoi {
  /** Plusieurs agences autorisees par le super-administrateur ; sinon une seule agence ouverte. */
  agencesMultiples?: boolean;
  administrateur: boolean;
  permissions: string[];
  agences: { id: number; code: string; nom: string; actif: boolean }[];
}

export interface Agence {
  id: number;
  code: string;
  nom: string;
  ville?: string;
  quartier?: string;
  adresse?: string;
  telephone?: string;
  responsableId?: number;
  responsableNom?: string;
  actif: boolean;
  utilisateurs: MembrePersonnel[];
}

export interface RequeteAgence {
  code: string;
  nom: string;
  ville?: string;
  quartier?: string;
  adresse?: string;
  telephone?: string;
  responsableId?: number | null;
  actif?: boolean;
  utilisateurIds: number[];
}

export type CompteVirtuel = 'FLOAT' | 'RECHARGE' | 'AUCUN';
export type NatureOperation = 'TRANSIT' | 'VENTE_CARTE';

export interface ReseauFinancier {
  id: number;
  code: string;
  nom: string;
  ordre: number;
  actif: boolean;
  /** Logo en data URL ; absent : pastille aux initiales. */
  logo?: string | null;
}

export interface TypeOperation {
  id: number;
  /** Reseau auquel l'operation est propre ; nul : commune a tous les reseaux du service. */
  reseauId?: number | null;
  code: string;
  libelle: string;
  effetCash: number;
  effetVirtuel: number;
  compteVirtuel: CompteVirtuel;
  nature: NatureOperation;
  ordre: number;
  actif: boolean;
  regle: string;
}

export interface ServiceFinancier {
  id: number;
  code: string;
  nom: string;
  categorie: string;
  ordre: number;
  actif: boolean;
  commissionDifferee: boolean;
  logo?: string | null;
  reseaux: ReseauFinancier[];
  types: TypeOperation[];
}

export interface RequeteService {
  nom: string;
  categorie: string;
  ordre?: number | null;
  actif?: boolean;
  commissionDifferee?: boolean;
  /** Data URL ; vide : retire le logo ; absent : inchange. */
  logo?: string | null;
}

export interface RequeteReseau {
  serviceId: number;
  nom: string;
  ordre?: number | null;
  actif?: boolean;
  logo?: string | null;
}

export interface RequeteType {
  serviceId: number;
  /** Operation propre a ce reseau ; nul : commune a tout le service. */
  reseauId?: number | null;
  libelle: string;
  effetCash: number;
  effetVirtuel: number;
  compteVirtuel: CompteVirtuel;
  nature: NatureOperation;
  ordre?: number | null;
  actif?: boolean;
}

export type TypeCompte = 'CAISSE_GENERALE' | 'CAISSE_CAISSIER' | 'FLOAT' | 'RECHARGE' | 'COMMISSIONS' | 'BANQUE' | 'FONDS_EXPLOITATION';
export type FamilleCompte = 'CASH' | 'VIRTUEL' | 'BANQUE' | 'COMMISSIONS' | 'FONDS';

export interface TotauxTresorerie {
  cash: number;
  virtuel: number;
  banque: number;
  tresorerie: number;
  commissions: number;
  fonds: number;
}

export interface CompteAgence {
  id: number;
  agenceId: number;
  type: TypeCompte;
  typeLibelle: string;
  famille: FamilleCompte;
  libelle: string;
  serviceId?: number;
  serviceNom?: string;
  categorie?: string;
  serviceActif: boolean;
  banque?: string;
  numeroCompte?: string;
  solde: number;
  decouvertAutorise: number;
  bloque: boolean;
  actif: boolean;
  manuel: boolean;
  seuilAlerte?: number;
  ouverture: number;
  entrees: number;
  sorties: number;
  nombreMouvements: number;
}

export interface SoldesAgence {
  id: number;
  code: string;
  nom: string;
  actif: boolean;
  totaux: TotauxTresorerie;
  comptes: CompteAgence[];
}

export interface EtatSoldes {
  journee: string;
  totaux: TotauxTresorerie;
  agences: SoldesAgence[];
}

export interface LigneRegistre {
  id: number;
  ecritureId: number;
  numero: string;
  nature: string;
  natureLibelle: string;
  date: string;
  journee: string;
  libelle: string;
  motif?: string;
  montant: number;
  soldeApres: number;
  auteurNom?: string;
  annulation: boolean;
}

export interface RequeteCompte {
  type?: TypeCompte;
  libelle?: string;
  banque?: string;
  numeroCompte?: string;
  decouvertAutorise?: number;
  bloque?: boolean;
  actif?: boolean;
  seuilAlerte?: number | null;
}

export interface VerificationSolde {
  compteId: number;
  libelle: string;
  soldeEnregistre: number;
  soldeRegistre: number;
  ecart: number;
}

export interface ReglagesAgences {
  heureBascule: number;
  delaiDoublonMinutes: number;
  montantMaxTransaction: number;
  seuilValidationMouvement: number;
  plafondDepenseCaissier: number;
  joursAlerteEcheance: number;
  joursAlerteCommission: number;
  /** Depenses payees par une caisse : visa du superviseur puis approbation de l'administrateur. */
  validationDepensesCaisse?: boolean;
  /** Minutes sans geste avant que l'ecran du caissier d'agence se verrouille (code PIN). */
  minutesVerrouCaisse?: number;
}

export interface DroitsAgences {
  permissions: { code: string; libelle: string; groupe: string }[];
  profils: { role: string; libelle: string; permissions: string[] }[];
}

export type ModeBareme = 'FIXE' | 'POURCENTAGE' | 'TRANCHES';
export type CompteCredit = 'COMMISSIONS' | 'FLOAT';
export type StatutBareme = 'EN_COURS' | 'A_VENIR' | 'TERMINE' | 'DESACTIVE';

export interface TrancheBareme {
  montantMin: number | null;
  montantMax: number | null;
  mode: 'FIXE' | 'POURCENTAGE';
  valeurFixe?: number | null;
  taux?: number | null;
}

export interface Bareme {
  id: number;
  serviceId: number;
  serviceNom: string;
  typeOperationId?: number;
  typeLibelle?: string;
  reseauId?: number;
  reseauNom?: string;
  libelle: string;
  mode: ModeBareme;
  valeurFixe?: number;
  taux?: number;
  minimum?: number;
  maximum?: number;
  tranches: TrancheBareme[];
  compteCredit: CompteCredit;
  dateDebut: string;
  dateFin?: string;
  actif: boolean;
  exemple: boolean;
  precedentId?: number;
  statut: StatutBareme;
  resume: string;
  portee: string;
}

export interface RequeteBareme {
  serviceId: number;
  typeOperationId?: number | null;
  reseauId?: number | null;
  libelle: string;
  mode: ModeBareme;
  valeurFixe?: number | null;
  taux?: number | null;
  minimum?: number | null;
  maximum?: number | null;
  tranches?: TrancheBareme[];
  compteCredit: CompteCredit;
  dateDebut?: string | null;
  dateFin?: string | null;
  dateEffet?: string | null;
}

export interface CommissionCalculee {
  baremeId?: number;
  baremeLibelle?: string;
  commission: number;
  detail: string;
  compteCredit: CompteCredit;
  exemple: boolean;
}

export interface ControleBareme {
  service: string;
  operation: string;
  reseau?: string;
  montant: number;
  attendue: number;
  calculee?: number;
  bareme?: string;
  detail: string;
}

export interface SaisieTransaction {
  agenceId: number;
  serviceId: number;
  typeOperationId: number;
  reseauId?: number | null;
  montant: number;
  commission?: number | null;
  motifCommission?: string;
  referenceOperateur?: string;
  clientTelephone?: string;
  clientNom?: string;
  clientTypePiece?: string;
  clientNumeroPiece?: string;
  carte?: string;
  observations?: string;
  confirmerDoublon: boolean;
}

export interface EffetTransaction {
  compteId: number;
  compte: string;
  role: string;
  soldeActuel: number;
  variation: number;
  soldeApres: number;
  insuffisant: boolean;
  manque: number;
}

export interface ApercuTransaction {
  commissionProposee: number;
  commission: number;
  baremeLibelle?: string;
  detailCommission: string;
  baremeExemple: boolean;
  commissionModifiable: boolean;
  commissionDifferee: boolean;
  effets: EffetTransaction[];
  doublon?: { id: number; reference: string; date: string; caissierNom: string; texte: string };
  blocages: string[];
}

export type StatutTransaction = 'VALIDEE' | 'ANNULEE';

export interface Transaction {
  id: number;
  reference: string;
  dateOperation: string;
  journee: string;
  agenceId: number;
  agenceNom?: string;
  serviceId: number;
  serviceNom: string;
  categorie: string;
  reseauId?: number;
  reseauNom?: string;
  typeOperationId: number;
  typeLibelle: string;
  montant: number;
  effetCash: number;
  effetVirtuel: number;
  compteVirtuel: CompteVirtuel;
  commission: number;
  commissionProposee: number;
  commissionModifiee: boolean;
  motifCommission?: string;
  compteCommission: CompteCredit;
  referenceOperateur?: string;
  clientNom?: string;
  clientTelephone?: string;
  clientTypePiece?: string;
  clientNumeroPiece?: string;
  carteMasquee?: string;
  observations?: string;
  caissierId: number;
  caissierNom: string;
  statut: StatutTransaction;
  commissionStatut: 'ACQUISE' | 'CREDITEE';
  doublonConfirme: boolean;
  annuleeLe?: string;
  annuleeParNom?: string;
  motifAnnulation?: string;
  annulable: boolean;
}

export interface FiltresTransactions {
  agenceId?: number | null;
  du?: string;
  au?: string;
  serviceId?: number | null;
  reseauId?: number | null;
  typeOperationId?: number | null;
  statut?: StatutTransaction | '';
  montantMin?: number | null;
  montantMax?: number | null;
  q?: string;
}

export interface ResumeTransactions {
  nombre: number;
  volume: number;
  commissions: number;
  annulees: number;
}

export interface ClientAgence {
  id: number;
  telephone: string;
  nom?: string;
  typePiece?: string;
  numeroPiece?: string;
}

export interface LigneBillettageAgence {
  valeur: number;
  quantite: number;
}

export interface SessionCaisseAgence {
  id: number;
  numero: string;
  agenceId: number;
  agenceNom?: string;
  caissierId: number;
  caissierNom: string;
  /** SOUMISE : comptee par le caissier, en attente de validation du superviseur. */
  statut: 'OUVERTE' | 'SOUMISE' | 'FERMEE';
  ouverteLe: string;
  ouverteParNom?: string;
  fondInitial: number;
  soldeCaisse: number;
  nombreTransactions: number;
  encaisse: number;
  decaisse: number;
  commissions: number;
  autresMouvements: number;
  fermeeLe?: string;
  fermeeParNom?: string;
  montantTheorique?: number;
  montantCompte?: number;
  ecart?: number;
  justification?: string;
  billettageOuverture: LigneBillettageAgence[];
  billettageFermeture: LigneBillettageAgence[];
  /** Comptage soumis par le caissier. */
  soumiseLe?: string;
  montantSoumis?: number;
  ecartSoumis?: number;
  justificationSoumise?: string;
  billettageSoumis: LigneBillettageAgence[];
  /** Dernier rejet du comptage. */
  motifRejet?: string;
  rejeteeLe?: string;
  rejeteeParNom?: string;
}

export interface CaissierAgence {
  id: number;
  nomComplet: string;
  roles: string[];
  actif: boolean;
  session?: SessionCaisseAgence;
  ailleurs?: string;
}

export interface EtatCaisses {
  agenceId: number;
  agenceNom: string;
  caisseGenerale: number;
  caissiers: CaissierAgence[];
}

export type TypeMouvement = 'APPROVISIONNEMENT' | 'TRANSFERT_INTERNE' | 'VERSEMENT_BANCAIRE' | 'RETRAIT_BANCAIRE'
  | 'CREDIT_COMMISSION' | 'REGULARISATION' | 'AUTRE';
export type StatutMouvement = 'EN_ATTENTE' | 'VALIDE' | 'REJETE' | 'ANNULE';

export interface MouvementAgence {
  id: number;
  reference: string;
  agenceId: number;
  agenceNom?: string;
  type: TypeMouvement;
  typeLibelle: string;
  compteSourceId?: number;
  compteSource?: string;
  compteDestinationId?: number;
  compteDestination?: string;
  montant: number;
  motif: string;
  justification?: string;
  pieceNom?: string;
  statut: StatutMouvement;
  dateMouvement: string;
  auteurNom: string;
  valideLe?: string;
  valideParNom?: string;
  motifRejet?: string;
  motifAnnulation?: string;
  validable: boolean;
  annulable: boolean;
}

export interface RequeteMouvement {
  agenceId: number;
  type: TypeMouvement;
  compteSourceId?: number | null;
  compteDestinationId?: number | null;
  compteId?: number | null;
  sens?: 'AUGMENTER' | 'DIMINUER';
  montant: number;
  motif: string;
  justification?: string;
}

export type StatutCloture = 'SOUMISE' | 'VALIDEE' | 'REJETEE' | 'ROUVERTE';

export interface CompteAControler {
  compteId: number;
  libelle: string;
  famille: FamilleCompte;
  obligatoire: boolean;
  soldeTheorique: number;
}

export interface LigneCloture {
  compteId: number;
  libelle: string;
  famille: FamilleCompte;
  soldeTheorique: number;
  soldeReel?: number;
  ecart?: number;
  justification?: string;
}

export interface Cloture {
  id: number;
  agenceId: number;
  agenceNom?: string;
  journee: string;
  statut: StatutCloture;
  observations?: string;
  soumiseLe?: string;
  soumiseParNom?: string;
  valideeLe?: string;
  valideeParNom?: string;
  motifRejet?: string;
  rouverteLe?: string;
  rouverteParNom?: string;
  motifReouverture?: string;
  ecartTotal: number;
  lignes: LigneCloture[];
}

export interface PreparationCloture {
  agenceId: number;
  agenceNom: string;
  journee: string;
  journeeCourante: string;
  ecritures: number;
  comptes: CompteAControler[];
  caissesOuvertes: string[];
  cloture?: Cloture;
}

export interface ClotureAFaire {
  agenceId: number;
  agenceNom: string;
  journee: string;
  statut: string;
}

export interface PointActivite {
  jour: string;
  nombre: number;
  volume: number;
  commissions: number;
}

export interface RepartitionActivite {
  id: number;
  nom: string;
  nombre: number;
  volume: number;
  commissions: number;
}

export interface TableauBordAgences {
  du: string;
  au: string;
  journeeCourante: string;
  tresorerie: TotauxTresorerie;
  nombre: number;
  volume: number;
  commissions: number;
  commissionsEncaissees: number;
  commissionsEnAttente: number;
  ventesCartes: number;
  coutCartes: number;
  chiffreAffaires: number;
  depenses: number;
  fraisFinanciers: number;
  resultatNet: number;
  dettes: number;
  creances: number;
  parJour: PointActivite[];
  tresorerieParJour: { jour: string; solde: number }[];
  parService: RepartitionActivite[];
  parReseau: RepartitionActivite[];
  parCaissier: RepartitionActivite[];
  cloturesAFaire: number;
  mouvementsEnAttente: number;
  /** CAISSIER : ses seules donnees ; AGENCE : une agence ; TOUTES : toutes les agences. */
  perimetre: 'CAISSIER' | 'AGENCE' | 'TOUTES';
}

export interface LigneAudit {
  id: number;
  date: string;
  agenceId?: number;
  agenceNom?: string;
  auteurNom?: string;
  action: string;
  objet: string;
  objetId?: number;
  description: string;
  avant?: string;
  apres?: string;
}

export interface ResumeDepenses {
  total: number;
  nombre: number;
  moyenne: number;
  annulees: number;
  montantAnnule: number;
  sansJustificatif: number;
  parCategorie: { categorieId: number; categorie: string; total: number; nombre: number }[];
}

export interface CategorieDepense {
  id: number;
  nom: string;
  ordre: number;
  actif: boolean;
}

export interface DepenseAgence {
  id: number;
  numero: string;
  agenceId: number;
  agenceNom?: string;
  categorieId: number;
  categorieNom: string;
  libelle: string;
  montant: number;
  journee: string;
  dateDepense: string;
  compteId: number;
  compteNom?: string;
  beneficiaire?: string;
  reference?: string;
  recurrenteId?: number;
  periode?: string;
  observations?: string;
  justificatifNom?: string;
  statut: StatutDepenseAgence;
  auteurNom: string;
  annuleeLe?: string;
  annuleeParNom?: string;
  motifAnnulation?: string;
  annulable: boolean;
  viseeParNom?: string;
  viseeLe?: string;
  approuveeParNom?: string;
  approuveeLe?: string;
  refuseeParNom?: string;
  refuseeLe?: string;
  motifRefus?: string;
  /** L'utilisateur peut viser (superviseur) ou approuver (administrateur) cette depense. */
  validable: boolean;
}

/** ATTENTE_SUPERVISEUR / ATTENTE_ADMIN : depense de caisse pas encore ecrite au registre. */
export type StatutDepenseAgence = 'ATTENTE_SUPERVISEUR' | 'ATTENTE_ADMIN' | 'VALIDEE' | 'REFUSEE' | 'ANNULEE';

export const LIBELLES_STATUT_DEPENSE_AGENCE: Record<StatutDepenseAgence, string> = {
  ATTENTE_SUPERVISEUR: 'Attend le superviseur',
  ATTENTE_ADMIN: "Attend l'administrateur",
  VALIDEE: 'Validee',
  REFUSEE: 'Refusee',
  ANNULEE: 'Annulee'
};

export interface SyntheseDepenses {
  mois: string;
  total: number;
  parCategorie: { categorieId: number; categorie: string; total: number; nombre: number }[];
  mois6: { mois: string; libelle: string; total: number }[];
}

export interface ChargeRecurrente {
  id: number;
  agenceId: number;
  categorieId: number;
  categorieNom: string;
  libelle: string;
  montant: number;
  jourEcheance: number;
  actif: boolean;
  regleeCeMois: boolean;
}

export type TypeCredit = 'EMPRUNT' | 'APPORT' | 'AVANCE' | 'DETTE';

export interface RemboursementCredit {
  id: number;
  date: string;
  journee: string;
  capital: number;
  interets: number;
  compteId: number;
  compteNom?: string;
  observations?: string;
  statut: 'VALIDE' | 'ANNULE';
  auteurNom?: string;
  motifAnnulation?: string;
}

export interface CreditAgence {
  id: number;
  numero: string;
  agenceId: number;
  agenceNom?: string;
  type: TypeCredit;
  typeLibelle: string;
  dette: boolean;
  libelle: string;
  tiersNom: string;
  tiersTelephone?: string;
  montantInitial: number;
  capitalRestant: number;
  capitalRembourse: number;
  interetsPayes: number;
  dateDebut: string;
  dateEcheance?: string;
  tauxInteret?: number;
  compteId?: number;
  compteNom?: string;
  remboursable: boolean;
  observations?: string;
  statut: 'EN_COURS' | 'SOLDE' | 'ANNULE';
  auteurNom?: string;
  prochaineEcheance?: string;
  montantProchaineEcheance: number;
  enRetard: boolean;
  echeances: { datePrevue: string; montant: number; passee: boolean }[];
  remboursements: RemboursementCredit[];
}

export interface ProduitCarte {
  id: number;
  nom: string;
  prixVente: number;
  seuilAlerte: number;
  actif: boolean;
}

export interface LotCarte {
  id: number;
  numero: string;
  agenceId: number;
  produitId: number;
  dateEntree: string;
  quantiteInitiale: number;
  quantiteRestante: number;
  coutUnitaire: number;
  fournisseur?: string;
  reference?: string;
  compteNom?: string;
  auteurNom?: string;
}

export interface StockCarte {
  produitId: number;
  nom: string;
  prixVente: number;
  disponible: number;
  valeurStock: number;
  seuilAlerte: number;
  alerte: boolean;
  margeUnitaireEstimee: number;
  lots: LotCarte[];
}

export interface VenteCarte {
  id: number;
  reference: string;
  agenceId: number;
  agenceNom?: string;
  produitId: number;
  produitNom: string;
  dateVente: string;
  journee: string;
  quantite: number;
  prixUnitaire: number;
  montant: number;
  coutTotal: number;
  marge: number;
  compteNom?: string;
  clientNom?: string;
  clientPrenom?: string;
  clientTelephone?: string;
  /** Numero de chaque carte vendue. */
  numeros: string[];
  caissierNom: string;
  statut: 'VALIDEE' | 'ANNULEE';
  annuleeLe?: string;
  annuleeParNom?: string;
  motifAnnulation?: string;
  annulable: boolean;
}

export interface SuiviCartes {
  ventes: { ventes: number; cartes: number; chiffreAffaires: number; cout: number; marge: number };
  recharge: { soldeOuverture: number; soldeActuel: number; approvisionnements: number; nombreRecharges: number;
              montantRecharges: number; commissions: number };
}

export interface RapportAgenceCatalogue {
  code: string;
  titre: string;
  description: string;
  autorise: boolean;
}

export interface AlerteAgence {
  niveau: 'CRITIQUE' | 'ATTENTION' | 'INFO';
  categorie: string;
  agence?: string;
  message: string;
  lien?: string;
}

export interface AttenteOperateur {
  serviceId: number;
  service: string;
  differe: boolean;
  nombre: number;
  montant: number;
  plusAncienne?: string;
}

export interface CommissionAttente {
  transactionId: number;
  reference: string;
  date: string;
  journee: string;
  reseau?: string;
  operation: string;
  montant: number;
  commission: number;
  caissierNom: string;
}

export interface VersementCommission {
  id: number;
  reference: string;
  agenceId: number;
  agenceNom?: string;
  serviceId: number;
  service: string;
  referenceOperateur?: string;
  dateVersement: string;
  montantAttendu: number;
  montantVerse: number;
  ecart: number;
  nombreCommissions: number;
  compteNom?: string;
  observations?: string;
  statut: 'VALIDE' | 'ANNULE';
  auteurNom?: string;
  motifAnnulation?: string;
}
