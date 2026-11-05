import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import {
  Chauffeur,
  ChauffeurRequete,
  DocumentParc,
  DocumentRequete,
  STATUTS_CHAUFFEUR,
  TYPES_DOCUMENT,
  TypeChauffeur,
  TypeDocument
} from '../core/models/models';
import { ChauffeurService } from '../core/services/chauffeur.service';
import { DocumentService } from '../core/services/document.service';
import { PieceJointe } from './piece-jointe';

/** Ce que le formulaire demande selon le lien du chauffeur avec l'entreprise. */
interface ProfilChauffeur {
  libelle: string;
  description: string;
  /** Trace SVG (viewBox 24) de l'icone du type. */
  icone: string;
  /** Teinte de la fiche et de la pastille du type. */
  couleur: string;
  /** Contrat, salaire et CNSS : salarie seulement. */
  salarie: boolean;
  /** Remuneration au tarif (journalier ou prestation). */
  tarif: boolean;
  /** Unites de tarif proposees ; une seule = pas de choix a faire. */
  unites: { valeur: 'JOUR' | 'VOYAGE' | 'MOIS'; libelle: string }[];
  identifiantFiscal: boolean;
  /** Libelle de la date de debut : embauche ou debut de collaboration. */
  libelleDebut: string;
  garant: boolean;
  /** Pieces proposees, les premieres etant attendues en priorite. */
  pieces: TypeDocument[];
}

const PIECES_IDENTITE: TypeDocument[] = ['CARTE_IDENTITE', 'PASSEPORT', 'CARTE_ELECTEUR', 'CARTE_SEJOUR',
  'CARTE_CONSULAIRE'];
const PIECES_GARANT: TypeDocument[] = ['GARANT_PIECE_IDENTITE', 'GARANT_PERMIS_CONDUIRE', 'GARANT_ATTESTATION'];

export const PROFILS_CHAUFFEUR: Record<TypeChauffeur, ProfilChauffeur> = {
  SALARIE: {
    libelle: 'Salarie',
    description: 'Employe sous contrat, CDI ou CDD',
    couleur: '#1f6fd1',
    icone: 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21c0-3.6 3.6-6.5 8-6.5s8 2.9 8 6.5M9 15l3 3 3-3',
    salarie: true,
    tarif: false,
    unites: [],
    identifiantFiscal: false,
    libelleDebut: 'Date d\'embauche',
    garant: true,
    pieces: ['CARTE_IDENTITE', 'PERMIS_CONDUIRE', 'CONTRAT', 'VISITE_MEDICALE', 'CASIER_JUDICIAIRE',
      ...PIECES_IDENTITE.slice(1), ...PIECES_GARANT, 'AUTRE']
  },
  JOURNALIER: {
    libelle: 'Journalier',
    description: 'Paye a la journee de travail',
    couleur: '#d9792b',
    icone: 'M4 6h16v14H4zM4 10h16M8 3v5M16 3v5M8 14h3v3H8z',
    salarie: false,
    tarif: true,
    unites: [{ valeur: 'JOUR', libelle: 'par jour' }],
    identifiantFiscal: false,
    libelleDebut: 'Debut de collaboration',
    garant: true,
    pieces: ['CARTE_IDENTITE', 'PERMIS_CONDUIRE', 'CASIER_JUDICIAIRE', 'VISITE_MEDICALE',
      ...PIECES_IDENTITE.slice(1), ...PIECES_GARANT, 'AUTRE']
  },
  PRESTATAIRE: {
    libelle: 'Prestataire',
    description: 'Independant ou sous-traitant qui facture',
    couleur: '#7a4fd1',
    icone: 'M7 3h7l4 4v14H7zM14 3v4h4M10 12h5M10 15.5h5M10 9h2',
    salarie: false,
    tarif: true,
    unites: [
      { valeur: 'JOUR', libelle: 'par jour' },
      { valeur: 'VOYAGE', libelle: 'par voyage' },
      { valeur: 'MOIS', libelle: 'par mois' }
    ],
    identifiantFiscal: true,
    libelleDebut: 'Debut de collaboration',
    garant: false,
    pieces: ['CARTE_IDENTITE', 'PERMIS_CONDUIRE', 'CONTRAT', 'CARTE_OPERATEUR_ECONOMIQUE', 'ATTESTATION_NIF',
      'CASIER_JUDICIAIRE', 'VISITE_MEDICALE', ...PIECES_IDENTITE.slice(1), 'AUTRE']
  }
};

const ORDRE_TYPES: TypeChauffeur[] = ['SALARIE', 'JOURNALIER', 'PRESTATAIRE'];

/** Categories de permis proposees, avec ce qu'elles autorisent. */
export const CATEGORIES_PERMIS: { valeur: string; libelle: string }[] = [
  { valeur: 'A', libelle: 'A — motos' },
  { valeur: 'B', libelle: 'B — vehicules legers' },
  { valeur: 'BE', libelle: 'BE — vehicule leger et remorque' },
  { valeur: 'C', libelle: 'C — poids lourds' },
  { valeur: 'CE', libelle: 'CE — poids lourd et remorque, semi-remorque' },
  { valeur: 'D', libelle: 'D — transport en commun' },
  { valeur: 'DE', libelle: 'DE — transport en commun et remorque' },
  { valeur: 'F', libelle: 'F — engins et vehicules amenages' }
];
export const GROUPES_SANGUINS = ['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'];

/** Pieces dont la date d'expiration merite d'etre suivie. */
const PIECES_EXPIRABLES: TypeDocument[] = ['CARTE_IDENTITE', 'PASSEPORT', 'CARTE_SEJOUR', 'CARTE_CONSULAIRE',
  'VISITE_MEDICALE', 'CONTRAT', 'CARTE_OPERATEUR_ECONOMIQUE', 'GARANT_PIECE_IDENTITE', 'GARANT_PERMIS_CONDUIRE'];

/** Une piece en cours de saisie, envoyee apres l'enregistrement de la fiche. */
interface PieceSaisie {
  cle: number;
  type: TypeDocument;
  numero: string;
  dateExpiration: string;
  fichier: File | null;
}

/** Resultat remonte au parent : la fiche et le bilan de l'envoi des pieces. */
export interface ChauffeurEnregistre {
  chauffeur: Chauffeur;
  creation: boolean;
  echecsPieces: number;
}

/**
 * Fiche chauffeur adaptee a son type : un salarie a un contrat, un salaire
 * et un numero CNSS ; un journalier un tarif a la journee ; un prestataire
 * un tarif au jour, au voyage ou au mois et son NIF. Les pieces proposees
 * suivent le type (contrat de travail, casier judiciaire, carte d'operateur
 * economique...).
 * <p>
 * Partagee entre l'ecran Chauffeurs (version complete) et l'affectation
 * (version compacte). Pas de balise form : elle peut vivre dans le
 * formulaire d'affectation sans le soumettre.
 */
@Component({
  selector: 'app-formulaire-chauffeur',
  imports: [ReactiveFormsModule, PieceJointe],
  templateUrl: './formulaire-chauffeur.html',
  styleUrl: './formulaire-client.scss',
  styles: `
    .tarif {
      display: flex;
      gap: 0.4rem;

      input {
        flex: 1;
        min-width: 0;
      }

      ::ng-deep .select2-container {
        flex: 0 0 130px;
        width: 130px !important;
      }

      .unite {
        align-self: center;
        font-size: 0.8rem;
        color: var(--couleur-texte-doux);
        white-space: nowrap;
      }
    }

    .aide-piece {
      align-self: end;
      padding-bottom: 0.5rem;
      font-size: 0.75rem;
      color: var(--couleur-texte-doux);
    }
  `
})
export class FormulaireChauffeur implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ChauffeurService);
  private readonly documentService = inject(DocumentService);

  /** Chauffeur a modifier ; absent pour une creation. */
  readonly chauffeurId = input<number | null>(null);
  /** Version courte (affectation) : l'essentiel pour mettre le chauffeur au volant. */
  readonly compact = input(false);
  readonly libelleEnregistrer = input('Enregistrer');
  readonly enregistre = output<ChauffeurEnregistre>();
  readonly annule = output<void>();

  readonly types = ORDRE_TYPES.map((valeur) => ({ valeur, ...PROFILS_CHAUFFEUR[valeur] }));
  readonly statuts = STATUTS_CHAUFFEUR;
  readonly groupesSanguins = GROUPES_SANGUINS;

  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  /** Pieces deja enregistrees pour ce chauffeur (modification). */
  readonly documents = signal<DocumentParc[]>([]);
  readonly pieces = signal<PieceSaisie[]>([]);
  private prochaineCle = 1;

  readonly formulaire = this.fb.nonNullable.group({
    typeChauffeur: ['SALARIE' as TypeChauffeur],
    matricule: ['', [Validators.maxLength(40)]],
    nom: [''],
    prenom: [''],
    dateNaissance: [''],
    groupeSanguin: [''],
    telephone: [''],
    telephoneUrgence: [''],
    email: ['', [Validators.email]],
    adresse: [''],
    numeroPermis: [''],
    categoriesPermis: [''],
    permisObtention: [''],
    permisExpiration: [''],
    typeContrat: ['CDI'],
    dateEmbauche: [''],
    dateFinContrat: [''],
    salaireMensuel: [null as number | null],
    numeroCnss: [''],
    tarif: [null as number | null],
    uniteTarif: ['JOUR' as 'JOUR' | 'VOYAGE' | 'MOIS'],
    identifiantFiscal: [''],
    garantNom: [''],
    garantLien: [''],
    garantTelephone: [''],
    garantTelephoneSecondaire: [''],
    garantAdresse: [''],
    garantProfession: [''],
    garantPieceIdentite: [''],
    statut: ['ACTIF'],
    observations: [''],
    actif: [true]
  });

  private readonly valeurs = toSignal(this.formulaire.valueChanges, {
    initialValue: this.formulaire.getRawValue()
  });
  readonly type = computed(() => (this.valeurs().typeChauffeur ?? 'SALARIE') as TypeChauffeur);
  readonly profil = computed(() => PROFILS_CHAUFFEUR[this.type()]);
  readonly couleurType = computed(() => this.profil().couleur);
  /** Categories de la liste, plus la valeur d'une ancienne fiche saisie hors liste (« C, CE »). */
  readonly optionsCategories = computed(() => {
    const actuelle = (this.valeurs().categoriesPermis ?? '').trim();
    return actuelle && !CATEGORIES_PERMIS.some((c) => c.valeur === actuelle)
      ? [...CATEGORIES_PERMIS, { valeur: actuelle, libelle: actuelle }]
      : CATEGORIES_PERMIS;
  });
  readonly estCdd = computed(() => this.valeurs().typeContrat === 'CDD');

  /** Champs obligatoires restant a saisir, cites sous les boutons. */
  readonly champsManquants = computed(() => {
    const v = this.valeurs();
    const manquants: string[] = [];
    if (!v.nom?.trim()) {
      manquants.push('le nom');
    }
    if (!v.prenom?.trim()) {
      manquants.push('les prenoms');
    }
    if (!v.telephone?.trim()) {
      manquants.push('le telephone');
    }
    if (this.formulaire.controls.email.invalid) {
      manquants.push('un email valide');
    }
    if (v.permisObtention && v.permisExpiration && v.permisExpiration < v.permisObtention) {
      manquants.push('une expiration du permis posterieure a son obtention');
    }
    return manquants;
  });

  ngOnInit(): void {
    const id = this.chauffeurId();
    if (id === null) {
      this.pieces.set([this.nouvellePiece('CARTE_IDENTITE')]);
      return;
    }
    this.service.parId(id).subscribe({
      next: (chauffeur) => {
        this.formulaire.patchValue(chauffeur as never);
        this.ajouterPiece();
      },
      error: () => this.erreur.set('Chauffeur introuvable.')
    });
    this.chargerDocuments(id);
  }

  /** Changer de type remplace les pieces propres a l'ancien type. */
  choisirType(type: TypeChauffeur): void {
    if (type === this.type()) {
      return;
    }
    const profil = PROFILS_CHAUFFEUR[type];
    this.formulaire.patchValue({ typeChauffeur: type, uniteTarif: profil.unites[0]?.valeur ?? 'JOUR' });
    this.pieces.update((liste) => {
      const gardees = liste.map((piece) =>
        profil.pieces.includes(piece.type) ? piece : { ...piece, type: this.premierTypeLibre(profil.pieces, liste) }
      );
      return gardees.length ? gardees : [this.nouvellePiece(profil.pieces[0])];
    });
  }

  libelleType(type: TypeDocument): string {
    return TYPES_DOCUMENT.find((t) => t.valeur === type)?.libelle ?? type;
  }

  expirable(type: TypeDocument): boolean {
    return PIECES_EXPIRABLES.includes(type);
  }

  /** Le permis reprend numero et dates de la rubrique Permis : rien a ressaisir. */
  reprisDuPermis(type: TypeDocument): boolean {
    return type === 'PERMIS_CONDUIRE';
  }

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
    this.documentService.telecharger(document.id).subscribe({
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

  /** Entree valide la fiche sans soumettre le formulaire qui l'entoure (affectation). */
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
    this.enregistrement.set(true);
    this.erreur.set(null);

    const id = this.chauffeurId();
    const requete = this.versRequete();
    const enregistrement = id === null ? this.service.creer(requete) : this.service.modifier(id, requete);

    enregistrement
      .pipe(switchMap((chauffeur) =>
        this.envoyerPieces(chauffeur).pipe(map((echecs) => ({ chauffeur, echecs })))))
      .subscribe({
        next: ({ chauffeur, echecs }) => {
          this.enregistrement.set(false);
          this.pieces.set([]);
          this.chargerDocuments(chauffeur.id);
          this.ajouterPiece();
          if (echecs) {
            this.erreur.set(`Fiche enregistree, mais ${echecs} piece(s) n'ont pas pu etre envoyee(s).`);
          }
          this.enregistre.emit({ chauffeur, creation: id === null, echecsPieces: echecs });
        },
        error: (reponse) => {
          this.enregistrement.set(false);
          this.erreur.set(reponse.error?.message ?? 'Enregistrement du chauffeur impossible.');
        }
      });
  }

  /** N'envoie que les champs du type choisi : ceux d'un autre type sont effaces. */
  private versRequete(): ChauffeurRequete {
    const v = this.formulaire.getRawValue();
    const p = this.profil();
    const texte = (valeur: string) => valeur.trim() || undefined;
    const date = (valeur: string) => valeur || undefined;
    return {
      typeChauffeur: v.typeChauffeur,
      matricule: v.matricule.trim(),
      nom: v.nom.trim(),
      prenom: v.prenom.trim(),
      telephone: v.telephone.trim(),
      telephoneUrgence: texte(v.telephoneUrgence),
      email: texte(v.email),
      adresse: texte(v.adresse),
      dateNaissance: date(v.dateNaissance),
      groupeSanguin: texte(v.groupeSanguin),
      numeroPermis: texte(v.numeroPermis),
      categoriesPermis: texte(v.categoriesPermis),
      permisObtention: date(v.permisObtention),
      permisExpiration: date(v.permisExpiration),
      dateEmbauche: date(v.dateEmbauche),
      typeContrat: p.salarie ? v.typeContrat : undefined,
      dateFinContrat: p.salarie && v.typeContrat === 'CDD' ? date(v.dateFinContrat) : undefined,
      salaireMensuel: p.salarie ? (v.salaireMensuel ?? undefined) : undefined,
      numeroCnss: p.salarie ? texte(v.numeroCnss) : undefined,
      tarif: p.tarif ? (v.tarif ?? undefined) : undefined,
      uniteTarif: p.tarif ? v.uniteTarif : undefined,
      identifiantFiscal: p.identifiantFiscal ? texte(v.identifiantFiscal) : undefined,
      garantNom: p.garant ? texte(v.garantNom) : undefined,
      garantLien: p.garant ? texte(v.garantLien) : undefined,
      garantTelephone: p.garant ? texte(v.garantTelephone) : undefined,
      garantTelephoneSecondaire: p.garant ? texte(v.garantTelephoneSecondaire) : undefined,
      garantAdresse: p.garant ? texte(v.garantAdresse) : undefined,
      garantProfession: p.garant ? texte(v.garantProfession) : undefined,
      garantPieceIdentite: p.garant ? texte(v.garantPieceIdentite) : undefined,
      statut: v.statut as ChauffeurRequete['statut'],
      observations: v.observations,
      actif: v.actif
    };
  }

  /**
   * Televerse les pieces renseignees (fichier ou numero). Une piece deja
   * enregistree du meme type est remplacee plutot que dupliquee (sauf Autre).
   * Renvoie le nombre d'echecs.
   */
  private envoyerPieces(chauffeur: Chauffeur): Observable<number> {
    const aEnvoyer = this.pieces().filter((piece) =>
      piece.fichier || (piece.numero.trim() && !this.reprisDuPermis(piece.type)));
    if (!aEnvoyer.length) {
      return of(0);
    }
    return forkJoin(
      aEnvoyer.map((piece) => {
        const existant = piece.type === 'AUTRE' ? null
          : this.documents().find((d) => d.type === piece.type) ?? null;
        const requete = this.requetePiece(chauffeur, piece);
        const envoi = existant
          ? this.documentService.modifier(existant.id, requete, piece.fichier)
          : this.documentService.creer(requete, piece.fichier);
        return envoi.pipe(map(() => true), catchError(() => of(false)));
      })
    ).pipe(map((resultats) => resultats.filter((ok) => !ok).length));
  }

  private requetePiece(chauffeur: Chauffeur, piece: PieceSaisie): DocumentRequete {
    const garant = piece.type.startsWith('GARANT_');
    const titulaire = garant ? chauffeur.garantNom : chauffeur.nomComplet;
    const libelle = this.libelleType(piece.type);
    const requete: DocumentRequete = {
      proprietaireType: 'CHAUFFEUR',
      proprietaireId: chauffeur.id,
      type: piece.type,
      libelle: titulaire?.trim() ? `${libelle} — ${titulaire.trim()}` : libelle,
      numero: piece.numero.trim() || undefined,
      dateExpiration: this.expirable(piece.type) && piece.dateExpiration ? piece.dateExpiration : null
    };
    if (this.reprisDuPermis(piece.type)) {
      requete.numero = chauffeur.numeroPermis || undefined;
      requete.dateEmission = chauffeur.permisObtention || null;
      requete.dateExpiration = chauffeur.permisExpiration || null;
    }
    if (piece.type === 'GARANT_PIECE_IDENTITE' && !requete.numero) {
      requete.numero = chauffeur.garantPieceIdentite || undefined;
    }
    return requete;
  }

  private chargerDocuments(id: number): void {
    this.documentService.lister({ proprietaireType: 'CHAUFFEUR', proprietaireId: id, size: 50 }).subscribe({
      next: (page) => this.documents.set(page.content)
    });
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
