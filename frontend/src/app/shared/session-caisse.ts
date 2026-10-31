import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Billettage, ControleCaisse, SessionCaisse } from '../core/models/pdv';
import { PdvService } from '../core/services/pdv.service';
import { ChampQuantite } from './champ-quantite';
import { Modale } from './modale';

/** Billets et pieces du franc CFA, de la plus forte valeur a la plus faible (le serveur fait foi). */
export const COUPURES_FCFA = [10000, 5000, 2000, 1000, 500, 250, 200, 100, 50, 25, 10, 5];

/** Billettage saisi, pret a envoyer : une ligne par coupure. */
export function versBillettage(quantites: Record<number, number | null>): Billettage {
  return COUPURES_FCFA.map((valeur) => ({ valeur, quantite: Math.max(0, Math.floor(quantites[valeur] ?? 0)) }));
}

function message(reponse: unknown, defaut: string): string {
  return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
}

/** Propose le rapport PDF de la session au telechargement. */
export function telechargerRapportSession(service: PdvService, session: SessionCaisse, erreur?: (m: string) => void): void {
  service.rapportSessionPdf(session.id).subscribe({
    next: (reponse) => {
      const url = URL.createObjectURL(reponse.body ?? new Blob());
      const lien = document.createElement('a');
      lien.href = url;
      lien.download = `rapport-caisse-${session.numero}.pdf`;
      document.body.appendChild(lien);
      lien.click();
      lien.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
    error: () => erreur?.('Telechargement du rapport impossible.')
  });
}

/**
 * Saisie du billettage : nombre de billets et de pieces de chaque valeur, avec
 * le montant par ligne et le total compte.
 */
@Component({
  selector: 'app-saisie-billettage',
  imports: [DecimalPipe, ChampQuantite],
  template: `
    <div class="billettage">
      @for (groupe of groupes; track groupe.titre) {
        <section>
          <h3>{{ groupe.titre }}</h3>
          @for (v of groupe.valeurs; track v) {
            <div class="ligne-coupure" [class.saisie]="(quantites()[v] ?? 0) > 0">
              <span class="valeur">{{ v | number: '1.0-0' }} F</span>
              <app-champ-quantite [compact]="true" [valeur]="quantites()[v] ?? null"
                                  (valeurChange)="changer(v, $event)" [libelle]="'Nombre de ' + v + ' F'" />
              <span class="montant">{{ v * (quantites()[v] ?? 0) | number: '1.0-0' }} F</span>
            </div>
          }
        </section>
      }
    </div>
    <div class="total-billettage">
      <span>Total compte</span>
      <strong>{{ total() | number: '1.0-0' }} F</strong>
    </div>
  `,
  styles: `
    .billettage { display: grid; grid-template-columns: 1fr 1fr; gap: 0.9rem; }
    h3 { margin: 0 0 0.35rem; color: var(--couleur-texte-doux); font-size: 0.72rem; letter-spacing: 0.06em; text-transform: uppercase; }
    .ligne-coupure {
      display: grid; grid-template-columns: 74px auto 1fr; align-items: center; gap: 0.5rem;
      padding: 0.2rem 0.35rem; border-radius: 8px;
    }
    .ligne-coupure.saisie { background: color-mix(in srgb, var(--couleur-succes) 8%, #fff); }
    .valeur { font-weight: 700; }
    .montant { color: var(--couleur-texte-doux); font-size: 0.85rem; text-align: right; }
    .total-billettage {
      display: flex; justify-content: space-between; align-items: baseline; margin-top: 0.8rem;
      padding: 0.7rem 0.9rem; border-radius: 10px; background: var(--couleur-fond); font-weight: 600;
    }
    .total-billettage strong { font-size: 1.4rem; }
    @media (max-width: 620px) { .billettage { grid-template-columns: 1fr; } }
  `
})
export class SaisieBillettage {
  readonly quantites = model<Record<number, number | null>>({});
  readonly groupes = [
    { titre: 'Billets', valeurs: COUPURES_FCFA.filter((v) => v >= 1000) },
    { titre: 'Pieces', valeurs: COUPURES_FCFA.filter((v) => v < 1000) }
  ];
  readonly total = computed(() =>
    COUPURES_FCFA.reduce((somme, v) => somme + v * Math.max(0, Math.floor(this.quantites()[v] ?? 0)), 0)
  );

  changer(valeur: number, quantite: number | null): void {
    this.quantites.update((q) => ({ ...q, [valeur]: quantite }));
  }
}

/** Ouverture de la session d'une caisse par un gerant : le fond compte par coupure. */
@Component({
  selector: 'app-ouverture-session',
  imports: [Modale, SaisieBillettage],
  template: `
    <app-modale [titre]="'Ouvrir ' + caisseNom()" sousTitre="Comptez le fond de caisse, billet par billet"
                largeur="680px" [erreur]="erreur()" (ferme)="ferme.emit()">
      <app-saisie-billettage [(quantites)]="quantites" />
      <p class="aide">Une fois la session ouverte, tout le personnel affecte a cette caisse peut encaisser.</p>
      <div class="actions-formulaire">
        <button class="bouton secondaire" type="button" (click)="ferme.emit()">Annuler</button>
        <button class="bouton" type="button" [disabled]="envoi()" (click)="ouvrir()">
          {{ envoi() ? 'Ouverture...' : 'Ouvrir la session' }}
        </button>
      </div>
    </app-modale>
  `,
  styles: `.aide { margin: 0.8rem 0 0; color: var(--couleur-texte-doux); font-size: 0.85rem; }`
})
export class OuvertureSession {
  private readonly service = inject(PdvService);
  readonly caisseId = input.required<number>();
  readonly caisseNom = input('la caisse');
  readonly ferme = output<void>();
  readonly ouverte = output<SessionCaisse>();

  quantites: Record<number, number | null> = {};
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  ouvrir(): void {
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.ouvrirSession(this.caisseId(), versBillettage(this.quantites)).subscribe({
      next: (s) => {
        this.envoi.set(false);
        this.ouverte.emit(s);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Ouverture impossible.'));
      }
    });
  }
}

/**
 * Fermeture d'une session par un gerant, en trois temps : comptage du tiroir
 * a l'aveugle (le montant attendu n'est pas montre), revelation de l'ecart a
 * justifier s'il n'est pas nul, puis bilan avec le rapport PDF a telecharger.
 */
@Component({
  selector: 'app-fermeture-session',
  imports: [DecimalPipe, DatePipe, FormsModule, Modale, SaisieBillettage],
  template: `
    <app-modale [titre]="'Fermer ' + session().caisseNom" [sousTitre]="'Session ' + session().numero + sousTitreEtape()"
                largeur="720px" [erreur]="erreur()" (ferme)="ferme.emit()">
      @switch (etape()) {
        @case ('comptage') {
          @if (session().commandesEnCours > 0) {
            <div class="message erreur">
              {{ session().commandesEnCours }} commande(s) en cours non reglee(s) : encaissez-les ou annulez-les
              avant de fermer la caisse.
            </div>
          }
          <p class="consigne">Comptez les especes du tiroir. Le montant attendu s'affichera apres votre comptage.</p>
          <app-saisie-billettage [(quantites)]="quantites" />
          <div class="actions-formulaire">
            <button class="bouton secondaire" type="button" (click)="ferme.emit()">Annuler</button>
            <button class="bouton" type="button" [disabled]="envoi()" (click)="controler()">
              {{ envoi() ? 'Controle...' : 'Valider le comptage' }}
            </button>
          </div>
        }
        @case ('ecart') {
          @if (controle(); as c) {
            <div class="resultat" [class.juste]="c.ecart === 0" [class.manque]="c.ecart < 0" [class.surplus]="c.ecart > 0">
              <div><span>Especes comptees</span><strong>{{ c.montantCompte | number: '1.0-0' }} F</strong></div>
              <div><span>Especes attendues</span><strong>{{ c.especesAttendues | number: '1.0-0' }} F</strong></div>
              <div class="ecart">
                <span>{{ c.ecart === 0 ? 'Caisse juste' : c.ecart < 0 ? 'Il manque' : 'Excedent' }}</span>
                <strong>{{ c.ecart | number: '1.0-0' }} F</strong>
              </div>
            </div>
            <p class="detail-attendu">
              Attendu = fond {{ session().fondInitial | number: '1.0-0' }} F + ventes en especes
              + entrees {{ session().entrees | number: '1.0-0' }} F − sorties {{ session().sorties | number: '1.0-0' }} F
            </p>
            @if (c.commandesEnCours.length > 0) {
              <div class="message erreur">
                Fermeture impossible : {{ c.commandesEnCours.length }} commande(s) a regler ({{ c.commandesEnCours.join(', ') }}).
              </div>
            }
            @if (c.ecart !== 0) {
              <div class="champ">
                <label for="justification">Justification de l'ecart *</label>
                <textarea id="justification" rows="2" maxlength="2000" [(ngModel)]="justification"
                          placeholder="Ex. erreur de rendu de monnaie, billet refuse..."></textarea>
              </div>
            }
            <div class="champ">
              <label for="observations">Observations</label>
              <textarea id="observations" rows="2" maxlength="2000" [(ngModel)]="observations"></textarea>
            </div>
            <div class="actions-formulaire">
              <button class="bouton secondaire" type="button" (click)="etape.set('comptage')">‹ Recompter</button>
              <button class="bouton" type="button"
                      [disabled]="envoi() || c.commandesEnCours.length > 0 || (c.ecart !== 0 && !justification.trim())"
                      (click)="fermer()">
                {{ envoi() ? 'Fermeture...' : 'Fermer la caisse' }}
              </button>
            </div>
          }
        }
        @case ('bilan') {
          @if (resultat(); as s) {
            <div class="bilan-fermeture">
              <span class="coche" aria-hidden="true">✓</span>
              <p>{{ s.caisseNom }} fermee le {{ s.fermeeLe | date: "dd/MM/yyyy 'a' HH:mm" }}</p>
              <dl>
                <div><dt>Ventes</dt><dd>{{ s.nombreVentes }} · {{ s.chiffreAffaires | number: '1.0-0' }} F</dd></div>
                <div><dt>Especes attendues</dt><dd>{{ s.montantTheorique | number: '1.0-0' }} F</dd></div>
                <div><dt>Especes comptees</dt><dd>{{ s.montantCompte | number: '1.0-0' }} F</dd></div>
                <div [class.negatif]="(s.ecart ?? 0) < 0"><dt>Ecart</dt><dd>{{ s.ecart | number: '1.0-0' }} F</dd></div>
              </dl>
            </div>
            <div class="actions-formulaire">
              <button class="bouton secondaire" type="button" (click)="telecharger(s)">⤓ Rapport de caisse (PDF)</button>
              <button class="bouton" type="button" (click)="ferme.emit()">Terminer</button>
            </div>
          }
        }
      }
    </app-modale>
  `,
  styles: `
    .consigne { margin: 0 0 0.8rem; color: var(--couleur-texte-doux); }
    .resultat { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.6rem; margin-bottom: 0.6rem; }
    .resultat > div { display: flex; flex-direction: column; gap: 0.2rem; padding: 0.8rem; border-radius: 12px; background: var(--couleur-fond); }
    .resultat span { color: var(--couleur-texte-doux); font-size: 0.8rem; }
    .resultat strong { font-size: 1.3rem; }
    .resultat.juste .ecart { background: #e3f6ed; color: #1f9d6b; }
    .resultat.manque .ecart { background: #fde8e4; color: #d9452b; }
    .resultat.surplus .ecart { background: #fff2df; color: #b8650a; }
    .resultat .ecart span { color: inherit; }
    .detail-attendu { margin: 0 0 0.8rem; color: var(--couleur-texte-doux); font-size: 0.8rem; }
    .champ textarea { width: 100%; }
    .bilan-fermeture { text-align: center; }
    .bilan-fermeture .coche {
      display: inline-grid; place-items: center; width: 56px; height: 56px; border-radius: 50%;
      background: #e3f6ed; color: #1f9d6b; font-size: 1.8rem; font-weight: 700;
    }
    .bilan-fermeture dl { display: grid; gap: 0.35rem; max-width: 360px; margin: 0.8rem auto 0; text-align: left; }
    .bilan-fermeture dl div { display: flex; justify-content: space-between; }
    .bilan-fermeture dt { color: var(--couleur-texte-doux); }
    .bilan-fermeture dd { margin: 0; font-weight: 700; }
    .bilan-fermeture .negatif dd { color: var(--couleur-danger); }
    @media (max-width: 620px) { .resultat { grid-template-columns: 1fr; } }
  `
})
export class FermetureSession {
  private readonly service = inject(PdvService);
  readonly session = input.required<SessionCaisse>();
  readonly ferme = output<void>();
  readonly fermee = output<SessionCaisse>();

  readonly etape = signal<'comptage' | 'ecart' | 'bilan'>('comptage');
  readonly controle = signal<ControleCaisse | null>(null);
  /** Session fermee, pour le bilan. */
  readonly resultat = signal<SessionCaisse | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  quantites: Record<number, number | null> = {};
  justification = '';
  observations = '';

  readonly sousTitreEtape = computed(() =>
    ({ comptage: ' · 1. comptage du tiroir', ecart: ' · 2. controle', bilan: ' · fermee' })[this.etape()]
  );

  controler(): void {
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.controlerSession(this.session().id, versBillettage(this.quantites)).subscribe({
      next: (c) => {
        this.envoi.set(false);
        this.controle.set(c);
        this.etape.set('ecart');
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Controle impossible.'));
      }
    });
  }

  fermer(): void {
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.fermerSession(this.session().id, versBillettage(this.quantites),
      this.justification.trim() || undefined, this.observations.trim() || undefined).subscribe({
      next: (s) => {
        this.envoi.set(false);
        this.resultat.set(s);
        this.etape.set('bilan');
        this.fermee.emit(s);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Fermeture impossible.'));
      }
    });
  }

  telecharger(s: SessionCaisse): void {
    telechargerRapportSession(this.service, s, (m) => this.erreur.set(m));
  }
}

/** Entree ou sortie d'especes hors vente : apport de monnaie, depense, retrait. */
@Component({
  selector: 'app-mouvement-session',
  imports: [DecimalPipe, FormsModule, Modale],
  template: `
    <app-modale titre="Entree ou sortie d'especes" [sousTitre]="session().caisseNom + ' · ' + session().numero"
                largeur="520px" [erreur]="erreur()" (ferme)="ferme.emit()">
      <form (ngSubmit)="enregistrer()">
        <div class="sens" role="radiogroup" aria-label="Sens du mouvement">
          <button type="button" role="radio" class="entree" [class.actif]="type() === 'ENTREE'"
                  [attr.aria-checked]="type() === 'ENTREE'" (click)="type.set('ENTREE')">
            <strong>⬇ Entree</strong><small>Apport de monnaie, remboursement...</small>
          </button>
          <button type="button" role="radio" class="sortie" [class.actif]="type() === 'SORTIE'"
                  [attr.aria-checked]="type() === 'SORTIE'" (click)="type.set('SORTIE')">
            <strong>⬆ Sortie</strong><small>Depense, retrait vers le coffre...</small>
          </button>
        </div>
        <div class="champ">
          <label for="montant-mvt">Montant (F CFA) *</label>
          <input id="montant-mvt" name="montant" type="number" min="1" step="any" class="champ-montant" required
                 [(ngModel)]="montant" />
        </div>
        <div class="champ">
          <label for="motif-mvt">Motif *</label>
          <input id="motif-mvt" name="motif" maxlength="255" required [(ngModel)]="motif"
                 [placeholder]="type() === 'ENTREE' ? 'Ex. monnaie apportee par le gerant' : 'Ex. achat de glace'" />
        </div>
        @if (session().mouvements.length > 0) {
          <ul class="historique">
            @for (m of session().mouvements.slice(-4); track m.id) {
              <li><span [class.sortie]="m.type === 'SORTIE'">{{ m.type === 'ENTREE' ? '+' : '−' }}{{ m.montant | number: '1.0-0' }} F</span>
                {{ m.motif }} <small>· {{ m.auteurNom }}</small></li>
            }
          </ul>
        }
        <div class="actions-formulaire">
          <button class="bouton secondaire" type="button" (click)="ferme.emit()">Annuler</button>
          <button class="bouton" type="submit" [disabled]="envoi() || !montant || montant <= 0 || !motif.trim()">
            Enregistrer
          </button>
        </div>
      </form>
    </app-modale>
  `,
  styles: `
    .sens { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; margin-bottom: 0.8rem; }
    .sens button {
      display: flex; flex-direction: column; gap: 0.2rem; padding: 0.75rem; border: 2px solid var(--couleur-bordure);
      border-radius: 12px; background: var(--couleur-surface); font: inherit; text-align: left; cursor: pointer;
    }
    .sens small { color: var(--couleur-texte-doux); }
    .sens .entree.actif { border-color: #1f9d6b; background: #e3f6ed; }
    .sens .sortie.actif { border-color: #e8870e; background: #fff2df; }
    .historique { margin: 0.4rem 0 0; padding-left: 1.1rem; color: var(--couleur-texte-doux); font-size: 0.85rem; }
    .historique span { font-weight: 700; color: #1f9d6b; }
    .historique span.sortie { color: #b8650a; }
  `
})
export class MouvementSession {
  private readonly service = inject(PdvService);
  readonly session = input.required<SessionCaisse>();
  readonly ferme = output<void>();
  readonly enregistre = output<SessionCaisse>();

  readonly type = signal<'ENTREE' | 'SORTIE'>('ENTREE');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  montant: number | null = null;
  motif = '';

  enregistrer(): void {
    if (!this.montant || this.montant <= 0 || !this.motif.trim()) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.mouvementCaisse(this.session().id, this.type(), this.montant, this.motif.trim()).subscribe({
      next: (s) => {
        this.envoi.set(false);
        this.enregistre.emit(s);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(message(e, 'Enregistrement impossible.'));
      }
    });
  }
}
