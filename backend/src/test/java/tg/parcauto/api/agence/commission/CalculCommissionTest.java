package tg.parcauto.api.agence.commission;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moteur de commission : les exemples du cahier des charges (6.3), le bareme
 * le plus precis qui l'emporte, la periode de validite, plancher et plafond.
 */
class CalculCommissionTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 11, 7);

    private static Bareme bareme(long id, Long type, Long reseau, Bareme.Mode mode) {
        Bareme b = new Bareme();
        b.setId(id);
        b.setServiceId(1L);
        b.setTypeOperationId(type);
        b.setReseauId(reseau);
        b.setLibelle("B" + id);
        b.setMode(mode);
        b.setDateDebut(LocalDate.of(2026, 1, 1));
        return b;
    }

    private static Bareme pourcentage(long id, Long type, Long reseau, String taux) {
        Bareme b = bareme(id, type, reseau, Bareme.Mode.POURCENTAGE);
        b.setTaux(new BigDecimal(taux));
        return b;
    }

    private static long commission(Bareme b, long montant) {
        return CalculCommission.appliquer(b, montant).commission();
    }

    @Test
    void exemplesDuCahierAvecLesBaremesDExemple() {
        Bareme depotKeaz = pourcentage(1, 10L, null, "0.25");
        assertThat(commission(depotKeaz, 100_000)).isEqualTo(250);
        assertThat(commission(depotKeaz, 900_000)).isEqualTo(2_250);

        Bareme retraitKeaz = bareme(2, 11L, null, Bareme.Mode.TRANCHES);
        retraitKeaz.getTranches().addAll(List.of(
                new TrancheBareme(0, 999_999L, Bareme.Mode.POURCENTAGE, null, new BigDecimal("0.2")),
                new TrancheBareme(1_000_000, null, Bareme.Mode.POURCENTAGE, null, new BigDecimal("0.5"))));
        assertThat(commission(retraitKeaz, 190_000)).isEqualTo(380);
        assertThat(commission(retraitKeaz, 1_800_000)).isEqualTo(9_000);

        assertThat(commission(pourcentage(3, null, 20L, "0.2478"), 1_000_000)).isEqualTo(2_478);
        assertThat(commission(pourcentage(4, null, 21L, "0.5228"), 57_000)).isEqualTo(298);
        assertThat(commission(pourcentage(5, null, 22L, "0.2386"), 113_602)).isEqualTo(271);
    }

    @Test
    void leBaremeLePlusPrecisLEmporte() {
        Bareme service = pourcentage(1, null, null, "1");
        Bareme reseau = pourcentage(2, null, 20L, "2");
        Bareme type = pourcentage(3, 10L, null, "3");
        Bareme typeEtReseau = pourcentage(4, 10L, 20L, "4");
        List<Bareme> tous = List.of(service, reseau, type, typeEtReseau);

        assertThat(CalculCommission.choisir(tous, 1L, 10L, 20L, JOUR)).contains(typeEtReseau);
        assertThat(CalculCommission.choisir(tous, 1L, 10L, 21L, JOUR)).contains(type);
        assertThat(CalculCommission.choisir(tous, 1L, 11L, 20L, JOUR)).contains(reseau);
        assertThat(CalculCommission.choisir(tous, 1L, 11L, null, JOUR)).contains(service);
        assertThat(CalculCommission.choisir(tous, 2L, 10L, 20L, JOUR)).isEmpty();
    }

    @Test
    void laPeriodeDeValiditeEstRespectee() {
        Bareme ancien = pourcentage(1, null, null, "1");
        ancien.setDateFin(LocalDate.of(2026, 11, 6));
        Bareme nouveau = pourcentage(2, null, null, "2");
        nouveau.setDateDebut(LocalDate.of(2026, 11, 7));
        List<Bareme> versions = List.of(ancien, nouveau);

        assertThat(CalculCommission.choisir(versions, 1L, null, null, LocalDate.of(2026, 11, 6))).contains(ancien);
        assertThat(CalculCommission.choisir(versions, 1L, null, null, JOUR)).contains(nouveau);
        nouveau.setActif(false);
        assertThat(CalculCommission.choisir(versions, 1L, null, null, JOUR)).isEmpty();
    }

    @Test
    void plancherPlafondEtFixe() {
        Bareme b = pourcentage(1, null, null, "1");
        b.setMinimum(100L);
        b.setMaximum(5_000L);
        assertThat(commission(b, 2_000)).isEqualTo(100);
        assertThat(commission(b, 200_000)).isEqualTo(2_000);
        assertThat(commission(b, 2_000_000)).isEqualTo(5_000);

        Bareme fixe = bareme(2, null, null, Bareme.Mode.FIXE);
        fixe.setValeurFixe(500L);
        assertThat(commission(fixe, 1)).isEqualTo(500);
    }

    @Test
    void trancheFixeEtMontantHorsTranche() {
        Bareme b = bareme(1, null, null, Bareme.Mode.TRANCHES);
        b.getTranches().addAll(List.of(
                new TrancheBareme(1_000, 50_000L, Bareme.Mode.FIXE, 200L, null),
                new TrancheBareme(50_001, 200_000L, Bareme.Mode.FIXE, 600L, null)));
        assertThat(commission(b, 50_000)).isEqualTo(200);
        assertThat(commission(b, 50_001)).isEqualTo(600);
        CalculCommission.Resultat hors = CalculCommission.appliquer(b, 500_000);
        assertThat(hors.commission()).isZero();
        assertThat(hors.detail()).contains("Aucune tranche");
    }

    @Test
    void arrondiAuFrancLePlusProche() {
        Bareme b = pourcentage(1, null, null, "0.5");
        assertThat(commission(b, 101)).isEqualTo(1);   // 0,505 F -> 1 F
        assertThat(commission(b, 99)).isZero();        // 0,495 F -> 0 F
    }
}
