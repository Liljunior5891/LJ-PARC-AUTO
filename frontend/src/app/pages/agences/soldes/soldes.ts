import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CompteAgence, EtatSoldes, FamilleCompte, LigneRegistre, RequeteCompte, SoldesAgence, TypeCompte, VerificationSolde } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

const FAMILLES: Record<FamilleCompte, string> = {
  CASH: 'Cash',
  VIRTUEL: 'Virtuel',
  BANQUE: 'Banque',
  COMMISSIONS: 'Commissions',
  FONDS: 'Fonds d\'exploitation'
};

/**
 * Soldes et tresorerie : toutes les agences pour l'administrateur, puis le
 * detail des comptes d'une agence. Chaque solde vient du registre des
 * mouvements, consultable compte par compte ; les soldes d'ouverture se
 * saisissent ici une fois par compte.
 */
@Component({
  selector: 'app-soldes-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, Modale, SuiteListe],
  templateUrl: './soldes.html',
  styleUrl: './soldes.scss'
})
export class SoldesAgences implements OnInit {
  readonly service = inject(AgencesService);

  readonly etat = signal<EtatSoldes | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly interdit = signal(false);
  readonly familles = FAMILLES;

  /** Agence affichee ; nulle : toutes les agences (vue centralisee). */
  agenceId: number | null = null;
  readonly agence = computed<SoldesAgence | null>(() => {
    const e = this.etat();
    return e && this.agenceChoisie() ? e.agences.find((a) => a.id === this.agenceChoisie()) ?? null : null;
  });
  private readonly agenceChoisie = signal<number | null>(null);
  readonly plusieurs = computed(() => (this.service.moi()?.agences.length ?? 0) > 1);

  // Registre d'un compte
  readonly compteRegistre = signal<CompteAgence | null>(null);
  readonly lignes = signal<Page<LigneRegistre> | null>(null);
  private readonly suite = new SuiteDePages();
  filtreDu = '';
  filtreAu = '';
  jourReconstitue = '';
  readonly soldeReconstitue = signal<{ jour: string; solde: number } | null>(null);

  // Soldes d'ouverture
  readonly ouverture = signal(false);
  montantsOuverture: Record<number, number | null> = {};
  observationOuverture = '';
  readonly erreurModale = signal<string | null>(null);
  readonly comptesOuvrables = computed(() => (this.agence()?.comptes ?? []).filter((c) => c.actif && c.nombreMouvements === 0));

  // Compte bancaire / reglage d'un compte
  readonly compteEdite = signal<CompteAgence | 'nouveau' | null>(null);
  formulaireCompte: RequeteCompte & { type: TypeCompte } = { type: 'BANQUE' };

  readonly verification = signal<VerificationSolde[] | null>(null);

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (moi) => {
        if (!moi.permissions.includes('SOLDES_CONSULTER')) {
          this.interdit.set(true);
          this.chargement.set(false);
          return;
        }
        // Une seule agence visible : on l'ouvre directement.
        if (moi.agences.length === 1) {
          this.agenceId = moi.agences[0].id;
        }
        this.charger();
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Lecture de vos droits impossible.'));
      }
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service.soldes(this.agenceId).subscribe({
      next: (e) => {
        this.etat.set(e);
        this.agenceChoisie.set(this.agenceId);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Chargement des soldes impossible.'));
      }
    });
  }

  choisirAgence(id: number | null): void {
    this.agenceId = id;
    this.succes.set(null);
    this.verification.set(null);
    this.charger();
  }

  /** Comptes d'une famille, pour regrouper le tableau. */
  comptesDe(famille: FamilleCompte): CompteAgence[] {
    return (this.agence()?.comptes ?? []).filter((c) => c.famille === famille);
  }

  famillesPresentes(): FamilleCompte[] {
    const presentes = new Set((this.agence()?.comptes ?? []).map((c) => c.famille));
    return (Object.keys(FAMILLES) as FamilleCompte[]).filter((f) => presentes.has(f));
  }

  totalFamille(famille: FamilleCompte): number {
    return this.comptesDe(famille).filter((c) => c.actif && !c.bloque).reduce((s, c) => s + c.solde, 0);
  }

  // ----------------------------------------------------------- registre
  ouvrirRegistre(compte: CompteAgence): void {
    this.compteRegistre.set(compte);
    this.lignes.set(null);
    this.filtreDu = '';
    this.filtreAu = '';
    this.jourReconstitue = '';
    this.soldeReconstitue.set(null);
    this.chargerRegistre();
  }

  chargerRegistre(): void {
    const compte = this.compteRegistre();
    if (!compte) {
      return;
    }
    this.service.registre(compte.id, this.suite.numero(), this.filtreDu || undefined, this.filtreAu || undefined).subscribe({
      next: (p) => this.lignes.set(cumulerPage(this.lignes(), p)),
      error: (e) => this.erreurModale.set(this.message(e, 'Lecture du registre impossible.'))
    });
  }

  suiteRegistre(): void {
    if (this.suite.demander(this.lignes())) {
      this.chargerRegistre();
    }
  }

  filtrerRegistre(): void {
    this.lignes.set(null);
    this.chargerRegistre();
  }

  reconstituer(): void {
    const compte = this.compteRegistre();
    if (!compte || !this.jourReconstitue) {
      return;
    }
    this.service.soldeAu(compte.id, this.jourReconstitue).subscribe({
      next: (r) => this.soldeReconstitue.set({ jour: r.jour, solde: r.solde }),
      error: (e) => this.erreurModale.set(this.message(e, 'Calcul impossible.'))
    });
  }

  // --------------------------------------------------- soldes d'ouverture
  ouvrirSaisieOuverture(): void {
    this.montantsOuverture = {};
    this.observationOuverture = '';
    this.erreurModale.set(null);
    this.ouverture.set(true);
  }

  totalOuverture(): number {
    return Object.values(this.montantsOuverture).reduce<number>((s, m) => s + (m && m > 0 ? m : 0), 0);
  }

  enregistrerOuverture(): void {
    const agence = this.agence();
    if (!agence) {
      return;
    }
    const soldes = Object.entries(this.montantsOuverture)
      .filter(([, m]) => m != null && m > 0)
      .map(([id, m]) => ({ compteId: Number(id), montant: Math.round(m as number) }));
    if (!soldes.length) {
      this.erreurModale.set('Saisissez au moins un solde.');
      return;
    }
    this.service.ouvrir(agence.id, soldes, this.observationOuverture.trim() || undefined).subscribe({
      next: (e) => {
        this.etat.set(e);
        this.ouverture.set(false);
        this.succes.set(`Soldes d'ouverture enregistres pour ${agence.nom} : ${this.totalOuvertureTexte(soldes)}.`);
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  private totalOuvertureTexte(soldes: { montant: number }[]): string {
    return `${soldes.length} compte(s), ${soldes.reduce((s, x) => s + x.montant, 0).toLocaleString('fr-FR')} F`;
  }

  // -------------------------------------------------------------- comptes
  nouveauCompte(): void {
    this.formulaireCompte = { type: 'BANQUE', libelle: '', banque: '', numeroCompte: '', decouvertAutorise: 0, bloque: false };
    this.erreurModale.set(null);
    this.compteEdite.set('nouveau');
  }

  modifierCompte(c: CompteAgence): void {
    this.formulaireCompte = {
      type: c.type, libelle: c.libelle, banque: c.banque ?? '', numeroCompte: c.numeroCompte ?? '',
      decouvertAutorise: c.decouvertAutorise, bloque: c.bloque, actif: c.actif, seuilAlerte: c.seuilAlerte ?? null
    };
    this.erreurModale.set(null);
    this.compteEdite.set(c);
  }

  enregistrerCompte(): void {
    const edition = this.compteEdite();
    const agence = this.agence();
    if (!edition || !agence) {
      return;
    }
    const f = this.formulaireCompte;
    const requete: RequeteCompte = {
      type: f.type, libelle: f.libelle?.trim(), banque: f.banque?.trim() || undefined,
      numeroCompte: f.numeroCompte?.trim() || undefined, decouvertAutorise: Math.max(0, Math.round(f.decouvertAutorise ?? 0)),
      bloque: !!f.bloque, actif: edition === 'nouveau' ? true : f.actif,
      seuilAlerte: f.seuilAlerte == null || (f.seuilAlerte as unknown) === '' ? null : Math.max(0, Math.round(f.seuilAlerte))
    };
    const appel = edition === 'nouveau' ? this.service.creerCompte(agence.id, requete) : this.service.modifierCompte(edition.id, requete);
    appel.subscribe({
      next: (c) => {
        this.compteEdite.set(null);
        this.succes.set(`Compte ${c.libelle} enregistre.`);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  // --------------------------------------------------------- verification
  verifier(): void {
    const agence = this.agence();
    if (!agence) {
      return;
    }
    this.succes.set(null);
    this.service.verifier(agence.id).subscribe({
      next: (r) => {
        const ecarts = r.filter((v) => v.ecart !== 0);
        this.verification.set(ecarts);
        if (!ecarts.length) {
          this.succes.set(`Les ${r.length} soldes de ${agence.nom} correspondent exactement au registre.`);
        }
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Verification impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
