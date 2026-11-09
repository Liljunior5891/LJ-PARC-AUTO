import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import {
  Depense,
  FicheFournisseurParc,
  FournisseurParc,
  FournisseurParcRequete,
  MODES_PAIEMENT,
  ReglementFournisseurParc
} from '../../core/models/finance';
import { ChargesParcService } from '../../core/services/charges-parc.service';
import { Modale } from '../../shared/modale';
import { Onglet, OngletsInclines } from '../../shared/onglets-inclines';

/**
 * Fournisseurs du parc (garages, stations, assureurs...) : fiche, achats,
 * solde du, dettes ouvertes (depenses a payer) et reglements partiels.
 */
@Component({
  selector: 'app-fournisseurs-parc',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, OngletsInclines],
  templateUrl: './fournisseurs-parc.html',
  styleUrl: '../depenses/depenses.scss'
})
export class FournisseursParc implements OnInit {
  private readonly service = inject(ChargesParcService);
  readonly peutGerer = inject(AuthService).peutGererParc();
  readonly modes = MODES_PAIEMENT;

  readonly onglet = signal<'fournisseurs' | 'dettes'>('fournisseurs');
  readonly onglets = computed<Onglet[]>(() => [
    { id: 'fournisseurs', libelle: 'Fournisseurs', icone: '🏪' },
    { id: 'dettes', libelle: 'Dettes a regler', icone: '💳', compteur: this.dettes().length || null }
  ]);

  readonly fournisseurs = signal<FournisseurParc[]>([]);
  readonly dettes = signal<Depense[]>([]);
  readonly fiche = signal<FicheFournisseurParc | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  recherche = '';

  readonly filtres = computed(() => {
    const q = this.recherche.trim().toLowerCase();
    return this.fournisseurs().filter((f) => !q || f.nom.toLowerCase().includes(q) || (f.activite ?? '').toLowerCase().includes(q)
      || (f.telephone ?? '').includes(q));
  });
  readonly totalDu = computed(() => this.fournisseurs().reduce((s, f) => s + f.soldeDu, 0));
  readonly totalRetard = computed(() => this.fournisseurs().reduce((s, f) => s + f.enRetard, 0));

  // Fenetres
  readonly edition = signal<FournisseurParc | 'nouveau' | null>(null);
  f: FournisseurParcRequete = this.vide();
  readonly aRegler = signal<Depense | null>(null);
  reglement = { montant: 0, dateReglement: this.aujourdhui(), modePaiement: null as string | null, reference: '', note: '' };
  readonly reglementAAnnuler = signal<ReglementFournisseurParc | null>(null);
  motif = '';

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.fournisseurs().subscribe({
      next: (l) => this.fournisseurs.set(l),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement impossible.')
    });
    this.service.dettes().subscribe({ next: (d) => this.dettes.set(d) });
  }

  changerOnglet(id: string): void {
    this.onglet.set(id as 'fournisseurs' | 'dettes');
    this.succes.set(null);
    this.charger();
  }

  ouvrirFiche(f: FournisseurParc | number): void {
    this.service.fiche(typeof f === 'number' ? f : f.id).subscribe({
      next: (fiche) => this.fiche.set(fiche),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Fiche introuvable.')
    });
  }

  ouvrirEdition(f: FournisseurParc | 'nouveau'): void {
    this.erreurModale.set(null);
    this.f = f === 'nouveau' ? this.vide() : {
      nom: f.nom, activite: f.activite ?? '', contact: f.contact ?? '', telephone: f.telephone ?? '', email: f.email ?? '',
      adresse: f.adresse ?? '', nif: f.nif ?? '', delaiPaiementJours: f.delaiPaiementJours, note: f.note ?? '', actif: f.actif
    };
    this.edition.set(f);
  }

  enregistrer(): void {
    const e = this.edition();
    this.service.enregistrerFournisseur(e === 'nouveau' || e === null ? null : e.id,
      { ...this.f, delaiPaiementJours: Math.max(0, Math.round(Number(this.f.delaiPaiementJours) || 0)) }).subscribe({
      next: (r) => {
        this.edition.set(null);
        this.succes.set(`${r.nom} enregistre.`);
        this.charger();
        if (this.fiche()?.fournisseur.id === r.id) {
          this.ouvrirFiche(r.id);
        }
      },
      error: (err) => this.erreurModale.set(err?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  supprimer(f: FournisseurParc): void {
    this.service.supprimerFournisseur(f.id).subscribe({
      next: () => {
        this.fiche.set(null);
        this.succes.set(`${f.nom} supprime.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Suppression impossible.')
    });
  }

  ouvrirReglement(d: Depense): void {
    this.erreurModale.set(null);
    this.reglement = { montant: d.resteDu, dateReglement: this.aujourdhui(), modePaiement: d.modePaiement ?? null, reference: '', note: '' };
    this.aRegler.set(d);
  }

  regler(): void {
    const d = this.aRegler();
    if (!d) {
      return;
    }
    this.service.regler({
      depenseId: d.id, montant: Number(this.reglement.montant), dateReglement: this.reglement.dateReglement,
      modePaiement: (this.reglement.modePaiement || null) as never, reference: this.reglement.reference || undefined,
      note: this.reglement.note || undefined
    }).subscribe({
      next: (r) => {
        this.aRegler.set(null);
        this.succes.set(`Reglement ${r.numero} de ${Math.round(r.montant).toLocaleString('fr-FR')} F enregistre sur ${d.numero}.`);
        this.apresChangement(d.fournisseurId);
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Reglement impossible.')
    });
  }

  demanderAnnulation(r: ReglementFournisseurParc): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.reglementAAnnuler.set(r);
  }

  annulerReglement(): void {
    const r = this.reglementAAnnuler();
    if (!r || !this.motif.trim()) {
      return;
    }
    this.service.annulerReglement(r.id, this.motif.trim()).subscribe({
      next: () => {
        this.reglementAAnnuler.set(null);
        this.succes.set(`Reglement ${r.numero} annule : la dette remonte de ${Math.round(r.montant).toLocaleString('fr-FR')} F.`);
        this.apresChangement(this.fiche()?.fournisseur.id);
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  private apresChangement(fournisseurId?: number): void {
    this.charger();
    if (fournisseurId && this.fiche()) {
      this.ouvrirFiche(fournisseurId);
    }
  }

  private vide(): FournisseurParcRequete {
    return { nom: '', activite: '', contact: '', telephone: '', email: '', adresse: '', nif: '', delaiPaiementJours: 0, note: '', actif: true };
  }

  private aujourdhui(): string {
    const d = new Date();
    return `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}-${`${d.getDate()}`.padStart(2, '0')}`;
  }
}
