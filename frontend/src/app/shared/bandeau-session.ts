import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth/auth.service';
import { CaissePdv, SessionCaisse } from '../core/models/pdv';
import { PdvService } from '../core/services/pdv.service';
import { SessionTravailService } from '../core/services/session-travail.service';
import { Modale } from './modale';
import { FermetureSession, OuvertureSession } from './session-caisse';

/** Relecture des sessions : le poste voit sa caisse s'ouvrir sans recharger la page. */
const RAFRAICHISSEMENT_MS = 20000;

/**
 * Etat de la caisse dans les ecrans d'encaissement : la session sur laquelle
 * le poste encaisse et le choix de la caisse quand plusieurs sont ouvertes (les
 * entrees et sorties d'especes ont leur bouton dans la barre de l'ecran). Seul un gerant ouvre et ferme la session ; le
 * personnel est invite a le solliciter tant que sa caisse est fermee.
 */
@Component({
  selector: 'app-bandeau-session',
  imports: [DecimalPipe, FormsModule, Modale, OuvertureSession, FermetureSession],
  template: `
    @if (sessions.courante() === undefined) {
      <div class="etat-caisse attente"><span class="voyant"></span><span>Lecture de la caisse...</span></div>
    } @else if (sessions.choixRequis()) {
      <div class="etat-caisse choix">
        <span class="voyant"></span>
        <label>
          <span>Encaisser sur</span>
          <select [ngModel]="null" (ngModelChange)="sessions.choisir($event)" aria-label="Caisse">
            <option [ngValue]="null" disabled>Choisir la caisse...</option>
            @for (s of sessions.ouvertes() ?? []; track s.id) {
              <option [ngValue]="s.id">{{ s.caisseNom }}</option>
            }
          </select>
        </label>
      </div>
    } @else if (sessions.courante(); as s) {
      <div class="etat-caisse">
        <span class="voyant"></span>
        <span class="texte">{{ s.caisseNom }} · {{ s.nombreVentes }} vente(s) · {{ s.chiffreAffaires | number: '1.0-0' }} F</span>
        @if ((sessions.ouvertes()?.length ?? 0) > 1) {
          <button type="button" class="lien-caisse" (click)="sessions.choisir(null)" title="Changer de caisse">⇄</button>
        }
        @if (gestionnaire) {
          <button type="button" class="lien-caisse" (click)="fermer(s)">Fermer</button>
        }
      </div>
    } @else {
      <div class="etat-caisse fermee">
        <span class="voyant"></span>
        <span class="texte">
          Caisse fermee : {{ restauration ? 'aucune commande ni vente possible.' : 'aucune vente possible.' }}
          @if (!gestionnaire) { <small>Un gerant doit ouvrir la session.</small> }
        </span>
        @if (gestionnaire) {
          <button type="button" class="lien-caisse" (click)="demanderOuverture()">Ouvrir</button>
        }
      </div>
    }

    @if (choixCaisse(); as caisses) {
      <app-modale titre="Ouvrir une caisse" sousTitre="Choisissez la caisse a ouvrir" largeur="440px" (ferme)="choixCaisse.set(null)">
        <div class="caisses-fermees">
          @for (c of caisses; track c.id) {
            <button type="button" class="bouton secondaire info" (click)="choixCaisse.set(null); aOuvrir.set(c)">🧾 {{ c.nom }}</button>
          } @empty {
            <p>Toutes les caisses actives sont deja ouvertes.</p>
          }
        </div>
      </app-modale>
    }
    @if (aOuvrir(); as c) {
      <app-ouverture-session [caisseId]="c.id" [caisseNom]="c.nom" (ferme)="aOuvrir.set(null)" (ouverte)="ouverte($event)" />
    }
    @if (aFermer(); as s) {
      <app-fermeture-session [session]="s" (ferme)="aFermer.set(null)" (fermee)="sessions.remplacer($event)" />
    }
  `,
  styles: `
    .etat-caisse {
      display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0.75rem; border-radius: 10px;
      background: #e6f5ea; color: #157a38; font-size: 0.8rem; font-weight: 600;
    }
    .etat-caisse.fermee { background: #fde8e4; color: var(--couleur-danger); }
    .etat-caisse.attente { background: var(--couleur-fond); color: var(--couleur-texte-doux); }
    .etat-caisse.choix { background: #fff2df; color: #8a5a00; }
    .texte { flex: 1; min-width: 0; }
    .texte small { display: block; font-weight: 500; }
    .voyant { width: 8px; height: 8px; flex-shrink: 0; border-radius: 50%; background: currentColor; }
    label { display: flex; flex: 1; align-items: center; gap: 0.5rem; }
    select { flex: 1; height: 32px; border: 1px solid currentColor; border-radius: 8px; background: #fff; font: inherit; }
    label ::ng-deep .select2-container { flex: 1; min-width: 170px; }
    .lien-caisse {
      flex-shrink: 0; padding: 0.2rem 0.6rem; border: 1px solid currentColor; border-radius: 999px;
      background: none; color: inherit; font-size: 0.75rem; font-weight: 700; cursor: pointer;
    }
    .caisses-fermees { display: grid; gap: 0.5rem; }
  `
})
export class BandeauSession implements OnInit {
  readonly sessions = inject(SessionTravailService);
  private readonly pdv = inject(PdvService);
  private readonly auth = inject(AuthService);
  readonly gestionnaire = this.auth.aRole('ADMIN', 'GERANT');
  /** Au restaurant, une caisse fermee bloque aussi la prise de commande. */
  readonly restauration = this.auth.estRestauration();

  readonly choixCaisse = signal<CaissePdv[] | null>(null);
  readonly aOuvrir = signal<CaissePdv | null>(null);
  readonly aFermer = signal<SessionCaisse | null>(null);

  constructor() {
    const minuterie = setInterval(() => this.sessions.rafraichir(), RAFRAICHISSEMENT_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  ngOnInit(): void {
    this.sessions.rafraichir();
  }

  /** Une seule caisse fermee : elle s'ouvre directement ; sinon le gerant choisit. */
  demanderOuverture(): void {
    this.pdv.caisses().subscribe({
      next: (caisses) => {
        const fermees = caisses.filter((c) => c.actif && !c.session);
        if (fermees.length === 1) {
          this.aOuvrir.set(fermees[0]);
        } else {
          this.choixCaisse.set(fermees);
        }
      }
    });
  }

  ouverte(session: SessionCaisse): void {
    this.aOuvrir.set(null);
    this.sessions.remplacer(session);
    this.sessions.choisir(session.id);
  }

  /** Totaux et commandes en cours relus au moment de fermer. */
  fermer(session: SessionCaisse): void {
    this.pdv.session(session.id).subscribe({ next: (s) => this.aFermer.set(s) });
  }
}
