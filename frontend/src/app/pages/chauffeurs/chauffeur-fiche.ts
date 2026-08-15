import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ChauffeurRequete, STATUTS_CHAUFFEUR } from '../../core/models/models';
import { ChauffeurService } from '../../core/services/chauffeur.service';

@Component({
  selector: 'app-chauffeur-fiche',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './chauffeur-fiche.html'
})
export class ChauffeurFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ChauffeurService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly statuts = STATUTS_CHAUFFEUR;
  readonly id = signal<number | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

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
    }
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
