import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { LIBELLES_STATUT_DEPENSE } from '../../../core/models/achats';
import {
  BeneficeAssocie,
  CompteAssocie,
  DepotAssocie,
  FondsAssocie,
  LIBELLES_STATUT_DEPOT
} from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { Modale } from '../../../shared/modale';
import { PERIODES, Periode, bornesPeriode, datesDePeriode } from '../../../shared/periodes';

type Onglet = 'tableau' | 'compte' | 'fonds';

/**
 * Espace de l'associe, un ecran par menu (donnee de route « vue ») : le tableau
 * de bord de ses produits et de sa part, son sous-compte (parts mensuelles,
 * versements a valider, apports au fonds) et le fonds de reserve commun (solde,
 * ses apports, depenses en detail).
 */
@Component({
  selector: 'app-espace-associe-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, RouterLink],
  templateUrl: './espace-associe.html',
  styleUrl: './espace-associe.scss'
})
export class EspaceAssociePdv implements OnInit {
  private readonly options = inject(OptionsPdvService);
  private readonly route = inject(ActivatedRoute);
  private readonly monId = inject(AuthService).utilisateur()?.id;
  readonly libellesDepot = LIBELLES_STATUT_DEPOT;
  readonly libellesDepense = LIBELLES_STATUT_DEPENSE;

  readonly onglet = signal<Onglet>('tableau');
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  // ------------------------------------------------------- tableau de bord
  readonly portees = PERIODES;
  readonly portee = signal<Periode>('CE_MOIS');
  readonly dateDebut = signal(datesDePeriode('CE_MOIS').du);
  readonly dateFin = signal(datesDePeriode('CE_MOIS').au);
  readonly datesValides = computed(() => this.portee() !== 'PERSONNALISEE' || this.dateDebut() <= this.dateFin());
  readonly moi = signal<BeneficeAssocie | null>(null);
  readonly chargementStats = signal(false);
  readonly tauxMarge = computed(() => {
    const m = this.moi();
    return m && m.chiffreAffaires ? Math.round((m.marge / m.chiffreAffaires) * 1000) / 10 : 0;
  });

  // ---------------------------------------------------------- sous-compte
  readonly compte = signal<CompteAssocie | null>(null);
  readonly aValider = computed(() => (this.compte()?.depots ?? []).filter((d) => d.statut === 'EN_ATTENTE'));
  readonly historique = computed(() => (this.compte()?.depots ?? []).filter((d) => d.statut !== 'EN_ATTENTE'));
  /** Mois les plus recents d'abord. */
  readonly moisRecents = computed(() => [...(this.compte()?.mois ?? [])].reverse());
  readonly traitement = signal<number | null>(null);
  readonly refus = signal<{ depot: DepotAssocie; motif: string } | null>(null);
  readonly apport = signal<{ montant: number | null; note: string } | null>(null);

  // ---------------------------------------------------------------- fonds
  readonly fonds = signal<FondsAssocie | null>(null);

  ngOnInit(): void {
    this.onglet.set((this.route.snapshot.data['vue'] as Onglet | undefined) ?? 'tableau');
    // Le compte sert aux trois ecrans (reste a verser, solde, versements a valider) ; le fonds, au compte et au fonds.
    this.chargerCompte();
    if (this.onglet() === 'tableau') {
      this.chargerStats();
    } else {
      this.chargerFonds();
    }
  }

  chargerStats(): void {
    if (!this.datesValides()) {
      this.erreur.set('La date de debut doit preceder la date de fin.');
      return;
    }
    const { debut, fin } = bornesPeriode(this.portee(), this.dateDebut(), this.dateFin());
    this.chargementStats.set(true);
    this.options.benefices(debut, fin).subscribe({
      next: (b) => {
        // Un administrateur associe recoit la vue complete : on n'en garde que sa ligne.
        this.moi.set(b.associes.find((a) => a.associeId === this.monId) ?? null);
        this.chargementStats.set(false);
      },
      error: (e) => this.echec(e, 'Calcul de vos statistiques impossible.')
    });
  }

  changerPortee(portee: Periode): void {
    if (portee === 'PERSONNALISEE' && this.portee() !== 'PERSONNALISEE') {
      const { du, au } = datesDePeriode(this.portee());
      this.dateDebut.set(du);
      this.dateFin.set(au);
    }
    this.portee.set(portee);
    this.chargerStats();
  }

  changerDate(borne: 'debut' | 'fin', valeur: string): void {
    (borne === 'debut' ? this.dateDebut : this.dateFin).set(valeur);
    this.chargerStats();
  }

  chargerCompte(): void {
    this.options.monCompteAssocie().subscribe({
      next: (c) => this.compte.set(c),
      error: (e) => this.echec(e, 'Chargement de votre compte impossible.')
    });
  }

  chargerFonds(): void {
    this.options.fondsVuParAssocie().subscribe({
      next: (f) => this.fonds.set(f),
      error: (e) => this.echec(e, 'Chargement du fonds de reserve impossible.')
    });
  }

  valider(d: DepotAssocie): void {
    this.traitement.set(d.id);
    this.options.validerDepotAssocie(d.id).subscribe({
      next: () => {
        this.traitement.set(null);
        this.succes.set(`Versement ${d.numero} valide : ${d.montant.toLocaleString('fr-FR')} F credites sur votre sous-compte.`);
        this.chargerCompte();
      },
      error: (e) => {
        this.traitement.set(null);
        this.echec(e, 'Validation impossible.');
      }
    });
  }

  confirmerRefus(): void {
    const r = this.refus();
    if (!r || !r.motif.trim()) {
      return;
    }
    this.traitement.set(r.depot.id);
    this.options.refuserDepotAssocie(r.depot.id, r.motif.trim()).subscribe({
      next: () => {
        this.traitement.set(null);
        this.refus.set(null);
        this.succes.set(`Versement ${r.depot.numero} refuse : le gerant est prevenu et le corrigera.`);
        this.chargerCompte();
      },
      error: (e) => {
        this.traitement.set(null);
        this.echec(e, 'Refus impossible.');
      }
    });
  }

  apportPossible(): boolean {
    const a = this.apport();
    const montant = Number(a?.montant);
    return !!a && montant > 0 && montant <= (this.compte()?.solde ?? 0) && this.traitement() === null;
  }

  confirmerApport(): void {
    const a = this.apport();
    if (!a || !this.apportPossible()) {
      return;
    }
    this.traitement.set(-1);
    this.options.apporterAuFonds(Number(a.montant), a.note.trim() || null).subscribe({
      next: (r) => {
        this.traitement.set(null);
        this.apport.set(null);
        this.succes.set(`Apport ${r.numero} de ${r.montant.toLocaleString('fr-FR')} F verse au fonds de reserve.`);
        this.chargerCompte();
        this.chargerFonds();
      },
      error: (e) => {
        this.traitement.set(null);
        this.echec(e, 'Apport impossible.');
      }
    });
  }

  voirPiece(d: DepotAssocie): void {
    this.options.pieceDepotAssocie(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.erreur.set('Bordereau introuvable.')
    });
  }

  private echec(e: unknown, defaut: string): void {
    this.chargementStats.set(false);
    this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? defaut);
  }
}
