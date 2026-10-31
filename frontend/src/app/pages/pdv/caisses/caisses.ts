import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CaissePdv, MembrePersonnel, SessionCaisse } from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { Modale } from '../../../shared/modale';
import { FermetureSession, OuvertureSession } from '../../../shared/session-caisse';

/**
 * Caisses de l'etablissement, creees par le gerant et affectees a des
 * utilisateurs. Le gerant ouvre et ferme ici la session de chaque caisse
 * (billettage, comptage a l'aveugle) ; les entrees et sorties d'especes se
 * font depuis l'ecran d'encaissement. L'historique ouvre le detail des sessions.
 */
@Component({
  selector: 'app-caisses-pdv',
  imports: [DatePipe, FormsModule, Modale, OuvertureSession, FermetureSession],
  templateUrl: './caisses.html',
  styleUrl: './caisses.scss'
})
export class CaissesPdv implements OnInit {
  private readonly service = inject(PdvService);
  private readonly router = inject(Router);

  readonly caisses = signal<CaissePdv[]>([]);
  readonly personnel = signal<MembrePersonnel[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);
  readonly chargement = signal(true);

  readonly edition = signal<CaissePdv | 'nouvelle' | null>(null);
  formulaire = { nom: '', description: '', actif: true };
  readonly choisis = signal<Set<number>>(new Set());

  // Session : ouverture et fermeture
  readonly aOuvrir = signal<CaissePdv | null>(null);
  readonly aFermer = signal<SessionCaisse | null>(null);
  readonly succes = signal<string | null>(null);

  ngOnInit(): void {
    this.charger();
    this.service.personnel().subscribe({ next: (p) => this.personnel.set(p.filter((m) => m.actif)) });
  }

  /** Retire une caisse sans perdre son historique, ou la remet en service. */
  basculerActivation(caisse: CaissePdv): void {
    this.succes.set(null);
    this.erreur.set(null);
    this.service.modifierCaisse(caisse.id, {
      nom: caisse.nom, description: caisse.description, actif: !caisse.actif, utilisateurIds: caisse.utilisateurs.map((u) => u.id)
    })
      .subscribe({
        next: () => {
          this.succes.set(`${caisse.nom} ${caisse.actif ? 'desactivee' : 'reactivee'}.`);
          this.charger();
        },
        error: (e) => this.erreur.set(this.message(e, 'Changement impossible.'))
      });
  }

  ouvrirHistorique(caisse: CaissePdv): void {
    void this.router.navigate(['/pdv/caisses', caisse.id]);
  }

  ouvrirSession(caisse: CaissePdv, evenement: Event): void {
    evenement.stopPropagation();
    this.succes.set(null);
    this.aOuvrir.set(caisse);
  }

  sessionOuverte(session: SessionCaisse): void {
    this.aOuvrir.set(null);
    this.succes.set(`${session.caisseNom} ouverte avec un fond de ${session.fondInitial.toLocaleString('fr-FR')} F : le personnel peut encaisser.`);
    this.charger();
  }

  /** Fermeture : la session est relue en entier (totaux, mouvements, commandes en cours). */
  agirSurSession(caisse: CaissePdv, evenement: Event): void {
    evenement.stopPropagation();
    if (!caisse.session) {
      return;
    }
    this.succes.set(null);
    this.service.session(caisse.session.id).subscribe({
      next: (s) => this.aFermer.set(s),
      error: (e) => this.erreur.set(this.message(e, 'Lecture de la session impossible.'))
    });
  }

  nouvelle(): void {
    this.formulaire = { nom: '', description: '', actif: true };
    this.choisis.set(new Set());
    this.erreurFormulaire.set(null);
    this.edition.set('nouvelle');
  }

  modifier(caisse: CaissePdv, evenement: Event): void {
    evenement.stopPropagation();
    this.formulaire = { nom: caisse.nom, description: caisse.description ?? '', actif: caisse.actif };
    this.choisis.set(new Set(caisse.utilisateurs.map((u) => u.id)));
    this.erreurFormulaire.set(null);
    this.edition.set(caisse);
  }

  basculer(membre: MembrePersonnel): void {
    this.choisis.update((ids) => {
      const suivant = new Set(ids);
      if (suivant.has(membre.id)) {
        suivant.delete(membre.id);
      } else {
        suivant.add(membre.id);
      }
      return suivant;
    });
  }

  enregistrer(): void {
    const edition = this.edition();
    if (!edition || !this.formulaire.nom.trim()) {
      return;
    }
    const requete = {
      nom: this.formulaire.nom.trim(),
      description: this.formulaire.description.trim() || undefined,
      actif: this.formulaire.actif,
      utilisateurIds: [...this.choisis()]
    };
    const appel = edition === 'nouvelle'
      ? this.service.creerCaisse(requete)
      : this.service.modifierCaisse(edition.id, requete);
    appel.subscribe({
      next: () => {
        this.edition.set(null);
        this.charger();
      },
      error: (e) => this.erreurFormulaire.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  charger(): void {
    this.service.caisses().subscribe({
      next: (c) => {
        this.caisses.set(c);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Chargement des caisses impossible.'));
      }
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
