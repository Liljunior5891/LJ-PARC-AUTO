package tg.parcauto.api.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Bornes de tous les nombres decimaux recus en JSON, avant tout calcul.
 * <p>
 * Une valeur comme {@code 1E+999999999} tient en quelques caracteres, mais son
 * arrondi ({@code setScale}) ou sa division occupe le serveur pendant des minutes
 * et peut saturer sa memoire, pour toutes les entreprises. Aucun montant, prix,
 * quantite ou taux de l'application n'a besoin de plus de
 * {@value #CHIFFRES_ENTIERS_MAX} chiffres avant la virgule ni de plus de
 * {@value #DECIMALES_MAX} decimales.
 */
@Configuration
public class MontantsBornes {

    static final int CHIFFRES_ENTIERS_MAX = 15;
    static final int DECIMALES_MAX = 6;
    /** Au-dela, la chaine n'est meme pas analysee. */
    static final int LONGUEUR_TEXTE_MAX = 40;

    /** Module enregistre par Spring Boot avec les autres modules Jackson (dates, etc.). */
    @Bean
    SimpleModule montantsBornesModule() {
        SimpleModule module = new SimpleModule("montants-bornes");
        module.addDeserializer(BigDecimal.class, new Deserialiseur());
        return module;
    }

    /** Vrai si le nombre reste dans les bornes de l'application. */
    public static boolean borne(BigDecimal valeur) {
        if (valeur == null) {
            return true;
        }
        int scale = valeur.scale();
        if (scale > DECIMALES_MAX) {
            // Zeros inutiles en fin de partie decimale (1.50000000) : tolere s'ils disparaissent.
            if (scale > 1_000) {
                return false;
            }
            valeur = valeur.stripTrailingZeros();
            if (valeur.scale() > DECIMALES_MAX) {
                return false;
            }
        }
        long entiers = (long) valeur.precision() - valeur.scale();
        return entiers <= CHIFFRES_ENTIERS_MAX;
    }

    static final class Deserialiseur extends StdScalarDeserializer<BigDecimal> {

        Deserialiseur() {
            super(BigDecimal.class);
        }

        @Override
        public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken jeton = p.currentToken();
            BigDecimal valeur;
            if (jeton == JsonToken.VALUE_NUMBER_INT || jeton == JsonToken.VALUE_NUMBER_FLOAT) {
                if (p.getTextLength() > LONGUEUR_TEXTE_MAX) {
                    return (BigDecimal) ctxt.handleWeirdNumberValue(BigDecimal.class, 0, "nombre trop long");
                }
                valeur = p.getDecimalValue();
            } else if (jeton == JsonToken.VALUE_STRING) {
                String texte = p.getText().trim();
                if (texte.isEmpty()) {
                    return null;
                }
                if (texte.length() > LONGUEUR_TEXTE_MAX) {
                    return (BigDecimal) ctxt.handleWeirdStringValue(BigDecimal.class, texte, "nombre trop long");
                }
                try {
                    valeur = new BigDecimal(texte);
                } catch (NumberFormatException e) {
                    return (BigDecimal) ctxt.handleWeirdStringValue(BigDecimal.class, texte, "nombre invalide");
                }
            } else if (jeton == JsonToken.VALUE_NULL) {
                return null;
            } else {
                return (BigDecimal) ctxt.handleUnexpectedToken(BigDecimal.class, p);
            }
            if (!borne(valeur)) {
                return (BigDecimal) ctxt.handleWeirdNumberValue(BigDecimal.class, 0,
                        "nombre hors des bornes de l'application");
            }
            return valeur;
        }
    }
}
