import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { CommandeResume, HAUTEUR_PLAN, LARGEUR_PLAN, PlanSalles, TablePlan } from '../../../core/models/restauration';
import { RestaurationService } from '../../../core/services/restauration.service';
import { Modale } from '../../../shared/modale';
import { SessionTravailService } from '../../../core/services/session-travail.service';
import { BandeauSession } from '../../../shared/bandeau-session';

/** Etat d'une table sur le plan, du plus calme au plus urgent. */
export type EtatTable = 'libre' | 'occupee' | 'a-envoyer' | 'prete' | 'addition' | 'demande';

/** Intervalle de rafraichissement du plan : la cuisine et les collegues le font bouger. */
const RAFRAICHISSEMENT_MS = 8000;

/**
 * Plan de service : les tables de chaque salle a leur place, colorees selon
 * leur etat. Toucher une table libre l'ouvre ; toucher une table occupee
 * ouvre sa commande. Les commandes au comptoir ou a emporter, sans table,
 * sont listees a cote.
 */
@Component({
  selector: 'app-salle-pdv',
  imports: [DecimalPipe, FormsModule, RouterLink, Modale, BandeauSession],
  templateUrl: './salle.html',
  styleUrl: './salle.scss'
})
export class SallePdv implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sessions = inject(SessionTravailService);
  /** Aucune caisse ouverte pour l'utilisateur : aucune table ne s'ouvre (le serveur le refuse aussi). */
  readonly salleFermee = computed(() => this.sessions.ouvertes()?.length === 0);

  readonly estGestionnaire = this.auth.aRole('ADMIN', 'GERANT');
  readonly largeurPlan = LARGEUR_PLAN;
  readonly hauteurPlan = HAUTEUR_PLAN;

  readonly plan = signal<PlanSalles | null>(null);
  readonly salleChoisie = signal<number | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly maintenant = signal(Date.now());

  // Ouverture d'une table ou d'une commande au comptoir
  readonly ouverture = signal<{ table?: TablePlan } | null>(null);
  couverts: number | null = null;
  libelle = '';
  readonly enCours = signal(false);

  readonly salle = computed(() => {
    const plan = this.plan();
    if (!plan || plan.salles.length === 0) {
      return null;
    }
    return plan.salles.find((s) => s.salle.id === this.salleChoisie()) ?? plan.salles[0];
  });

  readonly compteurs = computed(() => {
    const tables = this.plan()?.salles.flatMap((s) => s.tables) ?? [];
    return {
      libres: tables.filter((t) => !t.commande).length,
      occupees: tables.filter((t) => t.commande).length,
      pretes: tables.filter((t) => (t.commande?.pretes ?? 0) > 0).length
        + (this.plan()?.sansTable.filter((c) => c.pretes > 0).length ?? 0),
      demandes: tables.filter((t) => t.commande && (t.commande.aValider > 0 || t.commande.appel)).length
    };
  });

  ngOnInit(): void {
    this.charger();
    const minuterie = setInterval(() => {
      this.maintenant.set(Date.now());
      this.charger();
    }, RAFRAICHISSEMENT_MS);
    this.destroyRef.onDestroy(() => clearInterval(minuterie));
  }

  libres(tables: TablePlan[]): number {
    return tables.filter((t) => !t.commande).length;
  }

  etat(commande?: CommandeResume | null): EtatTable {
    if (!commande) {
      return 'libre';
    }
    // Le client attend une reponse depuis son telephone : c'est le plus urgent.
    if (commande.aValider > 0 || commande.appel) {
      return 'demande';
    }
    if (commande.pretes > 0) {
      return 'prete';
    }
    if (commande.additionLe) {
      return 'addition';
    }
    if (commande.aEnvoyer > 0) {
      return 'a-envoyer';
    }
    return 'occupee';
  }

  /** Ce que le client demande depuis le code QR de sa table. */
  demande(commande: CommandeResume): string {
    if (commande.appel === 'ADDITION') {
      return '🧾 Addition';
    }
    if (commande.appel === 'SERVEUR') {
      return '🙋 Appel';
    }
    return `📱 ${commande.aValider} a valider`;
  }

  /** Temps passe a table, en minutes ou en heures. */
  duree(commande: CommandeResume): string {
    const minutes = Math.max(0, Math.floor((this.maintenant() - new Date(commande.ouverteLe).getTime()) / 60000));
    return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`;
  }

  toucher(t: TablePlan): void {
    if (t.commande) {
      void this.router.navigate(['/pdv/commandes', t.commande.id]);
      return;
    }
    if (this.refuserSiFermee()) {
      return;
    }
    this.couverts = null;
    this.erreur.set(null);
    this.ouverture.set({ table: t });
  }

  ouvrirComptoir(): void {
    if (this.refuserSiFermee()) {
      return;
    }
    this.libelle = '';
    this.erreur.set(null);
    this.ouverture.set({});
  }

  confirmerOuverture(): void {
    const ouverture = this.ouverture();
    if (!ouverture || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.service
      .ouvrir({
        tableId: ouverture.table?.table.id,
        libelle: ouverture.table ? undefined : this.libelle,
        couverts: this.couverts || null
      })
      .subscribe({
        next: (commande) => {
          this.enCours.set(false);
          this.ouverture.set(null);
          void this.router.navigate(['/pdv/commandes', commande.resume.id]);
        },
        error: (e) => {
          this.enCours.set(false);
          this.erreur.set(this.message(e, 'Ouverture impossible.'));
          this.charger();
        }
      });
  }

  private refuserSiFermee(): boolean {
    if (this.salleFermee()) {
      this.erreur.set('Caisse fermee : un gerant doit ouvrir la session avant toute commande.');
      return true;
    }
    return false;
  }

  private charger(): void {
    this.service.plan().subscribe({
      next: (plan) => {
        this.plan.set(plan);
        this.erreur.set(null);
      },
      error: (e) => {
        if (!this.plan()) {
          this.erreur.set(this.message(e, 'Chargement du plan impossible.'));
        }
      }
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
