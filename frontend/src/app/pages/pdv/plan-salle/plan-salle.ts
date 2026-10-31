import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  FormeTable,
  HAUTEUR_PLAN,
  LARGEUR_PLAN,
  Salle,
  TableRequete,
  TableRestaurant
} from '../../../core/models/restauration';
import { RestaurationService } from '../../../core/services/restauration.service';
import { Modale } from '../../../shared/modale';

/** Gabarits proposes a l'ajout d'une table, en unites du plan. */
const GABARITS: Record<FormeTable, { largeur: number; hauteur: number; places: number; libelle: string }> = {
  CARREE: { largeur: 10, hauteur: 10, places: 4, libelle: 'Carree' },
  RONDE: { largeur: 10, hauteur: 10, places: 4, libelle: 'Ronde' },
  RECTANGLE: { largeur: 18, hauteur: 10, places: 6, libelle: 'Rectangulaire' }
};

/** Deplacement en cours d'une table sur le plan. */
interface Glisse {
  table: TableRestaurant;
  departX: number;
  departY: number;
  origineX: number;
  origineY: number;
  deplacee: boolean;
}

/**
 * Edition du plan du restaurant par le gerant : salles, tables, et leur place
 * sur le plan. Les tables se deplacent au doigt ou a la souris ; les positions
 * s'enregistrent en une fois.
 */
@Component({
  selector: 'app-plan-salle-pdv',
  imports: [FormsModule, RouterLink, Modale],
  templateUrl: './plan-salle.html',
  styleUrls: ['../salle/salle.scss', './plan-salle.scss']
})
export class PlanSallePdv implements OnInit {
  private readonly service = inject(RestaurationService);

  readonly largeurPlan = LARGEUR_PLAN;
  readonly hauteurPlan = HAUTEUR_PLAN;
  readonly gabarits = GABARITS;
  readonly formes = Object.keys(GABARITS) as FormeTable[];

  readonly salles = signal<Salle[]>([]);
  readonly tables = signal<TableRestaurant[]>([]);
  readonly salleChoisie = signal<number | null>(null);
  readonly modifiees = signal<Set<number>>(new Set());
  readonly erreur = signal<string | null>(null);
  readonly information = signal<string | null>(null);

  // Formulaires
  readonly editionSalle = signal<Salle | 'nouvelle' | null>(null);
  nomSalle = '';
  readonly editionTable = signal<TableRestaurant | 'nouvelle' | null>(null);
  formulaire: Required<Pick<TableRequete, 'nom' | 'places' | 'forme' | 'largeur' | 'hauteur'>> = {
    nom: '', places: 4, forme: 'CARREE', largeur: 10, hauteur: 10
  };

  private glisse: Glisse | null = null;
  private readonly plan = viewChild<ElementRef<HTMLElement>>('plan');

  readonly salle = computed(() =>
    this.salles().find((s) => s.id === this.salleChoisie()) ?? this.salles().find((s) => s.actif) ?? null
  );
  readonly tablesSalle = computed(() =>
    this.tables().filter((t) => t.actif && t.salleId === this.salle()?.id)
  );

  ngOnInit(): void {
    this.charger();
  }

  // ------------------------------------------------------------ salles

  nouvelleSalle(): void {
    this.nomSalle = '';
    this.erreur.set(null);
    this.editionSalle.set('nouvelle');
  }

  renommerSalle(salle: Salle): void {
    this.nomSalle = salle.nom;
    this.erreur.set(null);
    this.editionSalle.set(salle);
  }

  enregistrerSalle(): void {
    const edition = this.editionSalle();
    if (!edition || !this.nomSalle.trim()) {
      return;
    }
    const appel = edition === 'nouvelle'
      ? this.service.creerSalle(this.nomSalle.trim(), this.salles().length)
      : this.service.modifierSalle(edition.id, { nom: this.nomSalle.trim(), ordre: edition.ordre, actif: true });
    appel.subscribe({
      next: (salle) => {
        this.editionSalle.set(null);
        this.salleChoisie.set(salle.id);
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  // ------------------------------------------------------------ tables

  nouvelleTable(): void {
    this.formulaire = { nom: this.prochainNom(), ...this.gabarit('CARREE') };
    this.erreur.set(null);
    this.editionTable.set('nouvelle');
  }

  choisirForme(forme: FormeTable): void {
    this.formulaire = { ...this.formulaire, ...this.gabarit(forme) };
  }

  enregistrerTable(): void {
    const edition = this.editionTable();
    const salle = this.salle();
    if (!edition || !salle) {
      return;
    }
    const requete: TableRequete = { salleId: salle.id, ...this.formulaire };
    let appel;
    if (edition === 'nouvelle') {
      const place = this.placeLibre(this.formulaire.largeur, this.formulaire.hauteur);
      appel = this.service.creerTable({ ...requete, posX: place.x, posY: place.y });
    } else {
      appel = this.service.modifierTable(edition.id, { ...requete, posX: edition.posX, posY: edition.posY, actif: true });
    }
    appel.subscribe({
      next: () => {
        this.editionTable.set(null);
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  retirerTable(): void {
    const edition = this.editionTable();
    if (!edition || edition === 'nouvelle') {
      return;
    }
    this.service.retirerTable(edition.id).subscribe({
      next: () => {
        this.editionTable.set(null);
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Retrait impossible.'))
    });
  }

  // ------------------------------------------------------ deplacement

  saisir(table: TableRestaurant, event: PointerEvent): void {
    event.preventDefault();
    (event.target as HTMLElement).setPointerCapture?.(event.pointerId);
    this.glisse = {
      table, departX: event.clientX, departY: event.clientY, origineX: table.posX, origineY: table.posY, deplacee: false
    };
  }

  deplacer(event: PointerEvent): void {
    const glisse = this.glisse;
    const plan = this.plan()?.nativeElement;
    if (!glisse || !plan) {
      return;
    }
    const rect = plan.getBoundingClientRect();
    const dx = Math.round(((event.clientX - glisse.departX) / rect.width) * LARGEUR_PLAN);
    const dy = Math.round(((event.clientY - glisse.departY) / rect.height) * HAUTEUR_PLAN);
    if (!glisse.deplacee && Math.abs(dx) + Math.abs(dy) < 1) {
      return;
    }
    glisse.deplacee = true;
    const posX = Math.max(0, Math.min(LARGEUR_PLAN - glisse.table.largeur, glisse.origineX + dx));
    const posY = Math.max(0, Math.min(HAUTEUR_PLAN - glisse.table.hauteur, glisse.origineY + dy));
    this.tables.update((tables) => tables.map((t) => (t.id === glisse.table.id ? { ...t, posX, posY } : t)));
  }

  lacher(): void {
    const glisse = this.glisse;
    this.glisse = null;
    if (!glisse) {
      return;
    }
    if (!glisse.deplacee) {
      // Simple toucher : on edite la table.
      const table = this.tables().find((t) => t.id === glisse.table.id) ?? glisse.table;
      this.formulaire = {
        nom: table.nom, places: table.places, forme: table.forme, largeur: table.largeur, hauteur: table.hauteur
      };
      this.erreur.set(null);
      this.editionTable.set(table);
      return;
    }
    this.modifiees.update((ids) => new Set(ids).add(glisse.table.id));
    this.information.set(null);
  }

  enregistrerPositions(): void {
    const ids = this.modifiees();
    const positions = this.tables()
      .filter((t) => ids.has(t.id))
      .map((t) => ({ id: t.id, posX: t.posX, posY: t.posY }));
    if (positions.length === 0) {
      return;
    }
    this.service.placerTables(positions).subscribe({
      next: (tables) => {
        this.tables.set(tables);
        this.modifiees.set(new Set());
        this.information.set('Plan enregistre.');
      },
      error: (e) => this.erreur.set(this.message(e, 'Enregistrement du plan impossible.'))
    });
  }

  // ----------------------------------------------------------- interne

  private charger(): void {
    this.service.salles().subscribe({
      next: (salles) => this.salles.set(salles.filter((s) => s.actif)),
      error: (e) => this.erreur.set(this.message(e, 'Chargement impossible.'))
    });
    this.service.tables().subscribe({
      next: (tables) => {
        this.tables.set(tables);
        this.modifiees.set(new Set());
      }
    });
  }

  private gabarit(forme: FormeTable): { forme: FormeTable; places: number; largeur: number; hauteur: number } {
    const g = GABARITS[forme];
    return { forme, places: g.places, largeur: g.largeur, hauteur: g.hauteur };
  }

  /** T1, T2... : le numero suivant le plus grand deja utilise. */
  private prochainNom(): string {
    const numeros = this.tables()
      .map((t) => /^T(\d+)$/i.exec(t.nom)?.[1])
      .filter((n): n is string => !!n)
      .map(Number);
    return `T${numeros.length ? Math.max(...numeros) + 1 : 1}`;
  }

  /** Premiere place libre en balayant le plan de gauche a droite, de haut en bas. */
  private placeLibre(largeur: number, hauteur: number): { x: number; y: number } {
    const occupees = this.tablesSalle();
    const marge = 2;
    for (let y = marge; y + hauteur <= HAUTEUR_PLAN; y += 2) {
      for (let x = marge; x + largeur <= LARGEUR_PLAN; x += 2) {
        const chevauche = occupees.some(
          (t) => x < t.posX + t.largeur + marge && x + largeur + marge > t.posX &&
            y < t.posY + t.hauteur + marge && y + hauteur + marge > t.posY
        );
        if (!chevauche) {
          return { x, y };
        }
      }
    }
    return { x: 0, y: 0 };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
