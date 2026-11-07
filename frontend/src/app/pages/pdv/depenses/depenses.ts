import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CategorieDepense,
  Depense,
  DepenseAPayer,
  DepenseRecurrente,
  DepenseRecurrenteRequete,
  MODES_SORTIE,
  ModeSortie,
  SyntheseDepenses
} from '../../../core/models/achats';
import { Page } from '../../../core/models/models';
import { AchatsService } from '../../../core/services/achats.service';
import { FormulaireDepensePdv } from '../../../shared/formulaire-depense-pdv';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';
import { PERIODES, Periode, bornesPeriode, dateIso, datesDePeriode } from '../../../shared/periodes';

type Onglet = 'depenses' | 'a-payer' | 'recurrentes' | 'categories';

/** Icones proposees pour les categories de depense. */
const ICONES_DEPENSE = ['👥', '🏠', '⚡', '💧', '📶', '⛽', '🚚', '🔧', '📦', '🏛', '📣', '🏦', '🛡', '🧹',
  '🍽', '🧾', '💊', '🎓', '🖨', '💻', '📱', '🔥', '🚗', '✈', '🎁', '⚖', '📄', '💼'];

/**
 * Depenses du point de vente : saisie et suivi par periode, mois a payer des
 * depenses recurrentes (loyer, salaires), modeles recurrents, et categories
 * (modifiables par l'administrateur seul).
 */
@Component({
  selector: 'app-depenses-pdv',
  imports: [FormsModule, DecimalPipe, DatePipe, SuiteListe, FormulaireDepensePdv, Modale],
  templateUrl: './depenses.html',
  styleUrl: './depenses.scss'
})
export class DepensesPdv implements OnInit {
  private readonly service = inject(AchatsService);
  readonly estAdmin = inject(AuthService).aRole('ADMIN');

  readonly modes = MODES_SORTIE;
  readonly onglet = signal<Onglet>('depenses');
  readonly onglets = computed(() => [
    { id: 'depenses' as Onglet, libelle: 'Depenses', icone: '💸' },
    { id: 'a-payer' as Onglet, libelle: 'A payer ce mois', icone: '📅' },
    { id: 'recurrentes' as Onglet, libelle: 'Depenses recurrentes', icone: '🔁' },
    { id: 'categories' as Onglet, libelle: 'Categories', icone: '🏷' }
  ]);

  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly chargement = signal(false);

  // ------------------------------------------------------------- depenses
  readonly page = signal<Page<Depense> | null>(null);
  readonly synthese = signal<SyntheseDepenses | null>(null);
  readonly categories = signal<CategorieDepense[]>([]);
  private readonly suite = new SuiteDePages();
  /** Periode observee : les memes raccourcis que les rapports et le tableau de bord. */
  readonly portees = PERIODES;
  readonly portee = signal<Periode>('CE_MOIS');
  readonly dateDebut = signal(datesDePeriode('CE_MOIS').du);
  readonly dateFin = signal(datesDePeriode('CE_MOIS').au);
  readonly datesValides = computed(() => this.portee() !== 'PERSONNALISEE' || this.dateDebut() <= this.dateFin());
  categorieId: number | null = null;
  mode: ModeSortie | '' = '';
  statut: 'VALIDE' | 'ANNULEE' | '' = '';
  recherche = '';

  /** Saisie ouverte (null : nouvelle, une depense : correction, undefined : fermee). */
  readonly formulaire = signal<Depense | null | undefined>(undefined);
  readonly moisAPayer = signal<DepenseAPayer | null>(null);
  readonly aAnnuler = signal<Depense | null>(null);
  /** Derniere depense enregistree : sa fiche est proposee a l'impression. */
  readonly derniere = signal<Depense | null>(null);
  motif = '';

  // --------------------------------------------------------- a payer
  readonly aPayer = signal<DepenseAPayer[]>([]);
  readonly totalAPayer = computed(() => this.aPayer().reduce((s, a) => s + a.montant, 0));

  // ------------------------------------------------------ recurrentes
  readonly recurrentes = signal<DepenseRecurrente[]>([]);
  /** Modele ouvert (null : nouveau, undefined : ferme). */
  readonly modele = signal<DepenseRecurrente | null | undefined>(undefined);
  r: DepenseRecurrenteRequete = this.modeleVide();

  // ------------------------------------------------------- categories
  readonly icones = ICONES_DEPENSE;
  /** Categorie ouverte (null : nouvelle, undefined : fenetre fermee). */
  readonly categorieOuverte = signal<CategorieDepense | null | undefined>(undefined);
  edition = { nom: '', icone: '', ordre: 0, actif: true };

  ngOnInit(): void {
    this.chargerCategories();
    this.chargerAPayer();
    this.charger();
  }

  changerOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.succes.set(null);
    this.erreur.set(null);
    if (o === 'recurrentes') {
      this.chargerRecurrentes();
    } else if (o === 'a-payer') {
      this.chargerAPayer();
    } else if (o === 'categories') {
      this.chargerCategories();
    }
  }

  compte(o: Onglet): number | null {
    return o === 'a-payer' && this.aPayer().length ? this.aPayer().length : null;
  }

  // ------------------------------------------------------------- depenses

  charger(): void {
    this.chargement.set(true);
    if (!this.datesValides()) {
      this.erreur.set('La date de debut doit preceder la date de fin.');
      return;
    }
    const { debut, fin } = this.bornes();
    this.service.syntheseDepenses(debut, fin).subscribe({ next: (s) => this.synthese.set(s) });
    this.service.depenses({
      debut, fin, categorieId: this.categorieId, mode: this.mode, statut: this.statut,
      q: this.recherche.trim() || undefined, page: this.suite.numero()
    }).subscribe({
      next: (p) => {
        this.page.set(cumulerPage(this.page(), p));
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Chargement des depenses impossible.');
      }
    });
  }

  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
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

  changerDate(borne: 'debut' | 'fin', valeur: string): void {
    (borne === 'debut' ? this.dateDebut : this.dateFin).set(valeur);
    this.charger();
  }

  libellePeriode(): string {
    if (this.portee() === 'PERSONNALISEE') {
      return `du ${this.dateDebut().split('-').reverse().join('/')} au ${this.dateFin().split('-').reverse().join('/')}`;
    }
    return this.portees.find((p) => p.valeur === this.portee())!.libelle.toLowerCase();
  }

  /** Dates envoyees a l'API : le premier et le dernier jour inclus. */
  private bornes(): { debut: string; fin: string } {
    const { debut, fin } = bornesPeriode(this.portee(), this.dateDebut(), this.dateFin());
    return { debut: dateIso(debut), fin: dateIso(new Date(fin.getTime() - 1)) };
  }

  enregistree(d: Depense): void {
    const correction = this.formulaire() !== null && this.formulaire() !== undefined;
    this.derniere.set(correction ? null : d);
    this.formulaire.set(undefined);
    this.moisAPayer.set(null);
    this.succes.set(correction ? `Depense ${d.numero} corrigee.`
      : `Depense ${d.numero} enregistree : ${Math.round(d.montant).toLocaleString('fr-FR')} F${d.caisseNom ? ', sortis de ' + d.caisseNom : ''}.`);
    this.charger();
    this.chargerAPayer();
  }

  payer(a: DepenseAPayer): void {
    this.moisAPayer.set(a);
  }

  confirmerAnnulation(): void {
    const d = this.aAnnuler();
    if (!d || !this.motif.trim()) {
      this.erreur.set('Indiquez le motif de l\'annulation.');
      return;
    }
    this.service.annulerDepense(d.id, this.motif.trim()).subscribe({
      next: () => {
        this.aAnnuler.set(null);
        this.succes.set(`Depense ${d.numero} annulee${d.caisseNom ? ' : la sortie de ' + d.caisseNom + ' est retiree' : ''}.`);
        this.charger();
        this.chargerAPayer();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  imprimerFiche(d: Depense): void {
    this.service.ficheDepense(d.id).subscribe({
      next: (pdf) => this.service.ouvrirPdf(pdf),
      error: () => this.erreur.set('Fiche de depense indisponible.')
    });
  }

  voirJustificatif(d: Depense): void {
    this.service.justificatif(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.erreur.set('Justificatif introuvable.')
    });
  }

  joindre(d: Depense, evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    const fichier = champ.files?.[0];
    champ.value = '';
    if (!fichier) {
      return;
    }
    if (fichier.size > 15 * 1024 * 1024) {
      this.erreur.set('Le justificatif depasse 15 Mo.');
      return;
    }
    this.service.joindreJustificatif(d.id, fichier).subscribe({
      next: () => {
        this.succes.set(`Justificatif joint a ${d.numero}.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Envoi du justificatif impossible.')
    });
  }

  // --------------------------------------------------------- a payer

  chargerAPayer(): void {
    this.service.aPayer().subscribe({ next: (a) => this.aPayer.set(a) });
  }

  // ------------------------------------------------------ recurrentes

  chargerRecurrentes(): void {
    this.service.recurrentes().subscribe({
      next: (r) => this.recurrentes.set(r),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement impossible.')
    });
  }

  ouvrirModele(m: DepenseRecurrente | null): void {
    this.r = m
      ? { libelle: m.libelle, categorieId: m.categorieId, beneficiaire: m.beneficiaire, montant: m.montant,
          jourEcheance: m.jourEcheance, mode: m.mode ?? null, debut: m.debut, fin: m.fin ?? null, actif: m.actif, note: m.note }
      : this.modeleVide();
    this.erreur.set(null);
    this.modele.set(m);
  }

  enregistrerModele(): void {
    if (!this.r.libelle?.trim() || !this.r.categorieId || !this.r.montant || !this.r.debut) {
      this.erreur.set('Libelle, categorie, montant et premier mois sont obligatoires.');
      return;
    }
    const m = this.modele();
    const requete: DepenseRecurrenteRequete = { ...this.r, fin: this.r.fin || null, mode: this.r.mode || null };
    const appel = m ? this.service.modifierRecurrente(m.id, requete) : this.service.creerRecurrente(requete);
    appel.subscribe({
      next: (enregistre) => {
        this.modele.set(undefined);
        this.succes.set(`${enregistre.libelle} enregistree : elle sera proposee chaque mois dans « A payer ».`);
        this.chargerRecurrentes();
        this.chargerAPayer();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  supprimerModele(m: DepenseRecurrente): void {
    this.service.supprimerRecurrente(m.id).subscribe({
      next: () => {
        this.succes.set(`${m.libelle} supprimee.`);
        this.chargerRecurrentes();
        this.chargerAPayer();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Suppression impossible.')
    });
  }

  // ------------------------------------------------------- categories

  chargerCategories(): void {
    this.service.categoriesDepense().subscribe({ next: (c) => this.categories.set(c) });
  }

  ouvrirCategorie(c: CategorieDepense | null): void {
    const prochainOrdre = Math.max(0, ...this.categories().map((x) => x.ordre)) + 10;
    this.edition = c
      ? { nom: c.nom, icone: c.icone ?? '', ordre: c.ordre, actif: c.actif }
      : { nom: '', icone: this.icones[0], ordre: prochainOrdre, actif: true };
    this.erreur.set(null);
    this.categorieOuverte.set(c);
  }

  enregistrerCategorie(): void {
    if (!this.edition.nom.trim()) {
      this.erreur.set('Indiquez le nom de la categorie.');
      return;
    }
    const c = this.categorieOuverte();
    const requete = { nom: this.edition.nom.trim(), icone: this.edition.icone || null, ordre: this.edition.ordre,
      actif: this.edition.actif };
    const appel = c ? this.service.modifierCategorieDepense(c.id, requete) : this.service.creerCategorieDepense(requete);
    appel.subscribe({
      next: (enregistree) => {
        this.categorieOuverte.set(undefined);
        this.succes.set(`Categorie ${enregistree.nom} ${c ? 'modifiee' : 'ajoutee'}.`);
        this.chargerCategories();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  basculerCategorie(c: CategorieDepense): void {
    this.service.modifierCategorieDepense(c.id, { nom: c.nom, icone: c.icone ?? null, ordre: c.ordre, actif: !c.actif })
      .subscribe({
        next: () => this.chargerCategories(),
        error: (e) => this.erreur.set(e?.error?.message ?? 'Modification impossible.')
      });
  }

  supprimerCategorie(c: CategorieDepense): void {
    this.service.supprimerCategorieDepense(c.id).subscribe({
      next: () => {
        this.succes.set(`Categorie ${c.nom} supprimee.`);
        this.chargerCategories();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Suppression impossible.')
    });
  }

  // ------------------------------------------------------------ outils

  private modeleVide(): DepenseRecurrenteRequete {
    return { libelle: '', categorieId: 0, beneficiaire: '', montant: 0, jourEcheance: 5, mode: null,
      debut: new Date().toISOString().slice(0, 7), fin: null, actif: true, note: '' };
  }

}
