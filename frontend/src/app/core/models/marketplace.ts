/** Module Marketplace : boutique en ligne, offres, commandes et place de marche. */

export type TypeOffre = 'ARTICLE' | 'VEHICULE' | 'LIBRE';
export type ModeRemise = 'RETRAIT' | 'LIVRAISON';
export type ModePaiementEnLigne = 'A_LA_REMISE' | 'MOBILE_MONEY';
export type StatutPaiementEnLigne = 'EN_ATTENTE' | 'PAYEE' | 'ECHOUEE' | 'REMBOURSEE';
export type StatutCommandeEnLigne = 'NOUVELLE' | 'CONFIRMEE' | 'PRETE' | 'EN_LIVRAISON' | 'LIVREE' | 'ANNULEE';
export type StatutDemandeLocation = 'NOUVELLE' | 'ACCEPTEE' | 'REFUSEE' | 'ANNULEE' | 'EXPIREE' | 'CONCLUE';

export interface Boutique {
  /** Absent tant que la boutique n'a jamais ete enregistree (l'API omet les champs vides). */
  id?: number | null;
  slug: string;
  nom: string;
  slogan?: string;
  description?: string;
  categorie?: string;
  couverture?: string;
  logo?: string;
  telephone?: string;
  whatsapp?: string;
  email?: string;
  adresse?: string;
  ville?: string;
  horaires?: string;
  publiee: boolean;
  retraitActif: boolean;
  livraisonActive: boolean;
  fraisLivraison?: number | null;
  zoneLivraison?: string;
  paiementRemise: boolean;
  paiementMobileMoney: boolean;
  messageConfirmation?: string;
  /** Faux tant qu'aucun fournisseur de paiement en ligne n'est branche sur la plateforme. */
  mobileMoneyDisponible: boolean;
}

export type BoutiqueRequete = Omit<Boutique, 'id' | 'logo' | 'mobileMoneyDisponible'>;

export interface Offre {
  id: number;
  type: TypeOffre;
  articleId?: number;
  camionId?: number;
  titre: string;
  description?: string;
  categorie?: string;
  /** Prix propre a la boutique ; vide pour suivre celui de l'article. */
  prix?: number | null;
  prixAffiche: number;
  unitePrix?: string;
  caution?: number | null;
  image?: string;
  imageAffichee?: string;
  stock?: number | null;
  /** Quantite encore vendable ; vide = sans limite. */
  disponible?: number | null;
  publiee: boolean;
  miseEnAvant: boolean;
  ordre: number;
  source: string;
}

export interface OffreRequete {
  type: TypeOffre;
  articleId?: number | null;
  camionId?: number | null;
  titre: string;
  description?: string;
  categorie?: string;
  prix?: number | null;
  unitePrix?: string;
  caution?: number | null;
  image?: string | null;
  stock?: number | null;
  publiee: boolean;
  miseEnAvant: boolean;
  ordre: number;
}

export interface SourceArticle {
  id: number;
  code: string;
  nom: string;
  categorie?: string;
  prix: number;
  image?: string;
  suiviStock: boolean;
  stock: number;
  publiee: boolean;
}

export interface SourceVehicule {
  id: number;
  immatriculation: string;
  libelle: string;
  type?: string;
  photo?: string;
  statut: string;
  publiee: boolean;
}

export interface SourcesOffres {
  pointDeVente: boolean;
  gestionParc: boolean;
  articles: SourceArticle[];
  vehicules: SourceVehicule[];
}

export interface LigneCommandeEnLigne {
  offreId?: number;
  articleId?: number;
  libelle: string;
  prixUnitaire: number;
  quantite: number;
  total: number;
}

export interface CommandeEnLigne {
  id: number;
  numero: string;
  jeton: string;
  clientId?: number;
  clientNom: string;
  clientTelephone: string;
  clientEmail?: string;
  modeRemise: ModeRemise;
  modeRemiseLibelle: string;
  adresseLivraison?: string;
  modePaiement: ModePaiementEnLigne;
  modePaiementLibelle: string;
  statutPaiement: StatutPaiementEnLigne;
  statut: StatutCommandeEnLigne;
  statutLibelle: string;
  sousTotal: number;
  fraisLivraison: number;
  total: number;
  note?: string;
  motifAnnulation?: string;
  dateCommande: string;
  lignes: LigneCommandeEnLigne[];
  /** Traitee par la caisse du point de vente (acceptation, preparation, encaissement). */
  enCaisse: boolean;
  /** Restaurant : commande de salle qui la porte jusqu'aux ecrans de preparation. */
  pdvCommandeId?: number;
  /** Nom de famille et prenoms saisis separement ; absents pour les commandes anterieures. */
  clientNomFamille?: string;
  clientPrenoms?: string;
}

export interface DemandeLocationEnLigne {
  id: number;
  numero: string;
  jeton: string;
  offreId?: number;
  camionId: number;
  vehicule: string;
  clientId?: number;
  clientNom: string;
  clientTelephone: string;
  clientEmail?: string;
  dateDebut: string;
  dateFin: string;
  jours: number;
  tarifJour: number;
  montantEstime: number;
  lieuDepart?: string;
  destination?: string;
  message?: string;
  statut: StatutDemandeLocation;
  statutLibelle: string;
  motifRefus?: string;
  locationId?: number;
  locationNumero?: string;
  dateDemande: string;
  /** Recette enregistree en agence qui conclut la demande. */
  recetteId?: number;
  recetteNumero?: string;
  /** Nom de famille et prenoms saisis separement ; absents pour les demandes anterieures. */
  clientNomFamille?: string;
  clientPrenoms?: string;
  caution?: number | null;
}

/** Location en ligne d'un vehicule du parc. */
export interface VehiculeEnLigne {
  camionId: number;
  offreId?: number;
  publiee: boolean;
  tarifJour?: number | null;
  caution?: number | null;
}

/** Reglage de la location en ligne dans la fiche vehicule. */
export interface ReglageLocationEnLigne {
  publiee: boolean;
  tarifJour: number | null;
  caution: number | null;
  /** Autres photos montrees dans la boutique en ligne. */
  photos: string[];
}

export interface ResumeMarketplace {
  commandesNouvelles: number;
  commandesEnCours: number;
  demandesNouvelles: number;
  offresPubliees: number;
  boutiquePubliee: boolean;
  slug: string;
}

// ------------------------------------------------------------ Vues publiques

export interface OffrePublique {
  id: number;
  type: TypeOffre;
  titre: string;
  description?: string;
  categorie?: string;
  prix: number;
  unitePrix?: string;
  caution?: number | null;
  image?: string;
  disponible: boolean;
  /** Quantite maximale commandable ; vide = sans limite. */
  quantiteMax?: number | null;
  miseEnAvant: boolean;
  /** Nombre de photos supplementaires (galerie ouverte au clic sur la photo). */
  photos: number;
}

export interface Vitrine {
  slug: string;
  nom: string;
  slogan?: string;
  description?: string;
  categorie?: string;
  couverture?: string;
  logo?: string;
  telephone?: string;
  whatsapp?: string;
  adresse?: string;
  ville?: string;
  horaires?: string;
  retraitActif: boolean;
  livraisonActive: boolean;
  fraisLivraison?: number | null;
  zoneLivraison?: string;
  paiementRemise: boolean;
  paiementMobileMoney: boolean;
  offres: OffrePublique[];
  /** Faux quand la boutique ne prend pas de commande (caisse du point de vente fermee). */
  ouverte: boolean;
  messageFermeture?: string;
}

export interface DemandeCommande {
  /** Nom de famille. */
  nom: string;
  prenoms: string;
  telephone: string;
  email?: string;
  modeRemise: ModeRemise;
  adresseLivraison?: string;
  modePaiement: ModePaiementEnLigne;
  note?: string;
  lignes: { offreId: number; quantite: number }[];
}

export interface DemandeLocationPublique {
  offreId: number;
  /** Nom de famille. */
  nom: string;
  prenoms: string;
  telephone: string;
  email?: string;
  dateDebut: string;
  dateFin: string;
  lieuDepart?: string;
  destination?: string;
  message?: string;
}

export interface SuiviEnLigne {
  nature: 'COMMANDE' | 'LOCATION';
  boutique: string;
  slug: string;
  telephone?: string;
  whatsapp?: string;
  messageConfirmation?: string;
  commande?: CommandeEnLigne;
  location?: DemandeLocationEnLigne;
  /** Adresse ou poursuivre le paiement en ligne, s'il est attendu. */
  urlPaiement?: string;
  paiementSimule: boolean;
}

export interface BoutiqueMarche {
  slug: string;
  nom: string;
  slogan?: string;
  categorie?: string;
  ville?: string;
  logo?: string;
  couverture?: string;
  offres: number;
}

export interface OffreMarche {
  id: number;
  slug: string;
  boutique: string;
  ville?: string;
  type: TypeOffre;
  titre: string;
  categorie?: string;
  prix: number;
  unitePrix?: string;
  image?: string;
}

export interface Marche {
  boutiques: BoutiqueMarche[];
  offres: OffreMarche[];
  categories: string[];
  villes: string[];
}

/** Rayons proposes pour classer une boutique sur la place de marche. */
export const CATEGORIES_BOUTIQUE = [
  'Alimentation', 'Restauration', 'Boissons', 'Mode et beaute', 'Electronique', 'Maison',
  'Quincaillerie et BTP', 'Sante', 'Location de vehicules', 'Transport et logistique', 'Services', 'Autre'
];

export const LIBELLES_TYPE_OFFRE: Record<TypeOffre, string> = {
  ARTICLE: 'Article du point de vente',
  VEHICULE: 'Vehicule a louer',
  LIBRE: 'Produit de la boutique'
};

/** Etapes proposees depuis chaque etat d'une commande, avec le libelle du bouton. */
export const ETAPES_COMMANDE: Record<StatutCommandeEnLigne, { statut: StatutCommandeEnLigne; libelle: string }[]> = {
  NOUVELLE: [{ statut: 'CONFIRMEE', libelle: 'Confirmer la commande' }],
  CONFIRMEE: [{ statut: 'PRETE', libelle: 'Marquer prete' }, { statut: 'LIVREE', libelle: 'Remise au client' }],
  PRETE: [{ statut: 'EN_LIVRAISON', libelle: 'Partie en livraison' }, { statut: 'LIVREE', libelle: 'Remise au client' }],
  EN_LIVRAISON: [{ statut: 'LIVREE', libelle: 'Livree' }],
  LIVREE: [],
  ANNULEE: []
};
