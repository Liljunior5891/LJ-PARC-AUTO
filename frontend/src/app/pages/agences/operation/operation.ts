import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import {
  ApercuTransaction,
  SessionCaisseAgence,
  ClientAgence,
  ServiceFinancier,
  Transaction,
  TypeOperation
} from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';

const PIECES = ['CNI', 'Passeport', 'Permis de conduire', 'Carte consulaire', 'Carte d\'electeur', 'Autre'];

/**
 * Saisie d'une transaction sur un seul ecran : service, operation, reseau,
 * montant, puis client si besoin. Le resume a droite montre avant validation
 * la commission et l'effet sur chaque compte, bloque un solde insuffisant et
 * demande une confirmation en cas de doublon.
 */
@Component({
  selector: 'app-operation-agence',
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink],
  templateUrl: './operation.html',
  styleUrl: './operation.scss'
})
export class OperationAgence implements OnInit, OnDestroy {
  readonly service = inject(AgencesService);
  readonly pieces = PIECES;
  /** Caissier sans profil de supervision : il ne saisit que sur sa caisse ouverte. */
  readonly caissierSeul = !inject(AuthService).aRole('ADMIN', 'SUPERVISEUR_AGENCE');
  readonly maCaisse = signal<SessionCaisseAgence | null>(null);
  readonly caisseLue = signal(false);

  readonly catalogue = signal<ServiceFinancier[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly validee = signal<Transaction | null>(null);
  readonly recentes = signal<Transaction[]>([]);
  readonly apercu = signal<ApercuTransaction | null>(null);
  readonly erreurApercu = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly suggestions = signal<ClientAgence[]>([]);
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));

  /** Services proposes : actifs, avec au moins une operation de guichet active. */
  readonly parCategorie = computed(() => {
    const groupes = new Map<string, ServiceFinancier[]>();
    for (const s of this.catalogue()) {
      if (s.actif && this.typesDe(s).length) {
        groupes.set(s.categorie, [...(groupes.get(s.categorie) ?? []), s]);
      }
    }
    return [...groupes.entries()].map(([categorie, services]) => ({ categorie, services }));
  });

  agenceId: number | null = null;
  serviceChoisi = signal<ServiceFinancier | null>(null);
  typeChoisi = signal<TypeOperation | null>(null);
  reseauId: number | null = null;
  montant: number | null = null;
  commission: number | null = null;
  motifCommission = '';
  clientTelephone = '';
  clientNom = '';
  clientTypePiece = '';
  clientNumeroPiece = '';
  carte = '';
  referenceOperateur = '';
  observations = '';
  confirmerDoublon = false;
  detailsOuverts = false;

  private minuterie?: ReturnType<typeof setTimeout>;
  private minuterieClient?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        const actives = m.agences.filter((a) => a.actif);
        if (actives.length >= 1) {
          this.agenceId = actives[0].id;
        }
        this.chargerRecentes();
        this.chargerCaisse();
      }
    });
    this.service.catalogue().subscribe({
      next: (c) => this.catalogue.set(c),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des services impossible.'))
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.minuterie);
    clearTimeout(this.minuterieClient);
  }

  typesDe(s: ServiceFinancier): TypeOperation[] {
    return s.types.filter((t) => t.actif && t.nature !== 'VENTE_CARTE');
  }

  reseauxActifs(): { id: number; nom: string }[] {
    return (this.serviceChoisi()?.reseaux ?? []).filter((r) => r.actif);
  }

  /** Transfert d'argent : la piece du client est demandee. */
  estTransfert(): boolean {
    const s = this.serviceChoisi();
    const t = this.typeChoisi();
    return !!s && (s.reseaux.length > 0 || /transfert/i.test(s.categorie) || ['ENVOI', 'PAIEMENT'].includes(t?.code ?? ''));
  }

  estRecharge(): boolean {
    return this.typeChoisi()?.compteVirtuel === 'RECHARGE';
  }

  choisirService(s: ServiceFinancier): void {
    this.serviceChoisi.set(s);
    const types = this.typesDe(s);
    this.typeChoisi.set(types.length === 1 ? types[0] : (types.find((t) => t.id === this.typeChoisi()?.id) ?? types[0] ?? null));
    const reseaux = this.reseauxActifs();
    this.reseauId = reseaux.length === 1 ? reseaux[0].id : null;
    this.commission = null;
    this.recalculer();
  }

  choisirType(t: TypeOperation): void {
    this.typeChoisi.set(t);
    this.commission = null;
    this.recalculer();
  }

  choisirReseau(id: number): void {
    this.reseauId = id;
    this.commission = null;
    this.recalculer();
  }

  changerMontant(): void {
    this.commission = null;
    this.recalculer();
  }

  /** Apercu demande au serveur peu apres la derniere frappe. */
  recalculer(): void {
    clearTimeout(this.minuterie);
    this.validee.set(null);
    this.minuterie = setTimeout(() => this.demanderApercu(), 300);
  }

  private demanderApercu(): void {
    const saisie = this.saisie();
    if (!saisie) {
      this.apercu.set(null);
      this.erreurApercu.set(null);
      return;
    }
    this.service.apercuTransaction(saisie).subscribe({
      next: (a) => {
        this.apercu.set(a);
        this.erreurApercu.set(null);
        if (!a.doublon) {
          this.confirmerDoublon = false;
        }
      },
      error: (e) => {
        this.apercu.set(null);
        this.erreurApercu.set(this.message(e, 'Calcul impossible.'));
      }
    });
  }

  /** Suggestions de fiches clients a partir du telephone ou du nom. */
  chercherClient(texte: string): void {
    clearTimeout(this.minuterieClient);
    if (texte.trim().length < 3) {
      this.suggestions.set([]);
      return;
    }
    this.minuterieClient = setTimeout(() => this.service.clientsAgence(texte.trim()).subscribe({
      next: (c) => this.suggestions.set(c.filter((x) => x.telephone !== this.clientTelephone.trim() || !this.clientNom))
    }), 250);
  }

  choisirClient(c: ClientAgence): void {
    this.clientTelephone = c.telephone;
    this.clientNom = c.nom ?? '';
    this.clientTypePiece = c.typePiece ?? '';
    this.clientNumeroPiece = c.numeroPiece ?? '';
    this.suggestions.set([]);
    this.recalculer();
  }

  commissionModifiee(): boolean {
    const a = this.apercu();
    return !!a && this.commission != null && this.commission !== a.commissionProposee;
  }

  peutValider(): boolean {
    const a = this.apercu();
    return !!a && !this.envoi() && !a.blocages.length && (!a.doublon || this.confirmerDoublon)
      && (!this.commissionModifiee() || !!this.motifCommission.trim());
  }

  valider(): void {
    const saisie = this.saisie();
    if (!saisie || !this.peutValider()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.enregistrerTransaction(saisie).subscribe({
      next: (t) => {
        this.envoi.set(false);
        this.validee.set(t);
        this.reinitialiser();
        this.chargerRecentes();
        this.chargerCaisse();
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(this.message(e, 'Transaction refusee.'));
        this.demanderApercu();
      }
    });
  }

  /** Garde le service et l'operation pour enchainer, vide le reste. */
  private reinitialiser(): void {
    this.montant = null;
    this.commission = null;
    this.motifCommission = '';
    this.clientTelephone = '';
    this.clientNom = '';
    this.clientTypePiece = '';
    this.clientNumeroPiece = '';
    this.carte = '';
    this.referenceOperateur = '';
    this.observations = '';
    this.confirmerDoublon = false;
    this.apercu.set(null);
    this.suggestions.set([]);
  }

  private chargerRecentes(): void {
    if (!this.agenceId) {
      return;
    }
    const jour = new Date().toISOString().slice(0, 10);
    this.service.transactions({ agenceId: this.agenceId, du: jour }, 0).subscribe({
      next: (p) => this.recentes.set(p.content.slice(0, 8))
    });
  }

  changerAgence(): void {
    this.recalculer();
    this.chargerRecentes();
    this.chargerCaisse();
  }

  /** Caisse ouverte de l'utilisateur dans l'agence choisie. */
  chargerCaisse(): void {
    if (!this.agenceId) {
      return;
    }
    this.service.maSession(this.agenceId).subscribe({
      next: (s) => {
        this.maCaisse.set(s);
        this.caisseLue.set(true);
      },
      error: () => this.caisseLue.set(true)
    });
  }

  private saisie() {
    const s = this.serviceChoisi();
    const t = this.typeChoisi();
    if (!this.agenceId || !s || !t || !this.montant || this.montant <= 0) {
      return null;
    }
    if (this.reseauxActifs().length && !this.reseauId) {
      return null;
    }
    return {
      agenceId: this.agenceId,
      serviceId: s.id,
      typeOperationId: t.id,
      reseauId: this.reseauId,
      montant: Math.round(this.montant),
      commission: this.commission != null ? Math.max(0, Math.round(this.commission)) : null,
      motifCommission: this.motifCommission.trim() || undefined,
      referenceOperateur: this.referenceOperateur.trim() || undefined,
      clientTelephone: this.clientTelephone.trim() || undefined,
      clientNom: this.clientNom.trim() || undefined,
      clientTypePiece: this.clientTypePiece || undefined,
      clientNumeroPiece: this.clientNumeroPiece.trim() || undefined,
      carte: this.estRecharge() ? this.carte.trim() || undefined : undefined,
      observations: this.observations.trim() || undefined,
      confirmerDoublon: this.confirmerDoublon
    };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
