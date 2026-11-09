package tg.parcauto.api.pointdevente.caisse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.UtilisateurPrincipal;

/**
 * Validation des depenses payees par la caisse (reglage du point de vente) :
 * le gerant vise, puis l'administrateur approuve. Une depense saisie par le
 * gerant vaut premiere validation ; celle de l'administrateur passe directement.
 * Tant qu'elle attend, rien ne sort du tiroir : la sortie est passee a
 * l'approbation, sur la session d'origine si elle est encore ouverte, sinon sur
 * la session ouverte de la meme caisse.
 */
@Component
@RequiredArgsConstructor
public class CircuitDepensesCaisse {

    public enum Etape { DIRECTE, GERANT, ADMIN }

    private final ReglagePdvService reglages;
    private final SessionCaisseRepository sessions;

    /** Etape par laquelle commence une depense de caisse saisie par cet utilisateur. */
    public Etape etapeInitiale(UtilisateurPrincipal auteur) {
        if (!reglages.validationDepensesCaisse() || administrateur(auteur)) {
            return Etape.DIRECTE;
        }
        return gerant(auteur) ? Etape.ADMIN : Etape.GERANT;
    }

    /** Session sur laquelle passer la sortie approuvee. */
    public SessionCaisse sessionPourSortie(SessionCaisse origine) {
        if (origine.ouverte()) {
            return origine;
        }
        return sessions.findByCaisseIdAndStatut(origine.getCaisse().getId(), SessionCaisse.Statut.OUVERTE)
                .orElseThrow(() -> new BusinessException("La caisse " + origine.getCaisse().getNom()
                        + " est fermee : ouvrez-la pour faire sortir les especes, puis approuvez"));
    }

    /** Le visa du gerant : gerant ou administrateur. */
    public static void exigerVisa(UtilisateurPrincipal u) {
        if (!administrateur(u) && !gerant(u)) {
            throw new org.springframework.security.access.AccessDeniedException("La premiere validation revient au gerant");
        }
    }

    public static void exigerAdministrateur(UtilisateurPrincipal u) {
        if (!administrateur(u)) {
            throw new org.springframework.security.access.AccessDeniedException("La validation finale revient a l'administrateur");
        }
    }

    public static boolean administrateur(UtilisateurPrincipal u) {
        return a(u, Role.ADMIN);
    }

    public static boolean gerant(UtilisateurPrincipal u) {
        return a(u, Role.GERANT);
    }

    private static boolean a(UtilisateurPrincipal u, Role role) {
        return u.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals(role.authority()));
    }
}
