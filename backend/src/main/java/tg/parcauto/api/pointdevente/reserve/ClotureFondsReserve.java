package tg.parcauto.api.pointdevente.reserve;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Enregistre les versements des mois qui viennent de se terminer, dans une
 * transaction a part : la lecture du fonds qui les decouvre peut etre en
 * lecture seule. Deux lectures simultanees se heurtent a la contrainte
 * d'unicite (entreprise, debut) ; la perdante relit simplement ce qui a ete fige.
 */
@Component
@RequiredArgsConstructor
public class ClotureFondsReserve {

    private final MoisFondsReserveRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void figer(List<MoisFondsReserve> mois) {
        repository.saveAllAndFlush(mois);
    }
}
