import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CamionRequete,
  MarqueCamion,
  Odometre,
  Referentiel,
  CATEGORIES_VEHICULE,
  CategorieVehicule,
  marquesDeFamille,
  typesDeFamille,
  STATUTS_CAMION,
  TYPES_CARBURANT,
  LIBELLES_SOURCE_RELEVE
} from '../../core/models/models';
import { OptionSelect, SelectRecherche } from '../../shared/select-recherche';
import { Depense, Recette } from '../../core/models/finance';
import { CamionService } from '../../core/services/camion.service';
import { DepenseService } from '../../core/services/depense.service';
import { RecetteService } from '../../core/services/recette.service';
import { FormulaireDepense } from '../../shared/formulaire-depense';
import { FormulaireRecette } from '../../shared/formulaire-recette';
import { CarteCamion } from '../../shared/carte-camion';
import { Modale } from '../../shared/modale';
import { VALIDATION_FORMULAIRE } from '../../shared/validation-formulaire';

import { MarketplaceService } from '../../core/services/marketplace.service';
import { ReglageLocationEnLigne } from '../../core/models/marketplace';
import { AuthService } from '../../core/auth/auth.service';
import { PhotoVehicule } from '../../shared/photo-vehicule';
import { GaleriePhotos } from '../../shared/galerie-photos';
@Component({
  selector: 'app-camion-fiche',
  imports: [PhotoVehicule, GaleriePhotos, ReactiveFormsModule, FormsModule, RouterLink, DecimalPipe, SelectRecherche, FormulaireRecette, FormulaireDepense, CarteCamion, Modale, VALIDATION_FORMULAIRE],
  templateUrl: './camion-fiche.html'
})
export class CamionFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CamionService);
  private readonly recetteService = inject(RecetteService);
  private readonly depenseService = inject(DepenseService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly statuts = STATUTS_CAMION;
  readonly carburants = TYPES_CARBURANT;

  readonly id = signal<number | null>(null);
  // Gestion de parc avec Marketplace : la location en ligne se regle dans la fiche du vehicule.
  private readonly marketplace = inject(MarketplaceService);
  readonly avecEnLigne = inject(AuthService).aModule('MARKETPLACE');
  enLigne: ReglageLocationEnLigne = { publiee: false, tarifJour: null, caution: null, photos: [] };
  private enLigneInitial = JSON.stringify(this.enLigne);

  /** Location en ligne a enregistrer avec la fiche : renvoie un message si elle est incomplete. */
  private controleEnLigne(): string | null {
    return this.avecEnLigne && this.enLigne.publiee && !this.enLigne.tarifJour
      ? 'Indiquez le tarif par jour de la location en ligne.' : null;
  }

  /** Apres l'enregistrement de la fiche : publie, met a jour ou retire la location en ligne si elle a change. */
  private enregistrerEnLigne(camionId: number, suite: () => void): void {
    if (!this.avecEnLigne || JSON.stringify(this.enLigne) === this.enLigneInitial) {
      suite();
      return;
    }
    const r = this.enLigne;
    // Les autres photos ne sont envoyees que si elles ont change (null = galerie inchangee).
    const photosChangees = JSON.stringify(r.photos) !== JSON.stringify(JSON.parse(this.enLigneInitial).photos);
    this.marketplace.publierVehicule(camionId, r.publiee, r.tarifJour, r.caution,
      r.publiee && photosChangees ? r.photos : undefined).subscribe({
      next: () => {
        this.enLigneInitial = JSON.stringify(this.enLigne);
        suite();
      },
      error: (e) => this.erreur.set(e.error?.message ?? 'Vehicule enregistre, mais la location en ligne n\'a pas pu etre modifiee.')
    });
  }

  readonly marques = signal<MarqueCamion[]>([]);
  readonly types = signal<Referentiel[]>([]);
  readonly couleurs = signal<Referentiel[]>([]);
  readonly odometre = signal<Odometre | null>(null);

  /** Options des listes deroulantes avec recherche. */
  /** Famille choisie (voiture, utilitaire, camion, engin) : elle filtre les types et les marques. */
  readonly famille = signal<CategorieVehicule | null>(null);
  readonly familles = CATEGORIES_VEHICULE;

  choisirFamille(valeur: string): void {
    const famille = (valeur || null) as CategorieVehicule | null;
    this.famille.set(famille);
    // Un type ou une marque hors de la nouvelle famille est retire.
    const { typeId, marqueId } = this.formulaire.controls;
    if (typeId.value !== null && !typesDeFamille(this.types(), famille).some((t) => t.id === typeId.value)) {
      typeId.setValue(null);
    }
    if (marqueId.value !== null && !marquesDeFamille(this.marques(), famille).some((m) => m.id === marqueId.value)) {
      marqueId.setValue(null);
    }
  }

  /** Un type choisi sans famille fixe la famille. */
  private suivreType(typeId: number | null): void {
    const type = this.types().find((t) => t.id === typeId);
    if (type?.categorie && !this.famille()) {
      this.famille.set(type.categorie);
    }
  }

  readonly optionsMarque = computed<OptionSelect[]>(() =>
    marquesDeFamille(this.marques(), this.famille()).map((marque) => ({
      valeur: marque.id,
      libelle: marque.nom,
      detail: marque.pays,
      couleur: marque.couleur,
      monogramme: marque.monogramme,
      logoUrl: marque.logoUrl
    }))
  );
  readonly optionsType = computed<OptionSelect[]>(() =>
    typesDeFamille(this.types(), this.famille()).map((type) => ({
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
  /** Onglet ouvert sous la fiche : recettes, depenses ou releves du camion. */
  readonly volet = signal<Volet>('fiche');

  /** Volets de la fiche. */
  readonly volets: { id: Volet; libelle: string; icone: string }[] = [
    { id: 'fiche', libelle: 'Fiche du vehicule', icone: '📋' },
    { id: 'recettes', libelle: 'Recettes', icone: '💰' },
    { id: 'depenses', libelle: 'Depenses', icone: '💸' },
    { id: 'odometre', libelle: 'Odometre', icone: '⏱️' },
    { id: 'carte', libelle: 'Carte', icone: '📍' }
  ];
  readonly recettes = signal<Recette[]>([]);
  readonly depenses = signal<Depense[]>([]);
  /** Formulaires de saisie, ouverts a la demande. */
  readonly saisieRecette = signal(false);
  readonly saisieDepense = signal(false);
  readonly totalRecettes = computed(() =>
    this.recettes().reduce((cumul, recette) => cumul + recette.montant, 0)
  );
  readonly totalDepenses = computed(() =>
    this.depenses().reduce((cumul, depense) => cumul + depense.montant, 0)
  );
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly formulaire = this.fb.nonNullable.group({
    immatriculation: ['', [Validators.required, Validators.maxLength(40)]],
    /** Photo encodee (data URL) ; elle remplace le dessin du type sur la carte du vehicule. */
    photoUrl: [null as string | null],
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
    return this.id() === null ? 'Nouveau vehicule' : 'Fiche du vehicule';
  }

  ngOnInit(): void {
    this.chargerEnLigne();
    this.service.marques().subscribe({ next: (marques) => this.marques.set(marques) });
    this.service.types().subscribe({
      next: (types) => {
        this.types.set(types);
        this.suivreType(this.formulaire.controls.typeId.value);
      }
    });
    this.formulaire.controls.typeId.valueChanges.subscribe((id) => this.suivreType(id));
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
          this.famille.set(camion.typeCategorie ?? null);
          this.formulaire.patchValue(camion as never);
          this.marqueSelectionnee.set(camion.marqueId ?? null);
        },
        error: () => this.erreur.set('Vehicule introuvable.')
      });
    }
  }

  /** Ouvre un volet et charge ses donnees a la demande. */
  ouvrirVolet(volet: Volet): void {
    this.volet.set(volet);
    const id = this.id();
    if (id === null) {
      return;
    }
    if (volet === 'recettes' && this.recettes().length === 0) {
      this.chargerRecettes(id);
    }
    if (volet === 'depenses' && this.depenses().length === 0) {
      this.chargerDepenses(id);
    }
    if (volet === 'odometre' && this.odometre() === null) {
      this.service.odometre(id).subscribe({
        next: (odometre) => this.odometre.set(odometre)
      });
    }
  }

  /** Nombre de lignes d'un volet deja charge, rappele sur son onglet. */
  compte(volet: Volet): number | null {
    if (volet === 'recettes') {
      return this.recettes().length || null;
    }
    if (volet === 'depenses') {
      return this.depenses().length || null;
    }
    if (volet === 'odometre') {
      return this.odometre()?.nombreReleves || null;
    }
    return null;
  }

  /** Toutes les recettes du camion, quel que soit leur type. */
  private chargerRecettes(camionId: number): void {
    this.recetteService.lister({ camionId, size: 200 }).subscribe({
      next: (page) => this.recettes.set(page.content)
    });
  }

  /** Recharge la liste apres une saisie et referme le formulaire. */
  /** Derniere recette enregistree : son contrat est propose a l'impression. */
  readonly recetteAImprimer = signal<Recette | null>(null);

  imprimerContrat(recette: Recette): void {
    this.recetteService.ouvrirContrat(recette, (message) => this.erreur.set(message));
  }

  recetteEnregistree(recette?: Recette): void {
    this.saisieRecette.set(false);
    const id = this.id();
    if (id !== null) {
      this.chargerRecettes(id);
      this.succes.set('Recette enregistree.');
      this.recetteAImprimer.set(recette ?? null);
    }
  }

  private chargerDepenses(camionId: number): void {
    this.depenseService.lister({ camionId, size: 200 }).subscribe({
      next: (page) => this.depenses.set(page.content)
    });
  }

  /** Recharge la liste apres une saisie et referme le formulaire. */
  depenseEnregistree(): void {
    this.saisieDepense.set(false);
    const id = this.id();
    if (id !== null) {
      this.chargerDepenses(id);
      this.succes.set('Depense enregistree.');
    }
  }

  /** Libelle lisible de l'origine d'un releve. */
  libelleSource(source: string): string {
    return LIBELLES_SOURCE_RELEVE[source as keyof typeof LIBELLES_SOURCE_RELEVE] ?? source;
  }

  /** Location en ligne actuelle du vehicule ouvert. */
  private chargerEnLigne(): void {
    const parametre = this.route.snapshot.paramMap.get('id');
    const id = parametre && parametre !== 'nouveau' ? Number(parametre) : null;
    if (!this.avecEnLigne || id === null) {
      return;
    }
    this.marketplace.vehiculesEnLigne().subscribe({
      next: (liste) => {
        const v = liste.find((x) => x.camionId === id);
        if (v) {
          this.enLigne = { publiee: v.publiee, tarifJour: v.tarifJour ?? null, caution: v.caution ?? null, photos: [] };
          this.enLigneInitial = JSON.stringify(this.enLigne);
          this.marketplace.photosVehicule(id).subscribe({
            next: (r) => {
              this.enLigne = { ...this.enLigne, photos: r.photos };
              this.enLigneInitial = JSON.stringify({ ...JSON.parse(this.enLigneInitial), photos: r.photos });
            }
          });
        }
      }
    });
  }

  soumettre(): void {
    if (this.formulaire.invalid || this.enregistrement()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const incomplet = this.controleEnLigne();
    if (incomplet) {
      this.erreur.set(incomplet);
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
        this.enregistrerEnLigne(camion.id, () => {
          if (id === null) {
            void this.router.navigate(['/camions', camion.id]);
          } else {
            this.succes.set('Fiche enregistree.');
          }
        });
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? "Enregistrement impossible.");
      }
    });
  }
}

type Volet = 'fiche' | 'recettes' | 'depenses' | 'odometre' | 'carte';
