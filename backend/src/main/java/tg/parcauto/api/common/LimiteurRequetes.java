package tg.parcauto.api.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite le nombre d'appels d'un point public par cle (adresse IP, par
 * exemple) sur une fenetre glissante. Chaque appel est compte de facon
 * atomique : des appels paralleles ne passent pas au-dela de la limite.
 * En memoire : suffisant pour une instance unique du serveur.
 */
@Component
public class LimiteurRequetes {

    /** Trop d'appels : repondu en 429. */
    public static class TropDeRequetesException extends RuntimeException {
        public TropDeRequetesException(String message) {
            super(message);
        }
    }

    private static final long FENETRE_MAX_MS = Duration.ofHours(24).toMillis();

    private final Map<String, Deque<Long>> traces = new ConcurrentHashMap<>();

    public void exiger(String cle, int maximum, Duration fenetre, String message) {
        long maintenant = System.currentTimeMillis();
        long limite = maintenant - fenetre.toMillis();
        boolean[] refuse = {false};
        traces.compute(cle, (k, d) -> {
            Deque<Long> file = d == null ? new ArrayDeque<>() : d;
            while (!file.isEmpty() && file.peekFirst() < limite) {
                file.pollFirst();
            }
            if (file.size() >= maximum) {
                refuse[0] = true;
            } else {
                file.addLast(maintenant);
            }
            return file;
        });
        if (refuse[0]) {
            throw new TropDeRequetesException(message);
        }
    }

    /** Oublie les cles dont aucun appel n'est plus dans une fenetre. */
    @Scheduled(fixedDelay = 3_600_000)
    public void purger() {
        long limite = System.currentTimeMillis() - FENETRE_MAX_MS;
        traces.entrySet().removeIf(e -> {
            Long dernier = e.getValue().peekLast();
            return dernier == null || dernier < limite;
        });
    }
}
