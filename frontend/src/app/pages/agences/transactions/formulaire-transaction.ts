import { DecimalPipe, DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import {
  ApercuTransaction,
  ClientAgence,
  ServiceFinancier,
  SessionCaisseAgence,
  Transaction,
  TypeOperation
} from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';
import { LogoService, attributsOption } from '../../../shared/logo-service';
import { AgencesHorsLigneService, erreurReseau, nouvelleCleOperation } from '../../../core/services/agences-hors-ligne.service';
import { Modale } from '../../../shared/modale';

const PIECES = ['CNI', 'Passeport', 'Permis de conduire', 'Carte consulaire', 'Carte d\'electeur', 'Autre'];

/**
 * Saisie d'une transaction dans une fenetre : agence, service (liste Select2),
 * operation, reseau, montant et client. Le resume, a droite, montre avant
 * validation la commission et l'effet sur chaque compte, bloque un solde
 * insuffisant et demande une confirmation en cas de doublon.
 * <p>
 * Les cartes prepayees (vente et recharge) ont leur propre ecran : leurs
 * services ne sont pas proposes ici.
 */
@Component({
  selector: 'app-formulaire-transaction',
  imports: [DatePipe, DecimalPipe, FormsModule, LogoService, Modale],
  templateUrl: './formulaire-transaction.html',
  styleUrl: './formulaire-transaction.scss'
})
export class FormulaireTransaction implements OnInit, OnDestroy {
  readonly service = inject(AgencesService);
  readonly horsLigne = inject(AgencesHorsLigneService);
  /** Le serveur ne repond pas : apercu calcule sur l'appareil, transaction mise en file. */
  readonly sansReseau = signal(false);
  /** Reference provisoire de la derniere transaction mise en file. */
  readonly derniereHorsLigne = signal<string | null>(null);
  readonly pieces = PIECES;
  /** Logo ou pastille d'un service dans une liste Select2. */
  readonly opt = attributsOption;
  /** Caissier sans profil de supervision : il ne saisit que sur sa caisse ouverte. */
  readonly caissierSeul = !inject(AuthService).aRole('ADMIN', 'SUPERVISEUR_AGENCE');

  /** Une transaction vient d'etre validee (la fenetre reste ouverte si « Valider et continuer »). */
  readonly enregistree = output<Transaction>();
  readonly ferme = output<void>();

  readonly catalogue = signal<ServiceFinancier[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly derniere = signal<Transaction | null>(null);
  readonly apercu = signal<ApercuTransaction | null>(null);
  readonly erreurApercu = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly suggestions = signal<ClientAgence[]>([]);
  readonly maCaisse = signal<SessionCaisseAgence | null>(null);
  readonly caisseLue = signal(false);
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));

  /** Services proposes, par categorie : actifs, avec une operation de guichet, hors cartes prepayees. */
  readonly parCategorie = computed(() => {
    const groupes = new Map<string, ServiceFinancier[]>();
    for (const s of this.catalogue()) {
      if (s.actif && !estServiceCartes(s) && this.typesDe(s).length) {
        groupes.set(s.categorie, [...(groupes.get(s.categorie) ?? []), s]);
      }
    }
    return [...groupes.entries()].map(([categorie, services]) => ({ categorie, services }));
  });

  /** Tous les services proposes, dans l'ordre du catalogue, pour les tuiles. */
  readonly servicesProposes = computed(() => this.parCategorie().flatMap((g) => g.services));
  /** Filtre des tuiles quand il y a beaucoup de services. */
  readonly recherche = signal('');
  readonly servicesFiltres = computed(() => {
    const r = this.recherche().trim().toLowerCase();
    return r ? this.servicesProposes().filter((s) => (s.nom + ' ' + s.categorie).toLowerCase().includes(r)) : this.servicesProposes();
  });
  /** Montants courants, en un clic. */
  readonly montantsRapides = [5000, 10000, 25000, 50000, 100000];

  agenceId: number | null = null;
  serviceId: number | null = null;
  typeId: number | null = null;
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

  private readonly serviceChoisiId = signal<number | null>(null);
  readonly serviceChoisi = computed(() => this.catalogue().find((s) => s.id === this.serviceChoisiId()) ?? null);
  private readonly typeChoisiId = signal<number | null>(null);
  readonly typeChoisi = computed(() => this.serviceChoisi()?.types.find((t) => t.id === this.typeChoisiId()) ?? null);

  private minuterie?: ReturnType<typeof setTimeout>;
  private minuterieClient?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        const actives = m.agences.filter((a) => a.actif);
        this.agenceId = actives[0]?.id ?? null;
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

  /** Operations proposees : les communes au service, plus celles propres au reseau choisi. */
  typesProposes(s: ServiceFinancier): TypeOperation[] {
    return this.typesDe(s).filter((t) => !t.reseauId || t.reseauId === this.reseauId);
  }

  reseauxActifs(): { id: number; nom: string; logo?: string | null }[] {
    return (this.serviceChoisi()?.reseaux ?? []).filter((r) => r.actif);
  }

  /** Transfert d'argent : la piece du client est conseillee. */
  estTransfert(): boolean {
    const s = this.serviceChoisi();
    const t = this.typeChoisi();
    return !!s && (s.reseaux.length > 0 || /transfert/i.test(s.categorie) || ['ENVOI', 'PAIEMENT'].includes(t?.code ?? ''));
  }

  estRecharge(): boolean {
    return this.typeChoisi()?.compteVirtuel === 'RECHARGE';
  }

  changerAgence(): void {
    this.chargerCaisse();
    this.recalculer();
  }

  changerService(): void {
    this.serviceChoisiId.set(this.serviceId);
    const reseaux = this.reseauxActifs();
    this.reseauId = reseaux.length === 1 ? reseaux[0].id : null;
    this.choisirTypeParDefaut();
    this.commission = null;
    this.recalculer();
  }

  /** Une seule operation proposee (ou la meme qu'avant) : choisie d'office. */
  private choisirTypeParDefaut(): void {
    const s = this.serviceChoisi();
    const types = s ? this.typesProposes(s) : [];
    this.typeId = types.find((t) => t.id === this.typeId)?.id ?? (types.length === 1 ? types[0].id : null);
    this.typeChoisiId.set(this.typeId);
  }

  choisirService(s: ServiceFinancier): void {
    this.serviceId = s.id;
    this.changerService();
  }

  choisirType(id: number): void {
    this.typeId = id;
    this.changerType();
  }

  choisirReseau(id: number): void {
    this.reseauId = id;
    this.changerReseau();
  }

  choisirMontant(m: number): void {
    this.montant = m;
    this.changerMontant();
  }

  changerType(): void {
    this.typeChoisiId.set(this.typeId);
    this.commission = null;
    this.recalculer();
  }

  changerReseau(): void {
    this.choisirTypeParDefaut();
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
        this.sansReseau.set(false);
        this.apercu.set(a);
        this.erreurApercu.set(null);
        if (!a.doublon) {
          this.confirmerDoublon = false;
        }
      },
      error: (e) => {
        this.apercu.set(null);
        if (erreurReseau(e)) {
          this.sansReseau.set(true);
          this.erreurApercu.set(null);
          return;
        }
        this.erreurApercu.set(this.message(e, 'Calcul impossible.'));
      }
    });
  }

  /** Hors ligne : effet attendu sur la caisse et le virtuel, d'apres la regle de l'operation. */
  effetsLocaux(): { compte: string; variation: number }[] {
    const t = this.typeChoisi();
    const montant = Math.round(this.montant || 0);
    if (!t || !montant) {
      return [];
    }
    const effets = [{ compte: this.maCaisse() ? 'Ma caisse' : 'Caisse generale', variation: t.effetCash * montant }];
    if (t.compteVirtuel !== 'AUCUN' && t.effetVirtuel) {
      effets.push({ compte: (t.compteVirtuel === 'FLOAT' ? 'Float ' : 'Recharge ') + (this.serviceChoisi()?.nom ?? ''),
        variation: t.effetVirtuel * montant });
    }
    return effets.filter((e) => e.variation !== 0);
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

  /** Logo du reseau choisi, sinon celui du service. */
  logoChoisi(): string | null | undefined {
    return this.reseauxActifs().find((r) => r.id === this.reseauId)?.logo ?? this.serviceChoisi()?.logo;
  }

  /** Libelle du reseau choisi, pour le resume. */
  nomReseau(): string | undefined {
    return this.reseauxActifs().find((r) => r.id === this.reseauId)?.nom;
  }

  commissionModifiee(): boolean {
    const a = this.apercu();
    return !!a && this.commission != null && this.commission !== a.commissionProposee;
  }

  peutValider(): boolean {
    if (this.sansReseau()) {
      return !this.envoi() && !!this.saisie();
    }
    const a = this.apercu();
    return !!a && !this.envoi() && !a.blocages.length && (!a.doublon || this.confirmerDoublon)
      && (!this.commissionModifiee() || !!this.motifCommission.trim());
  }

  /** Valide la transaction ; « continuer » garde la fenetre ouverte pour la suivante. */
  valider(continuer: boolean): void {
    const saisie = this.saisie();
    if (!saisie || !this.peutValider()) {
      return;
    }
    if (this.sansReseau()) {
      this.mettreEnFile(saisie, continuer);
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const cle = nouvelleCleOperation();
    this.service.enregistrerTransaction(saisie, cle).subscribe({
      next: (t) => {
        this.envoi.set(false);
        this.enregistree.emit(t);
        if (continuer) {
          this.derniere.set(t);
          this.reinitialiser();
          this.chargerCaisse();
        } else {
          this.ferme.emit();
        }
      },
      error: (e) => {
        this.envoi.set(false);
        // Coupure pendant l'envoi : la transaction part dans la file plutot que de se perdre.
        // Meme cle : si le serveur l'avait deja enregistree, le rejeu ne la refera pas.
        if (erreurReseau(e)) {
          this.sansReseau.set(true);
          this.mettreEnFile(saisie, continuer, cle);
          return;
        }
        this.erreur.set(this.message(e, 'Transaction refusee.'));
        this.demanderApercu();
      }
    });
  }

  /** Sans connexion : la transaction est gardee sur l'appareil et envoyee au retour du reseau. */
  private mettreEnFile(saisie: NonNullable<ReturnType<FormulaireTransaction['saisie']>>, continuer: boolean, cle?: string): void {
    const resume = `${this.typeChoisi()?.libelle ?? 'Operation'} ${this.nomReseau() ?? this.serviceChoisi()?.nom ?? ''} `
      + `${Math.round(this.montant || 0).toLocaleString('fr-FR')} F`;
    const reference = this.horsLigne.ajouter('TRANSACTION', { ...saisie, confirmerDoublon: true }, resume.trim(), cle);
    this.derniereHorsLigne.set(`${reference} · ${resume.trim()}`);
    this.derniere.set(null);
    this.reinitialiser();
    if (!continuer) {
      this.ferme.emit();
    }
  }

  /** Garde l'agence, le service et l'operation pour enchainer, vide le reste. */
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

  /** Caisse ouverte de l'utilisateur dans l'agence choisie. */
  private chargerCaisse(): void {
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

/** Service des cartes prepayees (vente ou recharge de cartes) : il a son ecran dedie. */
function estServiceCartes(s: ServiceFinancier): boolean {
  return s.types.some((t) => t.nature === 'VENTE_CARTE' || t.compteVirtuel === 'RECHARGE');
}
