import { Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CaissePdv, CategoriePdv, MembrePersonnel } from '../../../core/models/pdv';
import { EcranPreparation, Imprimante, ImprimanteRequete, ListeEcrans } from '../../../core/models/restauration';
import { ImpressionService } from '../../../core/services/impression.service';
import { imprimerBonsCuisine } from '../../../shared/bon-cuisine';
import { PdvService } from '../../../core/services/pdv.service';
import { RestaurationService } from '../../../core/services/restauration.service';
import { Modale } from '../../../shared/modale';

/**
 * Postes de preparation crees par le gerant : chacun recoit les articles des
 * categories choisies (le grill, le bar, la patisserie...) sur un ecran ouvert
 * aux seuls utilisateurs affectes, sur une imprimante de cuisine, ou les deux.
 */
@Component({
  selector: 'app-ecrans-pdv',
  imports: [FormsModule, RouterLink, Modale],
  templateUrl: './ecrans.html',
  styleUrls: ['../caisses/caisses.scss', './ecrans.scss']
})
export class EcransPdv implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly pdv = inject(PdvService);
  private readonly impression = inject(ImpressionService);

  readonly liste = signal<ListeEcrans | null>(null);
  readonly categories = signal<CategoriePdv[]>([]);
  readonly personnel = signal<MembrePersonnel[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);

  readonly edition = signal<EcranPreparation | 'nouvel' | null>(null);
  formulaire = { nom: '', actif: true, afficherEcran: true, imprimanteId: null as number | null };

  // Imprimantes
  readonly imprimantes = signal<Imprimante[]>([]);
  readonly caisses = signal<CaissePdv[]>([]);
  readonly editionImprimante = signal<Imprimante | 'nouvelle' | null>(null);
  readonly erreurImprimante = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  formImprimante: ImprimanteRequete = this.imprimanteVide();
  readonly categoriesChoisies = signal<Set<number>>(new Set());
  readonly utilisateursChoisis = signal<Set<number>>(new Set());

  ngOnInit(): void {
    this.charger();
    this.pdv.categories().subscribe({ next: (c) => this.categories.set(c.filter((x) => x.actif)) });
    this.pdv.personnel().subscribe({ next: (p) => this.personnel.set(p.filter((m) => m.actif)) });
    this.pdv.caisses().subscribe({ next: (c) => this.caisses.set(c) });
    this.chargerImprimantes();
  }

  nouvel(): void {
    this.formulaire = { nom: '', actif: true, afficherEcran: true, imprimanteId: null };
    this.categoriesChoisies.set(new Set());
    this.utilisateursChoisis.set(new Set());
    this.erreurFormulaire.set(null);
    this.edition.set('nouvel');
  }

  modifier(ecran: EcranPreparation): void {
    this.formulaire = { nom: ecran.nom, actif: ecran.actif, afficherEcran: ecran.afficherEcran, imprimanteId: ecran.imprimanteId ?? null };
    this.categoriesChoisies.set(new Set(ecran.categories.map((c) => c.id)));
    this.utilisateursChoisis.set(new Set(ecran.utilisateurs.map((u) => u.id)));
    this.erreurFormulaire.set(null);
    this.edition.set(ecran);
  }

  basculer(ensemble: WritableSignal<Set<number>>, id: number): void {
    ensemble.update((ids) => {
      const suivant = new Set(ids);
      if (suivant.has(id)) {
        suivant.delete(id);
      } else {
        suivant.add(id);
      }
      return suivant;
    });
  }

  enregistrer(): void {
    const edition = this.edition();
    if (!edition || !this.formulaire.nom.trim()) {
      return;
    }
    const requete = {
      nom: this.formulaire.nom.trim(),
      actif: this.formulaire.actif,
      categorieIds: [...this.categoriesChoisies()],
      utilisateurIds: [...this.utilisateursChoisis()],
      afficherEcran: this.formulaire.afficherEcran,
      imprimanteId: this.formulaire.imprimanteId
    };
    const appel = edition === 'nouvel'
      ? this.service.creerEcran(requete)
      : this.service.modifierEcran(edition.id, requete);
    appel.subscribe({
      next: () => {
        this.edition.set(null);
        this.charger();
      },
      error: (e) => this.erreurFormulaire.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  /** Retire un ecran ou le remet en service, sans toucher a ses categories ni a son personnel. */
  basculerActivation(ecran: EcranPreparation): void {
    this.erreur.set(null);
    this.service.modifierEcran(ecran.id, {
      nom: ecran.nom,
      actif: !ecran.actif,
      categorieIds: ecran.categories.map((c) => c.id),
      utilisateurIds: ecran.utilisateurs.map((u) => u.id),
      afficherEcran: ecran.afficherEcran,
      imprimanteId: ecran.imprimanteId ?? null
    }).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(this.message(e, 'Changement impossible.'))
    });
  }

  // ------------------------------------------------------------ imprimantes

  nouvelleImprimante(): void {
    this.formImprimante = this.imprimanteVide();
    this.erreurImprimante.set(null);
    this.editionImprimante.set('nouvelle');
  }

  modifierImprimante(i: Imprimante): void {
    this.formImprimante = {
      nom: i.nom, mode: i.mode, adresseIp: i.adresseIp ?? '', port: i.port, largeurPapier: i.largeurPapier,
      copies: i.copies, coupe: i.coupe, bip: i.bip, caisseId: i.caisseId ?? null, actif: i.actif
    };
    this.erreurImprimante.set(null);
    this.editionImprimante.set(i);
  }

  enregistrerImprimante(): void {
    const edition = this.editionImprimante();
    if (!edition || !this.formImprimante.nom.trim()) {
      return;
    }
    const requete = { ...this.formImprimante, nom: this.formImprimante.nom.trim() };
    const appel = edition === 'nouvelle'
      ? this.impression.creerImprimante(requete)
      : this.impression.modifierImprimante(edition.id, requete);
    appel.subscribe({
      next: () => {
        this.editionImprimante.set(null);
        this.chargerImprimantes();
        this.charger();
      },
      error: (e) => this.erreurImprimante.set(this.message(e, 'Enregistrement impossible.'))
    });
  }

  /** Bon d'essai : imprime tout de suite ici en mode navigateur. */
  essai(i: Imprimante): void {
    this.erreur.set(null);
    this.succes.set(null);
    this.impression.essai(i.id).subscribe({
      next: (bon) => {
        if (i.mode === 'NAVIGATEUR') {
          this.impression.prendre([bon.id]).subscribe({ next: (pris) => imprimerBonsCuisine(pris.map((t) => t.bon)) });
        } else {
          this.succes.set(`Bon d'essai envoye a ${i.nom}.`);
        }
      },
      error: (e) => this.erreur.set(this.message(e, 'Essai impossible.'))
    });
  }

  basculerImprimante(i: Imprimante): void {
    this.erreur.set(null);
    this.impression.modifierImprimante(i.id, {
      nom: i.nom, mode: i.mode, adresseIp: i.adresseIp ?? null, port: i.port, largeurPapier: i.largeurPapier,
      copies: i.copies, coupe: i.coupe, bip: i.bip, caisseId: i.caisseId ?? null, actif: !i.actif
    }).subscribe({
      next: () => {
        this.chargerImprimantes();
        this.charger();
      },
      error: (e) => this.erreur.set(this.message(e, 'Changement impossible.'))
    });
  }

  private chargerImprimantes(): void {
    this.impression.imprimantes().subscribe({
      next: (l) => this.imprimantes.set(l),
      error: () => undefined
    });
  }

  private imprimanteVide(): ImprimanteRequete {
    return { nom: '', mode: 'NAVIGATEUR', adresseIp: '', port: 9100, largeurPapier: 80, copies: 1, coupe: true, bip: false,
      caisseId: null, actif: true };
  }

  private charger(): void {
    this.service.ecrans().subscribe({
      next: (l) => this.liste.set(l),
      error: (e) => this.erreur.set(this.message(e, 'Chargement des ecrans impossible.'))
    });
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
