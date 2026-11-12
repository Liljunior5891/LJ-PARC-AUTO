import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth/auth.service';
import { environment } from '../../environments/environment';

interface Etat { actif: boolean; codesSecoursRestants: number; }
interface Preparation { secret: string; adresse: string; codeQrSvg: string; }

function message(r: unknown, defaut: string): string {
  return (r as { error?: { message?: string } })?.error?.message ?? defaut;
}

/**
 * Double authentification du compte, dans « Mon compte » : activation par code QR
 * (application d'authentification), codes de secours montres une seule fois,
 * desactivation avec le mot de passe et un code.
 */
@Component({
  selector: 'app-bloc-deux-facteurs',
  imports: [FormsModule],
  template: `
    <section class="bloc-2fa">
      <h3>Double authentification</h3>
      @if (auth.utilisateur()?.deuxFacteursAActiver) {
        <p class="alerte" role="alert">Elle est obligatoire pour l'administrateur de la plateforme : activez-la pour continuer.</p>
      }
      @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }

      @if (codesSecours(); as codes) {
        <div class="message succes">Double authentification activee.</div>
        <p class="aide">Codes de secours, a usage unique, si vous perdez votre telephone. Notez-les maintenant : ils ne seront plus affiches.</p>
        <ul class="codes">@for (c of codes; track c) { <li>{{ c }}</li> }</ul>
        <button class="bouton secondaire" type="button" (click)="terminer()">J'ai note mes codes</button>
      } @else if (etat()?.actif) {
        <p class="aide">Active : a chaque connexion, un code de votre application d'authentification est demande.
          Codes de secours restants : {{ etat()?.codesSecoursRestants }}.</p>
        @if (desactivation()) {
          <div class="ligne">
            <div class="champ"><label for="df-mdp">Mot de passe</label>
              <input id="df-mdp" type="password" autocomplete="current-password" [(ngModel)]="motDePasse" (keydown.enter)="$event.preventDefault()" /></div>
            <div class="champ"><label for="df-code-d">Code</label>
              <input id="df-code-d" inputmode="numeric" autocomplete="one-time-code" maxlength="9" [(ngModel)]="code" (keydown.enter)="$event.preventDefault()" /></div>
          </div>
          <button class="bouton secondaire danger" type="button" [disabled]="enCours()" (click)="desactiver()">Desactiver</button>
        } @else {
          <button class="bouton secondaire" type="button" (click)="desactivation.set(true)">Desactiver…</button>
        }
      } @else if (preparation(); as p) {
        <p class="aide">1. Scannez ce code avec Google Authenticator, Microsoft Authenticator ou FreeOTP.</p>
        <img class="qr" [src]="imageQr(p.codeQrSvg)" alt="Code QR de la double authentification" />
        <p class="aide">Ou saisissez cette cle : <code>{{ p.secret }}</code></p>
        <p class="aide">2. Tapez le code a 6 chiffres affiche par l'application.</p>
        <div class="ligne">
          <div class="champ"><label for="df-code">Code</label>
            <input id="df-code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" [(ngModel)]="code" (keydown.enter)="$event.preventDefault(); activer()" /></div>
        </div>
        <button class="bouton" type="button" [disabled]="enCours() || code.length < 6" (click)="activer()">Activer</button>
      } @else {
        <p class="aide">Apres le mot de passe, un code a 6 chiffres de votre telephone est demande : un mot de passe vole ne suffit plus.</p>
        <button class="bouton secondaire" type="button" [disabled]="enCours()" (click)="preparer()">Activer la double authentification</button>
      }
    </section>
  `,
  styles: `
    .bloc-2fa { padding: .9rem; border: 1px solid var(--couleur-bordure); border-radius: 14px; margin-bottom: 1.1rem; }
    .bloc-2fa h3 { margin-top: 0; }
    .alerte { padding: .6rem .8rem; border-radius: 10px; background: #fef3c7; color: #92400e; font-size: .88rem; }
    .aide { font-size: .85rem; color: var(--couleur-texte-doux); }
    .qr { display: block; width: 180px; height: 180px; margin: .4rem 0; background: #fff; padding: 6px; border-radius: 8px; }
    code { font-size: .85rem; word-break: break-all; }
    .ligne { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: .5rem; margin-bottom: .5rem; }
    .codes { display: grid; grid-template-columns: repeat(2, 1fr); gap: .3rem; padding: 0; list-style: none;
      font-family: ui-monospace, monospace; font-size: .95rem; }
    .codes li { padding: .3rem .5rem; background: #f1f5f9; border-radius: 6px; text-align: center; }
  `
})
export class BlocDeuxFacteurs implements OnInit {
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth/deux-facteurs`;

  readonly etat = signal<Etat | null>(null);
  readonly preparation = signal<Preparation | null>(null);
  readonly codesSecours = signal<string[] | null>(null);
  readonly desactivation = signal(false);
  readonly enCours = signal(false);
  readonly erreur = signal<string | null>(null);
  code = '';
  motDePasse = '';

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.http.get<Etat>(this.base).subscribe({ next: (e) => this.etat.set(e) });
  }

  /** Image du code QR, sans inserer de HTML dans la page. */
  imageQr(svg: string): string {
    return 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg)));
  }

  preparer(): void {
    this.lancer(this.http.post<Preparation>(`${this.base}/preparation`, null), (p) => {
      this.code = '';
      this.preparation.set(p);
    });
  }

  activer(): void {
    this.lancer(this.http.post<{ codesSecours: string[] }>(`${this.base}/activation`, { code: this.code.trim() }), (r) => {
      this.preparation.set(null);
      this.code = '';
      this.codesSecours.set(r.codesSecours);
    });
  }

  /** Codes notes : l'etat est relu ; les prochaines connexions demanderont le code. */
  terminer(): void {
    this.codesSecours.set(null);
    this.auth.deuxFacteursActives();
    this.charger();
  }

  desactiver(): void {
    this.lancer(this.http.post<void>(`${this.base}/desactivation`, { motDePasse: this.motDePasse, code: this.code.trim() }), () => {
      this.desactivation.set(false);
      this.code = '';
      this.motDePasse = '';
      this.charger();
    });
  }

  private lancer<T>(appel: import('rxjs').Observable<T>, suite: (r: T) => void): void {
    this.enCours.set(true);
    this.erreur.set(null);
    appel.subscribe({
      next: (r) => {
        this.enCours.set(false);
        suite(r);
      },
      error: (r) => {
        this.enCours.set(false);
        this.erreur.set(message(r, 'Operation impossible.'));
      }
    });
  }
}
