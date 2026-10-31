import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CodeModule } from '../../core/models/models';
import { ICONES_VARIANTES, ModuleCatalogue, PRESENTATION_MODULES } from '../../core/models/plateforme';
import { PlateformeService } from '../../core/services/plateforme.service';
import { Modale } from '../../shared/modale';

/**
 * Catalogue des modules de la plateforme et etat de souscription de l'entreprise.
 *
 * Tout compte voit ce qui est active ; seul l'administrateur active ou
 * desactive. Un module qui se decline (le point de vente d'une boutique ou
 * d'un restaurant) demande son type d'activite a l'activation, modifiable
 * ensuite. La session est mise a jour aussitot : le menu suit sans
 * reconnexion.
 */
@Component({
  selector: 'app-modules',
  imports: [FormsModule, Modale],
  templateUrl: './modules.html',
  styleUrl: './modules.scss'
})
export class Modules implements OnInit {
  private readonly service = inject(PlateformeService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly presentation = PRESENTATION_MODULES;
  readonly icones = ICONES_VARIANTES;
  readonly estAdmin = this.auth.estAdmin();
  readonly entreprise = this.auth.utilisateur()?.entrepriseNom;

  readonly modules = signal<ModuleCatalogue[]>([]);
  readonly chargement = signal(true);
  readonly enCours = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);

  /** Module dont on choisit le type d'activite, a l'activation ou pour en changer. */
  readonly choix = signal<{ module: ModuleCatalogue; activation: boolean } | null>(null);
  readonly varianteChoisie = signal<string | null>(null);

  ngOnInit(): void {
    this.service.modules().subscribe({
      next: (modules) => this.appliquer(modules),
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des modules impossible.');
      }
    });
  }

  aucunActif(): boolean {
    return !this.chargement() && this.modules().every((m) => !m.actif);
  }

  libelleVariante(module: ModuleCatalogue): string | null {
    return module.variantes.find((v) => v.code === module.variante)?.libelle ?? null;
  }

  ouvrir(module: ModuleCatalogue): void {
    void this.router.navigateByUrl(this.auth.accueilModule(module.code));
  }

  activer(module: ModuleCatalogue): void {
    if (module.variantes.length > 0) {
      this.demanderVariante(module, true);
      return;
    }
    this.executer(module, this.service.activer(module.code), () => this.ouvrir(module));
  }

  demanderVariante(module: ModuleCatalogue, activation: boolean): void {
    this.erreur.set(null);
    this.varianteChoisie.set(module.variante ?? null);
    this.choix.set({ module, activation });
  }

  confirmerVariante(): void {
    const choix = this.choix();
    const variante = this.varianteChoisie();
    if (!choix || !variante) {
      return;
    }
    const { module, activation } = choix;
    const appel = activation
      ? this.service.activer(module.code, variante)
      : this.service.changerVariante(module.code, variante);
    this.executer(module, appel, () => {
      this.choix.set(null);
      if (activation) {
        this.ouvrir(module);
      }
    });
  }

  desactiver(module: ModuleCatalogue): void {
    this.executer(module, this.service.desactiver(module.code));
  }

  private executer(
    module: ModuleCatalogue,
    appel: ReturnType<PlateformeService['activer']>,
    ensuite?: () => void
  ): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(module.code);
    this.erreur.set(null);
    appel.subscribe({
      next: (modules) => {
        this.enCours.set(null);
        this.appliquer(modules);
        ensuite?.();
      },
      error: (reponse) => {
        this.enCours.set(null);
        const message = (reponse as { error?: { message?: string } })?.error?.message;
        this.erreur.set(message ?? 'Operation impossible.');
      }
    });
  }

  private appliquer(modules: ModuleCatalogue[]): void {
    this.chargement.set(false);
    this.modules.set(modules);
    const actifs = modules.filter((m) => m.actif);
    const variantes: Partial<Record<CodeModule, string>> = {};
    for (const m of actifs) {
      if (m.variante) {
        variantes[m.code] = m.variante;
      }
    }
    this.auth.mettreAJourModules(actifs.map((m) => m.code), variantes);
  }
}
