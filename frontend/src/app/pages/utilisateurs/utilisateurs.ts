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

/**
 * Gestion des comptes (chapitre 40), reservee a l'administrateur.
 *
 * La liste et le formulaire cohabitent sur le meme ecran : creer un compte ou
 * corriger un profil est une operation courte, qui ne merite pas une page a
 * part ni un aller-retour de navigation.
 */
@Component({
  selector: 'app-utilisateurs',
  imports: [FormsModule, DatePipe, Modale],
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

  recherche = '';
  filtreRole: Role | '' = '';
  filtreActif: 'true' | 'false' | '' = '';
  private pageCourante = 0;

  /** Champs du formulaire de compte. */
  email = '';
  nom = '';
  prenom = '';
  telephone = '';
  motDePasse = '';
  rolesChoisis: Role[] = ['GESTIONNAIRE_PARC'];
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
        page: this.pageCourante
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
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
    this.pageCourante = 0;
    this.charger();
  }

  changerPage(delta: number): void {
    const page = this.page();
    if (!page) {
      return;
    }
    const cible = page.number + delta;
    if (cible < 0 || cible >= page.totalPages) {
      return;
    }
    this.pageCourante = cible;
    this.charger();
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
    this.rolesChoisis = ['GESTIONNAIRE_PARC'];
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
    this.rolesChoisis = [...utilisateur.roles];
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
