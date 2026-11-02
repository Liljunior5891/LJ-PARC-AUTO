import { Component, input, model, signal } from '@angular/core';
import { reduirePhotoVehicule } from './photo-vehicule';

/**
 * Photos supplementaires d'un produit ou d'un vehicule vendu en ligne : ajout
 * (plusieurs a la fois, clic ou glisser-deposer), retrait et ordre. Les photos
 * sont reduites dans le navigateur avant l'envoi. Dans la boutique en ligne,
 * un clic sur la photo principale ouvre cette galerie.
 */
@Component({
  selector: 'app-galerie-photos',
  template: `
    <div class="galerie-photos" [class.survol]="survol()" (dragover)="$event.preventDefault(); survol.set(true)"
         (dragleave)="survol.set(false)" (drop)="deposer($event)">
      <div class="entete">
        <strong>Autres photos en ligne</strong>
        <small>Visibles dans la boutique en cliquant sur la photo principale · {{ photos().length }}/{{ max() }}</small>
      </div>
      <div class="vignettes">
        @for (photo of photos(); track $index; let i = $index; let premier = $first; let dernier = $last) {
          <div class="vignette" [style.background-image]="'url(' + photo + ')'">
            <div class="outils">
              <button type="button" title="Avancer" aria-label="Avancer la photo" [disabled]="premier"
                      (click)="deplacer(i, -1)">‹</button>
              <button type="button" class="retirer" title="Retirer" aria-label="Retirer la photo"
                      (click)="retirer(i)">✕</button>
              <button type="button" title="Reculer" aria-label="Reculer la photo" [disabled]="dernier"
                      (click)="deplacer(i, 1)">›</button>
            </div>
          </div>
        }
        @if (photos().length < max()) {
          <label class="ajout" [class.occupe]="chargement()">
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden
                   (change)="choisir($event)" />
            <span>{{ chargement() ? '...' : '+' }}</span>
            <small>Ajouter</small>
          </label>
        }
      </div>
      @if (erreur()) {
        <small class="erreur">{{ erreur() }}</small>
      }
    </div>
  `,
  styles: [`
    .galerie-photos { border: 1px dashed var(--bordure, #d0d5dd); border-radius: 10px; padding: 10px 12px;
      display: flex; flex-direction: column; gap: 8px; }
    .galerie-photos.survol { border-color: var(--primaire, #2563eb); background: rgba(37, 99, 235, .05); }
    .entete { display: flex; flex-direction: column; gap: 2px; }
    .entete small { color: var(--texte-secondaire, #667085); }
    .vignettes { display: flex; flex-wrap: wrap; gap: 8px; }
    .vignette, .ajout { width: 84px; height: 84px; border-radius: 8px; flex: none; }
    .vignette { background: #f2f4f7 center / cover no-repeat; position: relative; overflow: hidden;
      border: 1px solid var(--bordure, #e4e7ec); }
    .outils { position: absolute; inset: auto 0 0 0; display: flex; justify-content: space-between;
      background: rgba(0, 0, 0, .45); }
    .outils button { background: none; border: 0; color: #fff; cursor: pointer; font-size: 14px;
      padding: 2px 7px; line-height: 1.2; }
    .outils button:disabled { opacity: .3; cursor: default; }
    .outils .retirer { font-size: 12px; }
    .ajout { border: 1px dashed var(--bordure, #d0d5dd); display: flex; flex-direction: column;
      align-items: center; justify-content: center; cursor: pointer; color: var(--texte-secondaire, #667085); }
    .ajout span { font-size: 24px; line-height: 1; }
    .ajout.occupe { pointer-events: none; opacity: .6; }
    .erreur { color: #b42318; }
  `]
})
export class GaleriePhotos {
  readonly photos = model<string[]>([]);
  readonly max = input(8);

  readonly survol = signal(false);
  readonly chargement = signal(false);
  readonly erreur = signal('');

  choisir(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    this.ajouter(Array.from(champ.files ?? []));
    champ.value = '';
  }

  deposer(evenement: DragEvent): void {
    evenement.preventDefault();
    this.survol.set(false);
    this.ajouter(Array.from(evenement.dataTransfer?.files ?? []));
  }

  retirer(index: number): void {
    this.photos.update(liste => liste.filter((_, i) => i !== index));
  }

  deplacer(index: number, sens: number): void {
    this.photos.update(liste => {
      const copie = [...liste];
      const cible = index + sens;
      if (cible < 0 || cible >= copie.length) {
        return liste;
      }
      [copie[index], copie[cible]] = [copie[cible], copie[index]];
      return copie;
    });
  }

  private async ajouter(fichiers: File[]): Promise<void> {
    this.erreur.set('');
    const place = this.max() - this.photos().length;
    if (!fichiers.length || place <= 0) {
      return;
    }
    if (fichiers.length > place) {
      this.erreur.set(`Au plus ${this.max()} photos : seules les ${place} premieres sont ajoutees.`);
    }
    this.chargement.set(true);
    const nouvelles: string[] = [];
    for (const fichier of fichiers.slice(0, place)) {
      try {
        nouvelles.push(await reduirePhotoVehicule(fichier));
      } catch (e) {
        this.erreur.set((e as Error).message);
      }
    }
    this.photos.update(liste => [...liste, ...nouvelles]);
    this.chargement.set(false);
  }
}
