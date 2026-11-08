import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { CategorieDepense, ChargeRecurrente, CompteAgence, DepenseAgence, SyntheseDepenses } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

type Onglet = 'liste' | 'suivi' | 'recurrentes';

function moisCourant(): string {
  return new Date().toISOString().slice(0, 7);
}

/**
 * Depenses des agences : saisie (le compte choisi, ou la caisse du caissier,
 * est debite), suivi mensuel avec comparaison des mois et charges recurrentes
 * reglees une seule fois par mois.
 */
@Component({
  selector: 'app-depenses-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, SuiteListe],
  templateUrl: './depenses-agences.html',
  styleUrl: './depenses-agences.scss'
})
export class DepensesAgences implements OnInit {
  readonly service = inject(AgencesService);
  /** Le caissier paie depuis sa caisse ouverte : pas de choix de compte. */
  readonly caissierSeul = !inject(AuthService).aRole('ADMIN', 'SUPERVISEUR_AGENCE');

  onglet: Onglet = 'liste';
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));
  agenceId: number | null = null;
  readonly categories = signal<CategorieDepense[]>([]);
  readonly comptes = signal<CompteAgence[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);

  // Liste
  readonly page = signal<Page<DepenseAgence> | null>(null);
  private readonly suite = new SuiteDePages();
  filtre = { du: '', au: '', categorieId: null as number | null, statut: '', q: '' };

  // Suivi
  mois = moisCourant();
  readonly synthese = signal<SyntheseDepenses | null>(null);
  readonly maxMois = computed(() => Math.max(1, ...(this.synthese()?.mois6.map((m) => m.total) ?? [0])));

  // Recurrentes
  readonly recurrentes = signal<ChargeRecurrente[]>([]);
  moisGeneration = moisCourant();
  compteGeneration: number | null = null;

  // Formulaires
  readonly saisie = signal(false);
  f = this.vide();
  fichier: File | null = null;
  readonly charge = signal<ChargeRecurrente | 'nouvelle' | null>(null);
  fc = { categorieId: null as number | null, libelle: '', montant: null as number | null, jourEcheance: 5, actif: true };
  readonly detail = signal<DepenseAgence | null>(null);
  readonly aAnnuler = signal<DepenseAgence | null>(null);
  motif = '';

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        this.agenceId = m.agences.find((a) => a.actif)?.id ?? null;
        this.chargerComptes();
        this.charger();
      }
    });
    this.service.categoriesDepense().subscribe({ next: (c) => this.categories.set(c) });
  }

  changerOnglet(o: Onglet): void {
    this.onglet = o;
    this.succes.set(null);
    this.charger();
  }

  changerAgence(): void {
    this.chargerComptes();
    this.charger();
  }

  charger(): void {
    if (this.onglet === 'liste') {
      this.page.set(null);
      this.chargerListe();
    } else if (this.onglet === 'suivi') {
      this.service.syntheseDepenses(this.agenceId, this.mois).subscribe({ next: (s) => this.synthese.set(s) });
    } else if (this.agenceId) {
      this.service.chargesRecurrentes(this.agenceId, this.moisGeneration).subscribe({ next: (r) => this.recurrentes.set(r) });
    }
  }

  chargerListe(): void {
    this.service.depenses({ agenceId: this.agenceId, ...this.filtre }, this.suite.numero()).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des depenses impossible.'))
    });
  }

  suiteListe(): void {
    if (this.suite.demander(this.page())) {
      this.chargerListe();
    }
  }

  private chargerComptes(): void {
    if (!this.agenceId || this.caissierSeul) {
      return;
    }
    this.service.soldes(this.agenceId).subscribe({
      next: (e) => {
        const liste = (e.agences[0]?.comptes ?? []).filter((c) => c.actif && c.type !== 'CAISSE_CAISSIER');
        this.comptes.set(liste);
        this.compteGeneration = liste.find((c) => c.type === 'CAISSE_GENERALE')?.id ?? null;
      }
    });
  }

  // --------------------------------------------------------------- saisie
  nouvelle(): void {
    this.f = this.vide();
    this.f.compteId = this.comptes().find((c) => c.type === 'CAISSE_GENERALE')?.id ?? null;
    this.fichier = null;
    this.erreurModale.set(null);
    this.saisie.set(true);
  }

  choisirFichier(evenement: Event): void {
    this.fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
  }

  enregistrer(): void {
    const f = this.f;
    if (!this.agenceId || !f.categorieId || !f.libelle.trim() || !f.montant) {
      return;
    }
    this.service.creerDepense({
      agenceId: this.agenceId, categorieId: f.categorieId, libelle: f.libelle.trim(), montant: Math.round(f.montant),
      compteId: this.caissierSeul ? null : f.compteId, beneficiaire: f.beneficiaire.trim() || undefined,
      reference: f.reference.trim() || undefined, observations: f.observations.trim() || undefined
    }).subscribe({
      next: (d) => {
        const fin = () => {
          this.saisie.set(false);
          this.succes.set(`${d.numero} enregistree : ${d.montant.toLocaleString('fr-FR')} F debites de ${d.compteNom ?? 'la caisse'}.`);
          this.charger();
        };
        if (this.fichier) {
          this.service.joindreJustificatifDepense(d.id, this.fichier).subscribe({ next: fin, error: fin });
        } else {
          fin();
        }
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  demanderAnnulation(d: DepenseAgence): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aAnnuler.set(d);
  }

  annuler(): void {
    const d = this.aAnnuler();
    if (!d || !this.motif.trim()) {
      return;
    }
    this.service.annulerDepense(d.id, this.motif.trim()).subscribe({
      next: (r) => {
        this.aAnnuler.set(null);
        this.detail.set(null);
        this.succes.set(`${r.numero} annulee : le montant revient sur ${r.compteNom}.`);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Annulation impossible.'))
    });
  }

  ouvrirJustificatif(d: DepenseAgence): void {
    this.service.justificatifDepense(d.id).subscribe({
      next: (b) => {
        const url = URL.createObjectURL(b);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      }
    });
  }

  // ---------------------------------------------------------- recurrentes
  editerCharge(c: ChargeRecurrente | 'nouvelle'): void {
    this.fc = c === 'nouvelle'
      ? { categorieId: this.categories()[0]?.id ?? null, libelle: '', montant: null, jourEcheance: 5, actif: true }
      : { categorieId: c.categorieId, libelle: c.libelle, montant: c.montant, jourEcheance: c.jourEcheance, actif: c.actif };
    this.erreurModale.set(null);
    this.charge.set(c);
  }

  enregistrerCharge(): void {
    const c = this.charge();
    if (!c || !this.agenceId || !this.fc.categorieId || !this.fc.libelle.trim() || !this.fc.montant) {
      return;
    }
    this.service.enregistrerChargeRecurrente(c === 'nouvelle' ? null : c.id, {
      agenceId: this.agenceId, categorieId: this.fc.categorieId, libelle: this.fc.libelle.trim(),
      montant: Math.round(this.fc.montant), jourEcheance: this.fc.jourEcheance, actif: this.fc.actif
    }).subscribe({
      next: () => {
        this.charge.set(null);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  totalARegler(): number {
    return this.recurrentes().filter((r) => r.actif && !r.regleeCeMois).reduce((s, r) => s + r.montant, 0);
  }

  generer(): void {
    if (!this.agenceId) {
      return;
    }
    this.succes.set(null);
    this.erreur.set(null);
    this.service.genererCharges(this.agenceId, this.moisGeneration, this.caissierSeul ? null : this.compteGeneration).subscribe({
      next: (g) => {
        const total = g.creees.reduce((s, d) => s + d.montant, 0);
        this.succes.set(g.creees.length
          ? `${g.creees.length} charge(s) reglee(s) pour ${total.toLocaleString('fr-FR')} F` + (g.dejaReglees.length ? ` ; deja reglees : ${g.dejaReglees.join(', ')}.` : '.')
          : 'Toutes les charges du mois sont deja reglees : rien n\'a ete ajoute (pas de doublon).');
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Generation impossible.'))
    });
  }

  private vide() {
    return { categorieId: null as number | null, libelle: '', montant: null as number | null, compteId: null as number | null,
      beneficiaire: '', reference: '', observations: '' };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
