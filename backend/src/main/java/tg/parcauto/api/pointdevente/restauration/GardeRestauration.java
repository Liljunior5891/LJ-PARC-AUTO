package tg.parcauto.api.pointdevente.restauration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.GardeVariante;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

/** Le point de vente ne quitte pas le mode restaurant tant que des tables sont ouvertes. */
@Component
@RequiredArgsConstructor
public class GardeRestauration implements GardeVariante {

    static final String RESTAURATION = "RESTAURATION";

    private final CommandeRepository commandeRepository;

    @Override
    public ModuleApplicatif module() {
        return ModuleApplicatif.POINT_DE_VENTE;
    }

    @Override
    public void verifierChangement(String actuelle, String nouvelle) {
        if (RESTAURATION.equals(actuelle) && !RESTAURATION.equals(nouvelle)) {
            long ouvertes = commandeRepository.countByStatut(Commande.Statut.OUVERTE);
            if (ouvertes > 0) {
                throw new BusinessException(ouvertes + " commande(s) de table sont encore ouvertes : "
                        + "encaissez-les ou annulez-les avant de changer de type d'activite");
            }
        }
    }
}
