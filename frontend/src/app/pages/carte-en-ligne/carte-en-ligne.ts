import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { AppelClient, CarteEnLigne, StatutLigneCommande, SuiviClient } from '../../core/models/restauration';
import { RestaurationService } from '../../core/services/restauration.service';

type Article = CarteEnLigne['articles'][number];

interface LignePanier {
  articleId: number;
  quantite: number;
  note?: string;
}

/** Etapes de la commande, dites au client avec ses mots a lui. */
const ETAPES_CLIENT: Record<StatutLigneCommande, { libelle: string; classe: string }> = {
  PROPOSEE: { libelle: 'Attend le serveur', classe: 'attente' },
  BROUILLON: { libelle: 'Note par le serveur', classe: 'attente' },
  A_PREPARER: { libelle: 'En cuisine', classe: 'cuisine' },
  EN_PREPARATION: { libelle: 'En preparation', classe: 'cuisine' },
  PRETE: { libelle: 'Arrive bientot', classe: 'pret' },
  SERVIE: { libelle: 'Servi', classe: 'servi' },
  ANNULEE: { libelle: 'Annule', classe: 'annule' }
};

const RAFRAICHISSEMENT_MS = 10000;
const QUANTITE_MAX = 20;

/**
 * Carte en ligne d'une table, ouverte par son code QR depuis le telephone du
 * client, sans compte ni application a installer.
 * <p>
 * Le client parcourt la carte, compose son panier et l'envoie ; selon le
 * reglage de l'etablissement, un serveur valide la commande ou elle part
 * directement en cuisine. Il suit ensuite sa commande, appelle un serveur ou
 * demande l'addition, qu'il regle a la fin aupres du serveur.
 */
@Component({
  selector: 'app-carte-en-ligne',
  imports: [DecimalPipe, FormsModule],
  templateUrl: './carte-en-ligne.html',
  styleUrl: './carte-en-ligne.scss'
})
export class CarteEnLignePage implements OnInit {
  private readonly service = inject(RestaurationService);
  private readonly route = inject(ActivatedRoute);
  private readonly titre = inject(Title);
  private readonly destroyRef = inject(DestroyRef);

  readonly etapes = ETAPES_CLIENT;

  readonly carte = signal<CarteEnLigne | null>(null);
  readonly suivi = signal<SuiviClient | null>(null);
  readonly introuvable = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly confirmation = signal<string | null>(null);
  readonly onglet = signal<'carte' | 'commande'>('carte');
  readonly categorie = signal<number | null>(null);
  readonly panier = signal<LignePanier[]>([]);
  readonly panierOuvert = signal(false);
  readonly envoi = signal(false);

  private jeton = '';

  private readonly parId = computed(() => new Map((this.carte()?.articles ?? []).map((a) => [a.id, a])));

  readonly articlesAffiches = computed(() => {
    const c = this.categorie();
    return (this.carte()?.articles ?? []).filter((a) => c === null || a.categorieId === c);
  });

  readonly nombreArticles = computed(() => this.panier().reduce((s, l) => s + l.quantite, 0));
  readonly totalPanier = computed(() =>
    this.panier().reduce((s, l) => s + (this.parId().get(l.articleId)?.prix ?? 0) * l.quantite, 0)
  );

  ngOnInit(): void {
    this.jeton = this.route.snapshot.paramMap.get('jeton') ?? '';
    this.panier.set(this.lirePanier());
    this.service.carteEnLigne(this.jeton).subscribe({
      next: (carte) => {
        this.carte.set(carte);
        this.suivi.set(carte.suivi ?? null);
        this.titre.setTitle(`${carte.etablissement} — Table ${carte.table}`);
        // Les articles retires de la carte depuis la derniere visite quittent le panier.
        this.panier.update((p) => p.filter((l) => carte.articles.some((a) => a.id === l.articleId && a.disponible)));
      },
      error: (e) => this.introuvable.set(this.message(e, 'Ce code QR n\'est pas reconnu.'))
    });
    const minuterie = setInterval(() => this.rafraichirSuivi(), RAFRAICHISSEMENT_MS);
    this.destroyRef.onDestroy(() => clearInterval(minuterie));
  }

  article(id: number): Article | undefined {
    return this.parId().get(id);
  }

  icone(categorieId?: number): string {
    return this.carte()?.categories.find((c) => c.id === categorieId)?.icone || '🍽️';
  }

  quantite(articleId: number): number {
    return this.panier().filter((l) => l.articleId === articleId).reduce((s, l) => s + l.quantite, 0);
  }

  ajouter(a: Article): void {
    if (!a.disponible) {
      return;
    }
    this.changer(a.id, 1);
  }

  changer(articleId: number, delta: number): void {
    this.panier.update((lignes) => {
      const ligne = lignes.find((l) => l.articleId === articleId);
      if (!ligne) {
        return delta > 0 ? [...lignes, { articleId, quantite: 1 }] : lignes;
      }
      const quantite = Math.min(QUANTITE_MAX, ligne.quantite + delta);
      return quantite <= 0 ? lignes.filter((l) => l !== ligne) : lignes.map((l) => (l === ligne ? { ...l, quantite } : l));
    });
    this.memoriserPanier();
  }

  changerNote(ligne: LignePanier, note: string): void {
    this.panier.update((lignes) => lignes.map((l) => (l === ligne ? { ...l, note } : l)));
    this.memoriserPanier();
  }

  envoyer(): void {
    if (this.envoi() || this.panier().length === 0) {
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const lignes = this.panier().map((l) => ({ articleId: l.articleId, quantite: l.quantite, note: l.note?.trim() || undefined }));
    this.service.commanderEnLigne(this.jeton, lignes).subscribe({
      next: (suivi) => {
        this.envoi.set(false);
        this.suivi.set(suivi);
        this.panier.set([]);
        this.memoriserPanier();
        this.panierOuvert.set(false);
        this.onglet.set('commande');
        this.confirmation.set(this.carte()?.validationServeur
          ? 'Commande transmise ! Un serveur la confirme dans un instant.'
          : 'Commande envoyee en cuisine. Bon appetit !');
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(this.message(e, 'Envoi impossible. Reessayez ou appelez un serveur.'));
      }
    });
  }

  appeler(motif: AppelClient): void {
    this.erreur.set(null);
    this.service.appelerServeur(this.jeton, motif).subscribe({
      next: (suivi) => {
        this.suivi.set(suivi);
        this.confirmation.set(motif === 'ADDITION'
          ? 'L\'addition arrive : un serveur vient a votre table.'
          : 'Un serveur a ete prevenu, il arrive.');
      },
      error: (e) => this.erreur.set(this.message(e, 'Appel impossible pour le moment.'))
    });
  }

  private rafraichirSuivi(): void {
    if (!this.carte()) {
      return;
    }
    this.service.suiviClient(this.jeton).subscribe({
      next: (suivi) => this.suivi.set(suivi ?? null),
      error: () => undefined
    });
  }

  // Le panier survit a un rechargement de la page, sur ce telephone seulement.
  private cle(): string {
    return `carte.panier.${this.jeton}`;
  }

  private lirePanier(): LignePanier[] {
    try {
      const brut = localStorage.getItem(this.cle());
      const lignes = brut ? (JSON.parse(brut) as LignePanier[]) : [];
      return Array.isArray(lignes) ? lignes.filter((l) => l && typeof l.articleId === 'number' && l.quantite > 0) : [];
    } catch {
      return [];
    }
  }

  private memoriserPanier(): void {
    try {
      localStorage.setItem(this.cle(), JSON.stringify(this.panier()));
    } catch {
      // Navigation privee : le panier reste en memoire le temps de la visite.
    }
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
