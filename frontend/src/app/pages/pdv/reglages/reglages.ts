import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { Observable, forkJoin } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { ArticlePdv, CategoriePdv } from '../../../core/models/pdv';
import { Associe, PartAssocie, ReglagesPdv } from '../../../core/models/options-pdv';
import { OptionsPdvService } from '../../../core/services/options-pdv.service';
import { PdvService } from '../../../core/services/pdv.service';
import { Modale } from '../../../shared/modale';
import { OptionSelect, SelectRecherche } from '../../../shared/select-recherche';
import { ReglagesMateriauxPdv } from '../reglages-materiaux/reglages-materiaux';

/** Ligne de rattachement en cours de saisie : un produit ou une categorie, et un taux. */
interface LignePart {
  cle: number;
  type: 'PRODUIT' | 'CATEGORIE';
  cibleId: number | null;
  taux: number | null;
}

/**
 * Reglages du point de vente, reserves a l'administrateur : les options qui ne
 * conviennent pas a tous les commerces (benefices des associes, circuit des
 * especes), desactivees par defaut, et les rattachements des associes.
 */
@Component({
  selector: 'app-reglages-pdv',
  imports: [FormsModule, DecimalPipe, RouterLink, Modale, SelectRecherche, ReglagesMateriauxPdv],
  templateUrl: './reglages.html',
  styleUrls: ['./reglages-blocs.scss', './reglages.scss']
})
export class ReglagesPdvPage implements OnInit {
  readonly options = inject(OptionsPdvService);
  private readonly pdv = inject(PdvService);
  readonly materiaux = inject(AuthService).estMateriaux();

  readonly formulaire = signal<ReglagesPdv | null>(null);
  readonly associes = signal<Associe[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly envoi = signal(false);
  /** Partie quincaillerie, enregistree avec les options par la meme barre. */
  readonly partieMateriaux = viewChild(ReglagesMateriauxPdv);
  /** Etat enregistre des options, pour reperer une modification et pouvoir l'annuler. */
  private enregistre = '';

  /** Sommaire de la page : un lien par bloc. */
  readonly sommaire = computed(() => [
    { id: 'reglage-associes', libelle: 'Benefices des associes', icone: '🤝', groupe: 'Options' },
    { id: 'reglage-circuit', libelle: 'Circuit des especes', icone: '🔐', groupe: 'Options' },
    { id: 'reglage-validation-depenses', libelle: 'Validation des depenses de caisse', icone: '✅', groupe: 'Options' },
    { id: 'reglage-verrou', libelle: 'Verrouillage de la caisse', icone: '🔒', groupe: 'Options' },
    ...(this.materiaux ? [
      { id: 'reglage-credit', libelle: 'Vente a credit', icone: '💳', groupe: 'Quincaillerie' },
      { id: 'reglage-devis', libelle: 'Devis', icone: '📄', groupe: 'Quincaillerie' },
      { id: 'reglage-facture', libelle: 'Facture et TVA', icone: '🧾', groupe: 'Quincaillerie' },
      { id: 'reglage-conditionnements', libelle: 'Conditionnements', icone: '📦', groupe: 'Quincaillerie' },
      { id: 'reglage-livraison', libelle: 'Livraison en plusieurs fois', icone: '🚚', groupe: 'Quincaillerie' }
    ] : [])
  ]);

  // Rattachements d'un associe
  readonly articles = signal<ArticlePdv[]>([]);
  readonly categories = signal<CategoriePdv[]>([]);
  readonly associeOuvert = signal<Associe | null>(null);
  readonly lignes = signal<LignePart[]>([]);
  readonly erreurParts = signal<string | null>(null);
  private prochaineCle = 1;

  readonly optionsProduits = computed<OptionSelect[]>(() =>
    this.articles().map((a) => ({ valeur: a.id, libelle: a.nom, detail: a.code })));
  readonly optionsCategories = computed<OptionSelect[]>(() =>
    this.categories().map((c) => ({ valeur: c.id, libelle: c.nom, couleur: c.couleur })));

  ngOnInit(): void {
    this.options.lireReglages().subscribe({
      next: (r) => this.appliquer(r),
      error: (e) => this.erreur.set(e?.error?.message ?? 'Chargement des reglages impossible.')
    });
  }

  /** Une modification attend d'etre enregistree, dans les options ou la quincaillerie. */
  modifie(): boolean {
    const f = this.formulaire();
    return (!!f && JSON.stringify(f) !== this.enregistre) || !!this.partieMateriaux()?.modifie();
  }

  annuler(): void {
    if (this.enregistre) {
      this.formulaire.set(JSON.parse(this.enregistre));
    }
    this.partieMateriaux()?.reinitialiser();
    this.erreur.set(null);
  }

  aller(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  private appliquer(r: ReglagesPdv): void {
    this.formulaire.set({ ...r });
    this.enregistre = JSON.stringify(this.formulaire());
    if (r.associesActif) {
      this.chargerAssocies();
    }
  }

  /** Enregistre ce qui a change : les options, la quincaillerie, ou les deux. */
  enregistrer(): void {
    const f = this.formulaire();
    if (!f || this.envoi()) {
      return;
    }
    const appels: Observable<unknown>[] = [];
    if (JSON.stringify(f) !== this.enregistre) {
      appels.push(this.options.modifierReglages({ ...f, ecartTolere: Math.max(0, Number(f.ecartTolere) || 0),
        minutesVerrouCaisse: Math.min(120, Math.max(1, Math.round(Number(f.minutesVerrouCaisse) || 5))) }));
    }
    const materiaux = this.partieMateriaux();
    if (materiaux?.modifie()) {
      appels.push(materiaux.enregistrer());
    }
    if (!appels.length) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.succes.set(null);
    forkJoin(appels).subscribe({
      next: () => {
        this.envoi.set(false);
        const r = this.options.reglages();
        if (r) {
          this.appliquer(r);
        }
        this.succes.set("Reglages enregistres : ils s'appliquent tout de suite.");
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(e?.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  chargerAssocies(): void {
    this.options.associes().subscribe({ next: (a) => this.associes.set(a) });
  }

  ouvrirParts(a: Associe): void {
    if (!this.articles().length) {
      this.pdv.articles({ actif: true, size: 2000 }).subscribe({ next: (p) => this.articles.set(p.content) });
      this.pdv.categories().subscribe({ next: (c) => this.categories.set(c) });
    }
    this.lignes.set(a.parts.map((p) => ({
      cle: this.prochaineCle++,
      type: p.articleId ? 'PRODUIT' : 'CATEGORIE',
      cibleId: p.articleId ?? p.categorieId ?? null,
      taux: p.taux
    })));
    if (!a.parts.length) {
      this.ajouterLigne();
    }
    this.erreurParts.set(null);
    this.associeOuvert.set(a);
  }

  ajouterLigne(): void {
    this.lignes.update((l) => [...l, { cle: this.prochaineCle++, type: 'CATEGORIE', cibleId: null, taux: null }]);
  }

  retirerLigne(cle: number): void {
    this.lignes.update((l) => l.filter((x) => x.cle !== cle));
  }

  modifierLigne<K extends keyof LignePart>(cle: number, champ: K, valeur: LignePart[K]): void {
    this.lignes.update((l) => l.map((x) => {
      if (x.cle !== cle) {
        return x;
      }
      // Changer de type efface la cible choisie, qui n'a plus de sens.
      return champ === 'type' ? { ...x, type: valeur as LignePart['type'], cibleId: null } : { ...x, [champ]: valeur };
    }));
  }

  enregistrerParts(): void {
    const a = this.associeOuvert();
    if (!a) {
      return;
    }
    const remplies = this.lignes().filter((l) => l.cibleId);
    if (remplies.some((l) => !l.taux || l.taux <= 0 || l.taux > 100)) {
      this.erreurParts.set('Chaque ligne demande un pourcentage entre 0 et 100.');
      return;
    }
    const parts: PartAssocie[] = remplies.map((l) => ({
      articleId: l.type === 'PRODUIT' ? l.cibleId : null,
      categorieId: l.type === 'CATEGORIE' ? l.cibleId : null,
      taux: l.taux as number
    }));
    this.options.remplacerParts(a.id, parts).subscribe({
      next: () => {
        this.associeOuvert.set(null);
        this.succes.set(`Rattachements de ${a.nom} enregistres.`);
        this.chargerAssocies();
      },
      error: (e) => this.erreurParts.set(e?.error?.message ?? 'Enregistrement impossible.')
    });
  }
}
