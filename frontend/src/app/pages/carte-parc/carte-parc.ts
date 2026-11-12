import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { DessinVehicule } from '../../shared/dessin-vehicule';
import { DatePipe, DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  Component,
  OnDestroy,
  computed,
  inject,
  signal
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';
import { AlerteGps, COULEURS_STATUT, MarqueurCarte, Trajet, ZoneGeofence } from '../../core/models/gps';
import { StatutCamion } from '../../core/models/models';
import { GpsService, TraceurGps } from '../../core/services/gps.service';
import { pictoVehicule } from '../../shared/picto-vehicule';

/** Vue par defaut : Lome, siege de l'exploitation. */
const CENTRE_PAR_DEFAUT: L.LatLngExpression = [6.1319, 1.2228];
const ZOOM_PAR_DEFAUT = 8;
/** Cadence de rafraichissement des positions. */
const PERIODE_RAFRAICHISSEMENT_MS = 20_000;
/** Limite de vitesse du parc : la jauge est pleine a cette valeur. */
const VITESSE_LIMITE = 90;
/** Delai sans trame au-dela duquel le serveur declare le traceur hors connexion. */
const SEUIL_DECONNEXION_MIN = 120;
/** Couleur de l'itineraire et des camions en mouvement. */
const ORANGE = '#f0a202';


@Component({
  selector: 'app-carte-parc',
  imports: [FormsModule, DecimalPipe, DatePipe, DessinVehicule],
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

  // ------------------------------------------------ jeton des traceurs (administrateur)
  readonly estAdmin = inject(AuthService).aRole('ADMIN');
  readonly panneauJeton = signal(false);
  readonly etatJeton = signal<{ actif: boolean; creeLe?: string | null; dernierUsage?: string | null } | null>(null);
  readonly nouveauJeton = signal<{ jeton: string; adresse: string } | null>(null);

  readonly traceurs = signal<TraceurGps[]>([]);
  readonly nouveauJetonTraceur = signal<{ immatriculation: string; jeton: string; adresse: string } | null>(null);
  readonly erreurJeton = signal<string | null>(null);

  ouvrirJeton(): void {
    this.nouveauJeton.set(null);
    this.nouveauJetonTraceur.set(null);
    this.erreurJeton.set(null);
    this.panneauJeton.set(true);
    this.service.etatJeton().subscribe({ next: (e) => this.etatJeton.set(e) });
    this.chargerTraceurs();
  }

  private chargerTraceurs(): void {
    this.service.traceurs().subscribe({ next: (t) => this.traceurs.set(t) });
  }

  /** Jeton propre a un traceur : il n'envoie des positions que pour son vehicule. */
  genererJetonTraceur(t: TraceurGps): void {
    this.erreurJeton.set(null);
    this.service.genererJetonTraceur(t.camionId).subscribe({
      next: (j) => {
        this.nouveauJeton.set(null);
        this.nouveauJetonTraceur.set({
          immatriculation: t.immatriculation,
          jeton: j.jeton,
          adresse: environment.apiUrl.replace(/\/api$/, '') + j.adresse
        });
        this.chargerTraceurs();
      },
      error: (e) => this.erreurJeton.set(e?.error?.message ?? 'Jeton impossible a generer')
    });
  }

  revoquerJetonTraceur(t: TraceurGps): void {
    this.erreurJeton.set(null);
    this.service.revoquerJetonTraceur(t.camionId).subscribe({
      next: () => {
        this.nouveauJetonTraceur.set(null);
        this.chargerTraceurs();
      },
      error: (e) => this.erreurJeton.set(e?.error?.message ?? 'Revocation impossible')
    });
  }

  genererJeton(): void {
    this.service.genererJeton().subscribe({
      next: (j) => {
        // Adresse complete de l'API (la ou Traccar doit envoyer), pas celle de l'interface.
        this.nouveauJetonTraceur.set(null);
        this.nouveauJeton.set({ ...j, adresse: environment.apiUrl.replace(/\/api$/, '') + j.adresse });
        this.service.etatJeton().subscribe({ next: (e) => this.etatJeton.set(e) });
      }
    });
  }
  readonly derniereMaj = signal<Date | null>(null);
  readonly alertesOuvertes = signal(false);
  readonly afficherZones = signal(true);

  dateTrajet = new Date().toISOString().slice(0, 10);

  readonly limiteVitesse = VITESSE_LIMITE;

  /** Camion presente dans la fiche du bas : celui suivi, sinon le premier en mouvement. */
  readonly selection = computed(() => {
    const suivi = this.camionSuivi();
    const liste = this.marqueurs();
    return (suivi && liste.find((m) => m.camionId === suivi.camionId))
      ?? liste.find((m) => this.enMouvement(m))
      ?? liste[0]
      ?? null;
  });

  readonly nombreEnMouvement = computed(() => this.marqueurs().filter((m) => this.enMouvement(m)).length);

  /** Vitesse de chaque camion, pour l'histogramme de la tuile « Camions localises ». */
  readonly barresVitesse = computed(() =>
    this.marqueurs().map((m) => ({
      immatriculation: m.immatriculation,
      hauteur: Math.max(8, Math.min(100, ((m.vitesseKmh ?? 0) / VITESSE_LIMITE) * 100)),
      active: this.enMouvement(m)
    }))
  );

  /** Part de chaque etat dans le parc localise, pour la tuile du bas a droite. */
  readonly repartition = computed(() => {
    const liste = this.marqueurs();
    const total = liste.length || 1;
    const compter = (filtre: (m: MarqueurCarte) => boolean) => liste.filter(filtre).length;
    const enLigne = (statuts: StatutCamion[]) => (m: MarqueurCarte) =>
      !m.horsConnexion && statuts.includes(m.statut);
    return [
      { libelle: 'Disponibles', couleur: COULEURS_STATUT.DISPONIBLE, nombre: compter(enLigne(['DISPONIBLE'])) },
      { libelle: 'En mission', couleur: ORANGE, nombre: compter(enLigne(['EN_MISSION', 'EN_LIVRAISON', 'EN_LOCATION'])) },
      { libelle: 'Atelier', couleur: COULEURS_STATUT.EN_MAINTENANCE,
        nombre: compter(enLigne(['EN_MAINTENANCE', 'IMMOBILISE', 'ACCIDENTE', 'HORS_SERVICE', 'VENDU'])) },
      { libelle: 'Hors connexion', couleur: '#8a94a0', nombre: compter((m) => m.horsConnexion) }
    ].map((ligne) => ({ ...ligne, pourcent: Math.round((ligne.nombre / total) * 100) }));
  });

  private carte?: L.Map;
  private coucheMarqueurs?: L.LayerGroup;
  private coucheZones?: L.LayerGroup;
  private coucheTrajet?: L.LayerGroup;
  private minuterie?: ReturnType<typeof setInterval>;
  private observateurTaille?: ResizeObserver;
  /** La carte ne se cale sur le parc qu'au premier chargement, pas a chaque rafraichissement. */
  private dejaCadree = false;

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
      zoomControl: false
    });
    L.control.zoom({ position: 'topright' }).addTo(this.carte);

    // Fond OpenStreetMap, desature par la feuille de style (.fond-clair) :
    // les camions et l'itineraire ressortent sur un plan gris clair.
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      className: 'fond-clair',
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
    const choisi = this.selection();
    marqueurs.forEach((marqueur) => {
      const classes = ['pion-camion'];
      if (this.enMouvement(marqueur)) {
        classes.push('en-mouvement');
      }
      if (marqueur.horsConnexion) {
        classes.push('hors-connexion');
      }
      if (choisi?.camionId === marqueur.camionId) {
        classes.push('choisi');
      }
      const icone = L.divIcon({
        className: 'marqueur-parc',
        html: `<span class="${classes.join(' ')}" style="--statut:${this.couleurStatut(marqueur)}"
                 title="${this.echapper(marqueur.immatriculation)}">${pictoVehicule(marqueur.typeDessin)}</span>`,
        iconSize: [34, 34],
        iconAnchor: [17, 17],
        popupAnchor: [0, -18]
      });

      const pion = L.marker([marqueur.latitude, marqueur.longitude], { icon: icone })
        .bindPopup(this.contenuPopup(marqueur), {
          className: 'popup-parc',
          closeButton: false,
          maxWidth: 320,
          autoPanPaddingTopLeft: this.marges().paddingTopLeft,
          autoPanPaddingBottomRight: this.marges().paddingBottomRight
        })
        .on('click', () => this.suivre(marqueur, false))
        .addTo(this.coucheMarqueurs!);
      if (choisi?.camionId === marqueur.camionId && this.popupOuverte) {
        pion.openPopup();
      }
    });

    // Au premier chargement, la carte se cale sur l'etendue du parc.
    if (marqueurs.length > 0 && !this.dejaCadree) {
      this.dejaCadree = true;
      this.cadrerParc();
    }
  }

  /** Une popup ouverte reste ouverte d'un rafraichissement a l'autre. */
  private popupOuverte = false;

  cadrerParc(): void {
    const liste = this.marqueurs();
    if (liste.length === 0) {
      return;
    }
    const limites = L.latLngBounds(liste.map((m) => [m.latitude, m.longitude] as L.LatLngTuple));
    this.carte?.fitBounds(limites, { ...this.marges(), maxZoom: 13 });
  }

  /**
   * Zone utile de la carte : la barre d'outils couvre le haut et, sur grand
   * ecran, les tuiles couvrent le bas. Sur petit ecran, elles passent dessous.
   */
  private marges(): { paddingTopLeft: L.PointTuple; paddingBottomRight: L.PointTuple } {
    const tuilesSuperposees = !window.matchMedia('(max-width: 1100px)').matches;
    return {
      paddingTopLeft: [40, 90],
      paddingBottomRight: [40, tuilesSuperposees ? 300 : 30]
    };
  }

  /** Fenetre d'information : camion, activite, jauge de vitesse, chauffeur et derniere trame. */
  private contenuPopup(marqueur: MarqueurCarte): string {
    const vitesse = Math.round(marqueur.vitesseKmh ?? 0);
    const part = Math.min(vitesse / VITESSE_LIMITE, 1);
    const circonference = 2 * Math.PI * 15;
    const heure = new Date(marqueur.derniereCommunication)
      .toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return `
      <div class="entete-popup">
        <span class="picto-popup">${pictoVehicule(marqueur.typeDessin)}</span>
        <div>
          <strong>${this.echapper(marqueur.immatriculation)}</strong>
          <small>${this.echapper(marqueur.activite ?? marqueur.statutLibelle)}</small>
        </div>
        <svg class="anneau-popup" viewBox="0 0 36 36">
          <circle cx="18" cy="18" r="15" class="fond" />
          <circle cx="18" cy="18" r="15" class="valeur"
                  stroke-dasharray="${part * circonference} ${circonference}" transform="rotate(-90 18 18)" />
          <text x="18" y="21">${vitesse}</text>
        </svg>
      </div>
      <div class="ligne-popup"><b>Chauffeur</b> · ${this.echapper(marqueur.chauffeurNom ?? 'sans chauffeur')}</div>
      <div class="ligne-popup"><b>Signal</b> · ${marqueur.horsConnexion ? 'hors connexion depuis' : 'derniere trame a'} ${heure}</div>`;
  }

  private dessinerZones(zones: ZoneGeofence[]): void {
    this.coucheZones?.clearLayers();
    if (!this.afficherZones()) {
      return;
    }
    zones.forEach((zone) => {
      L.circle([zone.latitude, zone.longitude], {
        radius: zone.rayonMetres,
        color: zone.couleur,
        fillColor: zone.couleur,
        fillOpacity: 0.12,
        weight: 1.5,
        dashArray: '4 4'
      })
        .bindPopup(`<strong>${this.echapper(zone.nom)}</strong><br>${zone.typeLibelle}<br>Rayon : ${zone.rayonMetres} m`)
        .addTo(this.coucheZones!);
    });
  }

  basculerZones(): void {
    this.afficherZones.update((visible) => !visible);
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

    L.polyline(points, { color: ORANGE, weight: 4, opacity: 0.9, lineJoin: 'round' }).addTo(this.coucheTrajet!);
    L.circleMarker(points[0], { radius: 6, color: '#fff', weight: 2, fillColor: '#1d2b26', fillOpacity: 1 })
      .bindPopup('Depart')
      .addTo(this.coucheTrajet!);

    this.carte?.fitBounds(L.latLngBounds(points), this.marges());
  }

  effacerTrajet(): void {
    this.trajet.set(null);
    this.coucheTrajet?.clearLayers();
  }

  /** Suit un camion : fiche du bas, marqueur mis en avant et, au besoin, carte recentree. */
  suivre(marqueur: MarqueurCarte, centrer = true): void {
    this.camionSuivi.set(marqueur);
    this.popupOuverte = true;
    if (this.trajet() && this.trajet()!.camionId !== marqueur.camionId) {
      this.effacerTrajet();
    }
    this.dessinerMarqueurs(this.marqueurs());
    if (centrer) {
      this.carte?.setView([marqueur.latitude, marqueur.longitude], Math.max(this.carte.getZoom(), 12));
    }
  }

  /** Fleches de la fiche : passe au camion precedent ou suivant. */
  parcourir(pas: number): void {
    const liste = this.marqueurs();
    const courant = this.selection();
    if (liste.length === 0 || !courant) {
      return;
    }
    const index = liste.findIndex((m) => m.camionId === courant.camionId);
    this.suivre(liste[(index + pas + liste.length) % liste.length]);
  }

  traiter(alerte: AlerteGps): void {
    this.service.traiterAlerte(alerte.id).subscribe({
      next: () => this.service.alertesRecentes(8).subscribe({
        next: (alertes) => this.alertes.set(alertes)
      }),
      error: () => this.erreur.set('Traitement de l alerte impossible.')
    });
  }

  enMouvement(marqueur: MarqueurCarte): boolean {
    return !marqueur.horsConnexion && (marqueur.vitesseKmh ?? 0) > 0;
  }

  couleurStatut(marqueur: MarqueurCarte): string {
    return marqueur.horsConnexion ? '#8a94a0' : COULEURS_STATUT[marqueur.statut];
  }

  /** Minutes ecoulees depuis la derniere trame. */
  minutesDepuis(marqueur: MarqueurCarte): number {
    return Math.max(0, Math.round((Date.now() - new Date(marqueur.derniereCommunication).getTime()) / 60000));
  }

  /** Fraicheur du signal : pleine a la reception, vide au seuil de deconnexion. */
  fraicheur(marqueur: MarqueurCarte): number {
    return Math.max(0, 1 - this.minutesDepuis(marqueur) / SEUIL_DECONNEXION_MIN);
  }

  /** Arc de jauge en demi-cercle, rempli selon une part comprise entre 0 et 1. */
  arc(part: number): string {
    const longueur = Math.PI * 50;
    return `${Math.min(Math.max(part, 0), 1) * longueur} ${longueur}`;
  }

  classeGravite(alerte: AlerteGps): string {
    return alerte.gravite === 'CRITIQUE' ? 'gravite critique'
      : alerte.gravite === 'AVERTISSEMENT' ? 'gravite avertissement' : 'gravite';
  }

  private echapper(texte: string): string {
    return texte.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
  }
}
