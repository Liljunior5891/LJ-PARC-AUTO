package tg.parcauto.api.pointdevente.reserve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.depense.DepensePdv;
import tg.parcauto.api.pointdevente.depense.DepensePdvRepository;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.pointdevente.vente.MargeVentes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FondsReserveServiceTest {

    private PeriodeFondsReserveRepository periodes;
    private DepensePdvRepository depenses;
    private MargeVentes marges;
    private ReglagePdvService reglages;
    private MoisFondsReserveRepository moisClos;
    private ClotureFondsReserve cloture;
    /** Mois figes par le service, relus ensuite comme en base. */
    private final List<MoisFondsReserve> figes = new ArrayList<>();
    private FondsReserveService service;
    private final LocalDate aujourdhui = LocalDate.now(ZoneId.of("Africa/Lome"));
    private final LocalDate debutMois = aujourdhui.withDayOfMonth(1);

    /** Marge et depenses de chaque jour simule. */
    private final Map<LocalDate, MargeVentes.Marge> ventes = new HashMap<>();
    private final List<Object[]> frais = new ArrayList<>();

    @BeforeEach
    void preparer() {
        periodes = mock(PeriodeFondsReserveRepository.class);
        depenses = mock(DepensePdvRepository.class);
        marges = mock(MargeVentes.class);
        reglages = mock(ReglagePdvService.class);
        when(reglages.lire()).thenReturn(new ReglagePdvService.Reglages(false, false, BigDecimal.ZERO, false, 5,
                BigDecimal.TEN, true, BigDecimal.TEN));
        when(marges.parJour(any(), any())).thenReturn(ventes);
        when(depenses.totauxParJourHors(any(), any(), eq(ModeSortie.RESERVE))).thenReturn(frais);
        when(depenses.totalPar(eq(ModeSortie.RESERVE), any())).thenReturn(BigDecimal.ZERO);
        moisClos = mock(MoisFondsReserveRepository.class);
        cloture = mock(ClotureFondsReserve.class);
        when(moisClos.findAllByOrderByDebutAsc()).thenAnswer(i -> new ArrayList<>(figes));
        org.mockito.Mockito.doAnswer(i -> { figes.addAll(i.getArgument(0)); return null; }).when(cloture).figer(any());
        service = new FondsReserveService(periodes, moisClos, cloture, depenses, marges, reglages);
    }

    /** Un jour : ventes, cout d'achat, depenses (net = ventes - cout - depenses). */
    private void jour(LocalDate j, String chiffre, String cout, String depense) {
        ventes.put(j, new MargeVentes.Marge(new BigDecimal(chiffre), new BigDecimal(cout)));
        frais.add(new Object[]{j, new BigDecimal(depense)});
    }

    private static PeriodeFondsReserve periode(LocalDate debut, LocalDate fin, String pourcent) {
        PeriodeFondsReserve p = new PeriodeFondsReserve();
        p.setDebut(debut);
        p.setFin(fin);
        p.setPourcent(new BigDecimal(pourcent));
        return p;
    }

    @Test
    @DisplayName("Le fonds recoit le pourcentage du benefice net de chaque mois, au taux de sa periode")
    void dotationsParPeriode() {
        // Mois precedent a 10 % (net 300 000), mois en cours a 20 % (net 300 000).
        jour(debutMois.minusMonths(1), "1000000", "600000", "100000");
        jour(debutMois, "1000000", "600000", "100000");
        when(periodes.findAllByOrderByDebutAscIdAsc()).thenReturn(List.of(
                periode(debutMois.minusMonths(1), debutMois.minusDays(1), "10"),
                periode(debutMois, null, "20")));

        FondsReserveService.Etat etat = service.etat();

        assertThat(etat.dotations()).hasSize(2);
        assertThat(etat.dotations().get(0).enCours()).isTrue();
        assertThat(etat.dotations().get(0).dotation()).isEqualByComparingTo("60000");
        assertThat(etat.dotations().get(1).dotation()).isEqualByComparingTo("30000");
        // Seul le mois termine est verse (et sort du compte de l'entreprise) ; le mois en cours est estime.
        assertThat(etat.totalDotations()).isEqualByComparingTo("30000");
        assertThat(etat.solde()).isEqualByComparingTo("30000");
        assertThat(etat.moisEnCours()).isEqualByComparingTo("60000");
        assertThat(etat.disponible()).isEqualByComparingTo("30000");
        // Le mois termine est fige une fois pour toutes.
        assertThat(figes).hasSize(1);
        assertThat(figes.get(0).getVerse()).isEqualByComparingTo("30000");
        assertThat(etat.depuis()).isEqualTo(debutMois.minusMonths(1));
    }

    @Test
    @DisplayName("Une depense ne sort du fonds que dans la limite des mois termines, attentes comprises")
    void limiteDuDisponible() {
        // Mois precedent : 30 000 verses ; mois en cours : 30 000 non encore utilisables.
        jour(debutMois.minusMonths(1), "1000000", "600000", "100000");
        jour(debutMois, "1000000", "600000", "100000");
        when(periodes.findAllByOrderByDebutAscIdAsc()).thenReturn(List.of(periode(debutMois.minusMonths(1), null, "10")));
        // 10 000 deja payes, 5 000 en attente : 15 000 disponibles.
        when(depenses.totalPar(eq(ModeSortie.RESERVE), eq(List.of(DepensePdv.Statut.VALIDE)))).thenReturn(new BigDecimal("10000"));
        when(depenses.totalPar(eq(ModeSortie.RESERVE), eq(List.of(DepensePdv.Statut.ATTENTE_GERANT, DepensePdv.Statut.ATTENTE_ADMIN))))
                .thenReturn(new BigDecimal("5000"));

        assertThatCode(() -> service.exigerDisponible(new BigDecimal("15000"), null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.exigerDisponible(new BigDecimal("15001"), null)).isInstanceOf(BusinessException.class);
        // A l'approbation de la depense de 5 000 en attente, elle ne se bloque pas elle-meme.
        assertThatCode(() -> service.exigerDisponible(new BigDecimal("5000"), new BigDecimal("5000"))).doesNotThrowAnyException();
        assertThat(service.etat().disponible()).isEqualByComparingTo("15000");
        // Chaque verification passe par le verrou du reglage.
        verify(reglages, org.mockito.Mockito.times(3)).verrouillerFondsReserve();
    }

    @Test
    @DisplayName("Le benefice du mois en cours ne peut pas etre depense avant la fin du mois")
    void moisEnCoursNonDisponible() {
        jour(aujourdhui, "1000000", "600000", "100000");
        when(periodes.findAllByOrderByDebutAscIdAsc()).thenReturn(List.of(periode(aujourdhui, null, "10")));

        assertThat(service.etat().solde()).isEqualByComparingTo("0");
        assertThat(service.etat().moisEnCours()).isEqualByComparingTo("30000");
        assertThat(service.etat().disponible()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.exigerDisponible(new BigDecimal("1"), null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Une reprise ne depasse pas ce qui a ete verse : journaux et compte de l'entreprise d'accord")
    void repriseLimiteeAuVerse() {
        // Mois -2 en perte (-500 000 -> -50 000), mois -1 en benefice (+300 000 -> +30 000), mois en cours en perte.
        LocalDate m2 = debutMois.minusMonths(2);
        jour(m2, "100000", "600000", "0");
        jour(debutMois.minusMonths(1), "1000000", "600000", "100000");
        jour(debutMois, "100000", "300000", "0");
        when(periodes.findAllByOrderByDebutAscIdAsc()).thenReturn(List.of(periode(m2, null, "10")));

        List<FondsReserveService.Dotation> lignes = new ArrayList<>(service.etat().dotations());
        java.util.Collections.reverse(lignes);

        assertThat(lignes).extracting(FondsReserveService.Dotation::dotation)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("-50000"), new BigDecimal("30000"), new BigDecimal("-20000"));
        // Rien n'avait ete verse au premier mois : aucune reprise ; le mois en cours reprend 20 000 sur 30 000.
        assertThat(lignes).extracting(FondsReserveService.Dotation::verse)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("30000"), new BigDecimal("-20000"));
        // Verse par les mois clos : 30 000 ; la perte estimee du mois en cours reduit deja le disponible.
        assertThat(service.totalDotations()).isEqualByComparingTo("30000");
        assertThat(service.etat().disponible()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Un mois fige ne change plus, meme si ses ventes sont annulees apres coup")
    void moisFigeInchange() {
        LocalDate moisPasse = debutMois.minusMonths(1);
        jour(moisPasse, "1000000", "600000", "100000");
        when(periodes.findAllByOrderByDebutAscIdAsc()).thenReturn(List.of(periode(moisPasse, null, "10")));
        assertThat(service.totalDotations()).isEqualByComparingTo("30000");

        // Une grosse vente du mois passe est annulee : le versement deja fait reste.
        ventes.put(moisPasse, new MargeVentes.Marge(new BigDecimal("100000"), new BigDecimal("600000")));
        assertThat(service.totalDotations()).isEqualByComparingTo("30000");
        assertThat(figes).hasSize(1);
        org.mockito.Mockito.verify(cloture, org.mockito.Mockito.times(1)).figer(any());
    }

    @Test
    @DisplayName("Un cumul en perte ne verse rien au fonds")
    void perteSansDotationNegative() {
        jour(aujourdhui, "1000000", "1200000", "0");
        when(periodes.findAllByOrderByDebutAscIdAsc()).thenReturn(List.of(periode(aujourdhui, null, "10")));

        assertThat(service.totalDotations()).isEqualByComparingTo("0");
    }
}
