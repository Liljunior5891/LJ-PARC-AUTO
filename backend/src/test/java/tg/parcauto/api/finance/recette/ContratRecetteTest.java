package tg.parcauto.api.finance.recette;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat imprime apres une recette : intitule par type et montant en toutes lettres. */
class ContratRecetteTest {

    private static String lettres(long montant) {
        return ContratRecettePdfService.enLettres(BigDecimal.valueOf(montant));
    }

    @Test
    @DisplayName("Le montant du contrat s'ecrit en toutes lettres, selon l'orthographe francaise")
    void montantEnLettres() {
        assertThat(lettres(0)).isEqualTo("zéro");
        assertThat(lettres(21)).isEqualTo("vingt et un");
        assertThat(lettres(71)).isEqualTo("soixante et onze");
        assertThat(lettres(80)).isEqualTo("quatre-vingts");
        assertThat(lettres(81)).isEqualTo("quatre-vingt-un");
        assertThat(lettres(95)).isEqualTo("quatre-vingt-quinze");
        assertThat(lettres(200)).isEqualTo("deux cents");
        assertThat(lettres(250)).isEqualTo("deux cent cinquante");
        assertThat(lettres(1000)).isEqualTo("mille");
        assertThat(lettres(80_000)).isEqualTo("quatre-vingt mille");
        assertThat(lettres(200_000)).isEqualTo("deux cent mille");
        assertThat(lettres(125_000)).isEqualTo("cent vingt-cinq mille");
        assertThat(lettres(1_500_000)).isEqualTo("un million cinq cent mille");
        assertThat(lettres(2_000_000)).isEqualTo("deux millions");
        assertThat(lettres(350_075)).isEqualTo("trois cent cinquante mille soixante-quinze");
    }

    @Test
    @DisplayName("Chaque type de recette a son propre intitule de contrat")
    void intituleParType() {
        assertThat(ContratRecettePdfService.intitule(TypeRecette.LOCATION)).isEqualTo("CONTRAT DE LOCATION DE VÉHICULE");
        assertThat(ContratRecettePdfService.intitule(TypeRecette.TRANSPORT)).contains("TRANSPORT DE MARCHANDISES");
        assertThat(ContratRecettePdfService.intitule(TypeRecette.KILOMETRAGE_SUPPLEMENTAIRE)).startsWith("AVENANT");
        for (TypeRecette type : TypeRecette.values()) {
            assertThat(ContratRecettePdfService.intitule(type)).isNotBlank();
        }
    }
}
