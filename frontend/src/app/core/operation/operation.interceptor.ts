import {
  HttpContextToken,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest
} from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize, from, switchMap, tap, throwError } from 'rxjs';
import { DemandeConfirmation, OperationService } from './operation.service';

/** Requete d'ecriture envoyee sans confirmation ni suivi (rare : usage technique). */
export const SANS_SUIVI = new HttpContextToken<boolean>(() => false);

const ECRITURES = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** Message rendu a l'ecran appelant quand l'utilisateur renonce. */
export const MESSAGE_ANNULATION = "Action annulee : rien n'a ete enregistre.";

/**
 * Toute requete qui modifie des donnees passe par une confirmation, puis par
 * l'anneau de progression jusqu'a 100 %. La connexion en est exclue, de meme
 * que les requetes qui suivent une operation deja confirmee (envois en chaine,
 * comme les pieces jointes d'un chauffeur).
 */
export const operationInterceptor: HttpInterceptorFn = (req, next) => {
  if (!ECRITURES.has(req.method) || req.url.includes('/auth/') || req.context.get(SANS_SUIVI)) {
    return next(req);
  }
  const operation = inject(OperationService);

  const envoyer = () => {
    operation.debuter();
    let erreur: string | null = null;
    return next(req).pipe(
      tap({
        error: (e: HttpErrorResponse) => (erreur = e.error?.message ?? 'Operation refusee par le serveur.')
      }),
      finalize(() => operation.terminer(erreur))
    );
  };

  if (operation.consommerPreConfirmation() || operation.estEnCours()) {
    return envoyer();
  }
  return from(operation.demander(decrire(req))).pipe(
    switchMap((accepte) =>
      accepte
        ? envoyer()
        : throwError(() => new HttpErrorResponse({
            status: 0,
            url: req.url,
            error: { message: MESSAGE_ANNULATION }
          }))
    )
  );
};

// --------------------------------------------------------------- libelles

interface Ressource {
  nom: string;
  /** Article defini : « le », « la », « l' ». */
  le: string;
  /** Article indefini : « un », « une ». */
  un: string;
}

const RESSOURCES: Record<string, Ressource> = {
  camions: { nom: 'vehicule', le: 'le ', un: 'un ' },
  chauffeurs: { nom: 'chauffeur', le: 'le ', un: 'un ' },
  clients: { nom: 'client', le: 'le ', un: 'un ' },
  locations: { nom: 'contrat de location', le: 'le ', un: 'un ' },
  missions: { nom: 'mission', le: 'la ', un: 'une ' },
  recettes: { nom: 'recette', le: 'la ', un: 'une ' },
  depenses: { nom: 'depense', le: 'la ', un: 'une ' },
  factures: { nom: 'facture', le: 'la ', un: 'une ' },
  affectations: { nom: 'affectation', le: "l'", un: 'une ' },
  documents: { nom: 'document', le: 'le ', un: 'un ' },
  livraisons: { nom: 'livraison', le: 'la ', un: 'une ' },
  entretiens: { nom: 'intervention', le: "l'", un: 'une ' },
  pneus: { nom: 'pneu', le: 'le ', un: 'un ' },
  incidents: { nom: 'incident', le: "l'", un: 'un ' },
  utilisateurs: { nom: 'compte', le: 'le ', un: 'un ' },
  zones: { nom: 'zone', le: 'la ', un: 'une ' },
  alertes: { nom: 'alerte', le: "l'", un: 'une ' },
  societe: { nom: 'parametrage de la societe', le: 'le ', un: 'un ' }
};

/** Actions designees par le dernier segment de l'adresse. */
const ACTIONS: Record<string, (r: Ressource) => DemandeConfirmation> = {
  cloture: (r) => ({ titre: `Cloturer ${r.le}${r.nom} ?`, libelleAction: 'Cloturer' }),
  demarrage: (r) => ({ titre: `Demarrer ${r.le}${r.nom} ?`, libelleAction: 'Demarrer' }),
  activation: (r) => ({ titre: r.nom === 'compte' ? 'Changer l\'acces de ce compte ?' : `Activer ${r.le}${r.nom} ?`,
    libelleAction: 'Valider' }),
  annulation: (r) => ({ titre: `Annuler ${r.le}${r.nom} ?`, message: 'Cette action ne peut pas etre defaite.',
    libelleAction: 'Annuler definitivement', danger: true }),
  emission: () => ({ titre: 'Emettre la facture ?', message: 'Une facture emise n\'est plus modifiable.',
    libelleAction: 'Emettre' }),
  paiements: () => ({ titre: 'Enregistrer ce reglement ?', libelleAction: 'Enregistrer' }),
  confirmation: () => ({ titre: 'Valider la preuve de livraison ?', libelleAction: 'Valider' }),
  realisation: () => ({ titre: 'Valider la realisation de l\'intervention ?', libelleAction: 'Valider' }),
  traitement: (r) => ({ titre: `Traiter ${r.le}${r.nom} ?`, libelleAction: 'Traiter' }),
  montage: () => ({ titre: 'Monter ce pneu ?', libelleAction: 'Monter' }),
  depose: () => ({ titre: 'Deposer ce pneu ?', libelleAction: 'Deposer' }),
  kilometrage: () => ({ titre: 'Mettre a jour le kilometrage ?', libelleAction: 'Mettre a jour' }),
  statut: (r) => ({ titre: `Changer le statut ${r.le === "l'" ? 'de l\'' : 'du '}${r.nom} ?`, libelleAction: 'Changer' }),
  'mot-de-passe': () => ({ titre: 'Changer le mot de passe ?', libelleAction: 'Changer' }),
  retards: () => ({ titre: 'Actualiser les retards de paiement ?', libelleAction: 'Actualiser' }),
  'depuis-contrat': () => ({ titre: 'Generer la facture du contrat ?', libelleAction: 'Generer' }),
  'depuis-mission': () => ({ titre: 'Generer la facture de la mission ?', libelleAction: 'Generer' }),
  location: () => ({ titre: 'Enregistrer la recette et son contrat de location ?', libelleAction: 'Enregistrer' })
};

/** Titre et bouton de la confirmation, deduits de la methode et de l'adresse. */
function decrire(req: HttpRequest<unknown>): DemandeConfirmation {
  const segments = new URL(req.url, window.location.origin).pathname
    .split('/')
    .filter((s) => s && s !== 'api');
  const ressource = [...segments].reverse().map((s) => RESSOURCES[s]).find(Boolean)
    ?? { nom: 'element', le: "l'", un: 'un ' };
  const dernier = segments[segments.length - 1] ?? '';

  if (dernier === 'logo') {
    return req.method === 'DELETE'
      ? { titre: 'Retirer le logo de la societe ?', libelleAction: 'Retirer', danger: true }
      : { titre: 'Remplacer le logo de la societe ?', libelleAction: 'Remplacer' };
  }
  const action = ACTIONS[dernier];
  if (action) {
    return action(ressource);
  }
  switch (req.method) {
    case 'POST':
      return {
        titre: `Enregistrer ${ressource.un}${ressource.nom} ?`,
        message: 'Verifiez les informations saisies avant de valider.',
        libelleAction: 'Enregistrer'
      };
    case 'DELETE':
      return {
        titre: `Supprimer ${ressource.le}${ressource.nom} ?`,
        message: 'Cette action retire l\'element des listes en cours.',
        libelleAction: 'Supprimer',
        danger: true
      };
    default:
      return {
        titre: `Enregistrer les modifications ${ressource.le === "l'" ? 'de l\'' : ressource.le === 'la ' ? 'de la ' : 'du '}${ressource.nom} ?`,
        libelleAction: 'Enregistrer'
      };
  }
}
