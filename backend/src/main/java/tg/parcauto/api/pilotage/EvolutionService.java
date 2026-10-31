package tg.parcauto.api.pilotage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Evolution mensuelle des recettes et des depenses, pour le tableau de bord.
 * <p>
 * L'agregation par mois se fait en base plutot que par une requete par mois :
 * une seule interrogation suffit, quel que soit l'historique demande.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvolutionService {

    private static final DateTimeFormatter CLE = DateTimeFormatter.ofPattern("yyyy-MM");

    @PersistenceContext
    private EntityManager em;

    /** Serie mensuelle sur les {@code nombreMois} derniers mois, mois courant inclus. */
    public List<EvolutionMensuelleDto> evolution(int nombreMois) {
        YearMonth debut = YearMonth.now().minusMonths(Math.max(1, nombreMois) - 1L);
        LocalDate premierJour = debut.atDay(1);
        LocalDate dernierJour = YearMonth.now().atEndOfMonth();

        Map<String, BigDecimal> recettes = totauxParMois(
                "select to_char(date_recette, 'YYYY-MM') as mois, coalesce(sum(montant), 0) as total "
                        + "from recette where entreprise_id = :entreprise "
                        + "and date_recette between :debut and :fin group by 1",
                premierJour, dernierJour);
        // Les depenses refacturees au client n'entament pas le resultat.
        Map<String, BigDecimal> depenses = totauxParMois(
                "select to_char(date_depense, 'YYYY-MM') as mois, coalesce(sum(montant), 0) as total "
                        + "from depense where entreprise_id = :entreprise and a_charge_client = false "
                        + "and date_depense between :debut and :fin group by 1",
                premierJour, dernierJour);

        List<EvolutionMensuelleDto> serie = new ArrayList<>();
        for (YearMonth mois = debut; !mois.isAfter(YearMonth.now()); mois = mois.plusMonths(1)) {
            String cle = mois.format(CLE);
            BigDecimal recette = recettes.getOrDefault(cle, BigDecimal.ZERO);
            BigDecimal depense = depenses.getOrDefault(cle, BigDecimal.ZERO);
            serie.add(new EvolutionMensuelleDto(
                    cle,
                    mois.getMonth().getDisplayName(TextStyle.SHORT, Locale.FRENCH),
                    mois.getYear(),
                    recette, depense, recette.subtract(depense)));
        }
        return serie;
    }

    private Map<String, BigDecimal> totauxParMois(String sql, LocalDate debut, LocalDate fin) {
        Map<String, BigDecimal> totaux = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        List<Object[]> lignes = em.createNativeQuery(sql)
                // Requete native : le cloisonnement Hibernate ne s'y applique pas.
                .setParameter("entreprise", ContexteEntreprise.exiger())
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList();
        for (Object[] ligne : lignes) {
            totaux.put((String) ligne[0], (BigDecimal) ligne[1]);
        }
        return totaux;
    }
}
