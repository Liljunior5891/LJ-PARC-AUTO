import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RapportPdfDisponible } from '../../core/models/pilotage';
import { Camion } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { PilotageService } from '../../core/services/pilotage.service';

/**
 * Menu Rapports (chapitre 35).
 *
 * On choisit un rapport dans le catalogue, une periode, et — pour les rapports
 * qui l'acceptent — un ou plusieurs camions ; le PDF est ensuite telecharge.
 * L'export CSV du pilotage reste disponible pour ceux qui retravaillent les
 * chiffres dans un tableur.
 */
@Component({
  selector: 'app-rapports',
  imports: [FormsModule],
  templateUrl: './rapports.html',
  styleUrl: './rapports.scss'
})
export class Rapports implements OnInit {
  private readonly service = inject(PilotageService);
  private readonly camionService = inject(CamionService);

  readonly catalogue = signal<RapportPdfDisponible[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly selection = signal<number[]>([]);
  readonly typeChoisi = signal<string | null>(null);
  readonly edition = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  debut = this.premierJourDeLAnnee();
  fin = this.aujourdhui();
  recherche = '';

  /** Le rapport selectionne, ou null tant que rien n'est choisi. */
  readonly rapport = computed(() =>
    this.catalogue().find((r) => r.valeur === this.typeChoisi()) ?? null
  );

  /** Vrai si l'ecran doit proposer la liste des camions. */
  readonly choixCamions = computed(() => this.rapport()?.selectionCamions ?? false);

  readonly camionsFiltres = computed(() => {
    const terme = this.recherche.trim().toLowerCase();
    if (!terme) {
      return this.camions();
    }
    return this.camions().filter((camion) =>
      `${camion.immatriculation} ${camion.marque ?? ''} ${camion.modele ?? ''}`
        .toLowerCase()
        .includes(terme)
    );
  });

  ngOnInit(): void {
    this.service.rapportsPdf().subscribe({
      next: (catalogue) => {
        this.catalogue.set(catalogue);
        this.typeChoisi.set(catalogue[0]?.valeur ?? null);
      },
      error: () => this.erreur.set('Chargement du catalogue des rapports impossible.')
    });
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content),
      error: () => this.erreur.set('Chargement des camions impossible.')
    });
  }

  choisir(rapport: RapportPdfDisponible): void {
    this.typeChoisi.set(rapport.valeur);
    this.succes.set(null);
  }

  basculer(camionId: number): void {
    const selection = this.selection();
    this.selection.set(
      selection.includes(camionId)
        ? selection.filter((id) => id !== camionId)
        : [...selection, camionId]
    );
  }

  estSelectionne(camionId: number): boolean {
    return this.selection().includes(camionId);
  }

  toutSelectionner(): void {
    this.selection.set(this.camionsFiltres().map((camion) => camion.id));
  }

  toutDeselectionner(): void {
    this.selection.set([]);
  }

  /** Telecharge le PDF et propose son enregistrement. */
  telecharger(): void {
    const rapport = this.rapport();
    if (!rapport || this.edition()) {
      return;
    }
    if (this.debut > this.fin) {
      this.erreur.set('La date de debut est posterieure a la date de fin.');
      return;
    }

    this.erreur.set(null);
    this.succes.set(null);
    this.edition.set(rapport.valeur);

    const camionIds = rapport.selectionCamions ? this.selection() : [];
    this.service.telechargerRapportPdf(rapport.valeur, this.debut, this.fin, camionIds).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const lien = document.createElement('a');
        lien.href = url;
        lien.download = `${rapport.valeur.toLowerCase()}_${this.debut}_${this.fin}.pdf`;
        lien.click();
        URL.revokeObjectURL(url);
        this.edition.set(null);
        this.succes.set(`${rapport.libelle} telecharge.`);
      },
      error: () => {
        this.edition.set(null);
        this.erreur.set("Edition du rapport impossible. Verifiez la periode demandee.");
      }
    });
  }

  /** Raccourcis de periode : le mois, le trimestre ou l'annee en cours. */
  periodeRapide(portee: 'mois' | 'trimestre' | 'annee'): void {
    const aujourdhui = new Date();
    const debut =
      portee === 'mois'
        ? new Date(aujourdhui.getFullYear(), aujourdhui.getMonth(), 1)
        : portee === 'trimestre'
          ? new Date(aujourdhui.getFullYear(), Math.floor(aujourdhui.getMonth() / 3) * 3, 1)
          : new Date(aujourdhui.getFullYear(), 0, 1);
    this.debut = this.iso(debut);
    this.fin = this.aujourdhui();
  }

  private aujourdhui(): string {
    return this.iso(new Date());
  }

  private premierJourDeLAnnee(): string {
    return this.iso(new Date(new Date().getFullYear(), 0, 1));
  }

  /** Date locale au format ISO : toISOString decalerait d'un jour. */
  private iso(date: Date): string {
    const mois = `${date.getMonth() + 1}`.padStart(2, '0');
    const jour = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${mois}-${jour}`;
  }
}
