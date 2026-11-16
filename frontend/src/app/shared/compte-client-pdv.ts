import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { CompteClient, CompteDepot, OperationDepot, RecuReglement } from '../core/models/materiaux';
import { MODES_PAIEMENT_PDV, ModePaiementPdv } from '../core/models/pdv';
import { MateriauxService } from '../core/services/materiaux.service';
import { SessionTravailService } from '../core/services/session-travail.service';
import { SocieteService } from '../core/services/societe.service';
import { Modale } from './modale';
import { IconeMoyen } from './icone-moyen';
import { OperationService } from '../core/operation/operation.service';
import { imprimerRecuDepot, imprimerRecuReglement } from './ticket-pdv';

/**
 * Compte d'un client de la quincaillerie : encours, ventes dues avec leur
 * echeance, historique des versements, et encaissement d'un nouveau versement
 * sur la session de caisse du poste. Ouvert depuis la caisse comme depuis la
 * liste des clients.
 */
type OngletCompte = 'dues' | 'livrer' | 'versements' | 'depots';

@Component({
  selector: 'app-compte-client-pdv',
  imports: [FormsModule, DecimalPipe, DatePipe, Modale, RouterLink, IconeMoyen],
  templateUrl: './compte-client-pdv.html',
  styleUrls: ['./compte-tiers.scss', './compte-client-pdv.scss']
})
export class CompteClientPdv implements OnInit {
  private readonly service = inject(MateriauxService);
  private readonly societes = inject(SocieteService);
  private readonly sessions = inject(SessionTravailService);
  private readonly operation = inject(OperationService);
  readonly estGestionnaire = inject(AuthService).aRole('ADMIN', 'GERANT');

  readonly clientId = input.required<number>();
  /** Ouvre directement le formulaire de versement. */
  readonly versementDirect = input(false);
  readonly ferme = output<void>();
  /** Le solde a change (versement, annulation) : la liste appelante se met a jour. */
  readonly modifie = output<void>();

  readonly modes = MODES_PAIEMENT_PDV;
  readonly compte = signal<CompteClient | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly enCours = signal(false);
  readonly session = this.sessions.courante;
  readonly ouvertes = this.sessions.ouvertes;

  // Versement
  readonly versement = signal(false);
  readonly montant = signal<number | null>(null);
  readonly mode = signal<ModePaiementPdv>('ESPECES');
  readonly recu = signal<number | null>(null);
  venteId: number | null = null;
  reference = '';
  readonly monnaie = computed(() => Math.max(0, (this.recu() ?? 0) - (this.montant() ?? 0)));

  // Onglets des listes
  readonly onglets: { id: OngletCompte; libelle: string; icone: string }[] = [
    { id: 'dues', libelle: 'A regler', icone: '⏳' },
    { id: 'livrer', libelle: 'Reste a livrer', icone: '📦' },
    { id: 'versements', libelle: 'Versements', icone: '💵' },
    { id: 'depots', libelle: 'Argent depose', icone: '🏦' }
  ];

  // Argent depose par le client (sans achat), et son remboursement.
  readonly depots = signal<CompteDepot | null>(null);
  /** Formulaire ouvert : depot d'argent ou remboursement du solde. */
  readonly formulaireDepot = signal<'depot' | 'remboursement' | null>(null);
  readonly montantDepot = signal<number | null>(null);
  readonly recuDepot = signal<number | null>(null);
  readonly modeDepot = signal<ModePaiementPdv>('ESPECES');
  readonly sourceRemboursement = signal<'CAISSE' | 'BANQUE'>('CAISSE');
  referenceDepot = '';
  motifDepot = '';
  readonly monnaieDepot = computed(() => Math.max(0, (this.recuDepot() ?? 0) - (this.montantDepot() ?? 0)));
  /** Remboursement refuse en cours de saisie du motif. */
  readonly aRefuser = signal<number | null>(null);
  motifRefus = '';
  readonly aAnnulerDepot = signal<string | null>(null);
  readonly onglet = signal<OngletCompte>('dues');

  readonly initiales = computed(() => {
    const mots = (this.compte()?.client.nom ?? '').trim().split(/\s+/).filter(Boolean);
    return ((mots[0]?.[0] ?? '') + (mots[1]?.[0] ?? '')).toUpperCase() || '?';
  });

  /** Part du plafond de credit deja utilisee ; nulle quand le client est sans limite. */
  readonly partPlafond = computed(() => {
    const client = this.compte()?.client;
    const plafond = client?.plafondRetenu;
    if (!client || plafond === null || plafond === undefined || plafond <= 0) {
      return null;
    }
    return Math.min(100, Math.round((client.encours / plafond) * 100));
  });

  // Annulation d'un versement
  readonly aAnnuler = signal<string | null>(null);
  motif = '';

  /** Versements groupes par numero de recu (un versement peut solder plusieurs ventes). */
  readonly versements = computed(() => {
    const groupes = new Map<string, { numero: string; date: string; montant: number; mode: string; statut: string;
                                     caissier: string; ventes: string[] }>();
    for (const r of this.compte()?.reglements ?? []) {
      const g = groupes.get(r.numero) ?? { numero: r.numero, date: r.date, montant: 0, mode: r.modePaiementLibelle,
                                          statut: r.statut, caissier: r.caissierNom, ventes: [] };
      g.montant += r.montant;
      g.ventes.push(r.venteNumero);
      groupes.set(r.numero, g);
    }
    return [...groupes.values()];
  });

  nombre(id: OngletCompte): number {
    const c = this.compte();
    if (!c) {
      return 0;
    }
    if (id === 'depots') {
      return this.depots()?.operations.length ?? 0;
    }
    return id === 'dues' ? c.dues.length : id === 'livrer' ? c.aLivrer.length : this.versements().length;
  }

  /** Vente sur laquelle porte le versement en cours, s'il en vise une. */
  venteChoisie() {
    return this.compte()?.dues.find((v) => v.id === this.venteId);
  }

  ngOnInit(): void {
    this.charger();
    if (this.sessions.ouvertes() === undefined) {
      this.sessions.rafraichir();
    }
  }

  charger(): void {
    this.service.compte(this.clientId()).subscribe({
      next: (c) => {
        this.compte.set(c);
        if (this.versementDirect() && !this.versement() && c.client.encours > 0) {
          this.ouvrirVersement();
        }
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Compte illisible.')
    });
    this.service.depots(this.clientId()).subscribe({
      next: (d) => this.depots.set(d),
      error: () => this.depots.set(null)
    });
  }

  // ------------------------------------------------------- argent depose

  ouvrirDepot(type: 'depot' | 'remboursement'): void {
    this.versement.set(false);
    this.formulaireDepot.set(type);
    this.montantDepot.set(type === 'remboursement' ? this.depots()?.solde ?? null : null);
    this.recuDepot.set(null);
    this.modeDepot.set('ESPECES');
    this.sourceRemboursement.set(this.session() ? 'CAISSE' : 'BANQUE');
    this.referenceDepot = '';
    this.motifDepot = '';
    this.succes.set(null);
    this.erreur.set(null);
  }

  async enregistrerDepot(): Promise<void> {
    const montant = this.montantDepot() ?? 0;
    const type = this.formulaireDepot();
    if (this.enCours() || montant <= 0 || !type) {
      return;
    }
    const parCaisse = type === 'depot' || this.sourceRemboursement() === 'CAISSE';
    if (parCaisse && !this.session()) {
      this.erreur.set(this.sessions.choixRequis()
        ? 'Choisissez la caisse sur laquelle vous encaissez.'
        : 'Aucune caisse ouverte : un gerant doit ouvrir une session.');
      return;
    }
    const accepte = await this.operation.demander(type === 'depot'
      ? { titre: `Enregistrer le depot de ${montant.toLocaleString('fr-FR')} F ?`,
          message: 'L\'argent entre dans la caisse et reste a la disposition du client pour ses prochains achats.',
          libelleAction: 'Enregistrer' }
      : { titre: `Rembourser ${montant.toLocaleString('fr-FR')} F au client ?`,
          message: this.estGestionnaire
            ? (this.sourceRemboursement() === 'CAISSE' ? 'Les especes sortent aussitot de la caisse.' : 'Le remboursement sort du compte de l\'entreprise.')
            : 'La demande part au gerant, qui la validera ; le montant est bloque en attendant.',
          libelleAction: this.estGestionnaire ? 'Rembourser' : 'Demander' });
    if (!accepte || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    const appel = type === 'depot'
      ? this.service.deposer(this.clientId(), {
          montant, modePaiement: this.modeDepot(), montantRecu: this.modeDepot() === 'ESPECES' ? this.recuDepot() : null,
          referencePaiement: this.referenceDepot.trim() || null, sessionId: this.session()?.id, motif: this.motifDepot.trim() || null })
      : this.service.rembourserDepot(this.clientId(), {
          montant, source: this.sourceRemboursement(), sessionId: parCaisse ? this.session()?.id : null,
          motif: this.motifDepot.trim() || null });
    appel.subscribe({
      next: (op) => {
        this.enCours.set(false);
        this.formulaireDepot.set(null);
        if (op.type === 'DEPOT') {
          this.succes.set(`Depot ${op.numero} enregistre : ${op.montant.toLocaleString('fr-FR')} F.`
            + (op.monnaieRendue ? ` Monnaie a rendre : ${op.monnaieRendue.toLocaleString('fr-FR')} F.` : ''));
        } else {
          this.succes.set(op.statut === 'VALIDE'
            ? `Remboursement ${op.numero} de ${op.montant.toLocaleString('fr-FR')} F effectue.`
            : `Demande ${op.numero} envoyee au gerant : ${op.montant.toLocaleString('fr-FR')} F bloques en attendant.`);
        }
        if (op.statut === 'VALIDE') {
          this.imprimerDepot(op);
        }
        this.onglet.set('depots');
        this.sessions.rafraichir();
        this.charger();
        this.modifie.emit();
      },
      error: (e) => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Operation impossible.');
      }
    });
  }

  validerRemboursement(op: OperationDepot): void {
    this.service.validerRemboursementDepot(op.id).subscribe({
      next: (r) => {
        this.succes.set(`Remboursement ${r.numero} valide.`);
        this.imprimerDepot(r);
        this.sessions.rafraichir();
        this.charger();
        this.modifie.emit();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Validation impossible.')
    });
  }

  refuserRemboursement(op: OperationDepot): void {
    if (!this.motifRefus.trim()) {
      return;
    }
    this.service.refuserRemboursementDepot(op.id, this.motifRefus.trim()).subscribe({
      next: () => {
        this.aRefuser.set(null);
        this.motifRefus = '';
        this.succes.set(`Remboursement ${op.numero} refuse : le montant revient au solde du client.`);
        this.charger();
        this.modifie.emit();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Refus impossible.')
    });
  }

  annulerDepot(numero: string): void {
    this.service.annulerDepot(numero, this.motif.trim()).subscribe({
      next: () => {
        this.aAnnulerDepot.set(null);
        this.motif = '';
        this.succes.set(`Depot ${numero} annule.`);
        this.sessions.rafraichir();
        this.charger();
        this.modifie.emit();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  reimprimerDepot(numero: string): void {
    this.service.recuDepot(numero).subscribe({ next: (r) => this.imprimerDepot(r) });
  }

  private imprimerDepot(op: OperationDepot): void {
    this.societes.lire().subscribe({
      next: (s) => imprimerRecuDepot(op, s),
      error: () => imprimerRecuDepot(op, null)
    });
  }

  ouvrirVersement(venteId: number | null = null, montant?: number): void {
    const c = this.compte();
    if (!c) {
      return;
    }
    this.formulaireDepot.set(null);
    this.venteId = venteId;
    this.montant.set(montant ?? c.client.encours);
    this.recu.set(montant ?? c.client.encours);
    this.mode.set('ESPECES');
    this.reference = '';
    this.succes.set(null);
    this.erreur.set(null);
    this.versement.set(true);
  }

  /** Le versement est pris sur l'argent depose : au plus son solde, au plus ce qui est du. */
  payerParCompte(): void {
    const du = this.venteChoisie()?.resteDu ?? this.compte()?.client.encours ?? 0;
    this.mode.set('COMPTE_CLIENT');
    this.montant.set(Math.min(du, this.depots()?.solde ?? 0));
  }

  async encaisser(): Promise<void> {
    const montant = this.montant() ?? 0;
    if (this.enCours() || montant <= 0) {
      return;
    }
    if (!this.session()) {
      this.erreur.set(this.sessions.choixRequis()
        ? 'Choisissez la caisse sur laquelle vous encaissez.'
        : 'Aucune caisse ouverte : un gerant doit ouvrir une session avant d\'encaisser.');
      return;
    }
    const accepte = await this.operation.demander({
      titre: `Enregistrer le versement de ${montant.toLocaleString('fr-FR')} F ?`,
      message: this.mode() === 'COMPTE_CLIENT'
        ? "Il diminue ce que doit le client et est pris sur l'argent qu'il a depose."
        : 'Il diminue ce que doit le client et entre dans la caisse.',
      libelleAction: 'Enregistrer'
    });
    if (!accepte || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.service
      .regler(this.clientId(), {
        montant,
        modePaiement: this.mode(),
        montantRecu: this.mode() === 'ESPECES' ? this.recu() : null,
        referencePaiement: this.reference.trim() || null,
        venteId: this.venteId,
        sessionId: this.session()?.id
      })
      .subscribe({
        next: (recu) => {
          this.enCours.set(false);
          this.versement.set(false);
          this.succes.set(`Versement ${recu.numero} enregistre : ${recu.montant.toLocaleString('fr-FR')} F.`
            + (recu.monnaieRendue ? ` Monnaie a rendre : ${recu.monnaieRendue.toLocaleString('fr-FR')} F.` : ''));
          this.imprimer(recu);
          this.sessions.rafraichir();
          this.charger();
          this.modifie.emit();
        },
        error: (e) => {
          this.enCours.set(false);
          this.erreur.set(e?.error?.message ?? 'Versement impossible.');
        }
      });
  }

  reimprimer(numero: string): void {
    this.service.recu(numero).subscribe({ next: (r) => this.imprimer(r) });
  }

  annuler(numero: string): void {
    this.service.annulerReglement(numero, this.motif.trim()).subscribe({
      next: () => {
        this.aAnnuler.set(null);
        this.motif = '';
        this.succes.set(`Versement ${numero} annule : le solde du client est retabli.`);
        this.sessions.rafraichir();
        this.charger();
        this.modifie.emit();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  choisirSession(id: number | null): void {
    this.sessions.choisir(id);
  }

  private imprimer(recu: RecuReglement): void {
    this.societes.lire().subscribe({
      next: (s) => imprimerRecuReglement(recu, s),
      error: () => imprimerRecuReglement(recu, null)
    });
  }
}
