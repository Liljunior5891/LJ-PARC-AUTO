import { DatePipe, DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, LOCALE_ID, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AlerteAgence, ServiceFinancier, TableauBordAgences } from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';
import { MaCaisseAgence } from '../caisses/ma-caisse-agence';
import { LogoService, attributsOption } from '../../../shared/logo-service';
import { grosMontant } from '../../../shared/montant';
import { Periode, dateIso, datesDePeriode } from '../../../shared/periodes';

/** Periodes du cahier des charges : aujourd'hui, hier, semaine, mois, mois precedent, annee, personnalisee. */
type PeriodeAgence = Periode | 'MOIS_PRECEDENT';

const PERIODES_AGENCE: { valeur: PeriodeAgence; libelle: string }[] = [
  { valeur: 'AUJOURDHUI', libelle: 'Aujourd\'hui' },
  { valeur: 'HIER', libelle: 'Hier' },
  { valeur: 'CETTE_SEMAINE', libelle: 'Cette semaine' },
  { valeur: 'CE_MOIS', libelle: 'Ce mois' },
  { valeur: 'MOIS_PRECEDENT', libelle: 'Mois precedent' },
  { valeur: 'CETTE_ANNEE', libelle: 'Cette annee' },
  { valeur: 'PERSONNALISEE', libelle: 'Entre deux dates...' }
];

/** Geometrie commune des graphiques (unites du viewBox SVG). */
const LARGEUR = 640;
const HAUTEUR = 220;
const MARGE = { haut: 12, droite: 12, bas: 26, gauche: 64 };

/** Anneau des commissions par service (meme geometrie que le PDV). */
const RAYON_DONUT = 60;
const CIRCONFERENCE = 2 * Math.PI * RAYON_DONUT;
const ECART_ARC = 3;
const PALETTE = ['#ff8c1a', '#2f6fd6', '#7c9a3c', '#1f6b52', '#22c3a6', '#a855f7', '#d9b24a'];

interface Barre { x: number; y: number; largeur: number; hauteur: number; chemin: string }

/**
 * Tableau de bord des agences : tresorerie du moment, activite et revenus de
 * la periode, commissions par jour, evolution de la tresorerie, rentabilite
 * par service, activite par reseau et par caissier. Les filtres mettent a jour
 * indicateurs et graphiques.
 */
@Component({
  selector: 'app-tableau-bord-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, LogoService, MaCaisseAgence, NgTemplateOutlet, RouterLink],
  templateUrl: './tableau-bord-agences.html',
  styleUrls: ['../../tableau-bord/tableau-bord.scss', './tableau-bord-agences.scss']
})
export class TableauBordAgencesPage implements OnInit {
  readonly service = inject(AgencesService);
  private readonly locale = inject(LOCALE_ID);
  readonly periodes = PERIODES_AGENCE;
  readonly circonference = CIRCONFERENCE;
  readonly opt = attributsOption;
  /** Caissier sans le droit de voir toutes les operations : le tableau de bord ne montre que les siennes. */
  readonly caissier = computed(() => !!this.service.moi() && !this.service.peut('HISTORIQUE_COMPLET'));
  readonly couleurCommissions = '#22a27a';
  readonly couleurVolume = '#2f6fd6';
  readonly couleurTresorerie = '#1f7a5c';
  readonly L = LARGEUR;
  readonly H = HAUTEUR;
  readonly M = MARGE;

  readonly donnees = signal<TableauBordAgences | null>(null);
  readonly catalogue = signal<ServiceFinancier[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly caissiers = signal<{ id: number; nom: string }[]>([]);
  readonly alertes = signal<AlerteAgence[]>([]);

  periode: PeriodeAgence = 'AUJOURDHUI';
  du = dateIso(new Date());
  au = dateIso(new Date());
  agenceId: number | null = null;
  serviceId: number | null = null;
  reseauId: number | null = null;
  caissierId: number | null = null;
  readonly serviceChoisi = computed(() => this.catalogue().find((s) => s.id === this.serviceFiltre()));
  private readonly serviceFiltre = signal<number | null>(null);

  readonly survolJour = signal<number | null>(null);
  readonly survolTresorerie = signal<number | null>(null);

  private readonly route = inject(ActivatedRoute);

  /** Nom de l'agence affichee : celle choisie, ou la seule de l'utilisateur (superviseur, caissier). */
  readonly agenceAffichee = computed(() => {
    const agences = this.service.moi()?.agences ?? [];
    const id = this.agenceFiltre();
    return id != null ? agences.find((a) => a.id === id)?.nom : agences.length === 1 ? agences[0].nom : undefined;
  });
  private readonly agenceFiltre = signal<number | null>(null);

  /** Sous le titre : l'activite d'une agence, ou la vue de toutes les agences. */
  readonly portee = computed(() => (this.caissier() ? 'Vos operations, votre caisse et vos depenses'
    : this.agenceAffichee() ? 'Activite de l\'agence' : 'Vision globale des agences'));

  /** Lien d'une carte, seulement si le profil a acces a l'ecran vise. */
  lien(permission: string, route: string): string | null {
    return this.service.peut(permission) ? route : null;
  }

  ngOnInit(): void {
    // Tableau de bord d'une agence precise, ouvert depuis sa carte (?agence=ID).
    const demandee = Number(this.route.snapshot.queryParamMap.get('agence'));
    if (demandee) {
      this.agenceId = demandee;
      this.agenceFiltre.set(demandee);
    }
    this.service.chargerMoi().subscribe();
    this.service.catalogue().subscribe({ next: (c) => this.catalogue.set(c) });
    this.charger();
  }

  changerPeriode(): void {
    if (this.periode === 'MOIS_PRECEDENT') {
      const d = new Date();
      this.du = dateIso(new Date(d.getFullYear(), d.getMonth() - 1, 1));
      this.au = dateIso(new Date(d.getFullYear(), d.getMonth(), 0));
    } else if (this.periode !== 'PERSONNALISEE') {
      const p = datesDePeriode(this.periode);
      this.du = p.du;
      this.au = p.au;
    }
    this.charger();
  }

  changerService(): void {
    this.reseauId = null;
    this.serviceFiltre.set(this.serviceId);
    this.charger();
  }

  charger(): void {
    this.agenceFiltre.set(this.agenceId);
    this.service.alertes(this.agenceId).subscribe({ next: (a) => this.alertes.set(a) });
    this.service.tableauBord({ agenceId: this.agenceId, du: this.du, au: this.au, serviceId: this.serviceId,
      reseauId: this.reseauId, caissierId: this.caissierId }).subscribe({
      next: (d) => {
        this.donnees.set(d);
        this.erreur.set(null);
        // La liste des caissiers suit ceux qui ont travaille, sans perdre celui choisi.
        const connus = new Map(this.caissiers().map((c) => [c.id, c.nom]));
        d.parCaissier.forEach((c) => connus.set(c.id, c.nom));
        this.caissiers.set([...connus.entries()].map(([id, nom]) => ({ id, nom })).sort((a, b) => a.nom.localeCompare(b.nom)));
      },
      error: (e) => this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Chargement du tableau de bord impossible.')
    });
  }

  // ---------------------------------------------------- commissions par jour
  readonly maxCommission = computed(() => {
    const d = this.donnees();
    return graduer(Math.max(1, ...(d?.parJour.map((p) => p.commissions) ?? [0])));
  });

  readonly barresJour = computed<Barre[]>(() => {
    const d = this.donnees();
    if (!d) {
      return [];
    }
    const n = d.parJour.length;
    const zone = LARGEUR - MARGE.gauche - MARGE.droite;
    const pas = zone / Math.max(1, n);
    const largeur = Math.max(2, Math.min(28, pas - 2));
    const max = this.maxCommission();
    const base = HAUTEUR - MARGE.bas;
    return d.parJour.map((p, i) => {
      const h = p.commissions <= 0 ? 0 : Math.max(2, (p.commissions / max) * (base - MARGE.haut));
      const x = MARGE.gauche + i * pas + (pas - largeur) / 2;
      return { x, y: base - h, largeur, hauteur: h, chemin: barreArrondie(x, base, largeur, h) };
    });
  });

  // ------------------------------------------------------- tresorerie
  readonly echelleTresorerie = computed(() => {
    const serie = this.donnees()?.tresorerieParJour ?? [];
    const valeurs = serie.map((p) => p.solde);
    const min = Math.min(0, ...valeurs);
    const max = graduer(Math.max(1, ...valeurs));
    return { min, max };
  });

  readonly pointsTresorerie = computed(() => {
    const serie = this.donnees()?.tresorerieParJour ?? [];
    const { min, max } = this.echelleTresorerie();
    const zone = LARGEUR - MARGE.gauche - MARGE.droite;
    const base = HAUTEUR - MARGE.bas;
    return serie.map((p, i) => ({
      x: MARGE.gauche + (serie.length <= 1 ? zone / 2 : (i / (serie.length - 1)) * zone),
      y: base - ((p.solde - min) / (max - min || 1)) * (base - MARGE.haut)
    }));
  });

  readonly cheminTresorerie = computed(() =>
    this.pointsTresorerie().map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '));

  /** Graduations horizontales : 0, la moitie et le haut de l'axe. */
  graduations(max: number, min = 0): { y: number; valeur: number }[] {
    const base = HAUTEUR - MARGE.bas;
    return [0, 0.5, 1].map((t) => ({ y: base - t * (base - MARGE.haut), valeur: min + t * (max - min) }));
  }

  /** Quelques dates sous l'axe, sans chevauchement. */
  etiquettesJours(): { x: number; texte: string }[] {
    const d = this.donnees();
    if (!d?.parJour.length) {
      return [];
    }
    const n = d.parJour.length;
    const zone = LARGEUR - MARGE.gauche - MARGE.droite;
    const pas = zone / n;
    const tous = Math.max(1, Math.ceil(n / 7));
    return d.parJour
      .map((p, i) => ({ i, x: MARGE.gauche + i * pas + pas / 2, texte: p.jour.slice(8, 10) + '/' + p.jour.slice(5, 7) }))
      .filter((e) => e.i % tous === 0 || e.i === n - 1);
  }

  /** Index du jour sous le pointeur, a partir de sa position horizontale dans le graphique. */
  survoler(evenement: MouseEvent, cible: 'jour' | 'tresorerie'): void {
    const svg = evenement.currentTarget as SVGElement;
    const rect = svg.getBoundingClientRect();
    const x = ((evenement.clientX - rect.left) / rect.width) * LARGEUR;
    const n = cible === 'jour' ? (this.donnees()?.parJour.length ?? 0) : (this.donnees()?.tresorerieParJour.length ?? 0);
    if (!n) {
      return;
    }
    const zone = LARGEUR - MARGE.gauche - MARGE.droite;
    const relatif = Math.min(Math.max(x - MARGE.gauche, 0), zone - 0.01);
    const index = cible === 'jour' ? Math.floor((relatif / zone) * n)
      : (n <= 1 ? 0 : Math.round((relatif / zone) * (n - 1)));
    (cible === 'jour' ? this.survolJour : this.survolTresorerie).set(Math.min(n - 1, Math.max(0, index)));
  }

  /** Position du survol en pourcentage de la largeur, pour placer l'infobulle HTML. */
  pourcent(x: number): number {
    return (x / LARGEUR) * 100;
  }

  // ------------------------------------------------- repartition par service
  readonly maxService = computed(() => Math.max(1, ...(this.donnees()?.parService.map((s) => s.commissions) ?? [0])));

  largeurService(commissions: number): number {
    return (commissions / this.maxService()) * 100;
  }

  /** Libelle de la periode affichee, sous le titre et dans les graphiques. */
  libellePeriode(): string {
    const p = this.periodes.find((x) => x.valeur === this.periode);
    if (this.periode !== 'PERSONNALISEE' && p) {
      return p.libelle.toLowerCase();
    }
    const f = (iso: string) => iso.slice(8, 10) + '/' + iso.slice(5, 7) + '/' + iso.slice(0, 4);
    return this.du === this.au ? 'le ' + f(this.du) : 'du ' + f(this.du) + ' au ' + f(this.au);
  }

  /** Cash, virtuel et banque : la barre de tresorerie et sa legende. */
  readonly comptesTresorerie = computed(() => {
    const t = this.donnees()?.tresorerie;
    return [
      { libelle: 'Cash', valeur: t?.cash ?? 0, couleur: '#22a27a' },
      { libelle: 'Virtuel', valeur: t?.virtuel ?? 0, couleur: '#2f6fd6' },
      { libelle: 'Banque', valeur: t?.banque ?? 0, couleur: '#7a4fd1' }
    ];
  });

  /** Commissions par service en anneau, total au centre. */
  readonly arcsDonut = computed(() => {
    const lignes = (this.donnees()?.parService ?? []).filter((l) => l.commissions > 0);
    const total = lignes.reduce((s, l) => s + l.commissions, 0);
    let cumul = 0;
    return lignes.map((ligne, i) => {
      const part = total === 0 ? 0 : ligne.commissions / total;
      const arc = {
        id: ligne.id,
        libelle: ligne.nom,
        total: ligne.commissions,
        pourcent: Math.round(part * 100),
        couleur: this.couleurArc(i),
        longueur: Math.max(part * CIRCONFERENCE - ECART_ARC, 0),
        decalage: -cumul * CIRCONFERENCE
      };
      cumul += part;
      return arc;
    });
  });

  logoService(id: number): string | null | undefined {
    return this.catalogue().find((s) => s.id === id)?.logo;
  }

  logoReseau(id: number): string | null | undefined {
    for (const s of this.catalogue()) {
      const r = s.reseaux.find((x) => x.id === id);
      if (r) {
        return r.logo;
      }
    }
    return undefined;
  }

  couleurArc(i: number): string {
    return PALETTE[i % PALETTE.length];
  }

  readonly maximumReseaux = computed(() => Math.max(0, ...(this.donnees()?.parReseau.map((r) => r.volume) ?? [0])));
  readonly maximumCommissionsReseaux = computed(() =>
    Math.max(0, ...(this.donnees()?.parReseau.map((r) => r.commissions) ?? [0])));

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

  initiales(nom?: string): string {
    return (nom ?? '?').split(/\s+/).filter(Boolean).slice(0, 2).map((mot) => mot[0].toUpperCase()).join('');
  }

  abrege(valeur: number): string {
    const a = Math.abs(valeur);
    if (a >= 1_000_000) {
      return (valeur / 1_000_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' M';
    }
    if (a >= 1_000) {
      return (valeur / 1_000).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' k';
    }
    return valeur.toLocaleString('fr-FR');
  }
}

/** Haut d'axe arrondi a une valeur ronde (1, 2, 2,5 ou 5 x 10^n). */
function graduer(max: number): number {
  const puissance = Math.pow(10, Math.floor(Math.log10(max)));
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (m * puissance >= max) {
      return m * puissance;
    }
  }
  return 10 * puissance;
}

/** Barre posee sur l'axe, coins hauts arrondis de 4 unites. */
function barreArrondie(x: number, base: number, largeur: number, hauteur: number): string {
  if (hauteur <= 0) {
    return '';
  }
  const r = Math.min(4, largeur / 2, hauteur);
  const haut = base - hauteur;
  return `M${x},${base} V${haut + r} Q${x},${haut} ${x + r},${haut} H${x + largeur - r} Q${x + largeur},${haut} ${x + largeur},${haut + r} V${base} Z`;
}
