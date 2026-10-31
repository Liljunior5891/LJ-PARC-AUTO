package tg.parcauto.api.plateforme.tenant;

/**
 * Canaux temps reel d'une entreprise : tous ses messages sont publies sous
 * {@code /topic/entreprises/{id}}, et un client ne peut s'abonner qu'au canal
 * de sa propre entreprise (voir {@code WebSocketConfig}).
 */
public final class DiffusionEntreprise {

    public static final String PREFIXE = "/topic/entreprises/";

    private DiffusionEntreprise() {
    }

    public static String canal(Long entrepriseId) {
        return PREFIXE + entrepriseId;
    }

    /** Vrai si la destination est un canal de l'entreprise donnee. */
    public static boolean appartientA(String destination, Long entrepriseId) {
        if (destination == null || entrepriseId == null) {
            return false;
        }
        String canal = canal(entrepriseId);
        return destination.equals(canal) || destination.startsWith(canal + "/");
    }
}
