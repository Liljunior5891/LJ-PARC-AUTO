import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { BornePresence, CompteConvertible, LieuPresence, LieuRequete } from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';
import { Modale } from '../../../shared/modale';

/**
 * Lieux de travail et leurs bornes : position GPS et rayon du pointage par
 * telephone, responsables, et appairage des tablettes qui affichent le code QR.
 */
@Component({
  selector: 'app-lieux-presence',
  imports: [DatePipe, DecimalPipe, FormsModule, Modale],
  templateUrl: './lieux.html',
  styleUrl: './lieux.scss'
})
export class LieuxPresence implements OnInit {
  private readonly service = inject(PresenceService);
  private readonly auth = inject(AuthService);

  readonly gestion = this.auth.aRole('ADMIN', 'GERANT');
  readonly lieux = signal<LieuPresence[]>([]);
  readonly responsables = signal<CompteConvertible[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly localisation = signal(false);
  readonly adresseBorne = `${window.location.origin}/borne-presence`;

  readonly edition = signal<LieuPresence | 'nouveau' | null>(null);
  formulaire: LieuRequete = this.vide();
  readonly responsablesChoisis = signal<Set<number>>(new Set());

  readonly nouvelleBorne = signal<LieuPresence | null>(null);
  nomBorne = '';
  /** Code d'appairage tout juste genere, a saisir sur la tablette. */
  readonly codeAffiche = signal<{ borne: BornePresence; lieu: string } | null>(null);

  ngOnInit(): void {
    this.charger();
    if (this.gestion) {
      this.service.responsablesPossibles().subscribe({ next: (r) => this.responsables.set(r) });
    }
  }

  nouveau(): void {
    this.formulaire = this.vide();
    this.responsablesChoisis.set(new Set());
    this.erreurFormulaire.set(null);
    this.edition.set('nouveau');
  }

  modifier(l: LieuPresence): void {
    this.formulaire = {
      nom: l.nom, adresse: l.adresse ?? '', latitude: l.latitude ?? null, longitude: l.longitude ?? null,
      rayonMetres: l.rayonMetres, responsableIds: [], actif: l.actif
    };
    this.responsablesChoisis.set(new Set(l.responsableIds));
    this.erreurFormulaire.set(null);
    this.edition.set(l);
  }

  /** Prend la position de l'appareil du gerant, pose sur le lieu. */
  positionActuelle(): void {
    if (!('geolocation' in navigator)) {
      this.erreurFormulaire.set('Cet appareil ne donne pas sa position.');
      return;
    }
    this.localisation.set(true);
    navigator.geolocation.getCurrentPosition(
      (p) => {
        this.localisation.set(false);
        this.formulaire.latitude = Math.round(p.coords.latitude * 1e6) / 1e6;
        this.formulaire.longitude = Math.round(p.coords.longitude * 1e6) / 1e6;
        if (p.coords.accuracy > 50) {
          this.erreurFormulaire.set(`Position approximative (± ${Math.round(p.coords.accuracy)} m) : reessayez dehors ou pres d'une fenetre.`);
        } else {
          this.erreurFormulaire.set(null);
        }
      },
      () => {
        this.localisation.set(false);
        this.erreurFormulaire.set('Position refusee ou introuvable : autorisez la localisation.');
      },
      { enableHighAccuracy: true, timeout: 20000 }
    );
  }

  basculerResponsable(id: number): void {
    this.responsablesChoisis.update((ids) => {
      const s = new Set(ids);
      if (s.has(id)) {
        s.delete(id);
      } else {
        s.add(id);
      }
      return s;
    });
  }

  enregistrer(): void {
    const edition = this.edition();
    if (!edition || !this.formulaire.nom.trim()) {
      return;
    }
    const requete: LieuRequete = { ...this.formulaire, responsableIds: [...this.responsablesChoisis()] };
    const appel = edition === 'nouveau' ? this.service.creerLieu(requete) : this.service.modifierLieu(edition.id, requete);
    appel.subscribe({
      next: () => {
        this.edition.set(null);
        this.charger();
      },
      error: (e) => this.erreurFormulaire.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }

  renouveler(l: LieuPresence): void {
    this.service.renouvelerSecret(l.id).subscribe({
      next: () => this.succes.set(`Nouveau secret pour ${l.nom} : les anciens codes QR ne marchent plus.`),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Renouvellement impossible.')
    });
  }

  ajouterBorne(l: LieuPresence): void {
    this.nomBorne = `Borne ${l.nom}`;
    this.nouvelleBorne.set(l);
  }

  creerBorne(): void {
    const l = this.nouvelleBorne();
    if (!l || !this.nomBorne.trim()) {
      return;
    }
    this.service.creerBorne(l.id, this.nomBorne.trim()).subscribe({
      next: (b) => {
        this.nouvelleBorne.set(null);
        this.codeAffiche.set({ borne: b, lieu: l.nom });
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Creation impossible.')
    });
  }

  reappairer(b: BornePresence, l: LieuPresence): void {
    this.service.reappairer(b.id).subscribe({
      next: (n) => {
        this.codeAffiche.set({ borne: n, lieu: l.nom });
        this.charger();
      },
      error: (e) => this.erreur.set(e?.error?.message ?? 'Operation impossible.')
    });
  }

  basculerBorne(b: BornePresence): void {
    this.service.basculerBorne(b.id).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Operation impossible.')
    });
  }

  enLigne(b: BornePresence): boolean {
    return !!b.dernierContact && Date.now() - new Date(b.dernierContact).getTime() < 2 * 60 * 1000;
  }

  nomResponsable(id: number): string {
    const r = this.responsables().find((x) => x.id === id);
    return r ? `${r.prenom ? r.prenom + ' ' : ''}${r.nom}` : `#${id}`;
  }

  private charger(): void {
    this.service.lieux().subscribe({
      next: (l) => this.lieux.set(l),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement impossible.')
    });
  }

  private vide(): LieuRequete {
    return { nom: '', adresse: '', latitude: null, longitude: null, rayonMetres: 100, responsableIds: [], actif: true };
  }
}
