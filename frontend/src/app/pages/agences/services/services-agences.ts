import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompteVirtuel, NatureOperation, ReseauFinancier, ServiceFinancier, TypeOperation } from '../../../core/models/agences';
import { AgencesService } from '../../../core/services/agences.service';
import { Modale } from '../../../shared/modale';

type Edition =
  | { genre: 'service'; id: number | null }
  | { genre: 'reseau'; id: number | null; service: ServiceFinancier }
  | { genre: 'type'; id: number | null; service: ServiceFinancier };

/**
 * Catalogue des services proposes au guichet : services ranges par categorie,
 * reseaux d'un meme service (Western Union, MoneyGram...) et types d'operation
 * avec leur regle de mouvement. Tout se change ici, sans developpement ; rien
 * ne se supprime, un element desactive disparait seulement de la saisie.
 */
@Component({
  selector: 'app-services-agences',
  imports: [FormsModule, Modale],
  templateUrl: './services-agences.html',
  styleUrl: './services-agences.scss'
})
export class ServicesAgences implements OnInit {
  readonly agencesService = inject(AgencesService);

  readonly services = signal<ServiceFinancier[]>([]);
  readonly categories = signal<string[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly succes = signal<string | null>(null);
  readonly erreurFormulaire = signal<string | null>(null);
  readonly modifiable = signal(false);

  /** Services regroupes par categorie, dans l'ordre du catalogue. */
  readonly parCategorie = computed(() => {
    const groupes = new Map<string, ServiceFinancier[]>();
    for (const s of this.services()) {
      groupes.set(s.categorie, [...(groupes.get(s.categorie) ?? []), s]);
    }
    return [...groupes.entries()].map(([categorie, services]) => ({ categorie, services }));
  });

  readonly edition = signal<Edition | null>(null);
  formService = { nom: '', categorie: '', actif: true };
  formReseau = { nom: '', actif: true };
  formType: { libelle: string; effetCash: number; effetVirtuel: number; compteVirtuel: CompteVirtuel; nature: NatureOperation; actif: boolean } =
    { libelle: '', effetCash: 1, effetVirtuel: -1, compteVirtuel: 'FLOAT', nature: 'TRANSIT', actif: true };

  ngOnInit(): void {
    this.agencesService.chargerMoi().subscribe({ next: (m) => this.modifiable.set(m.permissions.includes('PARAMETRAGE')) });
    this.charger();
  }

  charger(): void {
    this.agencesService.catalogue().subscribe({
      next: (s) => {
        this.services.set(s);
        this.chargement.set(false);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(this.message(e, 'Chargement du catalogue impossible.'));
      }
    });
    this.agencesService.categories().subscribe({ next: (c) => this.categories.set(c) });
  }

  // ------------------------------------------------------------- ouverture
  nouveauService(): void {
    this.formService = { nom: '', categorie: '', actif: true };
    this.ouvrir({ genre: 'service', id: null });
  }

  modifierService(s: ServiceFinancier): void {
    this.formService = { nom: s.nom, categorie: s.categorie, actif: s.actif };
    this.ouvrir({ genre: 'service', id: s.id });
  }

  nouveauReseau(s: ServiceFinancier): void {
    this.formReseau = { nom: '', actif: true };
    this.ouvrir({ genre: 'reseau', id: null, service: s });
  }

  modifierReseau(s: ServiceFinancier, r: ReseauFinancier): void {
    this.formReseau = { nom: r.nom, actif: r.actif };
    this.ouvrir({ genre: 'reseau', id: r.id, service: s });
  }

  nouveauType(s: ServiceFinancier): void {
    this.formType = { libelle: '', effetCash: 1, effetVirtuel: -1, compteVirtuel: 'FLOAT', nature: 'TRANSIT', actif: true };
    this.ouvrir({ genre: 'type', id: null, service: s });
  }

  modifierType(s: ServiceFinancier, t: TypeOperation): void {
    this.formType = {
      libelle: t.libelle, effetCash: t.effetCash, effetVirtuel: t.effetVirtuel, compteVirtuel: t.compteVirtuel,
      nature: t.nature, actif: t.actif
    };
    this.ouvrir({ genre: 'type', id: t.id, service: s });
  }

  private ouvrir(e: Edition): void {
    this.erreurFormulaire.set(null);
    this.succes.set(null);
    this.edition.set(e);
  }

  /** Sans compte electronique, le virtuel ne bouge pas ; avec, il doit bouger. */
  changerCompte(compte: CompteVirtuel): void {
    this.formType.compteVirtuel = compte;
    if (compte === 'AUCUN') {
      this.formType.effetVirtuel = 0;
    } else if (this.formType.effetVirtuel === 0) {
      this.formType.effetVirtuel = this.formType.effetCash > 0 ? -1 : 1;
    }
  }

  /** Exemple chiffre de la regle, pour 100 000 F. */
  exemple(): string {
    const f = this.formType;
    const signe = (e: number) => (e > 0 ? '+100 000' : e < 0 ? '−100 000' : 'inchange');
    const parties = [`Cash ${signe(f.effetCash)}`];
    if (f.compteVirtuel !== 'AUCUN') {
      parties.push(`${f.compteVirtuel === 'FLOAT' ? 'float' : 'compte de recharge'} ${signe(f.effetVirtuel)}`);
    }
    return parties.join(', ');
  }

  titre(e: Edition): string {
    switch (e.genre) {
      case 'service':
        return e.id ? 'Modifier le service' : 'Nouveau service';
      case 'reseau':
        return (e.id ? 'Modifier un reseau de ' : 'Nouveau reseau pour ') + e.service.nom;
      case 'type':
        return (e.id ? 'Modifier une operation de ' : 'Nouvelle operation pour ') + e.service.nom;
    }
  }

  enregistrer(): void {
    const e = this.edition();
    if (!e) {
      return;
    }
    const appel = e.genre === 'service'
      ? this.agencesService.enregistrerService(e.id, { ...this.formService, nom: this.formService.nom.trim(), categorie: this.formService.categorie.trim() })
      : e.genre === 'reseau'
        ? this.agencesService.enregistrerReseau(e.id, { serviceId: e.service.id, ...this.formReseau, nom: this.formReseau.nom.trim() })
        : this.agencesService.enregistrerType(e.id, { serviceId: e.service.id, ...this.formType, libelle: this.formType.libelle.trim() });
    appel.subscribe({
      next: (s) => {
        this.edition.set(null);
        this.succes.set(`${s.nom} enregistre.`);
        this.charger();
      },
      error: (err) => this.erreurFormulaire.set(this.message(err, 'Enregistrement impossible.'))
    });
  }

  sens(effet: number): string {
    return effet > 0 ? '+' : effet < 0 ? '−' : '=';
  }

  private message(reponse: unknown, defaut: string): string {
    return (reponse as { error?: { message?: string } })?.error?.message ?? defaut;
  }
}
