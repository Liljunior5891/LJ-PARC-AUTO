import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { HistoriquePresence, ResultatPointage } from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';
import { ClavierPin } from '../clavier-pin';
import { HistoriqueEmploye } from '../historique-employe';

const CLE_JETON = 'lj-borne-presence';
/** Retour automatique a l'ecran du code QR apres un pointage ou un historique. */
const RETOUR_RESULTAT = 6000;
const RETOUR_HISTORIQUE = 30000;

type Ecran = 'appairage' | 'qr' | 'pin' | 'resultat' | 'historique';

/**
 * Borne de pointage posee a l'entree : elle affiche le code QR du lieu, qui
 * change toutes les 30 secondes, et un clavier PIN pour pointer sans
 * telephone. Appairee une fois avec le code donne par le gerant.
 */
@Component({
  selector: 'app-borne-presence',
  imports: [DatePipe, ClavierPin, HistoriqueEmploye],
  templateUrl: './borne.html',
  styleUrl: './borne.scss'
})
export class BornePresencePage implements OnInit {
  private readonly service = inject(PresenceService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);
  private readonly clavier = viewChild(ClavierPin);

  private jeton: string | null = null;
  private minuterieQr: ReturnType<typeof setTimeout> | null = null;
  private minuterieRetour: ReturnType<typeof setTimeout> | null = null;
  private horloge: ReturnType<typeof setInterval> | null = null;

  readonly ecran = signal<Ecran>('qr');
  /** Saisie du PIN pour pointer, ou pour voir son historique. */
  readonly modePin = signal<'pointer' | 'historique'>('pointer');
  readonly lieu = signal('');
  readonly entreprise = signal('');
  readonly qr = signal<SafeHtml | null>(null);
  readonly secondes = signal(30);
  readonly maintenant = signal(new Date());
  readonly erreur = signal<string | null>(null);
  readonly enCours = signal(false);
  readonly resultat = signal<ResultatPointage | null>(null);
  readonly historique = signal<HistoriquePresence | null>(null);

  ngOnInit(): void {
    this.jeton = this.lire();
    this.horloge = setInterval(() => {
      this.maintenant.set(new Date());
      this.secondes.update((s) => Math.max(0, s - 1));
    }, 1000);
    this.destroyRef.onDestroy(() => {
      this.arreterQr();
      this.annulerRetour();
      if (this.horloge) {
        clearInterval(this.horloge);
      }
    });
    if (this.jeton) {
      this.afficherQr();
    } else {
      this.ecran.set('appairage');
    }
  }

  // ------------------------------------------------------------ appairage

  codeSaisi(code: string): void {
    this.erreur.set(null);
    this.enCours.set(true);
    this.service.appairer(code).subscribe({
      next: (a) => {
        this.enCours.set(false);
        this.jeton = a.jeton;
        try {
          localStorage.setItem(CLE_JETON, a.jeton);
        } catch {
          // Stockage indisponible : la borne restera appairee jusqu'a la fermeture de la page.
        }
        this.afficherQr();
      },
      error: (e) => {
        this.enCours.set(false);
        this.clavier()?.vider();
        this.erreur.set(this.message(e, 'Code refuse.'));
      }
    });
  }

  // ------------------------------------------------------------ code QR

  afficherQr(): void {
    this.annulerRetour();
    this.erreur.set(null);
    this.ecran.set('qr');
    this.rafraichirQr();
  }

  private rafraichirQr(): void {
    this.arreterQr();
    if (!this.jeton) {
      return;
    }
    this.service.qrBorne(this.jeton, window.location.origin).subscribe({
      next: (q) => {
        this.lieu.set(q.lieu);
        this.entreprise.set(q.entreprise);
        this.qr.set(this.sanitizer.bypassSecurityTrustHtml(q.svg));
        this.secondes.set(q.secondesRestantes);
        // Le code suivant est demande juste apres le changement.
        this.minuterieQr = setTimeout(() => this.rafraichirQr(), (q.secondesRestantes + 1) * 1000);
      },
      error: (e) => {
        if (e?.status === 404) {
          this.oublier();
          return;
        }
        this.erreur.set('Connexion perdue : nouvel essai dans quelques secondes.');
        this.minuterieQr = setTimeout(() => this.rafraichirQr(), 5000);
      }
    });
  }

  private arreterQr(): void {
    if (this.minuterieQr) {
      clearTimeout(this.minuterieQr);
      this.minuterieQr = null;
    }
  }

  // ------------------------------------------------------------ pointage sur la borne

  ouvrirPin(mode: 'pointer' | 'historique'): void {
    this.modePin.set(mode);
    this.erreur.set(null);
    this.ecran.set('pin');
    this.retourDans(RETOUR_HISTORIQUE);
  }

  pinSaisi(pin: string): void {
    if (!this.jeton) {
      return;
    }
    this.erreur.set(null);
    this.enCours.set(true);
    if (this.modePin() === 'historique') {
      this.service.historiqueBorne(this.jeton, pin).subscribe({
        next: (h) => {
          this.enCours.set(false);
          this.historique.set(h);
          this.ecran.set('historique');
          this.retourDans(RETOUR_HISTORIQUE);
        },
        error: (e) => this.echec(e, 'Historique indisponible.')
      });
      return;
    }
    this.service.pointerBorne(this.jeton, pin).subscribe({
      next: (r) => {
        this.enCours.set(false);
        this.resultat.set(r);
        this.ecran.set('resultat');
        this.retourDans(RETOUR_RESULTAT);
      },
      error: (e) => this.echec(e, 'Pointage impossible.')
    });
  }

  private echec(e: unknown, defaut: string): void {
    this.enCours.set(false);
    this.clavier()?.vider();
    this.erreur.set(this.message(e, defaut));
    this.retourDans(RETOUR_HISTORIQUE);
  }

  private retourDans(ms: number): void {
    this.annulerRetour();
    this.minuterieRetour = setTimeout(() => this.afficherQr(), ms);
  }

  private annulerRetour(): void {
    if (this.minuterieRetour) {
      clearTimeout(this.minuterieRetour);
      this.minuterieRetour = null;
    }
  }

  /** La borne n'est plus reconnue (re-appairee ou desactivee) : retour a l'appairage. */
  private oublier(): void {
    this.jeton = null;
    try {
      localStorage.removeItem(CLE_JETON);
    } catch {
      // rien a effacer
    }
    this.arreterQr();
    this.erreur.set('Cette borne n\'est plus appairee : saisissez un nouveau code donne par le gerant.');
    this.ecran.set('appairage');
  }

  private lire(): string | null {
    try {
      return localStorage.getItem(CLE_JETON);
    } catch {
      return null;
    }
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
