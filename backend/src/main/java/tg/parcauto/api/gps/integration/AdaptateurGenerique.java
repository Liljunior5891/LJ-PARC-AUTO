package tg.parcauto.api.gps.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Format pivot de PARC AUTO : c'est celui a viser pour un traceur programmable
 * ou une passerelle maison. Accepte un objet unique ou un tableau de positions.
 */
@Component
public class AdaptateurGenerique implements AdaptateurGps {

    @Override
    public String fournisseur() {
        return "generique";
    }

    @Override
    public List<PositionBrute> convertir(JsonNode charge) {
        List<PositionBrute> positions = new ArrayList<>();
        if (charge == null || charge.isNull()) {
            return positions;
        }
        if (charge.isArray()) {
            charge.forEach(noeud -> positions.add(lire(noeud)));
        } else {
            positions.add(lire(charge));
        }
        return positions;
    }

    private PositionBrute lire(JsonNode noeud) {
        return new PositionBrute(
                texte(noeud, "deviceId"),
                decimal(noeud, "latitude"),
                decimal(noeud, "longitude"),
                decimal(noeud, "vitesseKmh"),
                noeud.hasNonNull("direction") ? noeud.get("direction").asInt() : null,
                decimal(noeud, "altitude"),
                noeud.hasNonNull("kilometrage") ? noeud.get("kilometrage").asLong() : null,
                noeud.hasNonNull("moteurAllume") ? noeud.get("moteurAllume").asBoolean() : null,
                horodatage(noeud, "horodatage"));
    }

    static String texte(JsonNode noeud, String champ) {
        return noeud.hasNonNull(champ) ? noeud.get(champ).asText() : null;
    }

    static BigDecimal decimal(JsonNode noeud, String champ) {
        return noeud.hasNonNull(champ) ? new BigDecimal(noeud.get(champ).asText()) : null;
    }

    static Instant horodatage(JsonNode noeud, String champ) {
        if (!noeud.hasNonNull(champ)) {
            return Instant.now();
        }
        JsonNode valeur = noeud.get(champ);
        // Un horodatage peut arriver en ISO-8601 ou en epoch (secondes ou millisecondes).
        if (valeur.isNumber()) {
            long brut = valeur.asLong();
            return brut > 100_000_000_000L ? Instant.ofEpochMilli(brut) : Instant.ofEpochSecond(brut);
        }
        return Instant.parse(valeur.asText());
    }
}
