import { Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  Camion,
  CamionRequete,
  MarqueCamion,
  Referentiel,
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
@Component({
  selector: 'app-formulaire-camion',
  imports: [ReactiveFormsModule, SelectRecherche, Modale, VALIDATION_FORMULAIRE],
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
    this.types().map((type) => ({ valeur: type.id, libelle: type.libelle, detail: type.complement }))
  );
  readonly optionsCouleur = computed<OptionSelect[]>(() =>
    this.couleurs().map((couleur) => ({
      valeur: couleur.id,
      libelle: couleur.libelle,
      couleur: couleur.complement
    }))
  );

  readonly formulaire = this.fb.nonNullable.group({
    immatriculation: ['', [Validators.required, Validators.maxLength(40)]],
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
    this.service.types().subscribe({ next: (types) => this.types.set(types) });
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
    this.enregistrement.set(true);
    this.erreur.set(null);
    this.service.creer(this.formulaire.getRawValue() as unknown as CamionRequete).subscribe({
      next: (camion) => {
        this.enregistrement.set(false);
        this.enregistre.emit(camion);
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
