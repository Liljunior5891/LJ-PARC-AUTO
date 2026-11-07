import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { BilanImport, TypeImport } from '../core/models/rapport';
import { RapportService, enregistrerFichier } from '../core/services/rapport.service';
import { Modale } from './modale';

/**
 * Fenetre d'import du point de vente : produits, categories ou stock compte, depuis un
 * fichier CSV ou Excel. Le modele se telecharge pre-rempli, le fichier est
 * verifie sans rien enregistrer, puis importe d'un bloc s'il est sans erreur.
 * Ouverte depuis les Rapports, les Produits et l'Inventaire.
 */
@Component({
  selector: 'app-import-pdv',
  imports: [Modale],
  template: `
    <app-modale titre="Importer un fichier" sousTitre="CSV ou Excel : verifie avant d'etre enregistre" largeur="680px"
                [erreur]="erreur()" (ferme)="ferme.emit()">
      <div class="import">
        @if (!typeFixe()) {
          <div class="types-import" role="radiogroup" aria-label="Type d'import">
            <button type="button" role="radio" [class.actif]="typeImport() === 'PRODUITS'"
                    [attr.aria-checked]="typeImport() === 'PRODUITS'" (click)="changerType('PRODUITS')">
              <strong>📦 Liste des produits</strong>
              <small>Cree ou met a jour le catalogue : nom, categorie, prix, code-barres, seuil...</small>
            </button>
            <button type="button" role="radio" [class.actif]="typeImport() === 'CATEGORIES'"
                    [attr.aria-checked]="typeImport() === 'CATEGORIES'" (click)="changerType('CATEGORIES')">
              <strong>🗂️ Categories</strong>
              <small>Cree ou met a jour les categories : nom, ordre, icone, couleur, active.</small>
            </button>
            <button type="button" role="radio" [class.actif]="typeImport() === 'STOCK'"
                    [attr.aria-checked]="typeImport() === 'STOCK'" (click)="changerType('STOCK')">
              <strong>📋 Stock compte</strong>
              <small>Remplace le stock par les quantites comptees (inventaire).</small>
            </button>
          </div>
        } @else {
          <p class="type-choisi">
            {{ typeImport() === 'PRODUITS'
              ? '📦 Liste des produits : chaque ligne cree un produit ou met a jour celui qui porte le meme code ou code-barres.'
              : typeImport() === 'CATEGORIES'
              ? '🗂️ Categories : chaque ligne cree une categorie ou met a jour celle qui porte le meme nom.'
              : '📋 Stock compte : le stock de chaque produit du fichier est remplace par la quantite indiquee.' }}
          </p>
        }

        <div class="etape">
          <span class="numero">1</span>
          <div>
            <strong>Telechargez le modele</strong>
            <small>Il reprend vos donnees actuelles : modifiez-le dans votre tableur.</small>
            <div class="boutons-modele">
              <button class="bouton secondaire petit info" type="button" (click)="telechargerModele('xlsx')">⤓ Modele Excel</button>
              <button class="bouton secondaire petit info" type="button" (click)="telechargerModele('csv')">⤓ Modele CSV</button>
            </div>
          </div>
        </div>

        <div class="etape">
          <span class="numero">2</span>
          <div>
            <strong>Choisissez votre fichier</strong>
            <small>Format .xlsx ou .csv (separateur point-virgule ou virgule), 5 000 lignes au plus.</small>
            @if (typeImport() === 'PRODUITS') {
              <details class="aide-zip">
                <summary>📷 Importer aussi les photos des produits (archive .zip)</summary>
                <ol>
                  <li>Remplissez le modele des produits comme d'habitude.</li>
                  <li>Nommez chaque photo d'apres le <strong>code du produit</strong> (ex. <code>CIM-001.jpg</code>),
                    ou ecrivez son nom de fichier dans la colonne <strong>Image</strong> du modele.</li>
                  <li>Mettez le tableau et les photos dans un meme fichier .zip (sous-dossiers acceptes), puis choisissez ce .zip ici.</li>
                </ol>
                <small>Photos JPEG, PNG, GIF ou BMP ; elles sont recadrees au carre et reduites comme dans la fiche produit.
                  Archive de 60 Mo au plus. Une photo remplace celle du produit ; sans photo, le produit garde la sienne.</small>
              </details>
            }
            <label class="choix-fichier">
              <input type="file" [accept]="typeImport() === 'PRODUITS'
                       ? '.csv,.xlsx,.zip,text/csv,application/zip,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
                       : '.csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'"
                     hidden (change)="choisirFichier($event)" />
              <span class="bouton secondaire petit">Parcourir...</span>
              <span class="nom-fichier">{{ fichier()?.name ?? 'Aucun fichier choisi' }}</span>
            </label>
          </div>
        </div>

        <div class="etape">
          <span class="numero">3</span>
          <div>
            <strong>Verifiez, puis importez</strong>
            <small>La verification n'enregistre rien. Une seule erreur bloque tout l'import.</small>
            @if (bilan(); as b) {
              <div class="bilan" [class.ko]="b.erreurs.length > 0">
                @if (b.erreurs.length === 0) {
                  <p>✓ {{ b.lignes }} ligne(s) lue(s) :
                    @if (b.type === 'PRODUITS') {
                      {{ b.crees }} produit(s) a creer, {{ b.modifies }} a mettre a jour, {{ b.inchanges }} inchange(s).
                      @if (b.photos) { {{ b.photos }} photo(s) rattachee(s). }
                    } @else if (b.type === 'CATEGORIES') {
                      {{ b.crees }} categorie(s) a creer, {{ b.modifies }} a mettre a jour, {{ b.inchanges }} inchangee(s).
                    } @else {
                      {{ b.modifies }} stock(s) a corriger, {{ b.inchanges }} deja juste(s).
                    }
                  </p>
                } @else {
                  <p>✕ {{ b.erreurs.length }} erreur(s) a corriger dans le fichier :</p>
                  <ul>
                    @for (e of b.erreurs.slice(0, 50); track $index) {
                      <li><strong>Ligne {{ e.ligne }}</strong> : {{ e.message }}</li>
                    }
                  </ul>
                }
              </div>
              @if (b.avertissements?.length) {
                <div class="avertissements">
                  <p>⚠ {{ b.avertissements!.length }} remarque(s), sans effet sur l'import :</p>
                  <ul>
                    @for (a of b.avertissements!.slice(0, 50); track $index) { <li>{{ a }}</li> }
                  </ul>
                </div>
              }
            }
          </div>
        </div>

        <div class="actions-formulaire">
          <button class="bouton secondaire" type="button" (click)="ferme.emit()">Fermer</button>
          <button class="bouton secondaire" type="button" [disabled]="!fichier() || envoi()" (click)="importer(true)">
            {{ envoi() ? 'Verification...' : 'Verifier le fichier' }}
          </button>
          <button class="bouton info" type="button" [disabled]="!bilan() || bilan()!.erreurs.length > 0 || envoi()"
                  (click)="importer(false)">
            Importer
          </button>
        </div>
      </div>
    </app-modale>
  `,
  styles: `
    .import { display: flex; flex-direction: column; gap: 1rem; }
    .type-choisi { margin: 0; padding: 0.7rem 0.9rem; border-radius: 10px; background: var(--couleur-fond); font-size: 0.9rem; }
    .types-import { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.6rem; }
    .types-import button {
      display: flex; flex-direction: column; gap: 0.3rem; padding: 0.8rem;
      border: 2px solid var(--couleur-bordure); border-radius: 12px; background: var(--couleur-surface);
      font: inherit; text-align: left; cursor: pointer;
    }
    .types-import small { color: var(--couleur-texte-doux); }
    .types-import button.actif {
      border-color: var(--couleur-primaire); background: color-mix(in srgb, var(--couleur-primaire) 6%, #fff);
    }
    .etape { display: flex; gap: 0.75rem; }
    .numero {
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
      width: 28px; height: 28px; border-radius: 50%; background: var(--couleur-primaire); color: #fff; font-weight: 700;
    }
    .etape > div { display: flex; flex-direction: column; gap: 0.3rem; flex: 1; min-width: 0; }
    .etape > div > small { color: var(--couleur-texte-doux); }
    .boutons-modele { display: flex; gap: 0.4rem; }
    .choix-fichier { display: flex; align-items: center; gap: 0.6rem; cursor: pointer; }
    .nom-fichier {
      overflow: hidden; color: var(--couleur-texte-doux); font-size: 0.9rem; text-overflow: ellipsis; white-space: nowrap;
    }
    .bilan {
      margin-top: 0.4rem; padding: 0.7rem 0.9rem; border-radius: 10px;
      background: color-mix(in srgb, var(--couleur-succes) 10%, #fff); color: var(--couleur-succes);
    }
    .bilan p { margin: 0; font-weight: 600; }
    .bilan.ko { background: #fde8e4; color: var(--couleur-danger); }
    .bilan ul {
      max-height: 180px; margin: 0.4rem 0 0; padding-left: 1.1rem; overflow-y: auto;
      color: var(--couleur-texte); font-size: 0.85rem;
    }
    .aide-zip {
      margin: 0.2rem 0; padding: 0.55rem 0.8rem; border: 1px dashed #b7c9e2; border-radius: 10px; background: #f6f9fe;
      font-size: 0.85rem;
    }
    .aide-zip summary { cursor: pointer; font-weight: 600; color: #1f5fae; }
    .aide-zip ol { margin: 0.5rem 0 0.4rem; padding-left: 1.2rem; }
    .aide-zip li { margin-bottom: 0.2rem; }
    .aide-zip code { padding: 0 0.25rem; border-radius: 4px; background: #e6eef9; }
    .aide-zip small { color: var(--couleur-texte-doux); }
    .avertissements {
      margin-top: 0.4rem; padding: 0.6rem 0.9rem; border-radius: 10px; background: #fff6e5; color: #8a5a00; font-size: 0.85rem;
    }
    .avertissements p { margin: 0; font-weight: 600; }
    .avertissements ul { max-height: 140px; margin: 0.3rem 0 0; padding-left: 1.1rem; overflow-y: auto; }
    @media (max-width: 700px) { .types-import { grid-template-columns: 1fr; } }
  `
})
export class ImportPdv implements OnInit {
  private readonly service = inject(RapportService);

  /** Type propose a l'ouverture. */
  readonly type = input<TypeImport>('PRODUITS');
  /** Vrai quand l'ecran appelant impose le type (Produits, Inventaire). */
  readonly typeFixe = input(false);
  readonly ferme = output<void>();
  /** Import enregistre : le bilan, pour le message et le rechargement de l'ecran appelant. */
  readonly importe = output<BilanImport>();

  readonly typeImport = signal<TypeImport>('PRODUITS');
  readonly fichier = signal<File | null>(null);
  readonly bilan = signal<BilanImport | null>(null);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    this.typeImport.set(this.type());
  }

  changerType(type: TypeImport): void {
    this.typeImport.set(type);
    this.bilan.set(null);
  }

  telechargerModele(format: 'csv' | 'xlsx'): void {
    this.service.modele(this.typeImport(), format).subscribe({
      next: (f) => enregistrerFichier(f),
      error: () => this.erreur.set('Telechargement du modele impossible.')
    });
  }

  choisirFichier(evenement: Event): void {
    const champ = evenement.target as HTMLInputElement;
    this.fichier.set(champ.files?.[0] ?? null);
    this.bilan.set(null);
    this.erreur.set(null);
    champ.value = '';
  }

  /** Verification (simulation), puis import reel une fois le fichier sans erreur. */
  importer(simulation: boolean): void {
    const fichier = this.fichier();
    if (!fichier || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.importer(this.typeImport(), fichier, simulation).subscribe({
      next: (bilan) => {
        this.envoi.set(false);
        this.bilan.set(bilan);
        if (bilan.applique) {
          this.importe.emit(bilan);
        }
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Import impossible.');
      }
    });
  }
}

/** Message de fin d'import, commun aux ecrans qui ouvrent la fenetre. */
export function messageImport(bilan: BilanImport): string {
  if (bilan.type === 'CATEGORIES') {
    return `Import termine : ${bilan.crees} categorie(s) creee(s), ${bilan.modifies} mise(s) a jour, ${bilan.inchanges} inchangee(s).`;
  }
  return bilan.type === 'PRODUITS'
    ? `Import termine : ${bilan.crees} produit(s) cree(s), ${bilan.modifies} mis a jour, ${bilan.inchanges} inchange(s)`
      + (bilan.photos ? `, ${bilan.photos} photo(s) ajoutee(s).` : '.')
    : `Import termine : ${bilan.modifies} stock(s) corrige(s), ${bilan.inchanges} deja juste(s).`;
}
