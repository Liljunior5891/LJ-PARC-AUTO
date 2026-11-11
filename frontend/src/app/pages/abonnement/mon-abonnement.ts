import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AbonnementVue,
  MOYENS_PAIEMENT,
  MoyenPaiementAbonnement,
  OffreAbonnement,
  PaiementAbonnement,
  Periodicite,
  TEINTES_ETATS
} from '../../core/models/abonnement';
import { CodeModule } from '../../core/models/models';
import { PRESENTATION_MODULES } from '../../core/models/plateforme';
import { AuthService } from '../../core/auth/auth.service';
import { AbonnementService } from '../../core/services/abonnement.service';
import { environment } from '../../../environments/environment';
import { imprimerFactureAbonnement } from '../../shared/facture-abonnement';
import { ouvrirFichier } from '../../shared/ouvrir-fichier';
import { ChoixFormule } from '../plateforme/choix-formule';
import { Modale } from '../../shared/modale';

const LIBELLES_MODULES: Record<string, string> = {
  GESTION_PARC: 'Gestion de parc',
  POINT_DE_VENTE: 'Point de vente',
  MARKETPLACE: 'Marketplace',
  AGENCES: 'Agences multiservices',
  PRESENCE: 'Gestion de presence'
};

function message(reponse: unknown, defaut: string): string {
  return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
}

/**
 * Abonnement de l'entreprise, pour son administrateur : etat et echeance,
 * consommation des limites, choix de la formule et des modules, declaration
 * du paiement avec sa preuve, historique et factures.
 */
@Component({
  selector: 'app-mon-abonnement',
  imports: [DatePipe, DecimalPipe, FormsModule, ChoixFormule, Modale],
  templateUrl: './mon-abonnement.html',
  styleUrl: './mon-abonnement.scss'
})
export class MonAbonnement implements OnInit {
  private readonly service = inject(AbonnementService);
  private readonly auth = inject(AuthService);

  readonly teintes = TEINTES_ETATS;
  readonly moyens = MOYENS_PAIEMENT;
  readonly presentation = PRESENTATION_MODULES;
  readonly libellesModules = LIBELLES_MODULES;

  readonly abonnement = signal<AbonnementVue | null>(null);
  readonly offre = signal<OffreAbonnement | null>(null);
  readonly paiements = signal<PaiementAbonnement[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly enCours = signal(false);
  readonly choix = signal(false);

  readonly formuleId = signal<number | null>(null);
  readonly periodicite = signal<Periodicite>('MENSUEL');
  readonly modules = signal<CodeModule[]>([]);
  moyen: MoyenPaiementAbonnement = 'MOBILE_MONEY';
  reference = '';
  note = '';
  preuve: File | null = null;

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.abonnement().subscribe({
      next: (a) => this.abonnement.set(a),
      error: (r) => this.erreur.set(message(r, 'Abonnement indisponible.'))
    });
    this.service.offre().subscribe({ next: (o) => this.offre.set(o) });
    this.service.paiements().subscribe({ next: (p) => this.paiements.set(p) });
    this.service.etat().subscribe();
  }

  /** Part de la periode deja ecoulee, pour la barre de l'echeance. */
  partPeriode(debut: string, fin: string): number {
    const d = new Date(debut).getTime();
    const f = new Date(fin).getTime();
    if (!(f > d)) {
      return 100;
    }
    return Math.min(100, Math.max(0, ((Date.now() - d) / (f - d)) * 100));
  }

  partUsage(utilise: number, limite?: number | null): number {
    if (limite == null) {
      return 0;
    }
    return limite === 0 ? 100 : Math.min(100, Math.round((utilise * 100) / limite));
  }

  /** Ouvre le choix : la formule et les modules en cours, a defaut les modules deja actives. */
  ouvrirChoix(): void {
    const a = this.abonnement();
    const o = this.offre();
    // La formule en cours n'est reprise que si elle est encore proposee.
    const proposees = o?.formules ?? [];
    const enCours = proposees.find((f) => f.id === a?.formuleId);
    this.formuleId.set(enCours?.id ?? proposees.find((f) => f.miseEnAvant)?.id ?? proposees[0]?.id ?? null);
    this.periodicite.set((a?.periodicite as Periodicite) ?? 'MENSUEL');
    const actifs = (this.auth.utilisateur()?.modules ?? []) as CodeModule[];
    this.modules.set(a && !a.essai && a.modules.length ? [...a.modules] : [...actifs]);
    this.moyen = 'MOBILE_MONEY';
    this.reference = '';
    this.note = '';
    this.preuve = null;
    this.succes.set(null);
    this.erreur.set(null);
    this.choix.set(true);
  }

  choisirPreuve(evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0] ?? null;
    if (fichier && fichier.size > 15 * 1024 * 1024) {
      this.erreur.set('La preuve depasse 15 Mo.');
      return;
    }
    if (fichier && !/\.(pdf|jpe?g|png|webp)$/i.test(fichier.name)) {
      this.erreur.set('La preuve doit etre un PDF ou une image (JPG, PNG, WEBP).');
      return;
    }
    this.preuve = fichier;
  }

  declarer(): void {
    const formuleId = this.formuleId();
    if (!formuleId || !this.modules().length) {
      this.erreur.set('Choisissez une formule et au moins un module.');
      return;
    }
    if (!this.reference.trim() && !this.preuve) {
      this.erreur.set('Indiquez la reference du paiement ou joignez une preuve : la plateforme en a besoin pour le retrouver.');
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.service.declarer({
      formuleId, periodicite: this.periodicite(), modules: this.modules(), moyen: this.moyen,
      reference: this.reference.trim() || null, note: this.note.trim() || null
    }).subscribe({
      next: (p) => {
        const fin = (avertissement?: string) => {
          this.enCours.set(false);
          this.choix.set(false);
          this.succes.set('Paiement declare. Il sera verifie par la plateforme ; votre abonnement s\'ouvrira des sa validation.'
            + (avertissement ? ' ' + avertissement : ''));
          this.charger();
        };
        if (!this.preuve) {
          fin();
          return;
        }
        this.service.joindrePreuve(p.id, this.preuve).subscribe({
          next: () => fin(),
          error: (r) => {
            const raison = message(r, 'La preuve n\'a pas pu etre envoyee.');
            if (this.reference.trim()) {
              // La reference suffit a retrouver le paiement : la declaration reste, la preuve se joindra depuis l'historique.
              fin(`Attention : ${raison} Joignez-la depuis l'historique.`);
              return;
            }
            // Sans reference ni preuve, la declaration ne vaut rien : on la retire.
            this.service.annuler(p.id).subscribe({
              complete: () => {
                this.enCours.set(false);
                this.erreur.set(`${raison} Paiement non declare : reessayez ou indiquez la reference.`);
                this.charger();
              },
              error: () => {
                this.enCours.set(false);
                this.erreur.set(`${raison} Joignez la preuve depuis l'historique ou annulez la declaration.`);
                this.charger();
              }
            });
          }
        });
      },
      error: (r) => {
        this.enCours.set(false);
        this.erreur.set(message(r, 'Declaration impossible.'));
      }
    });
  }

  annuler(p: PaiementAbonnement): void {
    this.service.annuler(p.id).subscribe({
      next: () => {
        this.succes.set('Declaration annulee.');
        this.charger();
      },
      error: (r) => this.erreur.set(message(r, 'Annulation impossible.'))
    });
  }

  joindre(p: PaiementAbonnement, evenement: Event): void {
    const fichier = (evenement.target as HTMLInputElement).files?.[0];
    if (!fichier) {
      return;
    }
    this.service.joindrePreuve(p.id, fichier).subscribe({
      next: (maj) => this.paiements.update((l) => l.map((x) => (x.id === maj.id ? maj : x))),
      error: (r) => this.erreur.set(message(r, 'Envoi de la preuve impossible.'))
    });
  }

  voirPreuve(p: PaiementAbonnement): void {
    ouvrirFichier(this.service.preuve(p.id), p.preuveNom ?? 'preuve');
  }

  imprimer(p: PaiementAbonnement): void {
    imprimerFactureAbonnement(p, environment.nomPlateforme, this.offre()?.parametres.mentionsFacture);
  }

  libellesListe(codes: string[]): string {
    return codes.map((c) => LIBELLES_MODULES[c] ?? c).join(', ');
  }
}
