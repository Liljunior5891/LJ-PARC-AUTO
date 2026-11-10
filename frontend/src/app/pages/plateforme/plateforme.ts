import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AbonnementVue,
  EtatAbonnement,
  Formule,
  LIBELLES_RESSOURCES,
  MOYENS_PAIEMENT,
  MoyenPaiementAbonnement,
  PaiementAbonnement,
  ParametresAbonnement,
  Periodicite,
  TEINTES_ETATS
} from '../../core/models/abonnement';
import { CodeModule } from '../../core/models/models';
import {
  EntrepriseResume,
  ICONES_VARIANTES,
  ModuleCatalogue,
  PRESENTATION_MODULES,
  StatistiquesPlateforme
} from '../../core/models/plateforme';
import { AbonnementService } from '../../core/services/abonnement.service';
import { PlateformeService } from '../../core/services/plateforme.service';
import { environment } from '../../../environments/environment';
import { imprimerFactureAbonnement } from '../../shared/facture-abonnement';
import { Modale } from '../../shared/modale';
import { ChoixFormule } from './choix-formule';

/** Saisie de la creation d'une entreprise par l'exploitant. */
interface NouvelleEntreprise {
  nomEntreprise: string;
  pays: string;
  telephone: string;
  nom: string;
  prenom: string;
  email: string;
  motDePasse: string;
  joursEssai: number | null;
}

function message(reponse: unknown, defaut: string): string {
  return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
}

/** Teinte des cartes d'apercu par module. */
const COULEURS_MODULES: Record<CodeModule, string> = {
  GESTION_PARC: 'bleu',
  POINT_DE_VENTE: 'orange',
  MARKETPLACE: 'violet',
  AGENCES: 'vert',
  PRESENCE: 'rouge'
};

const LIBELLES_MODULES: Record<CodeModule, string> = {
  GESTION_PARC: 'Gestion de parc',
  POINT_DE_VENTE: 'Point de vente',
  MARKETPLACE: 'Marketplace',
  AGENCES: 'Agences multiservices',
  PRESENCE: 'Gestion de presence'
};

/**
 * Console de l'exploitant : entreprises abonnees, modules souscrits et
 * suspension. Elle ne donne jamais acces aux donnees metier des entreprises.
 */
@Component({
  selector: 'app-plateforme',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, ChoixFormule],
  templateUrl: './plateforme.html',
  styleUrl: './plateforme.scss'
})
export class Plateforme implements OnInit {
  private readonly service = inject(PlateformeService);
  private readonly abonnementService = inject(AbonnementService);

  readonly teintes = TEINTES_ETATS;
  readonly moyens = MOYENS_PAIEMENT;
  readonly libellesRessources = LIBELLES_RESSOURCES;
  readonly iconesVariantes = ICONES_VARIANTES;

  /** Abonnement de chaque entreprise, par identifiant. */
  readonly abonnements = signal<Map<number, AbonnementVue>>(new Map());
  readonly etatAbo = signal<EtatAbonnement | ''>('');

  // -------------------------------------------------- creation d'entreprise
  readonly creation = signal(false);
  readonly catalogue = signal<ModuleCatalogue[]>([]);
  readonly modulesChoisis = signal<CodeModule[]>([]);
  readonly variantes = signal<Partial<Record<CodeModule, string>>>({});
  readonly erreurCreation = signal<string | null>(null);
  saisie: NouvelleEntreprise = this.saisieVide();

  // ------------------------------------------------ fiche d'abonnement
  readonly fiche = signal<EntrepriseResume | null>(null);
  readonly ficheAbonnement = signal<AbonnementVue | null>(null);
  readonly fichePaiements = signal<PaiementAbonnement[]>([]);
  readonly erreurFiche = signal<string | null>(null);
  readonly modeFiche = signal<'apercu' | 'paiement'>('apercu');
  joursProlongation = 15;

  readonly formules = signal<Formule[]>([]);
  readonly parametres = signal<ParametresAbonnement | null>(null);
  readonly formuleId = signal<number | null>(null);
  readonly periodicite = signal<Periodicite>('MENSUEL');
  readonly modulesPaiement = signal<CodeModule[]>([]);
  moyen: MoyenPaiementAbonnement = 'MOBILE_MONEY';
  reference = '';
  note = '';
  montantLibre: number | null = null;

  readonly presentation = PRESENTATION_MODULES;
  readonly libellesModules = LIBELLES_MODULES;
  readonly codesModules = Object.keys(LIBELLES_MODULES) as CodeModule[];
  readonly couleurs = COULEURS_MODULES;

  readonly statistiques = signal<StatistiquesPlateforme | null>(null);
  readonly entreprises = signal<EntrepriseResume[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly enCours = signal<number | null>(null);
  readonly succes = signal<string | null>(null);
  /** Entreprise dont la suspension attend confirmation. */
  readonly aSuspendre = signal<EntrepriseResume | null>(null);

  readonly filtre = signal('');
  readonly statut = signal<'' | 'ACTIVE' | 'SUSPENDUE'>('');
  readonly module = signal<CodeModule | ''>('');
  readonly tri = signal<'recentes' | 'nom' | 'comptes'>('recentes');

  readonly entreprisesAffichees = computed(() => {
    const motif = this.filtre().trim().toLowerCase();
    const statut = this.statut();
    const module = this.module();
    const etat = this.etatAbo();
    const abos = this.abonnements();
    const liste = this.entreprises().filter((e) =>
      (!statut || e.statut === statut)
      && (!module || e.modules.includes(module))
      && (!etat || abos.get(e.id)?.etat === etat)
      && (!motif || [e.nom, e.email, e.pays, e.telephone].some((v) => v?.toLowerCase().includes(motif))));
    const tri = this.tri();
    return [...liste].sort((a, b) =>
      tri === 'nom' ? a.nom.localeCompare(b.nom)
        : tri === 'comptes' ? b.nombreUtilisateurs - a.nombreUtilisateurs
          : (b.creeLe ?? '').localeCompare(a.creeLe ?? ''));
  });

  readonly inscritesCeMois = computed(() => {
    const mois = new Date().toISOString().slice(0, 7);
    return this.entreprises().filter((e) => e.creeLe?.startsWith(mois)).length;
  });

  readonly moyenneComptes = computed(() => {
    const s = this.statistiques();
    return s && s.entreprises ? Math.round((s.utilisateurs / s.entreprises) * 10) / 10 : 0;
  });

  /** Part des entreprises, en pourcentage entier. */
  part(n: number): number {
    const total = this.statistiques()?.entreprises ?? 0;
    return total ? Math.round((n * 100) / total) : 0;
  }

  /** Entreprises abonnees a un module (0 si le serveur ne le compte pas encore). */
  abonnes(code: CodeModule): number {
    return (this.statistiques()?.activationsParModule as Partial<Record<CodeModule, number>> | undefined)?.[code] ?? 0;
  }

  initiales(nom: string): string {
    return nom.split(/\s+/).filter(Boolean).slice(0, 2).map((m) => m[0]).join('').toUpperCase() || '?';
  }

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.statistiques().subscribe({ next: (s) => this.statistiques.set(s) });
    this.chargerAbonnements();
    this.service.entreprises().subscribe({
      next: (liste) => {
        this.entreprises.set(liste);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des entreprises impossible.');
      }
    });
  }

  private chargerAbonnements(): void {
    this.abonnementService.tousLesAbonnements().subscribe({
      next: (liste) => this.abonnements.set(new Map(liste.map((a) => [a.entrepriseId, a])))
    });
  }

  abonnement(id: number): AbonnementVue | undefined {
    return this.abonnements().get(id);
  }

  /** Part d'une limite consommee, en pourcentage (0 si illimitee). */
  partUsage(utilise: number, limite?: number | null): number {
    return limite ? Math.min(100, Math.round((utilise * 100) / limite)) : 0;
  }

  // ------------------------------------------------------------ creation

  ouvrirCreation(): void {
    this.saisie = this.saisieVide();
    this.modulesChoisis.set([]);
    this.erreurCreation.set(null);
    this.creation.set(true);
    if (!this.catalogue().length) {
      this.service.cataloguePublic().subscribe({
        next: (modules) => {
          this.catalogue.set(modules.filter((m) => m.disponible));
          const parDefaut: Partial<Record<CodeModule, string>> = {};
          modules.filter((m) => m.variantes.length).forEach((m) => (parDefaut[m.code] = m.variantes[0].code));
          this.variantes.set(parDefaut);
        }
      });
    }
  }

  basculerModuleCreation(code: CodeModule): void {
    this.modulesChoisis.update((l) => (l.includes(code) ? l.filter((m) => m !== code) : [...l, code]));
  }

  choisirVariante(code: CodeModule, variante: string): void {
    this.variantes.update((v) => ({ ...v, [code]: variante }));
  }

  creer(): void {
    const s = this.saisie;
    if (!s.nomEntreprise.trim() || !s.nom.trim() || !s.email.trim() || s.motDePasse.length < 8) {
      this.erreurCreation.set('Renseignez le nom de l\'entreprise, le nom et l\'e-mail de l\'administrateur et un mot de passe de 8 caracteres au moins.');
      return;
    }
    const modules = this.modulesChoisis();
    const variantes: Partial<Record<CodeModule, string>> = {};
    modules.filter((m) => this.variantes()[m]).forEach((m) => (variantes[m] = this.variantes()[m]));
    this.enCours.set(-1);
    this.erreurCreation.set(null);
    this.service.creerEntreprise({
      nomEntreprise: s.nomEntreprise.trim(), pays: s.pays.trim() || undefined, telephone: s.telephone.trim() || undefined,
      nom: s.nom.trim(), prenom: s.prenom.trim() || undefined, email: s.email.trim(), motDePasse: s.motDePasse,
      modules, variantes
    }, s.joursEssai || null).subscribe({
      next: (e) => {
        this.enCours.set(null);
        this.creation.set(false);
        this.entreprises.update((liste) => [e, ...liste]);
        this.succes.set(`${e.nom} est creee, en essai gratuit. L'administrateur se connecte avec ${s.email.trim()}.`);
        this.service.statistiques().subscribe({ next: (st) => this.statistiques.set(st) });
        this.chargerAbonnements();
      },
      error: (r) => {
        this.enCours.set(null);
        this.erreurCreation.set(message(r, 'Creation impossible.'));
      }
    });
  }

  private saisieVide(): NouvelleEntreprise {
    return { nomEntreprise: '', pays: '', telephone: '', nom: '', prenom: '', email: '', motDePasse: '', joursEssai: null };
  }

  // ------------------------------------------------- fiche d'abonnement

  ouvrirFiche(e: EntrepriseResume): void {
    this.fiche.set(e);
    this.modeFiche.set('apercu');
    this.erreurFiche.set(null);
    this.ficheAbonnement.set(this.abonnement(e.id) ?? null);
    this.fichePaiements.set([]);
    this.abonnementService.abonnementEntreprise(e.id).subscribe({ next: (a) => this.ficheAbonnement.set(a) });
    this.abonnementService.paiementsEntreprise(e.id).subscribe({ next: (p) => this.fichePaiements.set(p) });
  }

  fermerFiche(): void {
    this.fiche.set(null);
  }

  prolonger(): void {
    const e = this.fiche();
    if (!e || !this.joursProlongation || this.enCours() !== null) {
      return;
    }
    this.enCours.set(e.id);
    this.abonnementService.prolonger(e.id, this.joursProlongation).subscribe({
      next: (a) => {
        this.enCours.set(null);
        this.ficheAbonnement.set(a);
        this.abonnements.update((m) => new Map(m).set(e.id, a));
        this.succes.set(`Echeance de ${e.nom} repoussee de ${this.joursProlongation} jour(s).`);
      },
      error: (r) => {
        this.enCours.set(null);
        this.erreurFiche.set(message(r, 'Prolongation impossible.'));
      }
    });
  }

  ouvrirPaiement(): void {
    const e = this.fiche();
    if (!e) {
      return;
    }
    const a = this.ficheAbonnement();
    this.moyen = 'MOBILE_MONEY';
    this.reference = '';
    this.note = '';
    this.montantLibre = null;
    this.periodicite.set((a?.periodicite as Periodicite) ?? 'MENSUEL');
    this.modulesPaiement.set(a && !a.essai && a.modules.length ? [...a.modules] : [...e.modules]);
    this.modeFiche.set('paiement');
    this.abonnementService.formules().subscribe({
      next: (f) => {
        this.formules.set(f.filter((x) => x.active || x.id === a?.formuleId));
        this.formuleId.set(a?.formuleId ?? f.find((x) => x.miseEnAvant)?.id ?? f[0]?.id ?? null);
      }
    });
    this.abonnementService.parametres().subscribe({ next: (p) => this.parametres.set(p) });
  }

  enregistrerPaiement(): void {
    const e = this.fiche();
    const formuleId = this.formuleId();
    if (!e || !formuleId || !this.modulesPaiement().length || this.enCours() !== null) {
      this.erreurFiche.set('Choisissez une formule et au moins un module.');
      return;
    }
    this.enCours.set(e.id);
    this.erreurFiche.set(null);
    this.abonnementService.enregistrer(e.id, {
      formuleId, periodicite: this.periodicite(), modules: this.modulesPaiement(), moyen: this.moyen,
      reference: this.reference || null, note: this.note || null,
      montant: this.montantLibre === null || (this.montantLibre as unknown) === '' ? null : this.montantLibre
    }).subscribe({
      next: (p) => {
        this.enCours.set(null);
        this.succes.set(`Paiement enregistre pour ${e.nom} : facture ${p.numeroFacture}, jusqu'au ${new Date(p.periodeFin + 'T00:00:00').toLocaleDateString('fr-FR')}.`);
        this.ouvrirFiche(e);
        this.chargerAbonnements();
        this.service.entreprises().subscribe({ next: (l) => this.entreprises.set(l) });
      },
      error: (r) => {
        this.enCours.set(null);
        this.erreurFiche.set(message(r, 'Enregistrement impossible.'));
      }
    });
  }

  imprimer(p: PaiementAbonnement): void {
    this.abonnementService.parametres().subscribe({
      next: (prm) => imprimerFactureAbonnement(p, environment.nomPlateforme, prm.mentionsFacture)
    });
  }

  voirPreuve(p: PaiementAbonnement): void {
    this.abonnementService.preuve(p.id, true).subscribe({
      next: (blob) => window.open(URL.createObjectURL(blob), '_blank')
    });
  }

  /** Option du module Agences : une seule agence (par defaut) ou plusieurs. */
  basculerAgencesMultiples(entreprise: EntrepriseResume): void {
    if (this.enCours() !== null) {
      return;
    }
    this.enCours.set(entreprise.id);
    this.erreur.set(null);
    this.service.autoriserAgencesMultiples(entreprise.id, !entreprise.agencesMultiples).subscribe({
      next: (maj) => {
        this.enCours.set(null);
        this.entreprises.update((liste) => liste.map((e) => (e.id === maj.id ? maj : e)));
      },
      error: (reponse) => {
        this.enCours.set(null);
        const message = (reponse as { error?: { message?: string } })?.error?.message;
        this.erreur.set(message ?? 'Changement de l\'option impossible.');
      }
    });
  }

  basculerStatut(entreprise: EntrepriseResume): void {
    if (this.enCours() !== null) {
      return;
    }
    const statut = entreprise.statut === 'ACTIVE' ? 'SUSPENDUE' : 'ACTIVE';
    this.enCours.set(entreprise.id);
    this.erreur.set(null);
    this.succes.set(null);
    this.service.changerStatut(entreprise.id, statut).subscribe({
      next: (maj) => {
        this.enCours.set(null);
        this.aSuspendre.set(null);
        this.succes.set(statut === 'SUSPENDUE' ? `${maj.nom} est suspendue.` : `${maj.nom} est reactivee.`);
        this.entreprises.update((liste) => liste.map((e) => (e.id === maj.id ? maj : e)));
        this.service.statistiques().subscribe({ next: (s) => this.statistiques.set(s) });
      },
      error: (reponse) => {
        this.enCours.set(null);
        this.aSuspendre.set(null);
        const message = (reponse as { error?: { message?: string } })?.error?.message;
        this.erreur.set(message ?? 'Changement de statut impossible.');
      }
    });
  }
}
