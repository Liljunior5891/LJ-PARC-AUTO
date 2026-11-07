import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/auth/auth.service';
import { Billettage } from '../../../core/models/pdv';
import { Coffre, CompteEntreprise, Degagement, LIBELLES_STATUT_REMISE, Remise } from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { DepotBanque, RecompteBillettage, VerificationDepot } from '../../../shared/tresorerie-pdv';

type Onglet = 'remises' | 'coffre' | 'degagements' | 'compte' | 'historique';

/**
 * Circuit des especes (option des reglages) : remises des caissiers a recompter,
 * coffre du gerant et son degagement, degagements a valider et compte de
 * l'entreprise pour l'administrateur, et l'historique.
 */
@Component({
  selector: 'app-tresorerie-pdv',
  imports: [DatePipe, DecimalPipe, DepotBanque, RecompteBillettage, VerificationDepot],
  templateUrl: './tresorerie.html',
  styleUrl: './tresorerie.scss'
})
export class TresoreriePdv implements OnInit {
  private readonly auth = inject(AuthService);
  readonly options = inject(OptionsPdvService);
  readonly estAdmin = this.auth.aRole('ADMIN');
  /** Un gerant qui n'est pas administrateur a un coffre a degager. */
  readonly aUnCoffre = this.auth.aRole('GERANT') && !this.estAdmin;
  readonly libelles = LIBELLES_STATUT_REMISE;

  readonly onglet = signal<Onglet>('remises');
  readonly onglets = computed(() => [
    { id: 'remises' as Onglet, libelle: 'Remises a valider', icone: '📥', compte: this.remises().length },
    ...(this.aUnCoffre ? [{ id: 'coffre' as Onglet, libelle: 'Mon coffre', icone: '🔐', compte: 0 }] : []),
    ...(this.estAdmin ? [
      { id: 'degagements' as Onglet, libelle: 'Depots a verifier', icone: '📤', compte: this.degagements().length },
      { id: 'compte' as Onglet, libelle: "Banque (compte de l'entreprise)", icone: '🏦', compte: 0 }
    ] : []),
    { id: 'historique' as Onglet, libelle: 'Historique', icone: '🕘', compte: 0 }
  ]);

  readonly remises = signal<Remise[]>([]);
  readonly degagements = signal<Degagement[]>([]);
  readonly coffre = signal<Coffre | null>(null);
  readonly compte = signal<CompteEntreprise | null>(null);
  readonly historiqueRemises = signal<Remise[]>([]);
  readonly historiqueDegagements = signal<Degagement[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly remiseOuverte = signal<Remise | null>(null);
  readonly degagementOuvert = signal<Degagement | null>(null);
  readonly envoiDegagement = signal(false);

  readonly tolerance = computed(() => this.options.reglages()?.ecartTolere ?? 0);

  ngOnInit(): void {
    this.chargerRemises();
    if (this.estAdmin) {
      this.chargerDegagements();
    }
  }

  changerOnglet(o: Onglet): void {
    this.onglet.set(o);
    this.succes.set(null);
    this.erreur.set(null);
    if (o === 'remises') {
      this.chargerRemises();
    } else if (o === 'coffre') {
      this.chargerCoffre();
    } else if (o === 'degagements') {
      this.chargerDegagements();
    } else if (o === 'compte') {
      this.options.compteEntreprise().subscribe({ next: (c) => this.compte.set(c), error: (e) => this.echec(e) });
    } else {
      this.options.historiqueRemises().subscribe({ next: (p) => this.historiqueRemises.set(p.content) });
      this.options.historiqueDegagements().subscribe({ next: (p) => this.historiqueDegagements.set(p.content) });
    }
  }

  chargerRemises(): void {
    this.options.remisesEnAttente().subscribe({ next: (r) => this.remises.set(r), error: (e) => this.echec(e) });
    this.options.rafraichirEnAttente();
  }

  chargerDegagements(): void {
    this.options.degagementsEnAttente().subscribe({ next: (d) => this.degagements.set(d), error: (e) => this.echec(e) });
  }

  chargerCoffre(): void {
    this.options.coffre().subscribe({ next: (c) => this.coffre.set(c), error: (e) => this.echec(e) });
  }

  destinationRemise(): string {
    return this.estAdmin ? "le compte de l'entreprise" : 'votre coffre';
  }

  validerRemiseVers(r: Remise) {
    return (b: Billettage, commentaire?: string) => this.options.validerRemise(r.id, b, commentaire);
  }

  refuserRemiseVers(r: Remise) {
    return (motif: string) => this.options.refuserRemise(r.id, motif);
  }

  remiseTraitee(r: Remise, resultat: 'valide' | 'refuse'): void {
    this.remiseOuverte.set(null);
    this.succes.set(resultat === 'valide'
      ? `Remise ${r.numero} validee : l'argent est passe dans ${this.destinationRemise()}.`
      : `Remise ${r.numero} refusee : ${r.caissierNom} doit recompter et renvoyer.`);
    this.chargerRemises();
  }

  degagementTraite(d: Degagement, resultat: 'valide' | 'refuse'): void {
    this.degagementOuvert.set(null);
    this.succes.set(resultat === 'valide'
      ? `Depot ${d.numero} valide : l'argent est arrive sur le compte de l'entreprise.`
      : `Depot ${d.numero} refuse.`);
    this.chargerDegagements();
    this.options.rafraichirEnAttente();
  }

  degagementEnvoye(): void {
    this.envoiDegagement.set(false);
    this.succes.set("Depot envoye avec son bordereau : il attend la verification de l'administrateur.");
    this.chargerCoffre();
  }

  voirPiece(d: Degagement): void {
    this.options.pieceDegagement(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.erreur.set('Piece introuvable.')
    });
  }

  private echec(e: unknown): void {
    this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Chargement impossible.');
  }
}
