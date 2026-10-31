import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Page } from '../../../core/models/models';
import { CaissePdv, SessionCaisse } from '../../../core/models/pdv';
import { AuthService } from '../../../core/auth/auth.service';
import { PdvService } from '../../../core/services/pdv.service';


/** Historique d'une caisse : ses sessions ; un clic ouvre le detail (ventes, mouvements d'especes, billettage, rapport PDF). */
@Component({
  selector: 'app-sessions-pdv',
  imports: [DecimalPipe, DatePipe, RouterLink],
  templateUrl: './sessions.html',
  styles: [`
    .retour { display: inline-block; margin-bottom: 0.25rem; color: var(--couleur-primaire); font-weight: 600; text-decoration: none; }
    .ouverte { color: var(--couleur-succes); }
    .titre-mouvements { margin: 1rem 0 0.4rem; font-size: 0.9rem; }
    .mouvements-session { margin: 0; padding-left: 1.1rem; font-size: 0.88rem; }
    .mouvements-session small { color: var(--couleur-texte-doux); }
  `]
})
export class SessionsPdv implements OnInit {
  private readonly service = inject(PdvService);
  /** En restauration, la vente au comptoir et le service en salle s'ouvrent d'ici. */
  readonly restauration = inject(AuthService).estRestauration();

  private readonly route = inject(ActivatedRoute);

  readonly caisse = signal<CaissePdv | null>(null);
  readonly page = signal<Page<SessionCaisse> | null>(null);
  readonly erreur = signal<string | null>(null);
  private numeroPage = 0;

  private caisseId = 0;

  ngOnInit(): void {
    this.caisseId = Number(this.route.snapshot.paramMap.get('id'));
    this.service.caisse(this.caisseId).subscribe({
      next: (c) => this.caisse.set(c),
      error: () => this.erreur.set('Caisse introuvable.')
    });
    this.charger();
  }

  charger(): void {
    this.service.sessions(this.numeroPage, this.caisseId).subscribe({
      next: (p) => this.page.set(p),
      error: () => this.erreur.set('Chargement des caisses impossible.')
    });
  }

  changerPage(sens: number): void {
    this.numeroPage += sens;
    this.charger();
  }
}
