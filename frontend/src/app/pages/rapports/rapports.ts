import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RapportPdfDisponible } from '../../core/models/pilotage';
import { Camion } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { PilotageService } from '../../core/services/pilotage.service';

/**
 * Menu Rapports (chapitre 35).
 *
 * On choisit un rapport dans le catalogue, une periode, et — pour les rapports
 * qui l'acceptent — un ou plusieurs camions. Le PDF s'affiche d'abord dans
 * l'ecran : on ne telecharge qu'apres avoir vu ce que le rapport contient.
 * L'export CSV du pilotage reste disponible pour ceux qui retravaillent les
 * chiffres dans un tableur.
 */
@Component({
  selector: 'app-rapports',
  imports: [FormsModule],
  templateUrl: './rapports.html',
  styleUrl: './rapports.scss'
})
export class Rapports implements OnInit, OnDestroy {
  private readonly service = inject(PilotageService);
  private readonly camionService = inject(CamionService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly catalogue = signal<RapportPdfDisponible[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly selection = signal<number[]>([]);
  readonly typeChoisi = signal<string | null>(null);
  readonly edition = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  /** Le PDF actuellement affiche, avec les parametres qui l'ont produit. */
  readonly apercu = signal<Apercu | null>(null);
  /** Vrai quand les parametres ont bouge depuis l'edition de l'apercu. */
  readonly apercuPerime = signal(false);

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
      error: () => this.erreur.set('Chargement des vehicules impossible.')
    });
  }

  ngOnDestroy(): void {
    this.libererApercu();
  }

  choisir(rapport: RapportPdfDisponible): void {
    if (this.typeChoisi() === rapport.valeur) {
      return;
    }
    this.typeChoisi.set(rapport.valeur);
    this.succes.set(null);
    // Un autre rapport n'a rien a voir avec celui affiche : on repart a zero.
    this.libererApercu();
    this.apercu.set(null);
    this.apercuPerime.set(false);
  }

  basculer(camionId: number): void {
    const selection = this.selection();
    this.selection.set(
      selection.includes(camionId)
        ? selection.filter((id) => id !== camionId)
        : [...selection, camionId]
    );
    this.marquerPerime();
  }

  estSelectionne(camionId: number): boolean {
    return this.selection().includes(camionId);
  }

  toutSelectionner(): void {
    this.selection.set(this.camionsFiltres().map((camion) => camion.id));
    this.marquerPerime();
  }

  toutDeselectionner(): void {
    this.selection.set([]);
    this.marquerPerime();
  }

  /** L'apercu affiche ne correspond plus aux parametres a l'ecran. */
  marquerPerime(): void {
    if (this.apercu()) {
      this.apercuPerime.set(true);
    }
  }

  /**
   * Edite le rapport et l'affiche dans l'ecran.
   *
   * Le PDF est garde en memoire : le telechargement qui suit reutilise ce
   * fichier au lieu d'en redemander un au serveur.
   */
  previsualiser(): void {
    const rapport = this.rapport();
    if (!rapport || this.edition() || !this.periodeValide()) {
      this.enchainerTelechargement = false;
      return;
    }

    this.erreur.set(null);
    this.succes.set(null);
    this.edition.set(true);

    const camionIds = rapport.selectionCamions ? this.selection() : [];
    this.service.telechargerRapportPdf(rapport.valeur, this.debut, this.fin, camionIds).subscribe({
      next: (fichier) => {
        this.libererApercu();
        // #toolbar=0 masque la barre du lecteur : l'ecran porte deja ses boutons.
        const url = URL.createObjectURL(fichier);
        this.apercu.set({
          fichier,
          url,
          urlSure: this.sanitizer.bypassSecurityTrustResourceUrl(`${url}#toolbar=0&view=FitH`),
          libelle: rapport.libelle,
          nomFichier: `${rapport.valeur.toLowerCase()}_${this.debut}_${this.fin}.pdf`,
          taille: fichier.size
        });
        this.apercuPerime.set(false);
        this.edition.set(false);
        if (this.enchainerTelechargement) {
          this.enchainerTelechargement = false;
          this.enregistrer(this.apercu()!);
        }
      },
      error: () => {
        this.edition.set(false);
        this.enchainerTelechargement = false;
        this.erreur.set("Edition du rapport impossible. Verifiez la periode demandee.");
      }
    });
  }

  /** Enregistre le PDF affiche ; sans apercu, il est edite puis enregistre. */
  telecharger(): void {
    const apercu = this.apercu();
    if (!apercu || this.apercuPerime()) {
      this.enchainerTelechargement = true;
      this.previsualiser();
      return;
    }
    this.enregistrer(apercu);
  }

  /** Ouvre le PDF dans un onglet, pour le lire en plein ecran ou l'imprimer. */
  ouvrirDansUnOnglet(): void {
    const apercu = this.apercu();
    if (apercu) {
      window.open(apercu.url, '_blank');
    }
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
    this.marquerPerime();
  }

  /** Poids du PDF, pour situer l'ampleur du document avant de l'enregistrer. */
  poids(octets: number): string {
    return octets < 1024 * 1024
      ? `${Math.round(octets / 1024)} Ko`
      : `${(octets / 1024 / 1024).toFixed(1)} Mo`;
  }

  /** Vrai tant que le telechargement demande attend son edition. */
  private enchainerTelechargement = false;

  private enregistrer(apercu: Apercu): void {
    const lien = document.createElement('a');
    lien.href = apercu.url;
    lien.download = apercu.nomFichier;
    lien.click();
    this.succes.set(`${apercu.libelle} telecharge.`);
  }

  private periodeValide(): boolean {
    if (this.debut > this.fin) {
      this.erreur.set('La date de debut est posterieure a la date de fin.');
      return false;
    }
    return true;
  }

  /** Un blob non revoque garde sa memoire jusqu'au rechargement de la page. */
  private libererApercu(): void {
    const apercu = this.apercu();
    if (apercu) {
      URL.revokeObjectURL(apercu.url);
    }
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

/** Le PDF affiche et ce qu'il faut pour le relire ou l'enregistrer. */
interface Apercu {
  fichier: Blob;
  url: string;
  urlSure: SafeResourceUrl;
  libelle: string;
  nomFichier: string;
  taille: number;
}
