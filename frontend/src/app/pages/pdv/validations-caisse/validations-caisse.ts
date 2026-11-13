import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { Depense, LIBELLES_STATUT_DEPENSE, SortieAttente } from '../../../core/models/achats';
import { AchatsService } from '../../../core/services/achats.service';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { Modale } from '../../../shared/modale';

/** Element a refuser : une depense ou une sortie de caisse. */
interface ARefuser {
  type: 'depense' | 'sortie';
  id: number;
  numero: string;
  libelle: string;
  montant: number;
}

/**
 * Validation des depenses et sorties payees par la caisse (reglage du point de
 * vente) : le gerant vise, l'administrateur approuve et les especes sortent.
 */
@Component({
  selector: 'app-validations-caisse-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale],
  templateUrl: './validations-caisse.html',
  styleUrl: './validations-caisse.scss'
})
export class ValidationsCaissePdv implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly service = inject(AchatsService);
  readonly options = inject(OptionsPdvService);
  readonly estAdmin = this.auth.aRole('ADMIN');
  readonly libelles = LIBELLES_STATUT_DEPENSE;

  readonly depenses = signal<Depense[]>([]);
  readonly sorties = signal<SortieAttente[]>([]);
  readonly traitees = signal<SortieAttente[]>([]);
  readonly chargement = signal(true);
  readonly envoi = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly aRefuser = signal<ARefuser | null>(null);
  motif = '';

  readonly total = computed(() =>
    this.depenses().reduce((s, d) => s + d.montant, 0) + this.sorties().reduce((s, x) => s + x.montant, 0));

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service.validationsCaisse().subscribe({
      next: (v) => {
        this.depenses.set(v.depenses);
        this.sorties.set(v.sorties);
        this.traitees.set(v.sortiesTraitees);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Chargement impossible.');
      }
    });
  }

  /** Libelle du bouton : le gerant vise, l'administrateur approuve. */
  libelleValidation(): string {
    return this.estAdmin ? 'Approuver' : 'Viser';
  }

  validerDepense(d: Depense): void {
    this.lancer('depense-' + d.id, this.service.validerDepenseCaisse(d.id, d.version), (r: Depense) => r.statut === 'VALIDE'
      ? `Depense ${r.numero} approuvee : ${this.f(r.montant)} F sortis de ${r.caisseNom ?? 'la caisse'}.`
      : `Depense ${r.numero} visee : elle attend l'administrateur.`);
  }

  validerSortie(s: SortieAttente): void {
    this.lancer('sortie-' + s.id, this.service.validerSortieCaisse(s.id), (r: SortieAttente) => r.statut === 'VALIDEE'
      ? `Sortie ${r.numero} approuvee : ${this.f(r.montant)} F peuvent sortir de ${r.caisseNom}.`
      : `Sortie ${r.numero} visee : elle attend l'administrateur.`);
  }

  ouvrirRefus(type: 'depense' | 'sortie', id: number, numero: string, libelle: string, montant: number): void {
    this.motif = '';
    this.erreur.set(null);
    this.aRefuser.set({ type, id, numero, libelle, montant });
  }

  confirmerRefus(): void {
    const r = this.aRefuser();
    const motif = this.motif.trim();
    if (!r || !motif) {
      return;
    }
    const appel: Observable<unknown> = r.type === 'depense'
      ? this.service.refuserDepenseCaisse(r.id, motif)
      : this.service.refuserSortieCaisse(r.id, motif);
    this.lancer(r.type + '-' + r.id, appel, () => `${r.numero} refusee : rien n'est sorti de la caisse.`, () => this.aRefuser.set(null));
  }

  private lancer<T>(cle: string, appel: Observable<T>, message: (r: T) => string, apres?: () => void): void {
    if (this.envoi()) {
      return;
    }
    this.envoi.set(cle);
    this.erreur.set(null);
    this.succes.set(null);
    appel.subscribe({
      next: (r) => {
        this.envoi.set(null);
        this.succes.set(message(r));
        apres?.();
        this.charger();
      },
      error: (e) => {
        this.envoi.set(null);
        this.erreur.set(e?.error?.message ?? 'Operation impossible.');
      }
    });
  }

  private f(n: number): string {
    return Math.round(n).toLocaleString('fr-FR');
  }
}
