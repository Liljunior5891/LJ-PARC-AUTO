import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { SANS_SUIVI } from '../operation/operation.interceptor';

export type TypeNotificationPdv = 'VALIDATION' | 'APPEL' | 'ADDITION' | 'PRET' | 'BON' | 'CAISSE';

/** Evenement en attente pour le poste, tel que le serveur le decrit. */
export interface NotificationPdv {
  cle: string;
  type: TypeNotificationPdv;
  titre: string;
  message: string;
  lien: string | null;
  date: string | null;
}

/** Interrogation du serveur : assez frequente pour la cuisine, legere pour le serveur. */
const INTERVALLE_MS = 6000;
/** Un message reste affiche ce temps, sauf s'il est ferme ou ouvert avant. */
const DUREE_AFFICHAGE_MS = 25000;
const CLE_VUS = 'pdv.notifications-vues';
const CLE_SON = 'pdv.notifications-son';
const CLE_REFUS_SYSTEME = 'pdv.notifications-systeme-plus-tard';

/** Etat des notifications du systeme : non prises en charge hors connexion securisee (https ou localhost). */
export type PermissionSysteme = NotificationPermission | 'indisponible';

/** Melodie de chaque evenement : frequences (Hz) jouees l'une apres l'autre. */
const MELODIES: Record<TypeNotificationPdv, number[]> = {
  VALIDATION: [880, 660, 880],
  BON: [1046, 1046, 1046],
  PRET: [660, 880, 1175],
  APPEL: [740, 988],
  ADDITION: [988, 740],
  CAISSE: [523, 784]
};

/**
 * Notifications sonores et visuelles du point de vente. Le navigateur
 * interroge regulierement le serveur ; chaque evenement jamais vu (commande a
 * valider, nouveau bon, plat pret, appel d'un client, caisse ouverte) joue une
 * melodie propre a son type, fait vibrer un telephone et s'affiche en message.
 * Les evenements deja en attente a la connexion ne sonnent pas.
 * <p>
 * Si l'utilisateur l'autorise, chaque evenement s'affiche aussi en
 * notification du systeme quand l'onglet est en arriere-plan ou la fenetre
 * reduite ; un clic dessus ramene sur la commande ou l'ecran concerne.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsPdvService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly url = `${environment.apiUrl}/pdv/notifications`;

  /** Messages affiches, le plus recent en tete. */
  readonly messages = signal<NotificationPdv[]>([]);
  /** Derniers evenements recus, consultables depuis la barre du haut, et ceux pas encore vus. */
  readonly historique = signal<NotificationPdv[]>([]);
  readonly nonLues = signal(0);
  readonly son = signal(this.lireSon());
  readonly permission = signal<PermissionSysteme>(this.lirePermission());
  /** L'utilisateur a repousse la proposition : elle ne revient pas sur ce navigateur. */
  readonly propositionRepoussee = signal(this.lireRefus());

  private minuterie: ReturnType<typeof setInterval> | null = null;
  private vus = new Set<string>();
  private premierPassage = true;
  private audio: AudioContext | null = null;
  private readonly fermetures = new Map<string, ReturnType<typeof setTimeout>>();

  demarrer(): void {
    if (this.minuterie) {
      return;
    }
    this.vus = this.lireVus();
    this.premierPassage = this.vus.size === 0;
    // Les navigateurs n'autorisent le son qu'apres un geste de l'utilisateur.
    const deverrouiller = () => {
      this.contexteAudio();
      document.removeEventListener('pointerdown', deverrouiller);
      document.removeEventListener('keydown', deverrouiller);
    };
    document.addEventListener('pointerdown', deverrouiller);
    document.addEventListener('keydown', deverrouiller);
    this.interroger();
    this.minuterie = setInterval(() => this.interroger(), INTERVALLE_MS);
  }

  arreter(): void {
    if (this.minuterie) {
      clearInterval(this.minuterie);
      this.minuterie = null;
    }
    this.messages.set([]);
    // A la deconnexion, le compte suivant repart de zero sur ce navigateur.
    try {
      sessionStorage.removeItem(CLE_VUS);
    } catch {
      // Rien a nettoyer.
    }
  }

  marquerLues(): void {
    this.nonLues.set(0);
  }

  fermer(cle: string): void {
    clearTimeout(this.fermetures.get(cle));
    this.fermetures.delete(cle);
    this.messages.update((liste) => liste.filter((m) => m.cle !== cle));
  }

  /** A appeler sur un geste de l'utilisateur : le navigateur affiche sa demande d'autorisation. */
  demanderPermissionSysteme(): void {
    if (this.permission() === 'indisponible') {
      return;
    }
    void Notification.requestPermission().then((p) => {
      this.permission.set(p);
      if (p === 'granted') {
        this.systeme({ cle: 'essai', type: 'CAISSE', titre: 'Notifications activees',
          message: 'Vous serez prevenu meme quand cet onglet est en arriere-plan.', lien: null, date: null });
      }
    });
  }

  repousserProposition(): void {
    this.propositionRepoussee.set(true);
    try {
      localStorage.setItem(CLE_REFUS_SYSTEME, 'oui');
    } catch {
      // La proposition reviendra au prochain chargement.
    }
  }

  basculerSon(): void {
    this.son.update((actif) => !actif);
    try {
      localStorage.setItem(CLE_SON, this.son() ? 'oui' : 'non');
    } catch {
      // Preference gardee pour la page seulement.
    }
    if (this.son()) {
      this.jouer('CAISSE');
    }
  }

  private interroger(): void {
    this.http.get<NotificationPdv[]>(this.url, { context: new HttpContext().set(SANS_SUIVI, true) }).subscribe({
      next: (evenements) => this.traiter(evenements),
      error: () => undefined
    });
  }

  private traiter(evenements: NotificationPdv[]): void {
    const nouveaux = evenements.filter((e) => !this.vus.has(e.cle));
    nouveaux.forEach((e) => this.vus.add(e.cle));
    // On ne garde que les cles encore actives, plus les nouvelles : la memoire ne grossit pas.
    const actives = new Set(evenements.map((e) => e.cle));
    this.vus = new Set([...this.vus].filter((c) => actives.has(c)));
    this.ecrireVus();
    if (this.premierPassage) {
      this.premierPassage = false;
      return;
    }
    if (nouveaux.length === 0) {
      return;
    }
    this.messages.update((liste) => [...nouveaux, ...liste].slice(0, 6));
    this.historique.update((liste) => [...nouveaux, ...liste].slice(0, 20));
    this.nonLues.update((n) => n + nouveaux.length);
    nouveaux.forEach((e) => this.fermetures.set(e.cle, setTimeout(() => this.fermer(e.cle), DUREE_AFFICHAGE_MS)));
    this.jouer(nouveaux[0].type);
    navigator.vibrate?.([180, 90, 180]);
    // Onglet cache ou fenetre en arriere-plan : le systeme prend le relais.
    if (document.hidden || !document.hasFocus()) {
      nouveaux.slice(0, 3).forEach((e) => this.systeme(e));
    }
  }

  private systeme(e: NotificationPdv): void {
    if (this.permission() !== 'granted') {
      return;
    }
    try {
      const notification = new Notification(e.titre, {
        body: e.message,
        tag: e.cle,
        icon: '/favicon.ico',
        // Ce qui attend une action (commande a valider, bon a preparer) reste affiche jusqu'au clic.
        requireInteraction: e.type === 'VALIDATION' || e.type === 'BON'
      });
      notification.onclick = () => {
        window.focus();
        if (e.lien) {
          void this.router.navigateByUrl(e.lien);
        }
        this.fermer(e.cle);
        notification.close();
      };
    } catch {
      // Certains navigateurs mobiles n'affichent les notifications que par un service worker : on s'en tient au message.
    }
  }

  /** Melodie synthetisee : pas de fichier son a charger. */
  private jouer(type: TypeNotificationPdv): void {
    if (!this.son()) {
      return;
    }
    const contexte = this.contexteAudio();
    if (!contexte) {
      return;
    }
    const debut = contexte.currentTime + 0.02;
    MELODIES[type].forEach((frequence, i) => {
      const oscillateur = contexte.createOscillator();
      const volume = contexte.createGain();
      oscillateur.type = 'sine';
      oscillateur.frequency.value = frequence;
      const t = debut + i * 0.18;
      volume.gain.setValueAtTime(0.0001, t);
      volume.gain.exponentialRampToValueAtTime(0.35, t + 0.02);
      volume.gain.exponentialRampToValueAtTime(0.0001, t + 0.16);
      oscillateur.connect(volume).connect(contexte.destination);
      oscillateur.start(t);
      oscillateur.stop(t + 0.17);
    });
  }

  private contexteAudio(): AudioContext | null {
    try {
      this.audio ??= new AudioContext();
      if (this.audio.state === 'suspended') {
        void this.audio.resume();
      }
      return this.audio;
    } catch {
      return null;
    }
  }

  private lireVus(): Set<string> {
    try {
      return new Set(JSON.parse(sessionStorage.getItem(CLE_VUS) ?? '[]') as string[]);
    } catch {
      return new Set();
    }
  }

  private ecrireVus(): void {
    try {
      sessionStorage.setItem(CLE_VUS, JSON.stringify([...this.vus]));
    } catch {
      // Sans stockage, les evenements deja vus pourraient ressonner apres un rechargement.
    }
  }

  private lirePermission(): PermissionSysteme {
    return typeof Notification !== 'undefined' && window.isSecureContext ? Notification.permission : 'indisponible';
  }

  private lireRefus(): boolean {
    try {
      return localStorage.getItem(CLE_REFUS_SYSTEME) === 'oui';
    } catch {
      return false;
    }
  }

  private lireSon(): boolean {
    try {
      return localStorage.getItem(CLE_SON) !== 'non';
    } catch {
      return true;
    }
  }
}
