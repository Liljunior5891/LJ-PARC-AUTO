import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';

interface ReglageWhatsApp {
  actif: boolean;
  url: string;
  cleDefinie: boolean;
  session: string;
  pauseMs: number;
}

interface EtatSession {
  statut: 'ABSENTE' | 'STOPPED' | 'STARTING' | 'SCAN_QR_CODE' | 'WORKING' | 'FAILED' | string;
  numero?: string | null;
  nom?: string | null;
}

const LIBELLES_SESSION: Record<string, string> = {
  ABSENTE: 'Aucune session',
  STOPPED: 'Arretee',
  STARTING: 'Demarrage...',
  SCAN_QR_CODE: 'En attente du code QR',
  WORKING: 'Connectee',
  FAILED: 'En echec'
};

/**
 * Reglage de WhatsApp pour toute la plateforme : serveur WAHA (adresse, cle
 * d'API, session), liaison du compte WhatsApp par code QR, message d'essai.
 */
@Component({
  selector: 'app-whatsapp-plateforme',
  imports: [FormsModule],
  template: `
    <div class="entete-page">
      <div>
        <h1>WhatsApp (WAHA)</h1>
        <p class="sous-titre">Compte WhatsApp qui envoie les notifications aux administrateurs des entreprises.</p>
      </div>
      <div class="actions-entete">
        <button class="bouton secondaire" type="button" (click)="lireSession()" [disabled]="occupe()">Actualiser l'etat</button>
      </div>
    </div>

    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (info()) { <div class="message succes">{{ info() }}</div> }

    <!-- Vue d'ensemble : ou en est la mise en service. -->
    <section class="carte synthese">
      <div class="indicateur">
        <span class="pictogramme" [class.ok]="pret()" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2Zm0 18.2a8.2 8.2 0 0 1-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2Zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.3-.4.2-.4.7-1.4.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.2-1.2-.1-.1-.3-.2-.5-.3Z"/></svg>
        </span>
        <div>
          <strong>{{ titreSynthese() }}</strong>
          <small>{{ texteSynthese() }}</small>
        </div>
      </div>
      <ol class="etapes" aria-label="Mise en service">
        <li [class.faite]="!!f?.cleDefinie"><span class="puce">1</span>Serveur et cle</li>
        <li [class.faite]="statut() === 'WORKING'" [class.encours]="statut() === 'SCAN_QR_CODE' || statut() === 'STARTING'">
          <span class="puce">2</span>Compte relie</li>
        <li [class.faite]="!!f?.actif"><span class="puce">3</span>Envoi reel</li>
      </ol>
    </section>

    <div class="grille">
      <section class="carte">
        <div class="titre-carte">
          <h2>Compte WhatsApp</h2>
          @if (session(); as s) {
            <span class="statut {{ s.statut.toLowerCase() }}"><span class="point"></span>{{ libelles[s.statut] || s.statut }}</span>
          }
        </div>

        @if (session(); as s) {
          @if (s.statut === 'WORKING') {
            <div class="relie">
              <span class="avatar" aria-hidden="true">{{ initiale(s.nom) }}</span>
              <div>
                <strong>{{ s.nom || 'Compte WhatsApp' }}</strong>
                <small>{{ s.numero ? numeroLisible(s.numero) : '' }}</small>
              </div>
            </div>
            <p class="aide">Gardez le telephone de ce numero allume et connecte : WhatsApp coupe les appareils lies apres 14 jours sans lui.</p>
          } @else if (s.statut === 'SCAN_QR_CODE') {
            <div class="liaison">
              <div class="cadre-qr">
                @if (qr()) {
                  <img class="qr" [src]="qr()" alt="Code QR a scanner dans WhatsApp" />
                } @else {
                  <span class="aide">Code QR en preparation...</span>
                }
              </div>
              <ol class="consignes">
                <li>Ouvrez WhatsApp sur le telephone du numero dedie.</li>
                <li>Reglages, puis <strong>Appareils connectes</strong>.</li>
                <li><strong>Connecter un appareil</strong>, puis scannez ce code.</li>
              </ol>
            </div>
            <p class="aide">Le code se renouvelle tout seul.</p>
          } @else if (s.statut === 'STARTING') {
            <p class="vide">Demarrage de la session : le code QR arrive dans quelques secondes.</p>
          } @else if (s.statut === 'FAILED') {
            <p class="vide">La session s'est arretee en erreur. Redemarrez-la pour afficher un nouveau code QR.</p>
          } @else {
            <p class="vide">Aucun compte relie. Demarrez la session, puis scannez le code QR avec le telephone du numero dedie.</p>
          }

          <div class="actions">
            @if (s.statut !== 'WORKING' && s.statut !== 'SCAN_QR_CODE' && s.statut !== 'STARTING') {
              <button class="bouton succes" type="button" (click)="demarrer()" [disabled]="occupe()">
                {{ s.statut === 'FAILED' ? 'Redemarrer la session' : 'Demarrer et afficher le code QR' }}</button>
            }
            @if (s.statut === 'WORKING' || s.statut === 'SCAN_QR_CODE') {
              <button class="bouton danger" type="button" (click)="deconnecter()" [disabled]="occupe()">
                {{ s.statut === 'WORKING' ? 'Delier le compte' : 'Arreter la liaison' }}</button>
            }
          </div>
        } @else if (erreurSession()) {
          <div class="message erreur">{{ erreurSession() }}</div>
          <p class="aide">Verifiez l'adresse et la cle d'API du serveur, puis actualisez.</p>
        } @else {
          <p class="vide">Lecture de l'etat...</p>
        }

        <div class="essai">
          <h3>Message d'essai</h3>
          <p class="aide">Part tout de suite, meme en mode simulation : il sert a verifier la liaison.</p>
          <form (ngSubmit)="essayer()">
            <div class="champ">
              <label for="telephone">Numero du destinataire</label>
              <input id="telephone" name="telephone" inputmode="tel" [(ngModel)]="telephone" placeholder="+228 90 00 00 00"
                     maxlength="30" required />
            </div>
            <button class="bouton info" type="submit" [disabled]="occupe() || !telephone.trim()">Envoyer</button>
          </form>
        </div>
      </section>

      <section class="carte">
        <h2>Serveur WAHA</h2>
        @if (f) {
          <form (ngSubmit)="enregistrer()">
            <label class="bloc-bascule" [class.actif]="f.actif">
              <span>
                <strong>Envoyer vraiment les messages</strong>
                <small>{{ f.actif ? 'Les notifications partent sur WhatsApp.' : 'Desactive : les messages sont seulement simules.' }}</small>
              </span>
              <input class="bascule" type="checkbox" name="actif" [(ngModel)]="f.actif" aria-label="Envoyer vraiment les messages" />
            </label>

            <div class="grille-formulaire">
              <div class="champ champ-pleine">
                <label for="url">Adresse du serveur <span class="requis">*</span></label>
                <input id="url" name="url" [(ngModel)]="f.url" required maxlength="200" placeholder="http://127.0.0.1:3005" />
              </div>
              <div class="champ champ-pleine">
                <label for="cleApi">Cle d'API (X-Api-Key)</label>
                <input id="cleApi" name="cleApi" type="password" [(ngModel)]="cleApi" maxlength="200" autocomplete="new-password"
                       [placeholder]="f.cleDefinie ? 'Laisser vide pour garder la cle enregistree' : 'Cle donnee au lancement de WAHA'" />
                @if (f.cleDefinie && adresseChangee() && !cleApi.trim()) {
                  <small class="note">Nouvelle adresse : ressaisissez la cle d'API, l'ancienne ne part jamais vers une autre adresse.</small>
                } @else if (f.cleDefinie) {
                  <small class="note ok">Une cle est enregistree.</small>
                } @else {
                  <small class="note">Aucune cle enregistree : WAHA refusera les appels.</small>
                }
              </div>
              <div class="champ">
                <label for="session">Session <span class="requis">*</span></label>
                <input id="session" name="session" [(ngModel)]="f.session" required maxlength="60" />
              </div>
              <div class="champ">
                <label for="pauseMs">Pause entre deux messages (ms)</label>
                <input id="pauseMs" name="pauseMs" type="number" min="0" max="60000" step="100" [(ngModel)]="f.pauseMs" />
              </div>
            </div>
            <p class="aide">WAHA Core n'accepte qu'une session, nommee « default ». La pause evite que WhatsApp prenne l'envoi pour un robot.</p>
            <div class="actions-formulaire">
              <button class="bouton" type="submit" [disabled]="occupe()">Enregistrer</button>
            </div>
          </form>
        } @else {
          <p class="vide">Chargement du reglage...</p>
        }
      </section>
    </div>
  `,
  styles: `
    :host { display: block; }
    h2 { margin: 0 0 1rem; font-size: 1.05rem; }
    h3 { margin: 0 0 .25rem; font-size: .95rem; }
    .aide { margin: .5rem 0 0; font-size: .82rem; color: var(--couleur-texte-doux); line-height: 1.4; }
    .vide { margin: 0; padding: 1rem; border: 1px dashed var(--couleur-bordure); border-radius: 8px; text-align: center;
      font-size: .88rem; color: var(--couleur-texte-doux); }

    .synthese { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 1rem 2rem; margin-bottom: 1rem; }
    .indicateur { display: flex; align-items: center; gap: .9rem; min-width: 0; }
    .indicateur > div { display: flex; flex-direction: column; gap: .15rem; }
    .indicateur strong { font-size: 1.05rem; }
    .indicateur small { color: var(--couleur-texte-doux); font-size: .85rem; }
    .pictogramme { flex: none; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px;
      background: #eef1f5; color: #7a8594; }
    .pictogramme.ok { background: #e3f6ec; color: #1a9e5c; }
    .pictogramme svg { width: 26px; height: 26px; fill: currentColor; }
    .etapes { display: flex; flex-wrap: wrap; gap: .5rem; margin: 0; padding: 0; list-style: none; }
    .etapes li { display: flex; align-items: center; gap: .45rem; padding: .35rem .75rem .35rem .4rem; border-radius: 999px;
      background: #f2f4f7; font-size: .82rem; font-weight: 600; color: var(--couleur-texte-doux); }
    .etapes .puce { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; background: #fff;
      border: 1px solid var(--couleur-bordure); font-size: .75rem; }
    .etapes li.encours { background: #fdf3e3; color: #8a5a00; }
    .etapes li.faite { background: #e3f6ec; color: #1a7a48; }
    .etapes li.faite .puce { background: #1a9e5c; border-color: #1a9e5c; color: #fff; }

    .grille { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 1rem; align-items: start; }
    .titre-carte { display: flex; justify-content: space-between; align-items: center; gap: .5rem; margin-bottom: 1rem; }
    .titre-carte h2 { margin: 0; }
    .statut { display: inline-flex; align-items: center; gap: .4rem; padding: .25rem .65rem; border-radius: 999px;
      background: #f2f4f7; color: var(--couleur-texte-doux); font-size: .78rem; font-weight: 700; }
    .statut .point { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
    .statut.working { background: #e3f6ec; color: #1a7a48; }
    .statut.scan_qr_code, .statut.starting { background: #fdf3e3; color: #8a5a00; }
    .statut.failed { background: #fce9e9; color: var(--couleur-danger); }

    .relie { display: flex; align-items: center; gap: .8rem; padding: .9rem 1rem; border-radius: 10px; background: #f3fbf6;
      border: 1px solid #cdebd9; }
    .relie > div { display: flex; flex-direction: column; }
    .relie small { color: var(--couleur-texte-doux); }
    .avatar { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 50%; background: #1a9e5c; color: #fff;
      font-weight: 700; font-size: 1.1rem; }
    .liaison { display: flex; flex-wrap: wrap; align-items: center; gap: 1.25rem; }
    .cadre-qr { display: grid; place-items: center; width: 232px; height: 232px; padding: 8px; border-radius: 12px; background: #fff;
      border: 1px solid var(--couleur-bordure); box-shadow: var(--ombre); }
    .qr { width: 100%; height: 100%; object-fit: contain; }
    .consignes { flex: 1; min-width: 180px; margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: .5rem;
      font-size: .88rem; }
    .actions { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: 1rem; }

    .essai { margin-top: 1.25rem; padding-top: 1.1rem; border-top: 1px solid var(--couleur-bordure); }
    .essai form { display: flex; align-items: flex-end; gap: .6rem; margin-top: .75rem; }
    .essai .champ { flex: 1; min-width: 0; }
    .essai .bouton { min-height: 38px; }

    .bloc-bascule { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1.1rem;
      padding: .85rem 1rem; border: 1px solid var(--couleur-bordure); border-radius: 10px; background: #f7f9fc; cursor: pointer; }
    .bloc-bascule.actif { background: #f3fbf6; border-color: #cdebd9; }
    .bloc-bascule span { display: flex; flex-direction: column; gap: .15rem; }
    .bloc-bascule small { color: var(--couleur-texte-doux); font-size: .82rem; }
    .note { font-size: .76rem; color: #8a5a00; }
    .note.ok { color: var(--couleur-succes); }

    .bascule { position: relative; flex: none; width: 44px; height: 24px; margin: 0; border-radius: 999px; background: #cfd6df;
      cursor: pointer; appearance: none; transition: background .18s ease; }
    .bascule::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%;
      background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, .25); transition: transform .18s ease; }
    .bascule:checked { background: #1a9e5c; }
    .bascule:checked::after { transform: translateX(20px); }
    .bascule:focus-visible { outline: 2px solid var(--couleur-primaire-clair); outline-offset: 2px; }

    @media (max-width: 520px) {
      .grille { grid-template-columns: 1fr; }
      .essai form { flex-wrap: wrap; }
      .essai .bouton { width: 100%; }
      .cadre-qr { width: 100%; height: auto; aspect-ratio: 1; }
    }
  `
})
export class WhatsAppPlateforme implements OnInit, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/plateforme/whatsapp`;
  private minuterie?: ReturnType<typeof setInterval>;

  readonly libelles = LIBELLES_SESSION;
  readonly session = signal<EtatSession | null>(null);
  readonly qr = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly erreurSession = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly occupe = signal(false);
  f: ReglageWhatsApp | null = null;
  /** Adresse enregistree : la changer oblige a ressaisir la cle. */
  private urlEnregistree = '';
  cleApi = '';
  telephone = '';

  ngOnInit(): void {
    this.http.get<ReglageWhatsApp>(this.base).subscribe({
      next: (r) => {
        this.f = r;
        this.urlEnregistree = r.url;
      },
      error: (e) => this.erreur.set(message(e, 'Reglage indisponible.'))
    });
    this.lireSession();
    // Pendant la liaison, l'etat et le code QR (renouvele par WhatsApp) sont relus toutes les 5 s.
    this.minuterie = setInterval(() => {
      const s = this.session()?.statut;
      if (s === 'SCAN_QR_CODE' || s === 'STARTING') {
        this.lireSession();
      }
    }, 5000);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuterie);
  }

  enregistrer(): void {
    if (!this.f) {
      return;
    }
    this.occupe.set(true);
    this.effacer();
    this.http.put<ReglageWhatsApp>(this.base, { ...this.f, cleApi: this.cleApi }).subscribe({
      next: (r) => {
        this.f = r;
        this.urlEnregistree = r.url;
        this.cleApi = '';
        this.occupe.set(false);
        this.info.set('Reglage enregistre.');
        this.lireSession();
      },
      error: (e) => {
        this.occupe.set(false);
        this.erreur.set(message(e, 'Enregistrement impossible.'));
      }
    });
  }

  lireSession(): void {
    this.http.get<EtatSession>(`${this.base}/session`).subscribe({
      next: (s) => {
        this.erreurSession.set(null);
        this.session.set(s);
        if (s.statut === 'SCAN_QR_CODE') {
          this.http.get<{ mimetype: string; data: string }>(`${this.base}/session/qr`).subscribe({
            next: (q) => this.qr.set(q.data ? `data:${q.mimetype};base64,${q.data}` : null),
            error: () => this.qr.set(null)
          });
        } else {
          this.qr.set(null);
        }
      },
      error: (e) => {
        this.session.set(null);
        this.erreurSession.set(message(e, 'Serveur WAHA injoignable.'));
      }
    });
  }

  demarrer(): void {
    this.action(`${this.base}/session/demarrer`, null, 'Session demarree : le code QR arrive dans quelques secondes.');
  }

  deconnecter(): void {
    this.action(`${this.base}/session/deconnecter`, null, 'Compte WhatsApp delie.');
  }

  essayer(): void {
    this.action(`${this.base}/essai`, { telephone: this.telephone.trim() }, 'Message d\'essai envoye.');
  }

  adresseChangee(): boolean {
    return !!this.f && this.f.url.trim().replace(/\/+$/, '').toLowerCase() !== this.urlEnregistree.toLowerCase();
  }

  statut(): string | null {
    return this.session()?.statut ?? null;
  }

  /** Envoi reel active et compte relie : les messages partent vraiment. */
  pret(): boolean {
    return !!this.f?.actif && this.statut() === 'WORKING';
  }

  titreSynthese(): string {
    if (this.pret()) {
      return 'Les notifications partent sur WhatsApp';
    }
    return this.f?.actif ? 'Envoi active, mais aucun compte relie' : 'Mode simulation';
  }

  texteSynthese(): string {
    if (this.pret()) {
      const n = this.session()?.numero;
      return 'Envoyees depuis ' + (n ? this.numeroLisible(n) : 'le compte relie') + '.';
    }
    return this.f?.actif
      ? "Les envois echouent tant qu'aucun compte WhatsApp n'est relie : reliez-en un ci-dessous."
      : 'Les messages sont enregistres dans Notifications sans partir sur WhatsApp.';
  }

  initiale(nom?: string | null): string {
    return (nom || 'W').trim().charAt(0).toUpperCase();
  }

  numeroLisible(id: string): string {
    return '+' + id.replace(/@.*$/, '').replace(/:.*$/, '');
  }

  private action(url: string, corps: unknown, succes: string): void {
    this.occupe.set(true);
    this.effacer();
    this.http.post<void>(url, corps).subscribe({
      next: () => {
        this.occupe.set(false);
        this.info.set(succes);
        setTimeout(() => this.lireSession(), 2500);
      },
      error: (e) => {
        this.occupe.set(false);
        this.erreur.set(message(e, 'Operation impossible.'));
      }
    });
  }

  private effacer(): void {
    this.erreur.set(null);
    this.info.set(null);
  }
}

function message(e: HttpErrorResponse, defaut: string): string {
  return (e.error && typeof e.error === 'object' && e.error.message) || defaut;
}
