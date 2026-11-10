import { Component, computed, input } from '@angular/core';
import { PointSerie } from '../../core/models/abonnement';

/**
 * Histogramme d'une seule serie sur douze mois : une teinte, barres fines aux
 * coins hauts arrondis, quadrillage discret, valeur au survol. Le titre de la
 * carte nomme la serie : pas de legende.
 */
@Component({
  selector: 'app-graphe-barres',
  template: `
    <div class="graphe" role="img" [attr.aria-label]="description()">
      <svg [attr.viewBox]="'0 0 ' + L + ' ' + H" preserveAspectRatio="none" class="zone">
        @for (g of graduations(); track g.y) {
          <line [attr.x1]="0" [attr.x2]="L" [attr.y1]="g.y" [attr.y2]="g.y" class="grille" />
        }
        @for (b of barres(); track b.cle) {
          <g class="barre" [class.courante]="b.derniere">
            <rect class="cible" [attr.x]="b.x - pas() / 2 + b.largeur / 2" y="0" [attr.width]="pas()" [attr.height]="H">
              <title>{{ b.libelle }} : {{ b.texte }}</title>
            </rect>
            <path [attr.d]="b.chemin" />
          </g>
        }
      </svg>
      <div class="graduations">
        @for (g of graduations(); track g.y) {
          <span [style.top.%]="(g.y / H) * 100">{{ g.texte }}</span>
        }
      </div>
      <div class="abscisses">
        @for (b of barres(); track b.cle; let i = $index) {
          <span [class.cache]="i % 2 === 1 && barres().length > 8">{{ b.libelle }}</span>
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .graphe { position: relative; padding-left: 44px; }
    .zone { width: 100%; height: 170px; display: block; overflow: visible; }
    .grille { stroke: var(--couleur-bordure); stroke-width: 1; vector-effect: non-scaling-stroke; }
    .barre path { fill: var(--teinte, var(--couleur-primaire-clair)); opacity: .55; transition: opacity .15s; }
    .barre.courante path { opacity: 1; }
    .barre:hover path { opacity: 1; }
    .cible { fill: transparent; }
    .graduations { position: absolute; left: 0; top: 0; width: 40px; height: 170px; }
    .graduations span { position: absolute; right: 0; transform: translateY(-50%); font-size: .68rem;
      color: var(--couleur-texte-doux); white-space: nowrap; }
    .abscisses { display: flex; margin-top: 6px; }
    .abscisses span { flex: 1; text-align: center; font-size: .68rem; color: var(--couleur-texte-doux); white-space: nowrap; }
    .abscisses span.cache { visibility: hidden; }
  `
})
export class GrapheBarres {
  readonly points = input<PointSerie[]>([]);
  /** Suffixe des valeurs au survol (devise, « inscription(s) »). */
  readonly unite = input('');
  readonly description = input('Histogramme');

  readonly L = 600;
  readonly H = 170;

  private readonly maximum = computed(() => {
    const max = Math.max(0, ...this.points().map((p) => p.valeur));
    if (max <= 0) {
      return 1;
    }
    const puissance = Math.pow(10, Math.floor(Math.log10(max)));
    const pasRond = [1, 2, 2.5, 5, 10].map((f) => f * puissance).find((v) => v * 4 >= max) ?? max;
    return pasRond * 4;
  });

  readonly pas = computed(() => this.L / Math.max(1, this.points().length));

  readonly graduations = computed(() => {
    const max = this.maximum();
    return [0, 1, 2, 3, 4].map((i) => ({
      y: this.H - (this.H * i) / 4,
      texte: this.abrege((max * i) / 4)
    }));
  });

  readonly barres = computed(() => {
    const pas = this.pas();
    const largeur = Math.min(28, pas * 0.55);
    const max = this.maximum();
    const n = this.points().length;
    return this.points().map((p, i) => {
      const hauteur = (p.valeur / max) * this.H;
      const x = i * pas + (pas - largeur) / 2;
      const r = Math.min(4, hauteur / 2, largeur / 2);
      const haut = this.H - hauteur;
      // Coins hauts arrondis, base posee sur l'axe.
      const chemin = hauteur <= 0 ? '' :
        `M${x},${this.H} V${haut + r} Q${x},${haut} ${x + r},${haut} H${x + largeur - r} ` +
        `Q${x + largeur},${haut} ${x + largeur},${haut + r} V${this.H} Z`;
      return {
        cle: p.cle, libelle: p.libelle, x, largeur, chemin, derniere: i === n - 1,
        texte: new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(p.valeur) + (this.unite() ? ' ' + this.unite() : '')
      };
    });
  });

  private abrege(v: number): string {
    if (v >= 1_000_000) {
      return (Math.round(v / 100_000) / 10).toString().replace('.', ',') + ' M';
    }
    if (v >= 1_000) {
      return (Math.round(v / 100) / 10).toString().replace('.', ',') + ' k';
    }
    return Math.round(v).toString();
  }
}
