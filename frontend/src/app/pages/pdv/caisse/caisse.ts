import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import {
  ArticlePdv,
  CategoriePdv,
  MODES_PAIEMENT_PDV,
  ModePaiementPdv,
  Ticket
} from '../../../core/models/pdv';
import { PdvService } from '../../../core/services/pdv.service';
import { SessionTravailService } from '../../../core/services/session-travail.service';
import { BandeauSession } from '../../../shared/bandeau-session';
import { BoutonEspeces } from '../../../shared/bouton-especes';
import { SocieteService } from '../../../core/services/societe.service';
import { Modale } from '../../../shared/modale';
import { imprimerTicket } from '../../../shared/ticket-pdv';

/** Ligne du panier en cours de constitution. */
interface LignePanier {
  article: ArticlePdv;
  quantite: number;
}

/** Billets proposes en raccourci pour le montant recu en especes. */
const BILLETS = [500, 1000, 2000, 5000, 10000];

/**
 * Caisse de la boutique, au meme format que celle du restaurant mais sans
 * table ni cuisine : les produits en photo a gauche, la commande du client a
 * droite, l'encaissement en une fenetre.
 * <p>
 * La recherche garde le focus pour la douchette (Entree valide le code lu).
 * Le panier reste dans le navigateur jusqu'a l'encaissement ; les prix
 * affiches viennent du catalogue, le serveur recalcule tout et fait foi.
 */
@Component({
  selector: 'app-caisse',
  imports: [FormsModule, DecimalPipe, Modale, BandeauSession, BoutonEspeces],
  templateUrl: './caisse.html',
  styleUrls: ['../commande/commande.scss', './caisse.scss']
})
export class Caisse implements OnInit {
  private readonly service = inject(PdvService);
  private readonly auth = inject(AuthService);
  private readonly societeService = inject(SocieteService);

  readonly modes = MODES_PAIEMENT_PDV;
  readonly estGestionnaire = this.auth.aRole('ADMIN', 'GERANT');
  readonly vendeur = this.auth.utilisateur()?.nomComplet ?? '';

  /** Session ouverte par un gerant sur laquelle le poste encaisse : indefinie tant qu'elle n'est pas lue, nulle si aucune. */
  private readonly sessions = inject(SessionTravailService);
  readonly session = this.sessions.courante;
  readonly articles = signal<ArticlePdv[]>([]);
  readonly categories = signal<CategoriePdv[]>([]);
  readonly categorieChoisie = signal<number | null>(null);
  readonly recherche = signal('');
  readonly panier = signal<LignePanier[]>([]);
  readonly remise = signal(0);
  readonly erreur = signal<string | null>(null);
  readonly information = signal<string | null>(null);
  /** Sur telephone, une seule vue a la fois : les produits ou la commande. */
  readonly vueMobile = signal<'carte' | 'commande'>('carte');
  clientNom = '';

  // Encaissement
  readonly paiement = signal(false);
  readonly mode = signal<ModePaiementPdv>('ESPECES');
  readonly montantRecu = signal<number | null>(null);
  reference = '';
  readonly enCours = signal(false);
  readonly dernierTicket = signal<Ticket | null>(null);

  private readonly saisie = viewChild<ElementRef<HTMLInputElement>>('saisie');

  readonly articlesAffiches = computed(() => {
    const motif = this.recherche().trim().toLowerCase();
    const categorie = this.categorieChoisie();
    return this.articles().filter(
      (a) =>
        (categorie === null || a.categorieId === categorie) &&
        (!motif || a.nom.toLowerCase().includes(motif) || a.code.toLowerCase().includes(motif) ||
          (a.codeBarres ?? '').includes(motif))
    );
  });

  /** Nombre de produits par categorie, affiche sur les cartes de categorie. */
  readonly parCategorie = computed(() => {
    const nombres = new Map<number, number>();
    for (const a of this.articles()) {
      if (a.categorieId) {
        nombres.set(a.categorieId, (nombres.get(a.categorieId) ?? 0) + 1);
      }
    }
    return nombres;
  });

  /** Ligne du panier par produit : la carte du produit affiche alors son compteur. */
  readonly dansLePanier = computed(() => new Map(this.panier().map((l) => [l.article.id, l])));

  readonly totalBrut = computed(() =>
    this.panier().reduce((somme, l) => somme + l.article.prixVente * l.quantite, 0)
  );
  readonly total = computed(() => Math.max(0, this.totalBrut() - (this.remise() || 0)));
  readonly nombreArticles = computed(() => this.panier().reduce((somme, l) => somme + l.quantite, 0));
  readonly monnaie = computed(() => Math.max(0, (this.montantRecu() ?? 0) - this.total()));
  readonly billets = computed(() => {
    const total = this.total();
    const suggestions = new Set<number>([total, Math.ceil(total / 1000) * 1000]);
    BILLETS.filter((b) => b > total).forEach((b) => suggestions.add(b));
    return [...suggestions].filter((v) => v >= total).sort((a, b) => a - b).slice(0, 5);
  });
  readonly paiementPossible = computed(
    () => this.panier().length > 0 && (this.mode() !== 'ESPECES' || (this.montantRecu() ?? 0) >= this.total())
  );

  ngOnInit(): void {
    this.service.catalogueCaisse().subscribe({ next: (a) => this.articles.set(a) });
    this.service.categories().subscribe({ next: (c) => this.categories.set(c.filter((x) => x.actif)) });
    this.refocaliser();
  }

  // -------------------------------------------------------------- panier

  /** Entree dans la recherche : un code lu a la douchette, ou le seul produit filtre. */
  valider(): void {
    const valeur = this.recherche().trim();
    if (!valeur) {
      return;
    }
    const exact = this.articles().find(
      (a) => a.codeBarres === valeur || a.code.toLowerCase() === valeur.toLowerCase()
    );
    if (exact) {
      this.ajouter(exact);
      this.recherche.set('');
      return;
    }
    const filtres = this.articlesAffiches();
    if (filtres.length === 1) {
      this.ajouter(filtres[0]);
      this.recherche.set('');
      return;
    }
    // Code absent du catalogue charge (produit cree depuis) : le serveur tranche.
    this.service.articleParCode(valeur).subscribe({
      next: (article) => {
        this.articles.update((liste) => [...liste, article]);
        this.ajouter(article);
        this.recherche.set('');
      },
      error: () => this.signaler(`Aucun produit pour « ${valeur} »`)
    });
  }

  ajouter(article: ArticlePdv): void {
    const dejaPris = this.dansLePanier().get(article.id)?.quantite ?? 0;
    if (article.suiviStock && dejaPris + 1 > article.stockActuel) {
      this.signaler(`Stock epuise pour ${article.nom}`);
      return;
    }
    this.panier.update((lignes) => {
      const existante = lignes.find((l) => l.article.id === article.id);
      return existante
        ? lignes.map((l) => (l === existante ? { ...l, quantite: l.quantite + 1 } : l))
        : [...lignes, { article, quantite: 1 }];
    });
    this.information.set(null);
    this.refocaliser();
  }

  changerQuantite(ligne: LignePanier, quantite: number): void {
    if (!quantite || quantite <= 0) {
      this.retirer(ligne);
      return;
    }
    if (ligne.article.suiviStock && quantite > ligne.article.stockActuel) {
      this.signaler(`Seulement ${ligne.article.stockActuel} ${ligne.article.nom} en stock`);
      quantite = ligne.article.stockActuel;
    }
    this.panier.update((lignes) => lignes.map((l) => (l.article.id === ligne.article.id ? { ...l, quantite } : l)));
  }

  retirer(ligne: LignePanier): void {
    this.panier.update((lignes) => lignes.filter((l) => l.article.id !== ligne.article.id));
    this.refocaliser();
  }

  viderPanier(): void {
    this.panier.set([]);
    this.remise.set(0);
    this.clientNom = '';
    this.refocaliser();
  }

  // -------------------------------------------------------- encaissement

  ouvrirPaiement(mode: ModePaiementPdv = 'ESPECES'): void {
    if (this.panier().length === 0) {
      return;
    }
    if (!this.session()) {
      // Pas de vente sans session : seul un gerant l'ouvre.
      this.signaler(this.sessions.choixRequis()
        ? 'Choisissez d\'abord la caisse sur laquelle vous encaissez.'
        : 'Caisse fermee : un gerant doit ouvrir la session avant toute vente.');
      return;
    }
    this.mode.set(mode);
    this.montantRecu.set(this.total());
    this.reference = '';
    this.erreur.set(null);
    this.paiement.set(true);
  }

  encaisser(): void {
    if (this.enCours() || !this.paiementPossible()) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set(null);
    this.service
      .encaisser({
        lignes: this.panier().map((l) => ({ articleId: l.article.id, quantite: l.quantite })),
        remise: this.remise() || 0,
        modePaiement: this.mode(),
        montantRecu: this.mode() === 'ESPECES' ? this.montantRecu() : null,
        referencePaiement: this.reference || undefined,
        clientNom: this.clientNom.trim() || undefined,
        sessionId: this.session()?.id
      })
      .subscribe({
        next: (ticket) => {
          this.enCours.set(false);
          this.paiement.set(false);
          this.dernierTicket.set(ticket);
          this.decompterStock();
          this.panier.set([]);
          this.remise.set(0);
          this.clientNom = '';
          this.vueMobile.set('carte');
          this.sessions.rafraichir();
        },
        error: (e) => {
          this.enCours.set(false);
          this.erreur.set(this.message(e, 'Encaissement impossible.'));
        }
      });
  }

  imprimer(ticket: Ticket): void {
    // Le parametrage complet porte l'adresse et le NIF imprimes en tete du recu.
    this.societeService.lire().subscribe({
      next: (societe) => imprimerTicket(ticket, societe),
      error: () => imprimerTicket(ticket, null)
    });
  }

  nouvelleVente(): void {
    this.dernierTicket.set(null);
    this.information.set(null);
    this.refocaliser();
  }

  // ------------------------------------------------------------- interne

  /** Le stock affiche suit les ventes du poste sans recharger tout le catalogue. */
  private decompterStock(): void {
    const vendus = new Map(this.panier().map((l) => [l.article.id, l.quantite]));
    this.articles.update((liste) =>
      liste.map((a) =>
        a.suiviStock && vendus.has(a.id) ? { ...a, stockActuel: a.stockActuel - (vendus.get(a.id) ?? 0) } : a
      )
    );
  }

  private signaler(message: string): void {
    this.information.set(message);
    this.refocaliser();
  }

  private refocaliser(): void {
    // Sur telephone, le clavier ne doit pas surgir a chaque produit touche.
    if (window.matchMedia('(min-width: 821px)').matches) {
      setTimeout(() => this.saisie()?.nativeElement.focus());
    }
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
