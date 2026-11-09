import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ReglagePresence } from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';

/** Horaire de l'entreprise, regle par l'administrateur : il sert a calculer les retards et les absences. */
@Component({
  selector: 'app-reglages-presence',
  imports: [FormsModule],
  template: `
    <div class="entete-page">
      <div>
        <h1>Horaire</h1>
        <p class="sous-titre">Le meme horaire pour toute l'entreprise : il sert a calculer les retards et les absences.</p>
      </div>
    </div>
    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }
    @if (succes()) { <div class="message succes">{{ succes() }}</div> }
    @if (reglage(); as r) {
      <form class="carte formulaire-horaire" (ngSubmit)="enregistrer(r)">
        <div class="grille-formulaire">
          <div class="champ">
            <label for="h-arrivee">Heure d'arrivee prevue <span class="requis">*</span></label>
            <input id="h-arrivee" name="arrivee" type="time" required [(ngModel)]="r.heureArrivee" />
          </div>
          <div class="champ">
            <label for="h-tolerance">Tolerance (minutes) <span class="requis">*</span></label>
            <input id="h-tolerance" name="tolerance" type="number" min="0" max="240" required [(ngModel)]="r.toleranceMinutes" />
            <small>Arrive dans ce delai, l'employe est a l'heure ; au-dela, le retard compte depuis l'heure prevue.</small>
          </div>
          <div class="champ">
            <label for="h-limite">Heure limite <span class="requis">*</span></label>
            <input id="h-limite" name="limite" type="time" required [(ngModel)]="r.heureLimite" />
            <small>Passe cette heure, un employe qui n'a pas pointe est absent. S'il arrive ensuite, son arrivee compte comme un gros retard.</small>
          </div>
        </div>
        <fieldset class="choix-multiples">
          <legend>Jours travailles <span class="requis">*</span></legend>
          @for (j of jours; track j.valeur) {
            <label [class.coche]="r.joursTravailles.includes(j.valeur)">
              <input type="checkbox" [checked]="r.joursTravailles.includes(j.valeur)" (change)="basculer(r, j.valeur)" /> {{ j.libelle }}
            </label>
          }
        </fieldset>
        <p class="aide" style="margin-top: 1rem">
          Exemple : arrivee a {{ r.heureArrivee.slice(0, 5) }}, tolerance {{ r.toleranceMinutes }} min →
          arrive a {{ exemple(r, r.toleranceMinutes) }} : a l'heure ; a {{ exemple(r, r.toleranceMinutes + 5) }} : {{ r.toleranceMinutes + 5 }} min de retard.
        </p>
        <div class="actions-formulaire">
          <button class="bouton" type="submit" [disabled]="enCours() || !r.joursTravailles.length">Enregistrer</button>
        </div>
      </form>
    }
  `,
  styleUrl: '../_presence-commun.scss'
})
export class ReglagesPresence implements OnInit {
  private readonly service = inject(PresenceService);

  readonly reglage = signal<ReglagePresence | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly enCours = signal(false);
  readonly jours = [
    { valeur: 1, libelle: 'Lundi' }, { valeur: 2, libelle: 'Mardi' }, { valeur: 3, libelle: 'Mercredi' },
    { valeur: 4, libelle: 'Jeudi' }, { valeur: 5, libelle: 'Vendredi' }, { valeur: 6, libelle: 'Samedi' },
    { valeur: 7, libelle: 'Dimanche' }
  ];

  ngOnInit(): void {
    this.service.reglage().subscribe({
      next: (r) => this.reglage.set({ ...r, heureArrivee: r.heureArrivee.slice(0, 5), heureLimite: r.heureLimite.slice(0, 5) }),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement impossible.')
    });
  }

  basculer(r: ReglagePresence, jour: number): void {
    r.joursTravailles = r.joursTravailles.includes(jour)
      ? r.joursTravailles.filter((j) => j !== jour)
      : [...r.joursTravailles, jour].sort();
  }

  exemple(r: ReglagePresence, minutes: number): string {
    const [h, m] = r.heureArrivee.split(':').map(Number);
    const total = h * 60 + m + minutes;
    return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }

  enregistrer(r: ReglagePresence): void {
    this.enCours.set(true);
    this.erreur.set(null);
    this.succes.set(null);
    this.service.modifierReglage(r).subscribe({
      next: (n) => {
        this.enCours.set(false);
        this.reglage.set({ ...n, heureArrivee: n.heureArrivee.slice(0, 5), heureLimite: n.heureLimite.slice(0, 5) });
        this.succes.set('Horaire enregistre.');
      },
      error: (e) => {
        this.enCours.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
