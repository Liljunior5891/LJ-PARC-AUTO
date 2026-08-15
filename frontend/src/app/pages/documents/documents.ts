import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import {
  Camion,
  Chauffeur,
  DocumentParc,
  DocumentRequete,
  Page,
  ProprietaireDocument,
  TYPES_DOCUMENT,
  TypeDocument
} from '../../core/models/models';
import { CamionService } from '../../core/services/camion.service';
import { ChauffeurService } from '../../core/services/chauffeur.service';
import { DocumentService } from '../../core/services/document.service';

@Component({
  selector: 'app-documents',
  imports: [FormsModule, ReactiveFormsModule],
  templateUrl: './documents.html'
})
export class Documents implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DocumentService);
  private readonly camionService = inject(CamionService);
  private readonly chauffeurService = inject(ChauffeurService);
  private readonly auth = inject(AuthService);

  readonly types = TYPES_DOCUMENT;
  readonly page = signal<Page<DocumentParc> | null>(null);
  readonly camions = signal<Camion[]>([]);
  readonly chauffeurs = signal<Chauffeur[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly chargement = signal(false);
  readonly formulaireVisible = signal(false);

  filtreProprietaire: ProprietaireDocument | '' = '';
  filtreType: TypeDocument | '' = '';
  fichier: File | null = null;
  private pageCourante = 0;

  readonly peutGerer = this.auth.peutGererParc();

  readonly formulaire = this.fb.nonNullable.group({
    proprietaireType: ['CAMION' as ProprietaireDocument, [Validators.required]],
    proprietaireId: [null as number | null, [Validators.required]],
    type: ['ASSURANCE' as TypeDocument, [Validators.required]],
    libelle: ['', [Validators.required]],
    numero: [''],
    emetteur: [''],
    dateEmission: [null as string | null],
    dateExpiration: [null as string | null],
    cout: [null as number | null],
    observations: ['']
  });

  ngOnInit(): void {
    this.charger();
    this.camionService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.camions.set(page.content)
    });
    this.chauffeurService.lister({ actif: true, size: 200 }).subscribe({
      next: (page) => this.chauffeurs.set(page.content)
    });
  }

  charger(): void {
    this.chargement.set(true);
    this.service
      .lister({
        proprietaireType: this.filtreProprietaire,
        type: this.filtreType,
        page: this.pageCourante
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.chargement.set(false);
        },
        error: () => {
          this.chargement.set(false);
          this.erreur.set('Chargement des documents impossible.');
        }
      });
  }

  filtrer(): void {
    this.pageCourante = 0;
    this.charger();
  }

  changerPage(delta: number): void {
    const page = this.page();
    if (!page) {
      return;
    }
    const cible = page.number + delta;
    if (cible < 0 || cible >= page.totalPages) {
      return;
    }
    this.pageCourante = cible;
    this.charger();
  }

  basculerFormulaire(): void {
    this.formulaireVisible.update((visible) => !visible);
    this.erreur.set(null);
    this.succes.set(null);
  }

  fichierChoisi(evenement: Event): void {
    const entree = evenement.target as HTMLInputElement;
    this.fichier = entree.files?.[0] ?? null;
  }

  enregistrer(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.erreur.set(null);
    const valeur = this.formulaire.getRawValue();
    const requete: DocumentRequete = {
      proprietaireType: valeur.proprietaireType,
      proprietaireId: valeur.proprietaireId!,
      type: valeur.type,
      libelle: valeur.libelle,
      numero: valeur.numero,
      emetteur: valeur.emetteur,
      dateEmission: valeur.dateEmission || null,
      dateExpiration: valeur.dateExpiration || null,
      cout: valeur.cout,
      observations: valeur.observations
    };

    this.service.creer(requete, this.fichier).subscribe({
      next: () => {
        this.succes.set('Document enregistre.');
        this.formulaire.reset({ proprietaireType: 'CAMION', type: 'ASSURANCE' });
        this.fichier = null;
        this.formulaireVisible.set(false);
        this.charger();
      },
      error: (erreur) => this.erreur.set(erreur.error?.message ?? 'Enregistrement impossible.')
    });
  }

  telecharger(document: DocumentParc): void {
    this.service.telecharger(document.id).subscribe({
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

  archiver(document: DocumentParc): void {
    if (!confirm(`Archiver le document "${document.libelle}" ?`)) {
      return;
    }
    this.service.archiver(document.id).subscribe({
      next: () => this.charger(),
      error: () => this.erreur.set('Archivage impossible.')
    });
  }

  classeEcheance(document: DocumentParc): string {
    if (document.expire) {
      return 'etiquette vendu';
    }
    if (document.joursAvantExpiration !== undefined && document.joursAvantExpiration <= 30) {
      return 'etiquette en_location';
    }
    return 'etiquette disponible';
  }

  libelleEcheance(document: DocumentParc): string {
    if (!document.dateExpiration) {
      return 'Sans echeance';
    }
    if (document.expire) {
      return `Expire depuis ${Math.abs(document.joursAvantExpiration ?? 0)} j`;
    }
    return `${document.joursAvantExpiration} j restants`;
  }
}
