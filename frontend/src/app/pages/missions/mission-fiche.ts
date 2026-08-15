import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ClientParc,
  Location,
  Mission,
  MissionRequete,
  TYPES_MISSION,
  TypeMission
} from '../../core/models/exploitation';
import { Camion, Chauffeur } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { ClientService } from '../../core/services/client.service';
import { LocationService } from '../../core/services/location.service';
import { MissionService } from '../../core/services/mission.service';

@Component({
  selector: 'app-mission-fiche',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './mission-fiche.html'
})
export class MissionFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MissionService);
  private readonly clientService = inject(ClientService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly locationService = inject(LocationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly types = TYPES_MISSION;
  readonly id = signal<number | null>(null);
  readonly mission = signal<Mission | null>(null);
  readonly clients = signal<ClientParc[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly contrats = signal<Location[]>([]);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly formulaire = this.fb.nonNullable.group({
    clientId: [null as number | null],
    locationId: [null as number | null],
    camionId: [null as number | null, [Validators.required]],
    chauffeurId: [null as number | null],
    typeMission: ['TRANSPORT' as TypeMission, [Validators.required]],
    lieuDepart: [''],
    destination: [''],
    dateDepart: [this.maintenantLocal(), [Validators.required]],
    recettePrevue: [null as number | null],
    marchandise: [''],
    observations: ['']
  });

  get titre(): string {
    return this.id() === null ? 'Nouvelle mission' : 'Mission';
  }

  get modifiable(): boolean {
    const statut = this.mission()?.statut;
    return this.id() === null || statut === 'PLANIFIEE' || statut === 'EN_COURS';
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
    this.locationService.actives().subscribe({
      next: (contrats) => this.contrats.set(contrats)
    });

    const parametre = this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouvelle') {
      const id = Number(parametre);
      this.id.set(id);
      this.service.parId(id).subscribe({
        next: (mission) => {
          this.mission.set(mission);
          this.formulaire.patchValue({
            clientId: mission.clientId ?? null,
            locationId: mission.locationId ?? null,
            camionId: mission.camionId,
            chauffeurId: mission.chauffeurId ?? null,
            typeMission: mission.typeMission,
            lieuDepart: mission.lieuDepart ?? '',
            destination: mission.destination ?? '',
            dateDepart: mission.dateDepart.slice(0, 16),
            recettePrevue: mission.recettePrevue ?? null,
            marchandise: mission.marchandise ?? '',
            observations: mission.observations ?? ''
          });
          if (!this.modifiable) {
            this.formulaire.disable({ emitEvent: false });
          }
        },
        error: () => this.erreur.set('Mission introuvable.')
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

    const brut = this.formulaire.getRawValue();
    const requete: MissionRequete = {
      ...brut,
      // Le champ est requis par le formulaire : il est renseigne a ce stade.
      camionId: brut.camionId!,
      // L'API attend un instant ISO ; l'input datetime-local fournit une heure locale.
      dateDepart: new Date(brut.dateDepart).toISOString()
    };

    const id = this.id();
    const appel = id === null ? this.service.creer(requete) : this.service.modifier(id, requete);
    appel.subscribe({
      next: (mission) => {
        this.enregistrement.set(false);
        if (id === null) {
          void this.router.navigate(['/missions', mission.id]);
        } else {
          this.mission.set(mission);
          this.succes.set('Mission enregistree.');
        }
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  private maintenantLocal(): string {
    const maintenant = new Date();
    maintenant.setMinutes(maintenant.getMinutes() - maintenant.getTimezoneOffset());
    return maintenant.toISOString().slice(0, 16);
  }
}
