import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { SocieteService } from '../../core/services/societe.service';
import { VALIDATION_FORMULAIRE } from '../../shared/validation-formulaire';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, VALIDATION_FORMULAIRE],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly societeService = inject(SocieteService);

  /** Nom et logo parametres ; la page reste utilisable si l'API ne repond pas. */
  readonly societe = this.societeService.identite;

  readonly enCours = signal(false);
  readonly erreur = signal<string | null>(null);
  /** Renseigne lorsque l'utilisateur a ete redirige apres expiration de sa session. */
  readonly information = signal<string | null>(
    this.route.snapshot.queryParamMap.get('motif') === 'expiration'
      ? 'Votre session a expire. Reconnectez-vous pour continuer.'
      : null
  );

  readonly formulaire = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required]]
  });

  ngOnInit(): void {
    this.societeService.chargerIdentite().subscribe({ error: () => undefined });
  }

  soumettre(): void {
    if (this.formulaire.invalid || this.enCours()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);

    const { email, motDePasse } = this.formulaire.getRawValue();
    this.auth.login(email, motDePasse).subscribe({
      next: () => {
        const retour = this.route.snapshot.queryParamMap.get('retour') ?? '/tableau-bord';
        void this.router.navigateByUrl(retour);
      },
      error: (erreur) => {
        this.enCours.set(false);
        this.erreur.set(
          erreur.status === 401
            ? 'Email ou mot de passe incorrect.'
            : "Connexion au serveur impossible. Verifiez que l'API est demarree."
        );
      }
    });
  }
}
