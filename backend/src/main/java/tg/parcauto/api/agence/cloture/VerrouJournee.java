package tg.parcauto.api.agence.cloture;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Verrou des journees cloturees : une journee dont la cloture est validee ne
 * recoit plus aucune ecriture, et ses operations ne s'annulent plus, tant
 * qu'elle n'a pas ete rouverte (autorisation explicite et tracee).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerrouJournee {

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClotureAgenceRepository clotures;

    public boolean verrouillee(Long agenceId, LocalDate journee) {
        return clotures.existsByAgenceIdAndJourneeAndStatut(agenceId, journee, ClotureAgence.Statut.VALIDEE);
    }

    /** Refuse l'action si la journee est cloturee pour l'agence. */
    public void exigerOuverte(Long agenceId, LocalDate journee, String action) {
        if (verrouillee(agenceId, journee)) {
            throw new BusinessException("La journee du " + journee.format(JJ_MM_AAAA) + " est cloturee : " + action
                    + " impossible sans reouverture autorisee de la cloture");
        }
    }
}
