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

    public record Reglages(boolean associesActif, boolean circuitCaisseActif, BigDecimal ecartTolere,
                           boolean validationDepensesCaisse,
                           /** Minutes sans geste avant le verrouillage de l'ecran de caisse. */
                           int minutesVerrouCaisse) {
    }

    public record Requete(@NotNull Boolean associesActif, @NotNull Boolean circuitCaisseActif,
                          @NotNull @PositiveOrZero BigDecimal ecartTolere,
                          /** Absent : inchange. */
                          Boolean validationDepensesCaisse,
                          /** Absent : inchange. */
                          @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(120) Integer minutesVerrouCaisse) {
    }

    @Transactional(readOnly = true)
    public Reglages lire() {
        return repository.findFirstByOrderByIdAsc()
                .map(r -> new Reglages(r.isAssociesActif(), r.isCircuitCaisseActif(), r.getEcartTolere(),
                        r.isValidationDepensesCaisse(), r.getMinutesVerrouCaisse()))
                .orElse(new Reglages(false, false, BigDecimal.ZERO, false, 5));
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
        if (r.validationDepensesCaisse() != null) {
            reglage.setValidationDepensesCaisse(r.validationDepensesCaisse());
        }
        if (r.minutesVerrouCaisse() != null) {
            reglage.setMinutesVerrouCaisse(r.minutesVerrouCaisse());
        }
        repository.save(reglage);
        return lire();
    }

    /** Les depenses payees par la caisse passent par le gerant puis l'administrateur. */
    @Transactional(readOnly = true)
    public boolean validationDepensesCaisse() {
        return lire().validationDepensesCaisse();
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
