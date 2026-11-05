package tg.parcauto.api.pointdevente.materiaux;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.GardeVariante;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.math.BigDecimal;

/**
 * Le point de vente ne quitte pas le mode quincaillerie tant que des clients
 * doivent de l'argent : leurs comptes disparaitraient des ecrans avant d'etre
 * soldes. Fiches, devis et conditionnements sont conserves, et reapparaissent
 * si l'entreprise revient a ce mode.
 */
@Component
@RequiredArgsConstructor
public class GardeMateriaux implements GardeVariante {

    private final ClientPdvRepository clientRepository;
    private final tg.parcauto.api.pointdevente.lieu.LieuStockRepository lieux;

    @Override
    public ModuleApplicatif module() {
        return ModuleApplicatif.POINT_DE_VENTE;
    }

    @Override
    public void verifierChangement(String actuelle, String nouvelle) {
        if (ReglageMateriauxService.MATERIAUX.equals(actuelle) && !ReglageMateriauxService.MATERIAUX.equals(nouvelle)) {
            BigDecimal du = clientRepository.tousLesEncours().stream()
                    .map(l -> (BigDecimal) l[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (du.signum() > 0) {
                throw new BusinessException("Des clients doivent encore "
                        + du.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                        + " F a credit : faites-les regler avant de changer de type d'activite");
            }
            // Les autres activites n'ont qu'un stock : la marchandise des entrepots serait perdue de vue.
            if (lieux.existsByTypeAndActifTrue(tg.parcauto.api.pointdevente.lieu.LieuStock.Type.ENTREPOT)) {
                throw new BusinessException("Des entrepots sont actifs : videz-les et desactivez-les avant de changer de type d'activite");
            }
        }
    }
}
