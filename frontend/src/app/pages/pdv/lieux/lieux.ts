import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { Lieu, TypeLieu } from '../../../core/models/lieux';
import { MembrePersonnel } from '../../../core/models/pdv';
import { LieuxService } from '../../../core/services/lieux.service';
import { PdvService } from '../../../core/services/pdv.service';
import { Modale } from '../../../shared/modale';

/**
 * Magasins et entrepots. Sans entrepot, la boutique garde un seul stock ; le premier
 * entrepot active le fonctionnement par lieu, et le stock actuel va au magasin principal.
 */
@Component({
  selector: 'app-lieux-pdv',
  imports: [FormsModule, DecimalPipe, RouterLink, Modale],
  templateUrl: './lieux.html',
  styleUrl: './lieux.scss'
})
export class LieuxPdv implements OnInit {
  private readonly service = inject(LieuxService);
  private readonly pdv = inject(PdvService);
  readonly estAdmin = inject(AuthService).aRole('ADMIN');

  readonly lieux = signal<Lieu[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly personnel = signal<MembrePersonnel[]>([]);

  readonly edition = signal<Lieu | 'nouveau' | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);
  readonly choisis = signal<Set<number>>(new Set());
  formulaire: { nom: string; type: TypeLieu; adresse: string; telephone: string; actif: boolean; visibleEnLigne: boolean } = this.vierge('ENTREPOT');

  readonly actif = computed(() => this.lieux().some((l) => l.type === 'ENTREPOT' && l.actif));
  readonly totalAchat = computed(() => this.lieux().reduce((s, l) => s + l.valeurAchat, 0));
  /** Seuls les magasiniers s'affectent a un lieu ; la caisse y accede par son magasin. */
  readonly magasiniers = computed(() => this.personnel().filter((m) => (m.codes ?? []).includes('MAGASINIER')));

  ngOnInit(): void {
    this.charger();
    this.pdv.personnel().subscribe({ next: (p) => this.personnel.set(p.filter((m) => m.actif)) });
  }

  private vierge(type: TypeLieu) {
    return { nom: '', type, adresse: '', telephone: '', actif: true, visibleEnLigne: false };
  }

  charger(): void {
    this.service.lister().subscribe({
      next: (l) => {
        this.lieux.set(l);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(e?.error?.message ?? 'Chargement des lieux impossible.');
      }
    });
  }

  ouvrirNouveau(type: TypeLieu): void {
    this.formulaire = this.vierge(type);
    this.choisis.set(new Set());
    this.erreurFormulaire.set(null);
    this.edition.set('nouveau');
  }

  ouvrir(l: Lieu): void {
    this.formulaire = {
      nom: l.nom, type: l.type, adresse: l.adresse ?? '', telephone: l.telephone ?? '', actif: l.actif, visibleEnLigne: l.visibleEnLigne
    };
    // Un compte affecte avant la regle (sans profil magasinier) est retire a l'enregistrement.
    const eligibles = new Set(this.magasiniers().map((m) => m.id));
    this.choisis.set(new Set(l.utilisateurs.map((u) => u.id).filter((id) => eligibles.has(id))));
    this.erreurFormulaire.set(null);
    this.edition.set(l);
  }

  basculer(m: MembrePersonnel): void {
    this.choisis.update((s) => {
      const copie = new Set(s);
      if (!copie.delete(m.id)) {
        copie.add(m.id);
      }
      return copie;
    });
  }

  enregistrer(): void {
    const edition = this.edition();
    if (!edition || !this.formulaire.nom.trim()) {
      return;
    }
    const premier = edition === 'nouveau' && this.formulaire.type === 'ENTREPOT' && !this.actif();
    const requete = {
      nom: this.formulaire.nom.trim(),
      type: this.formulaire.type,
      adresse: this.formulaire.adresse.trim() || null,
      telephone: this.formulaire.telephone.trim() || null,
      actif: this.formulaire.actif,
      visibleEnLigne: this.formulaire.visibleEnLigne,
      utilisateurIds: [...this.choisis()]
    };
    (edition === 'nouveau' ? this.service.creer(requete) : this.service.modifier(edition.id, requete)).subscribe({
      next: (l) => {
        this.edition.set(null);
        this.succes.set(premier
          ? `${l.nom} est enregistre. Tout votre stock actuel est maintenant au Magasin principal : transferez ce qui doit aller a l'entrepot.`
          : `${l.nom} est enregistre.`);
        this.charger();
      },
      error: (e) => this.erreurFormulaire.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }
}
