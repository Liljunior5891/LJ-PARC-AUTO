import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Title } from '@angular/platform-browser';
import {
  ModePaiementEnLigne,
  ModeRemise,
  OffrePublique,
  SuiviEnLigne,
  Vitrine
} from '../../core/models/marketplace';
import { BoutiquePubliqueService } from '../../core/services/marketplace.service';
import { IconeVitrine } from './icone-vitrine';

/** Coordonnees du client, gardees d'une commande a l'autre sur cet appareil. */
interface Coordonnees {
  /** Nom de famille. */
  nom: string;
  prenoms: string;
  telephone: string;
  email: string;
  adresse: string;
}

/**
 * Boutique en ligne d'une entreprise, ouverte a tous sans compte : vitrine,
 * panier, commande (retrait ou livraison, paiement a la remise ou mobile
 * money) et demande de location d'un vehicule.
 */
@Component({
  selector: 'app-boutique-publique',
  imports: [FormsModule, DecimalPipe, RouterLink, NgTemplateOutlet, IconeVitrine],
  templateUrl: './boutique-publique.html',
  styleUrl: './boutique-publique.scss',
  host: { '(document:keydown)': 'toucheGalerie($event)' }
})
export class BoutiquePublique implements OnInit {
  private readonly service = inject(BoutiquePubliqueService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly titre = inject(Title);

  readonly slug = this.route.snapshot.paramMap.get('slug') ?? '';
  readonly vitrine = signal<Vitrine | null>(null);
  readonly chargement = signal(true);
  readonly introuvable = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);

  readonly categorie = signal<string>('');
  readonly recherche = signal('');
  readonly categories = computed(() =>
    [...new Set((this.vitrine()?.offres ?? []).map((o) => o.categorie).filter((c): c is string => !!c))].sort());
  readonly offresAffichees = computed(() => {
    const cat = this.categorie();
    const texte = this.recherche().trim().toLowerCase();
    return (this.vitrine()?.offres ?? []).filter((o) => (!cat || o.categorie === cat)
      && (!texte || o.titre.toLowerCase().includes(texte) || (o.description ?? '').toLowerCase().includes(texte)));
  });
  readonly offreEnAvant = signal<number | null>(null);
  readonly annee = new Date().getFullYear();
  /** Produits mis en avant par la boutique (section « A la une »). */
  readonly enAvant = computed(() => (this.vitrine()?.offres ?? []).filter((o) => o.miseEnAvant));
  /** Produits illustres montres dans le heros quand la boutique n'a pas d'image de couverture. */
  readonly apercus = computed(() => (this.vitrine()?.offres ?? []).filter((o) => !!o.image).slice(0, 4));
  /** Rayons illustres par la photo de leur premier produit. */
  readonly rayons = computed(() => this.categories().map((nom) => {
    const offres = (this.vitrine()?.offres ?? []).filter((o) => o.categorie === nom);
    return { nom, nombre: offres.length, image: offres.find((o) => !!o.image)?.image };
  }));

  choisirRayon(rayon: string): void {
    this.categorie.set(rayon);
    this.defilerVers('produits');
  }

  defilerVers(id: string): void {
    setTimeout(() => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
  }

  // ----------------------------------------------------------------- Panier
  readonly panier = signal<Map<number, number>>(new Map());
  readonly panierOuvert = signal(false);
  readonly lignesPanier = computed(() => {
    const offres = new Map((this.vitrine()?.offres ?? []).map((o) => [o.id, o]));
    return [...this.panier().entries()]
      .map(([id, quantite]) => ({ offre: offres.get(id), quantite }))
      .filter((l): l is { offre: OffrePublique; quantite: number } => !!l.offre && l.offre.disponible);
  });
  readonly nombreArticles = computed(() => this.lignesPanier().reduce((n, l) => n + l.quantite, 0));
  readonly sousTotal = computed(() => this.lignesPanier().reduce((t, l) => t + l.offre.prix * l.quantite, 0));
  readonly frais = computed(() =>
    this.modeRemise() === 'LIVRAISON' ? (this.vitrine()?.fraisLivraison ?? 0) : 0);

  // --------------------------------------------------------------- Commande
  readonly commande = signal(false);
  readonly modeRemise = signal<ModeRemise>('RETRAIT');
  readonly modePaiement = signal<ModePaiementEnLigne>('A_LA_REMISE');
  coordonnees: Coordonnees = { nom: '', prenoms: '', telephone: '', email: '', adresse: '' };
  note = '';
  readonly envoi = signal(false);

  // ---------------------------------------------------------------- Location
  readonly location = signal<OffrePublique | null>(null);
  dateDebut = '';
  dateFin = '';
  lieuDepart = '';
  destination = '';
  message = '';
  readonly aujourdhui = new Date().toISOString().slice(0, 10);

  // ---------------------------------------------------------------- Galerie
  /** Photos de l'offre ouverte dans la visionneuse (photo principale d'abord). */
  readonly galerie = signal<{ titre: string; photos: string[] } | null>(null);
  readonly photoGalerie = signal(0);

  /** Clic sur la photo principale : ouvre la visionneuse avec toutes les photos du produit. */
  ouvrirGalerie(offre: OffrePublique): void {
    if (!offre.image && !offre.photos) {
      return;
    }
    this.photoGalerie.set(0);
    this.galerie.set({ titre: offre.titre, photos: offre.image ? [offre.image] : [] });
    if (offre.photos) {
      this.service.galerie(this.slug, offre.id).subscribe({
        next: (r) => {
          if (this.galerie()?.titre === offre.titre && r.photos.length) {
            this.galerie.set({ titre: offre.titre, photos: r.photos });
          }
        }
      });
    }
  }

  defilerGalerie(sens: number): void {
    const n = this.galerie()?.photos.length ?? 0;
    if (n > 1) {
      this.photoGalerie.update((i) => (i + sens + n) % n);
    }
  }

  toucheGalerie(evenement: KeyboardEvent): void {
    if (!this.galerie()) {
      return;
    }
    if (evenement.key === 'Escape') {
      this.galerie.set(null);
    } else if (evenement.key === 'ArrowRight') {
      this.defilerGalerie(1);
    } else if (evenement.key === 'ArrowLeft') {
      this.defilerGalerie(-1);
    }
  }

  ngOnInit(): void {
    this.restaurer();
    const offre = Number(this.route.snapshot.queryParamMap.get('offre'));
    this.service.vitrine(this.slug).subscribe({
      next: (vitrine) => {
        this.vitrine.set(vitrine);
        this.chargement.set(false);
        this.titre.setTitle(`${vitrine.nom} — boutique en ligne`);
        this.modeRemise.set(vitrine.retraitActif ? 'RETRAIT' : 'LIVRAISON');
        this.modePaiement.set(vitrine.paiementRemise ? 'A_LA_REMISE' : 'MOBILE_MONEY');
        if (offre) {
          this.offreEnAvant.set(offre);
          setTimeout(() => document.getElementById(`offre-${offre}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 200);
        }
      },
      error: (reponse) => {
        this.chargement.set(false);
        this.introuvable.set(reponse.error?.message ?? 'Cette boutique est introuvable.');
      }
    });
  }

  quantite(offre: OffrePublique): number {
    return this.panier().get(offre.id) ?? 0;
  }

  ajouter(offre: OffrePublique, delta = 1): void {
    this.panier.update((panier) => {
      const suivant = new Map(panier);
      const max = offre.quantiteMax ?? 999;
      const quantite = Math.min(max, Math.max(0, (suivant.get(offre.id) ?? 0) + delta));
      if (quantite === 0) {
        suivant.delete(offre.id);
      } else {
        suivant.set(offre.id, quantite);
      }
      return suivant;
    });
    this.memoriser();
  }

  passerCommande(): void {
    this.erreur.set(null);
    this.panierOuvert.set(false);
    this.commande.set(true);
  }

  envoyerCommande(): void {
    const v = this.vitrine();
    if (!v || this.envoi()) {
      return;
    }
    if (!this.coordonnees.nom.trim() || !this.coordonnees.prenoms.trim() || !this.coordonnees.telephone.trim()) {
      this.erreur.set('Indiquez votre nom, vos prenoms et votre telephone.');
      return;
    }
    if (this.modeRemise() === 'LIVRAISON' && !this.coordonnees.adresse.trim()) {
      this.erreur.set('Indiquez l\'adresse de livraison.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.commander(this.slug, {
      nom: this.coordonnees.nom.trim(),
      prenoms: this.coordonnees.prenoms.trim(),
      telephone: this.coordonnees.telephone.trim(),
      email: this.coordonnees.email.trim() || undefined,
      modeRemise: this.modeRemise(),
      adresseLivraison: this.modeRemise() === 'LIVRAISON' ? this.coordonnees.adresse.trim() : undefined,
      modePaiement: this.modePaiement(),
      note: this.note.trim() || undefined,
      lignes: this.lignesPanier().map((l) => ({ offreId: l.offre.id, quantite: l.quantite }))
    }).subscribe({
      next: (suivi) => {
        this.envoi.set(false);
        this.panier.set(new Map());
        this.memoriser();
        this.allerAuSuivi(suivi);
      },
      error: (reponse) => {
        this.envoi.set(false);
        this.erreur.set(reponse.error?.message ?? 'La commande n\'a pas pu etre envoyee.');
      }
    });
  }

  reserver(offre: OffrePublique): void {
    this.erreur.set(null);
    this.dateDebut = this.aujourdhui;
    this.dateFin = this.aujourdhui;
    this.location.set(offre);
  }

  /** Nombre de jours factures, depart et retour compris. */
  joursLocation(): number {
    if (!this.dateDebut || !this.dateFin || this.dateFin < this.dateDebut) {
      return 0;
    }
    return Math.round((Date.parse(this.dateFin) - Date.parse(this.dateDebut)) / 86_400_000) + 1;
  }

  envoyerLocation(): void {
    const offre = this.location();
    if (!offre || this.envoi()) {
      return;
    }
    if (!this.coordonnees.nom.trim() || !this.coordonnees.prenoms.trim() || !this.coordonnees.telephone.trim()) {
      this.erreur.set('Indiquez votre nom, vos prenoms et votre telephone.');
      return;
    }
    if (this.joursLocation() < 1) {
      this.erreur.set('Choisissez des dates valides.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    this.service.demanderLocation(this.slug, {
      offreId: offre.id,
      nom: this.coordonnees.nom.trim(),
      prenoms: this.coordonnees.prenoms.trim(),
      telephone: this.coordonnees.telephone.trim(),
      email: this.coordonnees.email.trim() || undefined,
      dateDebut: this.dateDebut,
      dateFin: this.dateFin,
      lieuDepart: this.lieuDepart.trim() || undefined,
      destination: this.destination.trim() || undefined,
      message: this.message.trim() || undefined
    }).subscribe({
      next: (suivi) => {
        this.envoi.set(false);
        this.memoriser();
        this.allerAuSuivi(suivi);
      },
      error: (reponse) => {
        this.envoi.set(false);
        this.erreur.set(reponse.error?.message ?? 'La demande n\'a pas pu etre envoyee.');
      }
    });
  }

  whatsapp(numero: string): string {
    return `https://wa.me/${numero.replace(/[^0-9]/g, '')}`;
  }

  /** Paiement en ligne a poursuivre : page du fournisseur, ou page de suivi. */
  private allerAuSuivi(suivi: SuiviEnLigne): void {
    const url = suivi.urlPaiement;
    if (url && /^https?:\/\//.test(url)) {
      window.location.href = url;
      return;
    }
    const jeton = suivi.commande?.jeton ?? suivi.location?.jeton;
    void this.router.navigateByUrl(url ?? `/suivi/${jeton}`);
  }

  /** Panier et coordonnees survivent a un rechargement de la page, sur cet appareil seulement. */
  private memoriser(): void {
    try {
      localStorage.setItem(`panier-${this.slug}`, JSON.stringify([...this.panier().entries()]));
      localStorage.setItem('coordonnees-acheteur', JSON.stringify(this.coordonnees));
    } catch {
      // Stockage indisponible (navigation privee) : sans consequence.
    }
  }

  private restaurer(): void {
    try {
      const panier = localStorage.getItem(`panier-${this.slug}`);
      if (panier) {
        this.panier.set(new Map(JSON.parse(panier) as [number, number][]));
      }
      const coordonnees = localStorage.getItem('coordonnees-acheteur');
      if (coordonnees) {
        this.coordonnees = { ...this.coordonnees, ...(JSON.parse(coordonnees) as Partial<Coordonnees>) };
      }
    } catch {
      // Donnees illisibles : on repart d'un panier vide.
    }
  }
}
