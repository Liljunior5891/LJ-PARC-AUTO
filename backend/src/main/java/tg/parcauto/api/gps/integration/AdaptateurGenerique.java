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
                entier(noeud, "kilometrage"),
                noeud.hasNonNull("moteurAllume") ? noeud.get("moteurAllume").asBoolean() : null,
                horodatage(noeud, "horodatage"));
    }

    static String nettoyer(String t) {
        if (t == null) {
            return null;
        }
        String n = t.replaceAll("\\p{Cntrl}", "_").trim();
        return n.length() > 80 ? n.substring(0, 80) : n;
    }

    /** Entier positif lisible, sinon absent. */
    static Long entier(JsonNode noeud, String champ) {
        if (!noeud.hasNonNull(champ)) {
            return null;
        }
        BigDecimal d = decimal(noeud, champ);
        return d == null || d.signum() < 0 || d.compareTo(BigDecimal.valueOf(PositionBrute.KILOMETRAGE_MAX)) > 0
                ? null : d.longValue();
    }

    /** Texte court, sans caracteres de controle (pas de fausses lignes dans les journaux). */
    static String texte(JsonNode noeud, String champ) {
        if (!noeud.hasNonNull(champ)) {
            return null;
        }
        String t = noeud.get(champ).asText().replaceAll("\\p{Cntrl}", "_").trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    /** Nombre decimal ordinaire uniquement (pas de notation 1e-100000000, couteuse a convertir). */
    private static final java.util.regex.Pattern DECIMAL_SIMPLE = java.util.regex.Pattern.compile("^-?\\d{1,10}(\\.\\d{1,12})?$");

    static BigDecimal decimal(JsonNode noeud, String champ) {
        if (!noeud.hasNonNull(champ)) {
            return null;
        }
        JsonNode valeur = noeud.get(champ);
        if (valeur.isNumber()) {
            double d = valeur.asDouble();
            return Double.isFinite(d) && Math.abs(d) < 1e10 ? BigDecimal.valueOf(d) : null;
        }
        String texte = valeur.asText().trim();
        return texte.length() <= 24 && DECIMAL_SIMPLE.matcher(texte).matches() ? new BigDecimal(texte) : null;
    }

    static Instant horodatage(JsonNode noeud, String champ) {
        if (!noeud.hasNonNull(champ)) {
            return Instant.now();
        }
        JsonNode valeur = noeud.get(champ);
        // Un horodatage peut arriver en ISO-8601 ou en epoch (secondes ou millisecondes).
        if (valeur.isNumber()) {
            long brut = valeur.asLong();
            // Hors de toute date plausible : position ecartee (Instant.EPOCH est rejete comme trop ancien).
            if (brut <= 0 || brut > 100_000_000_000_000L) {
                return Instant.EPOCH;
            }
            return brut > 100_000_000_000L ? Instant.ofEpochMilli(brut) : Instant.ofEpochSecond(brut);
        }
        // ISO-8601 avec « Z », « +01:00 » ou « +0000 » (format de Traccar) ; illisible : heure de reception.
        String texte = valeur.asText().trim();
        if (texte.length() > 40) {
            return Instant.EPOCH;
        }
        try {
            return java.time.OffsetDateTime.parse(texte, LECTURE_DATE).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            return Instant.now();
        }
    }

    private static final java.time.format.DateTimeFormatter LECTURE_DATE = new java.time.format.DateTimeFormatterBuilder()
            .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HHMM", "Z").optionalEnd()
            .toFormatter();
}
