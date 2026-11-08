import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LigneAudit } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

const OBJETS: { code: string; libelle: string }[] = [
  { code: 'TRANSACTION', libelle: 'Transactions' },
  { code: 'MOUVEMENT', libelle: 'Mouvements' },
  { code: 'SESSION_CAISSE', libelle: 'Caisses des caissiers' },
  { code: 'CLOTURE', libelle: 'Clotures' },
  { code: 'BAREME', libelle: 'Baremes' },
  { code: 'COMPTE', libelle: 'Comptes' },
  { code: 'ECRITURE', libelle: 'Soldes d\'ouverture' },
  { code: 'SERVICE', libelle: 'Services' },
  { code: 'TYPE_OPERATION', libelle: 'Operations' },
  { code: 'AGENCE', libelle: 'Agences' },
  { code: 'DROITS', libelle: 'Droits' },
  { code: 'REGLAGES', libelle: 'Reglages' }
];

/**
 * Journal d'audit des agences, non modifiable : qui a fait quoi, quand, avec
 * la valeur avant et apres. Le superviseur voit ses agences et le parametrage
 * commun ; l'administrateur voit tout.
 */
@Component({
  selector: 'app-audit-agences',
  imports: [DatePipe, FormsModule, SuiteListe],
  templateUrl: './audit-agences.html',
  styleUrl: './audit-agences.scss'
})
export class AuditAgences implements OnInit {
  readonly service = inject(AgencesService);
  readonly objets = OBJETS;

  readonly page = signal<Page<LigneAudit> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly erreur = signal<string | null>(null);
  readonly ouverte = signal<number | null>(null);

  f: { agenceId: number | null; du: string; au: string; objet: string; q: string } = { agenceId: null, du: '', au: '', objet: '', q: '' };

  ngOnInit(): void {
    this.service.chargerMoi().subscribe();
    this.charger();
  }

  charger(): void {
    this.service.audit(this.f, this.suite.numero()).subscribe({
      next: (p) => this.page.set(cumulerPage(this.page(), p)),
      error: (e) => this.erreur.set((e as { error?: { message?: string } })?.error?.message ?? 'Lecture du journal impossible.')
    });
  }

  filtrer(): void {
    this.page.set(null);
    this.charger();
  }

  suiteListe(): void {
    if (this.suite.demander(this.page())) {
      this.charger();
    }
  }

  basculer(id: number): void {
    this.ouverte.set(this.ouverte() === id ? null : id);
  }

  /** Valeur avant / apres lisible : JSON indente, sinon le texte tel quel. */
  lisible(texte?: string): string {
    if (!texte) {
      return '—';
    }
    try {
      return JSON.stringify(JSON.parse(texte), null, 2);
    } catch {
      return texte;
    }
  }
}
