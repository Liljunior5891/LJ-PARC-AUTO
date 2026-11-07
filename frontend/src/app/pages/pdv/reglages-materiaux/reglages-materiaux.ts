import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, tap } from 'rxjs';
import { ReglagesMateriaux } from '../../../core/models/materiaux';
import { MateriauxService } from '../../../core/services/materiaux.service';

/**
 * Reglages de la quincaillerie (credit et acomptes, devis, facture et TVA,
 * conditionnements, livraison en plusieurs fois), integres a la page des
 * reglages du point de vente : celle-ci detecte les modifications et les
 * enregistre avec les siennes, par une seule barre d'enregistrement.
 */
@Component({
  selector: 'app-reglages-materiaux',
  imports: [FormsModule],
  templateUrl: './reglages-materiaux.html',
  styleUrls: ['../reglages/reglages-blocs.scss']
})
export class ReglagesMateriauxPdv implements OnInit {
  private readonly service = inject(MateriauxService);

  readonly formulaire = signal<ReglagesMateriaux | null>(null);
  readonly erreur = signal<string | null>(null);
  /** Etat enregistre, pour reperer une modification et pouvoir l'annuler. */
  private enregistre = '';

  ngOnInit(): void {
    this.service.reglages().subscribe({
      next: (r) => this.appliquer(r),
      error: () => this.erreur.set('Reglages de la quincaillerie illisibles.')
    });
  }

  /** Vrai si un champ differe de l'etat enregistre. */
  modifie(): boolean {
    const f = this.formulaire();
    return !!f && JSON.stringify(f) !== this.enregistre;
  }

  /** Revient a l'etat enregistre. */
  reinitialiser(): void {
    if (this.enregistre) {
      this.formulaire.set(JSON.parse(this.enregistre));
    }
  }

  enregistrer(): Observable<ReglagesMateriaux> {
    const r = this.formulaire()!;
    return this.service
      .modifierReglages({
        ...r,
        plafondDefaut: r.plafondDefaut === null || (r.plafondDefaut as unknown) === '' ? null : Number(r.plafondDefaut),
        mentionsDevis: r.mentionsDevis?.trim() || null
      })
      .pipe(tap((lus) => this.appliquer(lus)));
  }

  private appliquer(r: ReglagesMateriaux): void {
    this.formulaire.set({ ...r });
    this.enregistre = JSON.stringify(this.formulaire());
  }
}
