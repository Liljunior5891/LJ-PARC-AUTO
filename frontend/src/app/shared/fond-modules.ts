import { Component, OnDestroy, OnInit, computed, signal } from '@angular/core';
import { SceneModule } from './scene-module';

/** Une diapositive du fond : la scene animee du module sur son degrade, ou une photo. */
interface Diapositive {
  code: string;
  nom: string;
  accroche: string;
  points: string[];
  /** Photo plein cadre (jpg, webp) facultative : remplace la scene animee et le degrade. */
  photo?: string;
  degrade: string;
  accent: string;
}

/**
 * Chaque module est presente par une scene animee (app-scene-module) qui montre ce
 * qu'il fait. Une photo renseignee dans « photo » la remplace ; le formulaire occupe
 * la gauche de l'ecran, un sujet place a droite de la photo reste visible.
 */
const DIAPOSITIVES: Diapositive[] = [
  {
    code: 'GESTION_PARC',
    nom: 'Gestion de parc',
    accroche: 'Vehicules, missions, maintenance et GPS au meme endroit.',
    points: ['Rentabilite par vehicule', 'Entretiens et documents a echeance', 'Carte GPS en direct'],
    degrade: 'linear-gradient(135deg, #06182a 0%, #0b3a63 100%)',
    accent: '#7dd3fc'
  },
  {
    code: 'POINT_DE_VENTE',
    nom: 'Point de vente',
    accroche: 'Une caisse rapide pour la boutique, le restaurant ou la quincaillerie.',
    points: ['Caisse, stock et codes-barres', 'Commandes par table et cuisine', 'Credit client et devis'],
    degrade: 'linear-gradient(135deg, #022c22 0%, #065f46 55%, #0f766e 100%)',
    accent: '#6ee7b7'
  },
  {
    code: 'MARKETPLACE',
    nom: 'Marketplace',
    accroche: 'Votre boutique en ligne, ouverte a tous, en quelques minutes.',
    points: ['Vitrine et lien a partager', 'Paiement au retrait ou mobile money', 'Location de vehicules en ligne'],
    degrade: 'linear-gradient(135deg, #431407 0%, #9a3412 55%, #c2410c 100%)',
    accent: '#fdba74'
  },
  {
    code: 'AGENCES',
    nom: 'Agences multiservices',
    accroche: 'Mobile money, transferts et cartes, agence par agence.',
    points: ['Cash et virtuel toujours justes', 'Commissions et baremes', 'Journal que personne ne modifie'],
    degrade: 'linear-gradient(135deg, #0f0c2e 0%, #312e81 55%, #4338ca 100%)',
    accent: '#a5b4fc'
  },
  {
    code: 'PRESENCE',
    nom: 'Gestion de presence',
    accroche: 'Le pointage des equipes par code QR, sans badge ni appareil a acheter.',
    points: ['Code QR qui change toutes les 30 s', 'Position GPS controlee', 'Retards et absents en direct'],
    degrade: 'linear-gradient(135deg, #2e0854 0%, #581c87 55%, #7e22ce 100%)',
    accent: '#d8b4fe'
  }
];

/** Duree d'affichage de chaque module. */
const DUREE_MS = 7000;

/**
 * Fond des pages de connexion et d'inscription : les modules de la plateforme
 * defilent en fondu, avec leur presentation a droite. Decoratif pour les lecteurs
 * d'ecran ; arret du defilement au survol, au clavier et par le bouton pause, et
 * sans zoom ni defilement automatique si l'utilisateur limite les animations.
 */
@Component({
  selector: 'app-fond-modules',
  imports: [SceneModule],
  template: `
    <div class="diapositives" aria-hidden="true">
      @for (d of diapositives; track d.code; let i = $index) {
        <div class="diapositive" [class.active]="i === index()" [style.background]="d.degrade">
          @if (d.photo) {
            <div class="photo" [style.background-image]="'url(' + d.photo + ')'"></div>
          } @else if (i === index()) {
            <!-- Seule la scene affichee est animee ; elle repart du debut a chaque passage. -->
            <div class="scene"><app-scene-module [code]="d.code" /></div>
          }
        </div>
      }
    </div>

    <section class="legende" aria-label="Modules de la plateforme"
             (mouseenter)="survol.set(true)" (mouseleave)="survol.set(false)"
             (focusin)="survol.set(true)" (focusout)="survol.set(false)">
      @if (courante(); as d) {
        <div class="texte" [style.--accent]="d.accent">
          <span class="etiquette">Module {{ index() + 1 }} / {{ diapositives.length }}</span>
          <h2>{{ d.nom }}</h2>
          <p>{{ d.accroche }}</p>
          <ul>
            @for (p of d.points; track $index) { <li>{{ p }}</li> }
          </ul>
        </div>
      }
      <div class="commandes">
        @for (d of diapositives; track d.code; let i = $index) {
          <button type="button" class="point" [class.actif]="i === index()" [attr.aria-label]="'Voir le module ' + d.nom"
                  [attr.aria-current]="i === index() ? 'true' : null" (click)="aller(i)"></button>
        }
        <button type="button" class="pause" (click)="enPause.set(!enPause())"
                [attr.aria-label]="enPause() ? 'Reprendre le defilement' : 'Mettre le defilement en pause'">
          {{ enPause() ? '▶' : '❚❚' }}
        </button>
      </div>
    </section>
  `,
  styles: `
    :host { position: fixed; inset: 0; z-index: 0; display: block; overflow: hidden; }
    .diapositives, .diapositive, .photo, .scene { position: absolute; inset: 0; }
    .diapositive::after { content: ''; position: absolute; inset: 0; pointer-events: none;
      background: radial-gradient(circle at 75% 35%, rgba(255, 255, 255, .12), transparent 55%); }
    .diapositive { opacity: 0; transition: opacity 1.2s ease; }
    .diapositive.active { opacity: 1; }
    .photo { background-size: cover; background-position: center; }
    .diapositive:first-child .photo { background-position: center bottom; }
    .scene { left: 42%; right: 1%; top: 3%; bottom: 29%; z-index: 1; }
    .diapositive.active .photo { animation: zoom 8200ms ease-out both; }
    @keyframes zoom { from { transform: scale(1); } to { transform: scale(1.06); } }

    .legende {
      position: absolute; right: 4vw; bottom: 6vh; width: min(420px, 34vw); z-index: 2;
      padding: 1.3rem 1.4rem 1.1rem; border-radius: 18px; color: #fff;
      border: 1px solid rgba(255, 255, 255, .18); background: rgba(4, 14, 26, .55);
      backdrop-filter: blur(16px) saturate(140%); -webkit-backdrop-filter: blur(16px) saturate(140%);
      box-shadow: 0 20px 50px rgba(2, 12, 24, .45);
    }
    .texte { animation: entree .6s ease both; }
    @keyframes entree { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
    .etiquette { font-size: .72rem; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: var(--accent); }
    h2 { margin: .3rem 0 .4rem; font-size: 1.7rem; line-height: 1.15; }
    p { margin: 0 0 .7rem; font-size: 1rem; opacity: .92; }
    ul { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: .3rem; }
    li { display: flex; gap: .5rem; align-items: baseline; font-size: .92rem; }
    li::before { content: ''; flex: none; width: .45rem; height: .45rem; border-radius: 50%; background: var(--accent);
      transform: translateY(-.1rem); }
    .commandes { display: flex; align-items: center; gap: .45rem; margin-top: 1rem; }
    .point { width: 10px; height: 10px; padding: 0; border-radius: 999px; border: 0; cursor: pointer;
      background: rgba(255, 255, 255, .4); transition: width .3s ease, background .3s ease; }
    .point.actif { width: 28px; background: #fff; }
    .point:focus-visible, .pause:focus-visible { outline: 2px solid #fff; outline-offset: 3px; }
    .pause { margin-left: .4rem; width: 28px; height: 28px; border-radius: 50%; cursor: pointer; font-size: .7rem;
      border: 1px solid rgba(255, 255, 255, .5); background: rgba(255, 255, 255, .12); color: #fff; }

    @media (max-width: 900px) {
      .legende { display: none; }
      .scene { left: 8%; right: 8%; top: auto; bottom: 3%; height: 42%; opacity: .45; }
    }
    @media (prefers-reduced-motion: reduce) {
      .diapositive { transition: none; }
      .diapositive.active .photo, .texte { animation: none; }
    }
  `
})
export class FondModules implements OnInit, OnDestroy {
  readonly diapositives = DIAPOSITIVES;
  readonly index = signal(0);
  readonly survol = signal(false);
  readonly enPause = signal(false);
  readonly courante = computed(() => this.diapositives[this.index()]);
  private minuteur?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    // Images chargees a l'avance : le fondu ne montre pas d'ecran vide.
    for (const d of this.diapositives) {
      const image = new Image();
      if (d.photo) {
        image.src = d.photo;
      }
    }
    const animationsReduites = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (animationsReduites) {
      this.enPause.set(true);
    }
    this.minuteur = setInterval(() => {
      if (!this.enPause() && !this.survol() && !document.hidden) {
        this.index.update((i) => (i + 1) % this.diapositives.length);
      }
    }, DUREE_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.minuteur);
  }

  aller(i: number): void {
    this.index.set(i);
  }
}
