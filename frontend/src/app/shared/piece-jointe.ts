import { DecimalPipe } from '@angular/common';
import { Component, input, model, output, signal } from '@angular/core';
import { DocumentParc } from '../core/models/models';

/** Formats acceptes par le stockage des documents, cote API. */
const EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png', 'webp'];
/** Limite de l'API (spring.servlet.multipart.max-file-size). */
const TAILLE_MAX = 15 * 1024 * 1024;

/**
 * Zone de depot d'une piece justificative (permis, piece d'identite...).
 * <p>
 * Le fichier n'est pas envoye ici : il est remonte au formulaire, qui le
 * televerse apres avoir enregistre la fiche, car le document doit etre
 * rattache a un proprietaire deja connu. La piece deja enregistree, si elle
 * existe, reste telechargeable et peut etre remplacee.
 */
@Component({
  selector: 'app-piece-jointe',
  imports: [DecimalPipe],
  template: `
    <div class="piece-jointe" [class.survol]="survol()" [class.remplie]="fichier() || existant()?.fichierDisponible"
         (dragover)="$event.preventDefault(); survol.set(true)" (dragleave)="survol.set(false)"
         (drop)="deposer($event)">
      <span class="icone" aria-hidden="true">{{ fichier() || existant()?.fichierDisponible ? '✔' : '⇪' }}</span>
      <div class="textes">
        <strong>{{ libelle() }}</strong>
        @if (fichier(); as f) {
          <small>{{ f.name }} · {{ f.size / 1024 | number: '1.0-0' }} Ko — envoye a l'enregistrement</small>
        } @else if (existant()?.fichierDisponible) {
          <small>{{ existant()!.fichierNom }} — deja enregistre</small>
        } @else {
          <small title="Glissez le fichier dans la zone ou cliquez sur Choisir">PDF, JPG ou PNG — 15 Mo max.</small>
        }
        @if (erreur()) {
          <small class="erreur">{{ erreur() }}</small>
        }
      </div>
      <div class="actions">
        @if (existant()?.fichierDisponible && !fichier()) {
          <button class="bouton secondaire petit info" type="button" (click)="telecharger.emit(existant()!)">Voir</button>
        }
        @if (fichier()) {
          <button class="bouton secondaire petit danger" type="button" (click)="fichier.set(null)">Retirer</button>
        }
        <label class="bouton petit">
          {{ fichier() || existant()?.fichierDisponible ? 'Remplacer' : 'Choisir' }}
          <input type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,image/*,application/pdf" hidden
                 (change)="choisir($event)" />
        </label>
      </div>
    </div>
  `,
  styles: `
    .piece-jointe {
      display: flex;
      align-items: center;
      gap: 0.8rem;
      padding: 0.7rem 0.85rem;
      border: 1.5px dashed #b8c9e0;
      border-radius: 10px;
      background: #f7faff;
      transition: border-color 0.15s ease, background 0.15s ease;

      &.survol {
        border-color: var(--couleur-primaire);
        background: #eaf2fe;
      }

      &.remplie {
        border-style: solid;
        border-color: #9fd8bd;
        background: #f2fbf6;

        .icone {
          background: #1f9d6b;
          color: #fff;
        }
      }
    }

    .icone {
      display: grid;
      flex: none;
      place-items: center;
      width: 34px;
      height: 34px;
      border-radius: 50%;
      background: #dce8f8;
      color: #1f5fae;
      font-weight: 700;
    }

    .textes {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;

      strong {
        font-size: 0.85rem;
      }

      small {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.74rem;
        color: var(--couleur-texte-doux);

        &.erreur {
          color: var(--couleur-danger, #c63d22);
        }
      }
    }

    .actions {
      display: flex;
      flex: none;
      gap: 0.4rem;

      label {
        cursor: pointer;
      }
    }
  `
})
export class PieceJointe {
  readonly libelle = input.required<string>();
  /** Document deja enregistre pour ce type de piece, s'il y en a un. */
  readonly existant = input<DocumentParc | null>(null);
  /** Fichier choisi, en attente d'envoi. */
  readonly fichier = model<File | null>(null);
  readonly telecharger = output<DocumentParc>();

  readonly survol = signal(false);
  readonly erreur = signal<string | null>(null);

  choisir(evenement: Event): void {
    const entree = evenement.target as HTMLInputElement;
    this.retenir(entree.files?.[0] ?? null);
    // Permet de rechoisir le meme fichier apres l'avoir retire.
    entree.value = '';
  }

  deposer(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survol.set(false);
    this.retenir(evenement.dataTransfer?.files?.[0] ?? null);
  }

  private retenir(fichier: File | null): void {
    if (!fichier) {
      return;
    }
    const extension = fichier.name.split('.').pop()?.toLowerCase() ?? '';
    if (!EXTENSIONS.includes(extension)) {
      this.erreur.set('Format non accepte : PDF, JPG, PNG ou WEBP.');
      return;
    }
    if (fichier.size > TAILLE_MAX) {
      this.erreur.set('Fichier trop lourd : 15 Mo au maximum.');
      return;
    }
    this.erreur.set(null);
    this.fichier.set(fichier);
  }
}
