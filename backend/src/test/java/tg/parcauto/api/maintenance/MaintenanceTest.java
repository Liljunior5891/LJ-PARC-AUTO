package tg.parcauto.api.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.maintenance.entretien.Entretien;
import tg.parcauto.api.maintenance.entretien.StatutEntretien;
import tg.parcauto.api.maintenance.entretien.TypeEntretien;
import tg.parcauto.api.maintenance.pneu.EtatPneu;
import tg.parcauto.api.maintenance.pneu.Pneu;
import tg.parcauto.api.maintenance.pneu.StatutPneu;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceTest {

    private Camion camion(long kilometrageActuel) {
        Camion c = new Camion();
        c.setImmatriculation("TG-1234-AB");
        c.setKilometrageInitial(0L);
        c.setKilometrageActuel(kilometrageActuel);
        return c;
    }

    @Nested
    @DisplayName("Echeances d'entretien, chapitre 19")
    class Echeances {

        private Entretien vidange(long kilometragePrevu, long kilometrageActuel) {
            Entretien e = new Entretien();
            e.setType(TypeEntretien.VIDANGE);
            e.setLibelle("Vidange");
            e.setCamion(camion(kilometrageActuel));
            e.setKilometragePrevu(kilometragePrevu);
            e.setPeriodiciteKm(10_000L);
            return e;
        }

        @Test
        @DisplayName("L'exemple du cahier des charges : prochaine vidange a 130 000 km")
        void exempleDuCahierDesCharges() {
            // Derniere vidange a 120 000 km, compteur a 129 000 km.
            Entretien e = vidange(130_000L, 129_000L);

            assertThat(e.kilometresRestants()).isEqualTo(1_000L);
            assertThat(e.echeanceProche()).isTrue();
            assertThat(e.enRetard()).isFalse();
        }

        @Test
        @DisplayName("Une echeance kilometrique depassee est signalee en retard")
        void echeanceDepassee() {
            Entretien e = vidange(130_000L, 131_500L);

            assertThat(e.kilometresRestants()).isEqualTo(-1_500L);
            assertThat(e.enRetard()).isTrue();
            assertThat(e.echeanceProche()).isTrue();
        }

        @Test
        @DisplayName("Une echeance encore lointaine ne declenche rien")
        void echeanceLointaine() {
            Entretien e = vidange(130_000L, 122_000L);

            assertThat(e.echeanceProche()).isFalse();
            assertThat(e.enRetard()).isFalse();
        }

        @Test
        @DisplayName("L'echeance calendaire declenche aussi l'alerte")
        void echeanceCalendaire() {
            Entretien e = vidange(200_000L, 100_000L);
            e.setDatePrevue(LocalDate.now().plusDays(10));

            assertThat(e.joursRestants()).isEqualTo(10L);
            assertThat(e.echeanceProche()).isTrue();
        }

        @Test
        @DisplayName("Une intervention close ne declenche plus d'alerte")
        void interventionClose() {
            Entretien e = vidange(130_000L, 140_000L);
            e.setStatut(StatutEntretien.REALISE);

            assertThat(e.echeanceProche()).isFalse();
            assertThat(e.enRetard()).isFalse();
        }

        @Test
        @DisplayName("La prochaine occurrence se deduit de la periodicite")
        void prochaineOccurrence() {
            Entretien e = vidange(130_000L, 130_200L);
            e.setKilometrageRealise(130_200L);
            e.setDateRealisation(LocalDate.of(2026, 3, 1));
            e.setPeriodiciteJours(180);

            assertThat(e.prochainKilometrage()).isEqualTo(140_200L);
            assertThat(e.prochaineDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        }
    }

    @Nested
    @DisplayName("Usure des pneus, chapitre 20")
    class Pneus {

        private Pneu pneuMonte(long kmMontage, long kmActuel, long dureeVie) {
            Pneu p = new Pneu();
            p.setReference("PN-001");
            p.setStatut(StatutPneu.MONTE);
            p.setCamion(camion(kmActuel));
            p.setKilometrageMontage(kmMontage);
            p.setDureeViePrevueKm(dureeVie);
            p.setPrixAchat(new BigDecimal("90000"));
            return p;
        }

        @Test
        @DisplayName("Le kilometrage du pneu court depuis son montage")
        void kilometrageParcouru() {
            Pneu p = pneuMonte(120_000L, 150_000L, 80_000L);

            assertThat(p.kilometresParcourus()).isEqualTo(30_000L);
            assertThat(p.usurePourcent()).isEqualTo(38);
            assertThat(p.aRemplacer()).isFalse();
        }

        @Test
        @DisplayName("Un pneu au-dela de 85 % de sa duree de vie est signale")
        void pneuUse() {
            Pneu p = pneuMonte(120_000L, 190_000L, 80_000L);

            assertThat(p.usurePourcent()).isEqualTo(88);
            assertThat(p.aRemplacer()).isTrue();
        }

        @Test
        @DisplayName("Un etat critique impose le remplacement, quel que soit le kilometrage")
        void etatCritique() {
            Pneu p = pneuMonte(120_000L, 125_000L, 80_000L);
            p.setEtat(EtatPneu.A_REMPLACER);

            assertThat(p.aRemplacer()).isTrue();
        }

        @Test
        @DisplayName("Le cout au kilometre permet de comparer les marques")
        void coutParKm() {
            Pneu p = pneuMonte(120_000L, 150_000L, 80_000L);

            assertThat(p.coutParKm()).isEqualByComparingTo("3.00");
        }

        @Test
        @DisplayName("Le kilometrage se fige a la depose")
        void deposeFigeLeKilometrage() {
            Pneu p = pneuMonte(120_000L, 200_000L, 80_000L);
            p.setKilometrageDepose(175_000L);
            p.setStatut(StatutPneu.DEPOSE);

            assertThat(p.kilometresParcourus()).isEqualTo(55_000L);
            // Un pneu depose n'est plus a remplacer : il ne roule plus.
            assertThat(p.aRemplacer()).isFalse();
        }
    }
}
