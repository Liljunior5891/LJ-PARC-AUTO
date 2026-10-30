/** Raccourcis de periode proposes a cote des dates (pilotage, rentabilite). */
export type Periode = 'mois' | 'trimestre' | 'annee';

/** Date locale au format AAAA-MM-JJ, celui des champs date. */
export function dateIso(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

export function aujourdhui(): string {
  return dateIso(new Date());
}

/** Premier jour du mois, du trimestre ou de l'annee en cours. */
export function debutPeriode(periode: Periode): string {
  const date = new Date();
  if (periode === 'annee') {
    return dateIso(new Date(date.getFullYear(), 0, 1));
  }
  if (periode === 'trimestre') {
    return dateIso(new Date(date.getFullYear(), Math.floor(date.getMonth() / 3) * 3, 1));
  }
  return dateIso(new Date(date.getFullYear(), date.getMonth(), 1));
}

/** Vrai si l'intervalle correspond exactement au raccourci, jusqu'a aujourd'hui. */
export function estPeriode(periode: Periode, debut: string, fin: string): boolean {
  return debut === debutPeriode(periode) && fin === aujourdhui();
}
