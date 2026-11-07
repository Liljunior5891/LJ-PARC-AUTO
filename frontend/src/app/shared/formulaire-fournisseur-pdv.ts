import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { DocumentParc, TYPES_DOCUMENT, TypeDocument } from '../core/models/models';
import { FormsModule } from '@angular/forms';
import { Fournisseur, FournisseurRequete } from '../core/models/achats';
import { AchatsService } from '../core/services/achats.service';
import { Modale } from './modale';
import { PieceJointe } from './piece-jointe';

/** Fiche d'un fournisseur : creation et modification. */
@Component({
  selector: 'app-formulaire-fournisseur-pdv',
  imports: [FormsModule, Modale, PieceJointe],
  styleUrl: './formulaire-client.scss',
  styles: `
    .titre-pieces { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
  `,
  template: `
    <app-modale [titre]="fournisseur() ? 'Modifier ' + fournisseur()!.nom : 'Nouveau fournisseur'"
                [sousTitre]="fournisseur() ? fournisseur()!.code : 'Fiche, coordonnees et delai de paiement'"
                largeur="980px" [erreur]="erreur()" (ferme)="ferme.emit()">
      <form (ngSubmit)="enregistrer()">
        <div class="grille-formulaire">
          <div class="champ" style="grid-column: 1 / -1">
            <label for="f-nom">Raison sociale / nom *</label>
            <input id="f-nom" name="nom" required maxlength="150" [(ngModel)]="f.nom" placeholder="Ex. Brasserie BB Lome" />
          </div>
          <div class="champ">
            <label for="f-contact">Personne a contacter</label>
            <input id="f-contact" name="contact" maxlength="120" [(ngModel)]="f.personneContact" />
          </div>
          <div class="champ">
            <label for="f-tel">Telephone</label>
            <input id="f-tel" name="telephone" type="tel" maxlength="40" [(ngModel)]="f.telephone" />
          </div>
          <div class="champ">
            <label for="f-tel2">Autre telephone</label>
            <input id="f-tel2" name="telephone2" type="tel" maxlength="40" [(ngModel)]="f.telephone2" />
          </div>
          <div class="champ">
            <label for="f-email">E-mail</label>
            <input id="f-email" name="email" type="email" maxlength="150" [(ngModel)]="f.email" />
          </div>
          <div class="champ" style="grid-column: 1 / -1">
            <label for="f-adresse">Adresse</label>
            <input id="f-adresse" name="adresse" maxlength="255" [(ngModel)]="f.adresse" />
          </div>
          <div class="champ">
            <label for="f-ville">Ville</label>
            <input id="f-ville" name="ville" maxlength="80" [(ngModel)]="f.ville" />
          </div>
          <div class="champ">
            <label for="f-pays">Pays</label>
            <input id="f-pays" name="pays" maxlength="80" [(ngModel)]="f.pays" />
          </div>
          <div class="champ">
            <label for="f-nif">NIF</label>
            <input id="f-nif" name="nif" maxlength="60" [(ngModel)]="f.nif" />
          </div>
          <div class="champ">
            <label for="f-rccm">RCCM</label>
            <input id="f-rccm" name="rccm" maxlength="60" [(ngModel)]="f.rccm" />
          </div>
          <div class="champ">
            <label for="f-delai">Delai de paiement (jours)</label>
            <input id="f-delai" name="delai" type="number" min="0" step="1" [(ngModel)]="f.delaiPaiementJours"
                   placeholder="0 = a la livraison" />
            <small>L'echeance de chaque livraison = date de reception + ce delai.</small>
          </div>
          <div class="champ" style="grid-column: 1 / -1">
            <label for="f-conditions">Conditions commerciales</label>
            <textarea id="f-conditions" name="conditions" rows="2" maxlength="4000" [(ngModel)]="f.conditions"
                      placeholder="Remises, minimum de commande, jours de livraison..."></textarea>
          </div>
          <div class="champ" style="grid-column: 1 / -1">
            <label for="f-note">Note</label>
            <input id="f-note" name="note" maxlength="500" [(ngModel)]="f.note" />
          </div>
        </div>

        <!-- Pieces jointes : RCCM, NIF, contrat... envoyees apres l'enregistrement de la fiche. -->
        <section class="formulaire-client">
          <div class="titre-pieces">
            <h3>Pieces jointes</h3>
            <button class="bouton secondaire petit" type="button" (click)="ajouterPiece()">+ Ajouter une piece</button>
          </div>
          @if (documents().length) {
            <ul class="pieces-enregistrees">
              @for (d of documents(); track d.id) {
                <li>
                  <span class="coche" aria-hidden="true">✔</span>
                  <span class="textes">
                    <strong>{{ d.typeLibelle }}</strong>
                    <small>{{ d.numero ? 'N° ' + d.numero : 'Sans numero' }}{{ d.dateExpiration ? ' · expire le ' + d.dateExpiration : '' }}</small>
                  </span>
                  @if (d.fichierDisponible) {
                    <button class="bouton secondaire petit info" type="button" (click)="telecharger(d)">Voir</button>
                  }
                  <button class="retirer" type="button" title="Retirer cette piece" aria-label="Retirer cette piece"
                          (click)="retirerDocument(d)">✕</button>
                </li>
              }
            </ul>
          }
          <div class="pieces">
            @for (piece of pieces(); track piece.cle) {
              <div class="piece">
                <div class="champs-piece">
                  <div class="champ">
                    <label [for]="'ff-type-' + piece.cle">Type de piece</label>
                    <select [id]="'ff-type-' + piece.cle" [name]="'type' + piece.cle" [ngModel]="piece.type"
                            (ngModelChange)="modifierPiece(piece.cle, 'type', $event)">
                      @for (t of typesPieces; track t) {
                        <option [ngValue]="t">{{ libelleType(t) }}</option>
                      }
                    </select>
                  </div>
                  <div class="champ">
                    <label [for]="'ff-numero-' + piece.cle">Numero</label>
                    <input [id]="'ff-numero-' + piece.cle" [name]="'numero' + piece.cle" [ngModel]="piece.numero"
                           (ngModelChange)="modifierPiece(piece.cle, 'numero', $event)" />
                  </div>
                  <div class="champ">
                    <label [for]="'ff-expiration-' + piece.cle">Expire le</label>
                    <input [id]="'ff-expiration-' + piece.cle" [name]="'expiration' + piece.cle" type="date"
                           [ngModel]="piece.dateExpiration" (ngModelChange)="modifierPiece(piece.cle, 'dateExpiration', $event)" />
                  </div>
                </div>
                <div class="depot-piece">
                  <app-piece-jointe [libelle]="libelleType(piece.type)" [fichier]="piece.fichier"
                                    (fichierChange)="modifierPiece(piece.cle, 'fichier', $event)" />
                  <button class="retirer" type="button" title="Retirer cette piece" aria-label="Retirer cette piece"
                          (click)="retirerPiece(piece.cle)">✕</button>
                </div>
              </div>
            }
          </div>
        </section>
        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Annuler</button>
          <button class="bouton" type="submit" [disabled]="envoi() || !f.nom.trim()">
            {{ envoi() ? 'Enregistrement...' : 'Enregistrer' }}
          </button>
        </div>
      </form>
    </app-modale>
  `
})
export class FormulaireFournisseurPdv implements OnInit {
  private readonly service = inject(AchatsService);

  /** Fournisseur a modifier ; nul pour une creation. */
  readonly fournisseur = input<Fournisseur | null>(null);
  readonly enregistre = output<Fournisseur>();
  readonly ferme = output<void>();

  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  f: FournisseurRequete & { nom: string } = { nom: '' };

  /** Pieces proposees pour un fournisseur, la plus demandee en premier. */
  readonly typesPieces: TypeDocument[] = ['RCCM', 'ATTESTATION_NIF', 'CARTE_OPERATEUR_ECONOMIQUE', 'CONTRAT',
    'STATUTS', 'PIECE_REPRESENTANT', 'AUTRE'];
  readonly documents = signal<DocumentParc[]>([]);
  readonly pieces = signal<PieceSaisie[]>([]);
  private prochaineCle = 1;

  ngOnInit(): void {
    const existant = this.fournisseur();
    if (existant) {
      this.f = {
        nom: existant.nom, personneContact: existant.personneContact, telephone: existant.telephone,
        telephone2: existant.telephone2, email: existant.email, adresse: existant.adresse, ville: existant.ville,
        pays: existant.pays, nif: existant.nif, rccm: existant.rccm, delaiPaiementJours: existant.delaiPaiementJours,
        conditions: existant.conditions, note: existant.note
      };
      this.chargerDocuments(existant.id);
    }
    this.ajouterPiece();
  }

  libelleType(type: TypeDocument): string {
    return TYPES_DOCUMENT.find((t) => t.valeur === type)?.libelle ?? type;
  }

  /** Nouvelle ligne de piece, sur le premier type pas encore propose. */
  ajouterPiece(): void {
    const pris = new Set([...this.pieces().map((p) => p.type), ...this.documents().map((d) => d.type)]);
    const type = this.typesPieces.find((t) => !pris.has(t)) ?? 'AUTRE';
    this.pieces.update((l) => [...l, { cle: this.prochaineCle++, type, numero: '', dateExpiration: '', fichier: null }]);
  }

  retirerPiece(cle: number): void {
    this.pieces.update((l) => l.filter((p) => p.cle !== cle));
  }

  modifierPiece<K extends keyof PieceSaisie>(cle: number, champ: K, valeur: PieceSaisie[K]): void {
    this.pieces.update((l) => l.map((p) => (p.cle === cle ? { ...p, [champ]: valeur } : p)));
  }

  telecharger(d: DocumentParc): void {
    this.service.telechargerDocumentFournisseur(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.erreur.set('Piece introuvable.')
    });
  }

  retirerDocument(d: DocumentParc): void {
    this.service.retirerDocumentFournisseur(d.id).subscribe({
      next: () => this.documents.update((l) => l.filter((x) => x.id !== d.id)),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Retrait impossible.')
    });
  }

  enregistrer(): void {
    if (!this.f.nom.trim()) {
      this.erreur.set('Indiquez le nom du fournisseur.');
      return;
    }
    const existant = this.fournisseur();
    const requete: FournisseurRequete = { ...this.f, nom: this.f.nom.trim() };
    const appel = existant
      ? this.service.modifierFournisseur(existant.id, requete)
      : this.service.creerFournisseur(requete);
    this.envoi.set(true);
    this.erreur.set(null);
    appel.pipe(switchMap((f) => this.envoyerPieces(f.id).pipe(map((echecs) => ({ f, echecs }))))).subscribe({
      next: ({ f, echecs }) => {
        this.envoi.set(false);
        if (echecs) {
          // La fiche est enregistree : on reste sur le formulaire pour renvoyer les pieces refusees.
          this.erreur.set(`Fiche enregistree, mais ${echecs} piece(s) n'ont pas pu etre envoyee(s).`);
          return;
        }
        this.enregistre.emit(f);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  /** Envoie les pieces renseignees (fichier ou numero) ; renvoie le nombre d'echecs. */
  private envoyerPieces(fournisseurId: number): Observable<number> {
    const aEnvoyer = this.pieces().filter((p) => p.fichier || p.numero.trim());
    if (!aEnvoyer.length) {
      return of(0);
    }
    return forkJoin(aEnvoyer.map((p) =>
      this.service.ajouterDocumentFournisseur(fournisseurId, {
        type: p.type, libelle: this.libelleType(p.type), numero: p.numero.trim() || undefined,
        dateExpiration: p.dateExpiration || null
      }, p.fichier).pipe(map(() => true), catchError(() => of(false)))
    )).pipe(map((r) => {
      const envoyees = new Set(aEnvoyer.filter((_, i) => r[i]).map((p) => p.cle));
      this.pieces.update((l) => l.filter((p) => !envoyees.has(p.cle)));
      this.chargerDocuments(fournisseurId);
      return r.filter((ok) => !ok).length;
    }));
  }

  private chargerDocuments(id: number): void {
    this.service.documentsFournisseur(id).subscribe({ next: (p) => this.documents.set(p.content) });
  }
}

/** Une piece en cours de saisie, envoyee apres l'enregistrement de la fiche. */
interface PieceSaisie {
  cle: number;
  type: TypeDocument;
  numero: string;
  dateExpiration: string;
  fichier: File | null;
}
