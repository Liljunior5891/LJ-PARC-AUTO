package tg.parcauto.api.agence.tresorerie;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Journee de caisse d'un instant, a l'heure de Lome (UTC+0). Avant l'heure de
 * bascule reglee par l'administrateur, l'operation compte pour la veille : une
 * agence ouverte jusqu'a 2 h du matin avec une bascule a 6 h garde sa nuit sur
 * la meme journee.
 */
public final class JourneeCaisse {

    public static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");

    private JourneeCaisse() {
    }

    public static LocalDate de(Instant instant, int heureBascule) {
        ZonedDateTime local = instant.atZone(FUSEAU);
        LocalDate jour = local.toLocalDate();
        return local.getHour() < heureBascule ? jour.minusDays(1) : jour;
    }
}
