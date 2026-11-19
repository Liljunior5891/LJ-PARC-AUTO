import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

interface ParametresPlateforme {
  inscriptionConfirmationEmail: boolean;
  serveurEmailConfigure: boolean;
}

/**
 * Parametrage general de la plateforme : pour l'instant, la confirmation par
 * e-mail de l'inscription en libre-service.
 */
@Component({
  selector: 'app-parametres-plateforme',
  imports: [RouterLink],
  template: `
    <div class="entete-page">
      <div>
        <h1>Parametrage</h1>
        <p class="sous-titre">Reglages generaux de la plateforme.</p>
      </div>
    </div>

    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (info()) { <div class="message succes">{{ info() }}</div> }

    @if (parametres(); as p) {
      <section class="carte">
        <h2>Inscription des entreprises</h2>

        <label class="ligne-reglage">
          <span class="textes">
            <strong>Confirmation par e-mail</strong>
            <small>
              @if (p.inscriptionConfirmationEmail) {
                Active : apres le formulaire d'inscription, un lien est envoye par e-mail. Le compte n'est cree
                qu'a l'ouverture de ce lien (valable 24 heures).
              } @else {
                Desactivee : le compte est cree des l'envoi du formulaire et l'administrateur entre directement,
                sans verification de son adresse e-mail.
              }
            </small>
          </span>
          <input class="bascule" type="checkbox" role="switch" [checked]="p.inscriptionConfirmationEmail"
                 [disabled]="occupe()" (change)="basculer($any($event.target).checked)"
                 aria-label="Confirmation par e-mail de l'inscription" />
        </label>

        @if (!p.serveurEmailConfigure) {
          <div class="message avertissement">
            Aucun serveur d'e-mail (SMTP) n'est branche : les e-mails ne partent pas, ils sont seulement simules.
            @if (p.inscriptionConfirmationEmail) {
              Tant que c'est le cas, personne ne recoit le lien de confirmation : desactivez la confirmation,
              ou branchez un serveur SMTP (variables SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME,
              SPRING_MAIL_PASSWORD) puis relancez l'API.
            }
            Les messages simules restent visibles dans <a routerLink="/plateforme/notifications">Notifications</a>.
          </div>
        } @else {
          <p class="note">Serveur d'e-mail branche : les liens de confirmation partent reellement.</p>
        }
      </section>
    }
  `,
  styles: `
    .carte { max-width: 760px; padding: 1.2rem 1.4rem; }
    h2 { margin: 0 0 1rem; font-size: 1.05rem; }
    .ligne-reglage { display: flex; align-items: center; justify-content: space-between; gap: 1.5rem;
      padding: 0.9rem 0; border-top: 1px solid var(--couleur-bordure); border-bottom: 1px solid var(--couleur-bordure);
      cursor: pointer; }
    .textes { display: flex; flex-direction: column; gap: 0.25rem; }
    .textes small { color: var(--couleur-texte-doux); line-height: 1.45; }
    .message.avertissement { margin: 1rem 0 0; }
    .note { margin: 1rem 0 0; color: var(--couleur-texte-doux); font-size: 0.9rem; }

    .bascule { position: relative; flex: none; width: 44px; height: 24px; margin: 0; border-radius: 999px; background: #cfd6df;
      cursor: pointer; appearance: none; transition: background .18s ease; }
    .bascule::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%;
      background: #fff; box-shadow: 0 1px 3px rgba(0, 0, 0, .25); transition: transform .18s ease; }
    .bascule:checked { background: #1a9e5c; }
    .bascule:checked::after { transform: translateX(20px); }
    .bascule:disabled { opacity: .55; cursor: not-allowed; }
    .bascule:focus-visible { outline: 2px solid var(--couleur-primaire-clair); outline-offset: 2px; }
  `
})
export class ParametresPlateformeComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/plateforme/parametres`;

  readonly parametres = signal<ParametresPlateforme | null>(null);
  readonly occupe = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly info = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<ParametresPlateforme>(this.api).subscribe({
      next: (p) => this.parametres.set(p),
      error: (e: HttpErrorResponse) => this.erreur.set(e.error?.message ?? 'Lecture du parametrage impossible.')
    });
  }

  basculer(active: boolean): void {
    this.occupe.set(true);
    this.erreur.set(null);
    this.info.set(null);
    this.http.put<ParametresPlateforme>(this.api, { inscriptionConfirmationEmail: active }).subscribe({
      next: (p) => {
        this.parametres.set(p);
        this.occupe.set(false);
        this.info.set(active ? 'Confirmation par e-mail activee.' : 'Confirmation par e-mail desactivee.');
      },
      error: (e: HttpErrorResponse) => {
        this.occupe.set(false);
        // Remet l'interrupteur dans l'etat enregistre.
        this.parametres.update((p) => (p ? { ...p } : p));
        this.erreur.set(e.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
