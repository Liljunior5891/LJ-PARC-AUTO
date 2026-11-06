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
  /** Format des documents imprimes, choisi par l'administrateur. */
  formatDocuments?: FormatDocuments;
}

export type FormatDocuments = 'A4' | 'A5';

export type SocieteRequete = Omit<Societe, 'logo'>;

/** Nom et logo affiches en tete de l'application. */
export interface IdentiteSociete {
  nom: string;
  sigle?: string;
  logo?: string | null;
}
