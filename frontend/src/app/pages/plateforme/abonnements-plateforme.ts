import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AbonnementVue,
  EtatAbonnement,
  Formule,
  FormuleRequete,
  PaiementAbonnement,
  ParametresAbonnement,
  StatutPaiementAbonnement,
  TEINTES_ETATS,
  MODULES_FACTURABLES
} from '../../core/models/abonnement';
import { CodeModule } from '../../core/models/models';
import { PRESENTATION_MODULES } from '../../core/models/plateforme';
import { AbonnementService } from '../../core/services/abonnement.service';
import { environment } from '../../../environments/environment';
import { imprimerFactureAbonnement } from '../../shared/facture-abonnement';
import { Modale } from '../../shared/modale';
import { ouvrirFichier } from '../../shared/ouvrir-fichier';

type Vue = 'paiements' | 'echeances' | 'formules' | 'reglages';

const SECTIONS: Record<Vue, { titre: string; description: string }> = {
  paiements: { titre: 'Paiements', description: 'Paiements declares par les entreprises : une validation ouvre la periode et numerote la facture.' },
  echeances: { titre: 'Echeances', description: "Etat et echeance de l'abonnement de chaque entreprise." },
  formules: { titre: 'Formules', description: 'Prix par module, modules compris et limites de chaque formule.' },
  reglages: { titre: 'Reglages des abonnements', description: 'Essai gratuit, delai de grace, remises et mentions de la facture.' }
};

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
 * Console des abonnements : paiements declares a valider, echeances de chaque
 * entreprise, formules avec leur prix par module et reglages (essai, grace,
 * remises, instructions de paiement, mentions de facture).
 */
@Component({
  selector: 'app-abonnements-plateforme',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale],
  templateUrl: './abonnements-plateforme.html',
  styleUrl: './abonnements-plateforme.scss'
})
export class AbonnementsPlateforme implements OnInit {
  private readonly service = inject(AbonnementService);

  /** Onglet ouvert, tire de l'adresse. */
  readonly vue = input<Vue>('paiements');

  readonly teintes = TEINTES_ETATS;
  readonly presentation = PRESENTATION_MODULES;
  readonly libellesModules = LIBELLES_MODULES;
  readonly catalogue = MODULES_FACTURABLES;

  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly enCours = signal(false);

  readonly paiements = signal<PaiementAbonnement[]>([]);
  readonly abonnements = signal<AbonnementVue[]>([]);
  readonly formules = signal<Formule[]>([]);
  readonly parametres = signal<ParametresAbonnement | null>(null);

  /** Le menu Abonnements porte deja les sections : la page n'affiche que le titre de celle ouverte. */
  readonly section = computed(() => SECTIONS[this.vue()] ?? SECTIONS['paiements']);

  // ------------------------------------------------------------ paiements
  readonly statutPaiement = signal<StatutPaiementAbonnement | ''>('EN_ATTENTE');
  readonly recherchePaiement = signal('');
  readonly enAttente = computed(() => this.paiements().filter((p) => p.statut === 'EN_ATTENTE'));
  readonly paiementsAffiches = computed(() => {
    const statut = this.statutPaiement();
    const motif = this.recherchePaiement().trim().toLowerCase();
    return this.paiements().filter((p) => (!statut || p.statut === statut)
      && (!motif || [p.entrepriseNom, p.reference, p.numeroFacture].some((v) => v?.toLowerCase().includes(motif))));
  });
  readonly aRejeter = signal<PaiementAbonnement | null>(null);
  motifRejet = '';

  // ------------------------------------------------------------ echeances
  readonly etatFiltre = signal<EtatAbonnement | ''>('');
  readonly abonnementsAffiches = computed(() => {
    const etat = this.etatFiltre();
    return [...this.abonnements()]
      .filter((a) => !etat || a.etat === etat)
      .sort((a, b) => a.fin.localeCompare(b.fin));
  });
  readonly totalMensuel = computed(() => this.abonnements()
    .filter((a) => !a.essai && !a.lectureSeule).reduce((s, a) => s + a.mensualite, 0));

  // ------------------------------------------------------------- formules
  readonly formuleEditee = signal<Formule | 'nouvelle' | null>(null);
  saisieFormule: FormuleRequete = this.formuleVide();
  readonly erreurFormule = signal<string | null>(null);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.service.tousLesPaiements().subscribe({ next: (p) => this.paiements.set(p) });
    this.service.tousLesAbonnements().subscribe({ next: (a) => this.abonnements.set(a) });
    this.service.formules().subscribe({ next: (f) => this.formules.set(f) });
    this.service.parametres().subscribe({ next: (p) => this.parametres.set(p) });
  }

  private reussite(texte: string): void {
    this.erreur.set(null);
    this.succes.set(texte);
    this.enCours.set(false);
  }

  private echec(r: unknown, defaut: string): void {
    this.succes.set(null);
    this.erreur.set(message(r, defaut));
    this.enCours.set(false);
  }

  // ------------------------------------------------------------ paiements

  valider(p: PaiementAbonnement): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.service.valider(p.id).subscribe({
      next: (maj) => {
        this.paiements.update((l) => l.map((x) => (x.id === maj.id ? maj : x)));
        this.reussite(`Paiement de ${maj.entrepriseNom} valide : facture ${maj.numeroFacture}, abonnement jusqu'au `
          + new Date(maj.periodeFin + 'T00:00:00').toLocaleDateString('fr-FR') + '.');
        this.service.tousLesAbonnements().subscribe({ next: (a) => this.abonnements.set(a) });
      },
      error: (r) => this.echec(r, 'Validation impossible.')
    });
  }

  ouvrirRejet(p: PaiementAbonnement): void {
    this.motifRejet = '';
    this.aRejeter.set(p);
  }

  rejeter(): void {
    const p = this.aRejeter();
    if (!p || !this.motifRejet.trim() || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.service.rejeter(p.id, this.motifRejet.trim()).subscribe({
      next: (maj) => {
        this.paiements.update((l) => l.map((x) => (x.id === maj.id ? maj : x)));
        this.aRejeter.set(null);
        this.reussite(`Paiement de ${maj.entrepriseNom} rejete : l'entreprise voit le motif.`);
      },
      error: (r) => this.echec(r, 'Rejet impossible.')
    });
  }

  voirPreuve(p: PaiementAbonnement): void {
    ouvrirFichier(this.service.preuve(p.id, true), p.preuveNom ?? 'preuve');
  }

  imprimer(p: PaiementAbonnement): void {
    imprimerFactureAbonnement(p, environment.nomPlateforme, this.parametres()?.mentionsFacture);
  }

  libellesListe(codes: string[]): string {
    return codes.map((c) => LIBELLES_MODULES[c] ?? c).join(', ');
  }

  // ------------------------------------------------------------- formules

  editerFormule(f: Formule | null): void {
    this.erreurFormule.set(null);
    if (f) {
      const { id: _id, abonnes: _abonnes, ...reste } = f;
      this.saisieFormule = { ...reste, prix: { ...reste.prix } };
      this.formuleEditee.set(f);
    } else {
      this.saisieFormule = this.formuleVide();
      this.formuleEditee.set('nouvelle');
    }
  }

  enregistrerFormule(): void {
    const f = this.formuleEditee();
    if (!f || this.enCours()) {
      return;
    }
    const s = this.saisieFormule;
    const nettoyer = (v: number | null | undefined) => (v === null || v === undefined || (v as unknown) === '' ? null : Number(v));
    const requete: FormuleRequete = {
      ...s, nom: s.nom.trim(),
      prix: Object.fromEntries(MODULES_FACTURABLES.map((m) => [m.code, Number(s.prix?.[m.code]) || 0])),
      maxUtilisateurs: nettoyer(s.maxUtilisateurs),
      maxVehicules: nettoyer(s.maxVehicules), maxAgences: nettoyer(s.maxAgences),
      maxCaisses: nettoyer(s.maxCaisses), maxEmployes: nettoyer(s.maxEmployes), ordre: Number(s.ordre) || 0
    };
    if (!requete.nom) {
      this.erreurFormule.set('Donnez un nom a la formule.');
      return;
    }
    this.enCours.set(true);
    const appel = f === 'nouvelle' ? this.service.creerFormule(requete) : this.service.modifierFormule(f.id, requete);
    appel.subscribe({
      next: () => {
        this.formuleEditee.set(null);
        this.service.formules().subscribe({ next: (l) => this.formules.set(l) });
        this.reussite(`Formule ${requete.nom} enregistree.`);
      },
      error: (r) => {
        this.enCours.set(false);
        this.erreurFormule.set(message(r, 'Enregistrement impossible.'));
      }
    });
  }

  supprimerFormule(f: Formule): void {
    if (this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.service.supprimerFormule(f.id).subscribe({
      next: () => {
        this.formules.update((l) => l.filter((x) => x.id !== f.id));
        this.formuleEditee.set(null);
        this.reussite(`Formule ${f.nom} supprimee.`);
      },
      error: (r) => {
        this.enCours.set(false);
        this.erreurFormule.set(message(r, 'Suppression impossible.'));
      }
    });
  }

  private formuleVide(): FormuleRequete {
    return {
      nom: '', description: '', prix: Object.fromEntries(MODULES_FACTURABLES.map((m) => [m.code, 0])),
      maxUtilisateurs: null, maxVehicules: null,
      maxAgences: null, maxCaisses: null, maxEmployes: null, active: true, miseEnAvant: false, ordre: this.formules().length + 1
    };
  }

  limite(n: number | null | undefined): string {
    return n == null ? '∞' : String(n);
  }

  prixDe(f: Formule, m: CodeModule): number {
    return Number(f.prix?.[m] ?? 0);
  }

  // ------------------------------------------------------------- reglages

  enregistrerParametres(): void {
    const p = this.parametres();
    if (!p || this.enCours()) {
      return;
    }
    this.enCours.set(true);
    this.service.modifierParametres({
      ...p,
      dureeEssaiJours: Number(p.dureeEssaiJours) || 0, joursGrace: Number(p.joursGrace) || 0,
      joursRappel: Number(p.joursRappel) || 0, remiseTrimestrielle: Number(p.remiseTrimestrielle) || 0,
      remiseSemestrielle: Number(p.remiseSemestrielle) || 0, remiseAnnuelle: Number(p.remiseAnnuelle) || 0
    }).subscribe({
      next: (maj) => {
        this.parametres.set(maj);
        this.reussite('Reglages des abonnements enregistres.');
      },
      error: (r) => this.echec(r, 'Enregistrement impossible.')
    });
  }
}
