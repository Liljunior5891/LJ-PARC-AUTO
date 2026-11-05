import { InjectionToken, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ClientParc, ClientRequete, TypeClient } from '../core/models/exploitation';
import { ClientPdv, ClientPdvRequete, TypeClientPdv } from '../core/models/materiaux';
import { DocumentParc, DocumentRequete } from '../core/models/models';
import { ClientService } from '../core/services/client.service';
import { DocumentService } from '../core/services/document.service';
import { MateriauxService } from '../core/services/materiaux.service';

/** Types proposes par la fiche : ceux du parc, plus l'artisan du point de vente. */
export type TypeFiche = TypeClient | 'ARTISAN';

/** Fiche telle que le formulaire la connait, quelle que soit sa provenance. */
export type ClientFormulaire = Omit<ClientParc, 'type'> & { type: TypeFiche };
export type RequeteFormulaire = Omit<Partial<ClientRequete>, 'type'> & { type?: TypeFiche };
export type PieceRequete = Omit<DocumentRequete, 'proprietaireType' | 'proprietaireId'>;

/**
 * Ou le formulaire client lit et enregistre ses fiches. Le formulaire est le
 * meme partout ; le parc et le point de vente gardent chacun leurs clients,
 * car une entreprise peut n'avoir qu'un seul de ces modules.
 */
export interface SourceClients {
  /** Tuiles de type, la plus courante en premier. */
  types: { valeur: TypeFiche; libelle: string }[];
  /** Le code se genere seul : le champ n'est pas propose. */
  codeAutomatique?: boolean;
  /** Ce que signifie le champ laisse vide, en infobulle. */
  aidePlafond?: string;
  aideDelai?: string;
  parId(id: number): Observable<ClientFormulaire>;
  parTelephone(telephone: string): Observable<ClientFormulaire[]>;
  creer(requete: RequeteFormulaire): Observable<ClientFormulaire>;
  modifier(id: number, requete: RequeteFormulaire): Observable<ClientFormulaire>;
  documents(clientId: number): Observable<DocumentParc[]>;
  ajouterDocument(clientId: number, piece: PieceRequete, fichier: File | null): Observable<unknown>;
  telechargerDocument(id: number): Observable<Blob>;
}

/** Par defaut, les clients de la gestion de parc. */
export const SOURCE_CLIENTS = new InjectionToken<SourceClients>('SOURCE_CLIENTS', {
  providedIn: 'root',
  factory: () => {
    const clients = inject(ClientService);
    const pieces = inject(DocumentService);
    return {
      types: [
        { valeur: 'PARTICULIER', libelle: 'Particulier' },
        { valeur: 'ENTREPRISE', libelle: 'Entreprise' },
        { valeur: 'ADMINISTRATION', libelle: 'Administration' },
        { valeur: 'ONG', libelle: 'ONG / organisation internationale' }
      ],
      parId: (id) => clients.parId(id),
      parTelephone: (telephone) => clients.parTelephone(telephone),
      creer: (r) => clients.creer(r as Partial<ClientRequete>),
      modifier: (id, r) => clients.modifier(id, r as Partial<ClientRequete>),
      documents: (id) =>
        pieces.lister({ proprietaireType: 'CLIENT', proprietaireId: id, size: 50 }).pipe(map((p) => p.content)),
      ajouterDocument: (id, piece, fichier) =>
        pieces.creer({ ...piece, proprietaireType: 'CLIENT', proprietaireId: id }, fichier),
      telechargerDocument: (id) => pieces.telecharger(id)
    } satisfies SourceClients;
  }
});

/** Une personne se decrit par son nom et ses prenoms, les autres par une raison sociale. */
const estPersonne = (type: TypeClientPdv) => type === 'PARTICULIER' || type === 'ARTISAN';

/**
 * Fiche du point de vente vue par le formulaire. Une fiche anterieure ne porte
 * que le nom complet : premier mot pour le nom de famille, la suite pour les
 * prenoms, a corriger au besoin avant d'enregistrer.
 */
function versFormulaire(c: ClientPdv): ClientFormulaire {
  const mots = c.nom.trim().split(/\s+/);
  const ancienne = estPersonne(c.type) && !c.nomFamille && !c.prenoms;
  return {
    id: c.id,
    code: c.code,
    type: c.type,
    typeLibelle: c.typeLibelle,
    actif: c.actif,
    raisonSociale: c.nom,
    nom: ancienne ? mots[0] : (c.nomFamille ?? ''),
    prenoms: ancienne ? mots.slice(1).join(' ') : (c.prenoms ?? ''),
    dateNaissance: c.dateNaissance ?? '',
    profession: c.profession ?? '',
    formeJuridique: c.formeJuridique ?? '',
    rccm: c.rccm ?? '',
    identifiantFiscal: c.nif ?? '',
    secteurActivite: c.secteurActivite ?? '',
    organismeTutelle: c.organismeTutelle ?? '',
    numeroAgrement: c.numeroAgrement ?? '',
    telephone: c.telephone ?? '',
    email: c.email ?? '',
    adresse: c.adresse ?? '',
    ville: c.ville ?? '',
    pays: c.pays ?? '',
    personneContact: c.personneContact ?? '',
    fonctionContact: c.fonctionContact ?? '',
    telephoneContact: c.telephoneContact ?? '',
    conditionsCommerciales: c.conditionsCommerciales ?? '',
    observations: c.note ?? '',
    delaiPaiementJours: c.delaiPaiementJours ?? undefined,
    plafondCredit: c.plafondCredit ?? undefined
  };
}

function versRequetePdv(r: RequeteFormulaire): ClientPdvRequete {
  return {
    code: r.code,
    type: r.type as TypeClientPdv,
    nom: r.raisonSociale,
    nomFamille: r.nom,
    prenoms: r.prenoms,
    dateNaissance: r.dateNaissance,
    profession: r.profession,
    formeJuridique: r.formeJuridique,
    rccm: r.rccm,
    nif: r.identifiantFiscal,
    secteurActivite: r.secteurActivite,
    organismeTutelle: r.organismeTutelle,
    numeroAgrement: r.numeroAgrement,
    telephone: r.telephone,
    email: r.email,
    adresse: r.adresse,
    ville: r.ville,
    pays: r.pays,
    personneContact: r.personneContact,
    fonctionContact: r.fonctionContact,
    telephoneContact: r.telephoneContact,
    conditionsCommerciales: r.conditionsCommerciales,
    note: r.observations,
    plafondCredit: r.plafondCredit ?? null,
    delaiPaiementJours: r.delaiPaiementJours ?? null,
    actif: r.actif
  };
}

/** Les clients du point de vente, a fournir au-dessus du formulaire. */
export function sourceClientsPdv(): SourceClients {
  const service = inject(MateriauxService);
  return {
    types: [
      { valeur: 'PARTICULIER', libelle: 'Particulier' },
      { valeur: 'ARTISAN', libelle: 'Artisan / macon' },
      { valeur: 'ENTREPRISE', libelle: 'Entreprise' },
      { valeur: 'ADMINISTRATION', libelle: 'Administration' },
      { valeur: 'ONG', libelle: 'ONG / Association' }
    ],
    codeAutomatique: true,
    aidePlafond: 'Vide : plafond par defaut des reglages. 0 : aucun credit.',
    aideDelai: 'Vide : delai par defaut des reglages.',
    parId: (id) => service.client(id).pipe(map(versFormulaire)),
    parTelephone: (telephone) => service.clientsParTelephone(telephone).pipe(map((l) => l.map(versFormulaire))),
    creer: (r) => service.creerClient(versRequetePdv(r)).pipe(map(versFormulaire)),
    modifier: (id, r) => service.modifierClient(id, versRequetePdv(r)).pipe(map(versFormulaire)),
    documents: (id) => service.documentsClient(id).pipe(map((p) => p.content)),
    ajouterDocument: (id, piece, fichier) => service.ajouterDocumentClient(id, piece, fichier),
    telechargerDocument: (id) => service.telechargerDocumentClient(id)
  };
}
