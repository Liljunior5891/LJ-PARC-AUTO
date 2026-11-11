import { DecimalPipe } from '@angular/common';
import { Component, computed, input, model } from '@angular/core';
import { Formule, MODULES_FACTURABLES, PERIODICITES, ParametresAbonnement, Periodicite } from '../../core/models/abonnement';
import { CodeModule } from '../../core/models/models';
import { PRESENTATION_MODULES } from '../../core/models/plateforme';

/** Detail du montant, calcule comme le serveur (qui reste seul juge du montant facture). */
export interface Recapitulatif {
  lignes: { code: CodeModule; libelle: string; prix: number }[];
  mensualite: number;
  sousTotal: number;
  remisePourcent: number;
  remise: number;
  montant: number;
}

/**
 * Choix d'une formule, des modules et de la periodicite, avec le montant a
 * payer : chaque module coute son prix dans la formule choisie.
 */
@Component({
  selector: 'app-choix-formule',
  imports: [DecimalPipe],
  template: `
    <div class="formules">
      @for (f of formules(); track f.id) {
        <button type="button" class="formule" [class.choisie]="formuleId() === f.id" [class.vedette]="f.miseEnAvant"
                (click)="formuleId.set(f.id)" [attr.aria-pressed]="formuleId() === f.id">
          @if (f.miseEnAvant) { <span class="ruban">Conseillee</span> }
          <strong class="nom">{{ f.nom }}</strong>
          @if (modules().length) {
            <span class="prix">{{ totalPour(f) | number: '1.0-0' }} <small>{{ parametres().devise }} / mois</small></span>
            <span class="pour">pour {{ modules().length }} module(s)</span>
          } @else {
            <span class="prix">des {{ minimum(f) | number: '1.0-0' }} <small>{{ parametres().devise }} / mois</small></span>
          }
          @if (f.description) { <small class="desc">{{ f.description }}</small> }
          <ul>
            <li>{{ limite(f.maxUtilisateurs, 'Comptes') }}</li>
            <li>{{ limite(f.maxVehicules, 'Vehicules') }}</li>
            <li>{{ limite(f.maxAgences, 'Agences') }}</li>
            <li>{{ limite(f.maxCaisses, 'Caisses') }}</li>
            <li>{{ limite(f.maxEmployes, 'Employes pointes') }}</li>
          </ul>
        </button>
      } @empty {
        <p class="vide">Aucune formule n'est proposee pour le moment.</p>
      }
    </div>

    @if (formule(); as f) {
      <h4>Modules</h4>
      <div class="modules">
        @for (m of catalogue; track m.code) {
          <label class="module" [class.coche]="modules().includes(m.code)">
            <input type="checkbox" [checked]="modules().includes(m.code)" (change)="basculer(m.code)" />
            <span class="icone" aria-hidden="true">{{ presentation[m.code].icone }}</span>
            <span class="lib">{{ m.libelle }}</span>
            <span class="prix-module">{{ prixDe(f, m.code) | number: '1.0-0' }} / mois</span>
          </label>
        }
      </div>

      <h4>Duree payee d'avance</h4>
      <div class="periodes">
        @for (p of periodes; track p.code) {
          <button type="button" class="periode" [class.choisie]="mois() === null && periodicite() === p.code"
                  (click)="mois.set(null); periodicite.set(p.code)">
            {{ p.libelle }}
            @if (remise(p.code) > 0) { <small>-{{ remise(p.code) }} %</small> }
          </button>
        }
        @if (moisLibres()) {
          <label class="periode autre" [class.choisie]="mois() !== null">
            Autre durée
            <input type="number" min="1" max="36" step="1" [value]="mois() ?? ''" placeholder="—"
                   (input)="choisirMois($any($event.target).value)" aria-label="Nombre de mois" />
            mois
          </label>
        }
      </div>

      <div class="recap">
        @for (l of recap().lignes; track l.code) {
          <div><span>{{ l.libelle }} ({{ f.nom }})</span><span>{{ l.prix | number: '1.0-0' }} / mois</span></div>
        } @empty {
          <div class="doux"><span>Aucun module choisi</span></div>
        }
        <div class="separe"><span>{{ moisChoisis() }} mois × {{ recap().mensualite | number: '1.0-0' }}</span><span>{{ recap().sousTotal | number: '1.0-0' }}</span></div>
        @if (recap().remise > 0) {
          <div class="remise"><span>Remise {{ recap().remisePourcent }} %</span><span>-{{ recap().remise | number: '1.0-0' }}</span></div>
        }
        <div class="total"><span>A payer</span><strong>{{ recap().montant | number: '1.0-0' }} {{ parametres().devise }}</strong></div>
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    h4 { margin: 1rem 0 .5rem; font-size: .8rem; text-transform: uppercase; letter-spacing: .04em; color: var(--couleur-texte-doux); }
    .formules { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
    .formule { position: relative; display: flex; flex-direction: column; gap: 4px; text-align: left; padding: .9rem;
      border: 1.5px solid var(--couleur-bordure); border-radius: 12px; background: var(--couleur-surface); cursor: pointer; color: inherit; font: inherit; }
    .formule:hover { border-color: var(--couleur-primaire-clair); }
    .formule.choisie { border-color: var(--couleur-primaire); box-shadow: 0 0 0 3px rgba(15, 76, 129, .12); }
    .formule .nom { font-size: 1rem; }
    .formule .prix { font-size: 1.3rem; font-weight: 700; }
    .formule .prix small { font-size: .75rem; font-weight: 500; color: var(--couleur-texte-doux); }
    .formule .pour { font-size: .78rem; color: var(--couleur-primaire); font-weight: 600; }
    .formule .desc { color: var(--couleur-texte-doux); }
    .formule ul { margin: 4px 0 0; padding-left: 16px; font-size: .78rem; color: var(--couleur-texte-doux); }
    .ruban { position: absolute; top: -9px; right: 10px; background: var(--couleur-accent); color: #fff; font-size: .68rem;
      font-weight: 700; padding: 2px 8px; border-radius: 999px; }
    .modules { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 8px; }
    .module { display: flex; align-items: center; gap: 8px; padding: .55rem .7rem; border: 1px solid var(--couleur-bordure);
      border-radius: 10px; cursor: pointer; font-size: .88rem; }
    .module.coche { border-color: var(--couleur-primaire-clair); background: #f3f8fd; }
    .module .lib { flex: 1; }
    .prix-module { font-size: .75rem; font-weight: 600; color: var(--couleur-texte-doux); white-space: nowrap; }
    .module.coche .prix-module { color: var(--couleur-primaire); }
    .periodes { display: flex; flex-wrap: wrap; gap: 8px; }
    .periode { padding: .45rem .9rem; border: 1px solid var(--couleur-bordure); border-radius: 999px; background: var(--couleur-surface);
      cursor: pointer; font: inherit; color: inherit; }
    .periode small { color: var(--couleur-succes); font-weight: 700; margin-left: 4px; }
    .periode.choisie { border-color: var(--couleur-primaire); background: var(--couleur-primaire); color: #fff; }
    .periode.choisie small { color: #d6f5e3; }
    .periode.autre { display: inline-flex; align-items: center; gap: 6px; }
    .periode.autre input { width: 4.2rem; padding: .15rem .35rem; border: 1px solid var(--couleur-bordure); border-radius: 6px;
      font: inherit; color: var(--couleur-texte, #111); background: #fff; }
    .recap { margin-top: 1rem; border: 1px solid var(--couleur-bordure); border-radius: 10px; padding: .7rem .9rem; font-size: .88rem; }
    .recap > div { display: flex; justify-content: space-between; gap: 12px; padding: 3px 0; }
    .recap .separe { border-top: 1px dashed var(--couleur-bordure); margin-top: 4px; padding-top: 6px; }
    .recap .remise { color: var(--couleur-succes); }
    .recap .doux { color: var(--couleur-texte-doux); }
    .recap .total { border-top: 1px solid var(--couleur-bordure); margin-top: 4px; padding-top: 8px; font-size: 1rem; }
    .vide { color: var(--couleur-texte-doux); }
  `
})
export class ChoixFormule {
  readonly formules = input<Formule[]>([]);
  readonly parametres = input.required<ParametresAbonnement>();

  readonly formuleId = model<number | null>(null);
  readonly periodicite = model<Periodicite>('MENSUEL');
  readonly modules = model<CodeModule[]>([]);
  /** Console du super-administrateur : une duree libre en mois (1 a 36) est possible. */
  readonly moisLibres = input(false);
  /** Duree libre choisie ; nulle : la duree de la periodicite. */
  readonly mois = model<number | null>(null);

  readonly periodes = PERIODICITES;
  readonly catalogue = MODULES_FACTURABLES;
  readonly presentation = PRESENTATION_MODULES;

  readonly formule = computed(() => this.formules().find((f) => f.id === this.formuleId()) ?? null);
  readonly moisChoisis = computed(() => this.mois() ?? PERIODICITES.find((p) => p.code === this.periodicite())?.mois ?? 1);
  /** Remise : celle de la periodicite, ou de la tranche atteinte par une duree libre (comme le serveur). */
  readonly periodiciteRemise = computed<Periodicite>(() => {
    const m = this.mois();
    if (m === null) {
      return this.periodicite();
    }
    return m >= 12 ? 'ANNUEL' : m >= 6 ? 'SEMESTRIEL' : m >= 3 ? 'TRIMESTRIEL' : 'MENSUEL';
  });

  readonly recap = computed<Recapitulatif>(() => {
    const f = this.formule();
    const choisis = this.modules();
    const lignes = this.catalogue
      .filter((m) => choisis.includes(m.code))
      .map((m) => ({ code: m.code, libelle: m.libelle, prix: f ? this.prixDe(f, m.code) : 0 }));
    const mensualite = lignes.reduce((s, l) => s + l.prix, 0);
    const sousTotal = mensualite * this.moisChoisis();
    const remisePourcent = this.remise(this.periodiciteRemise());
    const remise = Math.round((sousTotal * remisePourcent) / 100);
    return { lignes, mensualite, sousTotal, remisePourcent, remise, montant: sousTotal - remise };
  });

  prixDe(f: Formule, m: CodeModule): number {
    return Number(f.prix?.[m] ?? 0);
  }

  /** Prix mensuel de la formule pour les modules choisis. */
  totalPour(f: Formule): number {
    return this.modules().reduce((s, m) => s + this.prixDe(f, m), 0);
  }

  /** Module le moins cher de la formule, pour le « des ... ». */
  minimum(f: Formule): number {
    const prix = this.catalogue.map((m) => this.prixDe(f, m.code));
    return prix.length ? Math.min(...prix) : 0;
  }

  remise(p: Periodicite): number {
    const r = this.parametres();
    return p === 'TRIMESTRIEL' ? r.remiseTrimestrielle : p === 'SEMESTRIEL' ? r.remiseSemestrielle
      : p === 'ANNUEL' ? r.remiseAnnuelle : 0;
  }

  limite(n: number | null | undefined, libelle: string): string {
    return `${libelle} : ${n == null ? 'illimite' : n}`;
  }

  choisirMois(valeur: string): void {
    const n = Math.round(Number(valeur));
    this.mois.set(valeur === '' || !Number.isFinite(n) ? null : Math.min(36, Math.max(1, n)));
  }

  basculer(m: CodeModule): void {
    this.modules.update((liste) => (liste.includes(m) ? liste.filter((x) => x !== m) : [...liste, m]));
  }
}
