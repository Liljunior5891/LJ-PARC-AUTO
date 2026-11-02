import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  Camion,
  CamionRequete,
  MarqueCamion,
  Referentiel,
  CATEGORIES_VEHICULE,
  CategorieVehicule,
  marquesDeFamille,
  typesDeFamille,
  STATUTS_CAMION,
  TYPES_CARBURANT
} from '../core/models/models';
import { CamionService } from '../core/services/camion.service';
import { Modale } from './modale';
import { OptionSelect, SelectRecherche } from './select-recherche';
import { VALIDATION_FORMULAIRE } from './validation-formulaire';

/**
 * Creation d'un camion dans une fenetre posee par-dessus la vue du parc.
 * <p>
 * On reprend les champs de la fiche camion, sans l'archivage : un camion qui
 * entre au parc est forcement actif. La fiche complete reste accessible ensuite
 * depuis sa carte.
 */
import { MarketplaceService } from '../core/services/marketplace.service';
import { ReglageLocationEnLigne } from '../core/models/marketplace';
import { AuthService } from '../core/auth/auth.service';
import { PhotoVehicule } from './photo-vehicule';
import { GaleriePhotos } from './galerie-photos';
@Component({
  selector: 'app-formulaire-camion',
  imports: [PhotoVehicule, GaleriePhotos, ReactiveFormsModule, FormsModule, SelectRecherche, Modale, VALIDATION_FORMULAIRE],
  templateUrl: './formulaire-camion.html',
  styleUrl: './formulaire-camion.scss'
})
export class FormulaireCamion implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CamionService);

  readonly enregistre = output<Camion>();
  readonly ferme = output<void>();

  readonly statuts = STATUTS_CAMION;
  readonly carburants = TYPES_CARBURANT;

  readonly marques = signal<MarqueCamion[]>([]);
  readonly types = signal<Referentiel[]>([]);
  readonly couleurs = signal<Referentiel[]>([]);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);

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
    typesDeFamille(this.types(), this.famille()).map((type) => ({ valeur: type.id, libelle: type.libelle, detail: type.complement }))
  );
  readonly optionsCouleur = computed<OptionSelect[]>(() =>
    this.couleurs().map((couleur) => ({
      valeur: couleur.id,
      libelle: couleur.libelle,
      couleur: couleur.complement
    }))
  );

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

  readonly formulaire = this.fb.nonNullable.group({
    immatriculation: ['', [Validators.required, Validators.maxLength(40)]],
    /** Photo encodee (data URL) ; elle remplace le dessin du type sur la carte du vehicule. */
    photoUrl: [null as string | null],
    marqueId: [null as number | null],
    modele: [''],
    annee: [null as number | null],
    typeId: [null as number | null],
    categorie: [''],
    couleurId: [null as number | null],
    numeroChassis: [''],
    numeroMoteur: [''],
    capaciteTonnes: [null as number | null],
    typeCarburant: ['DIESEL'],
    gpsDeviceId: [''],
    dateAcquisition: [null as string | null],
    prixAchat: [null as number | null],
    kilometrageInitial: [0],
    kilometrageActuel: [0],
    statut: ['DISPONIBLE'],
    observations: [''],
    actif: [true]
  });

  ngOnInit(): void {
    this.service.marques().subscribe({ next: (marques) => this.marques.set(marques) });
    this.service.types().subscribe({
      next: (types) => {
        this.types.set(types);
        this.suivreType(this.formulaire.controls.typeId.value);
      }
    });
    this.formulaire.controls.typeId.valueChanges.subscribe((id) => this.suivreType(id));
    this.service.couleurs().subscribe({ next: (couleurs) => this.couleurs.set(couleurs) });
    // Le compteur actuel part du compteur a l'achat tant qu'on ne le touche pas.
    this.formulaire.controls.kilometrageInitial.valueChanges.subscribe((initial) => {
      const actuel = this.formulaire.controls.kilometrageActuel;
      if (!actuel.dirty) {
        actuel.setValue(initial ?? 0);
      }
    });
  }

  fermer(): void {
    if (!this.enregistrement()) {
      this.ferme.emit();
    }
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
    this.service.creer(this.formulaire.getRawValue() as unknown as CamionRequete).subscribe({
      next: (camion) => {
        this.enregistrement.set(false);
        this.enregistrerEnLigne(camion.id, () => this.enregistre.emit(camion));
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
