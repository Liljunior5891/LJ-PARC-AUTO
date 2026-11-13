package tg.parcauto.api.pointdevente.vente;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chiffre d'affaires et cout d'achat des ventes validees : la marge brute dont
 * partent le compte de resultat et le fonds de reserve. Un seul endroit definit
 * ce qui compte, pour que les deux chiffres restent d'accord.
 */
@Component
@RequiredArgsConstructor
public class MargeVentes {

    private final VenteRepository ventes;

    public record Marge(BigDecimal chiffreAffaires, BigDecimal coutAchats) {
        public static final Marge NULLE = new Marge(BigDecimal.ZERO, BigDecimal.ZERO);

        public BigDecimal brute() {
            return chiffreAffaires.subtract(coutAchats);
        }

        public Marge plus(Marge autre) {
            return new Marge(chiffreAffaires.add(autre.chiffreAffaires), coutAchats.add(autre.coutAchats));
        }
    }

    /** Marge des ventes validees de [debut, fin[. */
    public Marge marge(Instant debut, Instant fin) {
        BigDecimal chiffre = ventes.totauxPeriode(debut, fin).stream()
                .map(l -> (BigDecimal) l[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Marge(chiffre, ventes.coutAchatPeriode(debut, fin));
    }

    /** Meme marge, jour par jour (heure de Lome), en une requete. */
    public Map<LocalDate, Marge> parJour(Instant debut, Instant fin) {
        Map<LocalDate, Marge> jours = new TreeMap<>();
        for (Object[] l : ventes.margeParJour(ContexteEntreprise.exiger(), debut, fin)) {
            jours.put(enDate(l[0]), new Marge(enMontant(l[1]), enMontant(l[2])));
        }
        return jours;
    }

    private static LocalDate enDate(Object o) {
        return o instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) o;
    }

    private static BigDecimal enMontant(Object o) {
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }
}
