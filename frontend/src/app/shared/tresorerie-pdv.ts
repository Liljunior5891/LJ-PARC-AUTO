import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Billettage } from '../core/models/pdv';
import { CoupureTresorerie, Degagement, EtatRemiseSession, LIBELLES_STATUT_REMISE } from '../core/models/options-pdv';
import { OptionsPdvService } from '../core/services/options-pdv.service';
import { SessionTravailService } from '../core/services/session-travail.service';
import { Modale } from './modale';
import { SaisieBillettage, versBillettage } from './session-caisse';
import { PieceJointe } from './piece-jointe';

function message(reponse: unknown, defaut: string): string {
  return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
}

/** Total d'un billettage saisi. */
function totalDe(quantites: Record<number, number | null>): number {
  return versBillettage(quantites).reduce((s, c) => s + c.valeur * c.quantite, 0);
}

/**
 * Comptage puis envoi d'especes : remise du caissier au gerant, ou degagement du
 * coffre du gerant vers l'administrateur. Le montant attendu et l'ecart
 * s'affichent pendant le comptage ; au-dela de la tolerance, une justification
 * est demandee.
 */
@Component({
  selector: 'app-envoi-billettage',
  imports: [DecimalPipe, FormsModule, Modale, SaisieBillettage],
  template: `
    <app-modale [titre]="titre()" [sousTitre]="sousTitre()" largeur="940px" [erreur]="erreur()" (ferme)="ferme.emit()">
      <div class="comptage">
        <div>
          <p class="consigne">{{ consigne() }}</p>
          <app-saisie-billettage [(quantites)]="quantites" />
        </div>
        <aside class="controle-direct" aria-live="polite">
          <h3>Controle</h3>
          <dl>
            <div class="attendu"><dt>{{ libelleAttendu() }}</dt><dd>{{ attendu() | number: '1.0-0' }} F</dd></div>
            <div class="compte"><dt>Compte</dt><dd>{{ total() | number: '1.0-0' }} F</dd></div>
            <div class="ecart-direct" [class.juste]="ecart() === 0" [class.manque]="ecart() < 0" [class.surplus]="ecart() > 0">
              <dt>{{ ecart() === 0 ? 'Juste' : ecart() < 0 ? 'Il manque' : 'Excedent' }}</dt>
              <dd>{{ ecart() | number: '1.0-0' }} F</dd>
            </div>
          </dl>
          <div class="champ">
            <label for="env-justif">Justification{{ justificationRequise() ? ' *' : '' }}</label>
            <textarea id="env-justif" rows="3" maxlength="2000" [(ngModel)]="justification"
                      [class.requise]="justificationRequise() && !justification.trim()"
                      [placeholder]="justificationRequise() ? 'Expliquez l\\'ecart' : 'Facultatif'"></textarea>
          </div>
        </aside>
      </div>
      <div class="actions-formulaire">
        <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Annuler</button>
        <button class="bouton succes" type="button" [disabled]="!envoyable()" (click)="envoyer()">
          {{ envoi() ? 'Envoi...' : libelleAction() + ' ' + (total() | number: '1.0-0') + ' F' }}
        </button>
      </div>
    </app-modale>
  `,
  styleUrl: './tresorerie-pdv.scss'
})
export class EnvoiBillettage {
  readonly titre = input.required<string>();
  readonly sousTitre = input('');
  readonly consigne = input('Comptez les billets et les pieces.');
  readonly libelleAttendu = input('Attendu');
  readonly libelleAction = input('Envoyer');
  readonly attendu = input.required<number>();
  readonly tolerance = input(0);
  /** Appel d'envoi fourni par l'appelant ; l'erreur du serveur s'affiche ici. */
  readonly envoyerVers = input.required<(billettage: Billettage, justification?: string) => import('rxjs').Observable<unknown>>();
  readonly ferme = output<void>();
  readonly envoye = output<void>();

  quantites: Record<number, number | null> = {};
  justification = '';
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  total(): number {
    return totalDe(this.quantites);
  }

  ecart(): number {
    return this.total() - this.attendu();
  }

  justificationRequise(): boolean {
    return Math.abs(this.ecart()) > this.tolerance();
  }

  envoyable(): boolean {
    return !this.envoi() && this.total() > 0 && (!this.justificationRequise() || !!this.justification.trim());
  }

  envoyer(): void {
    if (!this.envoyable()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.envoyerVers()(versBillettage(this.quantites), this.justification.trim() || undefined).subscribe({
      next: () => {
        this.envoi.set(false);
        this.envoye.emit();
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Envoi impossible.'));
      }
    });
  }
}

/**
 * Recompte par celui qui recoit l'argent (gerant pour une remise, administrateur
 * pour un degagement) : le billettage declare est repris, a corriger si besoin.
 * Valider fait circuler la somme recomptee ; refuser renvoie a l'expediteur.
 */
@Component({
  selector: 'app-recompte-billettage',
  imports: [DecimalPipe, FormsModule, Modale, SaisieBillettage],
  template: `
    <app-modale [titre]="titre()" [sousTitre]="sousTitre()" largeur="980px" [erreur]="erreur()" (ferme)="ferme.emit()">
      @if (refus()) {
        <div class="champ">
          <label for="rec-motif">Motif du refus *</label>
          <textarea id="rec-motif" rows="3" maxlength="2000" [(ngModel)]="motif"
                    placeholder="Ex. billettage faux, recomptez avant de renvoyer"></textarea>
        </div>
        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="refus.set(false)">Retour</button>
          <button class="bouton danger" type="button" [disabled]="envoi() || !motif.trim()" (click)="refuser()">Refuser</button>
        </div>
      } @else {
        <div class="comptage">
          <div>
            <p class="consigne">Recomptez : le billettage declare est repris, corrigez ce qui differe.</p>
            <app-saisie-billettage [(quantites)]="quantites" />
          </div>
          <aside class="controle-direct" aria-live="polite">
            <h3>Controle</h3>
            <dl>
              <div><dt>{{ libelleAttendu() }}</dt><dd>{{ attendu() | number: '1.0-0' }} F</dd></div>
              <div class="attendu"><dt>Declare par {{ expediteur() }}</dt><dd>{{ declare() | number: '1.0-0' }} F</dd></div>
              <div class="compte"><dt>Recompte</dt><dd>{{ total() | number: '1.0-0' }} F</dd></div>
              <div class="ecart-direct" [class.juste]="ecart() === 0" [class.manque]="ecart() < 0" [class.surplus]="ecart() > 0">
                <dt>{{ ecart() === 0 ? 'Conforme' : ecart() < 0 ? 'Il manque' : 'Excedent' }}</dt>
                <dd>{{ ecart() | number: '1.0-0' }} F</dd>
              </div>
            </dl>
            @if (justificationExpediteur()) {
              <p class="note-expediteur"><strong>Note de {{ expediteur() }} :</strong> {{ justificationExpediteur() }}</p>
            }
            <div class="champ">
              <label for="rec-comment">Commentaire{{ commentaireRequis() ? ' *' : '' }}</label>
              <textarea id="rec-comment" rows="3" maxlength="2000" [(ngModel)]="commentaire"
                        [class.requise]="commentaireRequis() && !commentaire.trim()"
                        [placeholder]="commentaireRequis() ? 'Indiquez l\\'ecart constate' : 'Facultatif'"></textarea>
            </div>
            <p class="destination">Apres validation : <strong>{{ destination() }}</strong></p>
          </aside>
        </div>
        <div class="actions-formulaire">
          <button class="bouton danger" type="button" (click)="refus.set(true)">Refuser</button>
          <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Fermer</button>
          <button class="bouton succes" type="button" [disabled]="!validable()" (click)="valider()">
            {{ envoi() ? 'Validation...' : 'Valider ' + (total() | number: '1.0-0') + ' F' }}
          </button>
        </div>
      }
    </app-modale>
  `,
  styleUrl: './tresorerie-pdv.scss'
})
export class RecompteBillettage implements OnInit {
  readonly titre = input.required<string>();
  readonly sousTitre = input('');
  readonly expediteur = input.required<string>();
  readonly libelleAttendu = input('Attendu');
  readonly attendu = input.required<number>();
  readonly declare = input.required<number>();
  readonly coupures = input.required<CoupureTresorerie[]>();
  readonly justificationExpediteur = input<string | undefined>();
  readonly destination = input.required<string>();
  readonly tolerance = input(0);
  readonly validerVers = input.required<(billettage: Billettage, commentaire?: string) => import('rxjs').Observable<unknown>>();
  readonly refuserVers = input.required<(motif: string) => import('rxjs').Observable<unknown>>();
  readonly ferme = output<void>();
  readonly traite = output<'valide' | 'refuse'>();

  quantites: Record<number, number | null> = {};
  commentaire = '';
  motif = '';
  readonly refus = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  ngOnInit(): void {
    const q: Record<number, number | null> = {};
    for (const c of this.coupures()) {
      q[c.valeur] = c.quantite;
    }
    this.quantites = q;
  }

  total(): number {
    return totalDe(this.quantites);
  }

  ecart(): number {
    return this.total() - this.declare();
  }

  commentaireRequis(): boolean {
    return Math.abs(this.ecart()) > this.tolerance();
  }

  validable(): boolean {
    return !this.envoi() && this.total() > 0 && (!this.commentaireRequis() || !!this.commentaire.trim());
  }

  valider(): void {
    if (!this.validable()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.validerVers()(versBillettage(this.quantites), this.commentaire.trim() || undefined).subscribe({
      next: () => {
        this.envoi.set(false);
        this.traite.emit('valide');
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Validation impossible.'));
      }
    });
  }

  refuser(): void {
    this.envoi.set(true);
    this.refuserVers()(this.motif.trim()).subscribe({
      next: () => {
        this.envoi.set(false);
        this.traite.emit('refuse');
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Refus impossible.'));
      }
    });
  }
}

/**
 * Bouton « Remettre ma recette » de la barre de caisse, quand le circuit des
 * especes est active : affiche la remise en attente, ou ouvre le comptage.
 */
@Component({
  selector: 'app-bouton-remise',
  imports: [DecimalPipe, DatePipe, Modale, EnvoiBillettage],
  template: `
    @if (options.circuitActif()) {
      <button type="button" class="bouton-remise" [class.attente]="etat()?.enAttente" [disabled]="!sessions.courante()"
              (click)="ouvrir()" [title]="sessions.courante() ? 'Remettre la recette de ' + sessions.courante()!.caisseNom + ' au gerant' : 'Aucune caisse ouverte'">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16v12H4z" /><path d="M8 7V5h8v2M12 11v5M9.5 13.5L12 16l2.5-2.5" /></svg>
        <span>{{ etat()?.enAttente ? 'Remise en attente' : 'Remettre ma recette' }}</span>
      </button>
    }
    @if (comptage(); as e) {
      <app-envoi-billettage [titre]="'Remettre la recette de ' + e.caisseNom" [sousTitre]="'Session ' + e.sessionNumero"
                            consigne="Comptez les especes a remettre au gerant. Le fond de caisse reste dans le tiroir."
                            libelleAttendu="A remettre (tiroir − fond)" libelleAction="Envoyer au gerant"
                            [attendu]="e.aRemettre" [tolerance]="options.reglages()?.ecartTolere ?? 0"
                            [envoyerVers]="envoyerVers(e.sessionId)" (ferme)="comptage.set(null)" (envoye)="envoyee()" />
    }
    @if (suivi(); as e) {
      <app-modale titre="Remise en attente" [sousTitre]="e.caisseNom + ' · session ' + e.sessionNumero" largeur="520px"
                  (ferme)="suivi.set(null)">
        @if (e.enAttente; as r) {
          <p>La remise <strong>{{ r.numero }}</strong> de <strong>{{ r.montantDeclare | number: '1.0-0' }} F</strong>,
            envoyee le {{ r.envoyeeLe | date: "dd/MM 'a' HH:mm" }}, attend la validation du gerant.</p>
          <p class="aide">Le gerant recompte, puis valide : l'argent quitte alors le tiroir pour son coffre.</p>
        }
        @if (dernierRefus(); as r) {
          <div class="message erreur">Remise {{ r.numero }} refusee par {{ r.traiteeParNom }} : {{ r.commentaire }}</div>
        }
        <div class="actions-formulaire"><button class="bouton" type="button" (click)="suivi.set(null)">Fermer</button></div>
      </app-modale>
    }
  `,
  styles: `
    :host { display: contents; }
    .bouton-remise {
      display: inline-flex; align-items: center; gap: 0.45rem; flex-shrink: 0; height: 44px; padding: 0 0.95rem;
      border: 1px solid #a9d8c0; border-radius: 12px; background: #e6f6ee; color: #157a37;
      font: inherit; font-weight: 700; cursor: pointer; transition: transform 0.15s ease, box-shadow 0.15s ease;
    }
    .bouton-remise.attente { border-color: #f0cf8f; background: #fff4dc; color: #9a5b00; }
    .bouton-remise:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 14px rgba(21, 122, 55, 0.2); }
    .bouton-remise:disabled { opacity: 0.45; cursor: not-allowed; }
    svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
    .aide { color: var(--couleur-texte-doux); font-size: 0.9rem; }
    @media (max-width: 820px) { span { display: none; } }
  `
})
export class BoutonRemise {
  readonly options = inject(OptionsPdvService);
  readonly sessions = inject(SessionTravailService);
  readonly etat = signal<EtatRemiseSession | null>(null);
  readonly comptage = signal<EtatRemiseSession | null>(null);
  readonly suivi = signal<EtatRemiseSession | null>(null);
  readonly dernierRefus = computed(() => {
    const derniere = this.etat()?.remises[0];
    return derniere?.statut === 'REFUSEE' ? derniere : null;
  });
  readonly libelles = LIBELLES_STATUT_REMISE;

  ouvrir(): void {
    const session = this.sessions.courante();
    if (!session) {
      return;
    }
    this.options.etatSession(session.id).subscribe({
      next: (e) => {
        this.etat.set(e);
        if (e.enAttente) {
          this.suivi.set(e);
        } else {
          this.comptage.set(e);
        }
      }
    });
  }

  envoyerVers(sessionId: number) {
    return (billettage: Billettage, justification?: string) => this.options.envoyerRemise(sessionId, billettage, justification);
  }

  envoyee(): void {
    this.comptage.set(null);
    const session = this.sessions.courante();
    if (session) {
      this.options.etatSession(session.id).subscribe({ next: (e) => {
        this.etat.set(e);
        this.suivi.set(e);
      } });
    }
  }
}

/**
 * Depot en banque des especes du coffre du gerant : billettage, banque,
 * reference et date du bordereau, et la piece (bordereau ou releve) que
 * l'administrateur verifiera. Sans piece, rien ne part.
 */
@Component({
  selector: 'app-depot-banque',
  imports: [DecimalPipe, FormsModule, Modale, SaisieBillettage, PieceJointe],
  template: `
    <app-modale titre="Deposer mon coffre en banque" sousTitre="L'administrateur verifiera le bordereau avant de valider"
                largeur="980px" [erreur]="erreur()" (ferme)="ferme.emit()">
      <div class="comptage">
        <div>
          <p class="consigne">Comptez les especes deposees en banque.</p>
          <app-saisie-billettage [(quantites)]="quantites" />
        </div>
        <aside class="controle-direct" aria-live="polite">
          <h3>Depot</h3>
          <dl>
            <div><dt>Solde du coffre</dt><dd>{{ solde() | number: '1.0-0' }} F</dd></div>
            <div class="compte"><dt>Depose</dt><dd>{{ total() | number: '1.0-0' }} F</dd></div>
            <div class="ecart-direct" [class.juste]="ecart() === 0" [class.manque]="ecart() < 0" [class.surplus]="ecart() > 0">
              <dt>{{ ecart() === 0 ? 'Coffre vide apres depot' : ecart() < 0 ? 'Reste au coffre' : 'Plus que le solde' }}</dt>
              <dd>{{ -ecart() | number: '1.0-0' }} F</dd>
            </div>
          </dl>
          <div class="champ">
            <label for="dep-banque">Banque</label>
            <input id="dep-banque" maxlength="100" [(ngModel)]="banque" placeholder="Ex. Ecobank, Orabank" />
          </div>
          <div class="champ">
            <label for="dep-ref">Reference du bordereau *</label>
            <input id="dep-ref" maxlength="80" [(ngModel)]="reference" placeholder="N° du bordereau de versement" />
          </div>
          <div class="champ">
            <label for="dep-date">Date du depot *</label>
            <input id="dep-date" type="date" [max]="aujourdhui" [(ngModel)]="date" />
          </div>
          @if (justificationRequise()) {
            <div class="champ">
              <label for="dep-justif">Justification *</label>
              <textarea id="dep-justif" rows="2" maxlength="2000" [(ngModel)]="justification"
                        [class.requise]="!justification.trim()" placeholder="Pourquoi le depot differe du solde"></textarea>
            </div>
          }
        </aside>
      </div>
      <div class="champ piece-depot">
        <label>Bordereau de versement ou releve de banque *</label>
        <app-piece-jointe libelle="Piece attestant le depot" [fichier]="fichier()" (fichierChange)="fichier.set($event)" />
      </div>
      <div class="actions-formulaire">
        <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Annuler</button>
        <button class="bouton succes" type="button" [disabled]="!envoyable()" (click)="envoyer()">
          {{ envoi() ? 'Envoi...' : 'Envoyer le depot de ' + (total() | number: '1.0-0') + ' F' }}
        </button>
      </div>
    </app-modale>
  `,
  styleUrl: './tresorerie-pdv.scss'
})
export class DepotBanque {
  private readonly options = inject(OptionsPdvService);
  readonly solde = input.required<number>();
  readonly tolerance = input(0);
  readonly ferme = output<void>();
  readonly envoye = output<void>();

  readonly aujourdhui = new Date().toISOString().slice(0, 10);
  quantites: Record<number, number | null> = {};
  banque = '';
  reference = '';
  date = this.aujourdhui;
  justification = '';
  readonly fichier = signal<File | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  total(): number {
    return totalDe(this.quantites);
  }

  ecart(): number {
    return this.total() - this.solde();
  }

  justificationRequise(): boolean {
    return Math.abs(this.ecart()) > this.tolerance();
  }

  envoyable(): boolean {
    return !this.envoi() && this.total() > 0 && this.total() <= this.solde() && !!this.reference.trim() && !!this.date
      && !!this.fichier() && (!this.justificationRequise() || !!this.justification.trim());
  }

  envoyer(): void {
    const fichier = this.fichier();
    if (!this.envoyable() || !fichier) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.options.envoyerDegagement({
      billettage: versBillettage(this.quantites), justification: this.justification.trim() || undefined,
      banque: this.banque.trim() || null, referenceDepot: this.reference.trim(), dateDepot: this.date
    }).subscribe({
      next: (d) => this.options.joindrePieceDegagement(d.id, fichier).subscribe({
        next: () => {
          this.envoi.set(false);
          this.envoye.emit();
        },
        error: (e) => {
          this.envoi.set(false);
          this.erreur.set(message(e, "Depot enregistre, mais la piece n'a pas pu etre jointe."));
        }
      }),
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Envoi impossible.'));
      }
    });
  }
}

/**
 * Verification d'un depot en banque par l'administrateur : billettage du
 * gerant, banque, reference et date du bordereau, la piece a ouvrir, puis le
 * montant porte sur le bordereau. Valider credite le compte de l'entreprise.
 */
@Component({
  selector: 'app-verification-depot',
  imports: [DecimalPipe, DatePipe, FormsModule, Modale],
  template: `
    @let d = depot();
    <app-modale [titre]="'Depot ' + d.numero" [sousTitre]="'Coffre de ' + d.gerantNom" largeur="820px"
                [erreur]="erreur()" (ferme)="ferme.emit()">
      @if (refus()) {
        <div class="champ">
          <label for="ver-motif">Motif du refus *</label>
          <textarea id="ver-motif" rows="3" maxlength="2000" [(ngModel)]="motif"
                    placeholder="Ex. le bordereau ne correspond pas au montant declare"></textarea>
        </div>
        <div class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="refus.set(false)">Retour</button>
          <button class="bouton danger" type="button" [disabled]="envoi() || !motif.trim()" (click)="refuser()">Refuser</button>
        </div>
      } @else {
        <div class="verification">
          <dl class="fiche-depot">
            <div><dt>Banque</dt><dd>{{ d.banque || '—' }}</dd></div>
            <div><dt>Reference du bordereau</dt><dd>{{ d.referenceDepot || '—' }}</dd></div>
            <div><dt>Date du depot</dt><dd>{{ d.dateDepot ? (d.dateDepot | date: 'dd/MM/yyyy') : '—' }}</dd></div>
            <div><dt>Envoye le</dt><dd>{{ d.envoyeLe | date: 'dd/MM/yyyy HH:mm' }}</dd></div>
            <div><dt>Solde du coffre a l'envoi</dt><dd>{{ d.montantAttendu | number: '1.0-0' }} F</dd></div>
            <div class="fort"><dt>Montant declare</dt><dd>{{ d.montantDeclare | number: '1.0-0' }} F</dd></div>
          </dl>
          <div>
            <h3>Billettage du gerant</h3>
            <table class="tableau billettage-lu">
              <tbody>
                @for (c of d.declare; track c.valeur) {
                  <tr><td>{{ c.valeur | number: '1.0-0' }} F</td><td class="numerique">× {{ c.quantite }}</td>
                    <td class="numerique">{{ c.montant | number: '1.0-0' }} F</td></tr>
                }
              </tbody>
            </table>
            @if (d.justification) { <p class="note-expediteur"><strong>Note du gerant :</strong> {{ d.justification }}</p> }
          </div>
        </div>
        <div class="piece-lue" [class.absente]="!d.piece">
          <span aria-hidden="true">{{ d.piece ? '📎' : '⚠' }}</span>
          <span>{{ d.piece ? (d.pieceNom || 'Bordereau joint') : 'Aucune piece jointe : le depot ne peut pas etre valide' }}</span>
          @if (d.piece) { <button class="bouton secondaire petit info" type="button" (click)="voirPiece()">Ouvrir la piece</button> }
        </div>
        <div class="grille-formulaire">
          <div class="champ">
            <label for="ver-montant">Montant porte sur le bordereau (F) *</label>
            <input id="ver-montant" type="number" min="1" step="any" [(ngModel)]="montant" />
          </div>
          <div class="champ">
            <label for="ver-comment">Commentaire{{ commentaireRequis() ? ' *' : '' }}</label>
            <input id="ver-comment" maxlength="2000" [(ngModel)]="commentaire"
                   [placeholder]="commentaireRequis() ? 'Expliquez l ecart avec le montant declare' : 'Facultatif'" />
          </div>
        </div>
        <div class="actions-formulaire">
          <button class="bouton danger" type="button" (click)="refus.set(true)">Refuser</button>
          <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Fermer</button>
          <button class="bouton succes" type="button" [disabled]="!validable()" (click)="valider()">
            {{ envoi() ? 'Validation...' : 'Valider le depot de ' + ((montant ?? 0) | number: '1.0-0') + ' F' }}
          </button>
        </div>
      }
    </app-modale>
  `,
  styles: `
    .verification { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 0.8rem; }
    .fiche-depot { display: grid; gap: 0.35rem; margin: 0; }
    .fiche-depot div { display: flex; justify-content: space-between; gap: 0.6rem; font-size: 0.88rem; }
    .fiche-depot dt { color: var(--couleur-texte-doux); }
    .fiche-depot dd { margin: 0; font-weight: 600; }
    .fiche-depot .fort dd { font-size: 1.05rem; }
    h3 { margin: 0 0 0.4rem; font-size: 0.85rem; }
    .billettage-lu td { padding: 0.25rem 0.4rem; font-size: 0.85rem; }
    .note-expediteur { margin: 0.5rem 0 0; font-size: 0.82rem; }
    .piece-lue { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.8rem; padding: 0.6rem 0.8rem;
      border: 1px solid #9fd8bd; border-radius: 10px; background: #f2fbf6; font-size: 0.88rem; }
    .piece-lue span:nth-child(2) { flex: 1; }
    .piece-lue.absente { border-color: #f2b8ab; background: #fdf0ed; color: var(--couleur-danger); }
    @media (max-width: 700px) { .verification { grid-template-columns: 1fr; } }
  `
})
export class VerificationDepot implements OnInit {
  private readonly options = inject(OptionsPdvService);
  readonly depot = input.required<Degagement>();
  readonly tolerance = input(0);
  readonly ferme = output<void>();
  readonly traite = output<'valide' | 'refuse'>();

  montant: number | null = null;
  commentaire = '';
  motif = '';
  readonly refus = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  ngOnInit(): void {
    this.montant = this.depot().montantDeclare;
  }

  commentaireRequis(): boolean {
    return Math.abs((this.montant ?? 0) - this.depot().montantDeclare) > this.tolerance();
  }

  validable(): boolean {
    return !this.envoi() && this.depot().piece && (this.montant ?? 0) > 0
      && (!this.commentaireRequis() || !!this.commentaire.trim());
  }

  voirPiece(): void {
    this.options.pieceDegagement(this.depot().id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.erreur.set('Piece introuvable.')
    });
  }

  valider(): void {
    if (!this.validable()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.options.validerDegagement(this.depot().id, this.montant as number, this.commentaire.trim() || undefined).subscribe({
      next: () => {
        this.envoi.set(false);
        this.traite.emit('valide');
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Validation impossible.'));
      }
    });
  }

  refuser(): void {
    this.envoi.set(true);
    this.options.refuserDegagement(this.depot().id, this.motif.trim()).subscribe({
      next: () => {
        this.envoi.set(false);
        this.traite.emit('refuse');
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Refus impossible.'));
      }
    });
  }
}
