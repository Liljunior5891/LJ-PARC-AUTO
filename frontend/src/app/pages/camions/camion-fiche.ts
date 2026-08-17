import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CamionRequete,
  MarqueCamion,
  Odometre,
  Referentiel,
  STATUTS_CAMION,
  TYPES_CARBURANT,
  LIBELLES_SOURCE_RELEVE
} from '../../core/models/models';
import { OptionSelect, SelectRecherche } from '../../shared/select-recherche';
import { Location } from '../../core/models/exploitation';
import { Depense } from '../../core/models/finance';
import { CamionService } from '../../core/services/camion.service';
import { DepenseService } from '../../core/services/depense.service';
import { LocationService } from '../../core/services/location.service';

@Component({
  selector: 'app-camion-fiche',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, SelectRecherche],
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
  readonly types = signal<Referentiel[]>([]);
  readonly couleurs = signal<Referentiel[]>([]);
  readonly odometre = signal<Odometre | null>(null);

  /** Options des listes deroulantes avec recherche. */
  readonly optionsMarque = computed<OptionSelect[]>(() =>
    this.marques().map((marque) => ({
      valeur: marque.id,
      libelle: marque.nom,
      detail: marque.pays,
      couleur: marque.couleur,
      monogramme: marque.monogramme,
      logoUrl: marque.logoUrl
    }))
  );
  readonly optionsType = computed<OptionSelect[]>(() =>
    this.types().map((type) => ({
      valeur: type.id,
      libelle: type.libelle,
      detail: type.complement
    }))
  );
  readonly optionsCouleur = computed<OptionSelect[]>(() =>
    this.couleurs().map((couleur) => ({
      valeur: couleur.id,
      libelle: couleur.libelle,
      couleur: couleur.complement
    }))
  );
  /** Onglet ouvert sous la fiche : contrats de location ou depenses du camion. */
  readonly volet = signal<'fiche' | 'recettes' | 'depenses' | 'odometre'>('fiche');
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
    typeId: [null as number | null],
    type: [''],
    categorie: [''],
    numeroChassis: [''],
    numeroMoteur: [''],
    couleurId: [null as number | null],
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
    this.service.types().subscribe({ next: (types) => this.types.set(types) });
    this.service.couleurs().subscribe({ next: (couleurs) => this.couleurs.set(couleurs) });
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
  ouvrirVolet(volet: 'fiche' | 'recettes' | 'depenses' | 'odometre'): void {
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
    if (volet === 'odometre' && this.odometre() === null) {
      this.service.odometre(id).subscribe({
        next: (odometre) => this.odometre.set(odometre)
      });
    }
  }

  /** Libelle lisible de l'origine d'un releve. */
  libelleSource(source: string): string {
    return LIBELLES_SOURCE_RELEVE[source as keyof typeof LIBELLES_SOURCE_RELEVE] ?? source;
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
