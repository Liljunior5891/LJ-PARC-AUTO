import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, LOCALE_ID, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { TableauBordPdv } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { grosMontant } from '../../../shared/montant';
import { PERIODES, Periode, bornesPeriode, datesDePeriode } from '../../../shared/periodes';

/** Geometrie fixe des graphiques SVG ; le navigateur les met a l'echelle. */
const RAYON_DONUT = 60;
const CIRCONFERENCE = 2 * Math.PI * RAYON_DONUT;
const ECART_ARC = 3;
const LARGEUR_COURBES = 820;
const HAUTEUR_COURBES = 260;
const MARGE_GAUCHE = 44;

/**
 * Tableau de bord du point de vente, sur le modele de celui du parc : en tete
 * ce que la vente rapporte, ce qu'elle coute et ce qu'elle degage ; puis
 * l'activite (caisses, salle en restauration), les alertes de stock, la
 * repartition par categorie, les derniers tickets, l'evolution, les
 * meilleures ventes, la marge par periode et l'affluence par heure.
 */
@Component({
  selector: 'app-tableau-bord-pdv',
  imports: [DecimalPipe, DatePipe, FormsModule, RouterLink, NgTemplateOutlet],
  templateUrl: './tableau-bord-pdv.html',
  styleUrls: ['../../tableau-bord/tableau-bord.scss', './tableau-bord-pdv.scss']
})
export class TableauBordPdvPage implements OnInit {
  private readonly service = inject(PdvService);
  private readonly locale = inject(LOCALE_ID);
  readonly restauration = inject(AuthService).estRestauration();

  readonly portees = PERIODES;
  readonly portee = signal<Periode>('CE_MOIS');
  /** Dates libres de la periode PERSONNALISEE (AAAA-MM-JJ, la seconde incluse). */
  readonly dateDebut = signal(datesDePeriode('CE_MOIS').du);
  readonly dateFin = signal(datesDePeriode('CE_MOIS').au);
  readonly datesValides = computed(() => !!this.dateDebut() && !!this.dateFin() && this.dateDebut() <= this.dateFin());
  readonly donnees = signal<TableauBordPdv | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);

  readonly libellePeriode = computed(() => {
    if (this.portee() === 'PERSONNALISEE') {
      const date = (iso: string) => iso.split('-').reverse().join('/');
      return `du ${date(this.dateDebut())} au ${date(this.dateFin())}`;
    }
    return this.portees.find((p) => p.valeur === this.portee())!.libelle;
  });

  readonly couleurVentes = '#22a27a';
  readonly couleurMarge = '#2f6fd6';
  readonly couleurAffluence = '#22c3a6';
  readonly paletteDonut = ['#ff8c1a', '#2f6fd6', '#7c9a3c', '#1f6b52', '#22c3a6', '#a855f7', '#d9b24a'];
  readonly circonference = CIRCONFERENCE;
  readonly largeurCourbes = LARGEUR_COURBES;
  readonly hauteurCourbes = HAUTEUR_COURBES;

  /** Moyens de paiement en barre continue, comme l'etat du parc. */
  readonly modes = computed(() => {
    const d = this.donnees();
    const couleurs: Record<string, string> = { ESPECES: '#22c3a6', MOBILE_MONEY: '#ff8c1a', CARTE: '#2f6fd6', AUTRE: '#a855f7' };
    return (d?.parMode ?? []).map((m) => ({ ...m, couleur: couleurs[m.mode] ?? '#8a94a0' }));
  });

  /** Ventes par categorie en anneau, total au centre. */
  readonly arcsDonut = computed(() => {
    const lignes = this.donnees()?.parCategorie ?? [];
    const total = lignes.reduce((s, l) => s + l.chiffreAffaires, 0);
    let cumul = 0;
    return lignes.map((ligne, i) => {
      const part = total === 0 ? 0 : ligne.chiffreAffaires / total;
      const arc = {
        libelle: ligne.libelle,
        icone: ligne.icone,
        total: ligne.chiffreAffaires,
        pourcent: Math.round(part * 100),
        couleur: ligne.couleur || this.paletteDonut[i % this.paletteDonut.length],
        longueur: Math.max(part * CIRCONFERENCE - ECART_ARC, 0),
        decalage: -cumul * CIRCONFERENCE
      };
      cumul += part;
      return arc;
    });
  });

  readonly totalCategories = computed(() =>
    (this.donnees()?.parCategorie ?? []).reduce((s, l) => s + l.chiffreAffaires, 0)
  );

  /** Courbes chiffre d'affaires, cout d'achat et marge sur un seul axe, zero compris. */
  readonly courbes = computed(() => {
    const serie = this.donnees()?.evolution ?? [];
    const valeurs = serie.flatMap((p) => [p.chiffreAffaires, p.cout, p.marge]);
    const haut = Math.max(...valeurs, 1);
    const bas = Math.min(...valeurs, 0);
    const largeurUtile = LARGEUR_COURBES - MARGE_GAUCHE - 16;
    const hauteurUtile = HAUTEUR_COURBES - 30;
    const x = (i: number) =>
      MARGE_GAUCHE + (serie.length < 2 ? largeurUtile / 2 : (i * largeurUtile) / (serie.length - 1));
    const y = (v: number) => 10 + ((haut - v) / (haut - bas)) * hauteurUtile;
    const trace = (cle: 'chiffreAffaires' | 'cout' | 'marge', couleur: string, libelle: string) => ({
      libelle,
      couleur,
      points: serie.map((p, i) => ({ x: x(i), y: y(p[cle]), valeur: p[cle], periode: p.libelle }))
    });
    // Au-dela d'une quinzaine de points, une etiquette sur deux ou trois suffit.
    const pas = Math.max(1, Math.ceil(serie.length / 12));
    return {
      series: [
        trace('chiffreAffaires', '#22a27a', "Chiffre d'affaires"),
        trace('cout', '#e8a33d', "Cout d'achat"),
        trace('marge', '#2f6fd6', 'Marge')
      ],
      graduations: [0, 1, 2, 3, 4].map((n) => {
        const valeur = haut - ((haut - bas) * n) / 4;
        return { valeur, y: y(valeur) };
      }),
      etiquettes: serie.map((p, i) => ({ libelle: p.libelle, x: x(i) })).filter((_, i) => i % pas === 0),
      zero: y(0)
    };
  });

  /** Barres empilees : le cout d'achat en socle sombre, la marge par-dessus. */
  readonly barresEmpilees = computed(() => {
    const serie = this.serieBarres();
    const maximum = Math.max(...serie.map((p) => p.chiffreAffaires), 1);
    return serie.map((p) => ({
      point: p,
      socle: (p.cout / maximum) * 100,
      marge: (Math.max(p.marge, 0) / maximum) * 100
    }));
  });

  readonly graduationsBarres = computed(() => {
    const maximum = Math.max(...this.serieBarres().map((p) => p.chiffreAffaires), 0);
    return [4, 3, 2, 1, 0].map((n) => (maximum * n) / 4);
  });

  /** Les barres restent lisibles : au plus les dix derniers points. */
  private readonly serieBarres = computed(() => (this.donnees()?.evolution ?? []).slice(-10));

  readonly maximumArticles = computed(() =>
    Math.max(...(this.donnees()?.meilleursArticles ?? []).map((a) => a.chiffreAffaires), 1)
  );

  /** Affluence : les heures d'ouverture seulement, de la premiere a la derniere vente. */
  readonly heures = computed(() => {
    const heures = this.donnees()?.parHeure ?? [];
    const actives = heures.filter((h) => h.tickets > 0).map((h) => h.heure);
    if (actives.length === 0) {
      return heures.filter((h) => h.heure >= 7 && h.heure <= 22);
    }
    const premiere = Math.max(0, Math.min(...actives) - 1);
    const derniere = Math.min(23, Math.max(...actives) + 1);
    return heures.filter((h) => h.heure >= premiere && h.heure <= derniere);
  });

  readonly maximumHeures = computed(() => Math.max(...this.heures().map((h) => h.chiffreAffaires), 1));

  readonly heurePointe = computed(() => {
    const h = [...(this.donnees()?.parHeure ?? [])].sort((a, b) => b.chiffreAffaires - a.chiffreAffaires)[0];
    return h && h.tickets > 0 ? h.heure : null;
  });

  ngOnInit(): void {
    this.charger();
  }

  changerPortee(portee: Periode): void {
    if (this.portee() === portee) {
      return;
    }
    if (portee === 'PERSONNALISEE') {
      // Les dates libres partent de la periode affichee jusque-la.
      const { du, au } = datesDePeriode(this.portee());
      this.dateDebut.set(du);
      this.dateFin.set(au);
    }
    this.portee.set(portee);
    this.charger();
  }

  /** Une date libre changee : le tableau suit des que l'intervalle est valide. */
  changerDate(borne: 'debut' | 'fin', valeur: string): void {
    (borne === 'debut' ? this.dateDebut : this.dateFin).set(valeur);
    if (this.datesValides()) {
      this.charger();
    }
  }

  charger(): void {
    if (this.portee() === 'PERSONNALISEE' && !this.datesValides()) {
      return;
    }
    this.chargement.set(!this.donnees());
    const { debut, fin } = bornesPeriode(this.portee(), this.dateDebut(), this.dateFin());
    this.service.tableauBord(debut.toISOString(), fin.toISOString()).subscribe({
      next: (d) => {
        this.donnees.set(d);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Impossible de charger les indicateurs du point de vente.');
      }
    });
  }

  grosMontant(valeur: number): string {
    return grosMontant(valeur, this.locale);
  }

  /** Part d'une valeur dans un maximum, en pourcentage ; une valeur faible garde un trait visible. */
  part(valeur: number, maximum: number): number {
    if (valeur <= 0 || maximum <= 0) {
      return 0;
    }
    return Math.max(1, Math.round((valeur / maximum) * 100));
  }

  abrege(valeur: number): string {
    const absolu = Math.abs(valeur);
    if (absolu >= 1_000_000) {
      return `${(valeur / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} M`;
    }
    if (absolu >= 1_000) {
      return `${Math.round(valeur / 1_000)} k`;
    }
    return `${Math.round(valeur)}`;
  }

  initiales(nom?: string): string {
    return (nom ?? '?')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((mot) => mot[0].toUpperCase())
      .join('');
  }

  points(serie: { points: { x: number; y: number }[] }): string {
    return serie.points.map((p) => `${p.x},${p.y}`).join(' ');
  }
}
