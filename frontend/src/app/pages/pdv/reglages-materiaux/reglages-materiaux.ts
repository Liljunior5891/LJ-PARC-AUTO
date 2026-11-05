import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ReglagesMateriaux } from '../../../core/models/materiaux';
import { MateriauxService } from '../../../core/services/materiaux.service';

/**
 * Reglages de la quincaillerie, reserves a l'administrateur : credit et
 * acomptes, devis, facture A4 et TVA, vente par conditionnement.
 */
@Component({
  selector: 'app-reglages-materiaux',
  imports: [FormsModule],
  templateUrl: './reglages-materiaux.html',
  styleUrl: './reglages-materiaux.scss'
})
export class ReglagesMateriauxPdv implements OnInit {
  private readonly service = inject(MateriauxService);

  readonly formulaire = signal<ReglagesMateriaux | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly enCours = signal(false);

  ngOnInit(): void {
    this.service.reglages().subscribe({
      next: (r) => this.formulaire.set({ ...r }),
      error: () => this.erreur.set('Reglages illisibles.')
    });
  }

  enregistrer(): void {
    const r = this.formulaire();
    if (!r || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.succes.set(null);
    this.service
      .modifierReglages({
        ...r,
        plafondDefaut: r.plafondDefaut === null || (r.plafondDefaut as unknown) === '' ? null : Number(r.plafondDefaut),
        mentionsDevis: r.mentionsDevis?.trim() || null
      })
      .subscribe({
        next: (lus) => {
          this.formulaire.set({ ...lus });
          this.enCours.set(false);
          this.succes.set('Reglages enregistres : ils s\'appliquent des la prochaine vente.');
        },
        error: (e) => {
          this.enCours.set(false);
          this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
        }
      });
  }
}
