/** Rapports du point de vente, exports et imports. */

export type FormatColonne = 'TEXTE' | 'NOMBRE' | 'MONTANT' | 'POURCENT' | 'DATE' | 'DATE_HEURE';

export interface RapportCatalogue {
  type: string;
  libelle: string;
  famille: string;
  /** Faux pour un etat a l'instant (le stock), sans periode. */
  periode: boolean;
  /** Vrai pour un rapport sur un seul produit : la fiche de stock. */
  parArticle: boolean;
  /** Vrai pour un rapport sur un ecran de preparation (bons, produits). */
  parEcran: boolean;
}

export interface Rapport {
  type: string;
  titre: string;
  sousTitre: string;
  colonnes: { cle: string; libelle: string; format: FormatColonne }[];
  lignes: Record<string, unknown>[];
  totaux: Record<string, unknown>;
  indicateurs: { libelle: string; valeur: number; format: FormatColonne }[];
}

export type TypeImport = 'PRODUITS' | 'STOCK' | 'CATEGORIES';

export interface BilanImport {
  type: TypeImport;
  lignes: number;
  crees: number;
  modifies: number;
  inchanges: number;
  simulation: boolean;
  applique: boolean;
  erreurs: { ligne: number; message: string }[];
  /** Import des produits par archive ZIP : photos rattachees a un produit. */
  photos?: number;
  /** Ce qui n'empeche pas l'import : photo sans produit, fichier ignore... */
  avertissements?: string[];
}
