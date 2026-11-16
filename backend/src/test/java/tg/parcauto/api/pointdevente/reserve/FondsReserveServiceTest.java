package tg.parcauto.api.pointdevente.reserve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.depense.DepensePdv;
import tg.parcauto.api.pointdevente.depense.DepensePdvRepository;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FondsReserveServiceTest {

    private ApportFondsRepository apports;
    private DepensePdvRepository depenses;
    private FondsReserveService service;

    @BeforeEach
    void preparer() {
        apports = mock(ApportFondsRepository.class);
        depenses = mock(DepensePdvRepository.class);
        ReglagePdvService reglages = mock(ReglagePdvService.class);
        when(reglages.lire()).thenReturn(new ReglagePdvService.Reglages(true, false, BigDecimal.ZERO, false, 5,
                BigDecimal.TEN, true));
        when(depenses.totalPar(eq(ModeSortie.RESERVE), any())).thenReturn(BigDecimal.ZERO);
        when(depenses.parModeEtStatuts(eq(ModeSortie.RESERVE), any(), any())).thenReturn(List.of());
        service = new FondsReserveService(apports, depenses, reglages, mock(NumerotationService.class));
    }

    private static ApportFonds apport(Long associeId, String nom, String montant) {
        ApportFonds a = new ApportFonds();
        a.setOrigine(associeId == null ? ApportFonds.Origine.ENTREPRISE : ApportFonds.Origine.ASSOCIE);
        a.setAssocieId(associeId);
        a.setAssocieNom(nom);
        a.setMontant(new BigDecimal(montant));
        a.setDateApport(Instant.now());
        a.setAuteurNom("x");
        return a;
    }

    @Test
    @DisplayName("Le solde du fonds = apports des associes et de l'entreprise - depenses payees ; cumul par contributeur")
    void soldeEtContributeurs() {
        when(apports.findAllByOrderByDateApportAsc()).thenReturn(List.of(
                apport(7L, "Ama", "100000"), apport(null, null, "50000"), apport(7L, "Ama", "20000")));
        when(depenses.totalPar(ModeSortie.RESERVE, List.of(DepensePdv.Statut.VALIDE))).thenReturn(new BigDecimal("30000"));
        when(depenses.totalPar(ModeSortie.RESERVE, List.of(DepensePdv.Statut.ATTENTE_GERANT, DepensePdv.Statut.ATTENTE_ADMIN)))
                .thenReturn(new BigDecimal("10000"));

        FondsReserveService.Etat etat = service.etat();

        assertThat(etat.totalApports()).isEqualByComparingTo("170000");
        assertThat(etat.solde()).isEqualByComparingTo("140000");
        assertThat(etat.disponible()).isEqualByComparingTo("130000");
        assertThat(etat.contributeurs()).extracting(FondsReserveService.Contributeur::nom).containsExactly("Ama", "Entreprise");
        assertThat(etat.contributeurs().get(0).total()).isEqualByComparingTo("120000");
    }

    @Test
    @DisplayName("Une depense par le fonds ne depasse pas le disponible ; a l'approbation, son montant lui est rendu")
    void depenseLimiteeAuDisponible() {
        when(apports.total()).thenReturn(new BigDecimal("50000"));
        when(depenses.totalPar(ModeSortie.RESERVE, List.of(DepensePdv.Statut.ATTENTE_GERANT, DepensePdv.Statut.ATTENTE_ADMIN)))
                .thenReturn(new BigDecimal("20000"));

        assertThatThrownBy(() -> service.exigerDisponible(new BigDecimal("40000"), null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("30");
        assertThatCode(() -> service.exigerDisponible(new BigDecimal("20000"), new BigDecimal("20000")))
                .doesNotThrowAnyException();
    }
}
