import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, LOCALE_ID, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  FicheSynthese,
  Kpi,
  Notification,
  RapportDisponible
} from '../../core/models/pilotage';
import { LigneRentabilite } from '../../core/models/finance';
import { Camion } from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { PilotageService } from '../../core/services/pilotage.service';
import { RentabiliteService } from '../../core/services/rentabilite.service';
import { grosMontant } from '../../shared/montant';
import { Periode, aujourdhui, dateIso, debutPeriode, estPeriode } from '../../shared/periode';

/** Un constat affiche dans la colonne « ce qui va » ou « ce qui coince ». */
interface Constat {
  titre: string;
  detail: string;
  lien?: string;
}

/**
 * Seuils de lecture des indicateurs. Ils ne changent rien aux calculs : ils
 * decident seulement de quel cote de l'ecran un indicateur est presente.
 */
const SEUIL_DISPONIBILITE_BONNE = 85;
const SEUIL_DISPONIBILITE_FAIBLE = 70;
const SEUIL_UTILISATION_BONNE = 60;
const SEUIL_UTILISATION_FAIBLE = 40;
const SEUIL_MARGE_BONNE = 15;
/** Circonference de l'anneau de marge (rayon 50). */
const CIRCONFERENCE_MARGE = 2 * Math.PI * 50;

@Component({
  selector: 'app-direction',
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './direction.html',
  styleUrl: './direction.scss'
})
export class Direction implements OnInit {
  private readonly service = inject(PilotageService);
  private readonly camionService = inject(CamionService);
  private readonly rentabiliteService = inject(RentabiliteService);
  private readonly locale = inject(LOCALE_ID);

  readonly kpi = signal<Kpi | null>(null);
  /** Meme duree, juste avant la periode affichee : sert aux ecarts. */
  readonly kpiPrecedent = signal<Kpi | null>(null);
  readonly lignesCamions = signal<LigneRentabilite[]>([]);
  readonly notifications = signal<Notification[]>([]);
  readonly rapports = signal<RapportDisponible[]>([]);
  readonly camions = signal<Camion[]>([]);
  readonly fiche = signal<FicheSynthese | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  debut = debutPeriode('mois');
  fin = aujourdhui();
  camionSynthese?: number;

  // --- Lecture de la periode ---------------------------------------------

  readonly critiques = computed(() =>
    this.notifications().filter((n) => n.niveau === 'CRITIQUE')
  );


  /** Camions dont les depenses depassent les recettes sur la periode. */
  readonly camionsEnPerte = computed(() =>
    this.lignesCamions()
      .filter((ligne) => ligne.resultat < 0)
      .sort((a, b) => a.resultat - b.resultat)
  );

  /** Camions du plus rentable au moins rentable : le classement de la periode. */
  readonly classementCamions = computed(() =>
    [...this.lignesCamions()].sort((a, b) => b.resultat - a.resultat)
  );

  /** Plus grand ecart a zero, gain ou perte : il fixe l'echelle des barres. */
  private readonly resultatExtreme = computed(() =>
    Math.max(1, ...this.lignesCamions().map((ligne) => Math.abs(ligne.resultat)))
  );

  /** Ecart de resultat avec la periode precedente, en pourcentage. */
  readonly evolutionResultat = computed(() => {
    const actuel = this.kpi()?.resultat;
    const precedent = this.kpiPrecedent()?.resultat;
    if (actuel === undefined || precedent === undefined || precedent === 0) {
      return null;
    }
    return ((actuel - precedent) / Math.abs(precedent)) * 100;
  });

  /** Ecart de marge avec la periode precedente, en points. */
  readonly evolutionMarge = computed(() => {
    const actuel = this.kpi()?.margePourcent;
    const precedent = this.kpiPrecedent()?.margePourcent;
    return actuel === undefined || precedent === undefined ? null : actuel - precedent;
  });

  /** Phrase de synthese, deduite du resultat et des camions en perte. */
  readonly verdict = computed(() => {
    const k = this.kpi();
    if (!k) {
      return '';
    }
    const enPerte = this.camionsEnPerte().length;
    if (k.resultat < 0) {
      return enPerte > 0
        ? `Le parc est en perte sur la periode ; ${enPerte} vehicule(s) y contribuent directement.`
        : 'Le parc est en perte sur la periode, sans qu\'un vehicule en particulier se detache.';
    }
    if (k.resultat === 0) {
      return 'Le parc est a l\'equilibre sur la periode.';
    }
    return enPerte > 0
      ? `Le parc est rentable, mais ${enPerte} vehicule(s) sont en perte et tirent le resultat vers le bas.`
      : 'Le parc est rentable sur la periode, et aucun vehicule n\'est en perte.';
  });

  /** Ce qui se passe bien : uniquement les indicateurs au-dessus des seuils. */
  readonly pointsForts = computed<Constat[]>(() => {
    const k = this.kpi();
    if (!k) {
      return [];
    }
    const constats: Constat[] = [];
    if (k.tauxDisponibilitePourcent >= SEUIL_DISPONIBILITE_BONNE) {
      constats.push({
        titre: `${this.arrondi(k.tauxDisponibilitePourcent)} % du parc est pret a partir`,
        detail: `${k.camionsActifs} vehicules exploitables sur ${k.nombreCamions}`,
        lien: '/vue-parc'
      });
    }
    if (k.tauxUtilisationPourcent >= SEUIL_UTILISATION_BONNE) {
      constats.push({
        titre: `Les vehicules roulent ${this.arrondi(k.tauxUtilisationPourcent)} % du temps`,
        detail: `${k.nombreMissions} mission(s) terminee(s) sur la periode`,
        lien: '/missions'
      });
    }
    if (k.margePourcent >= SEUIL_MARGE_BONNE) {
      constats.push({
        titre: `Marge de ${this.arrondi(k.margePourcent)} %`,
        detail: `il reste ${this.arrondi(k.resultat)} FCFA une fois les charges payees`,
        lien: '/rentabilite'
      });
    }
    if (k.revenuParKm > k.coutParKm && k.coutParKm > 0) {
      constats.push({
        titre: `Chaque kilometre rapporte plus qu'il ne coute`,
        detail: `${this.arrondi(k.revenuParKm)} FCFA gagnes contre ${this.arrondi(k.coutParKm)} depenses`,
        lien: '/rentabilite'
      });
    }
    if (this.critiques().length === 0 && this.notifications().length > 0) {
      constats.push({
        titre: 'Aucune alerte critique en attente',
        detail: `${this.notifications().length} point(s) de vigilance sans urgence`
      });
    }
    return constats;
  });

  /** Ce qui reclame une correction : indicateurs sous les seuils. */
  readonly pointsFaibles = computed<Constat[]>(() => {
    const k = this.kpi();
    if (!k) {
      return [];
    }
    const constats: Constat[] = [];
    const enPerte = this.camionsEnPerte();
    if (enPerte.length > 0) {
      constats.push({
        titre: `${enPerte.length} vehicule(s) en perte`,
        detail: enPerte
          .slice(0, 3)
          .map((l) => `${l.immatriculation} (${this.arrondi(l.resultat)})`)
          .join(', '),
        lien: '/rentabilite'
      });
    }
    if (k.tauxDisponibilitePourcent < SEUIL_DISPONIBILITE_FAIBLE) {
      constats.push({
        titre: `Seulement ${this.arrondi(k.tauxDisponibilitePourcent)} % du parc est disponible`,
        detail: `${k.joursImmobilisation} jour(s) d'immobilisation sur la periode`,
        lien: '/maintenance'
      });
    }
    if (k.tauxUtilisationPourcent < SEUIL_UTILISATION_FAIBLE) {
      constats.push({
        titre: `Les vehicules ne roulent que ${this.arrondi(k.tauxUtilisationPourcent)} % du temps`,
        detail: 'des vehicules disponibles restent sans mission',
        lien: '/affectations'
      });
    }
    if (k.coutParKm >= k.revenuParKm && k.revenuParKm > 0) {
      constats.push({
        titre: 'Le kilometre coute autant qu\'il rapporte',
        detail: `${this.arrondi(k.coutParKm)} FCFA de cout pour ${this.arrondi(k.revenuParKm)} de revenu`,
        lien: '/depenses'
      });
    }
    if (this.critiques().length > 0) {
      constats.push({
        titre: `${this.critiques().length} alerte(s) critique(s)`,
        detail: "a traiter depuis la cloche des notifications, en haut de l'ecran"
      });
    }
    return constats;
  });

  ngOnInit(): void {
    this.charger();
    this.service.rapports().subscribe({ next: (rapports) => this.rapports.set(rapports) });
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service.kpi(this.debut, this.fin).subscribe({
      next: (kpi) => {
        this.kpi.set(kpi);
        this.chargement.set(false);
        this.erreur.set(null);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement des indicateurs impossible.');
      }
    });

    // Periode precedente de meme duree : sans elle, un chiffre isole ne dit pas
    // si la situation s'ameliore ou se degrade.
    const precedente = this.periodePrecedente();
    this.service.kpi(precedente.debut, precedente.fin).subscribe({
      next: (kpi) => this.kpiPrecedent.set(kpi),
      error: () => this.kpiPrecedent.set(null)
    });

    this.rentabiliteService.global(this.debut, this.fin).subscribe({
      next: (global) => this.lignesCamions.set(global.camions),
      error: () => this.lignesCamions.set([])
    });

    this.service.notifications().subscribe({
      next: (notifications) => this.notifications.set(notifications)
    });

    if (this.camionSynthese) {
      this.afficherSynthese();
    }
  }

  afficherSynthese(): void {
    if (!this.camionSynthese) {
      this.fiche.set(null);
      return;
    }
    this.service.synthese(this.camionSynthese, this.debut, this.fin).subscribe({
      next: (fiche) => this.fiche.set(fiche),
      error: () => this.erreur.set('Chargement de la fiche synthetique impossible.')
    });
  }

  /** Telecharge le rapport et propose son enregistrement. */
  exporter(type: RapportDisponible): void {
    this.erreur.set(null);
    this.service.telechargerRapport(type.valeur, this.debut, this.fin).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const lien = document.createElement('a');
        lien.href = url;
        lien.download = `${type.valeur.toLowerCase()}_${this.debut}_${this.fin}.csv`;
        lien.click();
        URL.revokeObjectURL(url);
        this.succes.set(`${type.libelle} exporte.`);
      },
      error: () => this.erreur.set("Export impossible.")
    });
  }

  /** Cale la periode sur le mois, le trimestre ou l'annee en cours, puis recharge. */
  choisirPeriode(periode: Periode): void {
    this.debut = debutPeriode(periode);
    this.fin = aujourdhui();
    this.charger();
  }

  estPeriode(periode: Periode): boolean {
    return estPeriode(periode, this.debut, this.fin);
  }

  /** Montant du bandeau, lisible en grand corps. */
  grosMontant(valeur: number): string {
    return grosMontant(valeur, this.locale);
  }

  /** Arc de l'anneau de marge, borne entre 0 et 100 %. */
  arcMarge(marge: number): string {
    const longueur = (this.borne(marge) / 100) * CIRCONFERENCE_MARGE;
    return `${longueur} ${CIRCONFERENCE_MARGE}`;
  }

  /** Pourcentage ramene entre 0 et 100, pour les jauges. */
  borne(valeur: number): number {
    return Math.min(100, Math.max(0, valeur));
  }

  /** Longueur d'une barre du classement, dans sa moitie d'axe. */
  largeurResultat(resultat: number): number {
    return Math.max(2, (Math.abs(resultat) / this.resultatExtreme()) * 100);
  }

  /** Periode de meme duree se terminant la veille du debut affiche. */
  private periodePrecedente(): { debut: string; fin: string } {
    const debut = new Date(this.debut);
    const fin = new Date(this.fin);
    const jours = Math.max(1, Math.round((fin.getTime() - debut.getTime()) / 86_400_000) + 1);
    const finPrecedente = new Date(debut.getTime() - 86_400_000);
    const debutPrecedent = new Date(finPrecedente.getTime() - (jours - 1) * 86_400_000);
    return { debut: dateIso(debutPrecedent), fin: dateIso(finPrecedente) };
  }

  private arrondi(valeur: number): string {
    return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 }).format(valeur);
  }
}
