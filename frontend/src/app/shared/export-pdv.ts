import { Component, ElementRef, inject, input, signal } from '@angular/core';
import { FormatExport, OptionsRapport, RapportService, enregistrerFichier } from '../core/services/rapport.service';

/**
 * Bouton « Exporter » d'un rapport du point de vente : il deplie le choix du
 * format (Excel, PDF ou CSV). Le fichier est produit par le serveur, a
 * l'identique de l'ecran.
 */
@Component({
  selector: 'app-export-pdv',
  host: {
    '(document:click)': 'clicDehors($event)',
    '(document:keydown.escape)': 'ouvert.set(false)'
  },
  template: `
    <button type="button" class="bouton secondaire declencheur" [disabled]="desactive() || !!encours()"
            aria-haspopup="menu" [attr.aria-expanded]="ouvert()" (click)="ouvert.set(!ouvert())">
      {{ encours() ? 'Export...' : '⤓ Exporter' }} <span class="fleche" aria-hidden="true">▾</span>
    </button>
    @if (ouvert()) {
      <div class="menu-export" role="menu">
        @for (f of formats; track f.valeur) {
          <button type="button" role="menuitem" (click)="exporter(f.valeur)">
            <span class="icone" aria-hidden="true">{{ f.icone }}</span>
            <span><strong>{{ f.libelle }}</strong><small>{{ f.detail }}</small></span>
          </button>
        }
      </div>
    }
    @if (erreur()) { <small class="erreur-export">{{ erreur() }}</small> }
  `,
  styles: `
    :host { position: relative; display: inline-flex; flex-direction: column; align-items: flex-end; gap: 0.2rem; }
    .fleche { font-size: 0.75em; opacity: 0.7; }
    .menu-export {
      position: absolute; top: calc(100% + 6px); right: 0; z-index: 40;
      display: flex; flex-direction: column; min-width: 270px; padding: 0.35rem;
      border: 1px solid var(--couleur-bordure); border-radius: 12px;
      background: var(--couleur-surface); box-shadow: 0 12px 30px rgba(15, 30, 50, 0.16);
    }
    .menu-export button {
      display: flex; align-items: center; gap: 0.7rem; padding: 0.55rem 0.65rem;
      border: none; border-radius: 8px; background: none; color: var(--couleur-texte);
      font: inherit; text-align: left; cursor: pointer;
    }
    .menu-export button:hover, .menu-export button:focus-visible { background: var(--couleur-fond); outline: none; }
    .menu-export .icone { font-size: 1.25rem; }
    .menu-export span:last-child { display: flex; flex-direction: column; }
    .menu-export small { color: var(--couleur-texte-doux); font-size: 0.75rem; }
    .erreur-export { color: var(--couleur-danger); }
  `
})
export class ExportPdv {
  private readonly service = inject(RapportService);
  private readonly hote = inject(ElementRef<HTMLElement>);

  readonly type = input.required<string>();
  /** Periode et produit ; lus au moment du clic. */
  readonly options = input<OptionsRapport>({});
  readonly desactive = input(false);

  readonly formats: { valeur: FormatExport; libelle: string; detail: string; icone: string }[] = [
    { valeur: 'xlsx', libelle: 'Excel', detail: 'Classeur .xlsx, nombres calculables', icone: '📊' },
    { valeur: 'pdf', libelle: 'PDF', detail: 'Document a imprimer ou envoyer', icone: '📄' },
    { valeur: 'csv', libelle: 'CSV', detail: 'Texte pour tout tableur ou logiciel', icone: '🧾' }
  ];
  readonly ouvert = signal(false);
  readonly encours = signal<FormatExport | null>(null);
  readonly erreur = signal<string | null>(null);

  clicDehors(evenement: MouseEvent): void {
    if (this.ouvert() && !this.hote.nativeElement.contains(evenement.target as Node)) {
      this.ouvert.set(false);
    }
  }

  exporter(format: FormatExport): void {
    this.ouvert.set(false);
    this.encours.set(format);
    this.erreur.set(null);
    this.service.exporter(this.type(), format, this.options()).subscribe({
      next: (f) => {
        this.encours.set(null);
        enregistrerFichier(f);
      },
      error: () => {
        this.encours.set(null);
        this.erreur.set('Export impossible.');
      }
    });
  }
}
