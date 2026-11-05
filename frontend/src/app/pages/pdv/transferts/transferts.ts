import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { DecisionEcart, DisponibiliteLieu, LIBELLES_STATUT_TRANSFERT, StatutTransfert, Transfert } from '../../../core/models/lieux';
import { LieuxService } from '../../../core/services/lieux.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { Modale } from '../../../shared/modale';
import { Page } from '../../../core/models/models';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';
import { FormulaireTransfert } from './formulaire-transfert';

/**
 * Transferts entre lieux : le magasinier demande, le gerant valide, le depart expedie,
 * l'arrivee compte. Un ecart a l'arrivee attend la decision du gerant.
 */
@Component({
  selector: 'app-transferts-pdv',
  imports: [FormsModule, DatePipe, DecimalPipe, Modale, FormulaireTransfert, SuiteListe],
  templateUrl: './transferts.html',
  styleUrl: './transferts.scss'
})
export class TransfertsPdv implements OnInit {
  private readonly service = inject(LieuxService);
  private readonly materiaux = inject(MateriauxService);
  readonly gerant = inject(AuthService).aRole('ADMIN', 'GERANT');
  readonly libelles = LIBELLES_STATUT_TRANSFERT;
  readonly etat = this.service.etat;

  readonly pageTransferts = signal<Page<Transfert> | null>(null);
  readonly transferts = computed(() => this.pageTransferts()?.content ?? []);
  private readonly suite = new SuiteDePages();
  readonly statuts = Object.keys(LIBELLES_STATUT_TRANSFERT) as StatutTransfert[];
  /** Par defaut, tous les transferts. */
  readonly filtre = signal<StatutTransfert | 'EN_COURS' | ''>('');
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly nouvelle = signal(false);

  // Expedition, reception, ecart
  readonly erreurFenetre = signal<string | null>(null);
  readonly action = signal<{ type: 'validation' | 'expedition' | 'reception' | 'ecart'; transfert: Transfert } | null>(null);
  /** Validation : le lieu de depart choisi par le gerant, et le stock de chaque lieu pour l'aider. */
  readonly departValidation = signal<number | null>(null);
  private readonly disponibilites = signal<DisponibiliteLieu[]>([]);
  readonly quantites = signal<Record<number, number | null>>({});
  transporteur = '';
  decision: DecisionEcart = 'PERTE';
  motif = '';

  /** Lieux ou l'utilisateur peut agir : le gerant les a tous. */
  private readonly mesLieux = computed(() => new Set((this.etat()?.mesLieux ?? []).map((l) => l.id)));

  ngOnInit(): void {
    if (!this.etat()) {
      this.service.chargerEtat();
    }
    this.charger();
  }

  charger(): void {
    this.service.transferts(this.filtre(), this.suite.numero()).subscribe({
      next: (p) => this.pageTransferts.set(cumulerPage(this.pageTransferts(), p)),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement des transferts impossible.')
    });
  }

  /** Bas de la liste atteint : la page suivante rejoint les transferts deja affiches. */
  suivante(): void {
    if (this.suite.demander(this.pageTransferts())) {
      this.charger();
    }
  }

  peutExpedier(t: Transfert): boolean {
    return t.statut === 'VALIDE' && !!t.sourceId && this.mesLieux().has(t.sourceId);
  }

  peutRecevoir(t: Transfert): boolean {
    return t.statut === 'EXPEDIE' && this.mesLieux().has(t.destinationId);
  }

  transfertEnregistre(t: Transfert): void {
    this.nouvelle.set(false);
    this.succes.set(this.gerant ? `Transfert ${t.numero} cree : validez-le pour qu'il parte.` : `Demande ${t.numero} envoyee au gerant.`);
    this.charger();
  }

  departsPossibles(t: Transfert) {
    return (this.etat()?.lieux ?? []).filter((l) => l.id !== t.destinationId);
  }

  dispoDepart(articleId: number): number {
    return this.disponibilites().find((d) => d.articleId === articleId && d.lieuId === this.departValidation())?.disponible ?? 0;
  }

  annuler(t: Transfert): void {
    this.executer(this.service.annulerTransfert(t.id, ''), `Transfert ${t.numero} annule.`);
  }

  ouvrirAction(type: 'validation' | 'expedition' | 'reception' | 'ecart', t: Transfert): void {
    if (type === 'validation') {
      this.departValidation.set(t.sourceId ?? null);
      this.disponibilites.set([]);
      this.service.disponibilites(t.lignes.map((l) => l.articleId)).subscribe({ next: (d) => this.disponibilites.set(d) });
    }
    this.quantites.set(Object.fromEntries(t.lignes.map((l) => [l.id, type === 'expedition' ? l.quantite : (l.quantiteExpediee ?? 0)])));
    this.transporteur = '';
    this.decision = 'PERTE';
    this.motif = '';
    this.erreurFenetre.set(null);
    this.action.set({ type, transfert: t });
  }

  changerQuantite(id: number, q: number | null): void {
    this.quantites.update((v) => ({ ...v, [id]: q }));
  }

  confirmerAction(): void {
    const a = this.action();
    if (!a) {
      return;
    }
    const lignes = a.transfert.lignes.map((l) => ({ ligneId: l.id, quantite: Math.max(0, this.quantites()[l.id] ?? 0) }));
    const appel = a.type === 'validation' ? this.service.validerTransfert(a.transfert.id, this.departValidation())
      : a.type === 'expedition' ? this.service.expedierTransfert(a.transfert.id, this.transporteur.trim(), lignes)
      : a.type === 'reception' ? this.service.receptionnerTransfert(a.transfert.id, lignes)
      : this.service.traiterEcart(a.transfert.id, this.decision, this.motif.trim());
    appel.subscribe({
      next: (t) => {
        this.action.set(null);
        this.succes.set(a.type === 'validation' ? `Transfert ${t.numero} valide depuis ${t.sourceNom}.`
          : a.type === 'expedition' ? `Transfert ${t.numero} parti : imprimez le bon pour le transporteur.`
          : t.statut === 'ECART' ? `Reception enregistree : l'ecart attend la decision du gerant.`
          : `Transfert ${t.numero} termine.`);
        this.charger();
      },
      error: (e) => this.erreurFenetre.set(e?.error?.message ?? 'Operation impossible.')
    });
  }

  imprimer(t: Transfert): void {
    this.service.transfertPdf(t.id).subscribe({ next: (pdf) => this.materiaux.ouvrirPdf(pdf) });
  }

  private executer(appel: ReturnType<LieuxService['validerTransfert']>, message: string): void {
    this.erreur.set(null);
    appel.subscribe({
      next: () => {
        this.succes.set(message);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Operation impossible.')
    });
  }
}
