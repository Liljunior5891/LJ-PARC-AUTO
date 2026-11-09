import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import {
  ETIQUETTES_STATUT_PRESENCE,
  HistoriquePresence,
  LIBELLES_STATUT_PRESENCE,
  dureeMinutes
} from '../../core/models/presence';

/** Mois d'un employe : totaux et liste des jours ; partage par la fiche du gerant et par l'employe lui-meme. */
@Component({
  selector: 'app-historique-employe',
  imports: [DatePipe],
  template: `
    @let h = historique();
    <div class="cartes-apercu tete-ecran">
      <div class="carte-apercu vert">
        <span class="icone" aria-hidden="true"><svg viewBox="0 0 24 24"><rect x="3.5" y="5" width="17" height="15" rx="2.5" /><path d="M3.5 10h17M8 3v4M16 3v4M9 14.5l2 2 4-4" /></svg></span>
        <span class="textes">
          <span class="libelle">Jours presents</span>
          <strong>{{ h.totaux.presents }}</strong>
          <small>sur le mois</small>
        </span>
      </div>
      <div class="carte-apercu orange" [class.calme]="!h.totaux.retards">
        <span class="icone" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="13" r="8" /><path d="M12 9v4l2.5 2.5M9 2.5h6" /></svg></span>
        <span class="textes">
          <span class="libelle">Retards</span>
          <strong>{{ h.totaux.retards }}</strong>
          <small>{{ h.totaux.minutesRetard ? duree(h.totaux.minutesRetard) + ' au total' : 'aucun retard' }}</small>
        </span>
      </div>
      <div class="carte-apercu rouge" [class.calme]="!h.totaux.absences">
        <span class="icone" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5" /><path d="M9 9l6 6M15 9l-6 6" /></svg></span>
        <span class="textes">
          <span class="libelle">Absences</span>
          <strong>{{ h.totaux.absences }}</strong>
          <small>jour(s) travailles sans pointage</small>
        </span>
      </div>
      <div class="carte-apercu bleu">
        <span class="icone" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 20V10M10 20V4M16 20v-7M22 20H2" /></svg></span>
        <span class="textes">
          <span class="libelle">Ponctualite</span>
          <strong>{{ h.totaux.ponctualite }}<em> %</em></strong>
          <small>arrivees a l'heure</small>
        </span>
        <span class="jauge"><i [style.width.%]="h.totaux.ponctualite"></i></span>
      </div>
    </div>

    <div class="carte">
      <div class="tableau-conteneur">
        <table class="tableau">
          <thead>
            <tr><th>Jour</th><th>Etat</th><th>Arrivee</th><th>Retard</th><th>Lieu</th></tr>
          </thead>
          <tbody>
            @for (j of h.jours; track j.date) {
              <tr [class.repos]="j.statut === 'REPOS' || j.statut === 'NON_EMBAUCHE'">
                <td class="date">{{ j.date | date: 'EEE dd/MM' }}</td>
                <td><span class="etiquette {{ etiquettes[j.statut] }}">{{ libelles[j.statut] }}</span></td>
                <td class="heure">{{ j.arriveeLe ? (j.arriveeLe | date: 'HH:mm') : '—' }}</td>
                <td>
                  @if (j.retardMinutes > 0) {
                    <span class="retard">+{{ duree(j.retardMinutes) }}</span>
                    @if (j.apresLimite) { <small class="sous-ligne">apres l'heure limite</small> }
                  } @else { — }
                </td>
                <td>{{ j.lieu ?? '—' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5"><p class="message vide">Aucun jour a afficher pour ce mois.</p></td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .cartes-apercu.tete-ecran { grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); }
    .date { text-transform: capitalize; font-weight: 600; }
    .heure { font-weight: 600; font-variant-numeric: tabular-nums; }
    .retard { color: #b35f00; font-weight: 600; }
    .sous-ligne { display: block; color: var(--couleur-texte-doux); font-size: 0.75rem; }
    tr.repos td { color: var(--couleur-texte-doux); }
    @media (max-width: 520px) {
      .cartes-apercu.tete-ecran { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }
  `]
})
export class HistoriqueEmploye {
  readonly historique = input.required<HistoriquePresence>();
  readonly libelles = LIBELLES_STATUT_PRESENCE;
  readonly etiquettes = ETIQUETTES_STATUT_PRESENCE;
  readonly duree = dureeMinutes;
}
