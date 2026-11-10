import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  CATEGORIES_DEPENSE,
  CategorieCharge,
  CategorieDepense,
  Depense,
  DepenseRecurrenteParc,
  DepenseRecurrenteParcRequete,
  FournisseurParc,
  LIBELLES_STATUT_DEPENSE_PARC,
  LigneCategorieDepense,
  LigneChargeGenerale,
  MODES_PAIEMENT,
  MoisAPayerParc,
  StatutDepenseParc
} from '../../core/models/finance';
import { Camion, Page } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChargesParcService } from '../../core/services/charges-parc.service';
import { DepenseService, NaturesParCategorie } from '../../core/services/depense.service';
import { FormulaireDepense } from '../../shared/formulaire-depense';
import { Modale } from '../../shared/modale';
import { Onglet, OngletsInclines } from '../../shared/onglets-inclines';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';

type IdOnglet = 'liste' | 'a-payer' | 'recurrentes' | 'a-valider' | 'categories';

/**
 * Depenses du parc : depenses des vehicules et charges generales, mois a payer
 * des depenses recurrentes, validation par l'administrateur et categories des
 * charges generales. Une depense s'annule avec un motif ; elle ne se supprime plus.
 */
@Component({
  selector: 'app-depenses',
  imports: [SuiteListe, FormsModule, DecimalPipe, DatePipe, Modale, FormulaireDepense, OngletsInclines],
  templateUrl: './depenses.html',
  styleUrl: './depenses.scss'
})
export class Depenses implements OnInit {
  private readonly service = inject(DepenseService);
  private readonly charges = inject(ChargesParcService);
  private readonly camionService = inject(CamionService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly categories = CATEGORIES_DEPENSE;
  readonly modes = MODES_PAIEMENT;
  readonly libellesStatut = LIBELLES_STATUT_DEPENSE_PARC;
  readonly peutGerer = this.auth.peutGererParc();
  readonly estAdmin = this.auth.aRole('ADMIN');

  readonly onglet = signal<IdOnglet>('liste');
  readonly onglets = computed<Onglet[]>(() => [
    { id: 'liste', libelle: 'Depenses', icone: '🧾' },
    { id: 'a-payer', libelle: 'A payer ce mois', icone: '📅', compteur: this.aPayer().length || null },
    { id: 'recurrentes', libelle: 'Depenses recurrentes', icone: '🔁' },
    ...(this.estAdmin ? [
      { id: 'a-valider', libelle: 'A valider', icone: '⏳', compteur: this.aValider().length || null },
      { id: 'categories', libelle: 'Categories et reglage', icone: '🏷' }
    ] : [])
  ]);

  readonly page = signal<Page<Depense> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly repartition = signal<LigneCategorieDepense[]>([]);
  readonly repartitionCharges = signal<LigneChargeGenerale[]>([]);
  readonly categoriesCharge = signal<CategorieCharge[]>([]);
  readonly fournisseurs = signal<FournisseurParc[]>([]);
  readonly natures = signal<NaturesParCategorie | null>(null);
  readonly aPayer = signal<MoisAPayerParc[]>([]);
  readonly recurrentes = signal<DepenseRecurrenteParc[]>([]);
  readonly aValider = signal<Depense[]>([]);
  readonly validationActive = signal(false);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);
  readonly envoi = signal(false);

  readonly totalPeriode = computed(() => this.repartition().reduce((cumul, ligne) => cumul + ligne.total, 0));

  // ------------------------------------------------- chiffres de synthese de la periode
  /** Repartition de la periode precedente de meme duree, pour l'evolution. */
  readonly repartitionPrecedente = signal<LigneCategorieDepense[] | null>(null);
  readonly totalPrecedent = computed(() => (this.repartitionPrecedente() ?? []).reduce((t, l) => t + l.total, 0));
  /** Evolution en % par rapport a la periode precedente ; null sans base de comparaison. */
  readonly evolution = computed(() => {
    const avant = this.totalPrecedent();
    return this.repartitionPrecedente() && avant > 0 ? Math.round(((this.totalPeriode() - avant) * 100) / avant) : null;
  });
  readonly nombreOperations = computed(() => this.repartition().reduce((t, l) => t + l.nombre, 0));
  readonly totalCharges = computed(() => this.repartition().filter((l) => l.categorie === 'CHARGE_GENERALE').reduce((t, l) => t + l.total, 0));
  readonly totalVehicules = computed(() => this.totalPeriode() - this.totalCharges());
  readonly operationsVehicules = computed(() => this.repartition().filter((l) => l.categorie !== 'CHARGE_GENERALE').reduce((t, l) => t + l.nombre, 0));
  /** Cout moyen par vehicule actif (tout le parc seulement). */
  readonly coutParVehicule = computed(() => (this.camions().length ? Math.round(this.totalVehicules() / this.camions().length) : 0));

  // Filtres
  recherche = '';
  type: '' | 'VEHICULE' | 'GENERALE' = '';
  categorie: CategorieDepense | '' = '';
  statut: StatutDepenseParc | '' = '';
  camionId?: number;
  debut = this.premierJourDuMois();
  fin = this.aujourdhui();
  private readonly suite = new SuiteDePages();

  /** Vehicule impose a la saisie quand on arrive de sa fiche pour une nouvelle depense. */
  camionImposeSaisie: number | null = null;

  // Fenetres
  readonly detail = signal<Depense | null>(null);
  readonly aAnnuler = signal<Depense | null>(null);
  readonly aRefuser = signal<Depense | null>(null);
  motif = '';
  readonly moisAPayer = signal<MoisAPayerParc | null>(null);
  paiement = { dateDepense: this.aujourdhui(), montant: 0, modePaiement: null as string | null, reference: '', aPayer: false };
  readonly modele = signal<DepenseRecurrenteParc | 'nouveau' | null>(null);
  fm = this.modeleVide();
  readonly categorieOuverte = signal<CategorieCharge | 'nouvelle' | null>(null);
  fc = { nom: '', icone: '', actif: true };

  readonly naturesModele = computed(() => {
    const n = this.natures();
    if (!n) {
      return [] as { valeur: string; libelle: string; categorie: string }[];
    }
    return (Object.keys(n) as CategorieDepense[]).filter((c) => c !== 'CARBURANT')
      .flatMap((c) => n[c].map((x) => ({ ...x, categorie: this.categories.find((k) => k.valeur === c)?.libelle ?? c })));
  });

  ngOnInit(): void {
    const q = this.route.snapshot.queryParamMap;
    const camionId = q.get('camionId');
    if (camionId) {
      this.camionId = Number(camionId);
      if (q.get('nouvelle') === 'true') {
        this.camionImposeSaisie = Number(camionId);
        this.formulaireVisible.set(true);
      }
    }
    if (q.get('aValider') && this.estAdmin) {
      this.onglet.set('a-valider');
    }
    this.charger();
    this.chargerAPayer();
    if (this.estAdmin) {
      this.chargerAValider();
    }
    this.camionService.lister({ actif: true, size: 200 }).subscribe({ next: (page) => this.camions.set(page.content) });
    this.charges.categories().subscribe({ next: (c) => this.categoriesCharge.set(c) });
    this.charges.fournisseurs().subscribe({ next: (f) => this.fournisseurs.set(f) });
    this.charges.reglages().subscribe({ next: (r) => this.validationActive.set(r.validationDepenses) });
    this.service.natures().subscribe({ next: (n) => this.natures.set(n) });
  }

  changerOnglet(id: string): void {
    this.onglet.set(id as IdOnglet);
    this.succes.set(null);
    this.erreur.set(null);
    if (id === 'liste') {
      this.charger();
    } else if (id === 'a-payer') {
      this.chargerAPayer();
    } else if (id === 'recurrentes') {
      this.charges.recurrentes().subscribe({ next: (r) => this.recurrentes.set(r) });
    } else if (id === 'a-valider') {
      this.chargerAValider();
    } else {
      this.charges.categories().subscribe({ next: (c) => this.categoriesCharge.set(c) });
    }
  }

  // ---------------------------------------------------------------- liste

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche, categorie: this.categorie, camionId: this.type === 'GENERALE' ? undefined : this.camionId,
        debut: this.debut, fin: this.fin, type: this.type, statut: this.statut, page: this.suite.numero()
      })
      .subscribe({
        next: (page) => {
          this.page.set(cumulerPage(this.page(), page));
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des depenses impossible.');
        }
      });
    this.service.repartition(this.debut, this.fin, this.camionId).subscribe({ next: (lignes) => this.repartition.set(lignes) });
    this.chargerPeriodePrecedente();
    this.charges.repartitionCharges(this.debut, this.fin).subscribe({ next: (l) => this.repartitionCharges.set(l) });
  }

  /** Meme nombre de jours, juste avant le debut de la periode choisie. */
  private chargerPeriodePrecedente(): void {
    const d = new Date(this.debut + 'T12:00:00');
    const f = new Date(this.fin + 'T12:00:00');
    if (!this.debut || !this.fin || Number.isNaN(d.getTime()) || Number.isNaN(f.getTime()) || f < d) {
      this.repartitionPrecedente.set(null);
      return;
    }
    const jours = Math.round((f.getTime() - d.getTime()) / 86400000) + 1;
    const finAvant = new Date(d);
    finAvant.setDate(finAvant.getDate() - 1);
    const debutAvant = new Date(finAvant);
    debutAvant.setDate(debutAvant.getDate() - (jours - 1));
    const iso = (x: Date) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
    this.service.repartition(iso(debutAvant), iso(finAvant), this.camionId).subscribe({
      next: (l) => this.repartitionPrecedente.set(l),
      error: () => this.repartitionPrecedente.set(null)
    });
  }

  filtrer(): void {
    this.page.set(null);
    this.charger();
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  basculerFormulaire(): void {
    this.formulaireVisible.update((visible) => !visible);
    this.erreur.set(null);
    this.succes.set(null);
  }

  /** Depense enregistree par le formulaire partage : on ferme et on recharge. */
  depenseEnregistree(depense: Depense): void {
    this.succes.set(depense.statut === 'ATTENTE_ADMIN'
      ? `Depense ${depense.numero} enregistree : elle attend l'approbation de l'administrateur avant de compter.`
      : `Depense ${depense.numero} enregistree.`);
    this.formulaireVisible.set(false);
    this.filtrer();
    this.chargerAPayer();
  }

  demanderAnnulation(d: Depense): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aAnnuler.set(d);
  }

  annuler(): void {
    const d = this.aAnnuler();
    if (!d || !this.motif.trim()) {
      return;
    }
    this.charges.annuler(d.id, this.motif.trim()).subscribe({
      next: (r) => {
        this.aAnnuler.set(null);
        this.detail.set(null);
        this.succes.set(`Depense ${r.numero} annulee : elle ne compte plus.`);
        this.filtrer();
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  ouvrirJustificatif(d: Depense): void {
    this.charges.justificatif(d.id).subscribe({
      next: (b) => {
        const url = URL.createObjectURL(b);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: () => this.erreur.set('Justificatif introuvable.')
    });
  }

  joindre(d: Depense, evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0];
    if (!fichier) {
      return;
    }
    this.charges.joindre(d.id, fichier).subscribe({
      next: (r) => {
        this.succes.set(`Justificatif joint a ${r.numero}.`);
        this.detail.set(r);
        this.filtrer();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Envoi du justificatif impossible.')
    });
  }

  // ------------------------------------------------------------ validation

  chargerAValider(): void {
    this.charges.aValider().subscribe({ next: (l) => this.aValider.set(l) });
  }

  valider(d: Depense): void {
    if (this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.charges.valider(d.id).subscribe({
      next: (r) => {
        this.envoi.set(false);
        this.detail.set(null);
        this.succes.set(`Depense ${r.numero} approuvee : elle compte dans les resultats.`);
        this.chargerAValider();
        this.filtrer();
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.error?.message ?? 'Validation impossible.');
      }
    });
  }

  demanderRefus(d: Depense): void {
    this.motif = '';
    this.erreurModale.set(null);
    this.aRefuser.set(d);
  }

  refuser(): void {
    const d = this.aRefuser();
    if (!d || !this.motif.trim()) {
      return;
    }
    this.charges.refuser(d.id, this.motif.trim()).subscribe({
      next: (r) => {
        this.aRefuser.set(null);
        this.detail.set(null);
        this.succes.set(`Depense ${r.numero} refusee.`);
        this.chargerAValider();
        this.filtrer();
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Refus impossible.')
    });
  }

  basculerValidation(active: boolean): void {
    this.charges.modifierReglages(active).subscribe({
      next: (r) => {
        this.validationActive.set(r.validationDepenses);
        this.succes.set(r.validationDepenses
          ? 'Validation activee : les depenses du gestionnaire attendront votre approbation.'
          : 'Validation desactivee : les depenses comptent des leur saisie.');
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  // -------------------------------------------------------------- a payer

  chargerAPayer(): void {
    this.charges.aPayer().subscribe({ next: (l) => this.aPayer.set(l) });
  }

  ouvrirPaiement(m: MoisAPayerParc): void {
    this.paiement = { dateDepense: this.aujourdhui(), montant: m.montant, modePaiement: m.modePaiement ?? null, reference: '', aPayer: false };
    this.erreurModale.set(null);
    this.moisAPayer.set(m);
  }

  payer(): void {
    const m = this.moisAPayer();
    if (!m) {
      return;
    }
    this.charges.payer(m.recurrenteId, {
      periode: m.periode, dateDepense: this.paiement.dateDepense, montant: this.paiement.montant,
      modePaiement: (this.paiement.modePaiement || null) as never, reference: this.paiement.reference || undefined,
      aPayer: this.paiement.aPayer
    }).subscribe({
      next: (d) => {
        this.moisAPayer.set(null);
        this.succes.set(`${m.libelle} (${m.periodeLibelle}) : depense ${d.numero} enregistree`
          + (d.statut === 'ATTENTE_ADMIN' ? ", en attente de l'administrateur." : '.'));
        this.chargerAPayer();
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Paiement impossible.')
    });
  }

  // ---------------------------------------------------------- recurrentes

  ouvrirModele(m: DepenseRecurrenteParc | 'nouveau'): void {
    this.erreurModale.set(null);
    this.fm = m === 'nouveau' ? this.modeleVide() : {
      generale: !m.camionId, libelle: m.libelle, camionId: m.camionId ?? null, sousCategorie: m.sousCategorie ?? null,
      categorieChargeId: m.categorieChargeId ?? null, fournisseurId: m.fournisseurId ?? null, montant: m.montant,
      jourEcheance: m.jourEcheance, modePaiement: m.modePaiement ?? null, debut: m.debut, fin: m.fin ?? '',
      actif: m.actif, note: m.note ?? ''
    };
    this.modele.set(m);
  }

  enregistrerModele(): void {
    const m = this.modele();
    const f = this.fm;
    const r: DepenseRecurrenteParcRequete = {
      libelle: f.libelle.trim(), camionId: f.generale ? null : f.camionId, sousCategorie: f.generale ? null : f.sousCategorie,
      categorieChargeId: f.generale ? f.categorieChargeId : null, fournisseurId: f.fournisseurId, montant: Number(f.montant),
      jourEcheance: Number(f.jourEcheance) || 1, modePaiement: (f.modePaiement || null) as never, debut: f.debut,
      fin: f.fin || null, actif: f.actif, note: f.note.trim() || undefined
    };
    this.charges.enregistrerRecurrente(m === 'nouveau' || m === null ? null : m.id, r).subscribe({
      next: (enregistre) => {
        this.modele.set(null);
        this.succes.set(`${enregistre.libelle} enregistree : elle sera proposee chaque mois dans « A payer ».`);
        this.changerOnglet('recurrentes');
        this.chargerAPayer();
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  supprimerModele(m: DepenseRecurrenteParc): void {
    this.charges.supprimerRecurrente(m.id).subscribe({
      next: () => {
        this.succes.set(`${m.libelle} supprimee.`);
        this.changerOnglet('recurrentes');
        this.chargerAPayer();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Suppression impossible.')
    });
  }

  // ----------------------------------------------------------- categories

  ouvrirCategorie(c: CategorieCharge | 'nouvelle'): void {
    this.erreurModale.set(null);
    this.fc = c === 'nouvelle' ? { nom: '', icone: '', actif: true } : { nom: c.nom, icone: c.icone ?? '', actif: c.actif };
    this.categorieOuverte.set(c);
  }

  enregistrerCategorie(): void {
    const c = this.categorieOuverte();
    this.charges.enregistrerCategorie(c === 'nouvelle' || c === null ? null : c.id,
      { nom: this.fc.nom.trim(), icone: this.fc.icone.trim() || undefined, actif: this.fc.actif }).subscribe({
      next: (r) => {
        this.categorieOuverte.set(null);
        this.succes.set(`Categorie ${r.nom} enregistree.`);
        this.changerOnglet('categories');
      },
      error: (e) => this.erreurModale.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  supprimerCategorie(c: CategorieCharge): void {
    this.charges.supprimerCategorie(c.id).subscribe({
      next: () => {
        this.succes.set(`Categorie ${c.nom} supprimee.`);
        this.changerOnglet('categories');
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Suppression impossible.')
    });
  }

  // ---------------------------------------------------------------- outils

  /** Couleur de la carte d'une categorie (memes teintes que les cartes du tableau de bord). */
  couleurCategorie(categorie: string): string {
    const couleurs: Record<string, string> = {
      CARBURANT: 'rouge', ENTRETIEN: 'bleu', PNEUS: 'violet', EXPLOITATION: 'vert', ADMINISTRATIF: 'bleu', PERSONNEL: 'violet',
      CHARGE_GENERALE: 'orange'
    };
    return couleurs[categorie] ?? 'calme';
  }

  /** Part d'un montant dans le total de la periode, en pourcentage entier. */
  pourcent(montant: number): number {
    const total = this.totalPeriode();
    return total === 0 ? 0 : Math.round((montant / total) * 100);
  }

  part(ligne: LigneCategorieDepense): number {
    const total = this.totalPeriode();
    return total === 0 ? 0 : Math.round((ligne.total / total) * 100);
  }

  enAttente(d: Depense): boolean {
    return d.statut === 'ATTENTE_ADMIN';
  }

  private modeleVide() {
    const mois = this.aujourdhui().slice(0, 7);
    return {
      generale: true, libelle: '', camionId: null as number | null, sousCategorie: null as string | null,
      categorieChargeId: null as number | null, fournisseurId: null as number | null, montant: 0, jourEcheance: 5,
      modePaiement: null as string | null, debut: mois, fin: '', actif: true, note: ''
    };
  }

  private aujourdhui(): string {
    const d = new Date();
    return `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}-${`${d.getDate()}`.padStart(2, '0')}`;
  }

  private premierJourDuMois(): string {
    const d = new Date();
    return `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}-01`;
  }
}
