import { RouterLink } from '@angular/router';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Agence, RequeteAgence } from '../../../core/models/agences';
import { MembrePersonnel } from '../../../core/models/pdv';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';

/**
 * Agences de l'entreprise (LJ-Agoe, LJ-Be...) : chacune a sa caisse, ses floats
 * et son personnel ; l'administrateur les pilote toutes. Les comptes de
 * l'agence se creent d'office a l'enregistrement.
 */
@Component({
  selector: 'app-agences-liste',
  imports: [FormsModule, Modale, RouterLink],
  templateUrl: './agences-liste.html',
  styleUrl: './agences-liste.scss'
})
export class AgencesListe implements OnInit {
  readonly service = inject(AgencesService);

  readonly agences = signal<Agence[]>([]);
  readonly personnel = signal<MembrePersonnel[]>([]);
  readonly superviseurs = computed(() => this.personnel().filter((m) => m.actif && m.codes.includes('SUPERVISEUR_AGENCE')));
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly gestion = signal(false);
  /** Plusieurs agences autorisees par le super-administrateur de la plateforme. */
  readonly multiples = signal(true);
  /** Sans l'option, une seule agence ouverte : impossible d'en creer une autre. */
  readonly limiteAtteinte = computed(() => !this.multiples() && this.agences().some((a) => a.actif));

  readonly edition = signal<Agence | 'nouvelle' | null>(null);
  formulaire: RequeteAgence = this.vide();
  readonly choisis = signal<Set<number>>(new Set());

  ngOnInit(): void {
    this.service.chargerMoi().subscribe({
      next: (moi) => {
        this.gestion.set(moi.permissions.includes('AGENCES_GERER'));
        this.multiples.set(!!moi.agencesMultiples);
        if (this.gestion()) {
          this.service.personnel().subscribe({ next: (p) => this.personnel.set(p) });
        }
      }
    });
    this.charger();
  }

  charger(): void {
    this.service.agences().subscribe({
      next: (a) => {
        this.agences.set(a);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Chargement des agences impossible.'));
      }
    });
  }

  nouvelle(): void {
    this.formulaire = this.vide();
    this.choisis.set(new Set());
    this.erreurFormulaire.set(null);
    this.edition.set('nouvelle');
  }

  modifier(a: Agence): void {
    this.formulaire = {
      code: a.code, nom: a.nom, ville: a.ville ?? '', quartier: a.quartier ?? '', adresse: a.adresse ?? '',
      telephone: a.telephone ?? '', responsableId: a.responsableId ?? null, actif: a.actif, utilisateurIds: []
    };
    this.choisis.set(new Set(a.utilisateurs.map((u) => u.id)));
    this.erreurFormulaire.set(null);
    this.edition.set(a);
  }

  basculer(m: MembrePersonnel): void {
    this.choisis.update((ids) => {
      const suivant = new Set(ids);
      if (suivant.has(m.id)) {
        suivant.delete(m.id);
      } else {
        suivant.add(m.id);
      }
      return suivant;
    });
  }

  enregistrer(): void {
    const edition = this.edition();
    const f = this.formulaire;
    if (!edition || !f.code.trim() || !f.nom.trim()) {
      return;
    }
    const requete: RequeteAgence = {
      ...f,
      code: f.code.trim(),
      nom: f.nom.trim(),
      responsableId: f.responsableId || null,
      utilisateurIds: [...this.choisis()]
    };
    const appel = edition === 'nouvelle' ? this.service.creerAgence(requete) : this.service.modifierAgence(edition.id, requete);
    appel.subscribe({
      next: (a) => {
        this.edition.set(null);
        this.succes.set(edition === 'nouvelle'
          ? `Agence ${a.nom} creee : sa caisse generale, ses floats et son compte de commissions sont prets.`
          : `Agence ${a.nom} enregistree.`);
        this.charger();
        this.service.chargerMoi().subscribe();
      },
      error: (e) => this.erreurFormulaire.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  /** Quartier et ville, pour la carte. */
  lieu(a: Agence): string {
    return [a.quartier, a.ville].filter((x) => !!x).join(', ');
  }

  private vide(): RequeteAgence {
    return { code: '', nom: '', ville: '', quartier: '', adresse: '', telephone: '', responsableId: null, actif: true, utilisateurIds: [] };
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
