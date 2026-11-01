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
  | 'SUPER_ADMIN'
  | 'ADMIN'
  | 'GESTIONNAIRE_PARC'
  | 'CHAUFFEUR'
  | 'GERANT'
  | 'CAISSIER'
  | 'SERVEUR'
  | 'PREPARATEUR';

/** Modules de la plateforme ; une entreprise n'accede qu'a ceux qu'elle a actives. */
export type CodeModule = 'GESTION_PARC' | 'POINT_DE_VENTE' | 'MARKETPLACE';

export interface UtilisateurCourant {
  id: number;
  email: string;
  nomComplet: string;
  chauffeurId?: number;
  roles: Role[];
  /** Absente pour l'administrateur de la plateforme. */
  entrepriseId?: number;
  entrepriseNom?: string;
  modules: CodeModule[];
  /** Type d'activite des modules qui se declinent (POINT_DE_VENTE : BOUTIQUE ou RESTAURATION). */
  variantes?: Partial<Record<CodeModule, string>>;
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

/** Entree de referentiel (type de vehicule, couleur). */
export interface Referentiel {
  id: number;
  libelle: string;
  complement?: string;
  /** Types de vehicule : famille et dessin de la carte. */
  categorie?: CategorieVehicule;
  dessin?: string;
}

/** Familles de vehicules : elles filtrent les types et les marques a la saisie. */
export type CategorieVehicule = 'VOITURE' | 'UTILITAIRE' | 'CAMION' | 'ENGIN';

/** Types proposes pour une famille ; « Autre » (sans famille) reste toujours propose. */
export function typesDeFamille(types: Referentiel[], famille: CategorieVehicule | null): Referentiel[] {
  return famille ? types.filter((t) => !t.categorie || t.categorie === famille) : types;
}

/** Marques qui fabriquent dans la famille ; une marque sans famille reste proposee. */
export function marquesDeFamille(marques: MarqueCamion[], famille: CategorieVehicule | null): MarqueCamion[] {
  return famille ? marques.filter((m) => !m.categories?.length || m.categories.includes(famille)) : marques;
}

export const CATEGORIES_VEHICULE: { valeur: CategorieVehicule; libelle: string }[] = [
  { valeur: 'VOITURE', libelle: 'Voiture' },
  { valeur: 'UTILITAIRE', libelle: 'Utilitaire, minibus et bus' },
  { valeur: 'CAMION', libelle: 'Camion et poids lourd' },
  { valeur: 'ENGIN', libelle: 'Engin de chantier' }
];

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
  typeCategorie?: CategorieVehicule;
  /** Dessin de profil de la carte (berline, suv, benne...). */
  typeDessin?: string;
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
  /** Familles ou la marque fabrique ; vide = toutes. */
  categories: CategorieVehicule[];
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
  garantNom?: string;
  garantLien?: string;
  garantTelephone?: string;
  garantTelephoneSecondaire?: string;
  garantAdresse?: string;
  garantProfession?: string;
  garantPieceIdentite?: string;
  garantRenseigne?: boolean;
  statut: StatutChauffeur;
  statutLibelle: string;
  observations?: string;
  actif: boolean;
}

export type ChauffeurRequete = Omit<
  Chauffeur,
  'id' | 'nomComplet' | 'statutLibelle' | 'permisBientotExpire' | 'garantRenseigne'
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
  | 'GARANT_PIECE_IDENTITE'
  | 'GARANT_PERMIS_CONDUIRE'
  | 'GARANT_ATTESTATION'
  | 'GARANT_JUSTIFICATIF'
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

/**
 * Couleur de chaque statut, commune a la vue du parc et a la liste des camions.
 * Elle sert de legende visuelle : un meme statut porte partout la meme teinte.
 */
export const COULEURS_STATUT_CAMION: Record<StatutCamion, string> = {
  DISPONIBLE: '#1e874b',
  EN_MISSION: '#1565c0',
  EN_LIVRAISON: '#1565c0',
  EN_LOCATION: '#e07b00',
  EN_MAINTENANCE: '#8455c8',
  IMMOBILISE: '#c62828',
  ACCIDENTE: '#c62828',
  HORS_SERVICE: '#6b7684',
  VENDU: '#6b7684'
};

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
  { valeur: 'GARANT_PIECE_IDENTITE', libelle: "Piece d'identite du garant" },
  { valeur: 'GARANT_PERMIS_CONDUIRE', libelle: 'Permis de conduire du garant' },
  { valeur: 'GARANT_ATTESTATION', libelle: 'Attestation de garantie' },
  { valeur: 'GARANT_JUSTIFICATIF', libelle: 'Justificatif du garant' },
  { valeur: 'AUTRE', libelle: 'Autre' }
];
