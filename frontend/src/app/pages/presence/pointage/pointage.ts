import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HistoriquePresence, ResultatPointage, dureeMinutes } from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';
import { ClavierPin } from '../clavier-pin';
import { HistoriqueEmploye } from '../historique-employe';

type Etape = 'chargement' | 'pin' | 'envoi' | 'resultat' | 'historique' | 'erreur';

/**
 * Pointage depuis le telephone de l'employe, ouvert par le code QR de la
 * borne : position GPS, puis PIN. L'employe peut aussi consulter son
 * historique avec son PIN. Sans compte ni menu.
 */
@Component({
  selector: 'app-pointage',
  imports: [DatePipe, DecimalPipe, ClavierPin, HistoriqueEmploye],
  templateUrl: './pointage.html',
  styleUrl: './pointage.scss'
})
export class PointagePage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(PresenceService);
  private readonly clavier = viewChild(ClavierPin);

  private jeton = '';
  private code: string | null = null;

  readonly etape = signal<Etape>('chargement');
  /** Pointer son arrivee, ou consulter son historique. */
  readonly mode = signal<'pointer' | 'historique'>('pointer');
  readonly lieu = signal('');
  readonly entreprise = signal('');
  readonly codeValide = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly position = signal<GeolocationPosition | null>(null);
  readonly etatPosition = signal<'attente' | 'ok' | 'refusee' | 'indisponible'>('attente');
  readonly resultat = signal<ResultatPointage | null>(null);
  readonly historique = signal<HistoriquePresence | null>(null);
  readonly maintenant = new Date();
  readonly duree = dureeMinutes;

  ngOnInit(): void {
    this.jeton = this.route.snapshot.paramMap.get('jeton') ?? '';
    this.code = this.route.snapshot.queryParamMap.get('c');
    this.service.accueil(this.jeton, this.code).subscribe({
      next: (a) => {
        this.lieu.set(a.lieu);
        this.entreprise.set(a.entreprise);
        this.codeValide.set(a.codeValide);
        this.etape.set('pin');
      },
      error: (e) => {
        this.erreur.set(this.message(e, 'Ce code QR n\'est pas reconnu.'));
        this.etape.set('erreur');
      }
    });
    this.localiser();
  }

  /** Demande la position du telephone : le pointage n'est accepte que pres du lieu. */
  localiser(): void {
    if (!('geolocation' in navigator)) {
      this.etatPosition.set('indisponible');
      return;
    }
    this.etatPosition.set('attente');
    navigator.geolocation.getCurrentPosition(
      (p) => {
        this.position.set(p);
        this.etatPosition.set('ok');
      },
      (e) => this.etatPosition.set(e.code === e.PERMISSION_DENIED ? 'refusee' : 'indisponible'),
      { enableHighAccuracy: true, timeout: 20000, maximumAge: 30000 }
    );
  }

  changerMode(mode: 'pointer' | 'historique'): void {
    this.mode.set(mode);
    this.erreur.set(null);
    this.clavier()?.vider();
    this.etape.set('pin');
  }

  pinSaisi(pin: string): void {
    this.erreur.set(null);
    this.etape.set('envoi');
    if (this.mode() === 'historique') {
      this.service.monHistorique(this.jeton, pin, this.code).subscribe({
        next: (h) => {
          this.historique.set(h);
          this.etape.set('historique');
        },
        error: (e) => this.echec(e, 'Historique indisponible.')
      });
      return;
    }
    const p = this.position();
    if (!p) {
      this.echec(null, this.etatPosition() === 'refusee'
        ? 'Autorisez la localisation de votre telephone pour pointer, puis reessayez.'
        : 'Position introuvable : activez la localisation et reessayez.');
      this.localiser();
      return;
    }
    this.service.pointer(this.jeton, {
      code: this.code, pin,
      latitude: p.coords.latitude, longitude: p.coords.longitude, precision: p.coords.accuracy
    }).subscribe({
      next: (r) => {
        this.resultat.set(r);
        this.etape.set('resultat');
      },
      error: (e) => this.echec(e, 'Pointage impossible.')
    });
  }

  private echec(e: unknown, defaut: string): void {
    this.erreur.set(this.message(e, defaut));
    this.clavier()?.vider();
    this.etape.set('pin');
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
