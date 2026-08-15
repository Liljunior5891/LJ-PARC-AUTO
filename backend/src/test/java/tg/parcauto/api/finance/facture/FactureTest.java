package tg.parcauto.api.finance.facture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FactureTest {

    private Facture facture(String prixUnitaire, String quantite) {
        Facture f = new Facture();
        f.setDateEmission(LocalDate.of(2026, 3, 1));
        f.setTauxTva(new BigDecimal("18"));
        FactureLigne ligne = new FactureLigne();
        ligne.setDesignation("Transport Lome - Ouagadougou");
        ligne.setQuantite(new BigDecimal(quantite));
        ligne.setPrixUnitaire(new BigDecimal(prixUnitaire));
        f.ajouterLigne(ligne);
        f.recalculer();
        return f;
    }

    private Paiement paiement(Facture f, String montant, LocalDate date) {
        Paiement p = new Paiement();
        p.setFacture(f);
        p.setMontant(new BigDecimal(montant));
        p.setDatePaiement(date);
        p.setModePaiement(ModePaiement.VIREMENT);
        f.getPaiements().add(p);
        return p;
    }

    @Test
    @DisplayName("Le pied de facture est calcule a partir des lignes")
    void calculDuPied() {
        Facture f = facture("1200000", "2");

        assertThat(f.getMontantHt()).isEqualByComparingTo("2400000");
        assertThat(f.getMontantTva()).isEqualByComparingTo("432000");
        assertThat(f.getMontantTtc()).isEqualByComparingTo("2832000");
    }

    @Test
    @DisplayName("Le reste a payer decroit avec les reglements")
    void resteAPayer() {
        Facture f = facture("1000000", "1");
        f.setStatut(StatutFacture.NON_PAYEE);
        assertThat(f.resteAPayer()).isEqualByComparingTo("1180000");

        paiement(f, "180000", LocalDate.of(2026, 3, 5));
        assertThat(f.montantPaye()).isEqualByComparingTo("180000");
        assertThat(f.resteAPayer()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("Le statut suit les reglements enregistres")
    void statutSuitLesReglements() {
        Facture f = facture("1000000", "1");
        f.setStatut(StatutFacture.NON_PAYEE);
        f.setDateEcheance(LocalDate.now().plusDays(30));

        paiement(f, "500000", LocalDate.now());
        f.reevaluerStatut();
        assertThat(f.getStatut()).isEqualTo(StatutFacture.PARTIELLEMENT_PAYEE);

        paiement(f, "680000", LocalDate.now());
        f.reevaluerStatut();
        assertThat(f.getStatut()).isEqualTo(StatutFacture.PAYEE);
        assertThat(f.resteAPayer()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Une facture echue et non soldee bascule en retard")
    void factureEnRetard() {
        Facture f = facture("1000000", "1");
        f.setStatut(StatutFacture.NON_PAYEE);
        f.setDateEcheance(LocalDate.now().minusDays(5));

        assertThat(f.enRetard()).isTrue();
        f.reevaluerStatut();
        assertThat(f.getStatut()).isEqualTo(StatutFacture.EN_RETARD);
    }

    @Test
    @DisplayName("Une facture soldee n'est jamais consideree en retard")
    void factureSoldeeJamaisEnRetard() {
        Facture f = facture("1000000", "1");
        f.setStatut(StatutFacture.NON_PAYEE);
        f.setDateEcheance(LocalDate.now().minusDays(5));
        paiement(f, "1180000", LocalDate.now().minusDays(6));

        f.reevaluerStatut();
        assertThat(f.getStatut()).isEqualTo(StatutFacture.PAYEE);
        assertThat(f.enRetard()).isFalse();
    }

    @Test
    @DisplayName("Un brouillon reste hors du circuit de reglement")
    void brouillonNonConcerne() {
        Facture f = facture("1000000", "1");
        f.setStatut(StatutFacture.BROUILLON);
        f.reevaluerStatut();

        assertThat(f.getStatut()).isEqualTo(StatutFacture.BROUILLON);
        assertThat(StatutFacture.BROUILLON.modifiable()).isTrue();
        assertThat(StatutFacture.NON_PAYEE.modifiable()).isFalse();
    }
}
