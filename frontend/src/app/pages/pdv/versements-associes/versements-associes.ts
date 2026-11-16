import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import {
  DemandeDepotAssocie,
  DepotAssocie,
  LIBELLES_STATUT_DEPOT,
  LigneVersementAssocie,
  SourceDepotAssocie,
  SyntheseVersements
} from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { Modale } from '../../../shared/modale';
import { PieceJointe } from '../../../shared/piece-jointe';

/** Saisie (ou correction) d'un versement. */
interface Formulaire {
  depotId: number | null;
  associeId: number | null;
  montant: number | null;
  source: SourceDepotAssocie;
  banque: string;
  reference: string;
  dateDepot: string;
  periode: string;
  note: string;
  /** Bordereau deja joint (correction). */
  pieceExistante: boolean;
}

/**
 * Versements aux associes : ce qui reste du a chacun sur les mois clos, et les
 * depots en banque saisis par le gerant (bordereau joint), que l'associe valide
 * ou refuse. Un depot refuse se corrige ici puis repart vers l'associe.
 */
@Component({
  selector: 'app-versements-associes-pdv',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, PieceJointe],
  templateUrl: './versements-associes.html',
  styleUrl: './versements-associes.scss'
})
export class VersementsAssociesPdv implements OnInit {
  private readonly options = inject(OptionsPdvService);
  private readonly auth = inject(AuthService);
  readonly estAdmin = this.auth.aRole('ADMIN');
  readonly moi = this.auth.utilisateur()?.id;
  /** Le coffre n'existe qu'avec le circuit des especes, pour un gerant qui n'est pas administrateur. */
  readonly coffrePossible = computed(() => this.options.circuitActif() && !this.estAdmin && this.auth.aRole('GERANT'));
  readonly libelles = LIBELLES_STATUT_DEPOT;
  readonly aujourdhui = new Date().toISOString().slice(0, 10);

  readonly donnees = signal<SyntheseVersements | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly filtre = signal<'A_TRAITER' | 'TOUS'>('A_TRAITER');

  readonly formulaire = signal<Formulaire | null>(null);
  readonly fichier = signal<File | null>(null);
  readonly envoi = signal(false);
  readonly erreurFormulaire = signal<string | null>(null);

  readonly depotsAffiches = computed(() => {
    const d = this.donnees()?.depots ?? [];
    return this.filtre() === 'TOUS' ? d : d.filter((x) => x.statut === 'EN_ATTENTE' || x.statut === 'REFUSE');
  });
  readonly totaux = computed(() => {
    const a = this.donnees()?.associes ?? [];
    const somme = (f: (l: LigneVersementAssocie) => number) => a.reduce((s, l) => s + f(l), 0);
    return { reste: somme((l) => l.resteAVerser), verse: somme((l) => l.verse), enCours: somme((l) => l.enCours),
      estimation: somme((l) => l.estimation) };
  });
  readonly refuses = computed(() => (this.donnees()?.depots ?? []).filter((d) => d.statut === 'REFUSE').length);

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.options.versementsAssocies().subscribe({
      next: (d) => {
        this.donnees.set(d);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Chargement des versements impossible.');
      }
    });
  }

  /** Plafond du montant : reste a verser de l'associe, plus le montant deja engage par le depot corrige. */
  plafond(f: Formulaire): number {
    const ligne = this.donnees()?.associes.find((a) => a.associeId === f.associeId);
    const depot = f.depotId ? this.donnees()?.depots.find((d) => d.id === f.depotId) : null;
    return (ligne?.resteAVerser ?? 0) + (depot?.montant ?? 0);
  }

  nouveau(associe?: LigneVersementAssocie): void {
    this.fichier.set(null);
    this.erreurFormulaire.set(null);
    this.formulaire.set({
      depotId: null, associeId: associe?.associeId ?? null, montant: associe && associe.resteAVerser > 0 ? associe.resteAVerser : null,
      source: 'BANQUE', banque: '', reference: '', dateDepot: this.aujourdhui, periode: this.periodeParDefaut(), note: '',
      pieceExistante: false
    });
  }

  corriger(d: DepotAssocie): void {
    this.fichier.set(null);
    this.erreurFormulaire.set(null);
    this.formulaire.set({
      depotId: d.id, associeId: d.associeId, montant: d.montant, source: d.source, banque: d.banque ?? '',
      reference: d.reference, dateDepot: d.dateDepot, periode: d.periode ?? '', note: d.note ?? '', pieceExistante: d.piece
    });
  }

  modifiable(d: DepotAssocie): boolean {
    return (d.statut === 'EN_ATTENTE' || d.statut === 'REFUSE') && (this.estAdmin || d.auteurId === this.moi);
  }

  envoyable(f: Formulaire): boolean {
    const montant = Number(f.montant);
    return !this.envoi() && !!f.associeId && montant > 0 && montant <= this.plafond(f) && !!f.reference.trim()
      && !!f.dateDepot && (!!this.fichier() || f.pieceExistante);
  }

  enregistrer(): void {
    const f = this.formulaire();
    if (!f || !this.envoyable(f)) {
      return;
    }
    const demande: DemandeDepotAssocie = {
      associeId: f.associeId!, montant: Number(f.montant), source: f.source, banque: f.banque.trim() || null,
      reference: f.reference.trim(), dateDepot: f.dateDepot, periode: f.periode.trim() || null, note: f.note.trim() || null
    };
    this.envoi.set(true);
    this.erreurFormulaire.set(null);
    const appel = f.depotId ? this.options.corrigerDepotAssocie(f.depotId, demande) : this.options.creerDepotAssocie(demande);
    appel.subscribe({
      next: (d) => {
        const fichier = this.fichier();
        if (!fichier) {
          this.termine(d, !!f.depotId);
          return;
        }
        this.options.joindrePieceDepotAssocie(d.id, fichier).subscribe({
          next: (avecPiece) => this.termine(avecPiece, !!f.depotId),
          error: (e) => {
            this.envoi.set(false);
            this.erreurFormulaire.set(e?.error?.message ?? "Versement enregistre, mais le bordereau n'a pas pu etre joint : reessayez.");
            // Le depot existe : une nouvelle tentative le corrige au lieu d'en creer un second.
            this.formulaire.set({ ...f, depotId: d.id });
            this.charger();
          }
        });
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreurFormulaire.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  /** Annulation en deux temps : un premier clic demande confirmation sur la ligne. */
  readonly aAnnuler = signal<number | null>(null);

  annuler(d: DepotAssocie): void {
    if (this.aAnnuler() !== d.id) {
      this.aAnnuler.set(d.id);
      return;
    }
    this.aAnnuler.set(null);
    this.options.annulerDepotAssocie(d.id).subscribe({
      next: () => {
        this.succes.set(`Versement ${d.numero} annule : le montant reste a verser a ${d.associeNom}.`);
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Annulation impossible.')
    });
  }

  voirPiece(d: DepotAssocie): void {
    this.options.pieceDepotAssocie(d.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: () => this.erreur.set('Bordereau introuvable.')
    });
  }

  private termine(d: DepotAssocie, correction: boolean): void {
    this.envoi.set(false);
    this.formulaire.set(null);
    this.succes.set(`${correction ? 'Versement corrige' : 'Versement enregistre'} (${d.numero}) : il attend la validation de ${d.associeNom}.`);
    this.charger();
  }

  /** Le mois precedent, en clair : la periode la plus souvent versee. */
  private periodeParDefaut(): string {
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() - 1);
    return d.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  }
}
