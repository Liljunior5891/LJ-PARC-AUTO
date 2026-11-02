import { Component, model, signal } from '@angular/core';

/** Plus grand cote de la photo : nette sur la carte du vehicule, legere a charger. */
const COTE_MAX = 960;

/**
 * Reduit une photo de vehicule en gardant ses proportions et la renvoie en
 * data URL JPEG. Une photo de telephone pese plusieurs Mo ; reduite ici, elle
 * tombe a une centaine de Ko avant meme de quitter le navigateur.
 */
export function reduirePhotoVehicule(fichier: File): Promise<string> {
  return new Promise((resoudre, rejeter) => {
    if (!fichier.type.startsWith('image/')) {
      rejeter(new Error('Choisissez une image (JPEG, PNG ou WebP).'));
      return;
    }
    const url = URL.createObjectURL(fichier);
    const image = new Image();
    image.onload = () => {
      const echelle = Math.min(1, COTE_MAX / Math.max(image.naturalWidth, image.naturalHeight));
      const toile = document.createElement('canvas');
      toile.width = Math.round(image.naturalWidth * echelle);
      toile.height = Math.round(image.naturalHeight * echelle);
      const contexte = toile.getContext('2d');
      URL.revokeObjectURL(url);
      if (!contexte) {
        rejeter(new Error('Navigateur incompatible.'));
        return;
      }
      contexte.fillStyle = '#ffffff';
      contexte.fillRect(0, 0, toile.width, toile.height);
      contexte.drawImage(image, 0, 0, toile.width, toile.height);
      resoudre(toile.toDataURL('image/jpeg', 0.82));
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      rejeter(new Error('Image illisible.'));
    };
    image.src = url;
  });
}

/**
 * Photo du vehicule dans sa fiche : apercu, choix (clic ou glisser-deposer) et
 * retrait. Une fois enregistree, elle remplace le dessin du type sur la carte
 * du vehicule dans la vue du parc.
 */
@Component({
  selector: 'app-photo-vehicule',
  template: `
    <div class="photo-vehicule" [class.survol]="survol()" (dragover)="$event.preventDefault(); survol.set(true)"
         (dragleave)="survol.set(false)" (drop)="deposer($event)">
      <div class="apercu" [style.background-image]="photo() ? 'url(' + photo() + ')' : null">
        @if (!photo()) {
          <span>📷</span>
        }
      </div>
      <div class="textes">
        <strong>Photo du vehicule</strong>
        <small>{{ photo() ? 'Affichee sur la carte du vehicule a la place du dessin.' : 'JPEG, PNG ou WebP — glissez-la ici ou choisissez-la.' }}</small>
        @if (erreur()) {
          <small class="erreur">{{ erreur() }}</small>
        }
        <div class="actions">
          <label class="bouton petit">
            {{ photo() ? 'Remplacer' : 'Choisir une photo' }}
            <input type="file" accept="image/*" hidden (change)="choisir($event)" />
          </label>
          @if (photo()) {
            <button class="bouton secondaire petit" type="button" (click)="photo.set(null)">Retirer</button>
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
      max-width: 460px;
    }

    .photo-vehicule {
      display: flex;
      align-items: center;
      gap: 0.9rem;
      padding: 0.7rem;
      border: 1.5px dashed #b8c9e0;
      border-radius: 12px;
      background: #f7faff;

      &.survol {
        border-color: var(--couleur-primaire);
        background: #eaf2fe;
      }
    }

    .apercu {
      display: grid;
      flex: none;
      place-items: center;
      width: 150px;
      height: 100px;
      border-radius: 10px;
      background: #e8eef6 center / cover;
      font-size: 1.8rem;
    }

    .textes {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
      min-width: 0;

      strong {
        font-size: 0.86rem;
      }

      small {
        font-size: 0.74rem;
        color: var(--couleur-texte-doux);

        &.erreur {
          color: var(--couleur-danger, #c63d22);
        }
      }
    }

    .actions {
      display: flex;
      gap: 0.4rem;
      margin-top: 0.3rem;

      label {
        cursor: pointer;
      }
    }
  `
})
export class PhotoVehicule {
  /** Photo encodee (data URL) ; nulle sans photo. */
  readonly photo = model<string | null>(null);
  readonly survol = signal(false);
  readonly erreur = signal<string | null>(null);

  choisir(evenement: Event): void {
    const entree = evenement.target as HTMLInputElement;
    void this.retenir(entree.files?.[0]);
    entree.value = '';
  }

  deposer(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survol.set(false);
    void this.retenir(evenement.dataTransfer?.files?.[0]);
  }

  private async retenir(fichier: File | undefined): Promise<void> {
    if (!fichier) {
      return;
    }
    try {
      this.photo.set(await reduirePhotoVehicule(fichier));
      this.erreur.set(null);
    } catch (e) {
      this.erreur.set((e as Error).message);
    }
  }
}
