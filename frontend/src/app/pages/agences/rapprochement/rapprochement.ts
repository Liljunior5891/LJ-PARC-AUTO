import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AttenteOperateur, CommissionAttente, CompteAgence, VersementCommission, ServiceFinancier } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { LogoService } from '../../../shared/logo-service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Rapprochement des commissions : pour chaque operateur, les commissions
 * acquises qu'il n'a pas encore versees. On enregistre son versement (montant
 * recu, compte credite) en cochant les commissions couvertes, une a une ou
 * toutes celles d'une periode ; l'ecart entre attendu et verse est garde.
 */
@Component({
  selector: 'app-rapprochement',
  imports: [DatePipe, DecimalPipe, FormsModule, LogoService, Modale, SuiteListe],
  templateUrl: './rapprochement.html',
  styleUrl: './rapprochement.scss'
})
export class RapprochementCommissions implements OnInit {

  /** Catalogue des services, pour afficher leurs logos. */
  readonly catalogueLogos = signal<ServiceFinancier[]>([]);

  logoService(id?: number | null): string | null | undefined {
    return this.catalogueLogos().find((s) => s.id === id)?.logo;
  }
  readonly service = inject(AgencesService);

  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));
  agenceId: number | null = null;
  readonly attentes = signal<AttenteOperateur[]>([]);
  readonly operateur = signal<AttenteOperateur | null>(null);
  readonly commissions = signal<Page<CommissionAttente> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly versements = signal<Page<VersementCommission> | null>(null);
  private readonly suiteVersements = new SuiteDePages();
  readonly comptes = signal<CompteAgence[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);

  du = '';
  au = '';
  /** Commissions cochees (identifiant de transaction -> commission). */
  readonly choisies = signal<Map<number, number>>(new Map());
  readonly totalChoisi = computed(() => [...this.choisies().values()].reduce((s, c) => s + c, 0));

  readonly saisie = signal<'selection' | 'periode' | null>(null);
  fv = { montantVerse: null as number | null, compteId: null as number | null, referenceOperateur: '', observations: '' };
  readonly aAnnuler = signal<VersementCommission | null>(null);
  motif = '';

  ngOnInit(): void {
    this.service.catalogue().subscribe({ next: (c) => this.catalogueLogos.set(c) });
    this.service.chargerMoi().subscribe({
      next: (m) => {
        this.agenceId = m.agences.find((a) => a.actif)?.id ?? null;
        this.chargerTout();
      }
    });
  }

  chargerTout(): void {
    if (!this.agenceId) {
      return;
    }
    this.service.attentesCommissions(this.agenceId).subscribe({
      next: (a) => {
        this.attentes.set(a);
        const courant = this.operateur();
        const suivant = a.find((x) => x.serviceId === courant?.serviceId) ?? a.find((x) => x.nombre > 0) ?? null;
        this.choisirOperateur(suivant);
      },
      error: (e) => this.erreur.set(this.message(e, 'Lecture des commissions en attente impossible.'))
    });
    this.service.soldes(this.agenceId).subscribe({
      next: (e) => this.comptes.set((e.agences[0]?.comptes ?? []).filter((c) => c.actif && c.type !== 'CAISSE_CAISSIER'))
    });
  }

  changerAgence(): void {
    this.succes.set(null);
    this.operateur.set(null);
    this.chargerTout();
  }

  choisirOperateur(o: AttenteOperateur | null): void {
    this.operateur.set(o);
    this.choisies.set(new Map());
    this.commissions.set(null);
    this.versements.set(null);
    if (o) {
      this.chargerCommissions();
      this.chargerVersements();
    }
  }

  filtrer(): void {
    this.choisies.set(new Map());
    this.commissions.set(null);
    this.chargerCommissions();
  }

  chargerCommissions(): void {
    const o = this.operateur();
    if (!o || !this.agenceId) {
      return;
    }
    this.service.commissionsEnAttente(this.agenceId, o.serviceId, this.suite.numero(), this.du || undefined, this.au || undefined).subscribe({
      next: (p) => this.commissions.set(cumulerPage(this.commissions(), p))
    });
  }

  suiteCommissions(): void {
    if (this.suite.demander(this.commissions())) {
      this.chargerCommissions();
    }
  }

  chargerVersements(): void {
    const o = this.operateur();
    this.service.versementsCommissions(this.agenceId, o?.serviceId ?? null, this.suiteVersements.numero()).subscribe({
      next: (p) => this.versements.set(cumulerPage(this.versements(), p))
    });
  }

  suiteHistorique(): void {
    if (this.suiteVersements.demander(this.versements())) {
      this.chargerVersements();
    }
  }

  estChoisie(c: CommissionAttente): boolean {
    return this.choisies().has(c.transactionId);
  }

  basculer(c: CommissionAttente): void {
    this.choisies.update((m) => {
      const suivant = new Map(m);
      if (suivant.has(c.transactionId)) {
        suivant.delete(c.transactionId);
      } else {
        suivant.set(c.transactionId, c.commission);
      }
      return suivant;
    });
  }

  toutCocher(): void {
    const lignes = this.commissions()?.content ?? [];
    const toutes = lignes.every((c) => this.choisies().has(c.transactionId));
    this.choisies.set(toutes ? new Map() : new Map(lignes.map((c) => [c.transactionId, c.commission])));
  }

  /** Montant attendu pour le versement : la selection, ou toutes les commissions en attente de la periode affichee. */
  attendu(): number {
    return this.saisie() === 'periode' ? this.totalPeriode() : this.totalChoisi();
  }

  /** Total des commissions de la periode : connu exactement quand toute la liste est chargee, sinon par operateur. */
  totalPeriode(): number {
    const p = this.commissions();
    if (p && p.last) {
      return p.content.reduce((s, c) => s + c.commission, 0);
    }
    return this.du || this.au ? p?.content.reduce((s, c) => s + c.commission, 0) ?? 0 : this.operateur()?.montant ?? 0;
  }

  ouvrirVersement(mode: 'selection' | 'periode'): void {
    this.saisie.set(mode);
    this.fv = { montantVerse: null, compteId: this.comptes().find((c) => c.type === 'COMMISSIONS')?.id ?? this.comptes()[0]?.id ?? null,
      referenceOperateur: '', observations: '' };
    this.fv.montantVerse = this.attendu();
    this.erreurModale.set(null);
    this.succes.set(null);
  }

  ecart(): number {
    return Math.round(this.fv.montantVerse ?? 0) - this.attendu();
  }

  enregistrer(): void {
    const o = this.operateur();
    const mode = this.saisie();
    if (!o || !this.agenceId || this.fv.compteId == null || this.fv.montantVerse == null) {
      return;
    }
    this.service.enregistrerVersement({
      agenceId: this.agenceId, serviceId: o.serviceId,
      transactionIds: mode === 'selection' ? [...this.choisies().keys()] : undefined,
      du: mode === 'periode' ? this.du || undefined : undefined, au: mode === 'periode' ? this.au || undefined : undefined,
      montantVerse: Math.round(this.fv.montantVerse), compteId: this.fv.compteId,
      referenceOperateur: this.fv.referenceOperateur.trim() || undefined, observations: this.fv.observations.trim() || undefined
    }).subscribe({
      next: (v) => {
        this.saisie.set(null);
        this.succes.set(`${v.reference} : ${v.nombreCommissions} commission(s) ${v.service} rapprochee(s), attendu ${v.montantAttendu.toLocaleString('fr-FR')} F, `
          + `verse ${v.montantVerse.toLocaleString('fr-FR')} F` + (v.ecart ? `, ecart ${v.ecart > 0 ? '+' : ''}${v.ecart.toLocaleString('fr-FR')} F.` : ', sans ecart.'));
        this.chargerTout();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  demanderAnnulation(v: VersementCommission): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aAnnuler.set(v);
  }

  annuler(): void {
    const v = this.aAnnuler();
    if (!v || !this.motif.trim()) {
      return;
    }
    this.service.annulerVersement(v.id, this.motif.trim()).subscribe({
      next: (r) => {
        this.aAnnuler.set(null);
        this.succes.set(`${r.reference} annule : ${r.nombreCommissions} commission(s) de nouveau en attente.`);
        this.chargerTout();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Annulation impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
