import { KeyValuePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RapportAgenceCatalogue } from '../../../core/models/agences';
import { FormatColonne, Rapport } from '../../../core/models/rapport';
import { AgencesService } from '../../../core/services/agences.service';
import { enregistrerFichier } from '../../../core/services/rapport.service';
import { dateIso, datesDePeriode } from '../../../shared/periodes';

type Periodicite = 'JOUR' | 'SEMAINE' | 'MOIS' | 'ANNEE' | 'LIBRE';

/**
 * Rapports des agences : journalier, hebdomadaire, mensuel ou annuel (ou
 * periode libre), affiches puis exportes en Excel, PDF ou CSV a l'identique.
 * Chaque export porte l'agence, la periode et la date de generation, et
 * respecte les droits du profil.
 */
@Component({
  selector: 'app-rapports-agences',
  imports: [KeyValuePipe, FormsModule],
  templateUrl: './rapports-agences.html',
  styleUrl: './rapports-agences.scss'
})
export class RapportsAgences implements OnInit {
  readonly service = inject(AgencesService);

  readonly catalogue = signal<RapportAgenceCatalogue[]>([]);
  readonly rapport = signal<Rapport | null>(null);
  readonly chargement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly exportEnCours = signal<string | null>(null);
  readonly autorises = computed(() => this.catalogue().filter((r) => r.autorise));

  type = '';
  periodicite: Periodicite = 'MOIS';
  du = datesDePeriode('CE_MOIS').du;
  au = datesDePeriode('CE_MOIS').au;
  agenceId: number | null = null;

  ngOnInit(): void {
    this.service.chargerMoi().subscribe();
    this.service.rapportsCatalogue().subscribe({
      next: (c) => {
        this.catalogue.set(c);
        this.type = c.find((r) => r.autorise)?.code ?? '';
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Lecture des rapports impossible.'))
    });
  }

  choisir(code: string): void {
    this.type = code;
    this.charger();
  }

  changerPeriodicite(): void {
    const d = new Date();
    switch (this.periodicite) {
      case 'JOUR':
        this.du = this.au = dateIso(d);
        break;
      case 'SEMAINE':
        ({ du: this.du, au: this.au } = datesDePeriode('CETTE_SEMAINE'));
        break;
      case 'MOIS':
        ({ du: this.du, au: this.au } = datesDePeriode('CE_MOIS'));
        break;
      case 'ANNEE':
        ({ du: this.du, au: this.au } = datesDePeriode('CETTE_ANNEE'));
        break;
    }
    this.charger();
  }

  charger(): void {
    if (!this.type) {
      return;
    }
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.rapport(this.type, { agenceId: this.agenceId, du: this.du, au: this.au }).subscribe({
      next: (r) => {
        this.rapport.set(r);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.rapport.set(null);
        this.erreur.set(this.message(e, 'Rapport impossible.'));
      }
    });
  }

  exporter(format: 'xlsx' | 'pdf' | 'csv'): void {
    this.exportEnCours.set(format);
    this.service.exporterRapport(this.type, format, { agenceId: this.agenceId, du: this.du, au: this.au }).subscribe({
      next: (f) => {
        this.exportEnCours.set(null);
        enregistrerFichier(f);
      },
      error: (e) => {
        this.exportEnCours.set(null);
        this.erreur.set(this.message(e, 'Export impossible.'));
      }
    });
  }

  numerique(format: FormatColonne): boolean {
    return format === 'NOMBRE' || format === 'MONTANT' || format === 'POURCENT';
  }

  /** Valeur affichee selon le format de la colonne. */
  texte(valeur: unknown, format: FormatColonne): string {
    if (valeur === null || valeur === undefined || valeur === '') {
      return '';
    }
    switch (format) {
      case 'MONTANT':
        return Number(valeur).toLocaleString('fr-FR') + ' F';
      case 'NOMBRE':
        return Number(valeur).toLocaleString('fr-FR');
      case 'POURCENT':
        return Number(valeur).toLocaleString('fr-FR', { maximumFractionDigits: 2 }) + ' %';
      case 'DATE':
        return new Date(String(valeur)).toLocaleDateString('fr-FR');
      case 'DATE_HEURE':
        return new Date(String(valeur)).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
      default:
        return String(valeur);
    }
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
