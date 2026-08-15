import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TYPES_CLIENT } from '../../core/models/exploitation';
import { ClientService } from '../../core/services/client.service';
import { LocationService } from '../../core/services/location.service';
import { Location } from '../../core/models/exploitation';

@Component({
  selector: 'app-client-fiche',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  templateUrl: './client-fiche.html'
})
export class ClientFiche implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly types = TYPES_CLIENT;
  readonly id = signal<number | null>(null);
  readonly enregistrement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  /** Historique des contrats du client (chapitre 10). */
  readonly contrats = signal<Location[]>([]);

  readonly formulaire = this.fb.nonNullable.group({
    code: [''],
    raisonSociale: ['', [Validators.required]],
    type: ['ENTREPRISE'],
    telephone: [''],
    email: ['', [Validators.email]],
    adresse: [''],
    ville: [''],
    pays: ['Togo'],
    personneContact: [''],
    telephoneContact: [''],
    identifiantFiscal: [''],
    conditionsCommerciales: [''],
    delaiPaiementJours: [null as number | null],
    plafondCredit: [null as number | null],
    observations: [''],
    actif: [true]
  });

  get titre(): string {
    return this.id() === null ? 'Nouveau client' : 'Fiche du client';
  }

  ngOnInit(): void {
    const parametre = this.route.snapshot.paramMap.get('id');
    if (parametre && parametre !== 'nouveau') {
      const id = Number(parametre);
      this.id.set(id);
      this.service.parId(id).subscribe({
        next: (client) => this.formulaire.patchValue(client as never),
        error: () => this.erreur.set('Client introuvable.')
      });
      this.locationService.lister({ clientId: id, size: 10 }).subscribe({
        next: (page) => this.contrats.set(page.content)
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

    const valeur = this.formulaire.getRawValue() as never;
    const id = this.id();
    const requete = id === null ? this.service.creer(valeur) : this.service.modifier(id, valeur);

    requete.subscribe({
      next: (client) => {
        this.enregistrement.set(false);
        if (id === null) {
          void this.router.navigate(['/clients', client.id]);
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
