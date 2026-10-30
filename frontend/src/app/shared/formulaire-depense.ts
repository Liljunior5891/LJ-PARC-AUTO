import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { map } from 'rxjs';
import {
  CATEGORIES_DEPENSE,
  CategorieDepense,
  Depense,
  DepenseRequete,
  MODES_PAIEMENT
} from '../core/models/finance';
import { Camion } from '../core/models/models';
import { AffectationService } from '../core/services/affectation.service';
import { CamionService } from '../core/services/camion.service';
import { DepenseService, NaturesParCategorie } from '../core/services/depense.service';
import { OptionSelect, SelectRecherche } from './select-recherche';
import { VALIDATION_FORMULAIRE } from './validation-formulaire';

/**
 * Formulaire d'enregistrement d'une depense, partage par l'ecran Depenses et la
 * fiche camion.
 * <p>
 * La categorie pilote la liste des natures, et une depense de carburant demande
 * en plus les informations du plein. Le chauffeur n'est jamais saisi : il
 * decoule de l'affectation en cours du camion.
 */
@Component({
  selector: 'app-formulaire-depense',
  imports: [ReactiveFormsModule, SelectRecherche, DecimalPipe, VALIDATION_FORMULAIRE],
  templateUrl: './formulaire-depense.html',
  styleUrl: './formulaire-recette.scss'
})
export class FormulaireDepense implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DepenseService);
  private readonly camionService = inject(CamionService);
  private readonly affectationService = inject(AffectationService);

  /** Camion impose par l'ecran appelant ; le champ devient alors non modifiable. */
  readonly camionImpose = input<number | null>(null);

  readonly enregistree = output<Depense>();
  readonly annulee = output<void>();

  readonly categories = CATEGORIES_DEPENSE;
  readonly modes = MODES_PAIEMENT;

  readonly camions = signal<Camion[]>([]);
  readonly natures = signal<NaturesParCategorie | null>(null);
  readonly categorieSaisie = signal<CategorieDepense>('CARBURANT');
  /** Chauffeur conduisant le camion choisi ; null s'il n'est affecte a personne. */
  readonly chauffeur = signal<string | null>(null);
  readonly chauffeurId = signal<number | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);

  readonly naturesDisponibles = computed(() => this.natures()?.[this.categorieSaisie()] ?? []);

  /** Vrai si la categorie exige les informations de plein (chapitre 18). */
  readonly saisieCarburant = computed(() => this.categorieSaisie() === 'CARBURANT');

  readonly optionsCamion = computed<OptionSelect[]>(() =>
    this.camions().map((camion) => ({
      valeur: camion.id,
      libelle: camion.immatriculation,
      detail: [camion.marque, camion.modele].filter(Boolean).join(' ')
    }))
  );

  /** Immatriculation du camion impose, affichee a la place de la liste. */
  readonly camionLibelle = computed(() => {
    const camion = this.camions().find((c) => c.id === this.camionImpose());
    return camion
      ? `${camion.immatriculation} — ${camion.marque ?? ''} ${camion.modele ?? ''}`.trim()
      : '';
  });

  readonly formulaire = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    categorie: ['CARBURANT' as CategorieDepense],
    sousCategorie: ['DIESEL', [Validators.required]],
    libelle: ['', [Validators.required]],
    montant: [0, [Validators.required, Validators.min(0)]],
    dateDepense: [this.aujourdhui(), [Validators.required]],
    fournisseur: [''],
    modePaiement: [null as string | null],
    reference: [''],
    kilometrage: [null as number | null],
    station: [''],
    quantiteLitres: [null as number | null],
    prixLitre: [null as number | null],
    aChargeClient: [false],
    observations: ['']
  });

  /** Valeurs du formulaire sous forme de signal : les calculs suivent chaque saisie. */
  private readonly valeurs = toSignal(
    this.formulaire.valueChanges.pipe(map(() => this.formulaire.getRawValue())),
    { initialValue: this.formulaire.getRawValue() }
  );

  /** Montant d'un plein : la quantite multipliee par le prix au litre. */
  readonly montantCarburant = computed(() => {
    const v = this.valeurs();
    return Math.round((v.quantiteLitres || 0) * (v.prixLitre || 0));
  });

  ngOnInit(): void {
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.service.natures().subscribe({
      next: (natures) => this.natures.set(natures)
    });

    const c = this.formulaire.controls;
    c.camionId.valueChanges.subscribe((id) => this.camionChange(id));
    c.categorie.valueChanges.subscribe((categorie) => this.categorieChange(categorie));

    const impose = this.camionImpose();
    if (impose !== null) {
      this.formulaire.patchValue({ camionId: impose });
    }
  }

  /** Le camion determine son chauffeur : celui-ci n'est jamais choisi a la main. */
  private camionChange(camionId: number | null): void {
    this.chauffeur.set(null);
    this.chauffeurId.set(null);
    if (camionId === null) {
      return;
    }
    this.affectationService.couranteDuCamion(camionId).subscribe({
      next: (affectation) => {
        this.chauffeur.set(affectation.chauffeurNom ?? null);
        this.chauffeurId.set(affectation.chauffeurId ?? null);
      },
      // Un camion sans affectation repond 404 : ce n'est pas une erreur a signaler.
      error: () => {
        this.chauffeur.set(null);
        this.chauffeurId.set(null);
      }
    });
  }

  /** Changer de categorie change les natures possibles : on repositionne la premiere. */
  private categorieChange(categorie: CategorieDepense): void {
    this.categorieSaisie.set(categorie);
    const premiere = this.naturesDisponibles()[0];
    if (premiere) {
      this.formulaire.controls.sousCategorie.setValue(premiere.valeur);
    }
    if (!this.saisieCarburant()) {
      this.formulaire.patchValue(
        { station: '', quantiteLitres: null, prixLitre: null },
        { emitEvent: false }
      );
    }
  }

  enregistrer(): void {
    if (this.formulaire.invalid || this.enregistrement()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const brut = this.formulaire.getRawValue();
    if (this.saisieCarburant()) {
      if (!brut.quantiteLitres || !brut.prixLitre) {
        this.erreur.set('Renseignez la quantite et le prix au litre : ils donnent le montant du plein.');
        return;
      }
      if (brut.kilometrage === null) {
        this.erreur.set('Le releve du compteur est obligatoire pour un plein.');
        return;
      }
    }
    this.enregistrement.set(true);
    this.erreur.set(null);

    const v = this.formulaire.getRawValue();
    const requete: DepenseRequete = {
      camionId: v.camionId!,
      chauffeurId: this.chauffeurId(),
      sousCategorie: v.sousCategorie,
      libelle: v.libelle,
      // Pour un plein, le montant est calcule : quantite x prix au litre.
      montant: this.saisieCarburant() ? this.montantCarburant() : v.montant,
      dateDepense: v.dateDepense,
      fournisseur: v.fournisseur || undefined,
      modePaiement: (v.modePaiement || null) as DepenseRequete['modePaiement'],
      reference: v.reference || undefined,
      kilometrage: v.kilometrage,
      station: v.station || undefined,
      quantiteLitres: v.quantiteLitres,
      prixLitre: v.prixLitre,
      aChargeClient: v.aChargeClient,
      observations: v.observations || undefined
    };

    this.service.creer(requete).subscribe({
      next: (depense) => {
        this.enregistrement.set(false);
        this.reinitialiser();
        this.enregistree.emit(depense);
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(reponse.error?.message ?? "Enregistrement de la depense impossible.");
      }
    });
  }

  annuler(): void {
    this.reinitialiser();
    this.annulee.emit();
  }

  private reinitialiser(): void {
    const camionId = this.camionImpose() ?? this.formulaire.getRawValue().camionId;
    this.formulaire.reset({
      camionId,
      categorie: 'CARBURANT',
      sousCategorie: 'DIESEL',
      libelle: '',
      montant: 0,
      dateDepense: this.aujourdhui(),
      fournisseur: '',
      modePaiement: null,
      reference: '',
      kilometrage: null,
      station: '',
      quantiteLitres: null,
      prixLitre: null,
      aChargeClient: false,
      observations: ''
    });
    this.categorieSaisie.set('CARBURANT');
  }

  private aujourdhui(): string {
    const date = new Date();
    const mois = `${date.getMonth() + 1}`.padStart(2, '0');
    const jour = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${mois}-${jour}`;
  }
}
