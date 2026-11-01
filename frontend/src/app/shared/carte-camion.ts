import { DatePipe, DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  computed,
  inject,
  input,
  signal,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import * as L from 'leaflet';
import { MarqueurCarte, Trajet } from '../core/models/gps';
import { COULEURS_STATUT_CAMION } from '../core/models/models';
import { GpsService } from '../core/services/gps.service';
import { pictoVehicule } from './picto-vehicule';

/** Lome : centre de la carte tant qu'aucune position n'est connue. */
const CENTRE_PAR_DEFAUT: L.LatLngTuple = [6.1319, 1.2228];
const ORANGE = '#f0a202';
/** Au-dela, la position est consideree comme perdue (meme seuil que l'API). */
const MINUTES_HORS_CONNEXION = 120;

/**
 * Carte d'un seul camion, pour la fiche camion : sa derniere position connue
 * et son trajet sur la journee choisie. La carte du parc reste l'ecran de
 * suivi de l'ensemble des vehicules.
 */
@Component({
  selector: 'app-carte-camion',
  imports: [FormsModule, RouterLink, DecimalPipe, DatePipe],
  templateUrl: './carte-camion.html',
  styleUrl: './carte-camion.scss'
})
export class CarteCamion implements AfterViewInit, OnDestroy {
  private readonly service = inject(GpsService);

  readonly camionId = input.required<number>();

  private readonly conteneur = viewChild.required<ElementRef<HTMLElement>>('conteneur');
  private carte?: L.Map;
  private couchePosition?: L.LayerGroup;
  private coucheTrajet?: L.LayerGroup;
  private observateurTaille?: ResizeObserver;

  readonly position = signal<MarqueurCarte | null>(null);
  readonly trajet = signal<Trajet | null>(null);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  /** Journee du trajet, au format attendu par le champ date. */
  dateTrajet = new Date().toISOString().slice(0, 10);

  readonly couleurStatut = computed(() => {
    const position = this.position();
    if (!position) {
      return '#8a94a0';
    }
    return position.horsConnexion ? '#8a94a0' : COULEURS_STATUT_CAMION[position.statut];
  });

  ngAfterViewInit(): void {
    this.carte = L.map(this.conteneur().nativeElement, {
      center: CENTRE_PAR_DEFAUT,
      zoom: 12,
      zoomControl: false
    });
    L.control.zoom({ position: 'topright' }).addTo(this.carte);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      className: 'fond-clair',
      attribution: '© OpenStreetMap'
    }).addTo(this.carte);
    this.coucheTrajet = L.layerGroup().addTo(this.carte);
    this.couchePosition = L.layerGroup().addTo(this.carte);

    // L'onglet s'ouvre apres le calcul de la mise en page : Leaflet doit
    // reprendre ses dimensions, sans quoi des tuiles manquent.
    this.observateurTaille = new ResizeObserver(() => this.carte?.invalidateSize());
    this.observateurTaille.observe(this.conteneur().nativeElement);

    this.charger();
  }

  ngOnDestroy(): void {
    this.observateurTaille?.disconnect();
    this.carte?.remove();
  }

  charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.service.carte().subscribe({
      next: (marqueurs) => {
        const position = marqueurs.find((m) => m.camionId === this.camionId()) ?? null;
        this.position.set(position);
        // Premier affichage : la journee du dernier signal, pas forcement aujourd'hui.
        if (position && !this.journeeChoisie) {
          this.dateTrajet = position.derniereCommunication.slice(0, 10);
        }
        this.chargement.set(false);
        this.dessinerPosition();
        this.chargerTrajet();
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement de la position impossible.');
      }
    });
  }

  /** Vrai des que l'utilisateur a choisi lui-meme la journee du trajet. */
  private journeeChoisie = false;

  changerJournee(): void {
    this.journeeChoisie = true;
    this.chargerTrajet();
  }

  chargerTrajet(): void {
    const debut = `${this.dateTrajet}T00:00:00Z`;
    const fin = `${this.dateTrajet}T23:59:59Z`;
    this.service.trajet(this.camionId(), debut, fin).subscribe({
      next: (trajet) => {
        this.trajet.set(trajet);
        this.dessinerTrajet(trajet);
      },
      error: () => this.trajet.set(null)
    });
  }

  /** Recentre la carte sur le camion, dans la partie visible a cote de la fiche. */
  centrer(): void {
    const position = this.position();
    if (position) {
      const point: L.LatLngTuple = [position.latitude, position.longitude];
      this.carte?.fitBounds(L.latLngBounds([point, point]), { ...this.marges(), maxZoom: 15 });
    }
  }

  /**
   * Marges de cadrage : la fiche de position couvre la gauche de la carte et
   * la barre d'outils le haut. Sur petit ecran, la fiche passe sous la carte.
   */
  private marges(): L.FitBoundsOptions {
    const large = window.matchMedia('(min-width: 761px)').matches;
    return {
      paddingTopLeft: large ? [330, 80] : [30, 70],
      paddingBottomRight: [40, 40]
    };
  }

  /** Cadre la carte sur le trajet entier, position comprise. */
  cadrerTrajet(): void {
    const points = (this.trajet()?.points ?? []).map((p) => [p.latitude, p.longitude] as L.LatLngTuple);
    const position = this.position();
    if (position) {
      points.push([position.latitude, position.longitude]);
    }
    if (points.length > 1) {
      this.carte?.fitBounds(L.latLngBounds(points), this.marges());
    } else {
      this.centrer();
    }
  }

  /** Minutes ecoulees depuis la derniere trame du traceur. */
  minutesDepuis(horodatage: string): number {
    return Math.max(0, Math.round((Date.now() - new Date(horodatage).getTime()) / 60000));
  }

  /** Anciennete lisible du dernier signal : minutes, heures ou jours. */
  depuis(horodatage: string): string {
    const minutes = this.minutesDepuis(horodatage);
    if (minutes < 60) {
      return `${minutes} min`;
    }
    if (minutes < 48 * 60) {
      return `${Math.round(minutes / 60)} h`;
    }
    return `${Math.round(minutes / 1440)} j`;
  }

  /** Remplissage de la jauge de fraicheur du signal, entre 0 et 1. */
  fraicheur(horodatage: string): number {
    return Math.max(0, 1 - this.minutesDepuis(horodatage) / MINUTES_HORS_CONNEXION);
  }

  private dessinerPosition(): void {
    this.couchePosition?.clearLayers();
    const position = this.position();
    if (!position || !this.carte) {
      return;
    }
    const enMouvement = !position.horsConnexion && (position.vitesseKmh ?? 0) > 3;
    const classes = ['pion-camion', 'choisi'];
    if (enMouvement) {
      classes.push('en-mouvement');
    }
    if (position.horsConnexion) {
      classes.push('hors-connexion');
    }
    const icone = L.divIcon({
      className: 'marqueur-camion',
      html: `<span class="halo" style="--statut:${this.couleurStatut()}"></span>
             <span class="${classes.join(' ')}" style="--statut:${this.couleurStatut()}">${pictoVehicule(position.typeDessin)}</span>`,
      iconSize: [40, 40],
      iconAnchor: [20, 20]
    });
    L.marker([position.latitude, position.longitude], { icon: icone, zIndexOffset: 1000 })
      .addTo(this.couchePosition!);
    this.centrer();
  }

  private dessinerTrajet(trajet: Trajet): void {
    this.coucheTrajet?.clearLayers();
    if (trajet.points.length === 0) {
      return;
    }
    const points = trajet.points.map((p) => [p.latitude, p.longitude] as L.LatLngTuple);
    L.polyline(points, { color: ORANGE, weight: 4, opacity: 0.9, lineJoin: 'round' }).addTo(this.coucheTrajet!);
    L.circleMarker(points[0], { radius: 6, color: '#fff', weight: 2, fillColor: '#1d2b26', fillOpacity: 1 })
      .bindTooltip('Depart')
      .addTo(this.coucheTrajet!);
    this.cadrerTrajet();
  }
}
