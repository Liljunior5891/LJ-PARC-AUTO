import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/models';
import { LONGUEUR_MOT_DE_PASSE, ProfilRole } from '../../core/models/utilisateur';
import { UtilisateurService } from '../../core/services/utilisateur.service';

/**
 * Compte de la personne connectee.
 *
 * Changer son mot de passe ne doit dependre de personne : c'est la seule
 * operation de l'ecran, ouverte a tous les profils.
 */
@Component({
  selector: 'app-mon-compte',
  imports: [FormsModule],
  templateUrl: './mon-compte.html',
  styleUrl: './mon-compte.scss'
})
export class MonCompte implements OnInit {
  private readonly service = inject(UtilisateurService);
  private readonly auth = inject(AuthService);

  readonly longueurMinimale = LONGUEUR_MOT_DE_PASSE;
  readonly utilisateur = this.auth.utilisateur;
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly profils = signal<ProfilRole[]>([]);

  actuel = '';
  nouveau = '';
  confirmation = '';

  ngOnInit(): void {
    // Le profil courant ne connait que le code du role : le libelle vient du catalogue.
    this.service.profils().subscribe({ next: (profils) => this.profils.set(profils) });
  }

  libelleRole(role: Role): string {
    return this.profils().find((p) => p.valeur === role)?.libelle ?? role;
  }

  changer(): void {
    if (this.enregistrement()) {
      return;
    }
    this.erreur.set(null);
    this.succes.set(null);

    if (this.nouveau.length < this.longueurMinimale) {
      this.erreur.set(`Le nouveau mot de passe doit faire ${this.longueurMinimale} caracteres au minimum.`);
      return;
    }
    // Verifie ici plutot qu'au serveur : la faute de frappe se corrige sur place.
    if (this.nouveau !== this.confirmation) {
      this.erreur.set('La confirmation ne correspond pas au nouveau mot de passe.');
      return;
    }

    this.enregistrement.set(true);
    this.service
      .changerSonMotDePasse({ motDePasseActuel: this.actuel, nouveauMotDePasse: this.nouveau })
      .subscribe({
        next: () => {
          this.enregistrement.set(false);
          this.actuel = '';
          this.nouveau = '';
          this.confirmation = '';
          this.succes.set(
            'Mot de passe change. Il sera demande a votre prochaine connexion.'
          );
        },
        error: (reponse) => {
          this.enregistrement.set(false);
          const message = (reponse as { error?: { message?: string } })?.error?.message;
          this.erreur.set(message ?? 'Changement du mot de passe impossible.');
        }
      });
  }
}
