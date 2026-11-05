/**
 * Touches de la rangee des chiffres d'un clavier AZERTY, sans Majuscule.
 * Une douchette reglee en QWERTY branchee sur un poste AZERTY envoie ces
 * caracteres au lieu des chiffres : « 8901090312320 » arrive « _çà&àçà"&é"éà ».
 */
const AZERTY: Record<string, string> = {
  '&': '1', 'é': '2', '"': '3', "'": '4', '(': '5',
  '-': '6', 'è': '7', '_': '8', 'ç': '9', 'à': '0'
};

/** Plus de 100 ms entre deux touches : c'est une frappe humaine. */
const ECART_HUMAIN = 100;
/** Silence apres lequel une lecture sans Entree est consideree terminee. */
const FIN_DE_LECTURE = 250;

/**
 * Remet en chiffres un code lu a la douchette sur un clavier AZERTY. Une
 * saisie ordinaire (nom de produit, code article) est rendue telle quelle :
 * seule une suite d'au moins 4 caracteres, faite uniquement de chiffres et
 * de touches de cette rangee, est convertie.
 */
export function normaliserCodeBarres(valeur: string): string {
  const v = valeur.trim();
  if (v.length < 4 || ![...v].every((c) => c in AZERTY || (c >= '0' && c <= '9'))) {
    return v;
  }
  return [...v].map((c) => AZERTY[c] ?? c).join('');
}

/** Forme d'un numero de bon de retrait : BR-2026-0001. */
const NUMERO_BON = /^BR-\d{4}-\d+$/;

/**
 * Numero de bon lu a la douchette. Reglee en QWERTY sur un poste AZERTY, elle remplace
 * le tiret par « ° » ou « ) », et parfois les chiffres par la rangee « é"'(... ». Le numero
 * est remis en place. Tout autre texte (un nom de client, une saisie en cours) reste tel quel.
 */
export function normaliserNumeroBon(valeur: string): string {
  const v = valeur.trim();
  // Le tiret n'est un chiffre que si les autres chiffres sont eux aussi deformes.
  const chiffresDeformes = [...v].some((c) => c in AZERTY && c !== '-');
  const candidat = [...v]
    .map((c) => (c === ')' || c === '°' ? '-' : chiffresDeformes ? AZERTY[c] ?? c : c))
    .join('')
    .toUpperCase();
  return NUMERO_BON.test(candidat) ? candidat : v;
}

/**
 * Recueille les lectures de douchette tapees hors de tout champ de saisie
 * (focus sur un bouton, une categorie, ecran de telephone...). Une lecture
 * est une rafale rapide d'au moins 4 caracteres, terminee par Entree ou par
 * un court silence pour les douchettes qui n'envoient pas Entree.
 */
export class LecteurDouchette {
  private tampon = '';
  private derniere = 0;
  private minuterie?: ReturnType<typeof setTimeout>;

  /** @param lu recoit le code remis en chiffres, et s'il a ete clos par Entree. */
  constructor(private readonly lu: (code: string, parEntree: boolean) => void) {}

  /** A brancher sur keydown du document ; `actif` faux pendant une fenetre modale. */
  touche(e: KeyboardEvent, actif = true): void {
    const cible = e.target as HTMLElement | null;
    if (!actif || e.ctrlKey || e.altKey || e.metaKey ||
        cible?.closest('input, textarea, select, [contenteditable="true"]')) {
      return;
    }
    const maintenant = Date.now();
    if (maintenant - this.derniere > ECART_HUMAIN) {
      this.tampon = '';
    }
    this.derniere = maintenant;
    clearTimeout(this.minuterie);
    if (e.key === 'Enter') {
      if (this.tampon.length >= 4) {
        e.preventDefault();
        this.lu(normaliserCodeBarres(this.tampon), true);
      }
      this.tampon = '';
    } else if (e.key.length === 1) {
      this.tampon += e.key;
      this.minuterie = setTimeout(() => {
        if (this.tampon.length >= 4) {
          this.lu(normaliserCodeBarres(this.tampon), false);
        }
        this.tampon = '';
      }, FIN_DE_LECTURE);
    }
  }

  arreter(): void {
    clearTimeout(this.minuterie);
  }
}

/**
 * Appelle `rappel` quand la frappe s'arrete dans un champ de recherche : une
 * douchette sans Entree y laisse le code, qu'on reconnait alors tout seul.
 */
export class FinDeSaisie {
  private minuterie?: ReturnType<typeof setTimeout>;

  constructor(private readonly rappel: () => void) {}

  relancer(): void {
    clearTimeout(this.minuterie);
    this.minuterie = setTimeout(this.rappel, FIN_DE_LECTURE);
  }

  arreter(): void {
    clearTimeout(this.minuterie);
  }
}
