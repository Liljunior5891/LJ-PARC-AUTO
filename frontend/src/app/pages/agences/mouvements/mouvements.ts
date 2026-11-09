import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompteAgence, MouvementAgence, StatutMouvement, TypeMouvement } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { OngletsInclines } from '../../../shared/onglets-inclines';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

const TYPES: { code: TypeMouvement; libelle: string; aide: string }[] = [
  { code: 'APPROVISIONNEMENT', libelle: 'Approvisionnement', aide: 'Alimenter un float, le compte de recharge ou une caisse' },
  { code: 'TRANSFERT_INTERNE', libelle: 'Transfert interne', aide: 'Deplacer de l\'argent entre deux comptes de l\'agence' },
  { code: 'VERSEMENT_BANCAIRE', libelle: 'Versement bancaire', aide: 'Deposer des especes ou du virtuel a la banque' },
  { code: 'RETRAIT_BANCAIRE', libelle: 'Retrait bancaire', aide: 'Retirer de l\'argent de la banque' },
  { code: 'CREDIT_COMMISSION', libelle: 'Credit de commission', aide: 'Sortir ou recevoir des commissions' },
  { code: 'REGULARISATION', libelle: 'Regularisation', aide: 'Corriger le solde d\'un seul compte (toujours valide par un tiers)' },
  { code: 'AUTRE', libelle: 'Autre mouvement', aide: 'Tout autre mouvement autorise entre deux comptes' }
];

const STATUTS: Record<StatutMouvement, string> = {
  EN_ATTENTE: 'En attente',
  VALIDE: 'Valide',
  REJETE: 'Rejete',
  ANNULE: 'Annule'
};

/**
 * Mouvements entre comptes d'une agence : ils ne creent ni recette ni
 * depense. Au-dela du seuil regle, ou pour une regularisation, une autre
 * personne habilitee les valide avant qu'ils ne touchent les soldes.
 */
@Component({
  selector: 'app-mouvements-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, OngletsInclines, SuiteListe],
  templateUrl: './mouvements.html',
  styleUrl: './mouvements.scss'
})
export class MouvementsAgences implements OnInit {
  readonly ongletsStatut = [
    { id: 'TOUS', libelle: 'Tous', icone: '📋' },
    { id: 'EN_ATTENTE', libelle: 'En attente', icone: '⏳' },
    { id: 'VALIDE', libelle: 'Valides', icone: '✅' },
    { id: 'REJETE', libelle: 'Rejetes', icone: '⛔' },
    { id: 'ANNULE', libelle: 'Annules', icone: '↩' }
  ];
  readonly service = inject(AgencesService);
  readonly types = TYPES;
  readonly statuts = STATUTS;

  readonly page = signal<Page<MouvementAgence> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));
  readonly comptes = signal<CompteAgence[]>([]);
  readonly comptesActifs = computed(() => this.comptes().filter((c) => c.actif));

  filtreAgence: number | null = null;
  filtreStatut: StatutMouvement | '' = '';

  choisirStatut(id: string): void {
    this.filtreStatut = id === 'TOUS' ? '' : (id as StatutMouvement);
    this.filtrer();
  }

  // Formulaire
  readonly saisie = signal(false);
  f: { agenceId: number | null; type: TypeMouvement; source: number | null; destination: number | null; compte: number | null;
       sens: 'AUGMENTER' | 'DIMINUER'; montant: number | null; motif: string; justification: string } = this.vide();
  fichier: File | null = null;

  readonly detail = signal<MouvementAgence | null>(null);
  readonly decision = signal<{ m: MouvementAgence; action: 'rejet' | 'annulation' } | null>(null);
  motifDecision = '';

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({ next: () => this.charger() });
  }

  charger(): void {
    this.service.mouvements({ agenceId: this.filtreAgence, statut: this.filtreStatut }, this.suite.numero()).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des mouvements impossible.'))
    });
  }

  filtrer(): void {
    this.page.set(null);
    this.charger();
  }

  suiteListe(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  aideType(): string {
    return TYPES.find((t) => t.code === this.f.type)?.aide ?? '';
  }

  // ------------------------------------------------------------ saisie
  nouveau(): void {
    this.f = this.vide();
    this.f.agenceId = this.filtreAgence ?? this.agences()[0]?.id ?? null;
    this.fichier = null;
    this.erreurModale.set(null);
    this.chargerComptes();
    this.saisie.set(true);
  }

  chargerComptes(): void {
    this.comptes.set([]);
    if (!this.f.agenceId) {
      return;
    }
    this.service.soldes(this.f.agenceId).subscribe({
      next: (e) => this.comptes.set(e.agences[0]?.comptes ?? []),
      error: (e) => this.erreurModale.set(this.message(e, 'Lecture des comptes impossible.'))
    });
  }

  choisirFichier(evenement: Event): void {
    this.fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
  }

  enregistrer(): void {
    const f = this.f;
    if (!f.agenceId || !f.montant || !f.motif.trim()) {
      return;
    }
    const regularisation = f.type === 'REGULARISATION';
    this.service.creerMouvement({
      agenceId: f.agenceId, type: f.type, montant: Math.round(f.montant), motif: f.motif.trim(),
      justification: f.justification.trim() || undefined,
      compteSourceId: regularisation ? null : f.source, compteDestinationId: regularisation ? null : f.destination,
      compteId: regularisation ? f.compte : null, sens: regularisation ? f.sens : undefined
    }).subscribe({
      next: (m) => {
        const fin = () => {
          this.saisie.set(false);
          this.succes.set(m.statut === 'VALIDE'
            ? `${m.reference} enregistre : ${m.typeLibelle.toLowerCase()} de ${m.montant.toLocaleString('fr-FR')} F applique aux soldes.`
            : `${m.reference} en attente de validation par une autre personne habilitee : les soldes ne bougent pas encore.`);
          this.filtrer();
        };
        if (this.fichier) {
          this.service.joindrePieceMouvement(m.id, this.fichier).subscribe({ next: fin, error: fin });
        } else {
          fin();
        }
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  // ---------------------------------------------------------- decisions
  valider(m: MouvementAgence): void {
    this.service.validerMouvement(m.id).subscribe({
      next: (r) => {
        this.detail.set(null);
        this.succes.set(`${r.reference} valide : les soldes sont mis a jour.`);
        this.filtrer();
      },
      error: (e) => this.erreur.set(this.message(e, 'Validation impossible.'))
    });
  }

  demander(m: MouvementAgence, action: 'rejet' | 'annulation'): void {
    this.motifDecision = '';
    this.erreurModale.set(null);
    this.decision.set({ m, action });
  }

  confirmerDecision(): void {
    const d = this.decision();
    if (!d || !this.motifDecision.trim()) {
      return;
    }
    const appel = d.action === 'rejet'
      ? this.service.rejeterMouvement(d.m.id, this.motifDecision.trim())
      : this.service.annulerMouvement(d.m.id, this.motifDecision.trim());
    appel.subscribe({
      next: (r) => {
        this.decision.set(null);
        this.detail.set(null);
        this.succes.set(d.action === 'rejet' ? `${r.reference} rejete.` : `${r.reference} annule : l'ecriture inverse est passee.`);
        this.filtrer();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Action impossible.'))
    });
  }

  ouvrirPiece(m: MouvementAgence): void {
    this.service.pieceMouvement(m.id).subscribe({
      next: (b) => {
        const url = URL.createObjectURL(b);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: () => this.erreur.set('Lecture de la piece impossible.')
    });
  }

  private vide() {
    return {
      agenceId: null as number | null, type: 'APPROVISIONNEMENT' as TypeMouvement, source: null as number | null,
      destination: null as number | null, compte: null as number | null, sens: 'AUGMENTER' as 'AUGMENTER' | 'DIMINUER',
      montant: null as number | null, motif: '', justification: ''
    };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
