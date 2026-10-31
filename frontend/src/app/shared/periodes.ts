/**
 * Periodes proposees dans les listes de filtre (rapports, tableau de bord) et
 * calcul de leurs bornes, dans le fuseau du poste. La semaine commence le lundi ;
 * la borne de fin est exclue (minuit du jour suivant).
 */
export type Periode =
  | 'AUJOURDHUI' | 'HIER' | 'CETTE_SEMAINE' | 'SEMAINE_DERNIERE'
  | 'CE_MOIS' | 'CE_TRIMESTRE' | 'CETTE_ANNEE' | 'DEPUIS_DEBUT' | 'PERSONNALISEE';

export const PERIODES: { valeur: Periode; libelle: string }[] = [
  { valeur: 'AUJOURDHUI', libelle: "Aujourd'hui" },
  { valeur: 'HIER', libelle: 'Hier' },
  { valeur: 'CETTE_SEMAINE', libelle: 'Cette semaine' },
  { valeur: 'SEMAINE_DERNIERE', libelle: 'La semaine derniere' },
  { valeur: 'CE_MOIS', libelle: 'Ce mois' },
  { valeur: 'CE_TRIMESTRE', libelle: 'Ce trimestre' },
  { valeur: 'CETTE_ANNEE', libelle: 'Cette annee' },
  { valeur: 'DEPUIS_DEBUT', libelle: 'Depuis le debut' },
  { valeur: 'PERSONNALISEE', libelle: 'Entre deux dates...' }
];

/**
 * Bornes d'une periode. Pour PERSONNALISEE, les dates libres au format
 * AAAA-MM-JJ, la seconde incluse.
 */
export function bornesPeriode(periode: Periode, du?: string, au?: string): { debut: Date; fin: Date } {
  const maintenant = new Date();
  const a = maintenant.getFullYear();
  const m = maintenant.getMonth();
  const j = maintenant.getDate();
  const lundi = j - ((maintenant.getDay() + 6) % 7);
  const demain = new Date(a, m, j + 1);
  switch (periode) {
    case 'AUJOURDHUI':
      return { debut: new Date(a, m, j), fin: demain };
    case 'HIER':
      return { debut: new Date(a, m, j - 1), fin: new Date(a, m, j) };
    case 'CETTE_SEMAINE':
      return { debut: new Date(a, m, lundi), fin: demain };
    case 'SEMAINE_DERNIERE':
      return { debut: new Date(a, m, lundi - 7), fin: new Date(a, m, lundi) };
    case 'CE_TRIMESTRE':
      return { debut: new Date(a, Math.floor(m / 3) * 3, 1), fin: demain };
    case 'CETTE_ANNEE':
      return { debut: new Date(a, 0, 1), fin: demain };
    case 'DEPUIS_DEBUT':
      return { debut: new Date(2000, 0, 1), fin: demain };
    case 'PERSONNALISEE': {
      const [ad, md, jd] = (du ?? '').split('-').map(Number);
      const [af, mf, jf] = (au ?? '').split('-').map(Number);
      return { debut: new Date(ad, md - 1, jd), fin: new Date(af, mf - 1, jf + 1) };
    }
    default:
      return { debut: new Date(a, m, 1), fin: demain };
  }
}

/** Date locale au format AAAA-MM-JJ, celui des champs de date. */
export function dateIso(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/** Dates libres pre-remplies avec une periode : la fin affichee est le dernier jour inclus. */
export function datesDePeriode(periode: Periode): { du: string; au: string } {
  const { debut, fin } = bornesPeriode(periode);
  return { du: dateIso(debut), au: dateIso(new Date(fin.getTime() - 1)) };
}
