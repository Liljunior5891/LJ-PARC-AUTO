import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { Cloture, ClotureAFaire, FamilleCompte, PreparationCloture } from '../../../core/models/agences';
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

const STATUTS: Record<string, string> = {
  SOUMISE: 'Soumise, a valider',
  VALIDEE: 'Validee : journee verrouillee',
  REJETEE: 'Rejetee : a refaire',
  ROUVERTE: 'Rouverte : a refaire',
  'A FAIRE': 'A faire'
};

/**
 * Cloture journaliere d'une agence : pour chaque compte, le solde theorique
 * du registre et le solde reel (cash compte, solde de chaque float releve chez
 * l'operateur) ; l'ecart se justifie. Une cloture validee verrouille la
 * journee ; seule une reouverture motivee de l'administrateur la debloque.
 */
@Component({
  selector: 'app-cloture-agence',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, SuiteListe],
  templateUrl: './cloture.html',
  styleUrl: './cloture.scss'
})
export class ClotureAgencePage implements OnInit {
  readonly service = inject(AgencesService);
  readonly administrateur = inject(AuthService).aRole('ADMIN');
  readonly familles = FAMILLES;
  readonly statuts = STATUTS;

  readonly preparation = signal<PreparationCloture | null>(null);
  readonly aFaire = signal<ClotureAFaire[]>([]);
  readonly historique = signal<Page<Cloture> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));

  agenceId: number | null = null;
  journee = '';
  reels: Record<number, number | null> = {};
  justifications: Record<number, string> = {};
  observations = '';

  /** Saisie possible : pas de cloture, ou cloture rejetee ou rouverte. */
  readonly saisissable = computed(() => {
    const c = this.preparation()?.cloture;
    return !c || c.statut === 'REJETEE' || c.statut === 'ROUVERTE';
  });

  readonly decision = signal<{ c: Cloture; action: 'rejet' | 'reouverture' } | null>(null);
  motifDecision = '';
  readonly detail = signal<Cloture | null>(null);

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        this.agenceId = m.agences.find((a) => a.actif)?.id ?? null;
        this.charger();
      }
    });
  }

  charger(journee?: string): void {
    if (!this.agenceId) {
      return;
    }
    this.erreur.set(null);
    this.service.preparationCloture(this.agenceId, journee || this.journee || undefined).subscribe({
      next: (p) => {
        this.preparation.set(p);
        this.journee = p.journee;
        this.reels = {};
        this.justifications = {};
        this.observations = '';
        // Une cloture rejetee ou rouverte reprend les montants deja saisis.
        p.cloture?.lignes.forEach((l) => {
          if (l.soldeReel != null) {
            this.reels[l.compteId] = l.soldeReel;
          }
          if (l.justification) {
            this.justifications[l.compteId] = l.justification;
          }
        });
      },
      error: (e) => this.erreur.set(this.message(e, 'Preparation de la cloture impossible.'))
    });
    this.service.cloturesAFaire(this.agenceId).subscribe({ next: (a) => this.aFaire.set(a) });
    this.historique.set(null);
    this.chargerHistorique();
  }

  chargerHistorique(): void {
    this.service.clotures(this.agenceId, this.suite.numero()).subscribe({
      next: (p) => this.historique.set(cumulerPage(this.historique(), p))
    });
  }

  suiteHistorique(): void {
    if (this.suite.demander(this.historique())) {
      this.chargerHistorique();
    }
  }

  changerAgence(): void {
    this.journee = '';
    this.succes.set(null);
    this.charger();
  }

  choisirJournee(j: string): void {
    this.journee = j;
    this.succes.set(null);
    this.charger(j);
  }

  ecart(compteId: number, theorique: number): number | null {
    const r = this.reels[compteId];
    return r == null ? null : Math.round(r) - theorique;
  }

  peutSoumettre(): boolean {
    const p = this.preparation();
    if (!p || p.caissesOuvertes.length) {
      return false;
    }
    return p.comptes.every((c) => {
      const e = this.ecart(c.compteId, c.soldeTheorique);
      return (!c.obligatoire || this.reels[c.compteId] != null) && (e == null || e === 0 || !!this.justifications[c.compteId]?.trim());
    });
  }

  /** Recopie le theorique dans le reel : a utiliser apres avoir verifie que le compte est juste. */
  reprendreTheorique(compteId: number, theorique: number): void {
    this.reels[compteId] = theorique;
  }

  soumettre(): void {
    const p = this.preparation();
    if (!p || !this.peutSoumettre()) {
      return;
    }
    const lignes = p.comptes.map((c) => ({
      compteId: c.compteId,
      soldeReel: this.reels[c.compteId] == null ? null : Math.round(this.reels[c.compteId] as number),
      justification: this.justifications[c.compteId]?.trim() || undefined
    }));
    this.service.soumettreCloture(p.agenceId, p.journee, lignes, this.observations.trim() || undefined).subscribe({
      next: (c) => {
        this.succes.set(`Cloture du ${new Date(c.journee).toLocaleDateString('fr-FR')} soumise : un superviseur ou l'administrateur doit la valider.`);
        this.charger(c.journee);
      },
      error: (e) => this.erreur.set(this.message(e, 'Soumission impossible.'))
    });
  }

  valider(c: Cloture): void {
    this.service.validerCloture(c.id).subscribe({
      next: (r) => {
        this.succes.set(`Journee du ${new Date(r.journee).toLocaleDateString('fr-FR')} cloturee et verrouillee.`);
        this.charger(r.journee);
      },
      error: (e) => this.erreur.set(this.message(e, 'Validation impossible.'))
    });
  }

  demander(c: Cloture, action: 'rejet' | 'reouverture'): void {
    this.motifDecision = '';
    this.erreurModale.set(null);
    this.decision.set({ c, action });
  }

  confirmer(): void {
    const d = this.decision();
    if (!d || !this.motifDecision.trim()) {
      return;
    }
    const appel = d.action === 'rejet' ? this.service.rejeterCloture(d.c.id, this.motifDecision.trim())
      : this.service.rouvrirCloture(d.c.id, this.motifDecision.trim());
    appel.subscribe({
      next: (r) => {
        this.decision.set(null);
        this.succes.set(d.action === 'rejet' ? 'Cloture rejetee : elle est a refaire.'
          : `Journee du ${new Date(r.journee).toLocaleDateString('fr-FR')} rouverte : les saisies sont de nouveau possibles, la cloture est a refaire.`);
        this.charger(r.journee);
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Action impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
