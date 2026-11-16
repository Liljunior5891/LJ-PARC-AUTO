import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';

interface LigneType {
  type: string;
  libelle: string;
  description: string;
  module?: string | null;
  whatsapp: boolean;
  email: boolean;
  /** Rappels d'abonnement : l'e-mail part toujours. */
  emailObligatoire?: boolean;
}

interface VuePreferences {
  numeroWhatsapp?: string | null;
  /** Numero confirme par le code recu : seul un numero confirme recoit les WhatsApp. */
  numeroVerifie: boolean;
  whatsappBranche: boolean;
  emails: string[];
  types: LigneType[];
}

interface Groupe {
  titre: string;
  lignes: LigneType[];
}

type Canal = 'whatsapp' | 'email';

/**
 * Choix, par l'administrateur, des notifications recues par WhatsApp (numero
 * donne a l'inscription) et par e-mail (administrateurs).
 */
@Component({
  selector: 'app-preferences-notifications',
  imports: [FormsModule],
  template: `
    <div class="entete-page">
      <div>
        <h1>Notifications</h1>
        <p class="sous-titre">Choisissez ce que vous voulez recevoir sur WhatsApp et par e-mail.</p>
      </div>
    </div>

    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (info()) { <div class="message succes">{{ info() }}</div> }

    @if (vue(); as v) {
      @if (!v.whatsappBranche) {
        <div class="message avertissement">
          L'envoi WhatsApp n'est pas encore ouvert par la plateforme : vos choix sont gardes et s'appliqueront des son ouverture.
        </div>
      }

      <div class="destinataires">
        <section class="carte destinataire">
          <span class="pictogramme whatsapp" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2Zm0 18.2a8.2 8.2 0 0 1-4.2-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2Zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.3-.4.2-.4.7-1.4.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.7 11.8 11.8 0 0 0 4.5 4c1.7.7 2.3.8 3.2.6.5-.1 1.5-.6 1.7-1.2.2-.6.2-1.1.2-1.2-.1-.1-.3-.2-.5-.3Z"/></svg>
          </span>
          <div class="texte">
            <span class="surtitre">WhatsApp</span>
            @if (v.numeroWhatsapp) {
              <strong>{{ numeroLisible(v.numeroWhatsapp) }}
                @if (v.numeroVerifie) { <span class="confirme">Confirme</span> } @else { <span class="a-confirmer">A confirmer</span> }
              </strong>
              @if (v.numeroVerifie) {
                <small>Numero de l'entreprise, confirme par code.</small>
              } @else {
                <small>Confirmez-le avec un code WhatsApp : rien n'y part avant.</small>
              }
            } @else {
              <strong>Aucun numero</strong>
              <small>Ajoutez un telephone a un administrateur dans Utilisateurs.</small>
            }
          </div>
          @if (v.numeroVerifie) {
            <button class="bouton secondaire petit" type="button" (click)="essai()" [disabled]="occupe()">Message d'essai</button>
          } @else if (v.numeroWhatsapp && !codeEnvoye()) {
            <button class="bouton succes petit" type="button" (click)="demanderCode()" [disabled]="occupe()">Recevoir un code</button>
          }
          @if (!v.numeroVerifie && codeEnvoye()) {
            <form class="saisie-code" (ngSubmit)="confirmerCode()">
              <input name="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="Code a 6 chiffres"
                     [(ngModel)]="code" aria-label="Code recu sur WhatsApp" />
              <button class="bouton petit" type="submit" [disabled]="occupe() || code.trim().length < 6">Valider</button>
              <button class="lien" type="button" (click)="demanderCode()" [disabled]="occupe()">Renvoyer</button>
            </form>
          }
        </section>

        <section class="carte destinataire">
          <span class="pictogramme email" aria-hidden="true">
            <svg viewBox="0 0 24 24"><path d="M4 5h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Zm0 2v.4l8 5 8-5V7H4Zm16 2.8-7.5 4.7a1 1 0 0 1-1 0L4 9.8V17h16V9.8Z"/></svg>
          </span>
          <div class="texte">
            <span class="surtitre">E-mail</span>
            @if (v.emails.length) {
              <strong [title]="v.emails.join(', ')">{{ v.emails[0] }}@if (v.emails.length > 1) { <em> +{{ v.emails.length - 1 }}</em> }</strong>
              <small>Adresse de chaque administrateur actif.</small>
            } @else {
              <strong>Aucun administrateur actif</strong>
              <small>Les e-mails partent vers les administrateurs.</small>
            }
          </div>
        </section>
      </div>

      <section class="carte choix">
        <div class="ligne entete-choix">
          <span class="nom">Notification</span>
          <button type="button" class="tete-canal" (click)="tout('whatsapp')" [attr.aria-pressed]="toutCoche('whatsapp')"
                  title="Tout cocher ou tout decocher">WhatsApp<small>{{ toutCoche('whatsapp') ? 'Tout retirer' : 'Tout choisir' }}</small></button>
          <button type="button" class="tete-canal" (click)="tout('email')" [attr.aria-pressed]="toutCoche('email')"
                  title="Tout cocher ou tout decocher">E-mail<small>{{ toutCoche('email') ? 'Tout retirer' : 'Tout choisir' }}</small></button>
        </div>

        @for (g of groupes(); track g.titre) {
          <h2 class="titre-groupe">{{ g.titre }}</h2>
          @for (l of g.lignes; track l.type) {
            <div class="ligne" [class.coupee]="!l.whatsapp && !l.email">
              <div class="nom">
                <strong>{{ l.libelle }}</strong>
                <small>{{ l.description }}</small>
                @if (l.emailObligatoire) { <small class="obligatoire">L'e-mail part toujours : ces rappels evitent la coupure du compte.</small> }
              </div>
              <label class="interrupteur">
                <span class="libelle-mobile">WhatsApp</span>
                <input class="bascule vert" type="checkbox" [(ngModel)]="l.whatsapp" (ngModelChange)="changer()"
                       [attr.aria-label]="'WhatsApp : ' + l.libelle" />
              </label>
              <label class="interrupteur">
                <span class="libelle-mobile">E-mail</span>
                <input class="bascule bleu" type="checkbox" [(ngModel)]="l.email" (ngModelChange)="changer()"
                       [disabled]="!!l.emailObligatoire" [attr.aria-label]="'E-mail : ' + l.libelle" />
              </label>
            </div>
          }
        }
      </section>

      <div class="barre-enregistrement" [class.modifiee]="modifie()">
        <span>{{ modifie() ? 'Modifications non enregistrees · ' : '' }}{{ resume() }}</span>
        <div class="boutons">
          @if (modifie()) {
            <button class="bouton secondaire" type="button" (click)="annuler()" [disabled]="occupe()">Annuler</button>
          }
          <button class="bouton" type="button" (click)="enregistrer()" [disabled]="occupe() || !modifie()">Enregistrer</button>
        </div>
      </div>
    } @else if (!erreur()) {
      <div class="message vide">Chargement des notifications...</div>
    }
  `,
  styles: `
    :host { display: block; max-width: 1080px; }

    .destinataires { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem; margin-bottom: 1rem; }
    .destinataire { display: flex; align-items: center; gap: 1rem; }
    .destinataire .texte { display: flex; flex-direction: column; gap: .1rem; flex: 1; min-width: 0; }
    .destinataire strong { font-size: 1.05rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .destinataire strong em { font-style: normal; font-weight: 600; color: var(--couleur-texte-doux); font-size: .85rem; }
    .destinataire small { color: var(--couleur-texte-doux); font-size: .8rem; }
    .surtitre { font-size: .72rem; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; color: var(--couleur-texte-doux); }
    .pictogramme { flex: none; display: grid; place-items: center; width: 46px; height: 46px; border-radius: 12px; }
    .pictogramme svg { width: 24px; height: 24px; fill: currentColor; }
    .pictogramme.whatsapp { background: #e3f6ec; color: #1a9e5c; }
    .pictogramme.email { background: #e5effb; color: var(--couleur-info); }
    .confirme, .a-confirmer { margin-left: .4rem; padding: .1rem .5rem; border-radius: 999px; font-size: .7rem; font-weight: 700;
      vertical-align: middle; }
    .confirme { background: #e3f6ec; color: #1a7a48; }
    .a-confirmer { background: #fdf3e3; color: #8a5a00; }
    .saisie-code { display: flex; align-items: center; gap: .4rem; flex-basis: 100%; margin-top: .25rem; }
    .saisie-code input { width: 150px; padding: .45rem .6rem; border: 1px solid var(--couleur-bordure); border-radius: 8px;
      font: inherit; letter-spacing: .2em; }
    .lien { background: none; border: 0; padding: 0; font: inherit; font-size: .82rem; color: var(--couleur-primaire-clair);
      cursor: pointer; text-decoration: underline; }
    .destinataire { flex-wrap: wrap; }

    .choix { padding: 0; overflow: hidden; }
    .ligne { display: grid; grid-template-columns: 1fr 110px 110px; align-items: center; gap: 1rem; padding: .85rem 1.25rem;
      border-top: 1px solid var(--couleur-bordure); }
    .ligne.coupee .nom strong { color: var(--couleur-texte-doux); }
    .entete-choix { border-top: 0; background: #f7f9fc; border-bottom: 1px solid var(--couleur-bordure);
      font-size: .78rem; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--couleur-texte-doux); }
    .tete-canal { display: flex; flex-direction: column; align-items: center; gap: .1rem; padding: .3rem; border: 0; border-radius: 8px;
      background: none; font: inherit; color: var(--couleur-texte); cursor: pointer; }
    .tete-canal small { font-size: .68rem; font-weight: 600; text-transform: none; letter-spacing: 0; color: var(--couleur-primaire-clair); }
    .tete-canal:hover { background: #eaf1fa; }
    .titre-groupe { margin: 0; padding: .9rem 1.25rem .35rem; font-size: .78rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: .05em; color: var(--couleur-primaire); border-top: 1px solid var(--couleur-bordure); background: #fbfcfe; }
    .titre-groupe + .ligne { border-top-color: #eef1f5; }
    .nom { display: flex; flex-direction: column; gap: .15rem; min-width: 0; }
    .nom small { color: var(--couleur-texte-doux); font-size: .82rem; line-height: 1.35; }
    .interrupteur { display: flex; justify-content: center; align-items: center; gap: .5rem; cursor: pointer; }
    .libelle-mobile { display: none; font-size: .8rem; font-weight: 600; color: var(--couleur-texte-doux); }

    .bascule { position: relative; flex: none; width: 44px; height: 24px; margin: 0; border-radius: 999px; background: #cfd6df;
      cursor: pointer; appearance: none; transition: background .18s ease; }
    .bascule::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%;
      background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, .25); transition: transform .18s ease; }
    .bascule.vert:checked { background: #1a9e5c; }
    .bascule.bleu:checked { background: var(--couleur-info); }
    .bascule:checked::after { transform: translateX(20px); }
    .bascule:disabled { opacity: .55; cursor: not-allowed; }
    .nom .obligatoire { color: var(--couleur-info); font-weight: 600; }
    .bascule:focus-visible { outline: 2px solid var(--couleur-primaire-clair); outline-offset: 2px; }

    .barre-enregistrement { display: flex; flex-wrap: wrap; align-items: center;
      justify-content: space-between; gap: .75rem; margin-top: 1rem; padding: .75rem 1.25rem; border: 1px solid var(--couleur-bordure);
      border-radius: var(--rayon); background: var(--couleur-surface); box-shadow: var(--ombre); color: var(--couleur-texte-doux);
      font-size: .88rem; }
    .barre-enregistrement.modifiee { position: sticky; bottom: .75rem; z-index: 3; border-color: #f0dcb8; background: #fffaf0; color: #8a5a00;
      box-shadow: 0 -4px 16px rgba(16, 24, 40, .08); }
    .barre-enregistrement .boutons { display: flex; gap: .5rem; }

    @media (max-width: 640px) {
      .ligne { grid-template-columns: 1fr 1fr; padding: .85rem 1rem; row-gap: .6rem; }
      .ligne .nom { grid-column: 1 / -1; }
      .interrupteur { justify-content: flex-start; }
      .libelle-mobile { display: inline; }
      .entete-choix .nom { display: none; }
      .titre-groupe { padding-left: 1rem; }
      .destinataire { flex-wrap: wrap; }
    }
  `
})
export class PreferencesNotifications implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/notifications/preferences`;

  readonly vue = signal<VuePreferences | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly occupe = signal(false);
  readonly modifie = signal(false);
  /** Les cases changent dans les objets : ce compteur fait recalculer groupes et totaux. */
  private readonly revision = signal(0);
  /** Choix tels qu'enregistres, pour annuler les modifications. */
  private enregistre: LigneType[] = [];

  /** Notifications generales d'abord, puis une section par module. */
  readonly groupes = computed<Groupe[]>(() => {
    this.revision();
    const v = this.vue();
    if (!v) {
      return [];
    }
    const groupes: Groupe[] = [];
    for (const l of v.types) {
      const titre = l.module || 'General';
      let g = groupes.find((x) => x.titre === titre);
      if (!g) {
        g = { titre, lignes: [] };
        groupes.push(g);
      }
      g.lignes.push(l);
    }
    return groupes.sort((a, b) => (a.titre === 'General' ? -1 : b.titre === 'General' ? 1 : 0));
  });

  readonly resume = computed(() => {
    this.revision();
    const v = this.vue();
    if (!v) {
      return '';
    }
    const w = v.types.filter((t) => t.whatsapp).length;
    const e = v.types.filter((t) => t.email).length;
    return `${w} sur WhatsApp, ${e} par e-mail, sur ${v.types.length} notifications.`;
  });

  ngOnInit(): void {
    this.http.get<VuePreferences>(this.base).subscribe({
      next: (v) => this.charger(v),
      error: (e) => this.erreur.set(message(e, 'Notifications indisponibles.'))
    });
  }

  numeroLisible(numero: string): string {
    const chiffres = numero.replace(/[^0-9]/g, '');
    const complet = chiffres.length === 8 ? '228' + chiffres : chiffres;
    if (complet.length === 11 && complet.startsWith('228')) {
      return '+228 ' + complet.slice(3).replace(/(\d{2})(?=\d)/g, '$1 ');
    }
    return numero.startsWith('+') ? numero : '+' + chiffres;
  }

  toutCoche(canal: Canal): boolean {
    this.revision();
    const v = this.vue();
    return !!v && v.types.length > 0 && v.types.every((t) => t[canal]);
  }

  tout(canal: Canal): void {
    const v = this.vue();
    if (!v) {
      return;
    }
    const valeur = !v.types.every((t) => t[canal]);
    v.types.forEach((t) => {
      if (!(canal === 'email' && t.emailObligatoire)) {
        t[canal] = valeur;
      }
    });
    this.changer();
  }

  changer(): void {
    const v = this.vue();
    const different = !!v && v.types.some((t) => {
      const e = this.enregistre.find((x) => x.type === t.type);
      return !e || e.whatsapp !== t.whatsapp || e.email !== t.email;
    });
    this.revision.update((n) => n + 1);
    this.modifie.set(different);
    this.info.set(null);
  }

  annuler(): void {
    const v = this.vue();
    if (v) {
      this.charger({ ...v, types: this.enregistre.map((t) => ({ ...t })) });
    }
  }

  enregistrer(): void {
    const v = this.vue();
    if (!v) {
      return;
    }
    this.occupe.set(true);
    this.erreur.set(null);
    this.info.set(null);
    this.http.put<VuePreferences>(this.base, { types: v.types.map((t) => ({ type: t.type, whatsapp: t.whatsapp, email: t.email })) })
      .subscribe({
        next: (r) => {
          this.charger(r);
          this.occupe.set(false);
          this.info.set('Vos choix sont enregistres.');
        },
        error: (e) => {
          this.occupe.set(false);
          this.erreur.set(message(e, 'Enregistrement impossible.'));
        }
      });
  }

  readonly codeEnvoye = signal(false);
  code = '';

  demanderCode(): void {
    this.occupe.set(true);
    this.erreur.set(null);
    this.info.set(null);
    this.http.post<{ numero: string }>(`${this.base}/verification`, null).subscribe({
      next: (r) => {
        this.occupe.set(false);
        this.codeEnvoye.set(true);
        this.code = '';
        this.info.set(this.vue()?.whatsappBranche
          ? `Code envoye sur WhatsApp au ${this.numeroLisible(r.numero)} : saisissez-le ci-dessous.`
          : "Code enregistre, mais l'envoi WhatsApp n'est pas encore ouvert par la plateforme : il partira a son ouverture.");
      },
      error: (e) => {
        this.occupe.set(false);
        this.erreur.set(message(e, 'Envoi du code impossible.'));
      }
    });
  }

  confirmerCode(): void {
    this.occupe.set(true);
    this.erreur.set(null);
    this.info.set(null);
    this.http.post<VuePreferences>(`${this.base}/verification/confirmation`, { code: this.code.trim() }).subscribe({
      next: (v) => {
        this.occupe.set(false);
        this.codeEnvoye.set(false);
        this.charger(v);
        this.info.set('Numero confirme : les notifications WhatsApp y partiront desormais.');
      },
      error: (e) => {
        this.occupe.set(false);
        this.erreur.set(message(e, 'Code refuse.'));
      }
    });
  }

  essai(): void {
    this.occupe.set(true);
    this.erreur.set(null);
    this.info.set(null);
    this.http.post<void>(`${this.base}/essai`, null).subscribe({
      next: () => {
        this.occupe.set(false);
        this.info.set(this.vue()?.whatsappBranche
          ? 'Message d\'essai envoye : il arrive sur WhatsApp dans quelques secondes.'
          : 'Message d\'essai enregistre (simulation : l\'envoi WhatsApp n\'est pas encore ouvert).');
      },
      error: (e) => {
        this.occupe.set(false);
        this.erreur.set(message(e, 'Envoi impossible.'));
      }
    });
  }

  private charger(v: VuePreferences): void {
    this.enregistre = v.types.map((t) => ({ ...t }));
    this.vue.set({ ...v, types: v.types.map((t) => ({ ...t })) });
    this.revision.update((n) => n + 1);
    this.modifie.set(false);
  }
}

function message(e: HttpErrorResponse, defaut: string): string {
  return (e.error && typeof e.error === 'object' && e.error.message) || defaut;
}
