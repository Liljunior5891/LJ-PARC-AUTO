import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import {
  Entretien,
  GRAVITES_INCIDENT,
  Incident,
  Pneu,
  TYPES_ENTRETIEN,
  TYPES_INCIDENT,
  TypeEntretien,
  TypeIncident
} from '../../core/models/maintenance';
import { Camion, Chauffeur } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { MaintenanceService } from '../../core/services/maintenance.service';
import { Modale } from '../../shared/modale';
import { VALIDATION_FORMULAIRE } from '../../shared/validation-formulaire';

type Onglet = 'entretiens' | 'pneus' | 'incidents';

@Component({
  selector: 'app-maintenance',
  imports: [FormsModule, ReactiveFormsModule, DecimalPipe, DatePipe, Modale, VALIDATION_FORMULAIRE],
  templateUrl: './maintenance.html',
  styleUrl: './maintenance.scss'
})
export class Maintenance implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MaintenanceService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly auth = inject(AuthService);

  readonly typesEntretien = TYPES_ENTRETIEN;
  readonly typesIncident = TYPES_INCIDENT;
  readonly gravites = GRAVITES_INCIDENT;

  readonly onglet = signal<Onglet>('entretiens');

  /** Onglets de la page. */
  readonly onglets: { id: Onglet; libelle: string; icone: string }[] = [
    { id: 'entretiens', libelle: 'Entretiens', icone: '🔧' },
    { id: 'pneus', libelle: 'Pneumatiques', icone: '🛞' },
    { id: 'incidents', libelle: 'Incidents', icone: '⚠️' }
  ];
  readonly entretiens = signal<Entretien[]>([]);
  readonly pneus = signal<Pneu[]>([]);
  readonly incidents = signal<Incident[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);
  /** Intervention dont le formulaire de realisation est ouvert. */
  readonly realisationEnCours = signal<Entretien | null>(null);

  kilometrageRealise: number | null = null;
  coutRealisation: number | null = null;
  piecesRemplacees = '';

  readonly alertesEntretien = computed(() => this.entretiens().filter((e) => e.echeanceProche));
  readonly pneusARemplacer = computed(() => this.pneus().filter((p) => p.aRemplacer));
  readonly incidentsOuverts = computed(() => this.incidents().filter((i) => i.ouvert));

  readonly peutGerer = this.auth.peutGererParc();
  readonly peutDeclarer = this.auth.peutGererParc();

  readonly formEntretien = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    type: ['VIDANGE' as TypeEntretien, [Validators.required]],
    libelle: ['', [Validators.required]],
    datePrevue: [null as string | null],
    kilometragePrevu: [null as number | null],
    periodiciteKm: [null as number | null],
    fournisseur: ['']
  });

  readonly formIncident = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    chauffeurId: [null as number | null],
    type: ['PANNE' as TypeIncident, [Validators.required]],
    gravite: ['MOYENNE'],
    description: ['', [Validators.required]],
    lieu: ['']
  });

  readonly formPneu = this.fb.nonNullable.group({
    reference: ['', [Validators.required]],
    marque: [''],
    dimension: [''],
    dateAchat: [new Date().toISOString().slice(0, 10)],
    prixAchat: [null as number | null],
    dureeViePrevueKm: [80000 as number | null]
  });

  ngOnInit(): void {
    this.charger();
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.chauffeurService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.chauffeurs.set(page.content)
    });
  }

  charger(): void {
    this.service.entretiensOuverts().subscribe({
      next: (entretiens) => this.entretiens.set(entretiens),
      error: () => this.erreur.set('Chargement des interventions impossible.')
    });
    this.service.pneus().subscribe({ next: (pneus) => this.pneus.set(pneus) });
    this.service.incidents(0, 50).subscribe({ next: (page) => this.incidents.set(page.content) });
  }

  /** Nombre de lignes de l'onglet, rappele a cote de son libelle. */
  compte(onglet: Onglet): number | null {
    const lignes = { entretiens: this.entretiens(), pneus: this.pneus(), incidents: this.incidents() }[onglet];
    return lignes.length || null;
  }

  changerOnglet(onglet: Onglet): void {
    this.onglet.set(onglet);
    this.formulaireVisible.set(false);
    this.erreur.set(null);
    this.succes.set(null);
  }

  basculerFormulaire(): void {
    this.formulaireVisible.update((visible) => !visible);
  }

  // --- Entretiens ---------------------------------------------------------

  planifier(): void {
    if (this.formEntretien.invalid) {
      this.formEntretien.markAllAsTouched();
      return;
    }
    const brut = this.formEntretien.getRawValue();
    this.service.planifier({ ...brut, camionId: brut.camionId! }).subscribe({
      next: (entretien) => {
        this.succes.set(`Intervention ${entretien.numero} planifiee.`);
        this.formEntretien.reset({ type: 'VIDANGE' });
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Planification impossible.')
    });
  }

  demarrer(entretien: Entretien): void {
    this.service.demarrerEntretien(entretien.id).subscribe({
      next: () => {
        this.succes.set(`${entretien.numero} : le vehicule passe a l'atelier.`);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Demarrage impossible.')
    });
  }

  ouvrirRealisation(entretien: Entretien): void {
    this.kilometrageRealise = entretien.camionKilometrage;
    this.coutRealisation = null;
    this.piecesRemplacees = '';
    this.realisationEnCours.set(entretien);
  }

  realiser(entretien: Entretien): void {
    if (this.kilometrageRealise === null) {
      this.erreur.set('Le kilometrage releve est obligatoire.');
      return;
    }
    this.service
      .realiserEntretien(entretien.id, {
        dateRealisation: new Date().toISOString().slice(0, 10),
        kilometrageRealise: this.kilometrageRealise,
        cout: this.coutRealisation,
        piecesRemplacees: this.piecesRemplacees
      })
      .subscribe({
        next: (maj) => {
          this.realisationEnCours.set(null);
          this.succes.set(
            `${maj.numero} realise. ${maj.depenseNumero ? 'Depense ' + maj.depenseNumero + ' generee.' : ''}`
          );
          this.charger();
        },
        error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Realisation impossible.')
      });
  }

  // --- Pneus --------------------------------------------------------------

  creerPneu(): void {
    if (this.formPneu.invalid) {
      this.formPneu.markAllAsTouched();
      return;
    }
    this.service.creerPneu(this.formPneu.getRawValue()).subscribe({
      next: (pneu) => {
        this.succes.set(`Pneu ${pneu.reference} enregistre en stock.`);
        this.formPneu.reset({ dureeViePrevueKm: 80000 });
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.')
    });
  }

  // --- Incidents ----------------------------------------------------------

  declarer(): void {
    if (this.formIncident.invalid) {
      this.formIncident.markAllAsTouched();
      return;
    }
    const brut = this.formIncident.getRawValue();
    this.service.declarerIncident({ ...brut, camionId: brut.camionId! } as never).subscribe({
      next: (incident) => {
        this.succes.set(
          `Incident ${incident.numero} declare.${incident.immobilisant ? ' Le vehicule est immobilise.' : ''}`
        );
        this.formIncident.reset({ type: 'PANNE', gravite: 'MOYENNE' });
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Declaration impossible.')
    });
  }

  traiter(incident: Incident, statut: 'PRIS_EN_CHARGE' | 'RESOLU'): void {
    const ouvrirEntretien = statut === 'PRIS_EN_CHARGE' && incident.immobilisant;
    this.service.traiterIncident(incident.id, statut, undefined, ouvrirEntretien).subscribe({
      next: (maj) => {
        this.succes.set(`${maj.numero} : ${maj.statutLibelle}.`);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Traitement impossible.')
    });
  }

  classeEcheance(entretien: Entretien): string {
    if (entretien.enRetard) {
      return 'etiquette vendu';
    }
    return entretien.echeanceProche ? 'etiquette en_location' : 'etiquette disponible';
  }

  libelleEcheance(entretien: Entretien): string {
    if (entretien.kilometresRestants !== undefined && entretien.kilometresRestants !== null) {
      return entretien.kilometresRestants < 0
        ? `depasse de ${Math.abs(entretien.kilometresRestants).toLocaleString('fr-FR')} km`
        : `${entretien.kilometresRestants.toLocaleString('fr-FR')} km restants`;
    }
    if (entretien.joursRestants !== undefined && entretien.joursRestants !== null) {
      return entretien.joursRestants < 0
        ? `depasse de ${Math.abs(entretien.joursRestants)} j`
        : `${entretien.joursRestants} j restants`;
    }
    return 'sans echeance';
  }
}
