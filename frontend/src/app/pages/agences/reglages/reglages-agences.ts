import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DroitsAgences, ReglagesAgences } from '../../../core/models/agences';
import { AuthService } from '../../../core/auth/auth.service';
import { AgencesService } from '../../../core/services/agences.service';

/**
 * Reglages communs a toutes les agences (journee de caisse, doublons,
 * plafonds) et, pour l'administrateur, droits du superviseur et du caissier.
 */
@Component({
  selector: 'app-reglages-agences',
  imports: [FormsModule],
  templateUrl: './reglages-agences.html',
  styleUrl: './reglages-agences.scss'
})
export class ReglagesAgencesPage implements OnInit {
  private readonly service = inject(AgencesService);
  readonly administrateur = inject(AuthService).aRole('ADMIN');

  readonly heures = Array.from({ length: 24 }, (_, i) => i);
  readonly reglages = signal<ReglagesAgences | null>(null);
  readonly droits = signal<DroitsAgences | null>(null);
  /** Droits coches par profil, modifies avant enregistrement. */
  readonly coches = signal<Record<string, Set<string>>>({});
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly groupes = computed(() => {
    const d = this.droits();
    if (!d) {
      return [];
    }
    const map = new Map<string, DroitsAgences['permissions']>();
    d.permissions.forEach((p) => map.set(p.groupe, [...(map.get(p.groupe) ?? []), p]));
    return [...map.entries()].map(([groupe, permissions]) => ({ groupe, permissions }));
  });

  ngOnInit(): void {
    this.service.reglages().subscribe({
      next: (r) => this.reglages.set({ ...r }),
      error: (e) => this.erreur.set(this.message(e, 'Lecture des reglages impossible.'))
    });
    if (this.administrateur) {
      this.service.droits().subscribe({ next: (d) => this.appliquerDroits(d) });
    }
  }

  enregistrerReglages(): void {
    const r = this.reglages();
    if (!r) {
      return;
    }
    this.succes.set(null);
    this.erreur.set(null);
    this.service.enregistrerReglages({
      heureBascule: Number(r.heureBascule),
      delaiDoublonMinutes: Math.max(0, Math.round(r.delaiDoublonMinutes)),
      montantMaxTransaction: Math.round(r.montantMaxTransaction),
      seuilValidationMouvement: Math.round(r.seuilValidationMouvement),
      plafondDepenseCaissier: Math.round(r.plafondDepenseCaissier),
      joursAlerteEcheance: Math.max(0, Math.round(r.joursAlerteEcheance ?? 7))
    }).subscribe({
      next: (n) => {
        this.reglages.set({ ...n });
        this.succes.set('Reglages enregistres.');
      },
      error: (e) => this.erreur.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  estCoche(role: string, permission: string): boolean {
    return this.coches()[role]?.has(permission) ?? false;
  }

  basculer(role: string, permission: string): void {
    this.coches.update((c) => {
      const suivant = { ...c, [role]: new Set(c[role] ?? []) };
      if (suivant[role].has(permission)) {
        suivant[role].delete(permission);
      } else {
        suivant[role].add(permission);
      }
      return suivant;
    });
  }

  enregistrerDroits(): void {
    this.succes.set(null);
    this.erreur.set(null);
    const profils = Object.entries(this.coches()).map(([role, permissions]) => ({ role, permissions: [...permissions] }));
    this.service.enregistrerDroits(profils).subscribe({
      next: (d) => {
        this.appliquerDroits(d);
        this.succes.set('Droits enregistres : ils s\'appliquent des la prochaine action de chaque utilisateur.');
      },
      error: (e) => this.erreur.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  /** Libelle de l'heure de bascule. */
  libelleHeure(h: number): string {
    return h === 0 ? 'Minuit (00 h)' : `${String(h).padStart(2, '0')} h`;
  }

  private appliquerDroits(d: DroitsAgences): void {
    this.droits.set(d);
    const coches: Record<string, Set<string>> = {};
    d.profils.forEach((p) => (coches[p.role] = new Set(p.permissions)));
    this.coches.set(coches);
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
