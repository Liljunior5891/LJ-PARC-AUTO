package tg.parcauto.api.pointdevente.caisse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ce que chaque caisse voit : ses propres operations (ventes, commandes, reste a
 * livrer). Le gerant et l'administrateur voient toutes les caisses ; les clients
 * et les devis restent communs a toutes.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerimetreCaisse {

    /** Identifiant qui ne correspond a aucune caisse : une liste {@code in} n'est jamais vide. */
    private static final Long AUCUNE = -1L;

    private final CaisseRepository caisses;
    private final SessionCaisseRepository sessions;

    /**
     * Caisses visibles par l'utilisateur ; {@code null} : pas de limite par caisse.
     * Seul le personnel de caisse (caissier, serveur) est limite : le gerant voit
     * tout, et le magasinier est deja limite par ses lieux.
     */
    public Set<Long> caisses(UtilisateurPrincipal utilisateur) {
        if (utilisateur == null || PersonnelService.gestionnaire(utilisateur) || !personnelDeCaisse(utilisateur)) {
            return null;
        }
        Set<Long> ids = caisses.findAllByOrderByNomAsc().stream()
                .filter(c -> c.getUtilisateurIds().contains(utilisateur.getId()))
                .map(Caisse::getId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            ids.add(AUCUNE);
        }
        return ids;
    }

    /** Refuse une session tenue sur une caisse qui n'est pas celle de l'utilisateur. */
    public void exigerSession(Long sessionId, UtilisateurPrincipal utilisateur) {
        Set<Long> visibles = caisses(utilisateur);
        if (visibles == null || sessionId == null) {
            return;
        }
        sessions.findById(sessionId).ifPresent(s -> exigerCaisse(s.getCaisse().getId(), visibles));
    }

    /** Refuse une operation (vente, commande) enregistree sur une autre caisse. */
    public void exiger(SessionCaisse session, UtilisateurPrincipal utilisateur) {
        Set<Long> visibles = caisses(utilisateur);
        if (visibles == null || session == null) {
            return;
        }
        exigerCaisse(session.getCaisse().getId(), visibles);
    }

    private static boolean personnelDeCaisse(UtilisateurPrincipal utilisateur) {
        return utilisateur.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals(tg.parcauto.api.security.Role.CAISSIER.authority())
                        || a.getAuthority().equals(tg.parcauto.api.security.Role.SERVEUR.authority()));
    }

    private static void exigerCaisse(Long caisseId, Set<Long> visibles) {
        if (!visibles.contains(caisseId)) {
            throw new BusinessException("Cette operation a ete enregistree sur une autre caisse");
        }
    }
}
