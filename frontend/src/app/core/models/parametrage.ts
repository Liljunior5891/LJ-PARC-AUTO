/** Identite de l'entreprise (chapitre 39). */
export interface Societe {
  nom: string;
  sigle?: string;
  formeJuridique?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  telephone?: string;
  email?: string;
  siteWeb?: string;
  nif?: string;
  rccm?: string;
  banque?: string;
  compteBancaire?: string;
  mentionsFacture?: string;
  /** Taux de TVA applique aux factures, en pourcentage. */
  tauxTva: number;
  /** Logo pret a etre affiche : « data:image/png;base64,... ». */
  logo?: string | null;
}

export type SocieteRequete = Omit<Societe, 'logo'>;

/** Ce que la page de connexion affiche avant toute authentification. */
export interface IdentiteSociete {
  nom: string;
  sigle?: string;
  logo?: string | null;
}

/** Ce que le site vitrine publie aux visiteurs : identite et coordonnees. */
export interface VitrineSociete extends IdentiteSociete {
  formeJuridique?: string;
  adresse?: string;
  ville?: string;
  pays?: string;
  telephone?: string;
  email?: string;
  siteWeb?: string;
}
