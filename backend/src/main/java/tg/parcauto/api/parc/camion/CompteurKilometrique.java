package tg.parcauto.api.parc.camion;

import tg.parcauto.api.common.BusinessException;

/**
 * Seule regle d'avancement du compteur d'un vehicule par un releve de terrain
 * (mission, entretien, plein, traceur GPS).
 * <p>
 * Le compteur ne recule jamais, et la fiche du vehicule interdit de le baisser :
 * un releve aberrant serait donc definitif. Un saut de plus de
 * {@link #SAUT_MAX_KM} km par rapport au compteur est refuse ; seul le
 * gestionnaire corrige alors le compteur depuis la fiche du vehicule.
 */
public final class CompteurKilometrique {

    public static final long SAUT_MAX_KM = 5_000;
    /** Au-dela, la valeur n'est pas un compteur kilometrique. */
    public static final long RELEVE_MAX_KM = 9_999_999;

    private CompteurKilometrique() {
    }

    /** Avance le compteur, ou refuse le releve s'il est aberrant. Sans effet si le releve est inferieur. */
    public static void avancer(Camion camion, Long releve, String origine) {
        if (camion == null || releve == null) {
            return;
        }
        if (releve < 0 || releve > RELEVE_MAX_KM) {
            throw new BusinessException(origine + " : releve de compteur invalide (" + releve + " km)");
        }
        long ecart = releve - camion.getKilometrageActuel();
        if (ecart <= 0) {
            return;
        }
        if (ecart > SAUT_MAX_KM) {
            throw new BusinessException(origine + " : le releve de " + releve + " km depasse de " + ecart
                    + " km le compteur du vehicule " + camion.getImmatriculation() + " (" + camion.getKilometrageActuel()
                    + " km). Au-dela de " + SAUT_MAX_KM + " km d'ecart, corrigez d'abord le compteur depuis la fiche"
                    + " du vehicule.");
        }
        camion.setKilometrageActuel(releve);
    }

    /** Variante sans erreur (traceurs GPS) : un releve aberrant est simplement ignore. */
    public static boolean avancerSiPlausible(Camion camion, Long releve) {
        if (camion == null || releve == null || releve < 0 || releve > RELEVE_MAX_KM) {
            return false;
        }
        long ecart = releve - camion.getKilometrageActuel();
        if (ecart <= 0 || ecart > SAUT_MAX_KM) {
            return false;
        }
        camion.setKilometrageActuel(releve);
        return true;
    }
}
