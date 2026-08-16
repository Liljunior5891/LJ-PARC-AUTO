package tg.parcauto.api.gps.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur pour les passerelles au format Traccar, tres repandu chez les
 * integrateurs de traceurs. Sert aussi d'exemple pour brancher un autre
 * fournisseur (chapitre 22).
 */
@Component
public class AdaptateurTraccar implements AdaptateurGps {

    /** Traccar exprime la vitesse en noeuds. */
    private static final BigDecimal NOEUD_EN_KMH = new BigDecimal("1.852");

    @Override
    public String fournisseur() {
        return "traccar";
    }

    @Override
    public List<PositionBrute> convertir(JsonNode charge) {
        List<PositionBrute> positions = new ArrayList<>();
        if (charge == null || charge.isNull()) {
            return positions;
        }
        JsonNode elements = charge.isArray() ? charge : charge.path("positions");
        if (elements.isMissingNode() || elements.isNull()) {
            positions.add(lire(charge));
            return positions;
        }
        elements.forEach(noeud -> positions.add(lire(noeud)));
        return positions;
    }

    private PositionBrute lire(JsonNode noeud) {
        JsonNode attributs = noeud.path("attributes");
        BigDecimal vitesseNoeuds = AdaptateurGenerique.decimal(noeud, "speed");
        return new PositionBrute(
                noeud.hasNonNull("uniqueId") ? noeud.get("uniqueId").asText()
                        : AdaptateurGenerique.texte(noeud, "deviceId"),
                AdaptateurGenerique.decimal(noeud, "latitude"),
                AdaptateurGenerique.decimal(noeud, "longitude"),
                vitesseNoeuds == null ? null
                        : vitesseNoeuds.multiply(NOEUD_EN_KMH).setScale(2, RoundingMode.HALF_UP),
                noeud.hasNonNull("course") ? (int) noeud.get("course").asDouble() : null,
                AdaptateurGenerique.decimal(noeud, "altitude"),
                attributs.hasNonNull("odometer") ? attributs.get("odometer").asLong() / 1000 : null,
                attributs.hasNonNull("ignition") ? attributs.get("ignition").asBoolean() : null,
                AdaptateurGenerique.horodatage(noeud, "deviceTime"));
    }
}
