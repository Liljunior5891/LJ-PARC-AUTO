import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CommandeFournisseurDetail,
  LIBELLES_STATUT_COMMANDE_FOURNISSEUR,
  LigneCommandeFournisseur
} from '../../../core/models/pdv';
import { AuthService } from '../../../core/auth/auth.service';
import { LieuxService } from '../../../core/services/lieux.service';
import { PdvService } from '../../../core/services/pdv.service';
import { ChampQuantite } from '../../../shared/champ-quantite';
import { Modale } from '../../../shared/modale';
import { FormulaireCommande } from './formulaire-commande';
import { CompteFournisseurPdv } from '../../../shared/compte-fournisseur-pdv';
import { AchatsService } from '../../../core/services/achats.service';

/**
 * Une commande fournisseur et ses produits. Tant qu'elle est en cours, elle
 * se modifie, s'annule ou se receptionne : les quantites effectivement
 * livrees entrent alors en stock.
 */
@Component({
  selector: 'app-commande-fournisseur-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, Modale, FormulaireCommande, ChampQuantite, CompteFournisseurPdv],
  templateUrl: './commande-fournisseur.html',
  styleUrl: './commande-fournisseur.scss'
})
export class CommandeFournisseurPdv implements OnInit {
  private readonly service = inject(PdvService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly lieuxService = inject(LieuxService);
  /** Avec des entrepots, la marchandise entre dans le lieu choisi. */
  readonly lieux = computed(() => this.lieuxService.etat()?.lieux ?? []);
  lieuId: number | null = null;

  readonly libelles = LIBELLES_STATUT_COMMANDE_FOURNISSEUR;
  readonly commande = signal<CommandeFournisseurDetail | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly edition = signal(false);
  /** Compte du fournisseur ouvert pour payer cette commande. */
  readonly paiementOuvert = signal(false);
  private readonly achats = inject(AchatsService);
  /** Commande tout juste creee : le bon de commande est propose a l'impression. */
  readonly nouvelle = signal(false);

  // Reception : quantite livree et cout par ligne
  readonly reception = signal(false);
  readonly recues = signal<Record<number, { quantite: number | null; cout: number | null }>>({});

  readonly enCours = computed(() => this.commande()?.resume.statut === 'EN_COURS');
  readonly totalRecu = computed(() =>
    (this.commande()?.lignes ?? []).reduce((s, l) => s + (l.quantiteRecue ?? 0) * (l.coutUnitaire ?? 0), 0)
  );
  readonly totalReception = computed(() =>
    (this.commande()?.lignes ?? []).reduce((s, l) => {
      const r = this.recues()[l.id];
      return s + (r?.quantite ?? 0) * (r?.cout ?? 0);
    }, 0)
  );

  ngOnInit(): void {
    if (this.auth.estMateriaux()) {
      this.lieuxService.chargerEtat();
    }
    this.nouvelle.set(this.route.snapshot.queryParamMap.get('nouvelle') === '1');
    this.charger();
  }

  ecart(l: LigneCommandeFournisseur): number | null {
    return l.quantiteRecue == null ? null : l.quantiteRecue - l.quantite;
  }

  ouvrirReception(): void {
    const recues: Record<number, { quantite: number | null; cout: number | null }> = {};
    for (const l of this.commande()?.lignes ?? []) {
      recues[l.id] = { quantite: l.quantite, cout: l.coutUnitaire ?? null };
    }
    this.recues.set(recues);
    this.lieuId = this.lieux().find((l) => l.type === 'ENTREPOT')?.id ?? this.lieux()[0]?.id ?? null;
    this.erreur.set(null);
    this.reception.set(true);
  }

  modifierRecue(ligne: LigneCommandeFournisseur, champ: 'quantite' | 'cout', valeur: number | null): void {
    this.recues.update((r) => ({ ...r, [ligne.id]: { ...r[ligne.id], [champ]: valeur } }));
  }

  receptionner(): void {
    const c = this.commande();
    if (!c) {
      return;
    }
    const lignes = c.lignes.map((l) => ({
      ligneId: l.id,
      quantiteRecue: Math.max(0, this.recues()[l.id]?.quantite ?? 0),
      coutUnitaire: this.recues()[l.id]?.cout ?? null
    }));
    this.service.receptionnerCommandeFournisseur(c.resume.id, lignes, this.lieuId).subscribe({
      next: (detail) => {
        this.commande.set(detail);
        this.reception.set(false);
        this.succes.set('Commande receptionnee : les quantites livrees sont entrees en stock et la valeur recue est due au fournisseur.');
      },
      error: (e) => this.erreur.set(this.message(e, 'Reception impossible.'))
    });
  }

  annuler(): void {
    const c = this.commande();
    if (!c) {
      return;
    }
    this.service.annulerCommandeFournisseur(c.resume.id).subscribe({
      next: (detail) => {
        this.commande.set(detail);
        this.succes.set('Commande annulee.');
      },
      error: (e) => this.erreur.set(this.message(e, 'Annulation impossible.'))
    });
  }

  apresModification(detail: CommandeFournisseurDetail): void {
    this.commande.set(detail);
    this.edition.set(false);
    this.succes.set('Commande modifiee.');
  }

  /** Le paiement a change : la situation de la commande se relit. */
  imprimerBon(): void {
    const c = this.commande();
    if (!c) {
      return;
    }
    this.achats.bonCommande(c.resume.id).subscribe({
      next: (pdf) => this.achats.ouvrirPdf(pdf),
      error: () => this.erreur.set('Bon de commande indisponible.')
    });
  }

  imprimerLivraison(): void {
    const c = this.commande();
    if (!c) {
      return;
    }
    this.achats.bonLivraison(c.resume.id).subscribe({
      next: (pdf) => this.achats.ouvrirPdf(pdf),
      error: () => this.erreur.set('Bon de livraison indisponible.')
    });
  }

  readonly Math = Math;

  /** Jours jusqu'a l'echeance (negatif : en retard), en dates locales. */
  joursAvantEcheance(echeance?: string): number | null {
    if (!echeance) {
      return null;
    }
    const [a, m, j] = echeance.split('-').map(Number);
    const aujourdhui = new Date();
    const debut = Date.UTC(aujourdhui.getFullYear(), aujourdhui.getMonth(), aujourdhui.getDate());
    return Math.round((Date.UTC(a, m - 1, j) - debut) / 86_400_000);
  }

  paiementModifie(): void {
    this.charger();
  }

  private charger(): void {
    this.service.commandeFournisseur(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (c) => this.commande.set(c),
      error: (e) => this.erreur.set(this.message(e, 'Commande introuvable.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
