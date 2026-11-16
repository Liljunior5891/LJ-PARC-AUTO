package tg.parcauto.api.pointdevente.associe;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Enregistre les parts des mois qui viennent de se terminer, dans une
 * transaction a part : la lecture qui les decouvre peut etre en lecture seule.
 * Deux lectures simultanees se heurtent a la contrainte d'unicite (entreprise,
 * mois) ; la perdante relit simplement ce qui a ete fige.
 */
@Component
@RequiredArgsConstructor
public class ClotureParts {

    private final ClotureAssociesRepository clotures;
    private final PartMensuelleRepository parts;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void figer(List<ClotureAssocies> mois, List<PartMensuelle> lignes) {
        clotures.saveAllAndFlush(mois);
        parts.saveAllAndFlush(lignes);
    }
}
