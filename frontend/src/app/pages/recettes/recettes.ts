import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { ClientParc } from '../../core/models/exploitation';
import {
  LigneTypeRecette,
  MODES_PAIEMENT,
  Recette,
  RecetteRequete,
  TYPES_RECETTE,
  TypeRecette
} from '../../core/models/finance';
import { Camion, Page } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ClientService } from '../../core/services/client.service';
import { RecetteService } from '../../core/services/recette.service';

@Component({
  selector: 'app-recettes',
  imports: [FormsModule, ReactiveFormsModule, DecimalPipe],
  templateUrl: './recettes.html'
})
export class Recettes implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecetteService);
  private readonly camionService = inject(CamionService);
  private readonly clientService = inject(ClientService);
  private readonly auth = inject(AuthService);

  readonly types = TYPES_RECETTE;
  readonly modes = MODES_PAIEMENT;

  readonly page = signal<Page<Recette> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly clients = signal<ClientParc[]>([]);
  readonly repartition = signal<LigneTypeRecette[]>([]);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);

  /** Total encaisse sur la periode consultee. */
  readonly totalPeriode = computed(() =>
    this.repartition().reduce((cumul, ligne) => cumul + ligne.total, 0)
  );

  recherche = '';
  type: TypeRecette | '' = '';
  camionId?: number;
  debut = this.premierJourDuMois();
  fin = this.aujourdhui();
  private pageCourante = 0;

  readonly peutGerer = this.auth.aRole('ADMIN', 'COMPTABLE', 'LOGISTIQUE');

  readonly formulaire = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    clientId: [null as number | null],
    type: ['TRANSPORT' as TypeRecette, [Validators.required]],
    libelle: ['', [Validators.required]],
    montant: [0, [Validators.required, Validators.min(0)]],
    dateRecette: [this.aujourdhui(), [Validators.required]],
    modePaiement: [null as string | null],
    reference: [''],
    observations: ['']
  });

  ngOnInit(): void {
    this.charger();
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.clientService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.clients.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche,
        type: this.type,
        camionId: this.camionId,
        debut: this.debut,
        fin: this.fin,
        page: this.pageCourante
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des recettes impossible.');
        }
      });
    this.service.repartition(this.debut, this.fin, this.camionId).subscribe({
      next: (lignes) => this.repartition.set(lignes)
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
    const requete: RecetteRequete = {
      ...brut,
      camionId: brut.camionId!,
      modePaiement: (brut.modePaiement || null) as RecetteRequete['modePaiement']
    };
    this.service.creer(requete).subscribe({
      next: (recette) => {
        this.succes.set(`Recette ${recette.numero} enregistree.`);
        this.formulaire.reset({ type: 'TRANSPORT', dateRecette: this.aujourdhui(), montant: 0 });
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.')
    });
  }

  supprimer(recette: Recette): void {
    if (!confirm(`Supprimer la recette ${recette.numero} de ${recette.montant} FCFA ?`)) {
      return;
    }
    this.service.supprimer(recette.id).subscribe({
      next: () => this.charger(),
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Suppression impossible.')
    });
  }

  /** Part d'un type dans le total de la periode, pour la barre de repartition. */
  part(ligne: LigneTypeRecette): number {
    const total = this.totalPeriode();
    return total === 0 ? 0 : Math.round((ligne.total / total) * 100);
  }

  private aujourdhui(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private premierJourDuMois(): string {
    const date = new Date();
    return new Date(date.getFullYear(), date.getMonth(), 1).toISOString().slice(0, 10);
  }
}
