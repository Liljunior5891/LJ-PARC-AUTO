import { Injectable, signal } from '@angular/core';

/** Ce que la fenetre de confirmation presente avant une action. */
export interface DemandeConfirmation {
  titre: string;
  message?: string;
  /** Libelle du bouton de validation. */
  libelleAction?: string;
  /** Action destructrice ou irreversible : bouton rouge. */
  danger?: boolean;
}

export type EtatOperation = 'inactif' | 'confirmation' | 'en-cours' | 'succes' | 'echec';

/** Delai pendant lequel une requete suivante rejoint l'operation qui s'acheve (envois en chaine). */
const DELAI_REGROUPEMENT = 250;
/** Duree minimale de la progression : meme une reponse immediate laisse voir l'anneau se remplir. */
const DUREE_MINIMALE = 700;
const DUREE_SUCCES = 1100;
const DUREE_ECHEC = 2400;
/** La progression s'approche de ce plafond tant que le serveur n'a pas repondu. */
const PLAFOND_ATTENTE = 90;

/**
 * Confirmation et suivi des operations qui modifient des donnees.
 * <p>
 * Avant l'envoi, une fenetre demande de confirmer l'action ; pendant
 * l'envoi, un anneau progresse jusqu'a 100 % puis annonce que l'operation
 * est validee (ou refusee). L'intercepteur {@link operationInterceptor}
 * s'en sert pour toutes les requetes d'ecriture.
 * <p>
 * Un ecran qui veut un message precis appelle {@link confirmer} lui-meme :
 * la requete qui suit est alors envoyee sans seconde confirmation.
 */
@Injectable({ providedIn: 'root' })
export class OperationService {
  readonly etat = signal<EtatOperation>('inactif');
  readonly demande = signal<DemandeConfirmation | null>(null);
  readonly progression = signal(0);
  readonly messageResultat = signal<string | null>(null);

  private resoudre?: (accepte: boolean) => void;
  private preConfirmee = false;
  private actives = 0;
  private echec: string | null = null;
  private minuteurProgression?: ReturnType<typeof setInterval>;
  /** Court delai entre la fin de la derniere requete et le 100 % : une requete en chaine s'y rattache. */
  private minuteurRegroupement?: ReturnType<typeof setTimeout>;
  private minuteurAffichage?: ReturnType<typeof setTimeout>;
  private debutOperation = 0;

  /**
   * Confirmation demandee par un ecran, avec son propre message. Si elle est
   * acceptee, la prochaine requete d'ecriture part sans redemander.
   */
  async confirmer(demande: DemandeConfirmation): Promise<boolean> {
    const accepte = await this.demander(demande);
    this.preConfirmee = accepte;
    return accepte;
  }

  /** Affiche la fenetre de confirmation ; la promesse donne la reponse. */
  demander(demande: DemandeConfirmation): Promise<boolean> {
    // Une demande deja ouverte est consideree comme abandonnee.
    this.resoudre?.(false);
    this.demande.set(demande);
    this.etat.set('confirmation');
    return new Promise((resolve) => (this.resoudre = resolve));
  }

  /** Reponse de l'utilisateur dans la fenetre. */
  repondre(accepte: boolean): void {
    const resoudre = this.resoudre;
    this.resoudre = undefined;
    this.demande.set(null);
    if (this.etat() === 'confirmation') {
      this.etat.set(this.actives > 0 ? 'en-cours' : 'inactif');
    }
    resoudre?.(accepte);
  }

  /** Vrai une seule fois apres une confirmation donnee par un ecran. */
  consommerPreConfirmation(): boolean {
    const confirmee = this.preConfirmee;
    this.preConfirmee = false;
    return confirmee;
  }

  /** Une operation est en cours ou s'acheve : une requete suivante s'y rattache. */
  estEnCours(): boolean {
    return this.actives > 0 || this.minuteurRegroupement !== undefined;
  }

  debuter(): void {
    this.actives++;
    if (this.minuteurRegroupement !== undefined) {
      clearTimeout(this.minuteurRegroupement);
      this.minuteurRegroupement = undefined;
    }
    clearTimeout(this.minuteurAffichage);
    if (this.etat() === 'en-cours') {
      return;
    }
    this.echec = null;
    this.messageResultat.set(null);
    this.progression.set(0);
    this.debutOperation = Date.now();
    this.etat.set('en-cours');
    clearInterval(this.minuteurProgression);
    // Progression amortie : rapide au debut, de plus en plus lente vers le plafond.
    this.minuteurProgression = setInterval(() => {
      const p = this.progression();
      this.progression.set(p + Math.max(0.4, (PLAFOND_ATTENTE - p) * 0.09));
      if (this.progression() >= PLAFOND_ATTENTE) {
        this.progression.set(PLAFOND_ATTENTE);
      }
    }, 80);
  }

  /** Fin d'une requete ; quand la derniere s'acheve, l'anneau atteint 100 %. */
  terminer(erreur: string | null): void {
    this.actives = Math.max(0, this.actives - 1);
    if (erreur) {
      this.echec = erreur;
    }
    if (this.actives > 0) {
      return;
    }
    const restant = DUREE_MINIMALE - (Date.now() - this.debutOperation);
    this.minuteurRegroupement = setTimeout(() => {
      this.minuteurRegroupement = undefined;
      this.cloturer();
    }, Math.max(DELAI_REGROUPEMENT, restant));
  }

  private cloturer(): void {
    clearInterval(this.minuteurProgression);
    this.progression.set(100);
    const reussie = this.echec === null;
    this.etat.set(reussie ? 'succes' : 'echec');
    this.messageResultat.set(reussie ? 'Operation validee' : this.echec);
    this.minuteurAffichage = setTimeout(() => {
      if (this.actives === 0 && this.etat() !== 'confirmation') {
        this.etat.set('inactif');
      }
    }, reussie ? DUREE_SUCCES : DUREE_ECHEC);
  }
}
