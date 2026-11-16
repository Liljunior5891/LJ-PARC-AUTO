import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionCaisse } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { PdvHorsLigneService, StatutVenteHorsLigne, SuiviVenteHorsLigne } from '../../../core/services/pdv-hors-ligne.service';
import { Modale } from '../../../shared/modale';

const LIBELLES: Record<StatutVenteHorsLigne, string> = {
  ENREGISTREE: 'Enregistree',
  CONFLIT: 'A traiter',
  REPRISE: 'Reprise',
  ABANDONNEE: 'Abandonnee'
};

/**
 * Ventes encaissees sans connexion : ce qui est arrive au serveur, ce qu'il faut
 * regulariser (stock manquant, prix different du catalogue) et les conflits a
 * reprendre sur une caisse ouverte ou a abandonner avec une note.
 */
@Component({
  selector: 'app-hors-ligne-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale],
  template: `
    <div class="entete-page">
      <div>
        <h1>Ventes hors ligne</h1>
        <p class="sous-titre">Ventes encaissees en especes pendant une coupure, puis envoyees au retour du reseau (30 derniers jours).</p>
      </div>
      <button class="bouton secondaire" type="button" (click)="charger()" [disabled]="chargement()">Actualiser</button>
    </div>

    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (succes()) { <div class="message succes">{{ succes() }}</div> }

    <section class="cartes-apercu indicateurs">
      <div class="carte-apercu rouge"><span class="textes"><span class="libelle">A traiter</span>
        <strong>{{ conflits().length }}</strong><small>ventes non enregistrees</small></span></div>
      <div class="carte-apercu orange"><span class="textes"><span class="libelle">A regulariser</span>
        <strong>{{ aRegulariser() }}</strong><small>stock manquant ou prix different</small></span></div>
      <div class="carte-apercu vert"><span class="textes"><span class="libelle">Enregistrees</span>
        <strong>{{ enregistrees() }}</strong><small>{{ totalEnregistre() | number: '1.0-0' }} F</small></span></div>
    </section>

    <section class="carte">
      <div class="tableau-conteneur">
        <table class="tableau">
          <thead>
            <tr><th>Vente</th><th>Articles</th><th class="numerique">Total</th><th>Etat</th><th></th></tr>
          </thead>
          <tbody>
            @for (v of ventes(); track v.id) {
              <tr>
                <td><strong>{{ v.numeroProvisoire }}</strong><br />
                  <small>{{ v.saisieLe | date: 'dd/MM/yyyy HH:mm' }} · {{ v.caissierNom }}</small>
                  @if (v.numero) { <br /><small>Commande {{ v.numero }}</small> }</td>
                <td><small>{{ v.articles.join(' · ') }}</small></td>
                <td class="numerique"><strong>{{ v.total | number: '1.0-0' }}</strong></td>
                <td>
                  <span class="statut" [class]="'statut ' + v.statut.toLowerCase()">{{ libelles[v.statut] }}</span>
                  @if (v.statut === 'CONFLIT' && v.message) { <br /><small>{{ v.message }}</small> }
                  @if (v.manques) { <br /><small class="alerte">Stock manquant : {{ v.manques }}</small> }
                  @if (v.ecartsPrix) { <br /><small class="alerte">Prix : {{ v.ecartsPrix }}</small> }
                  @if (v.note) { <br /><small>Note : {{ v.note }}</small> }
                  @if (v.traiteePar) { <br /><small>par {{ v.traiteePar }}, le {{ v.traiteeLe | date: 'dd/MM HH:mm' }}</small> }
                </td>
                <td class="actions-ligne">
                  @if (v.statut === 'CONFLIT') {
                    <button class="bouton petit" type="button" (click)="ouvrirReprise(v)">Reprendre</button>
                    <button class="bouton petit secondaire" type="button" (click)="ouvrirAbandon(v)">Abandonner</button>
                  }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="5"><div class="message vide">{{ chargement() ? 'Chargement...' : 'Aucune vente hors ligne.' }}</div></td></tr>
            }
          </tbody>
        </table>
      </div>
      <p class="aide">Stock manquant : la vente a eu lieu mais le stock ne la couvrait pas ; il est reste a zero.
        Verifiez une reception oubliee ou faites un inventaire.</p>
    </section>

    @if (reprise(); as v) {
      <app-modale titre="Reprendre la vente" [sousTitre]="v.numeroProvisoire + ' · ' + (v.total | number: '1.0-0') + ' F'"
                  largeur="480px" [erreur]="erreur()" (ferme)="reprise.set(null)">
        <p>La vente est enregistree a votre nom, sur la caisse ouverte qui a recu l'argent.</p>
        <div class="champ">
          <label for="hl-session">Caisse</label>
          <select id="hl-session" name="session" [(ngModel)]="sessionId">
            @for (s of sessions(); track s.id) { <option [ngValue]="s.id">{{ s.caisseNom }} · {{ s.numero }}</option> }
          </select>
          @if (!sessions().length) { <small>Aucune caisse ouverte : ouvrez une session avant de reprendre la vente.</small> }
        </div>
        <div class="actions-formulaire">
          <button class="bouton secondaire" type="button" (click)="reprise.set(null)">Annuler</button>
          <button class="bouton" type="button" (click)="reprendre(v)" [disabled]="envoi() || !sessionId">Enregistrer la vente</button>
        </div>
      </app-modale>
    }

    @if (abandon(); as v) {
      <app-modale titre="Abandonner la vente" [sousTitre]="v.numeroProvisoire" largeur="480px" [erreur]="erreur()"
                  (ferme)="abandon.set(null)">
        <div class="champ">
          <label for="hl-note">Ce qui a ete fait *</label>
          <textarea id="hl-note" name="note" rows="3" maxlength="500" [(ngModel)]="note"
                    placeholder="Ex. : argent rendu au client, vente refaite en caisse sous le numero..."></textarea>
        </div>
        <div class="actions-formulaire">
          <button class="bouton secondaire" type="button" (click)="abandon.set(null)">Annuler</button>
          <button class="bouton danger" type="button" (click)="abandonner(v)" [disabled]="envoi() || !note.trim()">Abandonner</button>
        </div>
      </app-modale>
    }
  `,
  styles: `
    .statut { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: .78rem; font-weight: 600;
      background: #e3f5ea; color: #157a37; }
    .statut.conflit { background: #fdecec; color: #8b1c1c; }
    .statut.abandonnee { background: #eef1f5; color: #4a5361; }
    .statut.reprise { background: #e8f1fd; color: #12407a; }
    .alerte { color: #a14b05; font-weight: 600; }
    .actions-ligne { display: flex; flex-wrap: wrap; gap: .35rem; justify-content: flex-end; }
    .aide { color: var(--couleur-texte-doux); font-size: .85rem; }
  `
})
export class HorsLignePdv implements OnInit {
  private readonly service = inject(PdvHorsLigneService);
  private readonly pdv = inject(PdvService);
  readonly libelles = LIBELLES;

  readonly ventes = signal<SuiviVenteHorsLigne[]>([]);
  readonly sessions = signal<SessionCaisse[]>([]);
  readonly chargement = signal(true);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly reprise = signal<SuiviVenteHorsLigne | null>(null);
  readonly abandon = signal<SuiviVenteHorsLigne | null>(null);
  sessionId: number | null = null;
  note = '';

  readonly conflits = computed(() => this.ventes().filter((v) => v.statut === 'CONFLIT'));
  readonly aRegulariser = computed(() => this.ventes().filter((v) => v.statut !== 'CONFLIT' && (v.manques || v.ecartsPrix)).length);
  readonly enregistrees = computed(() => this.ventes().filter((v) => v.statut === 'ENREGISTREE' || v.statut === 'REPRISE').length);
  readonly totalEnregistre = computed(() => this.ventes()
    .filter((v) => v.statut === 'ENREGISTREE' || v.statut === 'REPRISE').reduce((s, v) => s + v.total, 0));

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.recentes().subscribe({
      next: (v) => { this.ventes.set(v); this.chargement.set(false); },
      error: (e) => { this.chargement.set(false); this.erreur.set(e?.error?.message ?? 'Chargement impossible.'); }
    });
  }

  ouvrirReprise(v: SuiviVenteHorsLigne): void {
    this.erreur.set(null);
    this.pdv.sessionsOuvertes().subscribe({
      next: (s) => {
        this.sessions.set(s);
        this.sessionId = s.length === 1 ? s[0].id : null;
        this.reprise.set(v);
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Caisses ouvertes illisibles.')
    });
  }

  reprendre(v: SuiviVenteHorsLigne): void {
    this.envoi.set(true);
    this.service.reprendre(v.id, this.sessionId).subscribe({
      next: (r) => this.reussi(`Vente ${v.numeroProvisoire} enregistree (${r.numero}).`, () => this.reprise.set(null)),
      error: (e) => { this.envoi.set(false); this.erreur.set(e?.error?.message ?? 'Reprise impossible.'); }
    });
  }

  ouvrirAbandon(v: SuiviVenteHorsLigne): void {
    this.erreur.set(null);
    this.note = '';
    this.abandon.set(v);
  }

  abandonner(v: SuiviVenteHorsLigne): void {
    this.envoi.set(true);
    this.service.abandonner(v.id, this.note.trim()).subscribe({
      next: () => this.reussi(`Vente ${v.numeroProvisoire} abandonnee.`, () => this.abandon.set(null)),
      error: (e) => { this.envoi.set(false); this.erreur.set(e?.error?.message ?? 'Abandon impossible.'); }
    });
  }

  private reussi(message: string, apres: () => void): void {
    this.envoi.set(false);
    this.succes.set(message);
    apres();
    this.charger();
    setTimeout(() => this.succes.set(null), 4000);
  }
}
