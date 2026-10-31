import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ArticlePdv } from '../../../core/models/pdv';
import { BilanImport, FormatColonne, Rapport, RapportCatalogue } from '../../../core/models/rapport';
import { PdvService } from '../../../core/services/pdv.service';
import { RestaurationService } from '../../../core/services/restauration.service';
import { AuthService } from '../../../core/auth/auth.service';
import { EcranPreparation } from '../../../core/models/restauration';
import { OptionsRapport, RapportService } from '../../../core/services/rapport.service';
import { ExportPdv } from '../../../shared/export-pdv';
import { ImportPdv, messageImport } from '../../../shared/import-pdv';
import { PERIODES, Periode, bornesPeriode, datesDePeriode } from '../../../shared/periodes';

/**
 * Rapports du point de vente pour le gerant et l'administrateur : ventes,
 * produits, stock et fiche de stock, achats, caisses et commandes de table,
 * sur la periode choisie, avec export Excel, PDF ou CSV. L'import des
 * produits et du stock compte se fait depuis un fichier CSV ou Excel.
 * <p>
 * L'adresse peut porter le rapport et le produit (?type=FICHE_STOCK&article=12) :
 * la page Produits ouvre ainsi directement la fiche de stock d'un produit.
 */
/** Pictogramme d'une carte d'indicateur (dessins au trait, comme le tableau de bord). */
type IconeIndicateur =
  | 'stock' | 'entree' | 'sortie' | 'stock-fin' | 'valeur' | 'argent' | 'commande' | 'marge'
  | 'achat' | 'panier' | 'alerte' | 'seuil' | 'produit' | 'convives' | 'calendrier' | 'graphique';

/** Apparence d'une carte d'indicateur : couleur, pictogramme et ligne de detail. */
interface CarteIndicateur {
  libelle: string;
  valeur: number;
  format: FormatColonne;
  couleur: string;
  icone: IconeIndicateur;
  detail: string | null;
}

/**
 * Couleur et pictogramme selon le libelle de l'indicateur, calcule par le
 * serveur : le meme mot donne la meme carte d'un rapport a l'autre. Une alerte
 * a zero (aucune rupture, aucun ecart) reste grise.
 */
function apparence(libelle: string, valeur: number): { couleur: string; icone: IconeIndicateur } {
  const l = libelle.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  const alerte = (couleur: string) => (valeur === 0 ? 'calme' : couleur);
  if (l.includes('stock au debut')) return { couleur: 'bleu', icone: 'stock' };
  if (l.includes('preparation moyenne') || l.includes('plus long')) return { couleur: 'orange', icone: 'calendrier' };
  if (l.includes('prepare')) return { couleur: 'vert', icone: 'produit' };
  if (l === 'bons') return { couleur: 'bleu', icone: 'commande' };
  if (l.includes('stock a la fin')) return { couleur: 'violet', icone: 'stock-fin' };
  if (l.startsWith('entree') || l.includes('quantites entrees')) return { couleur: 'vert', icone: 'entree' };
  if (l.startsWith('sortie') || l.includes('quantites sorties')) return { couleur: 'orange', icone: 'sortie' };
  if (l.includes('valeur')) return { couleur: 'turquoise', icone: 'valeur' };
  if (l.includes('chiffre')) return { couleur: 'vert', icone: 'argent' };
  if (l.includes('annul') || l.includes('rupture')) return { couleur: alerte('rouge'), icone: 'alerte' };
  if (l.includes('ecart')) return { couleur: alerte('rouge'), icone: 'alerte' };
  if (l.includes('seuil')) return { couleur: alerte('orange'), icone: 'seuil' };
  if (l.includes('marge')) return { couleur: 'violet', icone: 'marge' };
  if (l.includes('cout') || l.includes('montant commande')) return { couleur: 'orange', icone: 'achat' };
  if (l.includes('panier') || l.includes('moyenne')) return { couleur: 'bleu', icone: 'panier' };
  if (l.includes('couvert')) return { couleur: 'violet', icone: 'convives' };
  if (l.includes('jour')) return { couleur: 'bleu', icone: 'calendrier' };
  if (l.includes('produit') || l.includes('actif') || l.includes('suivi')) return { couleur: 'bleu', icone: 'produit' };
  if (l.includes('commande') || l.includes('session') || l.includes('mouvement')) return { couleur: 'bleu', icone: 'commande' };
  return { couleur: 'bleu', icone: 'graphique' };
}

@Component({
  selector: 'app-rapports-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, ExportPdv, ImportPdv],
  templateUrl: './rapports.html',
  styleUrl: './rapports.scss'
})
export class RapportsPdv implements OnInit {
  private readonly service = inject(RapportService);
  private readonly pdv = inject(PdvService);
  private readonly restauration = inject(RestaurationService);
  private readonly estRestauration = inject(AuthService).estRestauration();
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly portees = PERIODES;

  readonly catalogue = signal<RapportCatalogue[]>([]);
  readonly choisi = signal<string>('VENTES');
  readonly portee = signal<Periode>('CE_MOIS');
  readonly dateDebut = signal(datesDePeriode('CE_MOIS').du);
  readonly dateFin = signal(datesDePeriode('CE_MOIS').au);
  /** Produits proposes pour la fiche de stock, et celui retenu. */
  readonly produits = signal<ArticlePdv[]>([]);
  readonly article = signal<number | null>(null);
  /** Ecrans de preparation pour les rapports de production, et celui retenu. */
  readonly ecrans = signal<EcranPreparation[]>([]);
  readonly ecran = signal<number | null>(null);

  readonly rapport = signal<Rapport | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly filtre = signal('');
  readonly importOuvert = signal(false);
  readonly succes = signal<string | null>(null);

  /** Rapports regroupes par famille, pour le panneau de gauche. */
  readonly familles = computed(() => {
    const groupes = new Map<string, RapportCatalogue[]>();
    for (const r of this.catalogue()) {
      groupes.set(r.famille, [...(groupes.get(r.famille) ?? []), r]);
    }
    return [...groupes.entries()].map(([famille, rapports]) => ({ famille, rapports }));
  });

  readonly courant = computed(() => this.catalogue().find((r) => r.type === this.choisi()) ?? null);
  readonly attendProduit = computed(() => !!this.courant()?.parArticle && !this.article());
  readonly attendEcran = computed(() => !!this.courant()?.parEcran && !this.ecran());

  /** Periode et produit du rapport affiche : servent au calcul comme aux exports. */
  readonly options = computed<OptionsRapport>(() => ({ ...this.bornes(), article: this.article(), ecran: this.ecran() }));

  /** Lignes filtrees par la recherche, sur toutes les colonnes. */
  readonly lignes = computed(() => {
    const r = this.rapport();
    const motif = this.filtre().trim().toLowerCase();
    if (!r || !motif) {
      return r?.lignes ?? [];
    }
    return r.lignes.filter((l) => Object.values(l).some((v) => v != null && String(v).toLowerCase().includes(motif)));
  });

  /** Dates libres valides : la premiere ne depasse pas la seconde. */
  readonly datesValides = computed(() => !!this.dateDebut() && !!this.dateFin() && this.dateDebut() <= this.dateFin());

  /** Cartes d'indicateurs du rapport affiche, avec leur couleur, leur pictogramme et leur detail. */
  readonly cartes = computed<CarteIndicateur[]>(() => {
    const r = this.rapport();
    if (!r) {
      return [];
    }
    return r.indicateurs.map((i) => {
      const valeur = Number(i.valeur) || 0;
      return { ...i, valeur, ...apparence(i.libelle, valeur), detail: this.detail(r, i.libelle) };
    });
  });

  readonly aDesTotaux = computed(() => Object.keys(this.rapport()?.totaux ?? {}).length > 0);

  ngOnInit(): void {
    const parametres = this.route.snapshot.queryParamMap;
    const article = Number(parametres.get('article'));
    if (article) {
      this.article.set(article);
    }
    const ecran = Number(parametres.get('ecran'));
    if (ecran) {
      this.ecran.set(ecran);
    }
    if (this.estRestauration) {
      this.restauration.ecrans().subscribe({ next: (l) => this.ecrans.set(l.ecrans) });
    }
    this.pdv.articles({ size: 2000 }).subscribe({
      next: (p) => this.produits.set(p.content.filter((a) => a.suiviStock))
    });
    this.service.catalogue().subscribe({
      next: (c) => {
        this.catalogue.set(c);
        const type = parametres.get('type');
        if (type && c.some((r) => r.type === type)) {
          this.choisi.set(type);
        }
        this.charger();
      },
      error: () => this.erreur.set('Chargement des rapports impossible.')
    });
  }

  choisir(type: string): void {
    this.choisi.set(type);
    this.filtre.set('');
    this.rapport.set(null);
    this.charger();
  }

  choisirEcran(id: number | null): void {
    this.ecran.set(id);
    void this.router.navigate([], { queryParams: { type: this.choisi(), ecran: id }, replaceUrl: true });
    this.charger();
  }

  choisirProduit(id: number | null): void {
    this.article.set(id);
    // L'adresse garde le produit : un rechargement ou un lien partage rouvre la meme fiche.
    void this.router.navigate([], { queryParams: { type: this.choisi(), article: id }, replaceUrl: true });
    this.charger();
  }

  changerPortee(portee: Periode): void {
    if (portee === 'PERSONNALISEE' && this.portee() !== 'PERSONNALISEE') {
      // Les dates libres partent de la periode affichee jusque-la.
      const { du, au } = datesDePeriode(this.portee());
      this.dateDebut.set(du);
      this.dateFin.set(au);
    }
    this.portee.set(portee);
    this.charger();
  }

  /** Une date libre changee : le rapport suit des que l'intervalle est valide. */
  changerDate(borne: 'debut' | 'fin', valeur: string): void {
    (borne === 'debut' ? this.dateDebut : this.dateFin).set(valeur);
    if (this.datesValides()) {
      this.charger();
    } else {
      this.erreur.set('La date de debut doit preceder la date de fin.');
    }
  }

  charger(): void {
    if (this.attendProduit() || this.attendEcran()) {
      this.rapport.set(null);
      return;
    }
    if (this.portee() === 'PERSONNALISEE' && !this.datesValides()) {
      this.erreur.set('La date de debut doit preceder la date de fin.');
      return;
    }
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.rapport(this.choisi(), this.options()).subscribe({
      next: (r) => {
        this.rapport.set(r);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Calcul du rapport impossible.'));
      }
    });
  }

  importTermine(bilan: BilanImport): void {
    this.importOuvert.set(false);
    this.succes.set(messageImport(bilan));
    this.charger();
  }

  valeur(ligne: Record<string, unknown>, cle: string): unknown {
    return ligne[cle];
  }

  estNombre(format: FormatColonne): boolean {
    return format === 'NOMBRE' || format === 'MONTANT' || format === 'POURCENT';
  }

  // ----------------------------------------------------------- interne

  /** Ligne de detail sous le chiffre ; pour la fiche de stock, ce qui l'explique. */
  private detail(r: Rapport, libelle: string): string | null {
    if (r.type !== 'FICHE_STOCK') {
      return null;
    }
    const mouvements = r.lignes.slice(1);
    const nombre = (cle: string) => mouvements.filter((l) => l[cle] != null).length;
    const jour = (valeur: unknown) => new Date(String(valeur)).toLocaleDateString('fr-FR');
    switch (libelle) {
      case 'Stock au debut':
        return `au ${jour(r.lignes[0]?.['date'])}`;
      case 'Entrees':
        return `${nombre('entree')} arrivage(s) en stock`;
      case 'Sorties':
        return `${nombre('sortie')} vente(s) ou perte(s)`;
      case 'Stock a la fin':
        return mouvements.length ? `apres ${mouvements.length} mouvement(s)` : 'aucun mouvement sur la periode';
      case 'Valeur au cout':
        return 'stock final au cout d\'achat';
      default:
        return null;
    }
  }

  private bornes(): { debut: string; fin: string } {
    const { debut, fin } = bornesPeriode(this.portee(), this.dateDebut(), this.dateFin());
    return { debut: debut.toISOString(), fin: fin.toISOString() };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
