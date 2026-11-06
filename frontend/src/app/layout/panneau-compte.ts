import { Component, OnDestroy, OnInit, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth/auth.service';
import { LONGUEUR_MOT_DE_PASSE, Utilisateur } from '../core/models/utilisateur';
import { UtilisateurService } from '../core/services/utilisateur.service';
import { VerrouService } from '../core/services/verrou.service';
import { OperationService } from '../core/operation/operation.service';

/**
 * « Mon compte », dans une fenetre verticale ancree a droite : chacun met a jour
 * son nom, son prenom et son telephone, et change son mot de passe. L'adresse
 * e-mail (identifiant de connexion) et les profils restent geres par
 * l'administrateur. La fenetre ne se ferme que par sa croix ou Annuler.
 */
@Component({
  selector: 'app-panneau-compte',
  imports: [FormsModule],
  template: `
    <div class="voile-compte"></div>
    <aside class="panneau-compte" role="dialog" aria-modal="true" aria-label="Mon compte">
      <header>
        <div>
          <h2>Mon compte</h2>
          <p>Vos informations et votre mot de passe</p>
        </div>
        <button type="button" class="fermer" aria-label="Fermer" (click)="ferme.emit()">×</button>
      </header>

      <form class="corps" (ngSubmit)="enregistrer()">
        @if (compte(); as c) {
          <section class="identite">
            <span class="avatar">{{ initiales() }}</span>
            <div>
              <strong>{{ c.nomComplet }}</strong>
              <small>{{ c.email }}</small>
              <span class="profils">
                @for (libelle of c.rolesLibelles; track libelle) {
                  <span>{{ libelle }}</span>
                }
              </span>
            </div>
          </section>
        }

        @if (erreur()) {
          <div class="message erreur">{{ erreur() }}</div>
        }
        @if (succes()) {
          <div class="message succes">{{ succes() }}</div>
        }

        <section>
          <h3>Mes informations</h3>
          <div class="ligne-deux">
          <div class="champ">
            <label for="compte-nom">Nom <span class="requis">*</span></label>
            <input id="compte-nom" name="nom" required maxlength="100" [(ngModel)]="nom" autocomplete="family-name" />
          </div>
          <div class="champ">
            <label for="compte-prenom">Prenom</label>
            <input id="compte-prenom" name="prenom" maxlength="100" [(ngModel)]="prenom" autocomplete="given-name" />
          </div>
          </div>
          <div class="ligne-deux">
          <div class="champ">
            <label for="compte-telephone">Telephone</label>
            <input id="compte-telephone" name="telephone" type="tel" maxlength="30" [(ngModel)]="telephone"
                   autocomplete="tel" placeholder="Ex. +228 90 00 00 00" />
          </div>
          <div class="champ">
            <label for="compte-email">Adresse e-mail</label>
            <input id="compte-email" name="email" [value]="compte()?.email ?? ''" disabled />
          </div>
          </div>
          <small class="aide-email">L'adresse e-mail est votre identifiant de connexion : seul un administrateur peut la changer.</small>
        </section>

        <section>
          <h3>Mot de passe</h3>
          <p class="aide">Laissez vide pour garder votre mot de passe actuel.</p>
          <div class="champ">
            <label for="compte-actuel">Mot de passe actuel</label>
            <input id="compte-actuel" name="actuel" type="password" autocomplete="current-password" [(ngModel)]="actuel" />
          </div>
          <div class="ligne-deux">
          <div class="champ">
            <label for="compte-nouveau">Nouveau mot de passe</label>
            <input id="compte-nouveau" name="nouveau" type="password" autocomplete="new-password" [(ngModel)]="nouveau" />
          </div>
          <div class="champ">
            <label for="compte-confirmation">Confirmation</label>
            <input id="compte-confirmation" name="confirmation" type="password" autocomplete="new-password"
                   [(ngModel)]="confirmation" />
          </div>
          </div>
          <small class="aide-email">{{ longueurMinimale }} caracteres minimum.</small>
        </section>

        @if (avecPin) {
          <section class="bloc-pin">
            <h3>Code PIN de caisse</h3>
            <p class="aide">Il deverrouille votre ecran de caisse. De 4 a 6 chiffres, a garder pour vous.</p>
            @if (erreurPin()) { <div class="message erreur">{{ erreurPin() }}</div> }
            @if (succesPin()) { <div class="message succes">{{ succesPin() }}</div> }
            <div class="ligne-pin">
              <div class="champ">
                <label for="pin-actuel">Code actuel</label>
                <input id="pin-actuel" name="pinActuel" type="password" inputmode="numeric" maxlength="6" autocomplete="off"
                       [(ngModel)]="pinActuel" />
              </div>
              <div class="champ">
                <label for="pin-nouveau">Nouveau code</label>
                <input id="pin-nouveau" name="pinNouveau" type="password" inputmode="numeric" maxlength="6" autocomplete="off"
                       [(ngModel)]="pinNouveau" />
              </div>
              <div class="champ">
                <label for="pin-confirmation">Confirmation</label>
                <input id="pin-confirmation" name="pinConfirmation" type="password" inputmode="numeric" maxlength="6"
                       autocomplete="off" [(ngModel)]="pinConfirmation" />
              </div>
            </div>
            <button class="bouton secondaire" type="button" [disabled]="changementPin()" (click)="changerPin()">
              {{ changementPin() ? 'Changement...' : 'Changer mon code PIN' }}
            </button>
            <small class="aide">Code oublie ? Demandez a votre gerant de l'effacer : vous en creerez un nouveau au deverrouillage.</small>
          </section>
        }

        <footer class="actions-formulaire">
          <button class="bouton secondaire annuler" type="button" (click)="ferme.emit()">Annuler</button>
          <button class="bouton" type="submit" [disabled]="enregistrement() || !nom.trim()">
            {{ enregistrement() ? 'Enregistrement...' : 'Enregistrer' }}
          </button>
        </footer>
      </form>
    </aside>
  `,
  styles: `
    .voile-compte { position: fixed; inset: 0; z-index: 1000; background: rgba(16, 30, 24, 0.45); backdrop-filter: blur(2px); }
    .panneau-compte {
      position: fixed; top: 0; right: 0; bottom: 0; z-index: 1001; display: flex; flex-direction: column;
      width: min(540px, 100vw); background: var(--couleur-surface); box-shadow: -18px 0 50px rgba(16, 40, 30, 0.28);
      animation: glissement 0.2s ease-out;
    }
    @keyframes glissement { from { transform: translateX(40px); opacity: 0; } }
    @media (prefers-reduced-motion: reduce) { .panneau-compte { animation: none; } }
    header {
      display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.1rem 1.3rem 1rem;
      background: linear-gradient(180deg, #1f6fd1 0%, color-mix(in srgb, #1f6fd1 82%, #000) 100%); color: #fff;
    }
    header h2 { margin: 0; color: #fff; font-size: 1.15rem; }
    header p { margin: 0.2rem 0 0; color: rgba(255, 255, 255, 0.82); font-size: 0.8rem; }
    .fermer {
      flex: none; width: 34px; height: 34px; border: none; border-radius: 50%; background: rgba(255, 255, 255, 0.16);
      color: #fff; font-size: 1.3rem; line-height: 1; cursor: pointer;
    }
    .fermer:hover { background: rgba(255, 255, 255, 0.28); }
    .corps { display: flex; flex: 1; flex-direction: column; min-height: 0; overflow-y: auto; padding: 1rem 1.3rem 0; }
    section { margin-bottom: 1.1rem; }
    h3 { margin: 0 0 0.6rem; font-size: 0.95rem; }
    .identite {
      display: flex; align-items: center; gap: 0.9rem; padding: 0.9rem; border-radius: 14px;
      background: color-mix(in srgb, var(--couleur-fond) 70%, var(--couleur-surface));
    }
    .identite > div { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
    .identite strong { font-size: 1.05rem; }
    .identite small { overflow: hidden; color: var(--couleur-texte-doux); text-overflow: ellipsis; }
    .avatar {
      display: grid; flex-shrink: 0; place-items: center; width: 56px; height: 56px; border: 2px solid #f6a623;
      border-radius: 50%; background: #fff5e4; color: #b8650a; font-size: 1.2rem; font-weight: 700;
    }
    .profils { display: flex; flex-wrap: wrap; gap: 0.25rem; margin-top: 0.2rem; }
    .profils span { padding: 0.1rem 0.5rem; border-radius: 999px; background: #eef2f7; font-size: 0.72rem; font-weight: 600; }
    .champ { margin-bottom: 0.7rem; min-width: 0; }
    .ligne-deux { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.6rem; }
    .aide-email { display: block; margin: -0.3rem 0 0.4rem; color: var(--couleur-texte-doux); font-size: 0.75rem; }
    @media (max-width: 400px) { .ligne-deux { grid-template-columns: 1fr; } }
    .champ input { width: 100%; }
    .champ input:disabled { background: #f3f5f7; color: var(--couleur-texte-doux); cursor: not-allowed; }
    .champ small, .aide { color: var(--couleur-texte-doux); font-size: 0.75rem; }
    .aide { margin: -0.3rem 0 0.6rem; }
    .bloc-pin { padding: 0.9rem; border: 1px solid var(--couleur-bordure); border-radius: 14px; }
    .bloc-pin .aide { display: block; margin: 0 0 0.6rem; }
    .bloc-pin small.aide { margin: 0.6rem 0 0; }
    .ligne-pin { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.5rem; }
    .ligne-pin input { letter-spacing: 0.3em; text-align: center; }
    @media (max-width: 420px) { .ligne-pin { grid-template-columns: 1fr; } }
    .actions-formulaire {
      position: sticky; bottom: 0; justify-content: center; margin: auto -1.3rem 0; padding: 1rem 1.3rem 1.2rem;
      border-top: 1px solid var(--couleur-bordure); background: color-mix(in srgb, var(--couleur-fond) 70%, var(--couleur-surface));
    }
    .actions-formulaire .bouton { justify-content: center; min-width: 140px; padding: 0.7rem 1.5rem; border-radius: 11px; font-weight: 600; }
  `
})
export class PanneauCompte implements OnInit, OnDestroy {
  private readonly service = inject(UtilisateurService);
  private readonly auth = inject(AuthService);
  private readonly verrou = inject(VerrouService);
  private readonly operation = inject(OperationService);
  readonly ferme = output<void>();

  /** Caissier et serveur : ils changent eux-memes le code PIN de leur ecran de caisse. */
  readonly avecPin = this.auth.aRole('CAISSIER', 'SERVEUR');
  readonly changementPin = signal(false);
  readonly erreurPin = signal<string | null>(null);
  readonly succesPin = signal<string | null>(null);
  pinActuel = '';
  pinNouveau = '';
  pinConfirmation = '';

  async changerPin(): Promise<void> {
    this.erreurPin.set(null);
    this.succesPin.set(null);
    if (!/^\d{4,6}$/.test(this.pinActuel)) {
      this.erreurPin.set('Saisissez votre code actuel (4 a 6 chiffres).');
      return;
    }
    if (!/^\d{4,6}$/.test(this.pinNouveau)) {
      this.erreurPin.set('Le nouveau code compte 4 a 6 chiffres.');
      return;
    }
    if (this.pinNouveau !== this.pinConfirmation) {
      this.erreurPin.set('La confirmation ne correspond pas au nouveau code.');
      return;
    }
    const accepte = await this.operation.demander({
      titre: 'Changer votre code PIN de caisse ?',
      message: 'Le nouveau code sera demande a chaque deverrouillage de votre ecran.',
      libelleAction: 'Changer'
    });
    if (!accepte) {
      return;
    }
    this.changementPin.set(true);
    this.verrou.changer(this.pinActuel, this.pinNouveau).subscribe({
      next: () => {
        this.changementPin.set(false);
        this.pinActuel = this.pinNouveau = this.pinConfirmation = '';
        this.succesPin.set('Votre code PIN est change.');
      },
      error: (e) => {
        this.changementPin.set(false);
        this.pinActuel = '';
        this.erreurPin.set(this.message(e, 'Changement du code impossible.'));
      }
    });
  }

  readonly longueurMinimale = LONGUEUR_MOT_DE_PASSE;
  readonly compte = signal<Utilisateur | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  nom = '';
  prenom = '';
  telephone = '';
  actuel = '';
  nouveau = '';
  confirmation = '';

  ngOnInit(): void {
    document.body.classList.add('modale-ouverte');
    this.service.moi().subscribe({
      next: (u) => this.remplir(u),
      error: () => this.erreur.set('Lecture de votre compte impossible.')
    });
  }

  ngOnDestroy(): void {
    document.body.classList.remove('modale-ouverte');
  }

  initiales(): string {
    return (this.compte()?.nomComplet ?? '').split(/\s+/).filter(Boolean).slice(0, 2)
      .map((mot) => mot[0].toUpperCase()).join('');
  }

  /** Informations toujours enregistrees ; le mot de passe seulement s'il est saisi. */
  enregistrer(): void {
    if (this.enregistrement() || !this.nom.trim()) {
      return;
    }
    this.erreur.set(null);
    this.succes.set(null);
    const changeMotDePasse = !!(this.actuel || this.nouveau || this.confirmation);
    if (changeMotDePasse) {
      if (!this.actuel || !this.nouveau || !this.confirmation) {
        this.erreur.set('Pour changer de mot de passe, remplissez les trois champs.');
        return;
      }
      if (this.nouveau.length < this.longueurMinimale) {
        this.erreur.set(`Le nouveau mot de passe doit faire ${this.longueurMinimale} caracteres au minimum.`);
        return;
      }
      if (this.nouveau !== this.confirmation) {
        this.erreur.set('La confirmation ne correspond pas au nouveau mot de passe.');
        return;
      }
    }
    this.enregistrement.set(true);
    this.service.mettreAJourSonProfil({ nom: this.nom.trim(), prenom: this.prenom.trim() || null,
      telephone: this.telephone.trim() || null }).subscribe({
      next: (u) => {
        this.remplir(u);
        this.auth.mettreAJourNom(u.nomComplet);
        if (!changeMotDePasse) {
          this.enregistrement.set(false);
          this.succes.set('Vos informations sont enregistrees.');
          return;
        }
        this.service.changerSonMotDePasse({ motDePasseActuel: this.actuel, nouveauMotDePasse: this.nouveau }).subscribe({
          next: () => {
            this.enregistrement.set(false);
            this.actuel = this.nouveau = this.confirmation = '';
            this.succes.set('Informations et mot de passe enregistres. Le nouveau mot de passe sera demande a la prochaine connexion.');
          },
          error: (e) => {
            this.enregistrement.set(false);
            this.erreur.set(this.message(e, 'Informations enregistrees, mais changement du mot de passe impossible.'));
          }
        });
      },
      error: (e) => {
        this.enregistrement.set(false);
        this.erreur.set(this.message(e, 'Enregistrement impossible.'));
      }
    });
  }

  private remplir(u: Utilisateur): void {
    this.compte.set(u);
    this.nom = u.nom;
    this.prenom = u.prenom ?? '';
    this.telephone = u.telephone ?? '';
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
