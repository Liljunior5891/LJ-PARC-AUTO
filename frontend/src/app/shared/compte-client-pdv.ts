import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { CompteClient, RecuReglement } from '../core/models/materiaux';
import { MODES_PAIEMENT_PDV, ModePaiementPdv } from '../core/models/pdv';
import { MateriauxService } from '../core/services/materiaux.service';
import { SessionTravailService } from '../core/services/session-travail.service';
import { SocieteService } from '../core/services/societe.service';
import { Modale } from './modale';
import { OperationService } from '../core/operation/operation.service';
import { imprimerRecuReglement } from './ticket-pdv';

/**
 * Compte d'un client de la quincaillerie : encours, ventes dues avec leur
 * echeance, historique des versements, et encaissement d'un nouveau versement
 * sur la session de caisse du poste. Ouvert depuis la caisse comme depuis la
 * liste des clients.
 */
@Component({
  selector: 'app-compte-client-pdv',
  imports: [FormsModule, DecimalPipe, DatePipe, Modale, RouterLink],
  templateUrl: './compte-client-pdv.html',
  styleUrl: './compte-client-pdv.scss'
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
  }

  ouvrirVersement(venteId: number | null = null, montant?: number): void {
    const c = this.compte();
    if (!c) {
      return;
    }
    this.venteId = venteId;
    this.montant.set(montant ?? c.client.encours);
    this.recu.set(montant ?? c.client.encours);
    this.mode.set('ESPECES');
    this.reference = '';
    this.succes.set(null);
    this.erreur.set(null);
    this.versement.set(true);
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
      message: 'Il diminue ce que doit le client et entre dans la caisse.',
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
