package tg.parcauto.api.pointdevente.associe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.pointdevente.reserve.FondsReserveService;
import tg.parcauto.api.pointdevente.tresorerie.TresorerieService;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompteAssocieServiceTest {

    private AssocieService associes;
    private PartMensuelleRepository parts;
    private DepotAssocieRepository depots;
    private FondsReserveService fonds;
    private TresorerieService tresorerie;
    private CompteAssocieService service;
    private final Utilisateur ama = utilisateur(7L, Role.ASSOCIE);

    @BeforeEach
    void preparer() {
        associes = mock(AssocieService.class);
        parts = mock(PartMensuelleRepository.class);
        depots = mock(DepotAssocieRepository.class);
        fonds = mock(FondsReserveService.class);
        tresorerie = mock(TresorerieService.class);
        ReglagePdvService reglages = mock(ReglagePdvService.class);
        NumerotationService numerotation = mock(NumerotationService.class);
        when(numerotation.prochainNumero(anyString())).thenReturn("VAS-2026-0001");
        when(associes.compteAssocie(7L)).thenReturn(ama);
        when(depots.save(any())).thenAnswer(i -> i.getArgument(0));
        // Mois de novembre 2026 fige : 100 000 F dus a l'associe 7.
        PartMensuelle octobre = new PartMensuelle();
        octobre.setAssocieId(7L);
        octobre.setMois(LocalDate.of(2026, 10, 1));
        octobre.setPart(new BigDecimal("100000"));
        when(parts.findAllByOrderByMoisAsc()).thenReturn(List.of(octobre));
        // Aucune vente : rien de nouveau a figer.
        VenteRepository ventes = mock(VenteRepository.class);
        service = new CompteAssocieService(associes, parts, mock(ClotureAssociesRepository.class),
                mock(ClotureParts.class), depots, fonds, reglages, tresorerie, numerotation,
                mock(StockageFichierService.class), ventes);
    }

    private CompteAssocieService.DepotRequete depot(String montant, DepotAssocie.Source source) {
        return new CompteAssocieService.DepotRequete(7L, new BigDecimal(montant), source, "Ecobank", "BRD-1",
                LocalDate.now().minusDays(1), "octobre 2026", null);
    }

    @Test
    @DisplayName("Le gerant ne verse pas plus que ce qui reste du sur les mois clos, depots engages deduits")
    void versementLimiteAuResteAVerser() {
        when(depots.total(eq(7L), any())).thenReturn(new BigDecimal("60000"));
        UtilisateurPrincipal gerant = principal(utilisateur(3L, Role.GERANT));

        assertThatThrownBy(() -> service.creerDepot(depot("50000", DepotAssocie.Source.BANQUE), gerant))
                .isInstanceOf(BusinessException.class).hasMessageContaining("40");
        CompteAssocieService.Depot d = service.creerDepot(depot("40000", DepotAssocie.Source.BANQUE), gerant);

        assertThat(d.statut()).isEqualTo(DepotAssocie.Statut.EN_ATTENTE);
        assertThat(d.associeNom()).isEqualTo(ama.nomComplet());
    }

    @Test
    @DisplayName("Un versement depuis le coffre exige que le coffre du gerant le couvre")
    void versementDepuisLeCoffre() {
        when(depots.total(eq(7L), any())).thenReturn(BigDecimal.ZERO);
        UtilisateurPrincipal gerant = principal(utilisateur(3L, Role.GERANT));
        when(tresorerie.exigerCoffre(any(), any())).thenReturn(3L);

        CompteAssocieService.Depot d = service.creerDepot(depot("30000", DepotAssocie.Source.COFFRE), gerant);

        assertThat(d.source()).isEqualTo(DepotAssocie.Source.COFFRE);
        verify(tresorerie).exigerCoffre(gerant, new BigDecimal("30000.00"));
    }

    @Test
    @DisplayName("Seul l'associe concerne valide son depot, et seulement bordereau joint")
    void validationParLAssocie() {
        DepotAssocie d = new DepotAssocie();
        d.setAssocieId(7L);
        d.setNumero("VAS-1");
        d.setStatut(DepotAssocie.Statut.EN_ATTENTE);
        when(depots.findById(1L)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.valider(1L, principal(utilisateur(8L, Role.ASSOCIE))))
                .isInstanceOf(tg.parcauto.api.common.ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.valider(1L, principal(ama)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("bordereau");

        d.setPieceChemin("pdv-associes/1/b.pdf");
        service.valider(1L, principal(ama));
        assertThat(d.getStatut()).isEqualTo(DepotAssocie.Statut.VALIDE);
    }

    @Test
    @DisplayName("Un depot refuse repart au gerant avec le motif ; corrige, il revient en attente")
    void refusPuisCorrection() {
        DepotAssocie d = new DepotAssocie();
        d.setAssocieId(7L);
        d.setAssocieNom(ama.nomComplet());
        d.setNumero("VAS-1");
        d.setMontant(new BigDecimal("40000"));
        d.setSource(DepotAssocie.Source.BANQUE);
        d.setAuteurId(3L);
        d.setStatut(DepotAssocie.Statut.EN_ATTENTE);
        when(depots.findById(1L)).thenReturn(Optional.of(d));
        when(depots.total(eq(7L), any())).thenReturn(new BigDecimal("40000"));

        service.refuser(1L, "Montant different du bordereau", principal(ama));
        assertThat(d.getStatut()).isEqualTo(DepotAssocie.Statut.REFUSE);
        assertThat(d.getMotifRefus()).isEqualTo("Montant different du bordereau");

        // Un autre gerant ne corrige pas le depot ; son auteur, si.
        assertThatThrownBy(() -> service.corriger(1L, depot("45000", DepotAssocie.Source.BANQUE),
                principal(utilisateur(4L, Role.GERANT)))).isInstanceOf(BusinessException.class);
        service.corriger(1L, depot("45000", DepotAssocie.Source.BANQUE), principal(utilisateur(3L, Role.GERANT)));
        assertThat(d.getStatut()).isEqualTo(DepotAssocie.Statut.EN_ATTENTE);
        assertThat(d.getMontant()).isEqualByComparingTo("45000");
        assertThat(d.getMotifRefus()).isNull();
    }

    @Test
    @DisplayName("L'associe n'apporte au fonds que ce que contient son sous-compte (depots valides - apports)")
    void apportLimiteAuSolde() {
        when(depots.total(7L, Set.of(DepotAssocie.Statut.VALIDE))).thenReturn(new BigDecimal("30000"));
        when(fonds.totalApportsDe(7L)).thenReturn(new BigDecimal("10000"));

        assertThatThrownBy(() -> service.apporter(new CompteAssocieService.ApportRequete(new BigDecimal("25000"), null),
                principal(ama))).isInstanceOf(BusinessException.class).hasMessageContaining("20");
        verify(fonds, never()).apportAssocie(any(), any(), any(), any(), any());

        service.apporter(new CompteAssocieService.ApportRequete(new BigDecimal("20000"), "Loyer"), principal(ama));
        verify(fonds).apportAssocie(eq(7L), eq(ama.nomComplet()), eq(new BigDecimal("20000.00")), eq("Loyer"), any());
    }

    private static Utilisateur utilisateur(Long id, Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setEmail("u" + id + "@test.tg");
        u.setNom("Compte " + id);
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(role));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return u;
    }

    private static UtilisateurPrincipal principal(Utilisateur u) {
        return new UtilisateurPrincipal(u, "Test", true);
    }
}
