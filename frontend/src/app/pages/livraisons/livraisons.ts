import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import {
  ClientParc,
  Livraison,
  LivraisonRequete,
  Mission,
  STATUTS_LIVRAISON,
  StatutLivraison
} from '../../core/models/exploitation';
import { Camion, Chauffeur, Page } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { ClientService } from '../../core/services/client.service';
import { LivraisonService } from '../../core/services/livraison.service';
import { MissionService } from '../../core/services/mission.service';
import { Modale } from '../../shared/modale';
import { VALIDATION_FORMULAIRE, messageChampsManquants } from '../../shared/validation-formulaire';

@Component({
  selector: 'app-livraisons',
  imports: [FormsModule, ReactiveFormsModule, DecimalPipe, DatePipe, Modale, VALIDATION_FORMULAIRE],
  templateUrl: './livraisons.html'
})
export class Livraisons implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(LivraisonService);
  private readonly clientService = inject(ClientService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly missionService = inject(MissionService);
  private readonly auth = inject(AuthService);

  readonly statuts = STATUTS_LIVRAISON;
  readonly page = signal<Page<Livraison> | null>(null);
  readonly clients = signal<ClientParc[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly missions = signal<Mission[]>([]);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);
  /** Livraison dont le formulaire de confirmation est ouvert. */
  readonly confirmationEnCours = signal<Livraison | null>(null);

  recherche = '';
  statut: StatutLivraison | '' = '';
  private pageCourante = 0;

  statutConfirmation: StatutLivraison = 'LIVREE';
  signatureNom = '';
  photoUrl = '';
  motifRefus = '';

  readonly peutGerer = this.auth.peutGererParc();
  readonly peutConfirmer = this.auth.peutGererParc();

  readonly formulaire = this.fb.nonNullable.group({
    missionId: [null as number | null],
    clientId: [null as number | null],
    camionId: [null as number | null, [Validators.required]],
    chauffeurId: [null as number | null],
    expediteur: [''],
    destinataire: ['', [Validators.required]],
    telephoneDestinataire: [''],
    marchandise: ['', [Validators.required]],
    quantite: [null as number | null],
    unite: [''],
    poidsKg: [null as number | null],
    adresseLivraison: [''],
    datePrevue: [null as string | null],
    numeroBon: [''],
    observations: ['']
  });

  ngOnInit(): void {
    this.charger();
    this.clientService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.clients.set(page.content)
    });
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.chauffeurService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.chauffeurs.set(page.content)
    });
    this.missionService.lister({ size: 50 }).subscribe({
      next: (page) => this.missions.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({ q: this.recherche, statut: this.statut, page: this.pageCourante })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des livraisons impossible.');
        }
      });
  }

  filtrer(): void {
    this.pageCourante = 0;
    this.charger();
  }

  changerPage(delta: number): void {
    const page = this.page();
    if (!page) {
      return;
    }
    const cible = page.number + delta;
    if (cible < 0 || cible >= page.totalPages) {
      return;
    }
    this.pageCourante = cible;
    this.charger();
  }

  basculerFormulaire(): void {
    this.formulaireVisible.update((visible) => !visible);
    this.erreur.set(null);
    this.succes.set(null);
  }

  enregistrer(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const brut = this.formulaire.getRawValue();
    const requete: LivraisonRequete = {
      ...brut,
      // Le champ est requis par le formulaire : il est renseigne a ce stade.
      camionId: brut.camionId!,
      datePrevue: brut.datePrevue ? new Date(brut.datePrevue).toISOString() : null
    };
    this.service.creer(requete).subscribe({
      next: (livraison) => {
        this.succes.set(`Livraison ${livraison.numero} enregistree.`);
        this.formulaire.reset();
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.')
    });
  }

  ouvrirConfirmation(livraison: Livraison): void {
    this.statutConfirmation = 'LIVREE';
    this.signatureNom = '';
    this.photoUrl = '';
    this.motifRefus = '';
    this.confirmationEnCours.set(livraison);
  }

  confirmer(): void {
    const livraison = this.confirmationEnCours();
    if (!livraison) {
      return;
    }
    const manquants = this.statutConfirmation === 'REFUSEE'
      ? messageChampsManquants({ 'Motif du refus': this.motifRefus })
      : null;
    if (manquants) {
      this.erreur.set(manquants);
      return;
    }
    this.erreur.set(null);
    this.service
      .confirmer(livraison.id, {
        statut: this.statutConfirmation,
        signatureNom: this.signatureNom,
        photoUrl: this.photoUrl,
        motifRefus: this.motifRefus
      })
      .subscribe({
        next: (maj) => {
          this.confirmationEnCours.set(null);
          this.succes.set(`Livraison ${maj.numero} : ${maj.statutLibelle}.`);
          this.charger();
        },
        error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Confirmation impossible.')
      });
  }

  classeStatut(statut: StatutLivraison): string {
    const correspondances: Record<StatutLivraison, string> = {
      PLANIFIEE: 'etiquette',
      EN_COURS: 'etiquette en_mission',
      LIVREE: 'etiquette disponible',
      PARTIELLE: 'etiquette en_location',
      REFUSEE: 'etiquette vendu',
      ANNULEE: 'etiquette vendu'
    };
    return correspondances[statut];
  }
}
