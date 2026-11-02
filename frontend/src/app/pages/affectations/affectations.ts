import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Affectation, Camion, Chauffeur, Page } from '../../core/models/models';
import { OptionSelect, SelectRecherche } from '../../shared/select-recherche';
import { AffectationService } from '../../core/services/affectation.service';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { Modale } from '../../shared/modale';
import { ChauffeurEnregistre, FormulaireChauffeur } from '../../shared/formulaire-chauffeur';
import { VALIDATION_FORMULAIRE, messageChampsManquants } from '../../shared/validation-formulaire';

@Component({
  selector: 'app-affectations',
  imports: [FormsModule, ReactiveFormsModule, SelectRecherche, Modale, VALIDATION_FORMULAIRE, FormulaireChauffeur],
  templateUrl: './affectations.html'
})
export class Affectations implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(AffectationService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly auth = inject(AuthService);

  readonly page = signal<Page<Affectation> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly chargement = signal(false);
  readonly formulaireVisible = signal(false);
  /** Formulaire de creation rapide d'un chauffeur absent du systeme. */
  readonly creationChauffeurVisible = signal(false);

  readonly optionsCamion = computed<OptionSelect[]>(() =>
    this.camions().map((camion) => ({
      valeur: camion.id,
      libelle: camion.immatriculation,
      detail: [camion.marque, camion.modele, camion.statutLibelle].filter(Boolean).join(' - '),
      couleur: camion.marqueCouleur,
      monogramme: camion.marqueMonogramme,
      logoUrl: camion.marqueLogoUrl
    }))
  );
  readonly optionsChauffeur = computed<OptionSelect[]>(() =>
    this.chauffeurs().map((chauffeur) => ({
      valeur: chauffeur.id,
      libelle: chauffeur.nomComplet,
      detail: [chauffeur.matricule, chauffeur.telephone].filter(Boolean).join(' - ')
    }))
  );

  filtreEnCours: 'tous' | 'ouvertes' | 'closes' = 'ouvertes';
  private pageCourante = 0;

  readonly peutGerer = this.auth.peutGererParc();

  readonly formulaire = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    chauffeurId: [null as number | null, [Validators.required]],
    dateDebut: [new Date().toISOString().slice(0, 10), [Validators.required]],
    dateFin: [null as string | null],
    motif: [''],
    observations: ['']
  });

  ngOnInit(): void {
    this.charger();
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.chauffeurService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.chauffeurs.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    const enCours =
      this.filtreEnCours === 'tous' ? undefined : this.filtreEnCours === 'ouvertes';
    this.service.lister({ enCours, page: this.pageCourante }).subscribe({
      next: (page) => {
        this.page.set(page);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des affectations impossible.');
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

  basculerCreationChauffeur(): void {
    this.creationChauffeurVisible.update((visible) => !visible);
    this.erreur.set(null);
  }

  /**
   * Chauffeur cree depuis l'affectation : il rejoint la liste et se trouve
   * selectionne, sans quitter l'ecran.
   */
  chauffeurCree({ chauffeur, echecsPieces }: ChauffeurEnregistre): void {
    this.chauffeurs.update((liste) => [...liste.filter((c) => c.id !== chauffeur.id), chauffeur]);
    this.formulaire.controls.chauffeurId.setValue(chauffeur.id);
    this.creationChauffeurVisible.set(false);
    this.succes.set(echecsPieces
      ? `Chauffeur ${chauffeur.nomComplet} cree et selectionne, mais ${echecsPieces} piece(s) `
        + 'n\'ont pas pu etre envoyee(s) : reprenez-les depuis sa fiche.'
      : `Chauffeur ${chauffeur.nomComplet} cree et selectionne.`);
  }

  affecter(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.erreur.set(null);
    const valeur = this.formulaire.getRawValue();
    this.service
      .affecter({
        camionId: valeur.camionId!,
        chauffeurId: valeur.chauffeurId!,
        dateDebut: valeur.dateDebut,
        dateFin: valeur.dateFin || null,
        motif: valeur.motif,
        observations: valeur.observations
      })
      .subscribe({
        next: () => {
          this.succes.set('Affectation enregistree.');
          this.formulaire.reset({ dateDebut: new Date().toISOString().slice(0, 10) });
          this.formulaireVisible.set(false);
          this.charger();
        },
        error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Affectation impossible.')
      });
  }

  /** Identifiant de l'affectation dont la cloture est en cours de saisie. */
  /** Affectation dont la fenetre de cloture est ouverte. */
  readonly clotureEnCours = signal<Affectation | null>(null);
  dateCloture = new Date().toISOString().slice(0, 10);

  ouvrirCloture(affectation: Affectation): void {
    this.dateCloture = new Date().toISOString().slice(0, 10);
    this.clotureEnCours.set(affectation);
  }

  annulerCloture(): void {
    this.clotureEnCours.set(null);
  }

  cloturer(affectation: Affectation): void {
    const date = this.dateCloture;
    const manquants = messageChampsManquants({ 'Date de fin': date });
    if (manquants) {
      this.erreur.set(manquants);
      return;
    }
    this.clotureEnCours.set(null);
    this.service.cloturer(affectation.id, date).subscribe({
      next: () => {
        this.succes.set('Affectation cloturee.');
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Cloture impossible.')
    });
  }
}
