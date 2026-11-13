import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MODULES_FACTURABLES } from '../../core/models/abonnement';
import { CodeModule } from '../../core/models/models';
import {
  EtatPlateformeService,
  MaintenancePlateforme,
  SaisieMaintenance,
  StatutMaintenance,
  VersionPlateforme
} from '../../core/services/etat-plateforme.service';
import { Modale } from '../../shared/modale';

type Vue = 'maintenances' | 'versions';

const LIBELLES: Record<StatutMaintenance, string> = {
  PROGRAMMEE: 'Programmee',
  ANNONCEE: 'Annoncee',
  EN_COURS: 'En cours',
  TERMINEE: 'Terminee',
  ANNULEE: 'Annulee'
};

/** Date et heure locales au format d'un champ datetime-local. */
function local(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function iso(valeur: string): string {
  return new Date(valeur).toISOString();
}

interface FormMaintenance {
  message: string;
  modules: Record<CodeModule, boolean>;
  debut: string;
  finPrevue: string;
  annonceHeures: number | null;
}

interface FormVersion {
  numero: string;
  titre: string;
  notes: string;
  rechargement: boolean;
  publieeLe: string;
}

/**
 * Console de l'exploitant : maintenances programmees module par module (annonce,
 * blocage, fin anticipee) et notes de version publiees aux utilisateurs.
 */
@Component({
  selector: 'app-maintenance-plateforme',
  imports: [DatePipe, FormsModule, Modale],
  template: `
    <div class="entete-page">
      <div>
        <h1>{{ vue() === 'maintenances' ? 'Maintenances' : 'Versions' }}</h1>
        <p class="sous-titre">
          @if (vue() === 'maintenances') {
            Pendant une maintenance, les ecrans des modules choisis sont fermes a toutes les entreprises. Les agences continuent hors ligne.
          } @else {
            Notes de version montrees une fois a chaque utilisateur ; les navigateurs ouverts sur une version plus ancienne sont invites a recharger.
          }
        </p>
      </div>
      <div class="actions-entete">
        <button class="bouton secondaire" type="button" (click)="charger()">Actualiser</button>
        @if (vue() === 'maintenances') {
          <button class="bouton" type="button" (click)="nouvelleMaintenance()">Programmer une maintenance</button>
        } @else {
          <button class="bouton" type="button" (click)="nouvelleVersion()">Publier une version</button>
        }
      </div>
    </div>

    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (succes()) { <div class="message succes">{{ succes() }}</div> }

    @if (vue() === 'maintenances') {
      <section class="carte">
        <div class="tableau-conteneur">
          <table class="tableau">
            <thead>
              <tr><th>Statut</th><th>Modules</th><th>Plage</th><th>Annonce</th><th>Message</th><th></th></tr>
            </thead>
            <tbody>
              @for (m of maintenances(); track m.id) {
                <tr>
                  <td><span class="statut" [class]="'statut ' + m.statut.toLowerCase()">{{ libelles[m.statut] }}</span></td>
                  <td>{{ modulesDe(m) }}</td>
                  <td>{{ m.debut | date: 'dd/MM/yyyy HH:mm' }}<br /><small>au {{ (m.termineeLe ?? m.finPrevue) | date: 'dd/MM/yyyy HH:mm' }}
                    @if (m.termineeLe) { (terminee plus tot) }</small></td>
                  <td>{{ m.annonceLe | date: 'dd/MM HH:mm' }}</td>
                  <td class="message-cellule">{{ m.message }}<br /><small>par {{ m.creePar }}</small></td>
                  <td class="actions-ligne">
                    @if (m.statut === 'EN_COURS') {
                      <button class="bouton petit" type="button" (click)="terminer(m)" [disabled]="envoi()">Terminer maintenant</button>
                      <button class="bouton petit secondaire" type="button" (click)="editerMaintenance(m)">Prolonger</button>
                    } @else if (m.statut === 'PROGRAMMEE' || m.statut === 'ANNONCEE') {
                      <button class="bouton petit secondaire" type="button" (click)="editerMaintenance(m)">Modifier</button>
                      <button class="bouton petit danger" type="button" (click)="annuler(m)" [disabled]="envoi()">Annuler</button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6"><div class="message vide">{{ chargement() ? 'Chargement...' : 'Aucune maintenance programmee.' }}</div></td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    } @else {
      <section class="versions">
        @for (v of versions(); track v.id) {
          <article class="carte version">
            <header>
              <div>
                <h3>{{ v.numero }} — {{ v.titre }}</h3>
                <small>{{ futur(v) ? 'Publication prevue le' : 'Publiee le' }} {{ v.publieeLe | date: 'dd/MM/yyyy HH:mm' }} par {{ v.creePar }}
                  · {{ v.rechargement ? 'rechargement propose' : 'sans rechargement' }}</small>
              </div>
              <div class="actions-ligne">
                <button class="bouton petit secondaire" type="button" (click)="editerVersion(v)">Modifier</button>
                <button class="bouton petit danger" type="button" (click)="supprimer(v)" [disabled]="envoi()">Supprimer</button>
              </div>
            </header>
            <div class="notes">{{ v.notes }}</div>
          </article>
        } @empty {
          <div class="carte"><div class="message vide">{{ chargement() ? 'Chargement...' : 'Aucune version publiee.' }}</div></div>
        }
      </section>
    }

    @if (formMaintenance(); as f) {
      <app-modale [titre]="maintenanceEditee() ? 'Modifier la maintenance' : 'Programmer une maintenance'" largeur="640px"
                  [erreur]="erreur()" (ferme)="formMaintenance.set(null)">
        <form (ngSubmit)="enregistrerMaintenance(f)">
          <fieldset class="champ">
            <legend>Modules concernes *</legend>
            <div class="modules">
              @for (m of modules; track m.code) {
                <label class="case"><input type="checkbox" [name]="'module-' + m.code" [(ngModel)]="f.modules[m.code]" /> {{ m.libelle }}</label>
              }
            </div>
          </fieldset>
          <div class="grille-2">
            <div class="champ">
              <label for="m-debut">Debut *</label>
              <input id="m-debut" name="debut" type="datetime-local" [(ngModel)]="f.debut" required />
            </div>
            <div class="champ">
              <label for="m-fin">Fin prevue *</label>
              <input id="m-fin" name="finPrevue" type="datetime-local" [(ngModel)]="f.finPrevue" required />
            </div>
          </div>
          <div class="champ">
            <label for="m-annonce">Annoncer combien d'heures avant ?</label>
            <input id="m-annonce" name="annonceHeures" type="number" min="0" max="720" step="1" [(ngModel)]="f.annonceHeures" />
            <small>24 par defaut. 0 : pas d'annonce prealable (maintenance d'urgence).</small>
          </div>
          <div class="champ">
            <label for="m-message">Message aux utilisateurs *</label>
            <textarea id="m-message" name="message" rows="3" maxlength="500" [(ngModel)]="f.message" required
                      placeholder="Ex. : mise a jour du point de vente, les caisses rouvrent a 23h."></textarea>
          </div>
          <p class="aide">La maintenance s'arrete d'elle-meme a l'heure prevue ; vous pouvez la terminer plus tot ou la prolonger.</p>
          <div class="actions-formulaire">
            <button class="bouton secondaire" type="button" (click)="formMaintenance.set(null)">Annuler</button>
            <button class="bouton" type="submit" [disabled]="envoi()">{{ maintenanceEditee() ? 'Enregistrer' : 'Programmer' }}</button>
          </div>
        </form>
      </app-modale>
    }

    @if (formVersion(); as f) {
      <app-modale [titre]="versionEditee() ? 'Modifier la version' : 'Publier une version'" largeur="700px"
                  [erreur]="erreur()" (ferme)="formVersion.set(null)">
        <form (ngSubmit)="enregistrerVersion(f)">
          <div class="grille-2">
            <div class="champ">
              <label for="v-numero">Numero *</label>
              <input id="v-numero" name="numero" maxlength="30" [(ngModel)]="f.numero" required placeholder="2.4.0" />
            </div>
            <div class="champ">
              <label for="v-date">Publication</label>
              <input id="v-date" name="publieeLe" type="datetime-local" [(ngModel)]="f.publieeLe" />
              <small>Vide : tout de suite.</small>
            </div>
          </div>
          <div class="champ">
            <label for="v-titre">Titre *</label>
            <input id="v-titre" name="titre" maxlength="150" [(ngModel)]="f.titre" required placeholder="Fonds de reserve et maintenance" />
          </div>
          <div class="champ">
            <label for="v-notes">Nouveautes et corrections *</label>
            <textarea id="v-notes" name="notes" rows="10" [(ngModel)]="f.notes" required
                      placeholder="- Nouveau : ...&#10;- Correction : ..."></textarea>
          </div>
          <label class="case"><input type="checkbox" name="rechargement" [(ngModel)]="f.rechargement" />
            Inviter les navigateurs ouverts a recharger la page</label>
          <div class="actions-formulaire">
            <button class="bouton secondaire" type="button" (click)="formVersion.set(null)">Annuler</button>
            <button class="bouton" type="submit" [disabled]="envoi()">{{ versionEditee() ? 'Enregistrer' : 'Publier' }}</button>
          </div>
        </form>
      </app-modale>
    }
  `,
  styles: `
    .actions-entete { display: flex; flex-wrap: wrap; gap: .5rem; }
    .actions-ligne { display: flex; flex-wrap: wrap; gap: .35rem; justify-content: flex-end; }
    .message-cellule { max-width: 360px; }
    .statut { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: .78rem; font-weight: 600;
      background: #eef1f5; color: #4a5361; }
    .statut.en_cours { background: #fdecec; color: #8b1c1c; }
    .statut.annoncee { background: #fdf4e1; color: #6b4a05; }
    .statut.programmee { background: #e8f1fd; color: #12407a; }
    .modules { display: flex; flex-wrap: wrap; gap: .4rem 1rem; }
    .case { display: inline-flex; align-items: center; gap: .4rem; cursor: pointer; }
    fieldset.champ { border: 0; padding: 0; margin: 0 0 1rem; }
    .grille-2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 0 1rem; }
    .aide { color: var(--couleur-texte-doux); font-size: .85rem; }
    .versions { display: flex; flex-direction: column; gap: 1rem; }
    .version header { display: flex; flex-wrap: wrap; justify-content: space-between; gap: .5rem; margin-bottom: .6rem; }
    .version h3 { margin: 0; }
    .notes { white-space: pre-line; line-height: 1.5; }
  `
})
export class ConsoleMaintenance implements OnInit {
  private readonly service = inject(EtatPlateformeService);
  readonly vue = input<Vue>('maintenances');
  readonly libelles = LIBELLES;
  readonly modules = MODULES_FACTURABLES;

  readonly maintenances = signal<MaintenancePlateforme[]>([]);
  readonly versions = signal<VersionPlateforme[]>([]);
  readonly chargement = signal(true);
  readonly envoi = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly formMaintenance = signal<FormMaintenance | null>(null);
  readonly maintenanceEditee = signal<number | null>(null);
  readonly formVersion = signal<FormVersion | null>(null);
  readonly versionEditee = signal<number | null>(null);
  private readonly maintenant = computed(() => Date.now());

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.listeMaintenances().subscribe({
      next: (l) => { this.maintenances.set(l); this.chargement.set(false); },
      error: (e) => this.echec(e)
    });
    this.service.listeVersions().subscribe({
      next: (l) => this.versions.set(l),
      error: (e) => this.echec(e)
    });
  }

  modulesDe(m: MaintenancePlateforme): string {
    return m.modules.map((x) => x.libelle).join(', ');
  }

  futur(v: VersionPlateforme): boolean {
    return new Date(v.publieeLe).getTime() > this.maintenant();
  }

  // ------------------------------------------------------------- maintenances

  nouvelleMaintenance(): void {
    const debut = new Date(Date.now() + 24 * 3600 * 1000);
    debut.setMinutes(0, 0, 0);
    this.maintenanceEditee.set(null);
    this.erreur.set(null);
    this.formMaintenance.set({
      message: '',
      modules: this.aucunModule(),
      debut: local(debut),
      finPrevue: local(new Date(debut.getTime() + 2 * 3600 * 1000)),
      annonceHeures: 24
    });
  }

  editerMaintenance(m: MaintenancePlateforme): void {
    const modules = this.aucunModule();
    m.modules.forEach((x) => (modules[x.code] = true));
    const avance = Math.round((new Date(m.debut).getTime() - new Date(m.annonceLe).getTime()) / 3600000);
    this.maintenanceEditee.set(m.id);
    this.erreur.set(null);
    this.formMaintenance.set({
      message: m.message,
      modules,
      debut: local(new Date(m.debut)),
      finPrevue: local(new Date(m.finPrevue)),
      annonceHeures: avance
    });
  }

  enregistrerMaintenance(f: FormMaintenance): void {
    const modules = (Object.keys(f.modules) as CodeModule[]).filter((c) => f.modules[c]);
    if (modules.length === 0) {
      this.erreur.set('Choisissez au moins un module.');
      return;
    }
    if (!f.message.trim() || !f.debut || !f.finPrevue) {
      this.erreur.set('Le message, le debut et la fin prevue sont obligatoires.');
      return;
    }
    const saisie: SaisieMaintenance = {
      message: f.message.trim(), modules, debut: iso(f.debut), finPrevue: iso(f.finPrevue),
      annonceHeures: f.annonceHeures === null || f.annonceHeures === undefined ? null : Math.max(0, Math.round(f.annonceHeures))
    };
    const id = this.maintenanceEditee();
    this.envoi.set(true);
    (id ? this.service.modifierMaintenance(id, saisie) : this.service.programmer(saisie)).subscribe({
      next: () => this.reussi(id ? 'Maintenance modifiee.' : 'Maintenance programmee.', () => this.formMaintenance.set(null)),
      error: (e) => this.echec(e)
    });
  }

  terminer(m: MaintenancePlateforme): void {
    this.envoi.set(true);
    this.service.terminer(m.id).subscribe({
      next: () => this.reussi('Maintenance terminee : les ecrans sont rouverts.'),
      error: (e) => this.echec(e)
    });
  }

  annuler(m: MaintenancePlateforme): void {
    this.envoi.set(true);
    this.service.annuler(m.id).subscribe({
      next: () => this.reussi('Maintenance annulee.'),
      error: (e) => this.echec(e)
    });
  }

  // ------------------------------------------------------------- versions

  nouvelleVersion(): void {
    this.versionEditee.set(null);
    this.erreur.set(null);
    this.formVersion.set({ numero: '', titre: '', notes: '', rechargement: true, publieeLe: '' });
  }

  editerVersion(v: VersionPlateforme): void {
    this.versionEditee.set(v.id);
    this.erreur.set(null);
    this.formVersion.set({ numero: v.numero, titre: v.titre, notes: v.notes, rechargement: v.rechargement,
      publieeLe: local(new Date(v.publieeLe)) });
  }

  enregistrerVersion(f: FormVersion): void {
    if (!f.numero.trim() || !f.titre.trim() || !f.notes.trim()) {
      this.erreur.set('Le numero, le titre et les notes sont obligatoires.');
      return;
    }
    const saisie = { numero: f.numero.trim(), titre: f.titre.trim(), notes: f.notes.trim(), rechargement: f.rechargement,
      publieeLe: f.publieeLe ? iso(f.publieeLe) : null };
    const id = this.versionEditee();
    this.envoi.set(true);
    (id ? this.service.modifierVersion(id, saisie) : this.service.publier(saisie)).subscribe({
      next: () => this.reussi(id ? 'Version modifiee.' : 'Version publiee.', () => this.formVersion.set(null)),
      error: (e) => this.echec(e)
    });
  }

  supprimer(v: VersionPlateforme): void {
    this.envoi.set(true);
    this.service.supprimerVersion(v.id).subscribe({
      next: () => this.reussi(`Version ${v.numero} supprimee.`),
      error: (e) => this.echec(e)
    });
  }

  // ------------------------------------------------------------- internes

  private aucunModule(): Record<CodeModule, boolean> {
    return { GESTION_PARC: false, POINT_DE_VENTE: false, MARKETPLACE: false, AGENCES: false, PRESENCE: false };
  }

  private reussi(message: string, apres?: () => void): void {
    this.envoi.set(false);
    this.erreur.set(null);
    this.succes.set(message);
    apres?.();
    this.charger();
    this.service.actualiser();
    setTimeout(() => this.succes.set(null), 4000);
  }

  private echec(e: { error?: { message?: string; champs?: Record<string, string> } }): void {
    this.envoi.set(false);
    this.chargement.set(false);
    const champs = e?.error?.champs ? Object.values(e.error.champs).join(' ') : '';
    this.erreur.set(champs || e?.error?.message || 'Operation impossible.');
  }
}
