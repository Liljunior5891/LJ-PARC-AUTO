/**
 * Banques agreees de l'UEMOA (Benin, Burkina Faso, Cote d'Ivoire,
 * Guinee-Bissau, Mali, Niger, Senegal, Togo), regroupees par pays, pour les
 * listes de fournisseurs. Liste indicative : le choix « Autre » permet de
 * saisir un fournisseur absent.
 */
export interface PaysBanques {
  pays: string;
  banques: string[];
}

export const BANQUES_UEMOA: PaysBanques[] = [
  {
    pays: 'Togo',
    banques: [
      'Banque Atlantique Togo', 'Banque Internationale pour l\'Afrique au Togo (BIA-Togo)', 'Banque Populaire pour l\'Epargne et le Credit (BPEC)',
      'Banque Sahelo-Saharienne pour l\'Investissement et le Commerce (BSIC Togo)', 'Bank of Africa Togo', 'Coris Bank International Togo',
      'Ecobank Togo', 'Orabank Togo', 'Societe Generale Togo', 'Suntrust Bank Togo',
      'Union Togolaise de Banque (UTB)', 'NSIA Banque Togo', 'Vista Bank Togo'
    ]
  },
  {
    pays: 'Benin',
    banques: [
      'Bank of Africa Benin', 'Banque Atlantique Benin', 'Banque Internationale pour l\'Industrie et le Commerce (BIIC)',
      'BSIC Benin', 'Banque de l\'Habitat du Benin (BHB)', 'Coris Bank International Benin', 'Ecobank Benin', 'NSIA Banque Benin',
      'Orabank Benin', 'Societe Generale Benin', 'UBA Benin', 'CBAO Benin', 'BGFI Bank Benin'
    ]
  },
  {
    pays: 'Burkina Faso',
    banques: [
      'Bank of Africa Burkina Faso', 'Banque Atlantique Burkina Faso', 'Banque Commerciale du Burkina (BCB)',
      'Banque de l\'Union Burkina Faso (BDU-BF)', 'BSIC Burkina Faso', 'Banque Agricole du Faso (BADF)',
      'Coris Bank International', 'Ecobank Burkina Faso', 'Orabank Burkina Faso', 'Societe Generale Burkina Faso',
      'UBA Burkina Faso', 'Vista Bank Burkina Faso', 'Wendkuni Bank International', 'International Business Bank (IB Bank)'
    ]
  },
  {
    pays: 'Cote d\'Ivoire',
    banques: [
      'Banque Atlantique Cote d\'Ivoire', 'Bank of Africa Cote d\'Ivoire', 'Banque de l\'Habitat de Cote d\'Ivoire (BHCI)',
      'Banque Nationale d\'Investissement (BNI)', 'Banque Populaire de Cote d\'Ivoire', 'BGFI Bank Cote d\'Ivoire',
      'BSIC Cote d\'Ivoire', 'Bridge Bank Group', 'Citibank Cote d\'Ivoire', 'Coris Bank International Cote d\'Ivoire',
      'Ecobank Cote d\'Ivoire', 'Guaranty Trust Bank Cote d\'Ivoire', 'Mansa Bank', 'NSIA Banque Cote d\'Ivoire',
      'Orabank Cote d\'Ivoire', 'Societe Generale Cote d\'Ivoire', 'Standard Chartered Cote d\'Ivoire',
      'Societe Ivoirienne de Banque (SIB)', 'UBA Cote d\'Ivoire', 'Versus Bank', 'Stanbic Bank Cote d\'Ivoire'
    ]
  },
  {
    pays: 'Guinee-Bissau',
    banques: [
      'Banco da Africa Ocidental (BAO)', 'Banque Atlantique Guinee-Bissau', 'Ecobank Guinee-Bissau',
      'Orabank Guinee-Bissau', 'Coris Bank International Guinee-Bissau'
    ]
  },
  {
    pays: 'Mali',
    banques: [
      'Banque Atlantique Mali', 'Bank of Africa Mali', 'Banque de Developpement du Mali (BDM)', 'Banque Malienne de Solidarite (BMS)',
      'Banque Nationale de Developpement Agricole (BNDA)', 'Banque Commerciale du Sahel (BCS)', 'Banque Internationale pour le Mali (BIM)',
      'BSIC Mali', 'Coris Bank International Mali', 'Ecobank Mali', 'Orabank Mali', 'Banque pour le Commerce et l\'Industrie (BCI)',
      'Vista Bank Mali', 'UBA Mali'
    ]
  },
  {
    pays: 'Niger',
    banques: [
      'Bank of Africa Niger', 'Banque Atlantique Niger', 'Banque Agricole du Niger (BAGRI)', 'Banque Commerciale du Niger (BCN)',
      'Banque Internationale pour l\'Afrique au Niger (BIA-Niger)', 'Banque Islamique du Niger (BIN)', 'BSIC Niger',
      'Banque de l\'Habitat du Niger (BHN)', 'Coris Bank International Niger', 'Ecobank Niger', 'Orabank Niger',
      'Societe Nigerienne de Banque (SONIBANK)', 'UBA Niger'
    ]
  },
  {
    pays: 'Senegal',
    banques: [
      'Banque Atlantique Senegal', 'Bank of Africa Senegal', 'Banque Agricole (LBA)', 'Banque de l\'Habitat du Senegal (BHS)',
      'Banque Islamique du Senegal (BIS)', 'BNDE', 'BSIC Senegal', 'CBAO Groupe Attijariwafa Bank', 'Citibank Senegal',
      'Coris Bank International Senegal', 'Credit du Senegal', 'Credit International (CI)', 'Ecobank Senegal', 'NSIA Banque Senegal',
      'Orabank Senegal', 'Societe Generale Senegal', 'UBA Senegal', 'Banque de Dakar (BDK)', 'BGFI Bank Senegal'
    ]
  }
];

/** Toutes les banques, pour reconnaitre un fournisseur deja enregistre. */
export const TOUTES_BANQUES_UEMOA: string[] = BANQUES_UEMOA.flatMap((p) => p.banques);
