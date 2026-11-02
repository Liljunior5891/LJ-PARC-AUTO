import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ChauffeurEnregistre, FormulaireChauffeur } from '../../shared/formulaire-chauffeur';

@Component({
  selector: 'app-chauffeur-fiche',
  imports: [RouterLink, FormulaireChauffeur],
  templateUrl: './chauffeur-fiche.html'
})
export class ChauffeurFiche implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly id = signal<number | null>(null);
  /** Ouverte dans une fenetre depuis la liste : creation seule, sans en-tete de page. */
  readonly enModale = input(false);
  /** Creation terminee en mode fenetre : identifiant du nouvel enregistrement. */
  readonly cree = output<number>();
  readonly annule = output<void>();
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  get titre(): string {
    return this.id() === null ? 'Nouveau chauffeur' : 'Fiche du chauffeur';
  }

  ngOnInit(): void {
    const parametre = this.enModale() ? null : this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      this.id.set(Number(parametre));
      const echecs = (history.state as { echecsPieces?: number } | null)?.echecsPieces ?? 0;
      if (echecs > 0) {
        this.erreur.set(`Chauffeur cree, mais ${echecs} piece(s) n'ont pas pu etre envoyee(s).`);
      }
    }
  }

  /** Fiche enregistree par le formulaire, qui affiche lui-meme un echec d'envoi des pieces. */
  enregistre({ chauffeur, creation, echecsPieces }: ChauffeurEnregistre): void {
    // En fenetre, un envoi en echec renvoie sur la fiche pour le reprendre.
    if (creation && this.enModale() && echecsPieces === 0) {
      this.cree.emit(chauffeur.id);
    } else if (creation) {
      void this.router.navigate(['/chauffeurs', chauffeur.id], { state: { echecsPieces } });
    } else if (echecsPieces === 0) {
      this.succes.set('Fiche enregistree.');
    }
  }

  annuler(): void {
    if (this.enModale()) {
      this.annule.emit();
    } else {
      void this.router.navigate(['/chauffeurs']);
    }
  }
}
