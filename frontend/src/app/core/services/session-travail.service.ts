import { Injectable, computed, inject, signal } from '@angular/core';
import { SessionCaisse } from '../models/pdv';
import { PdvService } from './pdv.service';

const CLE_CHOIX = 'pdv.session-caisse';

/**
 * Session de caisse sur laquelle le poste encaisse. Un gerant ouvre les
 * sessions ; le personnel affecte a une caisse encaisse sur la sienne. Quand
 * plusieurs sont ouvertes a l'utilisateur, son choix est retenu par le
 * navigateur et accompagne chaque encaissement.
 */
@Injectable({ providedIn: 'root' })
export class SessionTravailService {
  private readonly pdv = inject(PdvService);

  /** Sessions ouvertes a l'utilisateur ; indefini tant qu'elles ne sont pas lues. */
  readonly ouvertes = signal<SessionCaisse[] | undefined>(undefined);
  private readonly choix = signal<number | null>(this.lireChoix());

  /** Session retenue : le choix du poste s'il est encore ouvert, sinon la seule ouverte ; null si aucune. */
  readonly courante = computed<SessionCaisse | null | undefined>(() => {
    const ouvertes = this.ouvertes();
    if (ouvertes === undefined) {
      return undefined;
    }
    return ouvertes.find((s) => s.id === this.choix()) ?? (ouvertes.length === 1 ? ouvertes[0] : null);
  });

  /** Plusieurs sessions ouvertes et aucune choisie : le poste doit preciser. */
  readonly choixRequis = computed(() => (this.ouvertes()?.length ?? 0) > 1 && !this.courante());

  rafraichir(): void {
    this.pdv.sessionsOuvertes().subscribe({
      next: (s) => this.ouvertes.set(s),
      error: () => this.ouvertes.set(this.ouvertes() ?? [])
    });
  }

  choisir(id: number | null): void {
    this.choix.set(id);
    try {
      if (id) {
        localStorage.setItem(CLE_CHOIX, String(id));
      } else {
        localStorage.removeItem(CLE_CHOIX);
      }
    } catch {
      // Stockage indisponible : le choix vaut pour cette page seulement.
    }
  }

  /** Session mise a jour (vente, mouvement) : la liste suit sans relire le serveur. */
  remplacer(session: SessionCaisse): void {
    this.ouvertes.update((liste) =>
      session.statut === 'OUVERTE'
        ? [...(liste ?? []).filter((s) => s.id !== session.id), session].sort((a, b) => a.caisseNom.localeCompare(b.caisseNom))
        : (liste ?? []).filter((s) => s.id !== session.id)
    );
  }

  private lireChoix(): number | null {
    try {
      return Number(localStorage.getItem(CLE_CHOIX)) || null;
    } catch {
      return null;
    }
  }
}
