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
        // Transfert Traccar (forward.json) : {"position": {...}, "device": {"uniqueId": "IMEI"}}.
        if (charge.has("position") && charge.get("position").isObject()) {
            positions.add(lire(charge.get("position"), charge.path("device").path("uniqueId").asText(null)));
            return positions;
        }
        JsonNode elements = charge.isArray() ? charge : charge.path("positions");
        if (elements.isMissingNode() || elements.isNull()) {
            positions.add(lire(charge, null));
            return positions;
        }
        elements.forEach(noeud -> positions.add(noeud.has("position") && noeud.get("position").isObject()
                ? lire(noeud.get("position"), noeud.path("device").path("uniqueId").asText(null))
                : lire(noeud, null)));
        return positions;
    }

    private PositionBrute lire(JsonNode noeud, String uniqueIdAppareil) {
        JsonNode attributs = noeud.path("attributes");
        BigDecimal vitesseNoeuds = AdaptateurGenerique.decimal(noeud, "speed");
        return new PositionBrute(
                uniqueIdAppareil != null ? AdaptateurGenerique.nettoyer(uniqueIdAppareil)
                        : noeud.hasNonNull("uniqueId") ? AdaptateurGenerique.texte(noeud, "uniqueId")
                        : AdaptateurGenerique.texte(noeud, "deviceId"),
                AdaptateurGenerique.decimal(noeud, "latitude"),
                AdaptateurGenerique.decimal(noeud, "longitude"),
                vitesseNoeuds == null ? null
                        : vitesseNoeuds.multiply(NOEUD_EN_KMH).setScale(2, RoundingMode.HALF_UP),
                noeud.hasNonNull("course") ? (int) noeud.get("course").asDouble() : null,
                AdaptateurGenerique.decimal(noeud, "altitude"),
                kilometresDepuisMetres(AdaptateurGenerique.decimal(attributs, "odometer")),
                attributs.hasNonNull("ignition") ? attributs.get("ignition").asBoolean() : null,
                AdaptateurGenerique.horodatage(noeud, "deviceTime"));
    }

    private static Long kilometresDepuisMetres(BigDecimal metres) {
        if (metres == null || metres.signum() < 0) {
            return null;
        }
        long km = metres.longValue() / 1000;
        return km > PositionBrute.KILOMETRAGE_MAX ? null : km;
    }
}
