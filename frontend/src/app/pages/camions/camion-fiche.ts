import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CamionRequete, STATUTS_CAMION, TYPES_CARBURANT } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';

@Component({
  selector: 'app-camion-fiche',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './camion-fiche.html'
})
export class CamionFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CamionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly statuts = STATUTS_CAMION;
  readonly carburants = TYPES_CARBURANT;

  readonly id = signal<number | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly formulaire = this.fb.nonNullable.group({
    numeroInterne: ['', [Validators.required, Validators.maxLength(40)]],
    immatriculation: ['', [Validators.required, Validators.maxLength(40)]],
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

  get titre(): string {
    return this.id() === null ? 'Nouveau camion' : 'Fiche du camion';
  }

  ngOnInit(): void {
    const parametre = this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      const id = Number(parametre);
      this.id.set(id);
      this.service.parId(id).subscribe({
        next: (camion) => this.formulaire.patchValue(camion as never),
        error: () => this.erreur.set('Camion introuvable.')
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
