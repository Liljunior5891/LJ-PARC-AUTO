import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ChauffeurRequete,
  DocumentParc,
  STATUTS_CHAUFFEUR,
  TYPES_DOCUMENT,
  TypeDocument
} from '../../core/models/models';
import { DocumentService } from '../../core/services/document.service';
import { OptionSelect, SelectRecherche } from '../../shared/select-recherche';
import { ChauffeurService } from '../../core/services/chauffeur.service';

@Component({
  selector: 'app-chauffeur-fiche',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, SelectRecherche],
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
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Dossiers du chauffeur et de son garant. */
  readonly documents = signal<DocumentParc[]>([]);
  readonly televersementVisible = signal(false);

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
      next: (chauffeur) => {
        this.enregistrement.set(false);
        if (id === null) {
          void this.router.navigate(['/chauffeurs', chauffeur.id]);
        } else {
          this.succes.set('Fiche enregistree.');
        }
      },
      error: (erreur) => {
        this.enregistrement.set(false);
        this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }
}
