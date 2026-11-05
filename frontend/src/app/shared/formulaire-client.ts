import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { DocumentParc, TYPES_DOCUMENT, TypeDocument } from '../core/models/models';
import { PieceJointe } from './piece-jointe';
import { ClientFormulaire, RequeteFormulaire, SOURCE_CLIENTS, TypeFiche } from './source-clients';

/** Ce que le formulaire demande selon le type de client. */
interface ProfilClient {
  /** Phrase d'aide sous la tuile du type. */
  description: string;
  /** Trace SVG (viewBox 24) de l'icone de la tuile. */
  icone: string;
  /** Un particulier se decrit par son nom et ses prenoms, pas par une raison sociale. */
  personne: boolean;
  libelleNom: string;
  placeholderNom: string;
  formeJuridique?: boolean;
  rccm?: boolean;
  identifiantFiscal?: boolean;
  secteurActivite?: boolean;
  organismeTutelle?: boolean;
  numeroAgrement?: boolean;
  /** Titre de la rubrique representant / interlocuteur ; absente pour un particulier. */
  libelleContact?: string;
  /** Pieces proposees, la premiere etant celle attendue en priorite. */
  pieces: TypeDocument[];
}

export const PROFILS_CLIENT: Record<TypeFiche, ProfilClient> = {
  PARTICULIER: {
    description: 'Personne physique',
    icone: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20c0-3.3 3.6-6 8-6s8 2.7 8 6',
    personne: true,
    libelleNom: 'Nom',
    placeholderNom: 'Nom de famille',
    pieces: ['CARTE_IDENTITE', 'PASSEPORT', 'CARTE_ELECTEUR', 'PERMIS_CONDUIRE', 'CARTE_SEJOUR',
      'CARTE_CONSULAIRE', 'AUTRE']
  },
  ARTISAN: {
    description: 'Macon, plombier, electricien...',
    icone: 'M14.7 6.3a4 4 0 0 0-5.4 5l-6 6a1.5 1.5 0 0 0 2.1 2.1l6-6a4 4 0 0 0 5-5.4l-2.6 2.6-2.1-.6-.6-2.1z',
    personne: true,
    libelleNom: 'Nom',
    placeholderNom: 'Nom de famille',
    pieces: ['CARTE_IDENTITE', 'CARTE_OPERATEUR_ECONOMIQUE', 'PASSEPORT', 'CARTE_ELECTEUR', 'AUTRE']
  },
  ENTREPRISE: {
    description: 'Societe, commerce, entreprise individuelle',
    icone: 'M4 20V6l8-3v17M12 9h8v11M7 8h2M7 12h2M7 16h2M15 13h2M15 16h2M2 20h20',
    personne: false,
    libelleNom: 'Raison sociale',
    placeholderNom: 'Nom de l\'entreprise',
    formeJuridique: true,
    rccm: true,
    identifiantFiscal: true,
    secteurActivite: true,
    libelleContact: 'Representant / personne de contact',
    pieces: ['CARTE_OPERATEUR_ECONOMIQUE', 'RCCM', 'ATTESTATION_NIF', 'STATUTS', 'PIECE_REPRESENTANT', 'AUTRE']
  },
  ADMINISTRATION: {
    description: 'Ministere, mairie, etablissement public',
    icone: 'M3 9l9-5 9 5M5 9v9M9.5 9v9M14.5 9v9M19 9v9M3 20h18',
    personne: false,
    libelleNom: 'Nom de l\'administration',
    placeholderNom: 'Direction, service, mairie...',
    organismeTutelle: true,
    identifiantFiscal: true,
    libelleContact: 'Interlocuteur',
    pieces: ['LETTRE_MANDAT', 'PIECE_REPRESENTANT', 'AUTRE']
  },
  ONG: {
    description: 'ONG, association, organisation internationale',
    icone: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3.5 9h17M3.5 15h17M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9z',
    personne: false,
    libelleNom: 'Nom de l\'organisation',
    placeholderNom: 'Nom de l\'ONG ou de l\'organisation',
    numeroAgrement: true,
    identifiantFiscal: true,
    libelleContact: 'Representant',
    pieces: ['AGREMENT', 'ACCORD_SIEGE', 'STATUTS', 'PIECE_REPRESENTANT', 'AUTRE']
  }
};

export const FORMES_JURIDIQUES = [
  'Entreprise individuelle', 'SARL', 'SARLU', 'SA', 'SAS', 'SNC', 'GIE', 'Cooperative', 'Autre'
];

/** Pieces dont la date d'expiration merite d'etre suivie. */
const PIECES_EXPIRABLES: TypeDocument[] = ['CARTE_IDENTITE', 'PASSEPORT', 'CARTE_SEJOUR', 'CARTE_CONSULAIRE',
  'PERMIS_CONDUIRE', 'CARTE_OPERATEUR_ECONOMIQUE', 'PIECE_REPRESENTANT', 'AGREMENT'];

/** Une piece en cours de saisie, envoyee apres l'enregistrement de la fiche. */
interface PieceSaisie {
  cle: number;
  type: TypeDocument;
  numero: string;
  dateExpiration: string;
  fichier: File | null;
}

/** Resultat remonte au parent : la fiche et le bilan de l'envoi des pieces. */
export interface ClientEnregistre {
  client: ClientFormulaire;
  creation: boolean;
  echecsPieces: number;
}

/**
 * Fiche client adaptee a son type : un particulier se decrit par son nom et
 * sa piece d'identite, une entreprise par sa raison sociale, son RCCM et sa
 * carte d'operateur economique, etc.
 * <p>
 * Partagee entre l'ecran Clients (version complete, conditions commerciales
 * comprises) et la saisie d'une recette (version compacte). Le composant ne
 * pose pas de balise form : il peut ainsi vivre dans le formulaire de la
 * recette sans le soumettre.
 */
@Component({
  selector: 'app-formulaire-client',
  imports: [ReactiveFormsModule, PieceJointe],
  templateUrl: './formulaire-client.html',
  styleUrl: './formulaire-client.scss'
})
export class FormulaireClient implements OnInit {
  private readonly fb = inject(FormBuilder);
  /** Clients du parc par defaut ; le point de vente fournit les siens. */
  readonly source = inject(SOURCE_CLIENTS);

  /** Client a modifier ; absent pour une creation. */
  readonly clientId = input<number | null>(null);
  /** Version courte, sans conditions commerciales (saisie d'une recette). */
  readonly compact = input(false);
  readonly libelleEnregistrer = input('Enregistrer');
  /** Creation preremplie (client d'une demande en ligne qui se presente en agence). */
  readonly nomInitial = input<string | null>(null);
  /** Nom de famille et prenoms deja separes par la boutique : prioritaires sur nomInitial. */
  readonly nomFamilleInitial = input<string | null>(null);
  readonly prenomsInitial = input<string | null>(null);
  readonly telephoneInitial = input<string | null>(null);
  readonly emailInitial = input<string | null>(null);
  readonly enregistre = output<ClientEnregistre>();
  readonly annule = output<void>();

  readonly types = this.source.types.map((t) => ({ ...t, ...PROFILS_CLIENT[t.valeur] }));
  readonly formesJuridiques = FORMES_JURIDIQUES;

  readonly enregistrement = signal(false);
  /** Creation : clients deja enregistres avec ce telephone, a utiliser plutot que de creer un doublon. */
  readonly doublons = signal<ClientFormulaire[]>([]);
  /** Creer quand meme (homonyme ou numero partage), apres avoir vu les fiches existantes. */
  private doublonsVus = false;
  readonly erreur = signal<string | null>(null);
  /** Pieces deja enregistrees pour ce client (modification). */
  readonly documents = signal<DocumentParc[]>([]);
  readonly pieces = signal<PieceSaisie[]>([]);
  private prochaineCle = 1;

  readonly formulaire = this.fb.nonNullable.group({
    type: ['PARTICULIER' as TypeFiche],
    code: [''],
    raisonSociale: [''],
    nom: [''],
    prenoms: [''],
    dateNaissance: [''],
    profession: [''],
    formeJuridique: [''],
    rccm: [''],
    identifiantFiscal: [''],
    secteurActivite: [''],
    organismeTutelle: [''],
    numeroAgrement: [''],
    telephone: [''],
    email: ['', [Validators.email]],
    adresse: [''],
    ville: [''],
    pays: ['Togo'],
    personneContact: [''],
    fonctionContact: [''],
    telephoneContact: [''],
    conditionsCommerciales: [''],
    delaiPaiementJours: [null as number | null],
    plafondCredit: [null as number | null],
    observations: [''],
    actif: [true]
  });

  private readonly valeurs = toSignal(this.formulaire.valueChanges, {
    initialValue: this.formulaire.getRawValue()
  });
  readonly type = computed(() => (this.valeurs().type ?? 'PARTICULIER') as TypeFiche);
  readonly profil = computed(() => PROFILS_CLIENT[this.type()]);

  /** Champs obligatoires restant a saisir, cites a cote du bouton. */
  readonly champsManquants = computed(() => {
    const v = this.valeurs();
    const manquants: string[] = [];
    if (this.profil().personne) {
      if (!v.nom?.trim()) {
        manquants.push('le nom');
      }
      if (!v.prenoms?.trim()) {
        manquants.push('les prenoms');
      }
    } else if (!v.raisonSociale?.trim()) {
      manquants.push(this.profil().libelleNom.toLowerCase());
    }
    if (this.formulaire.controls.email.invalid) {
      manquants.push('un email valide');
    }
    return manquants;
  });

  ngOnInit(): void {
    const id = this.clientId();
    if (id === null) {
      this.preremplir();
      this.ajouterPiece();
      return;
    }
    this.source.parId(id).subscribe({
      next: (client) => {
        this.formulaire.patchValue(client as never);
        this.ajouterPiece();
      },
      error: () => this.erreur.set('Client introuvable.')
    });
    this.chargerDocuments(id);
  }

  /**
   * Preremplissage depuis une demande en ligne. Le nom et les prenoms arrivent
   * separes ; une demande anterieure ne porte que le nom complet « Nom Prenoms »
   * (premier mot = nom de famille).
   */
  private preremplir(): void {
    const nom = (this.nomInitial() ?? '').trim();
    const famille = (this.nomFamilleInitial() ?? '').trim();
    if (!nom && !famille && !this.telephoneInitial()) {
      return;
    }
    const mots = nom.split(/\s+/).filter(Boolean);
    this.formulaire.patchValue({
      type: 'PARTICULIER',
      nom: famille || (mots.length > 1 ? mots[0] : nom),
      prenoms: famille ? (this.prenomsInitial() ?? '').trim() : (mots.length > 1 ? mots.slice(1).join(' ') : ''),
      raisonSociale: nom,
      telephone: this.telephoneInitial() ?? '',
      email: this.emailInitial() ?? ''
    });
  }

  /** Changer de type remplace les pieces proposees par celles du nouveau type. */
  choisirType(type: TypeFiche): void {
    if (type === this.type()) {
      return;
    }
    this.formulaire.controls.type.setValue(type);
    const permises = PROFILS_CLIENT[type].pieces;
    this.pieces.update((liste) => {
      const gardees = liste.map((piece) =>
        permises.includes(piece.type) ? piece : { ...piece, type: this.premierTypeLibre(permises, liste) }
      );
      return gardees.length ? gardees : [this.nouvellePiece(permises[0])];
    });
  }

  libelleType(type: TypeDocument): string {
    return TYPES_DOCUMENT.find((t) => t.valeur === type)?.libelle ?? type;
  }

  expirable(type: TypeDocument): boolean {
    return PIECES_EXPIRABLES.includes(type);
  }

  /** Ajoute une ligne de piece, sur le premier type pas encore propose. */
  ajouterPiece(): void {
    const permises = this.profil().pieces;
    this.pieces.update((liste) => [...liste, this.nouvellePiece(this.premierTypeLibre(permises, liste))]);
  }

  retirerPiece(cle: number): void {
    this.pieces.update((liste) => liste.filter((piece) => piece.cle !== cle));
  }

  modifierPiece<K extends keyof PieceSaisie>(cle: number, champ: K, valeur: PieceSaisie[K]): void {
    this.pieces.update((liste) => liste.map((piece) => (piece.cle === cle ? { ...piece, [champ]: valeur } : piece)));
  }

  telecharger(document: DocumentParc): void {
    this.source.telechargerDocument(document.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const lien = window.document.createElement('a');
        lien.href = url;
        lien.download = document.fichierNom ?? `document-${document.id}`;
        lien.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.erreur.set('Telechargement impossible.')
    });
  }

  /** Entree valide la fiche sans soumettre le formulaire qui l'entoure (recette). */
  surEntree(evenement: Event): void {
    if ((evenement.target as HTMLElement).tagName === 'TEXTAREA') {
      return;
    }
    evenement.preventDefault();
    this.enregistrer();
  }

  enregistrer(): void {
    if (this.enregistrement()) {
      return;
    }
    if (this.champsManquants().length) {
      this.formulaire.markAllAsTouched();
      this.erreur.set(`Il manque ${this.champsManquants().join(', ')}.`);
      return;
    }
    const id = this.clientId();
    const telephone = this.formulaire.controls.telephone.value.trim();
    if (id === null && telephone && !this.doublonsVus) {
      this.enregistrement.set(true);
      this.source.parTelephone(telephone).subscribe({
        next: (existants) => {
          this.enregistrement.set(false);
          if (existants.length) {
            this.doublons.set(existants);
          } else {
            this.doublonsVus = true;
            this.enregistrer();
          }
        },
        error: () => {
          this.enregistrement.set(false);
          this.doublonsVus = true;
          this.enregistrer();
        }
      });
      return;
    }
    this.enregistrement.set(true);
    this.erreur.set(null);

    const requete = this.versRequete();
    const enregistrement = id === null ? this.source.creer(requete) : this.source.modifier(id, requete);

    enregistrement
      .pipe(switchMap((client) => this.envoyerPieces(client.id).pipe(map((echecs) => ({ client, echecs })))))
      .subscribe({
        next: ({ client, echecs }) => {
          this.enregistrement.set(false);
          this.pieces.set([]);
          this.chargerDocuments(client.id);
          this.ajouterPiece();
          if (echecs) {
            this.erreur.set(`Fiche enregistree, mais ${echecs} piece(s) n'ont pas pu etre envoyee(s).`);
          }
          this.enregistre.emit({ client, creation: id === null, echecsPieces: echecs });
        },
        error: (reponse) => {
          this.enregistrement.set(false);
          this.erreur.set(reponse.error?.message ?? 'Enregistrement du client impossible.');
        }
      });
  }

  /** Le client existe deja : on le reprend (avec les pieces deposees) au lieu de creer un doublon. */
  utiliserExistant(client: ClientFormulaire): void {
    this.enregistrement.set(true);
    this.envoyerPieces(client.id).subscribe((echecs) => {
      this.enregistrement.set(false);
      this.doublons.set([]);
      this.enregistre.emit({ client, creation: false, echecsPieces: echecs });
    });
  }

  /** Homonyme ou numero partage : la nouvelle fiche est creee malgre tout. */
  creerQuandMeme(): void {
    this.doublons.set([]);
    this.doublonsVus = true;
    this.enregistrer();
  }

  /** N'envoie que les champs du type choisi : ceux d'un autre type sont effaces. */
  private versRequete(): RequeteFormulaire {
    const v = this.formulaire.getRawValue();
    const p = this.profil();
    const garder = (actif: boolean | undefined, valeur: string) => (actif ? valeur.trim() : '');
    return {
      type: v.type,
      code: v.code.trim() || undefined,
      raisonSociale: p.personne ? undefined : v.raisonSociale.trim(),
      nom: garder(p.personne, v.nom),
      prenoms: garder(p.personne, v.prenoms),
      dateNaissance: p.personne && v.dateNaissance ? v.dateNaissance : undefined,
      profession: garder(p.personne, v.profession),
      formeJuridique: garder(p.formeJuridique, v.formeJuridique),
      rccm: garder(p.rccm, v.rccm),
      identifiantFiscal: garder(p.identifiantFiscal, v.identifiantFiscal),
      secteurActivite: garder(p.secteurActivite, v.secteurActivite),
      organismeTutelle: garder(p.organismeTutelle, v.organismeTutelle),
      numeroAgrement: garder(p.numeroAgrement, v.numeroAgrement),
      telephone: v.telephone.trim(),
      email: v.email.trim(),
      adresse: v.adresse.trim(),
      ville: v.ville.trim(),
      pays: v.pays.trim(),
      personneContact: garder(!!p.libelleContact, v.personneContact),
      fonctionContact: garder(!!p.libelleContact, v.fonctionContact),
      telephoneContact: garder(!!p.libelleContact, v.telephoneContact),
      conditionsCommerciales: v.conditionsCommerciales,
      delaiPaiementJours: v.delaiPaiementJours ?? undefined,
      plafondCredit: v.plafondCredit ?? undefined,
      observations: v.observations,
      actif: v.actif
    };
  }

  /** Televerse les pieces renseignees (fichier ou numero) ; renvoie le nombre d'echecs. */
  private envoyerPieces(clientId: number): Observable<number> {
    const aEnvoyer = this.pieces().filter((piece) => piece.fichier || piece.numero.trim());
    if (!aEnvoyer.length) {
      return of(0);
    }
    return forkJoin(
      aEnvoyer.map((piece) =>
        this.source
          .ajouterDocument(
            clientId,
            {
              type: piece.type,
              libelle: this.libelleType(piece.type),
              numero: piece.numero.trim() || undefined,
              dateExpiration: this.expirable(piece.type) && piece.dateExpiration ? piece.dateExpiration : null
            },
            piece.fichier
          )
          .pipe(
            map(() => true),
            catchError(() => of(false))
          )
      )
    ).pipe(map((resultats) => resultats.filter((ok) => !ok).length));
  }

  private chargerDocuments(id: number): void {
    this.source.documents(id).subscribe({ next: (liste) => this.documents.set(liste) });
  }

  private nouvellePiece(type: TypeDocument): PieceSaisie {
    return { cle: this.prochaineCle++, type, numero: '', dateExpiration: '', fichier: null };
  }

  /** Premier type de la liste pas encore saisi, a defaut le dernier (Autre). */
  private premierTypeLibre(permises: TypeDocument[], liste: PieceSaisie[]): TypeDocument {
    const pris = new Set([...liste.map((piece) => piece.type), ...this.documents().map((d) => d.type)]);
    return permises.find((type) => !pris.has(type)) ?? permises[permises.length - 1];
  }
}
