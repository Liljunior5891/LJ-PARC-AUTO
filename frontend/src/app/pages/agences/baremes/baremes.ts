import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Bareme,
  CommissionCalculee,
  CompteCredit,
  ControleBareme,
  ModeBareme,
  ServiceFinancier,
  StatutBareme,
  TrancheBareme,
  TypeOperation
} from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';
import { LogoService, attributsOption } from '../../../shared/logo-service';
import { Modale } from '../../../shared/modale';

interface FormulaireBareme {
  serviceId: number | null;
  typeOperationId: number | null;
  reseauId: number | null;
  libelle: string;
  mode: ModeBareme;
  valeurFixe: number | null;
  taux: number | null;
  minimum: number | null;
  maximum: number | null;
  tranches: TrancheBareme[];
  compteCredit: CompteCredit;
  dateDebut: string;
  dateFin: string;
  dateEffet: string;
}

const STATUTS: Record<StatutBareme, string> = {
  EN_COURS: 'En vigueur',
  A_VENIR: 'A venir',
  TERMINE: 'Termine',
  DESACTIVE: 'Desactive'
};

/** Date du jour a Lome (UTC+0), au format des champs date. */
function aujourdhui(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Baremes de commission : fixe, pourcentage ou tranches, par service, et au
 * besoin par operation et par reseau ; le plus precis l'emporte. Modifier un
 * bareme en vigueur cree une nouvelle version ; la commission d'une operation
 * deja validee ne change jamais. Un simulateur montre la commission qu'une
 * operation recevrait, et les exemples du cahier des charges sont controles.
 */
@Component({
  selector: 'app-baremes-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, LogoService, Modale],
  templateUrl: './baremes.html',
  styleUrl: './baremes.scss'
})
export class BaremesAgences implements OnInit {
  readonly service = inject(AgencesService);

  readonly catalogue = signal<ServiceFinancier[]>([]);
  readonly baremes = signal<Bareme[]>([]);
  readonly controle = signal<ControleBareme[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly modifiable = signal(false);
  readonly statuts = STATUTS;
  readonly opt = attributsOption;

  /** Service affiche a droite ; le testeur porte sur lui. */
  private readonly serviceActifId = signal<number | null>(null);
  readonly serviceActif = computed(() => this.catalogue().find((s) => s.id === this.serviceActifId()) ?? null);

  /** Exemples du cahier dont la commission calculee est la bonne. */
  readonly controleJustes = computed(() => this.controle().filter((c) => c.calculee === c.attendue).length);

  choisirService(id: number): void {
    this.serviceActifId.set(id);
    const s = this.serviceDe(id);
    this.simService = id;
    this.simType = s?.types.find((t) => t.actif)?.id ?? null;
    this.simReseau = null;
    this.simuler();
  }

  /** Baremes du service : en vigueur d'abord, puis a venir, puis termines. */
  baremesDe(serviceId: number): Bareme[] {
    const ordre: Record<StatutBareme, number> = { EN_COURS: 0, A_VENIR: 1, TERMINE: 2, DESACTIVE: 3 };
    return this.baremes().filter((b) => b.serviceId === serviceId).sort((a, b) => ordre[a.statut] - ordre[b.statut]);
  }

  enVigueur(baremes: Bareme[]): number {
    return baremes.filter((b) => b.statut === 'EN_COURS').length;
  }
  historique = false;

  /** Baremes regroupes par service, services sans bareme compris. */
  readonly parService = computed(() =>
    this.catalogue()
      .filter((s) => s.actif || this.baremes().some((b) => b.serviceId === s.id))
      .map((s) => ({ service: s, baremes: this.baremes().filter((b) => b.serviceId === s.id) }))
  );
  readonly exemplesPresents = computed(() => this.baremes().some((b) => b.exemple && b.statut !== 'TERMINE'));

  // Simulateur
  simService: number | null = null;
  simType: number | null = null;
  simReseau: number | null = null;
  simMontant: number | null = 100000;
  readonly simulation = signal<CommissionCalculee | null>(null);

  // Formulaire
  readonly edition = signal<Bareme | 'nouveau' | null>(null);
  f: FormulaireBareme = this.vide();
  montantTest: number | null = 100000;

  // Historique et fin
  readonly versions = signal<Bareme[] | null>(null);
  readonly aTerminer = signal<Bareme | null>(null);
  dateFinTerminer = aujourdhui();

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({ next: (m) => this.modifiable.set(m.permissions.includes('PARAMETRAGE')) });
    this.service.catalogue().subscribe({
      next: (c) => {
        this.catalogue.set(c);
        const premier = c.find((s) => s.actif && s.types.length);
        if (premier && this.serviceActifId() == null) {
          this.choisirService(premier.id);
        }
      },
      error: (e) => this.erreur.set(this.message(e, 'Chargement du catalogue impossible.'))
    });
    this.charger();
  }

  charger(): void {
    this.service.baremes(this.historique).subscribe({
      next: (b) => {
        this.baremes.set(b);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Chargement des baremes impossible.'));
      }
    });
    this.service.controleBaremes().subscribe({ next: (c) => this.controle.set(c) });
  }

  serviceDe(id: number | null): ServiceFinancier | undefined {
    return this.catalogue().find((s) => s.id === id);
  }

  // ------------------------------------------------------------- simulateur
  changerServiceSimule(): void {
    const s = this.serviceDe(this.simService);
    this.simType = s?.types.find((t) => t.actif)?.id ?? null;
    this.simReseau = null;
    this.simuler();
  }

  simuler(): void {
    if (!this.simService || !this.simMontant || this.simMontant <= 0) {
      this.simulation.set(null);
      return;
    }
    this.service.simulerCommission(this.simService, Math.round(this.simMontant), this.simType, this.simReseau).subscribe({
      next: (r) => this.simulation.set(r),
      error: () => this.simulation.set(null)
    });
  }

  // ------------------------------------------------------------- formulaire
  nouveau(serviceId?: number): void {
    this.f = this.vide();
    this.f.serviceId = serviceId ?? this.catalogue().find((s) => s.actif)?.id ?? null;
    this.proposerLibelle();
    this.erreurModale.set(null);
    this.edition.set('nouveau');
  }

  modifier(b: Bareme): void {
    this.f = {
      serviceId: b.serviceId, typeOperationId: b.typeOperationId ?? null, reseauId: b.reseauId ?? null,
      libelle: b.libelle.replace(' (exemple du cahier)', ''), mode: b.mode, valeurFixe: b.valeurFixe ?? null,
      taux: b.taux ?? null, minimum: b.minimum ?? null, maximum: b.maximum ?? null,
      tranches: b.tranches.map((t) => ({ ...t })), compteCredit: b.compteCredit,
      dateDebut: b.dateDebut, dateFin: b.dateFin ?? '', dateEffet: aujourdhui()
    };
    if (!this.f.tranches.length) {
      this.f.tranches = [this.trancheVide(0)];
    }
    this.erreurModale.set(null);
    this.edition.set(b);
  }

  /** Bareme en vigueur avant aujourd'hui : la modification creera une nouvelle version. */
  nouvelleVersion(e: Bareme | 'nouveau'): boolean {
    return e !== 'nouveau' && e.dateDebut < aujourdhui();
  }

  /** Libelle d'une operation, suivi du reseau quand elle lui est propre. */
  libelleType(s: ServiceFinancier, t: TypeOperation): string {
    const reseau = t.reseauId ? s.reseaux.find((r) => r.id === t.reseauId)?.nom : null;
    return reseau ? `${t.libelle} (${reseau})` : t.libelle;
  }

  /** Une operation propre a un reseau fixe le reseau du bareme. */
  changerType(): void {
    const t = this.serviceDe(this.f.serviceId)?.types.find((x) => x.id === this.f.typeOperationId);
    if (t?.reseauId) {
      this.f.reseauId = t.reseauId;
    }
    this.proposerLibelle();
  }

  proposerLibelle(): void {
    const s = this.serviceDe(this.f.serviceId);
    if (!s) {
      return;
    }
    const type = s.types.find((t) => t.id === this.f.typeOperationId)?.libelle;
    const reseau = s.reseaux.find((r) => r.id === this.f.reseauId)?.nom;
    this.f.libelle = [reseau ?? s.nom, type?.toLowerCase()].filter((x) => !!x).join(' - ');
  }

  changerService(): void {
    this.f.typeOperationId = null;
    this.f.reseauId = null;
    this.proposerLibelle();
  }

  changerMode(mode: ModeBareme): void {
    this.f.mode = mode;
    if (mode === 'TRANCHES' && !this.f.tranches.length) {
      this.f.tranches = [this.trancheVide(0)];
    }
  }

  ajouterTranche(): void {
    const derniere = this.f.tranches[this.f.tranches.length - 1];
    const debut = derniere?.montantMax != null ? derniere.montantMax + 1 : null;
    if (derniere && derniere.montantMax == null) {
      this.erreurModale.set('Fermez d\'abord la derniere tranche (montant « jusqu\'a ») avant d\'en ajouter une.');
      return;
    }
    this.erreurModale.set(null);
    this.f.tranches = [...this.f.tranches, this.trancheVide(debut)];
  }

  retirerTranche(i: number): void {
    this.f.tranches = this.f.tranches.filter((_, j) => j !== i);
  }

  /** Apercu calcule a l'ecran, avec les memes regles que le serveur (arrondi au franc). */
  apercu(): string {
    const m = this.montantTest;
    if (!m || m <= 0) {
      return '';
    }
    const pct = (taux: number | null | undefined) => Math.round((m * (taux ?? 0)) / 100);
    const fmt = (v: number) => v.toLocaleString('fr-FR') + ' F';
    switch (this.f.mode) {
      case 'FIXE':
        return fmt(this.f.valeurFixe ?? 0);
      case 'POURCENTAGE': {
        let c = pct(this.f.taux);
        if (this.f.minimum != null && c < this.f.minimum) {
          c = this.f.minimum;
        }
        if (this.f.maximum != null && c > this.f.maximum) {
          c = this.f.maximum;
        }
        return fmt(c);
      }
      case 'TRANCHES': {
        const t = this.f.tranches.find((x) => x.montantMin != null && m >= x.montantMin && (x.montantMax == null || m <= x.montantMax));
        if (!t) {
          return 'aucune tranche ne couvre ce montant (commission 0)';
        }
        return fmt(t.mode === 'FIXE' ? t.valeurFixe ?? 0 : pct(t.taux));
      }
    }
  }

  enregistrer(): void {
    const e = this.edition();
    const f = this.f;
    if (!e || !f.serviceId) {
      return;
    }
    this.service.enregistrerBareme(e === 'nouveau' ? null : e.id, {
      serviceId: f.serviceId,
      typeOperationId: f.typeOperationId,
      reseauId: f.reseauId,
      libelle: f.libelle.trim(),
      mode: f.mode,
      valeurFixe: f.mode === 'FIXE' ? f.valeurFixe : null,
      taux: f.mode === 'POURCENTAGE' ? f.taux : null,
      minimum: f.mode === 'POURCENTAGE' ? f.minimum : null,
      maximum: f.mode === 'POURCENTAGE' ? f.maximum : null,
      tranches: f.mode === 'TRANCHES' ? f.tranches.map((t) => ({
        montantMin: t.montantMin ?? 0, montantMax: t.montantMax ?? null, mode: t.mode,
        valeurFixe: t.mode === 'FIXE' ? t.valeurFixe : null, taux: t.mode === 'POURCENTAGE' ? t.taux : null
      })) : [],
      compteCredit: f.compteCredit,
      dateDebut: f.dateDebut || null,
      dateFin: f.dateFin || null,
      dateEffet: e === 'nouveau' ? null : f.dateEffet || null
    }).subscribe({
      next: (b) => {
        this.edition.set(null);
        this.succes.set(e !== 'nouveau' && b.id !== e.id
          ? `Nouvelle version de « ${b.libelle} » a partir du ${new Date(b.dateDebut).toLocaleDateString('fr-FR')} ; l'ancienne reste dans l'historique.`
          : `Bareme « ${b.libelle} » enregistre (${b.resume}).`);
        this.charger();
        this.simuler();
      },
      error: (err) => this.erreurModale.set(this.message(err, 'Enregistrement impossible.'))
    });
  }

  // ---------------------------------------------------- historique et fin
  voirHistorique(b: Bareme): void {
    this.service.historiqueBareme(b.id).subscribe({
      next: (v) => this.versions.set(v),
      error: (e) => this.erreur.set(this.message(e, 'Lecture de l\'historique impossible.'))
    });
  }

  demanderFin(b: Bareme): void {
    this.dateFinTerminer = aujourdhui();
    this.erreurModale.set(null);
    this.aTerminer.set(b);
  }

  terminer(): void {
    const b = this.aTerminer();
    if (!b) {
      return;
    }
    this.service.terminerBareme(b.id, this.dateFinTerminer || undefined).subscribe({
      next: (r) => {
        this.aTerminer.set(null);
        this.succes.set(r.actif ? `« ${r.libelle} » s'arrete le ${new Date(r.dateFin!).toLocaleDateString('fr-FR')}.`
          : `« ${r.libelle} » est desactive.`);
        this.charger();
        this.simuler();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Arret impossible.'))
    });
  }

  chargerExemples(): void {
    this.succes.set(null);
    this.service.chargerExemplesBaremes().subscribe({
      next: (crees) => {
        this.succes.set(crees.length
          ? `${crees.length} bareme(s) d'exemple charge(s). Ils reproduisent les exemples du cahier : remplacez-les par vos baremes reels.`
          : 'Les exemples sont deja couverts par vos baremes : rien n\'a ete ajoute.');
        this.charger();
        this.simuler();
      },
      error: (e) => this.erreur.set(this.message(e, 'Chargement des exemples impossible.'))
    });
  }

  basculerHistorique(): void {
    this.historique = !this.historique;
    this.charger();
  }

  private trancheVide(debut: number | null): TrancheBareme {
    return { montantMin: debut, montantMax: null, mode: 'POURCENTAGE', valeurFixe: null, taux: null };
  }

  private vide(): FormulaireBareme {
    return {
      serviceId: null, typeOperationId: null, reseauId: null, libelle: '', mode: 'POURCENTAGE', valeurFixe: null,
      taux: null, minimum: null, maximum: null, tranches: [this.trancheVide(0)], compteCredit: 'COMMISSIONS',
      dateDebut: aujourdhui(), dateFin: '', dateEffet: aujourdhui()
    };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
