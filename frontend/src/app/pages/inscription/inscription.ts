import { FondModules } from '../../shared/fond-modules';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { CodeModule } from '../../core/models/models';
import { ICONES_VARIANTES, ModuleCatalogue, PRESENTATION_MODULES } from '../../core/models/plateforme';
import { LONGUEUR_MOT_DE_PASSE } from '../../core/models/utilisateur';
import { PlateformeService } from '../../core/services/plateforme.service';

/** Les deux saisies du mot de passe doivent concorder. */
function confirmationIdentique(groupe: AbstractControl): ValidationErrors | null {
  const motDePasse = groupe.get('motDePasse')?.value;
  const confirmation = groupe.get('confirmation')?.value;
  return confirmation && motDePasse !== confirmation ? { confirmation: true } : null;
}

/**
 * Creation du compte d'une entreprise en libre-service : l'entreprise, son
 * administrateur et les modules a activer d'emblee. La session s'ouvre a la
 * fin, sans repasser par l'ecran de connexion.
 */
@Component({
  selector: 'app-inscription',
  imports: [ReactiveFormsModule, RouterLink, FondModules],
  templateUrl: './inscription.html',
  styleUrls: ['../login/login.scss', './inscription.scss']
})
export class Inscription implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(PlateformeService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly nomPlateforme = environment.nomPlateforme;
  readonly longueurMinimale = LONGUEUR_MOT_DE_PASSE;
  readonly presentation = PRESENTATION_MODULES;
  readonly icones = ICONES_VARIANTES;

  readonly catalogue = signal<ModuleCatalogue[]>([]);
  /** La gestion de parc est proposee cochee : c'est le module qui motive la plupart des inscriptions. */
  readonly choisis = signal<Set<CodeModule>>(new Set(['GESTION_PARC']));
  /** Type d'activite choisi pour les modules qui se declinent (boutique ou restaurant). */
  readonly variantes = signal<Partial<Record<CodeModule, string>>>({});
  readonly enCours = signal(false);
  /** 1 : entreprise et administrateur ; 2 : modules a activer. */
  readonly etape = signal<1 | 2>(1);
  readonly erreur = signal<string | null>(null);
  /** Demande envoyee : message affiche a la place du formulaire (verifier sa boite mail). */
  readonly envoye = signal<string | null>(null);

  readonly formulaire = this.fb.nonNullable.group(
    {
      nomEntreprise: ['', [Validators.required, Validators.maxLength(150)]],
      pays: [''],
      telephone: [''],
      prenom: [''],
      nom: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      motDePasse: ['', [Validators.required, Validators.minLength(LONGUEUR_MOT_DE_PASSE)]],
      confirmation: ['', [Validators.required]]
    },
    { validators: confirmationIdentique }
  );

  ngOnInit(): void {
    this.service.cataloguePublic().subscribe({
      next: (modules) => {
        this.catalogue.set(modules);
        const parDefaut: Partial<Record<CodeModule, string>> = {};
        modules.filter((m) => m.variantes.length > 0).forEach((m) => (parDefaut[m.code] = m.variantes[0].code));
        this.variantes.set(parDefaut);
      },
      error: () => this.erreur.set("Le serveur ne repond pas. Reessayez dans un instant.")
    });
  }

  basculer(module: ModuleCatalogue): void {
    if (!module.disponible) {
      return;
    }
    this.choisis.update((courant) => {
      const suivant = new Set(courant);
      if (suivant.has(module.code)) {
        suivant.delete(module.code);
      } else {
        suivant.add(module.code);
      }
      return suivant;
    });
  }

  choisirVariante(module: ModuleCatalogue, variante: string): void {
    this.variantes.update((v) => ({ ...v, [module.code]: variante }));
  }

  invalide(champ: keyof typeof this.formulaire.controls): boolean {
    const controle = this.formulaire.controls[champ];
    return controle.touched && controle.invalid;
  }

  /** Passe au choix des modules une fois les informations professionnelles valides. */
  continuer(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      this.etape.set(1);
      return;
    }
    this.erreur.set(null);
    this.etape.set(2);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  soumettre(): void {
    if (this.etape() === 1) {
      this.continuer();
      return;
    }
    if (this.formulaire.invalid) {
      this.continuer();
      return;
    }
    if (!this.choisis().size || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);

    const { confirmation: _confirmation, ...saisie } = this.formulaire.getRawValue();
    const modules = [...this.choisis()];
    const variantes: Partial<Record<CodeModule, string>> = {};
    modules.filter((m) => this.variantes()[m]).forEach((m) => (variantes[m] = this.variantes()[m]));
    this.service.inscrire({ ...saisie, modules, variantes }).subscribe({
      next: (reponse) => {
        this.enCours.set(false);
        this.envoye.set(reponse.message);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (reponse) => {
        this.enCours.set(false);
        const message = (reponse as { error?: { message?: string } })?.error?.message;
        this.erreur.set(message ?? 'Creation du compte impossible. Reessayez dans un instant.');
      }
    });
  }
}
