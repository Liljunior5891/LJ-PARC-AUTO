package tg.parcauto.api.gps.integration;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Adaptateur d'un fournisseur de traceurs GPS, chapitre 22 du cahier des charges.
 * <p>
 * Ajouter un fabricant consiste a fournir une nouvelle implementation de cette
 * interface : aucune autre partie de l'application n'est a modifier.
 */
public interface AdaptateurGps {

    /** Identifiant du fournisseur, tel qu'il apparait dans l'URL d'ingestion. */
    String fournisseur();

    /** Traduit la charge utile du fournisseur en positions normalisees. */
    List<PositionBrute> convertir(JsonNode charge);
}
