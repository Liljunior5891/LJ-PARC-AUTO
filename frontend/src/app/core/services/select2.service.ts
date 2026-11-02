import { Injectable, afterEveryRender } from '@angular/core';

/**
 * Listes concernees. Une liste qui doit rester native porte l'attribut
 * data-natif.
 */
const SELECTEUR = 'select:not([data-natif])';

/**
 * Toutes les listes ont leur zone de saisie, quel que soit le nombre
 * d'options, pour choisir au clavier sans faire defiler. Une liste qui n'en
 * veut pas porte data-recherche="jamais".
 */
const SANS_RECHERCHE = Infinity;

/** Ce que Select2 transmet pour une option : son texte et, s'il existe, l'element d'origine. */
interface OptionRendue {
  text: string;
  element?: unknown;
}

/**
 * Applique Select2 a toutes les listes deroulantes de l'application.
 * <p>
 * Le select natif reste la source de verite : Angular (ngModel,
 * formControlName, (change)) continue de le lire et de l'ecrire. Ce service
 * fait le pont dans les deux sens :
 * <ul>
 *   <li>un choix fait dans Select2 est renvoye au select sous forme d'evenement
 *       natif, seul ecoute par Angular ;</li>
 *   <li>une valeur posee par Angular est reportee dans l'affichage de Select2
 *       apres chaque rendu.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class Select2Service {
  /** Listes enrichies, avec l'etat affiche lors du dernier rendu. */
  private readonly enrichies = new Map<HTMLSelectElement, string>();

  constructor() {
    afterEveryRender(() => this.synchroniser());
  }

  private synchroniser(): void {
    for (const [select, etat] of this.enrichies) {
      if (!select.isConnected) {
        this.retirer(select);
      } else if (this.etat(select) !== etat) {
        // Espace de noms select2 : seul l'affichage se met a jour, Angular n'est pas relance.
        $(select).trigger('change.select2');
        this.enrichies.set(select, this.etat(select));
      }
    }
    document.querySelectorAll<HTMLSelectElement>(SELECTEUR).forEach((select) => {
      if (!this.enrichies.has(select)) {
        this.enrichir(select);
      }
    });
  }

  private enrichir(select: HTMLSelectElement): void {
    const $select = $(select);
    $select.select2({
      language: 'fr',
      width: '100%',
      minimumResultsForSearch: select.dataset['recherche'] === 'jamais' ? SANS_RECHERCHE : 0,
      templateResult: (option) => this.rendu(option, true),
      templateSelection: (option) => this.rendu(option, false),
      matcher: (parametres, option) => this.correspond(parametres, option)
    });
    // Select2 declenche un evenement jQuery, invisible pour Angular : on le
    // relaie en evenement natif. Ce dernier repasse ici avec originalEvent
    // renseigne, ce qui evite la boucle.
    $select.on('change', (evenement) => {
      if (!evenement.originalEvent) {
        select.dispatchEvent(new Event('change', { bubbles: true }));
      }
    });
    this.saisieDirecte($select);
    // Le select natif etant masque, c'est la fermeture qui marque le champ comme visite.
    $select.on('select2:close', () => select.dispatchEvent(new Event('blur')));
    this.enrichies.set(select, this.etat(select));
  }

  /**
   * Saisie directe : une lettre tapee sur la liste fermee (atteinte au clavier
   * ou deja cliquee) l'ouvre et part dans la zone de recherche. On tape le
   * debut du libelle puis Entree, sans passer par la souris.
   */
  private saisieDirecte($select: JQuery<HTMLSelectElement>): void {
    const instance = $select.data('select2');
    instance?.$container.find('.select2-selection').on('keydown', (evenement: JQuery.KeyDownEvent) => {
      const caractere = evenement.key;
      if (caractere.length !== 1 || caractere === ' ' || evenement.ctrlKey || evenement.metaKey
          || evenement.altKey || instance.$container.hasClass('select2-container--open')) {
        return;
      }
      evenement.preventDefault();
      $select.select2('open');
      const champ = instance.$dropdown.find('.select2-search__field');
      champ.val(caractere).trigger('input').trigger('focus');
    });
  }

  /**
   * Option enrichie : pastille de couleur (logo ou initiales) et, dans la
   * liste ouverte, une ligne de detail. Sans attribut data-*, texte simple.
   */
  private rendu(option: OptionRendue, avecDetail: boolean): string | JQuery {
    const donnees = (option.element as HTMLOptionElement | undefined)?.dataset;
    if (!donnees || (!donnees['couleur'] && !donnees['detail'])) {
      return option.text;
    }
    const ligne = $('<span class="s2-option"></span>');
    if (donnees['couleur']) {
      const pastille = $('<span class="s2-pastille"></span>').css('background', donnees['couleur']);
      if (donnees['logo']) {
        pastille.append($('<img alt="">').attr('src', donnees['logo']));
      } else {
        pastille.text(donnees['monogramme'] ?? '');
      }
      ligne.append(pastille);
    }
    const textes = $('<span class="s2-textes"></span>').append($('<span></span>').text(option.text));
    if (avecDetail && donnees['detail']) {
      textes.append($('<small></small>').text(donnees['detail']));
    }
    return ligne.append(textes);
  }

  /** Recherche sur le libelle et la ligne de detail, sans tenir compte des accents. */
  private correspond<T extends OptionRendue>(parametres: { term?: string }, option: T): T | null {
    const terme = this.normaliser(parametres.term ?? '');
    if (!terme) {
      return option;
    }
    const detail = (option.element as HTMLOptionElement | undefined)?.dataset['detail'] ?? '';
    return this.normaliser(`${option.text} ${detail}`).includes(terme) ? option : null;
  }

  private normaliser(texte: string): string {
    return texte.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').trim();
  }

  /** Le select a quitte la page : son habillage, insere a cote de lui, doit suivre. */
  private retirer(select: HTMLSelectElement): void {
    const $select = $(select);
    const conteneur = $select.data('select2')?.$container;
    try {
      $select.select2('destroy');
    } catch {
      // Deja detruit : rien a faire.
    }
    conteneur?.remove();
    this.enrichies.delete(select);
  }

  /** Valeur, libelle choisi et nombre d'options : tout ce que Select2 affiche. */
  private etat(select: HTMLSelectElement): string {
    const choisie = select.selectedOptions[0];
    return `${select.value}|${choisie?.text ?? ''}|${select.options.length}`;
  }
}
