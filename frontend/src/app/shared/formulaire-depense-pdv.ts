import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, catchError, of, switchMap } from 'rxjs';
import { CategorieDepense, Depense, DepenseAPayer, Fournisseur, ModeSortie } from '../core/models/achats';
import { AchatsService } from '../core/services/achats.service';
import { ChoixSortie } from './choix-sortie';
import { Modale } from './modale';
import { PieceJointe } from './piece-jointe';

/**
 * Saisie d'une depense (salaire, loyer, electricite...) avec son moyen de
 * paiement et son justificatif. En modification, montant et moyen de paiement
 * restent figes : pour les changer, on annule et on ressaisit.
 */
@Component({
  selector: 'app-formulaire-depense-pdv',
  imports: [FormsModule, DecimalPipe, Modale, ChoixSortie, PieceJointe],
  template: `
    <app-modale [titre]="depense() ? 'Depense ' + depense()!.numero : (aPayer() ? 'Payer ' + aPayer()!.libelle : 'Nouvelle depense')"
                [sousTitre]="aPayer() ? 'Mois de ' + aPayer()!.periodeLibelle : 'Salaire, loyer, electricite, eau, transport...'"
                largeur="820px" [erreur]="erreur()" (ferme)="ferme.emit()">
      <form (ngSubmit)="enregistrer()">
        <div class="grille-formulaire">
          <div class="champ">
            <label for="d-categorie">Categorie *</label>
            <select id="d-categorie" name="categorie" required [(ngModel)]="categorieId">
              <option [ngValue]="null" disabled>Choisissez</option>
              @for (c of categories(); track c.id) {
                @if (c.actif || c.id === categorieId) {
                  <option [ngValue]="c.id">{{ c.icone ? c.icone + ' ' : '' }}{{ c.nom }}</option>
                }
              }
            </select>
          </div>
          <div class="champ">
            <label for="d-libelle">Libelle *</label>
            <input id="d-libelle" name="libelle" required maxlength="150" [(ngModel)]="libelle"
                   placeholder="Ex. Facture CEET octobre, salaire de Kossi" />
          </div>
          <div class="champ">
            <label for="d-beneficiaire">Beneficiaire</label>
            <input id="d-beneficiaire" name="beneficiaire" maxlength="150" [(ngModel)]="beneficiaire"
                   placeholder="Employe, proprietaire, societe..." />
          </div>
          <div class="champ">
            <label for="d-fournisseur">Fournisseur (facultatif)</label>
            <select id="d-fournisseur" name="fournisseur" [(ngModel)]="fournisseurId">
              <option [ngValue]="null">—</option>
              @for (f of fournisseurs(); track f.id) {
                <option [ngValue]="f.id">{{ f.nom }}</option>
              }
            </select>
          </div>
          @if (depense(); as d) {
            <div class="champ">
              <label>Montant</label>
              <p><strong>{{ d.montant | number: '1.0-0' }} F</strong> · {{ d.modeLibelle }}{{ d.caisseNom ? ' (' + d.caisseNom + ')' : '' }}</p>
              <small>Pour changer le montant ou le moyen de paiement, annulez la depense et ressaisissez-la.</small>
            </div>
          } @else {
            <div class="champ">
              <label for="d-montant">Montant (F) *</label>
              <input id="d-montant" name="montant" type="number" min="1" step="any" required [(ngModel)]="montant" />
            </div>
          }
          <div class="champ">
            <label for="d-date">Date</label>
            <input id="d-date" name="date" type="date" [max]="aujourdhui" [(ngModel)]="dateDepense" />
          </div>
        </div>

        @if (!depense()) {
          <div class="champ" style="margin-top: 0.8rem">
            <label>Paye par</label>
            <app-choix-sortie [(mode)]="mode" [(sessionId)]="sessionId" />
          </div>
        }

        <div class="grille-formulaire" style="margin-top: 0.8rem">
          @if (mode() !== 'CAISSE' || depense()) {
            <div class="champ">
              <label for="d-ref">Reference</label>
              <input id="d-ref" name="reference" maxlength="80" [(ngModel)]="reference" placeholder="N° de facture, de cheque, ID de transaction" />
            </div>
          }
          <div class="champ">
            <label for="d-note">Note</label>
            <input id="d-note" name="note" maxlength="500" [(ngModel)]="note" />
          </div>
        </div>

        @if (!depense()) {
          <div class="champ" style="margin-top: 0.8rem">
            <label>Justificatif</label>
            <app-piece-jointe libelle="Facture, recu ou bulletin de paie" [fichier]="fichier()"
                              (fichierChange)="fichier.set($event)" />
          </div>
        }

        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Annuler</button>
          <button class="bouton" type="submit" [disabled]="envoi()">
            {{ envoi() ? 'Enregistrement...' : (depense() ? 'Enregistrer' : 'Enregistrer la depense') }}
          </button>
        </div>
      </form>
    </app-modale>
  `
})
export class FormulaireDepensePdv implements OnInit {
  private readonly service = inject(AchatsService);

  /** Depense a corriger ; nulle pour une saisie. */
  readonly depense = input<Depense | null>(null);
  /** Mois d'une depense recurrente a payer, qui pre-remplit la saisie. */
  readonly aPayer = input<DepenseAPayer | null>(null);
  readonly enregistre = output<Depense>();
  readonly ferme = output<void>();

  readonly categories = signal<CategorieDepense[]>([]);
  readonly fournisseurs = signal<Fournisseur[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly mode = signal<ModeSortie>('CAISSE');
  readonly sessionId = signal<number | null>(null);
  readonly aujourdhui = new Date().toISOString().slice(0, 10);

  categorieId: number | null = null;
  libelle = '';
  beneficiaire = '';
  fournisseurId: number | null = null;
  montant: number | null = null;
  dateDepense = this.aujourdhui;
  reference = '';
  note = '';
  /** Justificatif choisi, envoye juste apres l'enregistrement de la depense. */
  readonly fichier = signal<File | null>(null);

  ngOnInit(): void {
    this.service.categoriesDepense().subscribe({ next: (c) => this.categories.set(c) });
    this.service.fournisseursActifs().subscribe({ next: (f) => this.fournisseurs.set(f) });
    const d = this.depense();
    const p = this.aPayer();
    if (d) {
      this.categorieId = d.categorieId;
      this.libelle = d.libelle;
      this.beneficiaire = d.beneficiaire ?? '';
      this.fournisseurId = d.fournisseurId ?? null;
      this.dateDepense = d.dateDepense;
      this.reference = d.reference ?? '';
      this.note = d.note ?? '';
    } else if (p) {
      this.categorieId = p.categorieId;
      this.libelle = `${p.libelle} - ${p.periodeLibelle}`;
      this.beneficiaire = p.beneficiaire ?? '';
      this.montant = p.montant;
      if (p.mode) {
        this.mode.set(p.mode);
      }
    }
  }

  enregistrer(): void {
    if (!this.categorieId || !this.libelle.trim()) {
      this.erreur.set('Choisissez la categorie et indiquez le libelle.');
      return;
    }
    const d = this.depense();
    let appel: Observable<Depense>;
    if (d) {
      appel = this.service.modifierDepense(d.id, {
        categorieId: this.categorieId, libelle: this.libelle.trim(), beneficiaire: this.beneficiaire.trim() || null,
        fournisseurId: this.fournisseurId, dateDepense: this.dateDepense || null,
        reference: this.reference.trim() || null, note: this.note.trim() || null
      });
    } else {
      if (!this.montant || this.montant <= 0) {
        this.erreur.set('Indiquez le montant.');
        return;
      }
      if (this.mode() === 'CAISSE' && !this.sessionId()) {
        this.erreur.set('Choisissez la caisse d\'ou sortent les especes.');
        return;
      }
      const p = this.aPayer();
      const fichier = this.fichier();
      appel = this.service.creerDepense({
        categorieId: this.categorieId, libelle: this.libelle.trim(), beneficiaire: this.beneficiaire.trim() || null,
        fournisseurId: this.fournisseurId, montant: this.montant, dateDepense: this.dateDepense || null,
        mode: this.mode(), sessionId: this.mode() === 'CAISSE' ? this.sessionId() : null,
        reference: this.mode() === 'CAISSE' ? null : this.reference.trim() || null, note: this.note.trim() || null,
        recurrenteId: p?.recurrenteId ?? null, periode: p?.periode ?? null
      }).pipe(switchMap((cree) => !fichier ? of(cree)
        // La depense est enregistree : un justificatif refuse n'empeche pas de la garder, il se joindra plus tard.
        : this.service.joindreJustificatif(cree.id, fichier, true).pipe(catchError(() => of(cree)))));
    }
    this.envoi.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (resultat) => {
        this.envoi.set(false);
        this.enregistre.emit(resultat);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
