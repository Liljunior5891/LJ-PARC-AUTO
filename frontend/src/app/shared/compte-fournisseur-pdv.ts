import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CompteFournisseur, ModeSortie, ReglementFournisseur } from '../core/models/achats';
import { AchatsService } from '../core/services/achats.service';
import { ChoixSortie } from './choix-sortie';
import { Modale } from './modale';

/** Paiement regroupe sous un meme numero (une ligne par commande reglee). */
interface Paiement {
  numero: string;
  date: string;
  commandes: string[];
  mode: string;
  reference?: string;
  session?: string;
  auteur: string;
  montant: number;
  statut: 'VALIDE' | 'ANNULE';
  motif?: string;
}

type OngletCompte = 'dettes' | 'paiements' | 'commandes';

/**
 * Compte d'un fournisseur : ce qu'on lui doit, commande par commande avec
 * l'echeance, les paiements deja faits, et un nouveau paiement (depuis une
 * caisse ouverte, en especes hors caisse, par banque ou mobile money).
 */
@Component({
  selector: 'app-compte-fournisseur-pdv',
  imports: [FormsModule, DecimalPipe, DatePipe, Modale, RouterLink, ChoixSortie],
  templateUrl: './compte-fournisseur-pdv.html',
  styleUrl: './compte-fournisseur-pdv.scss'
})
export class CompteFournisseurPdv implements OnInit {
  private readonly service = inject(AchatsService);

  readonly fournisseurId = input.required<number>();
  /** Ouvre directement le paiement de cette commande. */
  readonly commandeAPayer = input<number | null>(null);
  readonly ferme = output<void>();
  /** Le solde a change : la liste appelante se met a jour. */
  readonly modifie = output<void>();

  readonly compte = signal<CompteFournisseur | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly enCours = signal(false);

  // Paiement
  readonly paiement = signal(false);
  readonly montant = signal<number | null>(null);
  readonly mode = signal<ModeSortie>('CAISSE');
  readonly sessionId = signal<number | null>(null);
  commandeId: number | null = null;
  dateReglement = new Date().toISOString().slice(0, 10);
  reference = '';
  note = '';

  // Annulation
  readonly aAnnuler = signal<string | null>(null);
  motif = '';

  readonly aujourdhui = new Date().toISOString().slice(0, 10);

  readonly paiements = computed<Paiement[]>(() => {
    const groupes = new Map<string, Paiement>();
    for (const r of this.compte()?.reglements ?? []) {
      const g = groupes.get(r.numero);
      if (g) {
        g.commandes.push(r.commandeNumero);
        g.montant += r.montant;
      } else {
        groupes.set(r.numero, this.paiementDe(r));
      }
    }
    return [...groupes.values()];
  });

  // Onglets des listes
  readonly onglets: { id: OngletCompte; libelle: string; icone: string }[] = [
    { id: 'dettes', libelle: 'A payer', icone: '⏳' },
    { id: 'paiements', libelle: 'Paiements', icone: '💸' },
    { id: 'commandes', libelle: 'Commandes', icone: '📦' }
  ];
  readonly onglet = signal<OngletCompte>('dettes');

  readonly totalRecu = computed(() => (this.compte()?.dettes ?? []).reduce((s, d) => s + d.montantRecu, 0));
  readonly totalPaye = computed(() => (this.compte()?.dettes ?? []).reduce((s, d) => s + d.regle, 0));
  readonly partPayee = computed(() => {
    const recu = this.totalRecu();
    return recu > 0 ? Math.round((this.totalPaye() / recu) * 100) : 0;
  });

  readonly initiales = computed(() => {
    const mots = (this.compte()?.fournisseur.nom ?? '').trim().split(/\s+/).filter(Boolean);
    return ((mots[0]?.[0] ?? '') + (mots[1]?.[0] ?? '')).toUpperCase() || '?';
  });

  readonly adresse = computed(() => {
    const f = this.compte()?.fournisseur;
    return [f?.adresse, f?.ville, f?.pays].filter((v) => !!v?.trim()).join(', ');
  });

  readonly commandeChoisie = computed(() => this.compte()?.dettes.find((d) => d.commandeId === this.commandeId));

  ngOnInit(): void {
    this.charger(true);
  }

  charger(premier = false): void {
    this.service.compteFournisseur(this.fournisseurId()).subscribe({
      next: (c) => {
        this.compte.set(c);
        const cible = this.commandeAPayer();
        if (premier && cible) {
          const dette = c.dettes.find((d) => d.commandeId === cible);
          if (dette) {
            this.ouvrirPaiement(dette.commandeId, dette.reste);
          }
        }
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement du compte impossible.')
    });
  }

  ouvrirPaiement(commandeId: number | null = null, montant?: number): void {
    this.commandeId = commandeId;
    this.montant.set(montant ?? this.compte()?.fournisseur.du ?? null);
    this.reference = '';
    this.note = '';
    this.dateReglement = this.aujourdhui;
    this.succes.set(null);
    this.erreur.set(null);
    this.paiement.set(true);
  }

  payer(): void {
    const montant = this.montant() ?? 0;
    if (montant <= 0) {
      this.erreur.set('Indiquez le montant paye.');
      return;
    }
    if (this.mode() === 'CAISSE' && !this.sessionId()) {
      this.erreur.set('Choisissez la caisse d\'ou sortent les especes.');
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.service.reglerFournisseur(this.fournisseurId(), {
      montant,
      mode: this.mode(),
      sessionId: this.mode() === 'CAISSE' ? this.sessionId() : null,
      commandeId: this.commandeId,
      dateReglement: this.dateReglement || null,
      reference: this.reference.trim() || null,
      note: this.note.trim() || null
    }).subscribe({
      next: (recu) => {
        this.enCours.set(false);
        this.paiement.set(false);
        this.onglet.set('paiements');
        this.succes.set(`Paiement ${recu.numero} enregistre : ${this.entier(recu.montant)} F. Reste du : ${this.entier(recu.resteDu)} F.`);
        this.charger();
        this.modifie.emit();
      },
      error: (e) => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Paiement impossible.');
      }
    });
  }

  annuler(numero: string): void {
    if (!this.motif.trim()) {
      this.erreur.set('Indiquez le motif de l\'annulation.');
      return;
    }
    this.service.annulerReglementFournisseur(numero, this.motif.trim()).subscribe({
      next: () => {
        this.aAnnuler.set(null);
        this.succes.set(`Paiement ${numero} annule.`);
        this.charger();
        this.modifie.emit();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  nombre(id: OngletCompte): number {
    const c = this.compte();
    if (!c) {
      return 0;
    }
    return id === 'dettes' ? c.dettes.length : id === 'paiements' ? this.paiements().length : c.commandes.length;
  }

  private paiementDe(r: ReglementFournisseur): Paiement {
    return {
      numero: r.numero, date: r.dateReglement, commandes: [r.commandeNumero], mode: r.modeLibelle,
      reference: r.reference, session: r.sessionNumero, auteur: r.auteurNom, montant: r.montant,
      statut: r.statut, motif: r.motifAnnulation
    };
  }

  private entier(v: number): string {
    return Math.round(v).toLocaleString('fr-FR');
  }
}
