package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.util.List;

/**
 * Annule les commandes en ligne laissees en plan, pour que des commandes
 * anonymes fantomes ne retiennent pas le stock indefiniment :
 * <ul>
 *   <li>a payer en mobile money et non payees apres {@value #MINUTES_PAIEMENT} minutes ;</li>
 *   <li>jamais confirmees par la boutique apres {@value #HEURES_CONFIRMATION} heures.</li>
 * </ul>
 * Les commandes passees en salle ou en caisse se traitent au point de vente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpirationCommandesEnLigne {

    static final int MINUTES_PAIEMENT = 30;
    static final int HEURES_CONFIRMATION = 48;

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final CommandeEnLigneService commandes;

    @Scheduled(fixedDelay = 600_000, initialDelay = 180_000)
    public void expirer() {
        List<long[]> perimees = jdbc.query("""
                select id, entreprise_id,
                       case when mode_paiement = 'MOBILE_MONEY'
                             and date_commande < now() - make_interval(mins => ?) then 1 else 0 end
                from mkt_commande
                where statut = 'NOUVELLE' and pdv_commande_id is null and statut_paiement <> 'PAYEE'
                  and ((mode_paiement = 'MOBILE_MONEY' and date_commande < now() - make_interval(mins => ?))
                       or date_commande < now() - make_interval(hours => ?))
                order by id
                limit 500
                """, (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3)},
                MINUTES_PAIEMENT, MINUTES_PAIEMENT, HEURES_CONFIRMATION);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        for (long[] c : perimees) {
            String motif = c[2] == 1 ? "Annulee automatiquement : paiement non recu sous " + MINUTES_PAIEMENT + " minutes"
                    : "Annulee automatiquement : non confirmee par la boutique sous " + HEURES_CONFIRMATION + " heures";
            try {
                ContexteEntreprise.definir(c[1]);
                tx.executeWithoutResult(s -> commandes.expirer(c[0], motif));
            } catch (RuntimeException e) {
                log.warn("Expiration de la commande en ligne {} impossible : {}", c[0], e.getMessage());
            } finally {
                ContexteEntreprise.effacer();
            }
        }
    }
}
