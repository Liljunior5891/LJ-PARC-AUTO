/** Types partages avec l'API PARC AUTO. */

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export type Role =
  | 'ADMIN'
  | 'DIRECTION'
  | 'GESTIONNAIRE_PARC'
  | 'COMPTABLE'
  | 'LOGISTIQUE'
  | 'CHAUFFEUR';

export interface UtilisateurCourant {
  id: number;
  email: string;
  nomComplet: string;
  chauffeurId?: number;
  roles: Role[];
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  utilisateur: UtilisateurCourant;
}

export type StatutCamion =
  | 'DISPONIBLE'
  | 'EN_LOCATION'
  | 'EN_MISSION'
  | 'EN_LIVRAISON'
  | 'EN_MAINTENANCE'
  | 'IMMOBILISE'
  | 'ACCIDENTE'
  | 'HORS_SERVICE'
  | 'VENDU';

export type TypeCarburant = 'DIESEL' | 'ESSENCE' | 'GPL' | 'ELECTRIQUE' | 'HYBRIDE' | 'AUTRE';

/** Entree de referentiel (type de camion, couleur). */
export interface Referentiel {
  id: number;
  libelle: string;
  complement?: string;
}

export interface Camion {
  id: number;
  immatriculation: string;
  /** Marque du referentiel, porteuse du logo. */
  marqueId?: number;
  marque?: string;
  marqueLogoUrl?: string;
  marqueCouleur?: string;
  marqueMonogramme?: string;
  modele?: string;
  annee?: number;
  typeId?: number;
  type?: string;
  categorie?: string;
  numeroChassis?: string;
  numeroMoteur?: string;
  couleurId?: number;
  couleur?: string;
  couleurCodeHexa?: string;
  capaciteTonnes?: number;
  typeCarburant?: TypeCarburant;
  dateAcquisition?: string;
  prixAchat?: number;
  kilometrageInitial: number;
  kilometrageActuel: number;
  kilometrageParcouru: number;
  statut: StatutCamion;
  statutLibelle: string;
  photoUrl?: string;
  gpsDeviceId?: string;
  observations?: string;
  actif: boolean;
}

export type CamionRequete = Omit<
  Camion,
  | 'id'
  | 'statutLibelle'
  | 'kilometrageParcouru'
  | 'marqueLogoUrl'
  | 'marqueCouleur'
  | 'marqueMonogramme'
  | 'couleurCodeHexa'
>;

/** Historique du compteur kilometrique (remarque 5). */
export type SourceReleve = 'MISSION' | 'CARBURANT' | 'DEPENSE' | 'ENTRETIEN' | 'GPS' | 'SAISIE';

export interface ReleveOdometre {
  date: string;
  kilometrage: number;
  distanceDepuisPrecedent?: number;
  source: SourceReleve;
  libelle?: string;
  reference?: string;
  chauffeurNom?: string;
}

export interface Odometre {
  camionId: number;
  immatriculation: string;
  debut: string;
  fin: string;
  kilometrageActuel: number;
  distanceSurPeriode: number;
  nombreReleves: number;
  moyenneKmParJour: number;
  releves: ReleveOdometre[];
}

export const LIBELLES_SOURCE_RELEVE: Record<SourceReleve, string> = {
  MISSION: 'Mission',
  CARBURANT: 'Plein de carburant',
  DEPENSE: 'Depense',
  ENTRETIEN: 'Entretien',
  GPS: 'Traceur GPS',
  SAISIE: 'Saisie manuelle'
};

/** Marque du referentiel (chapitre 6). */
export interface MarqueCamion {
  id: number;
  nom: string;
  logoUrl?: string;
  couleur?: string;
  monogramme: string;
  pays?: string;
  ordre: number;
  actif: boolean;
}

export type StatutChauffeur = 'ACTIF' | 'EN_MISSION' | 'EN_CONGE' | 'SUSPENDU' | 'INACTIF';

export interface Chauffeur {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
  nomComplet: string;
  photoUrl?: string;
  telephone: string;
  telephoneUrgence?: string;
  email?: string;
  adresse?: string;
  dateNaissance?: string;
  numeroPermis?: string;
  categoriesPermis?: string;
  permisObtention?: string;
  permisExpiration?: string;
  permisBientotExpire: boolean;
  dateEmbauche?: string;
  salaireMensuel?: number;
  statut: StatutChauffeur;
  statutLibelle: string;
  observations?: string;
  actif: boolean;
}

export type ChauffeurRequete = Omit<
  Chauffeur,
  'id' | 'nomComplet' | 'statutLibelle' | 'permisBientotExpire'
>;

export interface Affectation {
  id: number;
  camionId: number;
  camionImmatriculation: string;
  camionMarque?: string;
  chauffeurId: number;
  chauffeurNom: string;
  dateDebut: string;
  dateFin?: string;
  enCours: boolean;
  motif?: string;
  observations?: string;
}

export interface AffectationRequete {
  camionId: number;
  chauffeurId: number;
  dateDebut: string;
  dateFin?: string | null;
  motif?: string;
  observations?: string;
}

export type ProprietaireDocument = 'CAMION' | 'CHAUFFEUR';

export type TypeDocument =
  | 'CARTE_GRISE'
  | 'ASSURANCE'
  | 'VISITE_TECHNIQUE'
  | 'TAXE'
  | 'AUTORISATION'
  | 'DOCUMENT_DOUANIER'
  | 'CONTRAT'
  | 'PERMIS_CONDUIRE'
  | 'PIECE_IDENTITE'
  | 'VISITE_MEDICALE'
  | 'AUTRE';

export interface DocumentParc {
  id: number;
  proprietaireType: ProprietaireDocument;
  proprietaireId: number;
  proprietaireLibelle?: string;
  type: TypeDocument;
  typeLibelle: string;
  libelle: string;
  numero?: string;
  emetteur?: string;
  dateEmission?: string;
  dateExpiration?: string;
  joursAvantExpiration?: number;
  expire: boolean;
  cout?: number;
  fichierNom?: string;
  fichierType?: string;
  fichierTaille?: number;
  fichierDisponible: boolean;
  observations?: string;
}

export interface DocumentRequete {
  proprietaireType: ProprietaireDocument;
  proprietaireId: number;
  type: TypeDocument;
  libelle: string;
  numero?: string;
  emetteur?: string;
  dateEmission?: string | null;
  dateExpiration?: string | null;
  cout?: number | null;
  observations?: string;
}

export interface TableauBordParc {
  totalCamions: number;
  camionsDisponibles: number;
  camionsEnLocation: number;
  camionsEnMission: number;
  camionsEnMaintenance: number;
  camionsImmobilises: number;
  repartitionStatuts: Record<StatutCamion, number>;
  chauffeursActifs: number;
  affectationsEnCours: number;
  camionsSansChauffeur: number;
  documentsAExpirer: number;
  documentsExpires: number;
  permisAExpirer: number;
}

export const STATUTS_CAMION: { valeur: StatutCamion; libelle: string }[] = [
  { valeur: 'DISPONIBLE', libelle: 'Disponible' },
  { valeur: 'EN_LOCATION', libelle: 'En location' },
  { valeur: 'EN_MISSION', libelle: 'En mission' },
  { valeur: 'EN_LIVRAISON', libelle: 'En livraison' },
  { valeur: 'EN_MAINTENANCE', libelle: 'En maintenance' },
  { valeur: 'IMMOBILISE', libelle: 'Immobilise' },
  { valeur: 'ACCIDENTE', libelle: 'Accidente' },
  { valeur: 'HORS_SERVICE', libelle: 'Hors service' },
  { valeur: 'VENDU', libelle: 'Vendu' }
];

export const STATUTS_CHAUFFEUR: { valeur: StatutChauffeur; libelle: string }[] = [
  { valeur: 'ACTIF', libelle: 'Actif' },
  { valeur: 'EN_MISSION', libelle: 'En mission' },
  { valeur: 'EN_CONGE', libelle: 'En conge' },
  { valeur: 'SUSPENDU', libelle: 'Suspendu' },
  { valeur: 'INACTIF', libelle: 'Inactif' }
];

export const TYPES_CARBURANT: TypeCarburant[] = [
  'DIESEL',
  'ESSENCE',
  'GPL',
  'ELECTRIQUE',
  'HYBRIDE',
  'AUTRE'
];

export const TYPES_DOCUMENT: { valeur: TypeDocument; libelle: string }[] = [
  { valeur: 'CARTE_GRISE', libelle: 'Carte grise' },
  { valeur: 'ASSURANCE', libelle: 'Assurance' },
  { valeur: 'VISITE_TECHNIQUE', libelle: 'Visite technique' },
  { valeur: 'TAXE', libelle: 'Taxe' },
  { valeur: 'AUTORISATION', libelle: 'Autorisation de transport' },
  { valeur: 'DOCUMENT_DOUANIER', libelle: 'Document douanier' },
  { valeur: 'CONTRAT', libelle: 'Contrat' },
  { valeur: 'PERMIS_CONDUIRE', libelle: 'Permis de conduire' },
  { valeur: 'PIECE_IDENTITE', libelle: "Piece d'identite" },
  { valeur: 'VISITE_MEDICALE', libelle: 'Visite medicale' },
  { valeur: 'AUTRE', libelle: 'Autre' }
];
