import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AgencesHorsLigneService, ConflitHorsLigne } from '../../../core/services/agences-hors-ligne.service';
import { Modale } from '../../../shared/modale';

const TYPES: Record<string, string> = { TRANSACTION: 'Transaction', VENTE_CARTE: 'Vente de cartes', DEPENSE: 'Depense' };

/**
 * Operations saisies sans connexion que le serveur a refusees a l'envoi (float
 * insuffisant, caisse fermee, carte deja vendue...). L'argent a deja change de
 * main : le superviseur leve la cause puis reprend l'operation, ou la regle
 * hors du logiciel et l'abandonne avec une note.
 */
@Component({
  selector: 'app-conflits-hors-ligne',
  imports: [DatePipe, FormsModule, Modale],
  template: `
    <div class="entete-page">
      <div>
        <h1>Operations hors ligne a traiter</h1>
        <p class="sous-titre">Operations saisies par les caissiers sans connexion et refusees a l'envoi. Levez la cause (approvisionner le float,
          rouvrir la caisse...) puis reprenez-la, ou abandonnez-la si elle a ete reglee autrement.</p>
      </div>
      <div class="actions-entete">
        <button type="button" class="bouton secondaire" (click)="charger()">Actualiser</button>
      </div>
    </div>

    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (succes()) { <div class="message succes">{{ succes() }}</div> }

    <div class="grille-conflits">
      @for (c of conflits(); track c.id) {
        <article class="carte conflit">
          <header>
            <span class="type">{{ type(c.type) }}</span>
            <small>{{ c.agenceNom ?? '' }}</small>
          </header>
          <h3>{{ c.resume }}</h3>
          <dl>
            <dt>Saisie</dt><dd>{{ c.saisieLe | date: 'dd/MM/yyyy HH:mm' }} par {{ c.auteurNom }}</dd>
            <dt>Recue</dt><dd>{{ c.recueLe | date: 'dd/MM/yyyy HH:mm' }}</dd>
          </dl>
          <p class="motif">⚠ {{ c.motif }}</p>
          <footer>
            <button type="button" class="bouton secondaire petit" (click)="demanderAbandon(c)">Abandonner</button>
            <button type="button" class="bouton petit" (click)="reprendre(c)">Reprendre maintenant</button>
          </footer>
        </article>
      } @empty {
        <div class="carte vide">{{ charge() ? 'Aucun conflit : toutes les operations hors ligne ont ete enregistrees.' : 'Chargement...' }}</div>
      }
    </div>

    @if (aAbandonner(); as c) {
      <app-modale [titre]="'Abandonner : ' + c.resume" largeur="520px" [erreur]="erreurModale()" (ferme)="aAbandonner.set(null)">
        <p class="aide">L'operation ne sera pas enregistree. Notez comment elle a ete reglee (argent rendu au client, refaite par une autre
          operation...).</p>
        <div class="champ">
          <label for="note-abandon">Note *</label>
          <input id="note-abandon" maxlength="500" [(ngModel)]="note" />
        </div>
        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="aAbandonner.set(null)">Retour</button>
          <button class="bouton danger" type="button" [disabled]="!note.trim()" (click)="abandonner()">Abandonner</button>
        </div>
      </app-modale>
    }
  `,
  styles: `
    .grille-conflits { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 1rem; }
    .conflit { display: flex; flex-direction: column; gap: 0.5rem; border-top: 4px solid #e0a100; }
    header { display: flex; justify-content: space-between; align-items: center; }
    .type { padding: 0.1rem 0.55rem; border-radius: 999px; background: #fff6e0; color: #8a6200; font-size: 0.75rem; font-weight: 700; }
    h3 { margin: 0; font-size: 1rem; }
    dl { display: grid; grid-template-columns: auto 1fr; gap: 0.2rem 0.7rem; margin: 0; font-size: 0.85rem; }
    dt { color: var(--couleur-texte-doux); }
    dd { margin: 0; }
    .motif { margin: 0; padding: 0.5rem 0.7rem; border-radius: 8px; background: #fce9e9; color: var(--couleur-danger); font-size: 0.88rem; }
    footer { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: auto; }
    .vide { padding: 2rem; color: var(--couleur-texte-doux); text-align: center; }
    .aide { color: var(--couleur-texte-doux); font-size: 0.85rem; }
  `
})
export class ConflitsHorsLigne implements OnInit {
  private readonly service = inject(AgencesHorsLigneService);

  readonly conflits = signal<ConflitHorsLigne[]>([]);
  readonly charge = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly aAbandonner = signal<ConflitHorsLigne | null>(null);
  note = '';

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.conflits().subscribe({
      next: (c) => {
        this.conflits.set(c);
        this.charge.set(true);
      },
      error: (e) => this.erreur.set(this.message(e, 'Chargement impossible.'))
    });
  }

  type(code: string): string {
    return TYPES[code] ?? code;
  }

  reprendre(c: ConflitHorsLigne): void {
    this.erreur.set(null);
    this.succes.set(null);
    this.service.reprendre(c.id).subscribe({
      next: (r) => {
        this.succes.set(`${c.resume} : enregistree (${r.reference}).`);
        this.charger();
      },
      error: (e) => this.erreur.set(`${c.resume} : ${this.message(e, 'reprise impossible.')}`)
    });
  }

  demanderAbandon(c: ConflitHorsLigne): void {
    this.note = '';
    this.erreurModale.set(null);
    this.aAbandonner.set(c);
  }

  abandonner(): void {
    const c = this.aAbandonner();
    if (!c || !this.note.trim()) {
      return;
    }
    this.service.abandonner(c.id, this.note.trim()).subscribe({
      next: () => {
        this.aAbandonner.set(null);
        this.succes.set(`${c.resume} : abandonnee.`);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Abandon impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
