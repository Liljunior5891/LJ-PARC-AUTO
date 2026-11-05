import { AfterViewInit, Component, ElementRef, OnDestroy, effect, inject, input, output, untracked } from '@angular/core';
import { Page } from '../core/models/models';

/** Distance du bas de la liste, en pixels, a laquelle la suite est demandee. */
const SEUIL = 160;

/** Ce que la liste affichee doit dire d'elle-meme. */
export interface ListeAffichee {
  last: boolean;
  content: unknown[];
  totalElements: number;
  number?: number;
}

/**
 * Numero de la prochaine page a demander au serveur. Un chargement ordinaire
 * (filtre, retour d'une modification) repart de la premiere page ; seule une
 * demande de suite vise la page d'apres, une seule fois tant qu'elle n'est pas arrivee.
 */
export class SuiteDePages {
  private demande = 0;
  private enAttente = -1;

  /** Numero a envoyer au serveur pour ce chargement. */
  numero(): number {
    const numero = this.demande;
    this.demande = 0;
    this.enAttente = numero;
    return numero;
  }

  /** Vrai s'il faut charger la suite de cette page. */
  demander(page: Page<unknown> | null): boolean {
    if (!page || page.last || page.number + 1 === this.enAttente) {
      return false;
    }
    this.demande = page.number + 1;
    return true;
  }
}

/** La premiere page remplace la liste ; les suivantes s'ajoutent aux lignes deja affichees. */
export function cumulerPage<T>(precedente: Page<T> | null, nouvelle: Page<T>): Page<T> {
  if (!precedente || nouvelle.number === 0) {
    return nouvelle;
  }
  const deja = new Set<unknown>(precedente.content.map((l) => (l as { id?: unknown }).id).filter((id) => id !== undefined));
  return {
    ...nouvelle,
    content: [...precedente.content, ...nouvelle.content.filter((l) => !deja.has((l as { id?: unknown }).id))]
  };
}

/**
 * Bas d'une liste qui se charge au defilement, sans pagination. Pose juste apres le
 * tableau, il surveille son defilement interne et demande la suite a l'approche de la fin,
 * ou tout de suite si les lignes ne remplissent pas encore la hauteur disponible.
 */
@Component({
  selector: 'app-suite-liste',
  template: `
    @if (!page().last) {
      <div class="suite-liste" role="status">Chargement de la suite au defilement · {{ page().content.length }} sur {{ page().totalElements }}</div>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .suite-liste {
      padding: 0.4rem 0 0;
      color: var(--couleur-texte-doux);
      font-size: 0.75rem;
      text-align: center;
    }
  `
})
export class SuiteListe implements AfterViewInit, OnDestroy {
  private readonly hote = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly page = input.required<ListeAffichee>();
  readonly suite = output<void>();

  private conteneur: HTMLElement | null = null;
  private redimensionnement?: ResizeObserver;
  private dernierNumero = 0;
  private minuterie?: ReturnType<typeof setTimeout>;
  private readonly verifier = () => this.verifierMaintenant();

  constructor() {
    effect(() => {
      const page = this.page();
      untracked(() => {
        // Liste remplacee (filtre, nouvelle recherche) : on revient en haut.
        if (page.number === 0 && this.dernierNumero > 0 && this.conteneur) {
          this.conteneur.scrollTop = 0;
        }
        this.dernierNumero = page.number ?? 0;
        // Le temps que les lignes s'affichent et que le tableau recoive sa hauteur.
        clearTimeout(this.minuterie);
        this.minuterie = setTimeout(this.verifier, 80);
      });
    });
  }

  ngAfterViewInit(): void {
    this.conteneur = this.trouverConteneur();
    if (this.conteneur) {
      this.conteneur.addEventListener('scroll', this.verifier, { passive: true });
      this.redimensionnement = new ResizeObserver(this.verifier);
      this.redimensionnement.observe(this.conteneur);
    }
    this.verifier();
  }

  ngOnDestroy(): void {
    clearTimeout(this.minuterie);
    this.conteneur?.removeEventListener('scroll', this.verifier);
    this.redimensionnement?.disconnect();
  }

  private verifierMaintenant(): void {
    const c = this.conteneur;
    if (c && !this.page().last && c.scrollHeight - c.scrollTop - c.clientHeight < SEUIL) {
      this.suite.emit();
    }
  }

  /** La liste qui precede ce composant dans la page. */
  private trouverConteneur(): HTMLElement | null {
    for (let frere = this.hote.nativeElement.previousElementSibling; frere; frere = frere.previousElementSibling) {
      if (frere.matches('.tableau-conteneur, .liste-defilante')) {
        return frere as HTMLElement;
      }
      const dedans = frere.querySelector<HTMLElement>('.tableau-conteneur, .liste-defilante');
      if (dedans) {
        return dedans;
      }
    }
    return null;
  }
}
