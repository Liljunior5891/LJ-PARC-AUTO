import { Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CategoriePdv, MembrePersonnel } from '../../../core/models/pdv';
import { EcranPreparation, ListeEcrans } from '../../../core/models/restauration';
import { PdvService } from '../../../core/services/pdv.service';
import { RestaurationService } from '../../../core/services/restauration.service';
import { Modale } from '../../../shared/modale';

/**
 * Ecrans de preparation crees par le gerant : chacun recoit les articles des
 * categories choisies (le grill, le bar, la patisserie...) et n'est ouvert
 * qu'aux utilisateurs affectes.
 */
@Component({
  selector: 'app-ecrans-pdv',
  imports: [FormsModule, RouterLink, Modale],
  templateUrl: './ecrans.html',
  styleUrls: ['../caisses/caisses.scss', './ecrans.scss']
})
export class EcransPdv implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly pdv = inject(PdvService);

  readonly liste = signal<ListeEcrans | null>(null);
  readonly categories = signal<CategoriePdv[]>([]);
  readonly personnel = signal<MembrePersonnel[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);

  readonly edition = signal<EcranPreparation | 'nouvel' | null>(null);
  formulaire = { nom: '', actif: true };
  readonly categoriesChoisies = signal<Set<number>>(new Set());
  readonly utilisateursChoisis = signal<Set<number>>(new Set());

  ngOnInit(): void {
    this.charger();
    this.pdv.categories().subscribe({ next: (c) => this.categories.set(c.filter((x) => x.actif)) });
    this.pdv.personnel().subscribe({ next: (p) => this.personnel.set(p.filter((m) => m.actif)) });
  }

  nouvel(): void {
    this.formulaire = { nom: '', actif: true };
    this.categoriesChoisies.set(new Set());
    this.utilisateursChoisis.set(new Set());
    this.erreurFormulaire.set(null);
    this.edition.set('nouvel');
  }

  modifier(ecran: EcranPreparation): void {
    this.formulaire = { nom: ecran.nom, actif: ecran.actif };
    this.categoriesChoisies.set(new Set(ecran.categories.map((c) => c.id)));
    this.utilisateursChoisis.set(new Set(ecran.utilisateurs.map((u) => u.id)));
    this.erreurFormulaire.set(null);
    this.edition.set(ecran);
  }

  basculer(ensemble: WritableSignal<Set<number>>, id: number): void {
    ensemble.update((ids) => {
      const suivant = new Set(ids);
      if (suivant.has(id)) {
        suivant.delete(id);
      } else {
        suivant.add(id);
      }
      return suivant;
    });
  }

  enregistrer(): void {
    const edition = this.edition();
    if (!edition || !this.formulaire.nom.trim()) {
      return;
    }
    const requete = {
      nom: this.formulaire.nom.trim(),
      actif: this.formulaire.actif,
      categorieIds: [...this.categoriesChoisies()],
      utilisateurIds: [...this.utilisateursChoisis()]
    };
    const appel = edition === 'nouvel'
      ? this.service.creerEcran(requete)
      : this.service.modifierEcran(edition.id, requete);
    appel.subscribe({
      next: () => {
        this.edition.set(null);
        this.charger();
      },
      error: (e) => this.erreurFormulaire.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  /** Retire un ecran ou le remet en service, sans toucher a ses categories ni a son personnel. */
  basculerActivation(ecran: EcranPreparation): void {
    this.erreur.set(null);
    this.service.modifierEcran(ecran.id, {
      nom: ecran.nom,
      actif: !ecran.actif,
      categorieIds: ecran.categories.map((c) => c.id),
      utilisateurIds: ecran.utilisateurs.map((u) => u.id)
    }).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(this.message(e, 'Changement impossible.'))
    });
  }

  private charger(): void {
    this.service.ecrans().subscribe({
      next: (l) => this.liste.set(l),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des ecrans impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
