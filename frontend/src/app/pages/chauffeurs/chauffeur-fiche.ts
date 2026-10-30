import { Component, OnInit, computed, inject, signal, input, output } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ChauffeurRequete,
  DocumentParc,
  DocumentRequete,
  STATUTS_CHAUFFEUR,
  TYPES_DOCUMENT,
  TypeDocument
} from '../../core/models/models';
import { DocumentService } from '../../core/services/document.service';
import { OptionSelect, SelectRecherche } from '../../shared/select-recherche';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { PieceJointe } from '../../shared/piece-jointe';
import { Modale } from '../../shared/modale';
import { VALIDATION_FORMULAIRE } from '../../shared/validation-formulaire';

@Component({
  selector: 'app-chauffeur-fiche',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, SelectRecherche, PieceJointe, Modale, VALIDATION_FORMULAIRE],
  templateUrl: './chauffeur-fiche.html'
})
export class ChauffeurFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ChauffeurService);
  private readonly documentService = inject(DocumentService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly statuts = STATUTS_CHAUFFEUR;
  readonly id = signal<number | null>(null);
  /** Ouverte dans une fenetre depuis la liste : creation seule, sans en-tete de page. */
  readonly enModale = input(false);
  /** Creation terminee en mode fenetre : identifiant du nouvel enregistrement. */
  readonly cree = output<number>();
  readonly annule = output<void>();
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Dossiers du chauffeur et de son garant. */
  readonly documents = signal<DocumentParc[]>([]);
  readonly televersementVisible = signal(false);

  /** Pieces deposees dans le formulaire, envoyees apres l'enregistrement de la fiche. */
  readonly piecesChoisies = signal<Partial<Record<TypeDocument, File | null>>>({});

  typeDocument: TypeDocument = 'PIECE_IDENTITE';
  libelleDocument = '';
  fichier: File | null = null;

  readonly optionsTypeDocument = computed<OptionSelect[]>(() =>
    TYPES_DOCUMENT.map((type) => ({ valeur: type.valeur, libelle: type.libelle }))
  );
  readonly optionsStatut = computed<OptionSelect[]>(() =>
    STATUTS_CHAUFFEUR.map((statut) => ({ valeur: statut.valeur, libelle: statut.libelle }))
  );

  readonly formulaire = this.fb.nonNullable.group({
    matricule: ['', [Validators.required, Validators.maxLength(40)]],
    nom: ['', [Validators.required]],
    prenom: ['', [Validators.required]],
    telephone: ['', [Validators.required]],
    telephoneUrgence: [''],
    email: ['', [Validators.email]],
    adresse: [''],
    dateNaissance: [null as string | null],
    numeroPermis: [''],
    categoriesPermis: [''],
    permisObtention: [null as string | null],
    permisExpiration: [null as string | null],
    dateEmbauche: [null as string | null],
    salaireMensuel: [null as number | null],
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

  get titre(): string {
    return this.id() === null ? 'Nouveau chauffeur' : 'Fiche du chauffeur';
  }

  ngOnInit(): void {
    const parametre = this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      const id = Number(parametre);
      this.id.set(id);
      this.service.parId(id).subscribe({
        next: (chauffeur) => this.formulaire.patchValue(chauffeur as never),
        error: () => this.erreur.set('Chauffeur introuvable.')
      });
      this.chargerDocuments(id);
      const echecs = (history.state as { echecsPieces?: number } | null)?.echecsPieces ?? 0;
      if (echecs > 0) {
        this.erreur.set(`Chauffeur cree, mais ${echecs} piece(s) n'ont pas pu etre envoyee(s).`);
      }
    }
  }

  /** Dossiers deja televerses pour ce chauffeur. */
  chargerDocuments(id: number): void {
    this.documentService.lister({ proprietaireType: 'CHAUFFEUR', proprietaireId: id, size: 50 })
      .subscribe({ next: (page) => this.documents.set(page.content) });
  }

  fichierChoisi(evenement: Event): void {
    const entree = evenement.target as HTMLInputElement;
    this.fichier = entree.files?.[0] ?? null;
  }

  /** Televerse une piece du chauffeur ou de son garant. */
  televerser(): void {
    const id = this.id();
    if (id === null || !this.fichier) {
      this.erreur.set('Choisissez un fichier a televerser.');
      return;
    }
    this.documentService
      .creer(
        {
          proprietaireType: 'CHAUFFEUR',
          proprietaireId: id,
          type: this.typeDocument,
          libelle: this.libelleDocument.trim()
            || TYPES_DOCUMENT.find((t) => t.valeur === this.typeDocument)!.libelle
        },
        this.fichier
      )
      .subscribe({
        next: () => {
          this.succes.set('Document televerse.');
          this.libelleDocument = '';
          this.fichier = null;
          this.televersementVisible.set(false);
          this.chargerDocuments(id);
        },
        error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Televersement impossible.')
      });
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

  /** Derniere piece enregistree d'un type donne, pour l'afficher dans sa zone de depot. */
  documentDe(type: TypeDocument): DocumentParc | null {
    return this.documents().find((document) => document.type === type) ?? null;
  }

  choisirPiece(type: TypeDocument, fichier: File | null): void {
    this.piecesChoisies.update((pieces) => ({ ...pieces, [type]: fichier }));
  }

  /**
   * Televerse les pieces deposees. Une piece deja enregistree pour le meme type
   * est remplacee plutot que dupliquee. Les numeros et dates saisis dans la
   * fiche sont reportes sur le document : l'echeance du permis sera surveillee.
   * Renvoie le nombre d'envois en echec.
   */
  private envoyerPieces(chauffeurId: number, fiche: ChauffeurRequete): Observable<number> {
    const envois = Object.entries(this.piecesChoisies())
      .filter((entree): entree is [TypeDocument, File] => entree[1] instanceof File)
      .map(([type, fichier]) => {
        const existant = this.documentDe(type);
        const requete = this.requetePiece(chauffeurId, type, fiche, existant);
        const envoi = existant
          ? this.documentService.modifier(existant.id, requete, fichier)
          : this.documentService.creer(requete, fichier);
        return envoi.pipe(map(() => 0), catchError(() => of(1)));
      });
    if (envois.length === 0) {
      return of(0);
    }
    return forkJoin(envois).pipe(map((echecs) => echecs.reduce((a, b) => a + b, 0)));
  }

  private requetePiece(chauffeurId: number, type: TypeDocument, fiche: ChauffeurRequete,
                       existant: DocumentParc | null): DocumentRequete {
    const garant = type.startsWith('GARANT_');
    const titulaire = garant ? fiche.garantNom : `${fiche.prenom} ${fiche.nom}`;
    const typeLibelle = TYPES_DOCUMENT.find((t) => t.valeur === type)!.libelle;
    const requete: DocumentRequete = {
      proprietaireType: 'CHAUFFEUR',
      proprietaireId: chauffeurId,
      type,
      libelle: titulaire?.trim() ? `${typeLibelle} — ${titulaire.trim()}` : typeLibelle,
      numero: existant?.numero,
      emetteur: existant?.emetteur,
      dateEmission: existant?.dateEmission ?? null,
      dateExpiration: existant?.dateExpiration ?? null,
      observations: existant?.observations
    };
    if (type === 'PERMIS_CONDUIRE') {
      requete.numero = fiche.numeroPermis || requete.numero;
      requete.dateEmission = fiche.permisObtention || requete.dateEmission;
      requete.dateExpiration = fiche.permisExpiration || requete.dateExpiration;
    }
    if (type === 'GARANT_PIECE_IDENTITE') {
      requete.numero = fiche.garantPieceIdentite || requete.numero;
    }
    return requete;
  }

  /** Vrai si le document concerne le garant plutot que le chauffeur. */
  estDocumentGarant(document: DocumentParc): boolean {
    return document.type.startsWith('GARANT_');
  }

  soumettre(): void {
    if (this.formulaire.invalid || this.enregistrement()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.enregistrement.set(true);
    this.erreur.set(null);
    this.succes.set(null);

    const valeur = this.formulaire.getRawValue() as unknown as ChauffeurRequete;
    const id = this.id();
    const requete = id === null ? this.service.creer(valeur) : this.service.modifier(id, valeur);

    requete.subscribe({
      // La fiche d'abord : les pieces se rattachent a un chauffeur existant.
      next: (chauffeur) => this.envoyerPieces(chauffeur.id, valeur).subscribe((echecs) => {
        this.enregistrement.set(false);
        this.piecesChoisies.set({});
        if (echecs > 0) {
          this.erreur.set(`Fiche enregistree, mais ${echecs} piece(s) n'ont pas pu etre envoyee(s).`);
        }
        // En fenetre, un envoi en echec renvoie sur la fiche pour le reprendre.
        if (id === null && this.enModale() && echecs === 0) {
          this.cree.emit(chauffeur.id);
        } else if (id === null) {
          // Un echec d'envoi doit rester visible sur la fiche qui s'ouvre.
          void this.router.navigate(['/chauffeurs', chauffeur.id], { state: { echecsPieces: echecs } });
        } else {
          this.chargerDocuments(id);
          if (echecs === 0) {
            this.succes.set('Fiche enregistree.');
          }
        }
      }),
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
