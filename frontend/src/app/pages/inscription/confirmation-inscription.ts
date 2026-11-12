import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PlateformeService } from '../../core/services/plateforme.service';
import { FondModules } from '../../shared/fond-modules';
import { environment } from '../../../environments/environment';

/**
 * Lien envoye par e-mail a l'inscription : confirme l'adresse, cree le compte
 * de l'entreprise et ouvre la session de son administrateur.
 */
@Component({
  selector: 'app-confirmation-inscription',
  imports: [RouterLink, FondModules],
  template: `
    <div class="page-connexion">
      <app-fond-modules />
      <div class="voile"></div>
      <div class="colonne-formulaire">
        <section class="carte-connexion" aria-live="polite">
          <div class="marque">
            <span class="logo" aria-hidden="true">◆</span>
            <h1>{{ nomPlateforme }}</h1>
            @if (erreur()) {
              <p class="sous-titre">Confirmation impossible</p>
            } @else {
              <p class="sous-titre">Creation de votre compte...</p>
            }
          </div>
          @if (erreur(); as e) {
            <div class="message erreur">{{ e }}</div>
            <p class="inscription"><a routerLink="/inscription">Recommencer l'inscription</a></p>
            <p class="inscription"><a routerLink="/connexion">Se connecter</a></p>
          }
        </section>
      </div>
    </div>
  `,
  styleUrls: ['../login/login.scss']
})
export class ConfirmationInscription implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly service = inject(PlateformeService);

  readonly nomPlateforme = environment.nomPlateforme;
  readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    const jeton = this.route.snapshot.queryParamMap.get('jeton') ?? '';
    if (!/^[0-9a-f]{64}$/.test(jeton)) {
      this.erreur.set('Ce lien est incomplet : ouvrez-le directement depuis l\'e-mail recu.');
      return;
    }
    this.service.confirmerInscription(jeton).subscribe({
      next: (reponse) => {
        this.auth.ouvrirSession(reponse);
        void this.router.navigateByUrl(this.auth.pageAccueil(), { replaceUrl: true });
      },
      error: (r) => this.erreur.set((r as { error?: { message?: string } })?.error?.message
        ?? 'Le serveur ne repond pas. Reessayez dans un instant.')
    });
  }
}
