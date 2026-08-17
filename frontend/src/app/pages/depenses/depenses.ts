import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  CATEGORIES_DEPENSE,
  CategorieDepense,
  Depense,
  DepenseRequete,
  LigneCategorieDepense,
  MODES_PAIEMENT
} from '../../core/models/finance';
import { Camion, Chauffeur, Page } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { DepenseService, NaturesParCategorie } from '../../core/services/depense.service';

@Component({
  selector: 'app-depenses',
  imports: [FormsModule, ReactiveFormsModule, DecimalPipe],
  templateUrl: './depenses.html'
})
export class Depenses implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DepenseService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly categories = CATEGORIES_DEPENSE;
  readonly modes = MODES_PAIEMENT;

  readonly page = signal<Page<Depense> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly natures = signal<NaturesParCategorie | null>(null);
  readonly repartition = signal<LigneCategorieDepense[]>([]);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);

  /** Categorie choisie dans le formulaire, qui pilote la liste des natures. */
  readonly categorieSaisie = signal<CategorieDepense>('CARBURANT');

  readonly naturesDisponibles = computed(
    () => this.natures()?.[this.categorieSaisie()] ?? []
  );

  /** Vrai si la categorie saisie exige les informations de plein (chapitre 18). */
  readonly saisieCarburant = computed(() => this.categorieSaisie() === 'CARBURANT');

  readonly totalPeriode = computed(() =>
    this.repartition().reduce((cumul, ligne) => cumul + ligne.total, 0)
  );

  recherche = '';
  categorie: CategorieDepense | '' = '';
  camionId?: number;
  debut = this.premierJourDuMois();
  fin = this.aujourdhui();
  private pageCourante = 0;

  readonly peutGerer = this.auth.peutGererParc();

  readonly formulaire = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    chauffeurId: [null as number | null],
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

  ngOnInit(): void {
    // Arrivee depuis la fiche d'un camion : filtre et formulaire prets.
    const camionId = this.route.snapshot.queryParamMap.get('camionId');
    if (camionId) {
      this.camionId = Number(camionId);
      this.formulaire.controls.camionId.setValue(Number(camionId));
      if (this.route.snapshot.queryParamMap.get('nouvelle') === 'true') {
        this.formulaireVisible.set(true);
      }
    }
    this.charger();
    this.service.natures().subscribe({
      next: (natures) => this.natures.set(natures)
    });
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.chauffeurService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.chauffeurs.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche,
        categorie: this.categorie,
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
          this.erreur.set('Chargement des depenses impossible.');
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

  /** Change la categorie de saisie et positionne la premiere nature disponible. */
  changerCategorieSaisie(categorie: string): void {
    this.categorieSaisie.set(categorie as CategorieDepense);
    const premiere = this.naturesDisponibles()[0];
    if (premiere) {
      this.formulaire.controls.sousCategorie.setValue(premiere.valeur);
    }
  }

  enregistrer(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const brut = this.formulaire.getRawValue();
    const requete: DepenseRequete = {
      ...brut,
      camionId: brut.camionId!,
      modePaiement: (brut.modePaiement || null) as DepenseRequete['modePaiement']
    };
    this.service.creer(requete).subscribe({
      next: (depense) => {
        this.succes.set(`Depense ${depense.numero} enregistree.`);
        this.formulaire.reset({
          sousCategorie: 'DIESEL',
          dateDepense: this.aujourdhui(),
          montant: 0,
          aChargeClient: false
        });
        this.categorieSaisie.set('CARBURANT');
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.')
    });
  }

  supprimer(depense: Depense): void {
    if (!confirm(`Supprimer la depense ${depense.numero} de ${depense.montant} FCFA ?`)) {
      return;
    }
    this.service.supprimer(depense.id).subscribe({
      next: () => this.charger(),
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Suppression impossible.')
    });
  }

  part(ligne: LigneCategorieDepense): number {
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
