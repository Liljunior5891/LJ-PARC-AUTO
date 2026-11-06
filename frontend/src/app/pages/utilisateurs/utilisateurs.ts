import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Page, Role } from '../../core/models/models';
import {
  LONGUEUR_MOT_DE_PASSE,
  ProfilRole,
  Utilisateur
} from '../../core/models/utilisateur';
import { UtilisateurService } from '../../core/services/utilisateur.service';
import { Modale } from '../../shared/modale';
import { messageChampsManquants } from '../../shared/validation-formulaire';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../shared/suite-liste';

/**
 * Gestion des comptes (chapitre 40), reservee a l'administrateur.
 *
 * La liste et le formulaire cohabitent sur le meme ecran : creer un compte ou
 * corriger un profil est une operation courte, qui ne merite pas une page a
 * part ni un aller-retour de navigation.
 */
@Component({
  selector: 'app-utilisateurs',
  imports: [SuiteListe, FormsModule, DatePipe, Modale],
  templateUrl: './utilisateurs.html',
  styleUrl: './utilisateurs.scss'
})
export class Utilisateurs implements OnInit {
  private readonly service = inject(UtilisateurService);
  private readonly auth = inject(AuthService);

  readonly longueurMinimale = LONGUEUR_MOT_DE_PASSE;
  readonly page = signal<Page<Utilisateur> | null>(null);
  readonly profils = signal<ProfilRole[]>([]);
  readonly chargement = signal(false);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  /** Compte en cours d'edition ; null quand le formulaire cree un compte. */
  readonly edite = signal<Utilisateur | null>(null);
  readonly formulaireOuvert = signal(false);
  /** Compte dont on reinitialise le mot de passe. */
  readonly reinitialise = signal<Utilisateur | null>(null);
  /** Compte dont on definit le code PIN de caisse. */
  readonly pinCible = signal<Utilisateur | null>(null);
  nouveauPin = '';

  recherche = '';
  filtreRole: Role | '' = '';
  filtreActif: 'true' | 'false' | '' = '';
  private readonly suite = new SuiteDePages();

  /** Champs du formulaire de compte. */
  email = '';
  nom = '';
  prenom = '';
  telephone = '';
  motDePasse = '';
  /** Aucun profil coche d'avance : un profil d'un module non active resterait coche sans etre visible. */
  rolesChoisis: Role[] = [];
  nouveauMotDePasse = '';

  ngOnInit(): void {
    this.service.profils().subscribe({
      next: (profils) => this.profils.set(profils),
      error: () => this.erreur.set('Chargement des profils impossible.')
    });
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        q: this.recherche || undefined,
        role: this.filtreRole || undefined,
        actif: this.filtreActif === '' ? undefined : this.filtreActif === 'true',
        page: this.suite.numero()
      })
      .subscribe({
        next: (page) => {
          this.page.set(cumulerPage(this.page(), page));
          this.chargement.set(false);
          this.erreur.set(null);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des comptes impossible.');
        }
      });
  }

  filtrer(): void {
    this.charger();
  }

  /** Bas de la liste atteint : la page suivante rejoint les lignes deja affichees. */
  changerPage(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  // ------------------------------------------------------------------
  // Formulaire de compte
  // ------------------------------------------------------------------

  ouvrirCreation(): void {
    this.edite.set(null);
    this.email = '';
    this.nom = '';
    this.prenom = '';
    this.telephone = '';
    this.motDePasse = '';
    this.rolesChoisis = [];
    this.formulaireOuvert.set(true);
    this.reinitialise.set(null);
    this.messages();
  }

  ouvrirEdition(utilisateur: Utilisateur): void {
    this.edite.set(utilisateur);
    this.email = utilisateur.email;
    this.nom = utilisateur.nom;
    this.prenom = utilisateur.prenom ?? '';
    this.telephone = utilisateur.telephone ?? '';
    this.motDePasse = '';
    // Seuls les profils proposes (modules actives) restent : un profil d'un module coupe part a l'enregistrement.
    const proposes = new Set<Role>(this.profils().map((p) => p.valeur));
    this.rolesChoisis = utilisateur.roles.filter((r) => proposes.size === 0 || proposes.has(r));
    this.formulaireOuvert.set(true);
    this.reinitialise.set(null);
    this.messages();
  }

  fermerFormulaire(): void {
    this.formulaireOuvert.set(false);
    this.edite.set(null);
  }

  basculerRole(role: Role): void {
    this.rolesChoisis = this.rolesChoisis.includes(role)
      ? this.rolesChoisis.filter((r) => r !== role)
      : [...this.rolesChoisis, role];
  }

  aLeRole(role: Role): boolean {
    return this.rolesChoisis.includes(role);
  }

  enregistrer(): void {
    if (this.enregistrement()) {
      return;
    }
    const manquants = messageChampsManquants({
      'Adresse e-mail': this.email,
      Nom: this.nom,
      ...(this.edite() ? {} : { 'Mot de passe initial': this.motDePasse })
    });
    if (manquants) {
      this.erreur.set(manquants);
      return;
    }
    if (this.rolesChoisis.length === 0) {
      this.erreur.set('Choisissez au moins un profil.');
      return;
    }
    this.messages();
    this.enregistrement.set(true);

    const commun = {
      email: this.email.trim(),
      nom: this.nom.trim(),
      prenom: this.prenom.trim() || undefined,
      telephone: this.telephone.trim() || undefined,
      roles: this.rolesChoisis
    };
    const encours = this.edite();
    const requete = encours
      ? this.service.modifier(encours.id, commun)
      : this.service.creer({ ...commun, motDePasse: this.motDePasse });

    requete.subscribe({
      next: (utilisateur) => {
        this.enregistrement.set(false);
        this.fermerFormulaire();
        this.succes.set(
          encours ? `Compte ${utilisateur.email} modifie.` : `Compte ${utilisateur.email} cree.`
        );
        this.charger();
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(this.messageErreur(reponse, "Enregistrement du compte impossible."));
      }
    });
  }

  // ------------------------------------------------------------------
  // Activation et mot de passe
  // ------------------------------------------------------------------

  changerActivation(utilisateur: Utilisateur): void {
    this.messages();
    this.service.changerActivation(utilisateur.id, !utilisateur.actif).subscribe({
      next: (maj) => {
        this.succes.set(`Compte ${maj.email} ${maj.actif ? 'active' : 'desactive'}.`);
        this.charger();
      },
      error: (reponse) =>
        this.erreur.set(this.messageErreur(reponse, "Changement d'activation impossible."))
    });
  }

  ouvrirPin(utilisateur: Utilisateur): void {
    this.messages();
    this.nouveauPin = '';
    this.pinCible.set(utilisateur);
  }

  enregistrerPin(): void {
    if (!/^\d{4,6}$/.test(this.nouveauPin)) {
      this.erreur.set('Le code PIN compte 4 a 6 chiffres.');
      return;
    }
    this.appliquerPin(this.nouveauPin, 'defini');
  }

  effacerPin(): void {
    this.appliquerPin(null, 'efface');
  }

  private appliquerPin(pin: string | null, action: string): void {
    const cible = this.pinCible();
    if (!cible || this.enregistrement()) {
      return;
    }
    this.enregistrement.set(true);
    this.service.definirPin(cible.id, pin).subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.pinCible.set(null);
        this.succes.set(`Code PIN de ${cible.nomComplet} ${action}.`);
        this.charger();
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(this.messageErreur(reponse, 'Enregistrement du code impossible.'));
      }
    });
  }

  ouvrirReinitialisation(utilisateur: Utilisateur): void {
    this.reinitialise.set(utilisateur);
    this.nouveauMotDePasse = '';
    this.formulaireOuvert.set(false);
    this.messages();
  }

  reinitialiser(): void {
    const cible = this.reinitialise();
    if (!cible || this.enregistrement()) {
      return;
    }
    const manquants = messageChampsManquants({ 'Nouveau mot de passe': this.nouveauMotDePasse });
    if (manquants) {
      this.erreur.set(manquants);
      return;
    }
    this.messages();
    this.enregistrement.set(true);
    this.service.reinitialiserMotDePasse(cible.id, this.nouveauMotDePasse).subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.reinitialise.set(null);
        this.nouveauMotDePasse = '';
        this.succes.set(
          `Mot de passe de ${cible.email} reinitialise. Communiquez-le lui par un canal sur.`
        );
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(this.messageErreur(reponse, 'Reinitialisation impossible.'));
      }
    });
  }

  /** Vrai pour le compte de la personne connectee : certaines actions lui sont fermees. */
  estMoi(utilisateur: Utilisateur): boolean {
    return this.auth.utilisateur()?.id === utilisateur.id;
  }

  private messages(): void {
    this.erreur.set(null);
    this.succes.set(null);
  }

  /** L'API explique precisement le refus : c'est ce message qu'il faut montrer. */
  private messageErreur(reponse: unknown, defaut: string): string {
    const message = (reponse as { error?: { message?: string } })?.error?.message;
    return message ?? defaut;
  }
}
