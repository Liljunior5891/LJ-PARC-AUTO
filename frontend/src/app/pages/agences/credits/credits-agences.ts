import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompteAgence, CreditAgence, RemboursementCredit, TypeCredit } from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';

const TYPES: { code: TypeCredit; libelle: string; aide: string }[] = [
  { code: 'EMPRUNT', libelle: 'Credit recu', aide: 'Un pret recu : l\'argent entre au compte choisi, ce n\'est pas un revenu' },
  { code: 'APPORT', libelle: 'Apport de fonds', aide: 'Argent apporte par le proprietaire ou un associe : pas un revenu' },
  { code: 'AVANCE', libelle: 'Avance accordee', aide: 'Argent prete ou avance par l\'agence : il sort du compte et doit revenir' },
  { code: 'DETTE', libelle: 'Dette', aide: 'Une somme due sans entree d\'argent (achat a credit...) ; le compte est facultatif' }
];

/**
 * Credits, dettes, apports et avances des agences : solde restant,
 * echeancier, remboursements (le capital n'est pas une charge, les interets
 * et frais financiers le sont) et alerte des echeances proches ou depassees.
 */
@Component({
  selector: 'app-credits-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale],
  templateUrl: './credits-agences.html',
  styleUrl: './credits-agences.scss'
})
export class CreditsAgences implements OnInit {
  readonly service = inject(AgencesService);
  readonly types = TYPES;

  readonly credits = signal<CreditAgence[]>([]);
  readonly comptes = signal<CompteAgence[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));
  agenceId: number | null = null;
  filtreStatut = 'EN_COURS';

  readonly totalDettes = computed(() => this.credits().filter((c) => c.dette && c.remboursable && c.statut === 'EN_COURS')
    .reduce((s, c) => s + c.capitalRestant, 0));
  readonly totalCreances = computed(() => this.credits().filter((c) => !c.dette && c.statut === 'EN_COURS')
    .reduce((s, c) => s + c.capitalRestant, 0));

  readonly saisie = signal(false);
  f = this.vide();
  echeances: { datePrevue: string; montant: number | null }[] = [];
  readonly fiche = signal<CreditAgence | null>(null);
  readonly remboursement = signal<CreditAgence | null>(null);
  fr = { capital: null as number | null, interets: null as number | null, compteId: null as number | null, observations: '' };
  readonly aAnnuler = signal<RemboursementCredit | null>(null);
  motif = '';

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        this.agenceId = m.agences.find((a) => a.actif)?.id ?? null;
        this.chargerComptes();
        this.charger();
      }
    });
  }

  charger(): void {
    this.service.credits(this.agenceId, this.filtreStatut || undefined).subscribe({
      next: (c) => this.credits.set(c),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des credits impossible.'))
    });
  }

  changerAgence(): void {
    this.chargerComptes();
    this.charger();
  }

  private chargerComptes(): void {
    if (!this.agenceId) {
      return;
    }
    this.service.soldes(this.agenceId).subscribe({
      next: (e) => this.comptes.set((e.agences[0]?.comptes ?? []).filter((c) => c.actif && c.type !== 'CAISSE_CAISSIER'))
    });
  }

  aideType(): string {
    return TYPES.find((t) => t.code === this.f.type)?.aide ?? '';
  }

  // --------------------------------------------------------------- saisie
  nouveau(): void {
    this.f = this.vide();
    this.f.compteId = this.comptes().find((c) => c.type === 'CAISSE_GENERALE')?.id ?? null;
    this.echeances = [];
    this.erreurModale.set(null);
    this.saisie.set(true);
  }

  ajouterEcheance(): void {
    this.echeances = [...this.echeances, { datePrevue: '', montant: null }];
  }

  retirerEcheance(i: number): void {
    this.echeances = this.echeances.filter((_, j) => j !== i);
  }

  enregistrer(): void {
    const f = this.f;
    if (!this.agenceId || !f.libelle.trim() || !f.tiersNom.trim() || !f.montant) {
      return;
    }
    this.service.creerCredit({
      agenceId: this.agenceId, type: f.type, libelle: f.libelle.trim(), tiersNom: f.tiersNom.trim(),
      tiersTelephone: f.tiersTelephone.trim() || undefined, montant: Math.round(f.montant), dateDebut: f.dateDebut || undefined,
      dateEcheance: f.dateEcheance || undefined, tauxInteret: f.tauxInteret, compteId: f.compteId,
      remboursable: f.remboursable, observations: f.observations.trim() || undefined,
      echeances: this.echeances.filter((e) => e.datePrevue && e.montant).map((e) => ({ datePrevue: e.datePrevue, montant: Math.round(e.montant!) }))
    }).subscribe({
      next: (c) => {
        this.saisie.set(false);
        this.succes.set(`${c.numero} enregistre : ${c.typeLibelle.toLowerCase()} de ${c.montantInitial.toLocaleString('fr-FR')} F.`);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  // -------------------------------------------------------- remboursements
  demanderRemboursement(c: CreditAgence): void {
    this.fr = { capital: c.montantProchaineEcheance || null, interets: null, compteId: c.compteId ?? this.comptes()[0]?.id ?? null, observations: '' };
    this.erreurModale.set(null);
    this.remboursement.set(c);
  }

  rembourser(): void {
    const c = this.remboursement();
    if (!c || this.fr.compteId == null) {
      return;
    }
    this.service.rembourserCredit(c.id, {
      capital: Math.round(this.fr.capital ?? 0), interets: Math.round(this.fr.interets ?? 0), compteId: this.fr.compteId,
      observations: this.fr.observations.trim() || undefined
    }).subscribe({
      next: (r) => {
        this.remboursement.set(null);
        this.fiche.set(this.fiche() ? r : null);
        this.succes.set(r.statut === 'SOLDE' ? `${r.numero} est solde.` : `Remboursement enregistre : reste ${r.capitalRestant.toLocaleString('fr-FR')} F.`);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Remboursement impossible.'))
    });
  }

  demanderAnnulation(r: RemboursementCredit): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aAnnuler.set(r);
  }

  annulerRemboursement(): void {
    const r = this.aAnnuler();
    if (!r || !this.motif.trim()) {
      return;
    }
    this.service.annulerRemboursement(r.id, this.motif.trim()).subscribe({
      next: (c) => {
        this.aAnnuler.set(null);
        this.fiche.set(c);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Annulation impossible.'))
    });
  }

  private vide() {
    return {
      type: 'EMPRUNT' as TypeCredit, libelle: '', tiersNom: '', tiersTelephone: '', montant: null as number | null,
      dateDebut: new Date().toISOString().slice(0, 10), dateEcheance: '', tauxInteret: null as number | null,
      compteId: null as number | null, remboursable: true, observations: ''
    };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
