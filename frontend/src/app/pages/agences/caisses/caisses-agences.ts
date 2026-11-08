import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CaissierAgence, EtatCaisses, SessionCaisseAgence } from '../../../core/models/agences';
import { Page } from '../../../core/models/models';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';
import { COUPURES_FCFA, SaisieBillettage, versBillettage } from '../../../shared/session-caisse';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/**
 * Caisses des caissiers d'une agence, comme au point de vente : le superviseur
 * ouvre la caisse en comptant le fond (pris sur la caisse generale) et la
 * ferme en comptant le tiroir, le montant attendu sous les yeux. Un ecart se
 * justifie ; l'argent compte revient a la caisse generale.
 */
@Component({
  selector: 'app-caisses-agences',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale, SaisieBillettage, SuiteListe],
  templateUrl: './caisses-agences.html',
  styleUrl: './caisses-agences.scss'
})
export class CaissesAgences implements OnInit {
  readonly service = inject(AgencesService);

  readonly etat = signal<EtatCaisses | null>(null);
  readonly historique = signal<Page<SessionCaisseAgence> | null>(null);
  private readonly suite = new SuiteDePages();
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurModale = signal<string | null>(null);
  readonly agences = computed(() => (this.service.moi()?.agences ?? []).filter((a) => a.actif));
  agenceId: number | null = null;

  readonly aOuvrir = signal<CaissierAgence | null>(null);
  readonly aFermer = signal<SessionCaisseAgence | null>(null);
  readonly detail = signal<SessionCaisseAgence | null>(null);
  readonly quantites = signal<Record<number, number | null>>({});
  justification = '';

  readonly totalCompte = computed(() =>
    COUPURES_FCFA.reduce((s, v) => s + v * Math.max(0, Math.floor(this.quantites()[v] ?? 0)), 0));
  readonly ecart = computed(() => this.totalCompte() - (this.aFermer()?.soldeCaisse ?? 0));

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (m) => {
        const actives = m.agences.filter((a) => a.actif);
        this.agenceId = actives[0]?.id ?? null;
        this.charger();
      }
    });
  }

  charger(): void {
    if (!this.agenceId) {
      return;
    }
    this.service.etatCaisses(this.agenceId).subscribe({
      next: (e) => this.etat.set(e),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des caisses impossible.'))
    });
    this.historique.set(null);
    this.chargerHistorique();
  }

  chargerHistorique(): void {
    this.service.sessionsCaisse(this.agenceId, this.suite.numero()).subscribe({
      next: (p) => this.historique.set(cumulerPage(this.historique(), p))
    });
  }

  suiteHistorique(): void {
    if (this.suite.demander(this.historique())) {
      this.chargerHistorique();
    }
  }

  changerAgence(): void {
    this.succes.set(null);
    this.charger();
  }

  demanderOuverture(c: CaissierAgence): void {
    this.quantites.set({});
    this.erreurModale.set(null);
    this.succes.set(null);
    this.aOuvrir.set(c);
  }

  ouvrir(): void {
    const c = this.aOuvrir();
    if (!c || !this.agenceId) {
      return;
    }
    this.service.ouvrirCaisse(this.agenceId, c.id, versBillettage(this.quantites())).subscribe({
      next: (s) => {
        this.aOuvrir.set(null);
        this.succes.set(`Caisse de ${s.caissierNom} ouverte (${s.numero}) avec un fond de ${s.fondInitial.toLocaleString('fr-FR')} F.`);
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Ouverture impossible.'))
    });
  }

  demanderFermeture(s: SessionCaisseAgence): void {
    this.quantites.set({});
    this.justification = '';
    this.erreurModale.set(null);
    this.succes.set(null);
    this.aFermer.set(s);
  }

  fermer(): void {
    const s = this.aFermer();
    if (!s) {
      return;
    }
    this.service.fermerCaisse(s.id, versBillettage(this.quantites()), this.justification.trim() || undefined).subscribe({
      next: (r) => {
        this.aFermer.set(null);
        const ecart = r.ecart ?? 0;
        this.succes.set(`Caisse de ${r.caissierNom} fermee : ${(r.montantCompte ?? 0).toLocaleString('fr-FR')} F remis a la caisse generale`
          + (ecart ? `, ecart de ${ecart > 0 ? '+' : ''}${ecart.toLocaleString('fr-FR')} F.` : ', sans ecart.'));
        this.charger();
      },
      error: (e) => this.erreurModale.set(this.message(e, 'Fermeture impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
