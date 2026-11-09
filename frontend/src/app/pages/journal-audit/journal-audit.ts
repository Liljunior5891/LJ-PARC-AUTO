import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { Page } from '../../core/models/models';
import { JournalAuditService, LigneJournal, ReferentielsJournal } from '../../core/services/journal-audit.service';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';

/** Modules du journal et leur libelle. */
const MODULES: { valeur: string; libelle: string }[] = [
  { valeur: 'PARC', libelle: 'Parc' },
  { valeur: 'EXPLOITATION', libelle: 'Exploitation' },
  { valeur: 'FINANCES', libelle: 'Finances' },
  { valeur: 'MAINTENANCE', libelle: 'Maintenance' },
  { valeur: 'GPS', libelle: 'GPS' },
  { valeur: 'POINT_DE_VENTE', libelle: 'Point de vente' },
  { valeur: 'MARKETPLACE', libelle: 'Marketplace' },
  { valeur: 'ADMINISTRATION', libelle: 'Administration' },
  { valeur: 'CONNEXION', libelle: 'Connexions' }
];

const ACTIONS: { valeur: string; libelle: string; classe: string }[] = [
  { valeur: 'CREATION', libelle: 'Creation', classe: 'creation' },
  { valeur: 'MODIFICATION', libelle: 'Modification', classe: 'modification' },
  { valeur: 'SUPPRESSION', libelle: 'Suppression', classe: 'suppression' },
  { valeur: 'CONNEXION', libelle: 'Connexion', classe: 'connexion' },
  { valeur: 'ECHEC_CONNEXION', libelle: 'Connexion refusee', classe: 'suppression' }
];

/**
 * Journal d'audit des modules (gestion de parc, point de vente, marketplace,
 * administration) : qui a cree, modifie ou supprime quoi, quand, depuis quelle
 * adresse, avec la valeur de chaque champ avant et apres. Non modifiable.
 * <p>
 * Ouvert depuis un module, il est d'emblee limite a ses donnees (donnee de route
 * « modules ») ; depuis l'administration, il couvre toute l'entreprise.
 */
@Component({
  selector: 'app-journal-audit',
  imports: [DatePipe, FormsModule, RouterLink, SuiteListe],
  templateUrl: './journal-audit.html',
  styleUrl: './journal-audit.scss'
})
export class JournalAudit implements OnInit {
  private readonly service = inject(JournalAuditService);
  private readonly route = inject(ActivatedRoute);
  /** Les agences tiennent leur propre journal : un lien y mene depuis le journal general. */
  readonly agences = inject(AuthService).peutUtiliser('AGENCES');

  readonly actions = ACTIONS;
  readonly page = signal<Page<LigneJournal> | null>(null);
  readonly referentiels = signal<ReferentielsJournal>({ objets: [], auteurs: [] });
  readonly erreur = signal<string | null>(null);
  readonly ouverte = signal<number | null>(null);
  private readonly suite = new SuiteDePages();

  /** Modules imposes par la page (ex. POINT_DE_VENTE) ; vide : tous. */
  modulesPage = '';
  titre = 'Journal d\'audit';
  modulesProposes = MODULES;

  f = { module: '', action: '', objet: '', auteurId: null as number | null, du: '', au: '', recherche: '' };

  ngOnInit(): void {
    const donnees = this.route.snapshot.data as { modules?: string; titre?: string };
    this.modulesPage = donnees.modules ?? '';
    if (donnees.titre) {
      this.titre = donnees.titre;
    }
    if (this.modulesPage) {
      const autorises = this.modulesPage.split(',');
      this.modulesProposes = MODULES.filter((m) => autorises.includes(m.valeur));
    }
    this.service.referentiels().subscribe({ next: (r) => this.referentiels.set(r) });
    this.charger();
  }

  charger(): void {
    const module = this.f.module || this.modulesPage;
    this.service.lister({ ...this.f, module }, this.suite.numero()).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Lecture du journal impossible.')
    });
  }

  filtrer(): void {
    this.page.set(null);
    this.ouverte.set(null);
    this.charger();
  }

  suiteListe(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  basculer(l: LigneJournal): void {
    if (l.changements.length) {
      this.ouverte.set(this.ouverte() === l.id ? null : l.id);
    }
  }

  action(code: string): { libelle: string; classe: string } {
    return ACTIONS.find((a) => a.valeur === code) ?? { libelle: code, classe: '' };
  }

  module(code: string): string {
    return MODULES.find((m) => m.valeur === code)?.libelle ?? code;
  }
}
