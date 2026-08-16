import { DatePipe, DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  OnDestroy,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';
import { AlerteGps, COULEURS_STATUT, MarqueurCarte, Trajet, ZoneGeofence } from '../../core/models/gps';
import { GpsService } from '../../core/services/gps.service';

/** Vue par defaut : Lome, siege de l'exploitation. */
const CENTRE_PAR_DEFAUT: L.LatLngExpression = [6.1319, 1.2228];
const ZOOM_PAR_DEFAUT = 8;
/** Cadence de rafraichissement des positions. */
const PERIODE_RAFRAICHISSEMENT_MS = 20_000;

@Component({
  selector: 'app-carte-parc',
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './carte-parc.html',
  styleUrl: './carte-parc.scss'
})
export class CarteParc implements AfterViewInit, OnDestroy {
  private readonly service = inject(GpsService);

  readonly marqueurs = signal<MarqueurCarte[]>([]);
  readonly zones = signal<ZoneGeofence[]>([]);
  readonly alertes = signal<AlerteGps[]>([]);
  readonly trajet = signal<Trajet | null>(null);
  readonly camionSuivi = signal<MarqueurCarte | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly derniereMaj = signal<Date | null>(null);

  afficherZones = true;
  dateTrajet = new Date().toISOString().slice(0, 10);

  private carte?: L.Map;
  private coucheMarqueurs?: L.LayerGroup;
  private coucheZones?: L.LayerGroup;
  private coucheTrajet?: L.LayerGroup;
  private minuterie?: ReturnType<typeof setInterval>;
  private observateurTaille?: ResizeObserver;

  ngAfterViewInit(): void {
    this.initialiserCarte();
    this.charger();
    this.minuterie = setInterval(() => this.charger(), PERIODE_RAFRAICHISSEMENT_MS);
  }

  ngOnDestroy(): void {
    if (this.minuterie) {
      clearInterval(this.minuterie);
    }
    this.observateurTaille?.disconnect();
    this.carte?.remove();
  }

  private initialiserCarte(): void {
    this.carte = L.map('carte-parc', {
      center: CENTRE_PAR_DEFAUT,
      zoom: ZOOM_PAR_DEFAUT,
      zoomControl: true
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap'
    }).addTo(this.carte);

    this.coucheZones = L.layerGroup().addTo(this.carte);
    this.coucheTrajet = L.layerGroup().addTo(this.carte);
    this.coucheMarqueurs = L.layerGroup().addTo(this.carte);

    // Leaflet fige ses dimensions a l'initialisation : si le conteneur n'a pas
    // encore sa taille finale (navigation interne, onglet restaure, panneau
    // replie), la carte reste tronquee tant qu'on ne la recalcule pas.
    const conteneur = document.getElementById('carte-parc');
    if (conteneur) {
      this.observateurTaille = new ResizeObserver(() => this.carte?.invalidateSize());
      this.observateurTaille.observe(conteneur);
    }
    setTimeout(() => this.carte?.invalidateSize(), 0);
  }

  charger(): void {
    this.service.carte().subscribe({
      next: (marqueurs) => {
        this.marqueurs.set(marqueurs);
        this.dessinerMarqueurs(marqueurs);
        this.derniereMaj.set(new Date());
        this.erreur.set(null);
      },
      error: () => this.erreur.set('Chargement des positions impossible.')
    });
    this.service.zones().subscribe({
      next: (zones) => {
        this.zones.set(zones);
        this.dessinerZones(zones);
      }
    });
    this.service.alertesRecentes(8).subscribe({
      next: (alertes) => this.alertes.set(alertes)
    });
  }

  private dessinerMarqueurs(marqueurs: MarqueurCarte[]): void {
    this.coucheMarqueurs?.clearLayers();
    marqueurs.forEach((marqueur) => {
      const couleur = marqueur.horsConnexion ? '#333d47' : COULEURS_STATUT[marqueur.statut];
      const pastille = L.divIcon({
        className: 'marqueur-camion',
        html: `<span style="background:${couleur}">${marqueur.numeroInterne}</span>`,
        iconSize: [56, 22],
        iconAnchor: [28, 11]
      });

      L.marker([marqueur.latitude, marqueur.longitude], { icon: pastille })
        .bindPopup(this.contenuPopup(marqueur))
        .on('click', () => this.camionSuivi.set(marqueur))
        .addTo(this.coucheMarqueurs!);
    });

    // Au premier chargement, la carte se cale sur l'etendue du parc.
    if (marqueurs.length > 0 && !this.trajet()) {
      const limites = L.latLngBounds(marqueurs.map((m) => [m.latitude, m.longitude] as L.LatLngTuple));
      this.carte?.fitBounds(limites, { padding: [60, 60], maxZoom: 13 });
    }
  }

  private contenuPopup(marqueur: MarqueurCarte): string {
    const lignes = [
      `<strong>${marqueur.immatriculation}</strong>`,
      `Statut : ${marqueur.statutLibelle}`,
      `Chauffeur : ${marqueur.chauffeurNom ?? '—'}`,
      `Mission : ${marqueur.activite ?? '—'}`,
      `Vitesse : ${marqueur.vitesseKmh ?? 0} km/h`,
      `Derniere position : ${new Date(marqueur.derniereCommunication).toLocaleString('fr-FR')}`
    ];
    if (marqueur.horsConnexion) {
      lignes.push('<em>Traceur hors connexion</em>');
    }
    return lignes.join('<br>');
  }

  private dessinerZones(zones: ZoneGeofence[]): void {
    this.coucheZones?.clearLayers();
    if (!this.afficherZones) {
      return;
    }
    zones.forEach((zone) => {
      L.circle([zone.latitude, zone.longitude], {
        radius: zone.rayonMetres,
        color: zone.couleur,
        fillColor: zone.couleur,
        fillOpacity: 0.12,
        weight: 2
      })
        .bindPopup(`<strong>${zone.nom}</strong><br>${zone.typeLibelle}<br>Rayon : ${zone.rayonMetres} m`)
        .addTo(this.coucheZones!);
    });
  }

  basculerZones(): void {
    this.dessinerZones(this.zones());
  }

  /** Affiche l'itineraire du camion pour la journee choisie (chapitre 24). */
  afficherTrajet(camionId: number): void {
    const debut = `${this.dateTrajet}T00:00:00Z`;
    const fin = `${this.dateTrajet}T23:59:59Z`;
    this.service.trajet(camionId, debut, fin).subscribe({
      next: (trajet) => {
        this.trajet.set(trajet);
        this.dessinerTrajet(trajet);
      },
      error: () => this.erreur.set('Chargement du trajet impossible.')
    });
  }

  private dessinerTrajet(trajet: Trajet): void {
    this.coucheTrajet?.clearLayers();
    if (trajet.points.length === 0) {
      return;
    }
    const points = trajet.points.map((p) => [p.latitude, p.longitude] as L.LatLngTuple);

    L.polyline(points, { color: '#0f4c81', weight: 4, opacity: 0.75 }).addTo(this.coucheTrajet!);
    L.circleMarker(points[0], { radius: 6, color: '#1e874b', fillOpacity: 1 })
      .bindPopup('Depart')
      .addTo(this.coucheTrajet!);
    L.circleMarker(points[points.length - 1], { radius: 6, color: '#c62828', fillOpacity: 1 })
      .bindPopup('Arrivee')
      .addTo(this.coucheTrajet!);

    this.carte?.fitBounds(L.latLngBounds(points), { padding: [60, 60] });
  }

  effacerTrajet(): void {
    this.trajet.set(null);
    this.coucheTrajet?.clearLayers();
  }

  centrerSur(marqueur: MarqueurCarte): void {
    this.camionSuivi.set(marqueur);
    this.carte?.setView([marqueur.latitude, marqueur.longitude], 14);
  }

  traiter(alerte: AlerteGps): void {
    this.service.traiterAlerte(alerte.id).subscribe({
      next: () => this.service.alertesRecentes(8).subscribe({
        next: (alertes) => this.alertes.set(alertes)
      }),
      error: () => this.erreur.set('Traitement de l alerte impossible.')
    });
  }

  couleurStatut(marqueur: MarqueurCarte): string {
    return marqueur.horsConnexion ? '#333d47' : COULEURS_STATUT[marqueur.statut];
  }

  classeGravite(alerte: AlerteGps): string {
    return alerte.gravite === 'CRITIQUE' ? 'etiquette vendu'
      : alerte.gravite === 'AVERTISSEMENT' ? 'etiquette en_location' : 'etiquette';
  }
}
