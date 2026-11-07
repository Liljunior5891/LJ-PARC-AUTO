package tg.parcauto.api.pointdevente.reglage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;

import java.math.BigDecimal;

/**
 * Options du point de vente : lues par tous les ecrans du module, modifiees par
 * l'administrateur seul. Sans ligne enregistree, tout est desactive.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReglagePdvService {

    private final ReglagePdvRepository repository;
    /** Fourni par la tresorerie, qui depend elle-meme des reglages : resolu a l'usage. */
    private final org.springframework.beans.factory.ObjectProvider<ControleOptions> controle;

    public record Reglages(boolean associesActif, boolean circuitCaisseActif, BigDecimal ecartTolere) {
    }

    public record Requete(@NotNull Boolean associesActif, @NotNull Boolean circuitCaisseActif,
                          @NotNull @PositiveOrZero BigDecimal ecartTolere) {
    }

    @Transactional(readOnly = true)
    public Reglages lire() {
        return repository.findFirstByOrderByIdAsc()
                .map(r -> new Reglages(r.isAssociesActif(), r.isCircuitCaisseActif(), r.getEcartTolere()))
                .orElse(new Reglages(false, false, BigDecimal.ZERO));
    }

    public Reglages modifier(Requete r) {
        ReglagePdv reglage = repository.findFirstByOrderByIdAsc().orElseGet(ReglagePdv::new);
        if (reglage.isCircuitCaisseActif() && !r.circuitCaisseActif()) {
            ControleOptions c = controle.getIfAvailable();
            String blocage = c == null ? null : c.blocageArretCircuit();
            if (blocage != null) {
                throw new BusinessException("Le circuit de caisse ne peut pas etre desactive : " + blocage);
            }
        }
        reglage.setAssociesActif(r.associesActif());
        reglage.setCircuitCaisseActif(r.circuitCaisseActif());
        reglage.setEcartTolere(r.ecartTolere());
        repository.save(reglage);
        return lire();
    }

    /** Refuse une action d'une option desactivee. */
    public void exigerAssocies() {
        if (!lire().associesActif()) {
            throw new BusinessException("La gestion des benefices des associes n'est pas activee dans les reglages");
        }
    }

    public void exigerCircuit() {
        if (!lire().circuitCaisseActif()) {
            throw new BusinessException("Le circuit des remises de caisse n'est pas active dans les reglages");
        }
    }

    /** Ce qui empeche d'arreter une option en cours d'usage ; fourni par le module de tresorerie. */
    public interface ControleOptions {
        /** Raison d'empecher l'arret du circuit (remises ou degagements en attente, coffre non vide), ou nul. */
        String blocageArretCircuit();
    }
}
