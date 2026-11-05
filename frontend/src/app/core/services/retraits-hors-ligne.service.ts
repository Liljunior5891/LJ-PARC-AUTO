import { Injectable, inject, signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { BonRetrait, LigneServie, RemiseDifferee, ResultatSynchro } from '../models/lieux';
import { LieuxService } from './lieux.service';

const INTERVALLE_ESSAI_MS = 20_000;

/**
 * Service des bons sans connexion. L'appareil garde une copie des bons de ses
 * lieux et une file des remises faites hors ligne, envoyees au retour du reseau.
 * <p>
 * Sans connexion, deux appareils ne peuvent pas savoir ce que l'autre a servi : un
 * bon peut donc etre servi deux fois. Le serveur le detecte a l'envoi et signale un
 * conflit au gerant. Sur un meme appareil, la copie locale diminue a chaque remise.
 */
@Injectable({ providedIn: 'root' })
export class RetraitsHorsLigneService {
  private readonly lieux = inject(LieuxService);
  private readonly auth = inject(AuthService);

  readonly enLigne = signal(navigator.onLine);
  readonly bons = signal<BonRetrait[]>([]);
  readonly enAttente = signal<RemiseDifferee[]>([]);
  readonly dernierEnvoi = signal<ResultatSynchro[]>([]);
  readonly copieDu = signal<string | null>(null);
  private synchronisation = false;
  private demarre = false;

  /** Prepare l'appareil : ecoute le reseau et rend l'application rouvrable sans connexion. */
  demarrer(): void {
    if (this.demarre) {
      this.rafraichir();
      return;
    }
    this.demarre = true;
    this.relire();
    window.addEventListener('online', () => {
      this.enLigne.set(true);
      this.envoyer();
    });
    window.addEventListener('offline', () => this.enLigne.set(false));
    // Le reseau de l'appareil peut marcher alors que le serveur ne repond pas : on reessaie regulierement.
    setInterval(() => this.envoyer(), INTERVALLE_ESSAI_MS);
    if ('serviceWorker' in navigator && window.isSecureContext) {
      void navigator.serviceWorker.register('/sw.js').catch(() => undefined);
    }
    this.envoyer();
  }

  /** Le reseau repond : la copie locale reprend l'etat du serveur, moins ce qui n'est pas encore envoye. */
  rafraichir(): void {
    if (!navigator.onLine) {
      return;
    }
    this.lieux.retraitsHorsLigne().subscribe({
      next: (bons) => {
        this.ecrire(this.cleBons(), bons);
        localStorage.setItem(this.cleDate(), new Date().toISOString());
        this.copieDu.set(localStorage.getItem(this.cleDate()));
        this.enLigne.set(true);
        this.bons.set(this.appliquerAttente(bons));
      },
      error: (e) => {
        if (e?.status === 0) {
          this.enLigne.set(false);
        }
      }
    });
  }

  /** Une erreur reseau pendant une remise : elle part dans la file plutot que de se perdre. */
  mettreEnAttente(bon: BonRetrait, lignes: LigneServie[], recuPar: string | null, note: string | null): void {
    const remise: RemiseDifferee = {
      cle: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      bonId: bon.id,
      faiteLe: new Date().toISOString(),
      lignes,
      recuPar,
      note
    };
    const file = [...this.enAttente(), remise];
    this.enAttente.set(file);
    this.ecrire(this.cleFile(), file);
    this.bons.set(this.appliquerAttente(this.lire<BonRetrait[]>(this.cleBons()) ?? []));
    this.enLigne.set(false);
  }

  /** Envoie la file. Une remise n'en sort que si le serveur a repondu pour elle. */
  envoyer(): void {
    const file = this.enAttente();
    if (!navigator.onLine || this.synchronisation) {
      return;
    }
    if (file.length === 0) {
      this.rafraichir();
      return;
    }
    this.synchronisation = true;
    this.lieux.synchroniser(file).subscribe({
      next: (resultats) => {
        this.synchronisation = false;
        const traitees = new Set(resultats.map((r) => r.cle));
        const reste = this.enAttente().filter((r) => !traitees.has(r.cle));
        this.enAttente.set(reste);
        this.ecrire(this.cleFile(), reste);
        this.dernierEnvoi.set(resultats);
        this.rafraichir();
      },
      error: () => {
        this.synchronisation = false;
      }
    });
  }

  private relire(): void {
    this.enAttente.set(this.lire<RemiseDifferee[]>(this.cleFile()) ?? []);
    this.copieDu.set(localStorage.getItem(this.cleDate()));
    this.bons.set(this.appliquerAttente(this.lire<BonRetrait[]>(this.cleBons()) ?? []));
  }

  private appliquerAttente(bons: BonRetrait[]): BonRetrait[] {
    const file = this.enAttente();
    return bons
      .map((b) => {
        const remises = file.filter((r) => r.bonId === b.id).flatMap((r) => r.lignes);
        return {
          ...b,
          lignes: b.lignes.map((l) => {
            const servi = remises.filter((r) => r.ligneId === l.ligneId).reduce((s, r) => s + r.quantite, 0);
            return { ...l, servi: l.servi + servi, reste: Math.max(0, l.reste - servi) };
          })
        };
      })
      .filter((b) => b.lignes.some((l) => l.reste > 0));
  }

  private cle(nom: string): string {
    return `pdv.retraits.${this.auth.utilisateur()?.email ?? 'poste'}.${nom}`;
  }

  private cleBons(): string { return this.cle('bons'); }
  private cleFile(): string { return this.cle('file'); }
  private cleDate(): string { return this.cle('date'); }

  private lire<T>(cle: string): T | null {
    try {
      const brut = localStorage.getItem(cle);
      return brut ? (JSON.parse(brut) as T) : null;
    } catch {
      return null;
    }
  }

  private ecrire(cle: string, valeur: unknown): void {
    try {
      localStorage.setItem(cle, JSON.stringify(valeur));
    } catch {
      // Stockage plein ou bloque : le service reste possible tant que le reseau repond.
    }
  }
}
