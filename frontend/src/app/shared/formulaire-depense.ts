import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { map } from 'rxjs';
import {
  CATEGORIES_DEPENSE,
  CategorieCharge,
  CategorieDepense,
  Depense,
  DepenseRequete,
  FournisseurParc,
  MODES_PAIEMENT
} from '../core/models/finance';
import { Camion } from '../core/models/models';
import { AffectationService } from '../core/services/affectation.service';
import { CamionService } from '../core/services/camion.service';
import { ChargesParcService } from '../core/services/charges-parc.service';
import { DepenseService, NaturesParCategorie } from '../core/services/depense.service';
import { OptionSelect, SelectRecherche } from './select-recherche';
import { VALIDATION_FORMULAIRE } from './validation-formulaire';

type TypeDepense = 'VEHICULE' | 'GENERALE';

/**
 * Formulaire d'enregistrement d'une depense, partage par l'ecran Depenses et la
 * fiche camion.
 * <p>
 * Depense d'un vehicule (categorie, nature, plein de carburant) ou charge
 * generale de l'entreprise (sans vehicule, avec sa categorie modifiable). Le
 * chauffeur n'est jamais saisi : il decoule de l'affectation en cours du
 * camion. Une depense a payer devient une dette envers son fournisseur.
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
  private readonly charges = inject(ChargesParcService);
  private readonly camionService = inject(CamionService);
  private readonly affectationService = inject(AffectationService);

  /** Camion impose par l'ecran appelant ; le champ devient alors non modifiable. */
  readonly camionImpose = input<number | null>(null);

  readonly enregistree = output<Depense>();
  readonly annulee = output<void>();

  readonly categories = CATEGORIES_DEPENSE.filter((c) => c.valeur !== 'CHARGE_GENERALE');
  readonly modes = MODES_PAIEMENT;

  readonly type = signal<TypeDepense>('VEHICULE');
  readonly camions = signal<Camion[]>([]);
  readonly natures = signal<NaturesParCategorie | null>(null);
  readonly categoriesCharge = signal<CategorieCharge[]>([]);
  readonly fournisseurs = signal<FournisseurParc[]>([]);
  readonly categorieSaisie = signal<CategorieDepense>('CARBURANT');
  /** Chauffeur conduisant le camion choisi ; null s'il n'est affecte a personne. */
  readonly chauffeur = signal<string | null>(null);
  readonly chauffeurId = signal<number | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  fichier: File | null = null;

  readonly naturesDisponibles = computed(() => this.natures()?.[this.categorieSaisie()] ?? []);

  /** Vrai si la categorie exige les informations de plein (chapitre 18). */
  readonly saisieCarburant = computed(() => this.type() === 'VEHICULE' && this.categorieSaisie() === 'CARBURANT');

  readonly optionsCamion = computed<OptionSelect[]>(() =>
    this.camions().map((camion) => ({
      valeur: camion.id,
      libelle: camion.immatriculation,
      detail: [camion.marque, camion.modele].filter(Boolean).join(' ')
    }))
  );

  readonly optionsFournisseur = computed<OptionSelect[]>(() =>
    this.fournisseurs().filter((f) => f.actif).map((f) => ({ valeur: f.id, libelle: f.nom, detail: f.activite ?? '' })));

  /** Immatriculation du camion impose, affichee a la place de la liste. */
  readonly camionLibelle = computed(() => {
    const camion = this.camions().find((c) => c.id === this.camionImpose());
    return camion
      ? `${camion.immatriculation} — ${camion.marque ?? ''} ${camion.modele ?? ''}`.trim()
      : '';
  });

  readonly formulaire = this.fb.nonNullable.group({
    camionId: [null as number | null],
    categorie: ['CARBURANT' as CategorieDepense],
    sousCategorie: ['DIESEL'],
    categorieChargeId: [null as number | null],
    libelle: ['', [Validators.required]],
    montant: [0, [Validators.required, Validators.min(0)]],
    dateDepense: [this.aujourdhui(), [Validators.required]],
    fournisseurId: [null as number | null],
    nouveauFournisseur: [''],
    modePaiement: [null as string | null],
    reference: [''],
    kilometrage: [null as number | null],
    station: [''],
    quantiteLitres: [null as number | null],
    prixLitre: [null as number | null],
    aChargeClient: [false],
    aPayer: [false],
    echeance: [''],
    acompte: [null as number | null],
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

  readonly aPayer = computed(() => this.valeurs().aPayer);

  /** Delai du fournisseur choisi, pour proposer l'echeance. */
  readonly delaiFournisseur = computed(() =>
    this.fournisseurs().find((f) => f.id === this.valeurs().fournisseurId)?.delaiPaiementJours ?? 0);

  ngOnInit(): void {
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.service.natures().subscribe({
      next: (natures) => this.natures.set(natures)
    });
    this.charges.categories().subscribe({ next: (c) => this.categoriesCharge.set(c.filter((x) => x.actif)) });
    this.charges.fournisseurs().subscribe({ next: (f) => this.fournisseurs.set(f) });

    const c = this.formulaire.controls;
    c.camionId.valueChanges.subscribe((id) => this.camionChange(id));
    c.categorie.valueChanges.subscribe((categorie) => this.categorieChange(categorie));

    const impose = this.camionImpose();
    if (impose !== null) {
      this.formulaire.patchValue({ camionId: impose });
    }
  }

  changerType(type: TypeDepense): void {
    this.type.set(type);
    this.erreur.set(null);
    if (type === 'GENERALE') {
      this.formulaire.patchValue({ camionId: null, station: '', quantiteLitres: null, prixLitre: null,
        kilometrage: null, aChargeClient: false });
      if (!this.formulaire.controls.categorieChargeId.value) {
        this.formulaire.controls.categorieChargeId.setValue(this.categoriesCharge()[0]?.id ?? null);
      }
    } else {
      this.formulaire.patchValue({ categorieChargeId: null });
    }
  }

  choisirFichier(evenement: Event): void {
    this.fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
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
    const v = this.formulaire.getRawValue();
    const generale = this.type() === 'GENERALE';
    if (!generale && v.camionId === null) {
      this.erreur.set('Choisissez le vehicule, ou passez en « Charge generale ».');
      return;
    }
    if (generale && !v.categorieChargeId) {
      this.erreur.set('Choisissez la categorie de la charge generale.');
      return;
    }
    if (this.saisieCarburant()) {
      if (!v.quantiteLitres || !v.prixLitre) {
        this.erreur.set('Renseignez la quantite et le prix au litre : ils donnent le montant du plein.');
        return;
      }
      if (v.kilometrage === null) {
        this.erreur.set('Le releve du compteur est obligatoire pour un plein.');
        return;
      }
    }
    const nouveau = v.nouveauFournisseur.trim();
    if (v.aPayer && !v.fournisseurId && !nouveau) {
      this.erreur.set('Une depense a payer est une dette : choisissez le fournisseur.');
      return;
    }
    const montant = this.saisieCarburant() ? this.montantCarburant() : v.montant;
    if (v.aPayer && v.acompte && v.acompte > montant) {
      this.erreur.set("L'acompte depasse le montant de la depense.");
      return;
    }
    this.enregistrement.set(true);
    this.erreur.set(null);

    const requete: DepenseRequete = {
      camionId: generale ? null : v.camionId,
      chauffeurId: generale ? null : this.chauffeurId(),
      sousCategorie: generale ? null : v.sousCategorie,
      categorieChargeId: generale ? v.categorieChargeId : null,
      libelle: v.libelle,
      // Pour un plein, le montant est calcule : quantite x prix au litre.
      montant,
      dateDepense: v.dateDepense,
      fournisseurId: v.fournisseurId,
      fournisseur: v.fournisseurId ? undefined : nouveau || undefined,
      modePaiement: (v.modePaiement || null) as DepenseRequete['modePaiement'],
      reference: v.reference || undefined,
      kilometrage: generale ? null : v.kilometrage,
      station: v.station || undefined,
      quantiteLitres: v.quantiteLitres,
      prixLitre: v.prixLitre,
      aChargeClient: !generale && v.aChargeClient,
      aPayer: v.aPayer,
      echeance: v.aPayer && v.echeance ? v.echeance : null,
      acompte: v.aPayer ? v.acompte : null,
      observations: v.observations || undefined
    };

    this.service.creer(requete).subscribe({
      next: (depense) => {
        const fin = (d: Depense) => {
          this.enregistrement.set(false);
          this.reinitialiser();
          this.enregistree.emit(d);
        };
        if (this.fichier) {
          // La depense est enregistree : un justificatif refuse n'empeche pas de la garder.
          this.charges.joindre(depense.id, this.fichier).subscribe({ next: fin, error: () => fin(depense) });
        } else {
          fin(depense);
        }
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
      camionId: this.type() === 'GENERALE' ? null : camionId,
      categorie: 'CARBURANT',
      sousCategorie: 'DIESEL',
      categorieChargeId: this.type() === 'GENERALE' ? this.formulaire.getRawValue().categorieChargeId : null,
      libelle: '',
      montant: 0,
      dateDepense: this.aujourdhui(),
      fournisseurId: null,
      nouveauFournisseur: '',
      modePaiement: null,
      reference: '',
      kilometrage: null,
      station: '',
      quantiteLitres: null,
      prixLitre: null,
      aChargeClient: false,
      aPayer: false,
      echeance: '',
      acompte: null,
      observations: ''
    });
    this.fichier = null;
    this.categorieSaisie.set('CARBURANT');
  }

  private aujourdhui(): string {
    const date = new Date();
    const mois = `${date.getMonth() + 1}`.padStart(2, '0');
    const jour = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${mois}-${jour}`;
  }
}
