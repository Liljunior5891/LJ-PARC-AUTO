package tg.parcauto.api.agence;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/**
 * Agences que l'utilisateur voit : toutes pour l'administrateur (gestion
 * centralisee), seulement celles ou il est rattache pour le superviseur et le
 * caissier.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerimetreAgence {

    private final AgenceRepository agences;

    public List<Agence> visibles() {
        List<Agence> toutes = agences.findAllByOrderByNomAsc();
        if (DroitsAgence.estAdministrateur()) {
            return toutes;
        }
        Long moi = utilisateurId();
        return toutes.stream().filter(a -> a.getUtilisateurIds().contains(moi)).toList();
    }

    /** L'agence demandee, si l'utilisateur y a acces. */
    public Agence exiger(Long agenceId) {
        Agence agence = agences.findById(agenceId).orElseThrow(() -> ResourceNotFoundException.of("Agence", agenceId));
        if (!DroitsAgence.estAdministrateur() && !agence.getUtilisateurIds().contains(utilisateurId())) {
            throw new AccessDeniedException("Vous n'etes pas rattache a l'agence " + agence.getNom());
        }
        return agence;
    }

    /** Identifiant de l'utilisateur connecte. */
    public Long utilisateurCourant() {
        return utilisateurId();
    }

    private static Long utilisateurId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal p ? p.getId() : null;
    }
}
