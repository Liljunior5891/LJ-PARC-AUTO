import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { CompteConvertible, EmployeAvecPin, EmployePresence, EmployeRequete, LieuPresence } from '../../../core/models/presence';
import { PresenceService } from '../../../core/services/presence.service';
import { Modale } from '../../../shared/modale';
import { imprimerDocument } from '../../../shared/ticket-pdv';

/**
 * Employes qui pointent : fiche, lieux, PIN (montre une seule fois a la
 * creation ou a la reinitialisation) et conversion d'un compte utilisateur.
 */
@Component({
  selector: 'app-employes-presence',
  imports: [FormsModule, RouterLink, Modale],
  templateUrl: './employes.html',
  styleUrl: './employes.scss'
})
export class EmployesPresence implements OnInit {
  private readonly service = inject(PresenceService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);

  readonly employes = signal<EmployePresence[]>([]);
  readonly lieux = signal<LieuPresence[]>([]);
  readonly comptes = signal<CompteConvertible[]>([]);
  readonly recherche = signal('');
  readonly voirInactifs = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);
  readonly enCours = signal(false);

  readonly edition = signal<EmployePresence | 'nouvel' | null>(null);
  formulaire: EmployeRequete = this.vide();
  readonly lieuxChoisis = signal<Set<number>>(new Set());
  /** PIN tout juste attribue : affiche une seule fois. */
  readonly pinAffiche = signal<EmployeAvecPin | null>(null);
  readonly aReinitialiser = signal<EmployePresence | null>(null);

  readonly filtres = computed(() => {
    const q = this.recherche().trim().toLowerCase();
    return this.employes().filter((e) => (this.voirInactifs() || e.actif)
      && (!q || [e.nomComplet, e.matricule, e.fonction, e.service, e.telephone].some((v) => v?.toLowerCase().includes(q))));
  });

  ngOnInit(): void {
    this.charger();
    this.service.lieux().subscribe({ next: (l) => this.lieux.set(l.filter((x) => x.actif)) });
    this.service.comptesConvertibles().subscribe({
      next: (c) => {
        this.comptes.set(c);
        // Arrivee depuis la page Utilisateurs : « Creer sa fiche employe ».
        const depuis = Number(this.route.snapshot.queryParamMap.get('utilisateur'));
        const compte = depuis ? c.find((x) => x.id === depuis) : null;
        if (compte) {
          this.nouvel();
          this.convertir(compte);
        }
      }
    });
  }

  nouvel(): void {
    this.formulaire = this.vide();
    this.lieuxChoisis.set(new Set(this.lieux().length === 1 ? [this.lieux()[0].id] : []));
    this.erreurFormulaire.set(null);
    this.edition.set('nouvel');
  }

  modifier(e: EmployePresence): void {
    this.formulaire = {
      nom: e.nom, prenom: e.prenom ?? '', telephone: e.telephone ?? '', email: e.email ?? '', fonction: e.fonction ?? '',
      service: e.service ?? '', dateEmbauche: e.dateEmbauche ?? null, lieuIds: [], utilisateurId: e.utilisateurId ?? null,
      actif: e.actif
    };
    this.lieuxChoisis.set(new Set(e.lieux.map((l) => l.id)));
    this.erreurFormulaire.set(null);
    this.edition.set(e);
  }

  /** Remplit la fiche avec un compte utilisateur, qui lui reste relie. */
  convertir(compte: CompteConvertible | null): void {
    if (!compte) {
      this.formulaire.utilisateurId = null;
      return;
    }
    this.formulaire = {
      ...this.formulaire,
      nom: compte.nom, prenom: compte.prenom ?? '', email: compte.email, telephone: compte.telephone ?? '',
      utilisateurId: compte.id
    };
  }

  compteChoisi(id: number | null): void {
    this.convertir(id ? this.comptes().find((c) => c.id === id) ?? null : null);
  }

  basculerLieu(id: number): void {
    this.lieuxChoisis.update((ids) => {
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
    this.enCours.set(true);
    const requete: EmployeRequete = { ...this.formulaire, lieuIds: [...this.lieuxChoisis()] };
    if (edition === 'nouvel') {
      this.service.creerEmploye(requete).subscribe({
        next: (r) => {
          this.enCours.set(false);
          this.edition.set(null);
          this.pinAffiche.set(r);
          this.charger();
          this.rafraichirComptes();
        },
        error: (e) => this.echec(e)
      });
    } else {
      this.service.modifierEmploye(edition.id, requete).subscribe({
        next: () => {
          this.enCours.set(false);
          this.edition.set(null);
          this.charger();
          this.rafraichirComptes();
        },
        error: (e) => this.echec(e)
      });
    }
  }

  reinitialiser(): void {
    const e = this.aReinitialiser();
    if (!e) {
      return;
    }
    this.service.reinitialiserPin(e.id).subscribe({
      next: (r) => {
        this.aReinitialiser.set(null);
        this.pinAffiche.set(r);
      },
      error: (err) => this.erreur.set(err?.error?.message ?? 'Reinitialisation impossible.')
    });
  }

  /** Petite carte a remettre a l'employe, avec son PIN. */
  imprimerCarte(r: EmployeAvecPin): void {
    const e = r.employe;
    imprimerDocument(`<!doctype html><html lang="fr"><head><meta charset="utf-8"><title>PIN ${e.matricule}</title>
<style>
  @page { size: 85mm 55mm; margin: 0; }
  body { margin: 0; width: 85mm; height: 55mm; font-family: Arial, sans-serif; display: flex; flex-direction: column;
         justify-content: center; align-items: center; gap: 2mm; border: 1px dashed #999; box-sizing: border-box; }
  h1 { margin: 0; font-size: 14pt; } small { color: #555; }
  .pin { font: 700 24pt 'Courier New', monospace; letter-spacing: 3mm; }
</style></head><body>
  <h1>${this.echapper(e.nomComplet)}</h1><small>${this.echapper(e.matricule)}</small>
  <div class="pin">${r.pin}</div>
  <small>PIN de pointage personnel · a garder secret</small>
</body></html>`);
  }

  peutGererLieux(): boolean {
    return this.auth.aRole('ADMIN', 'GERANT');
  }

  private echec(e: unknown): void {
    this.enCours.set(false);
    this.erreurFormulaire.set((e as { error?: { message?: string } })?.error?.message ?? 'Enregistrement impossible.');
  }

  private charger(): void {
    this.service.employes().subscribe({
      next: (e) => this.employes.set(e),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement impossible.')
    });
  }

  private rafraichirComptes(): void {
    this.service.comptesConvertibles().subscribe({ next: (c) => this.comptes.set(c) });
  }

  private vide(): EmployeRequete {
    return { nom: '', prenom: '', telephone: '', email: '', fonction: '', service: '', dateEmbauche: null, lieuIds: [],
      utilisateurId: null, actif: true };
  }

  private echapper(v: string): string {
    return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }
}
