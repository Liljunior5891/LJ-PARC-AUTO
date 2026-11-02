import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { Boutique, CATEGORIES_BOUTIQUE } from '../../core/models/marketplace';
import { MarketplaceService } from '../../core/services/marketplace.service';

/** Largeur maximale du bandeau : net sur grand ecran, leger a charger. */
const LARGEUR_BANDEAU = 1400;

/**
 * Reglages de la boutique en ligne : adresse publique, vitrine, contacts,
 * retrait ou livraison, modes de paiement, et publication.
 */
@Component({
  selector: 'app-ma-boutique',
  imports: [FormsModule],
  templateUrl: './ma-boutique.html',
  styleUrl: './marketplace.scss'
})
export class MaBoutique implements OnInit, OnDestroy {
  private readonly service = inject(MarketplaceService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly categories = CATEGORIES_BOUTIQUE;
  readonly chargement = signal(true);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly codeQr = signal<SafeUrl | null>(null);
  private urlQr: string | null = null;
  readonly copie = signal(false);

  boutique: Boutique | null = null;
  /** Adresse enregistree : le lien et le QR ne changent qu'apres enregistrement. */
  readonly slugEnregistre = signal<string | null>(null);
  readonly lienPublic = computed(() =>
    this.slugEnregistre() ? `${window.location.origin}/boutique/${this.slugEnregistre()}` : null);

  ngOnInit(): void {
    this.service.boutique().subscribe({
      next: (boutique) => {
        this.boutique = boutique;
        this.chargement.set(false);
        if (boutique.id != null) {
          this.slugEnregistre.set(boutique.slug);
          this.chargerQr();
        }
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement de la boutique impossible.');
      }
    });
  }

  ngOnDestroy(): void {
    if (this.urlQr) {
      URL.revokeObjectURL(this.urlQr);
    }
  }

  /** L'adresse ne garde que minuscules, chiffres et tirets, au fil de la saisie. */
  nettoyerSlug(valeur: string): void {
    if (!this.boutique) {
      return;
    }
    this.boutique.slug = valeur.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9-]+/g, '-').replace(/-{2,}/g, '-').replace(/^-/, '').slice(0, 60);
  }

  async choisirCouverture(evenement: Event): Promise<void> {
    const entree = evenement.target as HTMLInputElement;
    const fichier = entree.files?.[0];
    entree.value = '';
    if (!fichier || !this.boutique) {
      return;
    }
    try {
      this.boutique.couverture = await reduireBandeau(fichier);
    } catch (e) {
      this.erreur.set((e as Error).message);
    }
  }

  retirerCouverture(): void {
    if (this.boutique) {
      this.boutique.couverture = undefined;
    }
  }

  enregistrer(): void {
    const b = this.boutique;
    if (!b || this.enregistrement()) {
      return;
    }
    this.erreur.set(null);
    this.succes.set(null);
    this.enregistrement.set(true);
    const { id: _id, logo: _logo, mobileMoneyDisponible: _mm, ...requete } = b;
    this.service.enregistrerBoutique({ ...requete, slug: requete.slug.replace(/-$/, '') }).subscribe({
      next: (enregistree) => {
        this.boutique = enregistree;
        this.enregistrement.set(false);
        const adresseChangee = this.slugEnregistre() !== enregistree.slug;
        this.slugEnregistre.set(enregistree.slug);
        if (adresseChangee) {
          this.chargerQr();
        }
        this.succes.set(enregistree.publiee
          ? 'Boutique enregistree : elle est en ligne.'
          : 'Boutique enregistree. Elle restera invisible tant qu\'elle n\'est pas publiee.');
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(reponse.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  copierLien(): void {
    const lien = this.lienPublic();
    if (!lien) {
      return;
    }
    void navigator.clipboard?.writeText(lien).then(() => {
      this.copie.set(true);
      setTimeout(() => this.copie.set(false), 2000);
    });
  }

  telechargerQr(): void {
    if (!this.urlQr) {
      return;
    }
    const lien = document.createElement('a');
    lien.href = this.urlQr;
    lien.download = `boutique-${this.slugEnregistre()}.svg`;
    lien.click();
  }

  private chargerQr(): void {
    this.service.codeQr().subscribe({
      next: (svg) => {
        if (this.urlQr) {
          URL.revokeObjectURL(this.urlQr);
        }
        this.urlQr = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }));
        this.codeQr.set(this.sanitizer.bypassSecurityTrustUrl(this.urlQr));
      }
    });
  }
}

/** Reduit l'image de bandeau en gardant ses proportions, et la renvoie en data URL JPEG. */
function reduireBandeau(fichier: File): Promise<string> {
  return new Promise((resoudre, rejeter) => {
    if (!fichier.type.startsWith('image/')) {
      rejeter(new Error('Choisissez une image (JPEG, PNG ou WebP).'));
      return;
    }
    const url = URL.createObjectURL(fichier);
    const image = new Image();
    image.onload = () => {
      const echelle = Math.min(1, LARGEUR_BANDEAU / image.naturalWidth);
      const toile = document.createElement('canvas');
      toile.width = Math.round(image.naturalWidth * echelle);
      toile.height = Math.round(image.naturalHeight * echelle);
      const contexte = toile.getContext('2d');
      URL.revokeObjectURL(url);
      if (!contexte) {
        rejeter(new Error('Navigateur incompatible.'));
        return;
      }
      contexte.drawImage(image, 0, 0, toile.width, toile.height);
      resoudre(toile.toDataURL('image/jpeg', 0.8));
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      rejeter(new Error('Image illisible.'));
    };
    image.src = url;
  });
}
