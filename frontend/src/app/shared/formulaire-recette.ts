import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { map } from 'rxjs';
import { ClientParc, TYPES_CLIENT, TYPES_LOCATION, TypeLocation } from '../core/models/exploitation';
import {
  MODES_PAIEMENT,
  Recette,
  RecetteLocationRequete,
  RecetteRequete,
  TYPES_RECETTE,
  TypeRecette
} from '../core/models/finance';
import { Camion } from '../core/models/models';
import { AffectationService } from '../core/services/affectation.service';
import { CamionService } from '../core/services/camion.service';
import { ClientService } from '../core/services/client.service';
import { RecetteService } from '../core/services/recette.service';
import { OptionSelect, SelectRecherche } from './select-recherche';
import { VALIDATION_FORMULAIRE } from './validation-formulaire';

/** Ce que la saisie demande pour un type de recette (hors location, qui a son bloc). */
interface ProfilRecette {
  /** Une ligne sous le type, pour dire a quoi il sert. */
  aide: string;
  /** Titre du bloc de details, quand le type en demande. */
  titreDetails?: string;
  clientRequis: boolean;
  /** Periode de la prestation, avec les libelles adaptes au type. */
  periode?: { debut: string; fin: string };
  /** Lieux : depart facultatif pour une livraison, ou les deux exiges. */
  trajet?: 'complet' | 'destination';
  marchandise?: boolean;
  /** Kilometres factures au tarif du kilometre : le montant se calcule. */
  kilometres?: boolean;
}

const PROFILS: Record<Exclude<TypeRecette, 'LOCATION'>, ProfilRecette> = {
  TRANSPORT: {
    aide: 'Acheminement de marchandises pour un client, d\'un lieu a un autre.',
    titreDetails: 'Details du transport',
    clientRequis: true,
    periode: { debut: 'Date de depart', fin: 'Date d\'arrivee' },
    trajet: 'complet',
    marchandise: true
  },
  LIVRAISON: {
    aide: 'Remise de marchandises a un destinataire.',
    titreDetails: 'Details de la livraison',
    clientRequis: true,
    periode: { debut: 'Date d\'enlevement', fin: 'Date de livraison' },
    trajet: 'destination',
    marchandise: true
  },
  VOYAGE: {
    aide: 'Deplacement aller ou aller-retour facture au forfait.',
    titreDetails: 'Details du voyage',
    clientRequis: true,
    periode: { debut: 'Date de depart', fin: 'Date de retour' },
    trajet: 'complet'
  },
  MISSION_SPECIALE: {
    aide: 'Prestation ponctuelle hors transport courant (convoi, evenement, depannage...).',
    titreDetails: 'Details de la mission',
    clientRequis: true,
    periode: { debut: 'Debut de la mission', fin: 'Fin de la mission' }
  },
  CONTRAT: {
    aide: 'Somme due au titre d\'un contrat sur une periode (abonnement, mise a disposition...).',
    titreDetails: 'Periode du contrat',
    clientRequis: true,
    periode: { debut: 'Debut de la periode', fin: 'Fin de la periode' }
  },
  KILOMETRAGE_SUPPLEMENTAIRE: {
    aide: 'Kilometres parcourus au-dela de ce qui etait prevu, factures au kilometre.',
    titreDetails: 'Kilometrage facture',
    clientRequis: false,
    kilometres: true
  },
  FRAIS_SUPPLEMENTAIRE: {
    aide: 'Frais refactures en plus de la prestation (peage, attente, manutention...).',
    clientRequis: false
  },
  AUTRE: {
    aide: 'Toute autre entree d\'argent rattachee au vehicule.',
    clientRequis: false
  }
};

/**
 * Formulaire d'enregistrement d'une recette, partage par l'ecran Recettes et la
 * fiche vehicule.
 * <p>
 * Les champs suivent le type choisi : periode, trajet et marchandise pour un
 * transport, periode pour un contrat, kilometres pour un kilometrage
 * supplementaire... Le libelle est propose d'apres ces details. Une recette de
 * location demande les conditions de la location et son montant s'en deduit ;
 * le contrat correspondant est cree avec la recette. Le chauffeur n'est jamais
 * saisi : il decoule de l'affectation en cours du vehicule.
 */
@Component({
  selector: 'app-formulaire-recette',
  imports: [ReactiveFormsModule, FormsModule, SelectRecherche, DecimalPipe, VALIDATION_FORMULAIRE],
  templateUrl: './formulaire-recette.html',
  styleUrl: './formulaire-recette.scss'
})
export class FormulaireRecette implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(RecetteService);
  private readonly camionService = inject(CamionService);
  private readonly clientService = inject(ClientService);
  private readonly affectationService = inject(AffectationService);

  /** Vehicule impose par l'ecran appelant ; le champ devient alors non modifiable. */
  readonly camionImpose = input<number | null>(null);

  readonly enregistree = output<Recette>();
  readonly annulee = output<void>();

  readonly types = TYPES_RECETTE;
  readonly modes = MODES_PAIEMENT;
  readonly typesLocation = TYPES_LOCATION;
  readonly typesClient = TYPES_CLIENT;

  readonly camions = signal<Camion[]>([]);
  readonly clients = signal<ClientParc[]>([]);
  /** Chauffeur conduisant le vehicule choisi ; null s'il n'est affecte a personne. */
  readonly chauffeur = signal<string | null>(null);
  readonly chauffeurId = signal<number | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);

  /** Creation d'un client a la volee, quand il n'existe pas encore. */
  readonly creationClient = signal(false);
  readonly creationEnCours = signal(false);
  nouveauClientNom = '';
  nouveauClientTelephone = '';
  nouveauClientType: string = 'ENTREPRISE';

  readonly formulaire = this.fb.nonNullable.group({
    camionId: [null as number | null, [Validators.required]],
    type: ['TRANSPORT' as TypeRecette, [Validators.required]],
    clientId: [null as number | null],
    libelle: [''],
    montant: [0, [Validators.min(0)]],
    dateRecette: [this.aujourdhui(), [Validators.required]],
    modePaiement: [null as string | null],
    reference: [''],
    observations: [''],
    // Details de la prestation, selon le type.
    dateDebut: [this.aujourdhui()],
    dateFin: [this.aujourdhui()],
    lieuDepart: [''],
    destination: [''],
    marchandise: [''],
    kilometrage: [null as number | null],
    tarif: [0],
    // Mode de facturation, propre a une recette de location.
    typeLocation: ['JOURNALIERE' as TypeLocation]
  });

  /**
   * Toutes les valeurs du formulaire, sous forme de signal : chaque calcul
   * ci-dessous se met a jour a la moindre saisie. (La valeur brute du
   * formulaire n'est pas suivie par un calcul et le figerait.)
   */
  private readonly valeurs = toSignal(
    this.formulaire.valueChanges.pipe(map(() => this.formulaire.getRawValue())),
    { initialValue: this.formulaire.getRawValue() }
  );

  readonly typeChoisi = computed(() => this.valeurs().type);
  readonly estLocation = computed(() => this.typeChoisi() === 'LOCATION');
  /** Profil du type choisi ; la location a son propre bloc de conditions. */
  readonly profil = computed<ProfilRecette | null>(() => {
    const type = this.typeChoisi();
    return type === 'LOCATION' ? null : PROFILS[type];
  });

  // --- Location ---------------------------------------------------------
  readonly typeLocationChoisi = computed(() => this.valeurs().typeLocation);
  /** La location au kilometre se compte en distance, pas en jours. */
  readonly demandeKilometrage = computed(() => this.typeLocationChoisi() === 'KILOMETRIQUE');
  readonly demandePeriode = computed(
    () => this.typeLocationChoisi() !== 'VOYAGE' && !this.demandeKilometrage()
  );
  readonly uniteTarif = computed(
    () => this.typesLocation.find((t) => t.valeur === this.typeLocationChoisi())?.unite ?? 'FCFA'
  );

  /** Nombre d'unites facturees pour une location : jours, kilometres, mois ou forfait. */
  readonly quantite = computed(() => {
    const v = this.valeurs();
    if (v.typeLocation === 'VOYAGE') {
      return 1;
    }
    if (v.typeLocation === 'KILOMETRIQUE') {
      return v.kilometrage ?? 0;
    }
    const jours = this.nombreJours(v.dateDebut, v.dateFin);
    return v.typeLocation === 'JOURNALIERE' ? jours : jours > 0 ? Math.max(1, Math.round(jours / 30)) : 0;
  });

  readonly libelleQuantite = computed(() => {
    switch (this.typeLocationChoisi()) {
      case 'JOURNALIERE':
        return `${this.quantite()} jour(s)`;
      case 'KILOMETRIQUE':
        return `${this.quantite()} km`;
      case 'VOYAGE':
        return 'forfait';
      default:
        return `${this.quantite()} mois`;
    }
  });

  // --- Montant et libelle -------------------------------------------------
  /** Le montant se calcule pour une location et un kilometrage supplementaire. */
  readonly montantAutomatique = computed(() => this.estLocation() || !!this.profil()?.kilometres);

  readonly montantCalcule = computed(() => {
    const v = this.valeurs();
    if (this.estLocation()) {
      return Math.round((v.tarif || 0) * this.quantite());
    }
    if (this.profil()?.kilometres) {
      return Math.round((v.tarif || 0) * (v.kilometrage || 0));
    }
    return v.montant || 0;
  });

  /** Duree de la prestation, rappelee sous la periode. */
  readonly dureeJours = computed(() => this.nombreJours(this.valeurs().dateDebut, this.valeurs().dateFin));

  /** Libelle propose d'apres les details ; la saisie libre le remplace. */
  readonly libelleSuggere = computed(() => {
    const v = this.valeurs();
    const trajet = v.lieuDepart.trim() && v.destination.trim()
      ? `${v.lieuDepart.trim()} → ${v.destination.trim()}`
      : v.destination.trim() ? `vers ${v.destination.trim()}` : '';
    const periode = v.dateDebut
      ? v.dateFin && v.dateFin !== v.dateDebut
        ? `du ${this.court(v.dateDebut)} au ${this.court(v.dateFin)}`
        : `le ${this.court(v.dateDebut)}`
      : '';
    switch (this.typeChoisi()) {
      case 'TRANSPORT':
        return ['Transport', v.marchandise.trim(), trajet].filter(Boolean).join(' ');
      case 'LIVRAISON':
        return ['Livraison', v.marchandise.trim(), trajet].filter(Boolean).join(' ');
      case 'VOYAGE':
        return ['Voyage', trajet, periode].filter(Boolean).join(' ');
      case 'MISSION_SPECIALE':
        return ['Mission speciale', periode].filter(Boolean).join(' ');
      case 'CONTRAT':
        return ['Contrat', periode].filter(Boolean).join(' ');
      case 'KILOMETRAGE_SUPPLEMENTAIRE':
        return v.kilometrage ? `Kilometrage supplementaire : ${v.kilometrage} km` : '';
      default:
        return '';
    }
  });

  /** Libelle retenu : celui saisi, a defaut celui propose. */
  private readonly libelleFinal = computed(() => this.valeurs().libelle.trim() || this.libelleSuggere());

  /** Ce qui manque encore pour enregistrer, selon le type choisi. */
  readonly champsManquants = computed(() => {
    const v = this.valeurs();
    const manquants: string[] = [];
    if (v.camionId === null) {
      manquants.push('le vehicule');
    }
    if (this.estLocation()) {
      if (v.clientId === null) {
        manquants.push('le client');
      }
      if (this.montantCalcule() <= 0) {
        manquants.push('les conditions de location (periode et tarif)');
      }
      return manquants;
    }
    const profil = this.profil()!;
    if (profil.clientRequis && v.clientId === null) {
      manquants.push('le client');
    }
    if (profil.periode) {
      if (!v.dateDebut || !v.dateFin) {
        manquants.push('la periode');
      } else if (v.dateFin < v.dateDebut) {
        manquants.push('une date de fin posterieure au debut');
      }
    }
    if (profil.trajet === 'complet' && !v.lieuDepart.trim()) {
      manquants.push('le lieu de depart');
    }
    if (profil.trajet && !v.destination.trim()) {
      manquants.push('la destination');
    }
    if (profil.kilometres) {
      if (!v.kilometrage) {
        manquants.push('les kilometres');
      }
      if (!v.tarif) {
        manquants.push('le tarif du kilometre');
      }
    } else if (this.montantCalcule() <= 0) {
      manquants.push('un montant superieur a 0');
    }
    if (!this.libelleFinal()) {
      manquants.push('le libelle');
    }
    return manquants;
  });

  readonly saisieComplete = computed(() => this.champsManquants().length === 0);

  readonly optionsCamion = computed<OptionSelect[]>(() =>
    this.camions().map((camion) => ({
      valeur: camion.id,
      libelle: camion.immatriculation,
      detail: [camion.marque, camion.modele].filter(Boolean).join(' ')
    }))
  );
  readonly optionsClient = computed<OptionSelect[]>(() =>
    this.clients().map((client) => ({
      valeur: client.id,
      libelle: client.raisonSociale,
      detail: client.telephone
    }))
  );

  /** Immatriculation du vehicule impose, affichee a la place de la liste. */
  readonly camionLibelle = computed(() => {
    const camion = this.camions().find((c) => c.id === this.camionImpose());
    return camion
      ? `${camion.immatriculation} — ${camion.marque ?? ''} ${camion.modele ?? ''}`.trim()
      : '';
  });

  ngOnInit(): void {
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.clientService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.clients.set(page.content)
    });
    // Le vehicule determine son chauffeur.
    this.formulaire.controls.camionId.valueChanges.subscribe((id) => this.camionChange(id));

    const impose = this.camionImpose();
    if (impose !== null) {
      this.formulaire.patchValue({ camionId: impose });
    }
  }

  private camionChange(camionId: number | null): void {
    this.chauffeur.set(null);
    this.chauffeurId.set(null);
    if (camionId === null) {
      return;
    }
    this.affectationService.couranteDuCamion(camionId).subscribe({
      next: (affectation) => {
        this.chauffeur.set(affectation.chauffeurNom ?? null);
        this.chauffeurId.set(affectation.chauffeurId ?? null);
      },
      // Un vehicule sans affectation repond 404 : ce n'est pas une erreur a signaler.
      error: () => {
        this.chauffeur.set(null);
        this.chauffeurId.set(null);
      }
    });
  }

  /** Ouvre ou referme le petit formulaire de creation de client. */
  basculerCreationClient(): void {
    this.creationClient.update((ouvert) => !ouvert);
    this.nouveauClientNom = '';
    this.nouveauClientTelephone = '';
  }

  /**
   * Cree le client sans quitter la saisie, puis le selectionne.
   * Seule la raison sociale est exigee : la fiche complete se complete plus tard
   * depuis l'ecran Clients.
   */
  creerClient(): void {
    const nom = this.nouveauClientNom.trim();
    if (!nom || this.creationEnCours()) {
      return;
    }
    this.creationEnCours.set(true);
    this.erreur.set(null);
    this.clientService
      .creer({
        raisonSociale: nom,
        telephone: this.nouveauClientTelephone.trim() || undefined,
        type: this.nouveauClientType as ClientParc['type']
      })
      .subscribe({
        next: (client) => {
          this.creationEnCours.set(false);
          this.clients.update((liste) => [client, ...liste]);
          this.formulaire.patchValue({ clientId: client.id });
          this.creationClient.set(false);
        },
        error: (reponse) => {
          this.creationEnCours.set(false);
          this.erreur.set(reponse.error?.message ?? 'Creation du client impossible.');
        }
      });
  }

  enregistrer(): void {
    if (this.enregistrement() || !this.saisieComplete()) {
      return;
    }
    this.erreur.set(null);
    this.enregistrement.set(true);

    const v = this.formulaire.getRawValue();
    const requete = this.estLocation()
      ? this.service.enregistrerLocation(this.versLocation(v))
      : this.service.creer(this.versRecette(v));

    requete.subscribe({
      next: (recette) => {
        this.enregistrement.set(false);
        this.reinitialiser();
        this.enregistree.emit(recette);
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(reponse.error?.message ?? 'Enregistrement de la recette impossible.');
      }
    });
  }

  annuler(): void {
    this.reinitialiser();
    this.annulee.emit();
  }

  private versLocation(v: ReturnType<typeof this.formulaire.getRawValue>): RecetteLocationRequete {
    return {
      camionId: v.camionId!,
      clientId: v.clientId!,
      chauffeurId: this.chauffeurId(),
      typeLocation: v.typeLocation,
      dateDebut: v.dateDebut,
      dateFin: v.dateFin || null,
      tarif: v.tarif,
      kilometrage: v.kilometrage,
      lieuDepart: v.lieuDepart || undefined,
      destination: v.destination || undefined,
      dateRecette: v.dateRecette,
      modePaiement: (v.modePaiement || null) as RecetteRequete['modePaiement'],
      reference: v.reference || undefined,
      observations: v.observations || undefined
    };
  }

  /** Seuls les details que le type demande partent avec la recette. */
  private versRecette(v: ReturnType<typeof this.formulaire.getRawValue>): RecetteRequete {
    const profil = this.profil()!;
    return {
      camionId: v.camionId!,
      clientId: v.clientId,
      type: v.type,
      libelle: this.libelleFinal(),
      montant: this.montantCalcule(),
      dateRecette: v.dateRecette,
      modePaiement: (v.modePaiement || null) as RecetteRequete['modePaiement'],
      reference: v.reference || undefined,
      observations: v.observations || undefined,
      dateDebut: profil.periode ? v.dateDebut : null,
      dateFin: profil.periode ? v.dateFin : null,
      lieuDepart: profil.trajet ? v.lieuDepart || null : null,
      destination: profil.trajet ? v.destination || null : null,
      marchandise: profil.marchandise ? v.marchandise || null : null,
      kilometres: profil.kilometres ? v.kilometrage : null
    };
  }

  private reinitialiser(): void {
    const camionId = this.camionImpose() ?? this.formulaire.getRawValue().camionId;
    this.formulaire.reset({
      camionId,
      type: 'TRANSPORT',
      clientId: null,
      libelle: '',
      montant: 0,
      dateRecette: this.aujourdhui(),
      modePaiement: null,
      reference: '',
      observations: '',
      dateDebut: this.aujourdhui(),
      dateFin: this.aujourdhui(),
      lieuDepart: '',
      destination: '',
      marchandise: '',
      kilometrage: null,
      tarif: 0,
      typeLocation: 'JOURNALIERE'
    });
  }

  /** Jours couverts, bornes comprises : du 1er au 10, dix jours. */
  private nombreJours(debut: string, fin: string): number {
    if (!debut) {
      return 0;
    }
    const jours = Math.floor(
      (new Date(fin || this.aujourdhui()).getTime() - new Date(debut).getTime()) / 86_400_000
    ) + 1;
    return Math.max(0, jours);
  }

  /** Date courte JJ/MM pour le libelle propose. */
  private court(date: string): string {
    const [, mois, jour] = date.split('-');
    return `${jour}/${mois}`;
  }

  private aujourdhui(): string {
    const date = new Date();
    const mois = `${date.getMonth() + 1}`.padStart(2, '0');
    const jour = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${mois}-${jour}`;
  }
}
