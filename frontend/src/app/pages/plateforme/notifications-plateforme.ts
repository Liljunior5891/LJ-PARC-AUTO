import { DatePipe } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';

interface LigneNotification {
  id: number;
  entrepriseId?: number | null;
  entrepriseNom?: string | null;
  canal: 'EMAIL' | 'SMS' | 'WHATSAPP';
  destinataire: string;
  sujet?: string | null;
  texte: string;
  motif: string;
  statut: 'EN_ATTENTE' | 'ENVOYE' | 'SIMULE' | 'ECHEC' | 'ABANDONNE';
  fournisseur?: string | null;
  tentatives: number;
  erreur?: string | null;
  creeLe: string;
  envoyeLe?: string | null;
}

interface EtatFournisseurs {
  emailBranche: boolean;
  smsBranche: boolean;
  whatsappBranche: boolean;
}

const LIBELLES_STATUT: Record<LigneNotification['statut'], string> = {
  EN_ATTENTE: 'En attente',
  ENVOYE: 'Envoye',
  SIMULE: 'Simule',
  ECHEC: 'Echec (renvoi auto)',
  ABANDONNE: 'Abandonne'
};

/**
 * Messages envoyes par la plateforme (e-mail, SMS, WhatsApp) : etat de chaque
 * envoi et texte complet. Tant qu'aucun fournisseur n'est branche, les messages
 * sont simules et se lisent ici.
 */
@Component({
  selector: 'app-notifications-plateforme',
  imports: [DatePipe, FormsModule],
  template: `
    <div class="entete-page">
      <div>
        <h1>Notifications</h1>
        <p class="sous-titre">E-mails, SMS et WhatsApp envoyes par la plateforme.</p>
      </div>
      <button class="bouton secondaire" type="button" (click)="charger()">Actualiser</button>
    </div>

    @if (etat(); as e) {
      <div class="fournisseurs">
        <span class="etiquette" [class.branche]="e.emailBranche">E-mail : {{ e.emailBranche ? 'serveur SMTP branche' : 'simulation' }}</span>
        <span class="etiquette" [class.branche]="e.smsBranche">SMS : {{ e.smsBranche ? 'fournisseur branche' : 'simulation' }}</span>
        <span class="etiquette" [class.branche]="e.whatsappBranche">WhatsApp : {{ e.whatsappBranche ? 'fournisseur branche' : 'simulation' }}</span>
      </div>
      @if (!e.emailBranche || !e.smsBranche || !e.whatsappBranche) {
        <p class="aide">En simulation, rien ne part : le message est enregistre et se lit ci-dessous. Les fournisseurs se branchent au passage en production.</p>
      }
    }
    @if (erreur()) { <div class="message erreur">{{ erreur() }}</div> }

    <div class="filtres">
      <label>Canal
        <select [(ngModel)]="canal" (ngModelChange)="charger()">
          <option value="">Tous</option><option value="EMAIL">E-mail</option><option value="SMS">SMS</option><option value="WHATSAPP">WhatsApp</option>
        </select></label>
      <label>Statut
        <select [(ngModel)]="statut" (ngModelChange)="charger()">
          <option value="">Tous</option>
          @for (s of statuts; track s) { <option [value]="s">{{ libelles[s] }}</option> }
        </select></label>
    </div>

    <section class="carte">
      <div class="tableau-conteneur">
        <table class="tableau">
          <thead><tr><th>Date</th><th>Canal</th><th>Destinataire</th><th>Objet</th><th>Entreprise</th><th>Statut</th><th></th></tr></thead>
          <tbody>
            @for (n of lignes(); track n.id) {
              <tr (click)="ouvert.set(ouvert() === n.id ? null : n.id)" class="cliquable">
                <td>{{ n.creeLe | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td>{{ n.canal === 'EMAIL' ? 'E-mail' : n.canal === 'SMS' ? 'SMS' : 'WhatsApp' }}</td>
                <td>{{ n.destinataire }}</td>
                <td>{{ n.sujet || n.motif }}</td>
                <td>{{ n.entrepriseNom || 'Plateforme' }}</td>
                <td><span class="statut {{ n.statut.toLowerCase() }}">{{ libelles[n.statut] }}</span>
                  @if (n.erreur) { <small class="doux">{{ n.erreur }}</small> }</td>
                <td class="actions">
                  @if (n.statut === 'ECHEC' || n.statut === 'ABANDONNE' || n.statut === 'SIMULE') {
                    <button class="bouton secondaire petit" type="button" (click)="renvoyer(n, $event)">Renvoyer</button>
                  }
                </td>
              </tr>
              @if (ouvert() === n.id) {
                <tr class="detail"><td colspan="7"><pre>{{ n.texte }}</pre></td></tr>
              }
            } @empty {
              <tr><td colspan="7" class="vide">Aucun message.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .fournisseurs { display: flex; flex-wrap: wrap; gap: .5rem; margin-bottom: .5rem; }
    .etiquette { padding: .3rem .7rem; border-radius: 999px; font-size: .8rem; font-weight: 600;
      background: #fef3c7; color: #92400e; }
    .etiquette.branche { background: #dcfce7; color: #166534; }
    .filtres { display: flex; flex-wrap: wrap; gap: 1rem; margin: 1rem 0; }
    .filtres label { display: flex; flex-direction: column; gap: .25rem; font-size: .8rem; font-weight: 600; }
    .cliquable { cursor: pointer; }
    .statut { padding: .15rem .55rem; border-radius: 999px; font-size: .78rem; font-weight: 600; background: #e2e8f0; }
    .statut.envoye { background: #dcfce7; color: #166534; }
    .statut.simule { background: #e0f2fe; color: #075985; }
    .statut.echec, .statut.abandonne { background: #fee2e2; color: #991b1b; }
    .detail pre { margin: 0; padding: .75rem; white-space: pre-wrap; font-family: inherit; font-size: .88rem;
      background: #f8fafc; border-radius: 8px; }
  `
})
export class NotificationsPlateforme implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/plateforme/notifications`;

  readonly libelles = LIBELLES_STATUT;
  readonly statuts = Object.keys(LIBELLES_STATUT) as LigneNotification['statut'][];
  readonly lignes = signal<LigneNotification[]>([]);
  readonly etat = signal<EtatFournisseurs | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly ouvert = signal<number | null>(null);
  canal = '';
  statut = '';

  ngOnInit(): void {
    this.http.get<EtatFournisseurs>(`${this.base}/etat`).subscribe({ next: (e) => this.etat.set(e) });
    this.charger();
  }

  charger(): void {
    let params = new HttpParams();
    if (this.canal) {
      params = params.set('canal', this.canal);
    }
    if (this.statut) {
      params = params.set('statut', this.statut);
    }
    this.http.get<LigneNotification[]>(this.base, { params }).subscribe({
      next: (l) => {
        this.erreur.set(null);
        this.lignes.set(l);
      },
      error: () => this.erreur.set('Notifications indisponibles.')
    });
  }

  renvoyer(n: LigneNotification, evenement: Event): void {
    evenement.stopPropagation();
    this.http.post<void>(`${this.base}/${n.id}/renvoi`, null).subscribe({
      next: () => setTimeout(() => this.charger(), 1500),
      error: () => this.erreur.set('Renvoi impossible.')
    });
  }
}
