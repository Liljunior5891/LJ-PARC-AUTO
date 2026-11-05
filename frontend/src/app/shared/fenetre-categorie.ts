import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth/auth.service';
import { CategoriePdv, ICONES_CATEGORIES, POSTES_PREPARATION, PostePreparation } from '../core/models/pdv';
import { PdvService } from '../core/services/pdv.service';
import { Modale } from './modale';

/**
 * Creation ou modification d'une categorie, depuis la page Categories ou
 * directement depuis la fiche d'un produit.
 */
@Component({
  selector: 'app-fenetre-categorie',
  imports: [FormsModule, Modale],
  template: `
    <app-modale [titre]="categorie() ? 'Modifier ' + categorie()!.nom : 'Nouvelle categorie'" largeur="560px"
                [erreur]="erreur()" (ferme)="ferme.emit()">
      <form (ngSubmit)="enregistrer()">
        <div class="grille-formulaire">
          <div class="champ">
            <label for="cat-nom">Nom *</label>
            <input id="cat-nom" name="nom" maxlength="80" required [(ngModel)]="nom" placeholder="Ex. Boissons" />
          </div>
          <div class="champ">
            <label for="cat-couleur">Couleur</label>
            <input id="cat-couleur" name="couleur" type="color" [(ngModel)]="couleur" />
          </div>
          @if (restauration) {
            <div class="champ">
              <label for="cat-poste">Preparee</label>
              <select id="cat-poste" name="poste" [(ngModel)]="poste">
                @for (p of postes; track p.valeur) {
                  <option [ngValue]="p.valeur">{{ p.icone }} {{ p.libelle }}</option>
                }
              </select>
            </div>
          }
        </div>
        <div class="champ choix-icone">
          <label>Icone</label>
          <div class="grille-icones" role="radiogroup" aria-label="Icone de la categorie">
            @for (i of icones; track i) {
              <button type="button" class="bouton-icone" role="radio" [class.actif]="icone === i"
                      [attr.aria-checked]="icone === i" (click)="icone = i">{{ i }}</button>
            }
          </div>
        </div>
        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Annuler</button>
          <button class="bouton" type="submit">Enregistrer</button>
        </div>
      </form>
    </app-modale>
  `,
  styles: `
    .choix-icone {
      margin-top: 1rem;
    }

    .grille-icones {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(44px, 1fr));
      gap: 0.4rem;
    }

    .bouton-icone {
      height: 44px;
      border: 1px solid var(--couleur-bordure);
      border-radius: 10px;
      background: var(--couleur-surface);
      font-size: 1.3rem;
      cursor: pointer;
      transition: border-color 0.15s ease, background 0.15s ease;

      &:hover {
        border-color: var(--couleur-primaire-clair);
      }

      &.actif {
        border: 2px solid var(--couleur-primaire);
        background: #e6f0fa;
      }
    }
  `
})
export class FenetreCategorie implements OnInit {
  private readonly service = inject(PdvService);
  readonly restauration = inject(AuthService).estRestauration();
  readonly postes = POSTES_PREPARATION;
  readonly icones = ICONES_CATEGORIES;

  /** Categorie a modifier ; absente : creation. */
  readonly categorie = input<CategoriePdv | null>(null);
  /** Rang d'une nouvelle categorie dans la caisse. */
  readonly ordre = input(0);
  readonly enregistre = output<CategoriePdv>();
  readonly ferme = output<void>();

  readonly erreur = signal<string | null>(null);
  nom = '';
  couleur = '#1f6fd1';
  icone = ICONES_CATEGORIES[0];
  poste: PostePreparation = 'CUISINE';

  ngOnInit(): void {
    const c = this.categorie();
    if (c) {
      this.nom = c.nom;
      this.couleur = c.couleur || this.couleur;
      this.icone = c.icone || this.icone;
      this.poste = c.poste;
    }
  }

  enregistrer(): void {
    if (!this.nom.trim()) {
      this.erreur.set('Le nom de la categorie est obligatoire.');
      return;
    }
    const valeurs = {
      nom: this.nom.trim(),
      couleur: this.couleur,
      icone: this.icone,
      poste: this.restauration ? this.poste : ('AUCUN' as PostePreparation)
    };
    const c = this.categorie();
    const appel = c
      ? this.service.modifierCategorie(c.id, { ...c, ...valeurs })
      : this.service.creerCategorie({ ...valeurs, ordre: this.ordre() });
    appel.subscribe({
      next: (enregistree) => this.enregistre.emit(enregistree),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Enregistrement de la categorie impossible.')
    });
  }
}
