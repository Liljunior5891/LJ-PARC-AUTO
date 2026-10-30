import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { SocieteRequete } from '../../core/models/parametrage';
import { SocieteService } from '../../core/services/societe.service';
import { VALIDATION_FORMULAIRE } from '../../shared/validation-formulaire';

/**
 * Parametrage de l'entreprise (chapitre 39).
 * <p>
 * Ce qui est saisi ici sert partout : entete des factures et des rapports,
 * nom de la barre laterale et logo de la page de connexion. C'est le seul
 * endroit ou ces informations se modifient.
 */
@Component({
  selector: 'app-parametrage',
  imports: [ReactiveFormsModule, VALIDATION_FORMULAIRE],
  templateUrl: './parametrage.html',
  styleUrl: './parametrage.scss'
})
export class Parametrage implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(SocieteService);

  readonly logo = signal<string | null>(null);
  readonly chargement = signal(true);
  readonly enregistrement = signal(false);
  readonly televersement = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);

  readonly formulaire = this.fb.nonNullable.group({
    nom: ['', [Validators.required]],
    sigle: [''],
    formeJuridique: [''],
    adresse: [''],
    ville: [''],
    pays: [''],
    telephone: [''],
    email: [''],
    siteWeb: [''],
    nif: [''],
    rccm: [''],
    banque: [''],
    compteBancaire: [''],
    mentionsFacture: [''],
    tauxTva: [18, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  ngOnInit(): void {
    this.service.lire().subscribe({
      next: (societe) => {
        this.formulaire.patchValue({
          nom: societe.nom ?? '',
          sigle: societe.sigle ?? '',
          formeJuridique: societe.formeJuridique ?? '',
          adresse: societe.adresse ?? '',
          ville: societe.ville ?? '',
          pays: societe.pays ?? '',
          telephone: societe.telephone ?? '',
          email: societe.email ?? '',
          siteWeb: societe.siteWeb ?? '',
          nif: societe.nif ?? '',
          rccm: societe.rccm ?? '',
          banque: societe.banque ?? '',
          compteBancaire: societe.compteBancaire ?? '',
          mentionsFacture: societe.mentionsFacture ?? '',
          tauxTva: societe.tauxTva ?? 18
        });
        this.logo.set(societe.logo ?? null);
        this.chargement.set(false);
      },
      error: () => {
        this.chargement.set(false);
        this.erreur.set('Chargement du parametrage impossible.');
      }
    });
  }

  enregistrer(): void {
    if (this.formulaire.invalid || this.enregistrement()) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.messages();
    this.enregistrement.set(true);
    this.service.enregistrer(this.formulaire.getRawValue() as SocieteRequete).subscribe({
      next: () => {
        this.enregistrement.set(false);
        this.succes.set('Parametrage enregistre. Il s\'applique aux prochains documents edites.');
      },
      error: (reponse) => {
        this.enregistrement.set(false);
        this.erreur.set(reponse.error?.message ?? 'Enregistrement impossible.');
      }
    });
  }

  /** Le logo est envoye des sa selection : il n'y a rien d'autre a decider. */
  choisirLogo(evenement: Event): void {
    const entree = evenement.target as HTMLInputElement;
    const fichier = entree.files?.[0];
    if (!fichier) {
      return;
    }
    this.messages();
    this.televersement.set(true);
    this.service.televerserLogo(fichier).subscribe({
      next: (societe) => {
        this.televersement.set(false);
        this.logo.set(societe.logo ?? null);
        this.succes.set('Logo enregistre.');
        entree.value = '';
      },
      error: (reponse) => {
        this.televersement.set(false);
        this.erreur.set(reponse.error?.message ?? 'Envoi du logo impossible.');
        entree.value = '';
      }
    });
  }

  retirerLogo(): void {
    this.messages();
    this.service.supprimerLogo().subscribe({
      next: () => {
        this.logo.set(null);
        this.succes.set('Logo retire.');
      },
      error: () => this.erreur.set('Suppression du logo impossible.')
    });
  }

  private messages(): void {
    this.erreur.set(null);
    this.succes.set(null);
  }
}
