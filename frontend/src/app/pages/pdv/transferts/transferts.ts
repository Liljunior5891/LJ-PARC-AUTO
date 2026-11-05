import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { DecisionEcart, LIBELLES_STATUT_TRANSFERT, Transfert } from '../../../core/models/lieux';
import { LieuxService } from '../../../core/services/lieux.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { Modale } from '../../../shared/modale';
import { FormulaireTransfert } from './formulaire-transfert';

/**
 * Transferts entre lieux : le magasinier demande, le gerant valide, le depart expedie,
 * l'arrivee compte. Un ecart a l'arrivee attend la decision du gerant.
 */
@Component({
  selector: 'app-transferts-pdv',
  imports: [FormsModule, DatePipe, DecimalPipe, Modale, FormulaireTransfert],
  templateUrl: './transferts.html',
  styleUrl: './transferts.scss'
})
export class TransfertsPdv implements OnInit {
  private readonly service = inject(LieuxService);
  private readonly materiaux = inject(MateriauxService);
  readonly gerant = inject(AuthService).aRole('ADMIN', 'GERANT');
  readonly libelles = LIBELLES_STATUT_TRANSFERT;
  readonly etat = this.service.etat;

  readonly transferts = signal<Transfert[]>([]);
  readonly enCours = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly nouvelle = signal(false);

  // Expedition, reception, ecart
  readonly erreurFenetre = signal<string | null>(null);
  readonly action = signal<{ type: 'expedition' | 'reception' | 'ecart'; transfert: Transfert } | null>(null);
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
    this.service.transferts(this.enCours()).subscribe({
      next: (p) => this.transferts.set(p.content),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement des transferts impossible.')
    });
  }

  peutExpedier(t: Transfert): boolean {
    return t.statut === 'VALIDE' && this.mesLieux().has(t.sourceId);
  }

  peutRecevoir(t: Transfert): boolean {
    return t.statut === 'EXPEDIE' && this.mesLieux().has(t.destinationId);
  }

  transfertEnregistre(t: Transfert): void {
    this.nouvelle.set(false);
    this.succes.set(this.gerant ? `Transfert ${t.numero} cree : validez-le pour qu'il parte.` : `Demande ${t.numero} envoyee au gerant.`);
    this.charger();
  }

  valider(t: Transfert): void {
    this.executer(this.service.validerTransfert(t.id), `Transfert ${t.numero} valide.`);
  }

  annuler(t: Transfert): void {
    this.executer(this.service.annulerTransfert(t.id, ''), `Transfert ${t.numero} annule.`);
  }

  ouvrirAction(type: 'expedition' | 'reception' | 'ecart', t: Transfert): void {
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
    const appel = a.type === 'expedition' ? this.service.expedierTransfert(a.transfert.id, this.transporteur.trim(), lignes)
      : a.type === 'reception' ? this.service.receptionnerTransfert(a.transfert.id, lignes)
      : this.service.traiterEcart(a.transfert.id, this.decision, this.motif.trim());
    appel.subscribe({
      next: (t) => {
        this.action.set(null);
        this.succes.set(a.type === 'expedition' ? `Transfert ${t.numero} parti : imprimez le bon pour le transporteur.`
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
