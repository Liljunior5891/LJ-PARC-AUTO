package tg.parcauto.api.pointdevente.impression;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Contenu d'un bon de preparation, fige a la creation et sans prix. Il sert
 * aussi bien a la fenetre d'impression du navigateur qu'a l'agent qui le
 * met en forme pour l'imprimante (ESC/POS).
 */
public record BonCuisine(
        /** Nom de l'imprimante (« Cuisine », « Bar »), en tete du bon. */
        String poste,
        TravailImpression.Type type,
        /** Table, client ou « Comptoir ». */
        String repere,
        /** Numero de la commande ou du ticket. */
        String numero,
        /** Envoi n° X de la commande (1er, 2e ajout...) ; nul pour un ticket. */
        Integer envoi,
        Instant date,
        String serveur,
        String caissier,
        List<Ligne> lignes,
        /** Note de la commande (allergie, a emporter...). */
        String note,
        int largeurPapier,
        int copies) {

    public record Ligne(BigDecimal quantite, String libelle, String note) {
    }

    BonCuisine pour(TravailImpression.Type autre) {
        return new BonCuisine(poste, autre, repere, numero, envoi, date, serveur, caissier, lignes, note,
                largeurPapier, copies);
    }
}
