package tg.parcauto.api.finance.depense;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DepenseTest {

    private Depense depense(SousCategorieDepense nature, String montant) {
        Depense d = new Depense();
        d.setSousCategorie(nature);
        d.synchroniserCategorie();
        d.setMontant(new BigDecimal(montant));
        d.setLibelle("Test");
        return d;
    }

    @Test
    @DisplayName("La categorie est deduite de la nature choisie")
    void categorieDeduite() {
        assertThat(depense(SousCategorieDepense.VIDANGE, "45000").getCategorie())
                .isEqualTo(CategorieDepense.ENTRETIEN);
        assertThat(depense(SousCategorieDepense.PEAGE, "5000").getCategorie())
                .isEqualTo(CategorieDepense.EXPLOITATION);
        assertThat(depense(SousCategorieDepense.DIESEL, "450000").getCategorie())
                .isEqualTo(CategorieDepense.CARBURANT);
    }

    @Test
    @DisplayName("Le prix au litre est deduit du montant s'il n'est pas saisi")
    void prixLitreDeduit() {
        Depense d = depense(SousCategorieDepense.DIESEL, "450000");
        d.setQuantiteLitres(new BigDecimal("600"));

        assertThat(d.prixLitreCalcule()).isEqualByComparingTo("750");
        assertThat(d.estPleinCarburant()).isTrue();
    }

    @Test
    @DisplayName("Le prix au litre saisi prime sur le calcul")
    void prixLitreSaisiPrioritaire() {
        Depense d = depense(SousCategorieDepense.DIESEL, "450000");
        d.setQuantiteLitres(new BigDecimal("600"));
        d.setPrixLitre(new BigDecimal("745"));

        assertThat(d.prixLitreCalcule()).isEqualByComparingTo("745");
    }

    @Test
    @DisplayName("Une depense refacturee au client ne greve pas le resultat")
    void depenseAChargeClient() {
        Depense d = depense(SousCategorieDepense.PEAGE, "25000");
        assertThat(d.montantACharge()).isEqualByComparingTo("25000");

        d.setAChargeClient(true);
        assertThat(d.montantACharge()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Les natures sont regroupees par categorie")
    void naturesParCategorie() {
        assertThat(SousCategorieDepense.de(CategorieDepense.PNEUS))
                .containsExactlyInAnyOrder(
                        SousCategorieDepense.ACHAT_PNEU,
                        SousCategorieDepense.MONTAGE_PNEU,
                        SousCategorieDepense.REPARATION_PNEU,
                        SousCategorieDepense.EQUILIBRAGE,
                        SousCategorieDepense.PARALLELISME);
    }
}
