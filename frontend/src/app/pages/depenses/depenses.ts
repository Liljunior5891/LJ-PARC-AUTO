import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import {
  CATEGORIES_DEPENSE,
  CategorieDepense,
  Depense,
  LigneCategorieDepense
} from '../../core/models/finance';
import { Camion, Page } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { DepenseService } from '../../core/services/depense.service';
import { FormulaireDepense } from '../../shared/formulaire-depense';
import { Modale } from '../../shared/modale';
import { OperationService } from '../../core/operation/operation.service';

@Component({
  selector: 'app-depenses',
  imports: [FormsModule, DecimalPipe, Modale, FormulaireDepense],
  templateUrl: './depenses.html'
})
export class Depenses implements OnInit {
  private readonly operation = inject(OperationService);
  private readonly service = inject(DepenseService);
  private readonly camionService = inject(CamionService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly categories = CATEGORIES_DEPENSE;

  readonly page = signal<Page<Depense> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly repartition = signal<LigneCategorieDepense[]>([]);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly formulaireVisible = signal(false);

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

  /** Vehicule impose a la saisie quand on arrive de sa fiche pour une nouvelle depense. */
  camionImposeSaisie: number | null = null;

  ngOnInit(): void {
    // Arrivee depuis la fiche d'un camion : filtre et formulaire prets.
    const camionId = this.route.snapshot.queryParamMap.get('camionId');
    if (camionId) {
      this.camionId = Number(camionId);
      if (this.route.snapshot.queryParamMap.get('nouvelle') === 'true') {
        this.camionImposeSaisie = Number(camionId);
        this.formulaireVisible.set(true);
      }
    }
    this.charger();
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
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

  /** Depense enregistree par le formulaire partage : on ferme et on recharge. */
  depenseEnregistree(depense: Depense): void {
    this.succes.set(`Depense ${depense.numero} enregistree.`);
    this.formulaireVisible.set(false);
    this.charger();
  }

  async supprimer(depense: Depense): Promise<void> {
    const accepte = await this.operation.confirmer({
      titre: `Supprimer la depense ${depense.numero} ?`,
      message: `${depense.montant.toLocaleString('fr-FR')} FCFA seront retires des comptes du vehicule.`,
      libelleAction: 'Supprimer',
      danger: true
    });
    if (!accepte) {
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
