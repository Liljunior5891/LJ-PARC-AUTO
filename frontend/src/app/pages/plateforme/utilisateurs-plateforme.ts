import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { Page } from '../../core/models/models';
import { EntrepriseResume } from '../../core/models/plateforme';
import { LONGUEUR_MOT_DE_PASSE } from '../../core/models/utilisateur';
import { PlateformeService } from '../../core/services/plateforme.service';
import { Modale } from '../../shared/modale';
import { OptionSelect, SelectRecherche } from '../../shared/select-recherche';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';

interface CompteEntreprise {
  id: number;
  email: string;
  nom: string;
  prenom?: string | null;
  nomComplet: string;
  telephone?: string | null;
  actif: boolean;
  roles: string[];
  rolesLibelles: string[];
  entrepriseId: number;
  entrepriseNom: string;
  entrepriseStatut: string;
  motDePasseAChanger: boolean;
  deuxFacteurs: boolean;
  creeLe?: string | null;
  derniereConnexion?: string | null;
}

/** Action qui demande une confirmation avant de partir. */
type ActionSensible = 'desactiver' | 'activer' | 'deconnecter' | 'deux-facteurs';

const TEXTES_ACTIONS: Record<ActionSensible, { titre: string; texte: string; bouton: string }> = {
  desactiver: {
    titre: 'Desactiver le compte',
    texte: 'Le compte ne pourra plus se connecter et ses sessions ouvertes se ferment. Ses donnees sont conservees.',
    bouton: 'Desactiver'
  },
  activer: { titre: 'Reactiver le compte', texte: 'Le compte pourra de nouveau se connecter.', bouton: 'Reactiver' },
  deconnecter: {
    titre: 'Fermer les sessions',
    texte: 'Toutes les sessions ouvertes de ce compte (ordinateurs, telephones) se ferment : il devra se reconnecter.',
    bouton: 'Fermer les sessions'
  },
  'deux-facteurs': {
    titre: 'Retirer la double authentification',
    texte: 'A utiliser si la personne a perdu son telephone. Elle se connectera avec son seul mot de passe et pourra '
      + 'remettre la double authentification depuis son compte. Ses sessions ouvertes se ferment.',
    bouton: 'Retirer'
  }
};

/**
 * Comptes de toutes les entreprises pour le super-administrateur : recherche,
 * fiche, mot de passe provisoire, activation, sessions et double authentification.
 * Les profils restent geres par l'administrateur de chaque entreprise.
 */
@Component({
  selector: 'app-utilisateurs-plateforme',
  imports: [DatePipe, FormsModule, Modale, SelectRecherche, SuiteListe],
  styleUrl: '../utilisateurs/utilisateurs.scss',
  template: `
    <div class="entete-page">
      <div>
        <h1>Utilisateurs</h1>
        <p class="sous-titre">{{ page()?.totalElements ?? 0 }} compte(s) dans les entreprises. Les profils se gerent
          dans chaque entreprise ; chaque action ci-dessous est ecrite dans son journal.</p>
      </div>
    </div>

    @if (succes()) { <div class="message succes">{{ succes() }}</div> }
    @if (erreur() && !modaleOuverte()) { <div class="message erreur">{{ erreur() }}</div> }

    <div class="carte">
      <div class="grille-formulaire filtres">
        <div class="champ">
          <label for="up-recherche">Recherche</label>
          <input id="up-recherche" type="search" placeholder="Nom, e-mail, telephone, entreprise..."
                 [(ngModel)]="recherche" (keyup.enter)="filtrer()" (search)="filtrer()" />
        </div>
        <div class="champ">
          <label for="up-entreprise">Entreprise</label>
          <app-select-recherche id="up-entreprise" [options]="optionsEntreprises()" placeholder="Toutes les entreprises"
                                [(ngModel)]="filtreEntreprise" (ngModelChange)="filtrer()" />
        </div>
        <div class="champ">
          <label for="up-etat">Etat</label>
          <select id="up-etat" [(ngModel)]="filtreActif" (change)="filtrer()">
            <option value="">Tous les comptes</option>
            <option value="true">Comptes actifs</option>
            <option value="false">Comptes desactives</option>
          </select>
        </div>
      </div>

      <div class="tableau-conteneur" style="margin-top: 1rem">
        <table class="tableau">
          <thead>
            <tr>
              <th>Nom</th>
              <th>Entreprise</th>
              <th>Adresse e-mail</th>
              <th>Profils</th>
              <th>Etat</th>
              <th>Derniere connexion</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (u of page()?.content ?? []; track u.id) {
              <tr [class.compte-inactif]="!u.actif">
                <td>
                  {{ u.nomComplet }}
                  @if (u.telephone) { <small class="detail">{{ u.telephone }}</small> }
                </td>
                <td>
                  {{ u.entrepriseNom }}
                  @if (u.entrepriseStatut !== 'ACTIVE') { <small class="detail alerte">Entreprise suspendue</small> }
                </td>
                <td>{{ u.email }}</td>
                <td>
                  @for (libelle of u.rolesLibelles; track libelle) {
                    <span class="etiquette-profil">{{ libelle }}</span>
                  }
                </td>
                <td>
                  <span class="etiquette" [class.actif]="u.actif">{{ u.actif ? 'Actif' : 'Desactive' }}</span>
                  @if (u.motDePasseAChanger) { <small class="detail">Mot de passe provisoire</small> }
                  @if (u.deuxFacteurs) { <small class="detail">Double authentification</small> }
                </td>
                <td>{{ u.derniereConnexion ? (u.derniereConnexion | date: 'dd/MM/yyyy HH:mm') : 'Jamais' }}</td>
                <td class="actions-ligne">
                  <button class="bouton secondaire petit info" type="button" (click)="ouvrirEdition(u)">Modifier</button>
                  <button class="bouton secondaire petit info" type="button" (click)="ouvrirMotDePasse(u)">Mot de passe</button>
                  <button class="bouton secondaire petit" type="button" (click)="demander(u, 'deconnecter')"
                          title="Fermer toutes ses sessions">Deconnecter</button>
                  @if (u.deuxFacteurs) {
                    <button class="bouton secondaire petit" type="button" (click)="demander(u, 'deux-facteurs')"
                            title="Telephone perdu">Retirer 2FA</button>
                  }
                  <button class="bouton secondaire petit" [class.danger]="u.actif" type="button"
                          (click)="demander(u, u.actif ? 'desactiver' : 'activer')">
                    {{ u.actif ? 'Desactiver' : 'Activer' }}
                  </button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7">
                  <p class="message vide">{{ chargement() ? 'Chargement...' : 'Aucun compte pour ce filtre.' }}</p>
                </td>
              </tr>
            }
          </tbody>
        </table>
        @if (page(); as p) {
          <app-suite-liste [page]="p" (suite)="changerPage()" />
        }
      </div>
    </div>

    <!-- Modification de la fiche. -->
    @if (edite(); as u) {
      <app-modale titre="Modifier le compte" [sousTitre]="u.entrepriseNom" largeur="640px"
                  [erreur]="erreur()" (ferme)="fermer()">
        <div class="grille-formulaire">
          <div class="champ">
            <label for="up-email">Adresse e-mail (identifiant) <span class="requis">*</span></label>
            <input id="up-email" type="email" autocomplete="off" [(ngModel)]="fiche.email" />
          </div>
          <div class="champ">
            <label for="up-nom">Nom <span class="requis">*</span></label>
            <input id="up-nom" [(ngModel)]="fiche.nom" />
          </div>
          <div class="champ">
            <label for="up-prenom">Prenom</label>
            <input id="up-prenom" [(ngModel)]="fiche.prenom" />
          </div>
          <div class="champ">
            <label for="up-tel">Telephone</label>
            <input id="up-tel" type="tel" [(ngModel)]="fiche.telephone" />
          </div>
        </div>
        <p class="aide">Changer l'adresse e-mail ferme les sessions ouvertes : la personne se reconnecte avec la nouvelle.</p>
        <div class="actions-formulaire">
          <button class="bouton" type="button" [disabled]="occupe()" (click)="enregistrerFiche()">
            {{ occupe() ? 'Enregistrement...' : 'Enregistrer' }}
          </button>
          <button class="bouton secondaire" type="button" (click)="fermer()">Annuler</button>
        </div>
      </app-modale>
    }

    <!-- Mot de passe provisoire. -->
    @if (motDePasseDe(); as u) {
      <app-modale titre="Reinitialiser le mot de passe" [sousTitre]="u.nomComplet + ' · ' + u.email" largeur="560px"
                  [erreur]="erreur()" (ferme)="fermer()">
        @if (provisoire(); as mdp) {
          <p class="aide">Mot de passe provisoire de <strong>{{ u.nomComplet }}</strong>. Il ne sera plus affiche :
            transmettez-le par un canal sur. La personne devra le changer a sa prochaine connexion.</p>
          <div class="mot-de-passe-affiche">
            <code>{{ mdp }}</code>
            <button class="bouton secondaire petit" type="button" (click)="copier(mdp)">{{ copie() ? 'Copie ✓' : 'Copier' }}</button>
          </div>
          <div class="actions-formulaire">
            <button class="bouton" type="button" (click)="fermer()">Terminer</button>
          </div>
        } @else {
          <p class="aide">L'ancien mot de passe n'est pas demande. Le nouveau est provisoire : la personne le remplacera a
            sa prochaine connexion, et ses sessions ouvertes se ferment.</p>
          <div class="choix-mode">
            <label><input type="radio" name="up-mode" [checked]="genere()" (change)="genere.set(true)" />
              Generer un mot de passe</label>
            <label><input type="radio" name="up-mode" [checked]="!genere()" (change)="genere.set(false)" />
              Le saisir moi-meme</label>
          </div>
          @if (!genere()) {
            <div class="champ">
              <label for="up-mdp">Nouveau mot de passe <span class="requis">*</span></label>
              <input id="up-mdp" type="text" autocomplete="off" [(ngModel)]="saisieMotDePasse" />
              <small>{{ longueurMinimale }} caracteres minimum.</small>
            </div>
          }
          <div class="actions-formulaire">
            <button class="bouton" type="button" [disabled]="occupe()" (click)="reinitialiser()">
              {{ occupe() ? 'Enregistrement...' : 'Reinitialiser' }}
            </button>
            <button class="bouton secondaire" type="button" (click)="fermer()">Annuler</button>
          </div>
        }
      </app-modale>
    }

    <!-- Confirmation d'une action sensible. -->
    @if (confirmation(); as c) {
      <app-modale [titre]="textes[c.action].titre" [sousTitre]="c.compte.nomComplet + ' · ' + c.compte.entrepriseNom"
                  largeur="520px" [erreur]="erreur()" (ferme)="fermer()">
        <p class="aide">{{ textes[c.action].texte }}</p>
        <div class="actions-formulaire">
          <button class="bouton" [class.danger]="c.action === 'desactiver'" type="button" [disabled]="occupe()"
                  (click)="confirmer()">{{ occupe() ? 'Patientez...' : textes[c.action].bouton }}</button>
          <button class="bouton secondaire" type="button" (click)="fermer()">Annuler</button>
        </div>
      </app-modale>
    }
  `,
  styles: `
    .filtres { grid-template-columns: 2fr 2fr 1fr; }
    .detail { display: block; margin-top: 0.15rem; font-size: 0.75rem; color: var(--couleur-texte-doux); }
    .detail.alerte { color: var(--couleur-danger); }
    .aide { color: var(--couleur-texte-doux); line-height: 1.5; }
    .choix-mode { display: flex; gap: 1.2rem; flex-wrap: wrap; margin: 0.6rem 0 1rem; }
    .choix-mode label { display: flex; align-items: center; gap: 0.4rem; cursor: pointer; }
    .mot-de-passe-affiche { display: flex; align-items: center; gap: 0.8rem; margin: 0.8rem 0 1.2rem;
      padding: 0.8rem 1rem; border: 1px dashed var(--couleur-bordure); border-radius: 8px; background: #f7f9fc; }
    .mot-de-passe-affiche code { font-size: 1.15rem; font-weight: 700; letter-spacing: 0.06em; user-select: all; }
    @media (max-width: 760px) { .filtres { grid-template-columns: 1fr; } }
  `
})
export class UtilisateursPlateforme implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly plateforme = inject(PlateformeService);
  private readonly api = `${environment.apiUrl}/plateforme/utilisateurs`;
  private readonly suite = new SuiteDePages();

  readonly textes = TEXTES_ACTIONS;
  readonly longueurMinimale = LONGUEUR_MOT_DE_PASSE;

  readonly page = signal<Page<CompteEntreprise> | null>(null);
  readonly optionsEntreprises = signal<OptionSelect[]>([]);
  readonly chargement = signal(false);
  readonly occupe = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  recherche = '';
  filtreEntreprise: number | null = null;
  filtreActif = '';

  readonly edite = signal<CompteEntreprise | null>(null);
  fiche = { email: '', nom: '', prenom: '', telephone: '' };

  readonly motDePasseDe = signal<CompteEntreprise | null>(null);
  readonly genere = signal(true);
  readonly provisoire = signal<string | null>(null);
  readonly copie = signal(false);
  saisieMotDePasse = '';

  readonly confirmation = signal<{ compte: CompteEntreprise; action: ActionSensible } | null>(null);

  ngOnInit(): void {
    this.plateforme.entreprises().subscribe({
      next: (liste: EntrepriseResume[]) => this.optionsEntreprises.set(
        [...liste].sort((a, b) => a.nom.localeCompare(b.nom)).map((e) => ({ valeur: e.id, libelle: e.nom, detail: e.pays || undefined }))
      )
    });
    this.charger();
  }

  modaleOuverte(): boolean {
    return !!(this.edite() || this.motDePasseDe() || this.confirmation());
  }

  charger(): void {
    this.chargement.set(true);
    let params = new HttpParams().set('page', this.suite.numero()).set('taille', 50);
    if (this.recherche.trim()) params = params.set('q', this.recherche.trim());
    if (this.filtreEntreprise != null) params = params.set('entrepriseId', this.filtreEntreprise);
    if (this.filtreActif) params = params.set('actif', this.filtreActif);
    this.http.get<Page<CompteEntreprise>>(this.api, { params }).subscribe({
      next: (p) => {
        this.page.set(cumulerPage(this.page(), p));
        this.chargement.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.chargement.set(false);
        this.erreur.set(e.error?.message ?? 'Chargement des comptes impossible.');
      }
    });
  }

  filtrer(): void {
    this.charger();
  }

  changerPage(): void {
    if (this.suite.demander(this.page())) this.charger();
  }

  fermer(): void {
    this.edite.set(null);
    this.motDePasseDe.set(null);
    this.confirmation.set(null);
    this.provisoire.set(null);
    this.erreur.set(null);
  }

  // ------------------------------------------------------------ fiche

  ouvrirEdition(u: CompteEntreprise): void {
    this.fermer();
    this.succes.set(null);
    this.fiche = { email: u.email, nom: u.nom, prenom: u.prenom ?? '', telephone: u.telephone ?? '' };
    this.edite.set(u);
  }

  enregistrerFiche(): void {
    const u = this.edite();
    if (!u) return;
    if (!this.fiche.email.trim() || !this.fiche.nom.trim()) {
      this.erreur.set("L'adresse e-mail et le nom sont obligatoires.");
      return;
    }
    this.executer(this.http.put<void>(`${this.api}/${u.id}`, this.fiche), `Compte de ${this.fiche.nom.trim()} modifie.`);
  }

  // ------------------------------------------------------------ mot de passe

  ouvrirMotDePasse(u: CompteEntreprise): void {
    this.fermer();
    this.succes.set(null);
    this.genere.set(true);
    this.saisieMotDePasse = '';
    this.copie.set(false);
    this.motDePasseDe.set(u);
  }

  reinitialiser(): void {
    const u = this.motDePasseDe();
    if (!u) return;
    const saisi = this.genere() ? '' : this.saisieMotDePasse;
    if (!this.genere() && saisi.length < LONGUEUR_MOT_DE_PASSE) {
      this.erreur.set(`Le mot de passe doit contenir au moins ${LONGUEUR_MOT_DE_PASSE} caracteres.`);
      return;
    }
    this.occupe.set(true);
    this.erreur.set(null);
    this.http.put<{ motDePasse: string }>(`${this.api}/${u.id}/mot-de-passe`, { motDePasse: saisi }).subscribe({
      next: (r) => {
        this.occupe.set(false);
        this.provisoire.set(r.motDePasse);
        this.recharger();
      },
      error: (e: HttpErrorResponse) => {
        this.occupe.set(false);
        this.erreur.set(e.error?.message ?? 'Reinitialisation impossible.');
      }
    });
  }

  copier(texte: string): void {
    void navigator.clipboard?.writeText(texte).then(() => this.copie.set(true));
  }

  // ------------------------------------------------------------ actions confirmees

  demander(u: CompteEntreprise, action: ActionSensible): void {
    this.fermer();
    this.succes.set(null);
    this.confirmation.set({ compte: u, action });
  }

  confirmer(): void {
    const c = this.confirmation();
    if (!c) return;
    const id = c.compte.id;
    const nom = c.compte.nomComplet;
    switch (c.action) {
      case 'desactiver':
      case 'activer':
        this.executer(this.http.patch<void>(`${this.api}/${id}/activation`, { actif: c.action === 'activer' }),
          c.action === 'activer' ? `Compte de ${nom} reactive.` : `Compte de ${nom} desactive.`);
        break;
      case 'deconnecter':
        this.executer(this.http.post<void>(`${this.api}/${id}/deconnexion`, {}), `Sessions de ${nom} fermees.`);
        break;
      case 'deux-facteurs':
        this.executer(this.http.delete<void>(`${this.api}/${id}/deux-facteurs`), `Double authentification de ${nom} retiree.`);
        break;
    }
  }

  private executer(requete: import('rxjs').Observable<void>, message: string): void {
    this.occupe.set(true);
    this.erreur.set(null);
    requete.subscribe({
      next: () => {
        this.occupe.set(false);
        this.fermer();
        this.succes.set(message);
        this.recharger();
      },
      error: (e: HttpErrorResponse) => {
        this.occupe.set(false);
        this.erreur.set(e.error?.message ?? 'Action impossible.');
      }
    });
  }

  /** Recharge la liste depuis le debut pour afficher l'etat a jour. */
  private recharger(): void {
    this.page.set(null);
    this.charger();
  }
}
