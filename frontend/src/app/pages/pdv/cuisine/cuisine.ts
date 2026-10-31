import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PostePreparation } from '../../../core/models/pdv';
import { Bon, EcranPreparation, LigneBon } from '../../../core/models/restauration';
import { RestaurationService } from '../../../core/services/restauration.service';

const RAFRAICHISSEMENT_MS = 5000;
const CLE_POSTE = 'pdv.cuisine.poste';
const CLE_ECRAN = 'pdv.cuisine.ecran';
const CLE_SON = 'pdv.cuisine.son';

/** Au-dela de ces delais d'attente, le bon passe a l'orange puis au rouge. */
const MINUTES_RETARD = 10;
const MINUTES_URGENT = 20;

/**
 * Ecran de preparation, a poser sur un ecran ou une tablette au poste de
 * preparation. Il affiche les produits des categories de l'ecran choisi parmi
 * ceux affectes a l'utilisateur ; un etablissement qui n'a pas encore cree
 * d'ecran garde les deux postes cuisine et bar.
 * <p>
 * Les bons arrivent dans l'ordre d'envoi. Un geste sur le bon le fait avancer
 * (commencer, puis pret) ; un geste sur une ligne ne fait avancer qu'elle. Un
 * signal sonore annonce chaque nouveau bon.
 */
@Component({
  selector: 'app-cuisine-pdv',
  imports: [DecimalPipe],
  templateUrl: './cuisine.html',
  styleUrl: './cuisine.scss'
})
export class CuisinePdv implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  /** Ecrans ouverts a l'utilisateur ; nul tant qu'ils ne sont pas lus. */
  readonly ecrans = signal<EcranPreparation[] | null>(null);
  readonly ecranChoisi = signal<number | null>(null);
  readonly ecranCourant = computed(() => this.ecrans()?.find((e) => e.id === this.ecranChoisi()) ?? null);

  readonly poste = signal<PostePreparation>(this.lire(CLE_POSTE) === 'BAR' ? 'BAR' : 'CUISINE');
  readonly son = signal(this.lire(CLE_SON) !== 'non');
  readonly bons = signal<Bon[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly maintenant = signal(Date.now());
  readonly chargement = signal(true);

  readonly aPreparer = computed(() => this.bons().filter((b) => b.statut === 'A_PREPARER'));
  readonly enPreparation = computed(() => this.bons().filter((b) => b.statut === 'EN_PREPARATION'));
  readonly prets = computed(() => this.bons().filter((b) => b.statut === 'PRETE'));

  /** Bons deja vus : un bon absent de cet ensemble declenche le signal sonore. */
  private connus = new Set<string>();
  private audio: AudioContext | null = null;

  ngOnInit(): void {
    this.service.ecransAccessibles().subscribe({
      next: (ecrans) => {
        this.ecrans.set(ecrans);
        if (ecrans.length > 0) {
          const demande = Number(this.route.snapshot.queryParamMap.get('ecran')) || Number(this.lire(CLE_ECRAN));
          this.ecranChoisi.set(ecrans.some((e) => e.id === demande) ? demande : ecrans[0].id);
        }
        this.charger();
      },
      error: () => {
        this.ecrans.set([]);
        this.charger();
      }
    });
    const minuterie = setInterval(() => {
      this.maintenant.set(Date.now());
      this.charger();
    }, RAFRAICHISSEMENT_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(minuterie);
      void this.audio?.close();
    });
  }

  titreEcran(e: EcranPreparation): string {
    return e.categories.map((c) => c.nom).join(', ');
  }

  choisirEcran(id: number): void {
    this.ecranChoisi.set(id);
    this.ecrire(CLE_ECRAN, String(id));
    this.connus.clear();
    this.bons.set([]);
    this.chargement.set(true);
    this.charger();
  }

  choisirPoste(poste: PostePreparation): void {
    this.poste.set(poste);
    this.ecrire(CLE_POSTE, poste);
    this.connus.clear();
    this.bons.set([]);
    this.chargement.set(true);
    this.charger();
  }

  basculerSon(): void {
    this.son.update((actif) => !actif);
    this.ecrire(CLE_SON, this.son() ? 'oui' : 'non');
    if (this.son()) {
      this.bip();
    }
  }

  pleinEcran(): void {
    if (document.fullscreenElement) {
      void document.exitFullscreen();
    } else {
      void document.documentElement.requestFullscreen?.();
    }
  }

  attente(bon: Bon): number {
    return Math.max(0, Math.floor((this.maintenant() - new Date(bon.envoyeLe).getTime()) / 60000));
  }

  urgence(bon: Bon): string {
    if (bon.statut === 'PRETE') {
      return '';
    }
    const minutes = this.attente(bon);
    return minutes >= MINUTES_URGENT ? 'urgent' : minutes >= MINUTES_RETARD ? 'retard' : '';
  }

  avancer(bon: Bon): void {
    this.service.avancerBon(bon.commandeId, bon.envoi, this.source()).subscribe({
      next: (maj) => this.remplacer(bon, maj),
      error: (e) => this.signaler(e)
    });
  }

  avancerLigne(bon: Bon, ligne: LigneBon, event: Event): void {
    event.stopPropagation();
    const appel = ligne.statut === 'PRETE' ? this.service.reculerLigne(ligne.id) : this.service.avancerLigne(ligne.id);
    appel.subscribe({
      next: () => this.charger(),
      error: (e) => this.signaler(e)
    });
  }

  libelleAction(bon: Bon): string {
    return bon.statut === 'A_PREPARER' ? 'Commencer' : bon.statut === 'EN_PREPARATION' ? 'Pret !' : 'Servi bientot';
  }

  private remplacer(ancien: Bon, nouveau: Bon): void {
    this.bons.update((bons) =>
      bons.map((b) => (b.commandeId === ancien.commandeId && b.envoi === ancien.envoi ? nouveau : b))
    );
  }

  /** Ecran choisi, ou a defaut le poste cuisine / bar d'un etablissement sans ecran. */
  private source(): { ecranId?: number; poste?: PostePreparation } {
    const ecran = this.ecranChoisi();
    return ecran ? { ecranId: ecran } : { poste: this.poste() };
  }

  private charger(): void {
    if (this.ecrans() === null) {
      return;
    }
    const source = this.source();
    const cle = JSON.stringify(source);
    this.service.bons(source).subscribe({
      next: (bons) => {
        if (cle !== JSON.stringify(this.source())) {
          return;
        }
        const nouveaux = bons.filter((b) => b.statut === 'A_PREPARER' && !this.connus.has(this.cle(b)));
        if (nouveaux.length > 0 && !this.chargement() && this.son()) {
          this.bip();
        }
        bons.forEach((b) => this.connus.add(this.cle(b)));
        this.bons.set(bons);
        this.erreur.set(null);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.signaler(e);
      }
    });
  }

  private cle(bon: Bon): string {
    return `${bon.commandeId}/${bon.envoi}`;
  }

  /** Deux notes breves, assez nettes pour couvrir le bruit d'une cuisine. */
  private bip(): void {
    try {
      this.audio ??= new AudioContext();
      const debut = this.audio.currentTime;
      [880, 1320].forEach((frequence, i) => {
        const oscillateur = this.audio!.createOscillator();
        const volume = this.audio!.createGain();
        oscillateur.frequency.value = frequence;
        volume.gain.setValueAtTime(0.25, debut + i * 0.18);
        volume.gain.exponentialRampToValueAtTime(0.001, debut + i * 0.18 + 0.16);
        oscillateur.connect(volume).connect(this.audio!.destination);
        oscillateur.start(debut + i * 0.18);
        oscillateur.stop(debut + i * 0.18 + 0.17);
      });
    } catch {
      // Navigateur sans audio : l'ecran suffit.
    }
  }

  private signaler(reponse: unknown): void {
    this.erreur.set((reponse as { error?: { message?: string } })?.error?.message ?? 'Serveur injoignable.');
  }

  private lire(cle: string): string | null {
    try {
      return localStorage.getItem(cle);
    } catch {
      return null;
    }
  }

  private ecrire(cle: string, valeur: string): void {
    try {
      localStorage.setItem(cle, valeur);
    } catch {
      // Preference non memorisee : sans consequence.
    }
  }
}
