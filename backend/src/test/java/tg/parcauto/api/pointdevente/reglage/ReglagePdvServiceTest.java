package tg.parcauto.api.pointdevente.reglage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.pointdevente.depense.DepensePdv;
import tg.parcauto.api.pointdevente.depense.DepensePdvRepository;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.pointdevente.reserve.PeriodeFondsReserve;
import tg.parcauto.api.pointdevente.reserve.PeriodeFondsReserveRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReglagePdvServiceTest {

    @Test
    @DisplayName("Desactiver le fonds refuse d'office les demandes en attente, au nom de l'administrateur")
    void desactivationRefuseLesDemandes() {
        ReglagePdvRepository repository = mock(ReglagePdvRepository.class);
        PeriodeFondsReserveRepository periodes = mock(PeriodeFondsReserveRepository.class);
        DepensePdvRepository depenses = mock(DepensePdvRepository.class);
        ReglagePdv reglage = new ReglagePdv();
        reglage.setFondsReserveActif(true);
        when(repository.verrouiller()).thenReturn(List.of(reglage));
        when(repository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(reglage));
        PeriodeFondsReserve ouverte = new PeriodeFondsReserve();
        ouverte.setDebut(LocalDate.now().minusMonths(2));
        ouverte.setPourcent(BigDecimal.TEN);
        when(periodes.findFirstByFinIsNull()).thenReturn(Optional.of(ouverte));
        DepensePdv demande = new DepensePdv();
        demande.setStatut(DepensePdv.Statut.ATTENTE_ADMIN);
        when(depenses.findByModeAndStatutIn(eq(ModeSortie.RESERVE), any())).thenReturn(List.of(demande));
        ReglagePdvService service = new ReglagePdvService(repository, periodes, depenses, mock(org.springframework.beans.factory.ObjectProvider.class));

        service.modifier(new ReglagePdvService.Requete(false, false, BigDecimal.ZERO, null, null, null, false, null), "Admin");

        assertThat(reglage.isFondsReserveActif()).isFalse();
        assertThat(demande.getStatut()).isEqualTo(DepensePdv.Statut.REFUSEE);
        assertThat(demande.getRefuseePar()).isEqualTo("Admin");
        assertThat(ouverte.getFin()).isNotNull();
    }

    @Test
    @DisplayName("Les profils hors gestion ne voient pas le fonds de reserve")
    void reglagesSansFonds() {
        ReglagePdvService.Reglages r = new ReglagePdvService.Reglages(true, false, BigDecimal.ZERO, false, 5,
                BigDecimal.TEN, true, new BigDecimal("12.5"));
        assertThat(r.sansFondsReserve().fondsReserveActif()).isFalse();
        assertThat(r.sansFondsReserve().fondsReservePourcent()).isNull();
        assertThat(r.sansFondsReserve().associesActif()).isTrue();
    }
}
