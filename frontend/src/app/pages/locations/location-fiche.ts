import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  ClientParc,
  Location,
  LocationRequete,
  TYPES_LOCATION,
  TypeLocation
} from '../../core/models/exploitation';
import { Camion, Chauffeur } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { ClientService } from '../../core/services/client.service';
import { LocationService } from '../../core/services/location.service';

@Component({
  selector: 'app-location-fiche',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, DecimalPipe],
  templateUrl: './location-fiche.html'
})
export class LocationFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(LocationService);
  private readonly clientService = inject(ClientService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly types = TYPES_LOCATION;
  readonly id = signal<number | null>(null);
  readonly contrat = signal<Location | null>(null);
  readonly clients = signal<ClientParc[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly clotureVisible = signal(false);

  dateFinReelle = new Date().toISOString().slice(0, 10);
  kilometrageReel: number | null = null;
  observationsCloture = '';

  readonly peutGerer = this.auth.peutGererParc();

  readonly formulaire = this.fb.nonNullable.group({
    clientId: [null as number | null, [Validators.required]],
    camionId: [null as number | null, [Validators.required]],
    chauffeurId: [null as number | null],
    typeLocation: ['JOURNALIERE' as TypeLocation, [Validators.required]],
    dateDebut: [new Date().toISOString().slice(0, 10), [Validators.required]],
    dateFin: [null as string | null],
    tarif: [0, [Validators.required, Validators.min(0)]],
    lieuDepart: [''],
    destination: [''],
    kilometragePrevu: [null as number | null],
    caution: [null as number | null],
    conditions: [''],
    depensesChargeClient: [''],
    depensesChargeEntreprise: [''],
    observations: ['']
  });

  /** Unite du tarif, dependante du modele choisi (chapitre 12). */
  readonly uniteTarif = computed(() => {
    const type = this.formulaire.controls.typeLocation.value;
    return this.types.find((t) => t.valeur === type)?.unite ?? 'FCFA';
  });

  get titre(): string {
    return this.id() === null ? 'Nouveau contrat de location' : 'Contrat de location';
  }

  get modifiable(): boolean {
    const statut = this.contrat()?.statut;
    return this.id() === null || statut === 'BROUILLON' || statut === 'EN_ATTENTE';
  }

  ngOnInit(): void {
    this.clientService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.clients.set(page.content)
    });
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.chauffeurService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.chauffeurs.set(page.content)
    });

    const parametre = this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      this.id.set(Number(parametre));
      this.recharger();
      return;
    }
    // Creation depuis la fiche d'un camion : celui-ci est deja choisi.
    const camionId = this.route.snapshot.queryParamMap.get('camionId');
    if (camionId) {
      this.formulaire.controls.camionId.setValue(Number(camionId));
    }
  }

  recharger(): void {
    const id = this.id();
    if (id === null) {
      return;
    }
    this.service.parId(id).subscribe({
      next: (contrat) => {
        this.contrat.set(contrat);
        this.formulaire.patchValue(contrat as never);
        this.kilometrageReel = contrat.kilometrageReel ?? contrat.kilometragePrevu ?? null;
        if (!this.modifiable) {
          this.formulaire.disable({ emitEvent: false });
        }
      },
      error: () => this.erreur.set('Contrat introuvable.')
    });
  }

  soumettre(): void {
    if (this.formulaire.invalid || this.enregistrement()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.enregistrement.set(true);
    this.erreur.set(null);
    this.succes.set(null);

    const valeur = this.formulaire.getRawValue() as unknown as LocationRequete;
    const id = this.id();
    const requete = id === null ? this.service.creer(valeur) : this.service.modifier(id, valeur);

    requete.subscribe({
      next: (contrat) => {
        this.enregistrement.set(false);
        if (id === null) {
          void this.router.navigate(['/locations', contrat.id]);
        } else {
          this.contrat.set(contrat);
          this.succes.set('Contrat enregistre.');
        }
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  activer(): void {
    const id = this.id();
    if (id === null) {
      return;
    }
    this.erreur.set(null);
    this.service.activer(id).subscribe({
      next: (contrat) => {
        this.contrat.set(contrat);
        this.succes.set('Contrat active : le camion est engage aupres du client.');
        this.recharger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Activation impossible.')
    });
  }

  cloturer(): void {
    const id = this.id();
    if (id === null) {
      return;
    }
    this.erreur.set(null);
    this.service
      .cloturer(id, this.dateFinReelle, this.kilometrageReel, this.observationsCloture)
      .subscribe({
        next: (contrat) => {
          this.contrat.set(contrat);
          this.clotureVisible.set(false);
          this.succes.set('Contrat cloture : le camion est libere.');
          this.recharger();
        },
        error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Cloture impossible.')
      });
  }

  annuler(): void {
    const id = this.id();
    if (id === null || !confirm('Annuler definitivement ce contrat ?')) {
      return;
    }
    this.service.annuler(id).subscribe({
      next: () => {
        this.succes.set('Contrat annule.');
        this.recharger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Annulation impossible.')
    });
  }
}
