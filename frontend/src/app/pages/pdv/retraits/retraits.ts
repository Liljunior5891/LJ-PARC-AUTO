import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { BonRetrait, ConflitHorsLigne, DisponibiliteLieu, LigneRetrait, StatutBonRetrait } from '../../../core/models/lieux';
import { BonLivraison } from '../../../core/models/materiaux';
import { LieuxService } from '../../../core/services/lieux.service';
import { MateriauxService } from '../../../core/services/materiaux.service';
import { RetraitsHorsLigneService } from '../../../core/services/retraits-hors-ligne.service';
import { LecteurDouchette, normaliserNumeroBon } from '../../../shared/code-barres';
import { Modale } from '../../../shared/modale';
import { OperationService } from '../../../core/operation/operation.service';
import { Page } from '../../../core/models/models';
import { SuiteDePages, SuiteListe, cumulerPage } from '../../../shared/suite-liste';

/** Lecteur natif des navigateurs mobiles recents ; absent ailleurs : la saisie du numero reste possible. */
declare const BarcodeDetector: {
  new (options: { formats: string[] }): { detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]> };
} | undefined;

/**
 * Comptoir du magasinier : il lit ou saisit le bon, voit les quantites a remettre
 * (jamais les prix) et emet le bon de livraison. Sans reseau, il sert depuis la copie
 * de l'appareil ; les remises partent au retour de la connexion.
 */
@Component({
  selector: 'app-retraits-pdv',
  imports: [FormsModule, DatePipe, DecimalPipe, Modale, SuiteListe],
  templateUrl: './retraits.html',
  styleUrl: './retraits.scss',
  host: { '(document:keydown)': 'captureDouchette($event)' }
})
export class RetraitsPdv implements OnInit, OnDestroy {
  private readonly service = inject(LieuxService);
  private readonly materiaux = inject(MateriauxService);
  private readonly route = inject(ActivatedRoute);
  readonly horsLigne = inject(RetraitsHorsLigneService);
  private readonly operation = inject(OperationService);
  readonly gerant = inject(AuthService).aRole('ADMIN', 'GERANT');

  readonly etat = this.service.etat;
  readonly pageBons = signal<Page<BonRetrait> | null>(null);
  readonly bons = computed(() => this.pageBons()?.content ?? []);
  private readonly suite = new SuiteDePages();
  /** Par defaut, tous les bons : ceux a servir viennent en premier. */
  readonly statut = signal<StatutBonRetrait | ''>('');
  readonly lieuFiltre = signal<number | null>(null);
  readonly conflits = signal<ConflitHorsLigne[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly dernierBon = signal<BonLivraison | null>(null);
  private readonly remiseDifferee = signal(false);
  saisie = '';

  /** Bons affiches : ceux du serveur, ou la copie de l'appareil sans connexion. */
  readonly affiches = computed(() => {
    const motif = this.motif().trim().toLowerCase();
    return this.horsLigne.enLigne()
      ? this.bons()
      : this.horsLigne.bons().filter((b) => !motif || b.numero.toLowerCase().includes(motif)
        || b.venteNumero.toLowerCase().includes(motif) || (b.clientNom ?? '').toLowerCase().includes(motif));
  });
  private readonly motif = signal('');

  // Fenetre de remise
  readonly bonOuvert = signal<BonRetrait | null>(null);
  readonly quantites = signal<Record<number, number | null>>({});
  readonly erreurRemise = signal<string | null>(null);
  readonly envoi = signal(false);
  recuPar = '';
  note = '';
  readonly remiseValide = computed(() => {
    const bon = this.bonOuvert();
    if (!bon) {
      return false;
    }
    const lignes = bon.lignes.filter((l) => l.reste > 0);
    const valeurs = lignes.map((l) => this.quantites()[l.ligneId] ?? 0);
    return valeurs.some((v) => v > 0) && lignes.every((l, i) => valeurs[i] >= 0 && valeurs[i] <= l.reste);
  });

  // Changement de lieu
  readonly aDeplacer = signal<BonRetrait | null>(null);
  readonly disponibilites = signal<DisponibiliteLieu[]>([]);
  readonly erreurDeplacement = signal<string | null>(null);
  nouveauLieu: number | null = null;

  // Lecteur de code QR
  readonly lecture = signal(false);
  readonly lecteurPossible = typeof BarcodeDetector !== 'undefined' && !!navigator.mediaDevices;
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  /** Lecture a la douchette hors de la zone de saisie : le bon s'ouvre directement. */
  private readonly douchette = new LecteurDouchette((code) => this.ouvrirParNumero(code));
  private flux?: MediaStream;
  private minuterieRecherche?: ReturnType<typeof setTimeout>;

  constructor() {
    // Au retour de la connexion, la liste reprend l'etat du serveur.
    effect(() => {
      if (this.horsLigne.enLigne()) {
        untracked(() => this.charger());
      }
    });
    // La file vient de se vider : le message « gardee sur l'appareil » n'est plus vrai.
    effect(() => {
      if (this.horsLigne.enAttente().length === 0 && untracked(() => this.remiseDifferee())) {
        untracked(() => {
          this.remiseDifferee.set(false);
          this.succes.set('Les remises faites sans connexion ont ete envoyees au serveur.');
          this.charger();
        });
      }
    });
  }

  ngOnInit(): void {
    this.service.chargerEtat();
    this.horsLigne.demarrer();
    const q = this.route.snapshot.queryParamMap.get('q');
    if (q) {
      this.saisie = q;
      this.motif.set(q);
    }
    this.charger();
    if (this.gerant) {
      this.chargerConflits();
    }
  }

  captureDouchette(e: KeyboardEvent): void {
    this.douchette.touche(e, !this.bonOuvert() && !this.aDeplacer() && !this.lecture());
  }

  ngOnDestroy(): void {
    this.douchette.arreter();
    this.arreterLecture();
    clearTimeout(this.minuterieRecherche);
  }

  charger(): void {
    if (!this.horsLigne.enLigne()) {
      return;
    }
    this.service.retraits({ q: this.motif().trim(), lieuId: this.lieuFiltre(), statut: this.statut(), page: this.suite.numero() }).subscribe({
      next: (p) => this.pageBons.set(cumulerPage(this.pageBons(), p)),
      error: (e) => {
        if (e?.status === 0) {
          this.horsLigne.enLigne.set(false);
        } else {
          this.erreur.set(e?.error?.message ?? 'Chargement des bons impossible.');
        }
      }
    });
  }

  /** Bas de la liste atteint : la page suivante rejoint les bons deja affiches. */
  suivante(): void {
    if (this.suite.demander(this.pageBons())) {
      this.charger();
    }
  }

  rechercher(valeur: string): void {
    const texte = normaliserNumeroBon(valeur);
    this.saisie = texte;
    this.motif.set(texte);
    clearTimeout(this.minuterieRecherche);
    this.minuterieRecherche = setTimeout(() => this.charger(), 250);
  }

  /** Entree ou lecture du code : le bon s'ouvre directement. */
  ouvrirParNumero(numero: string): void {
    const n = normaliserNumeroBon(numero);
    if (!n) {
      return;
    }
    this.erreur.set(null);
    const local = this.horsLigne.bons().find((b) => b.numero.toLowerCase() === n.toLowerCase());
    if (!this.horsLigne.enLigne()) {
      if (local) {
        this.ouvrir(local);
      } else {
        this.erreur.set(`Le bon ${n} n'est pas sur cet appareil : il est peut-etre deja servi, ou a verifier au retour du reseau.`);
      }
      return;
    }
    this.service.retraitParNumero(n).subscribe({
      next: (b) => {
        this.saisie = '';
        this.motif.set('');
        if (b.statut === 'A_SERVIR') {
          this.ouvrir(b);
        } else {
          this.erreur.set(`Le bon ${b.numero} ${b.statut === 'SERVI' ? 'est deja entierement servi' : 'est annule'}.`);
        }
      },
      error: (e) => {
        if (e?.status === 0 && local) {
          this.horsLigne.enLigne.set(false);
          this.ouvrir(local);
        } else {
          this.erreur.set(e?.error?.message ?? `Bon ${n} introuvable.`);
        }
      }
    });
  }

  ouvrir(bon: BonRetrait): void {
    this.bonOuvert.set(bon);
    this.quantites.set(Object.fromEntries(bon.lignes.filter((l) => l.reste > 0).map((l) => [l.ligneId, l.reste])));
    this.recuPar = '';
    this.note = '';
    this.erreurRemise.set(null);
  }

  changerQuantite(l: LigneRetrait, valeur: number | null): void {
    this.quantites.update((q) => ({ ...q, [l.ligneId]: valeur }));
  }

  async remettre(): Promise<void> {
    const bon = this.bonOuvert();
    if (!bon || !this.remiseValide() || this.envoi()) {
      return;
    }
    // Confirmation demandee ici, avant l'envoi : un refus ne doit pas passer pour une coupure reseau.
    const accepte = await this.operation.demander({
      titre: `Remettre la marchandise du bon ${bon.numero} ?`,
      message: 'Le stock sort du lieu et un bon de livraison est emis pour le client.',
      libelleAction: 'Remettre'
    });
    if (!accepte || this.envoi() || this.bonOuvert() !== bon) {
      return;
    }
    const lignes = bon.lignes
      .map((l) => ({ ligneId: l.ligneId, quantite: this.quantites()[l.ligneId] ?? 0 }))
      .filter((l) => l.quantite > 0);
    const recuPar = this.recuPar.trim() || null;
    const note = this.note.trim() || null;
    if (!this.horsLigne.enLigne()) {
      this.differer(bon, lignes, recuPar, note);
      return;
    }
    this.envoi.set(true);
    this.service.remettre(bon.id, { lignes, recuPar, note }).subscribe({
      next: (livraison) => {
        this.envoi.set(false);
        this.bonOuvert.set(null);
        this.dernierBon.set(livraison);
        this.succes.set(`Bon ${bon.numero} servi : bon de livraison ${livraison.numero}.`);
        this.charger();
        this.horsLigne.rafraichir();
      },
      error: (e) => {
        this.envoi.set(false);
        if (e?.status === 0) {
          this.differer(bon, lignes, recuPar, note);
        } else {
          this.erreurRemise.set(e?.error?.message ?? 'Remise impossible.');
        }
      }
    });
  }

  private differer(bon: BonRetrait, lignes: { ligneId: number; quantite: number }[], recuPar: string | null, note: string | null): void {
    this.horsLigne.mettreEnAttente(bon, lignes, recuPar, note);
    this.remiseDifferee.set(true);
    this.bonOuvert.set(null);
    this.dernierBon.set(null);
    this.succes.set(`Remise du bon ${bon.numero} gardee sur l'appareil : elle partira au retour du reseau. Faites signer un recu papier.`);
  }

  imprimerLivraison(b: BonLivraison): void {
    this.service.livraisonPdf(b.id).subscribe({ next: (pdf) => this.materiaux.ouvrirPdf(pdf) });
  }

  imprimerRetrait(b: BonRetrait): void {
    this.service.retraitPdf(b.id).subscribe({
      next: (pdf) => this.materiaux.ouvrirPdf(pdf),
      error: () => this.erreur.set('Le bon ' + b.numero + ' ne peut pas etre imprime pour le moment.')
    });
  }

  // ------------------------------------------- cartes : 5 produits, puis « + plus »

  readonly APERCU = 5;
  private readonly deplies = signal<ReadonlySet<number>>(new Set());

  deplie(id: number): boolean {
    return this.deplies().has(id);
  }

  basculer(id: number): void {
    this.deplies.update((s) => {
      const copie = new Set(s);
      if (!copie.delete(id)) {
        copie.add(id);
      }
      return copie;
    });
  }

  // ---------------------------------------------------------- changement de lieu

  ouvrirDeplacement(b: BonRetrait): void {
    this.aDeplacer.set(b);
    this.nouveauLieu = null;
    this.erreurDeplacement.set(null);
    this.service.disponibilites(b.lignes.map((l) => l.articleId)).subscribe({
      next: (d) => this.disponibilites.set(d.filter((x) => x.lieuId !== b.lieuId))
    });
  }

  dispoDe(lieuId: number, articleId: number): number {
    return this.disponibilites().find((d) => d.lieuId === lieuId && d.articleId === articleId)?.disponible ?? 0;
  }

  autresLieux(b: BonRetrait) {
    return (this.etat()?.lieux ?? []).filter((l) => l.id !== b.lieuId);
  }

  async deplacer(): Promise<void> {
    const b = this.aDeplacer();
    const lieu = this.nouveauLieu;
    if (!b || !lieu) {
      return;
    }
    const nom = (this.etat()?.lieux ?? []).find((l) => l.id === lieu)?.nom ?? 'l\'autre lieu';
    const accepte = await this.operation.demander({
      titre: `Faire retirer le reste a ${nom} ?`,
      message: `Le bon ${b.numero} est remplace par un nouveau bon, a remettre au client.`,
      libelleAction: 'Changer de lieu'
    });
    if (!accepte) {
      return;
    }
    this.service.changerLieu(b.id, lieu).subscribe({
      next: (nouveau) => {
        this.aDeplacer.set(null);
        this.succes.set(`Le reste se retire maintenant a ${nouveau.lieuNom} avec le bon ${nouveau.numero}. Remettez ce nouveau bon au client.`);
        this.imprimerRetrait(nouveau);
        this.charger();
      },
      error: (e) => this.erreurDeplacement.set(e?.error?.message ?? 'Changement de lieu impossible.')
    });
  }

  // -------------------------------------------------------------------- conflits

  chargerConflits(): void {
    this.service.conflits().subscribe({ next: (c) => this.conflits.set(c), error: () => this.conflits.set([]) });
  }

  async traiter(c: ConflitHorsLigne): Promise<void> {
    const accepte = await this.operation.demander({
      titre: 'Marquer ce conflit comme traite ?',
      message: 'Il disparait de la liste des remises a regulariser.',
      libelleAction: 'Marquer traite'
    });
    if (!accepte) {
      return;
    }
    this.service.traiterConflit(c.id).subscribe({ next: () => this.chargerConflits() });
  }

  // ------------------------------------------------------------------ code QR

  async demarrerLecture(): Promise<void> {
    this.erreur.set(null);
    try {
      this.flux = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
      this.lecture.set(true);
      setTimeout(() => void this.lire());
    } catch {
      this.erreur.set("La camera n'est pas accessible : saisissez le numero du bon.");
    }
  }

  private async lire(): Promise<void> {
    const video = this.video()?.nativeElement;
    if (!video || !this.flux || typeof BarcodeDetector === 'undefined') {
      return;
    }
    video.srcObject = this.flux;
    await video.play();
    const detecteur = new BarcodeDetector({ formats: ['qr_code', 'code_128'] });
    while (this.lecture()) {
      const codes = await detecteur.detect(video).catch(() => []);
      if (codes.length) {
        this.arreterLecture();
        this.ouvrirParNumero(codes[0].rawValue);
        return;
      }
      await new Promise((attente) => setTimeout(attente, 250));
    }
  }

  arreterLecture(): void {
    this.lecture.set(false);
    this.flux?.getTracks().forEach((t) => t.stop());
    this.flux = undefined;
  }
}
