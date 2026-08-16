import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CamionRequete, MarqueCamion, STATUTS_CAMION, TYPES_CARBURANT } from '../../core/models/models';
import { Location } from '../../core/models/exploitation';
import { Depense } from '../../core/models/finance';
import { CamionService } from '../../core/services/camion.service';
import { DepenseService } from '../../core/services/depense.service';
import { LocationService } from '../../core/services/location.service';

@Component({
  selector: 'app-camion-fiche',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './camion-fiche.html'
})
export class CamionFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CamionService);
  private readonly locationService = inject(LocationService);
  private readonly depenseService = inject(DepenseService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly statuts = STATUTS_CAMION;
  readonly carburants = TYPES_CARBURANT;

  readonly id = signal<number | null>(null);
  readonly marques = signal<MarqueCamion[]>([]);
  /** Onglet ouvert sous la fiche : contrats de location ou depenses du camion. */
  readonly volet = signal<'fiche' | 'recettes' | 'depenses'>('fiche');
  readonly contrats = signal<Location[]>([]);
  readonly depenses = signal<Depense[]>([]);
  readonly totalContrats = computed(() =>
    this.contrats().reduce((cumul, contrat) => cumul + contrat.montant, 0)
  );
  readonly totalDepenses = computed(() =>
    this.depenses().reduce((cumul, depense) => cumul + depense.montant, 0)
  );
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly formulaire = this.fb.nonNullable.group({
    immatriculation: ['', [Validators.required, Validators.maxLength(40)]],
    marqueId: [null as number | null],
    marque: [''],
    modele: [''],
    annee: [null as number | null],
    type: [''],
    categorie: [''],
    numeroChassis: [''],
    numeroMoteur: [''],
    couleur: [''],
    capaciteTonnes: [null as number | null],
    typeCarburant: ['DIESEL'],
    dateAcquisition: [null as string | null],
    prixAchat: [null as number | null],
    kilometrageInitial: [0],
    kilometrageActuel: [0],
    statut: ['DISPONIBLE'],
    gpsDeviceId: [''],
    observations: [''],
    actif: [true]
  });

  /** Marque selectionnee, pour l'apercu du logo. */
  readonly marqueChoisie = computed(() => {
    const id = this.marqueSelectionnee();
    return this.marques().find((marque) => marque.id === id) ?? null;
  });

  private readonly marqueSelectionnee = signal<number | null>(null);

  get titre(): string {
    return this.id() === null ? 'Nouveau camion' : 'Fiche du camion';
  }

  ngOnInit(): void {
    this.service.marques().subscribe({ next: (marques) => this.marques.set(marques) });
    // L'apercu suit la marque choisie dans le formulaire.
    this.formulaire.controls.marqueId.valueChanges.subscribe((id) =>
      this.marqueSelectionnee.set(id)
    );

    const parametre = this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      const id = Number(parametre);
      this.id.set(id);
      this.service.parId(id).subscribe({
        next: (camion) => {
          this.formulaire.patchValue(camion as never);
          this.marqueSelectionnee.set(camion.marqueId ?? null);
        },
        error: () => this.erreur.set('Camion introuvable.')
      });
    }
  }

  /** Ouvre un volet et charge ses donnees a la demande. */
  ouvrirVolet(volet: 'fiche' | 'recettes' | 'depenses'): void {
    this.volet.set(volet);
    const id = this.id();
    if (id === null) {
      return;
    }
    if (volet === 'recettes' && this.contrats().length === 0) {
      this.locationService.lister({ camionId: id, size: 100 }).subscribe({
        next: (page) => this.contrats.set(page.content)
      });
    }
    if (volet === 'depenses' && this.depenses().length === 0) {
      this.depenseService.lister({ camionId: id, size: 100 }).subscribe({
        next: (page) => this.depenses.set(page.content)
      });
    }
  }

  soumettre(): void {
    if (this.formulaire.invalid || this.enregistrement()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.enregistrement.set(true);
    this.erreur.set(null);
    this.succes.set(null);

    const valeur = this.formulaire.getRawValue() as unknown as CamionRequete;
    const id = this.id();
    const requete = id === null ? this.service.creer(valeur) : this.service.modifier(id, valeur);

    requete.subscribe({
      next: (camion) => {
        this.enregistrement.set(false);
        if (id === null) {
          void this.router.navigate(['/camions', camion.id]);
        } else {
          this.succes.set('Fiche enregistree.');
        }
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? "Enregistrement impossible.");
      }
    });
  }
}
