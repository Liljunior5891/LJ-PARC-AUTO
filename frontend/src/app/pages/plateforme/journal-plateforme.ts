import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { PointSerie } from '../../core/models/abonnement';
import { Page } from '../../core/models/models';
import {
  CompteJournal,
  FiltresJournalGeneral,
  JOURNAL_PLATEFORME,
  JournalPlateformeService,
  LigneJournalGeneral,
  SyntheseJournal
} from '../../core/services/journal-plateforme.service';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';
import { GrapheBarres } from './graphe-barres';

/** Modules du journal general et leur libelle. */
const MODULES: { valeur: string; libelle: string }[] = [
  { valeur: 'PLATEFORME', libelle: 'Plateforme' },
  { valeur: 'CONNEXION', libelle: 'Connexions' },
  { valeur: 'ADMINISTRATION', libelle: 'Administration' },
  { valeur: 'ABONNEMENT', libelle: 'Abonnement' },
  { valeur: 'PARC', libelle: 'Parc' },
  { valeur: 'EXPLOITATION', libelle: 'Exploitation' },
  { valeur: 'FINANCES', libelle: 'Finances du parc' },
  { valeur: 'MAINTENANCE', libelle: 'Maintenance' },
  { valeur: 'GPS', libelle: 'GPS' },
  { valeur: 'POINT_DE_VENTE', libelle: 'Point de vente' },
  { valeur: 'MARKETPLACE', libelle: 'Marketplace' },
  { valeur: 'AGENCES', libelle: 'Agences' },
  { valeur: 'PRESENCE', libelle: 'Presence' }
];

const ACTIONS: { valeur: string; libelle: string; classe: string }[] = [
  { valeur: 'CREATION', libelle: 'Creation', classe: 'creation' },
  { valeur: 'MODIFICATION', libelle: 'Modification', classe: 'modification' },
  { valeur: 'SUPPRESSION', libelle: 'Suppression', classe: 'suppression' },
  { valeur: 'CONNEXION', libelle: 'Connexion', classe: 'connexion' },
  { valeur: 'ECHEC_CONNEXION', libelle: 'Connexion refusee', classe: 'suppression' }
];

function jour(decalage = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + decalage);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * Journal d'audit general du super-admin : un rapport de la periode (volumes,
 * modules, entreprises et auteurs les plus actifs, activite par jour), puis la
 * liste de toutes les actions du systeme, avec les valeurs avant et apres.
 */
@Component({
  selector: 'app-journal-plateforme',
  imports: [DatePipe, DecimalPipe, FormsModule, SuiteListe, GrapheBarres],
  templateUrl: './journal-plateforme.html',
  styleUrls: ['../journal-audit/journal-audit.scss', './tableau-bord-plateforme.scss', './journal-plateforme.scss']
})
export class JournalPlateforme implements OnInit, OnDestroy {
  private readonly service = inject(JournalPlateformeService);

  readonly modules = MODULES;
  readonly actions = ACTIONS;
  readonly plateforme = JOURNAL_PLATEFORME;

  readonly page = signal<Page<LigneJournalGeneral> | null>(null);
  readonly synthese = signal<SyntheseJournal | null>(null);
  readonly entreprises = signal<{ valeur: string; libelle: string }[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly ouverte = signal<string | null>(null);
  private readonly suite = new SuiteDePages();
  /** Requetes en cours : un nouveau filtre les annule, une reponse en retard ne melange pas les listes. */
  private requeteListe?: Subscription;
  private requeteRapport?: Subscription;

  /** Trente derniers jours par defaut. */
  f: FiltresJournalGeneral = { entreprise: '', module: '', action: '', du: jour(-29), au: jour(), recherche: '' };

  /** Activite par jour, pour l'histogramme. */
  readonly parJour = computed<PointSerie[]>(() => (this.synthese()?.parJour ?? []).map((j) => ({
    cle: j.jour,
    libelle: j.jour.slice(8, 10) + '/' + j.jour.slice(5, 7),
    valeur: j.nombre
  })));

  ngOnInit(): void {
    this.service.entreprises().subscribe({ next: (e) => this.entreprises.set(e) });
    this.filtrer();
  }

  filtrer(): void {
    if (this.f.du && this.f.au && this.f.du > this.f.au) {
      return;
    }
    this.erreur.set(null);
    this.page.set(null);
    this.ouverte.set(null);
    this.requeteListe?.unsubscribe();
    this.requeteRapport?.unsubscribe();
    this.chargerListe();
    this.requeteRapport = this.service.synthese({ ...this.f }).subscribe({
      next: (s) => this.synthese.set(s),
      error: (e) => this.erreur.set(this.message(e))
    });
  }

  private chargerListe(): void {
    const numero = this.suite.numero();
    this.requeteListe = this.service.lister({ ...this.f }, numero, this.page()?.totalElements).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set(this.message(e))
    });
  }

  ngOnDestroy(): void {
    this.requeteListe?.unsubscribe();
    this.requeteRapport?.unsubscribe();
  }

  suiteListe(): void {
    if (this.suite.demander(this.page())) {
      this.chargerListe();
    }
  }

  /** Clic sur une entreprise ou un module du rapport : la liste s'y limite. */
  choisirEntreprise(c: CompteJournal): void {
    this.f.entreprise = this.f.entreprise === c.valeur ? '' : c.valeur;
    this.filtrer();
  }

  choisirModule(c: CompteJournal): void {
    this.f.module = this.f.module === c.valeur ? '' : c.valeur;
    this.filtrer();
  }

  periode(jours: number): void {
    this.f.du = jour(-(jours - 1));
    this.f.au = jour();
    this.filtrer();
  }

  basculer(l: LigneJournalGeneral): void {
    if (l.changements.length) {
      this.ouverte.set(this.ouverte() === l.id ? null : l.id);
    }
  }

  part(valeur: number, liste: CompteJournal[]): number {
    const max = Math.max(...liste.map((c) => c.nombre), 1);
    return Math.round((valeur / max) * 100);
  }

  action(code: string): { libelle: string; classe: string } {
    const connue = ACTIONS.find((a) => a.valeur === code);
    if (connue) {
      return connue;
    }
    // Actions propres aux agences (OUVERTURE_CAISSE...) : mises en forme lisible.
    const texte = code.toLowerCase().replace(/_/g, ' ');
    return { libelle: texte.charAt(0).toUpperCase() + texte.slice(1), classe: 'modification' };
  }

  module(code: string): string {
    return MODULES.find((m) => m.valeur === code)?.libelle ?? code;
  }

  private message(e: unknown): string {
    return (e as { error?: { message?: string } })?.error?.message ?? 'Lecture du journal impossible.';
  }
}
