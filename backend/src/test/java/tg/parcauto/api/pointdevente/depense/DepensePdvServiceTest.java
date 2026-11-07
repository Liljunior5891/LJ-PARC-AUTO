package tg.parcauto.api.pointdevente.depense;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.pointdevente.caisse.Caisse;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.fournisseur.FournisseurRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepensePdvServiceTest {

    private DepensePdvService service;
    private DepensePdvRepository repository;
    private DepenseRecurrenteRepository recurrentes;
    private CaisseService caisse;
    private CategorieDepensePdv loyer;

    @BeforeEach
    void preparer() {
        repository = mock(DepensePdvRepository.class);
        CategorieDepensePdvRepository categories = mock(CategorieDepensePdvRepository.class);
        recurrentes = mock(DepenseRecurrenteRepository.class);
        caisse = mock(CaisseService.class);
        NumerotationService numerotation = mock(NumerotationService.class);
        when(numerotation.prochainNumero("DEP")).thenReturn("DEP-2026-0001");
        loyer = new CategorieDepensePdv();
        loyer.setId(4L);
        loyer.setNom("Loyer");
        when(categories.findById(4L)).thenReturn(Optional.of(loyer));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        service = new DepensePdvService(repository, categories, recurrentes, mock(FournisseurRepository.class), caisse,
                mock(StockageFichierService.class), numerotation);
    }

    @Test
    @DisplayName("Payee depuis la caisse, la depense cree une sortie d'especes dans la session choisie")
    void depenseDepuisLaCaisse() {
        SessionCaisse session = new SessionCaisse();
        session.setNumero("CAI-2026-0002");
        Caisse comptoir = new Caisse();
        comptoir.setNom("Comptoir");
        session.setCaisse(comptoir);
        MouvementCaisse sortie = new MouvementCaisse();
        sortie.setSession(session);
        when(caisse.sortie(eq(5L), any(), anyString(), any())).thenReturn(sortie);

        DepensePdvService.Reponse d = service.creer(new DepensePdvService.Requete(4L, "Loyer de novembre", "M. Kodjo",
                null, new BigDecimal("150000"), null, ModeSortie.CAISSE, 5L, null, null, null, null), gerant());

        verify(caisse).sortie(eq(5L), eq(new BigDecimal("150000.00")), anyString(), any());
        assertThat(d.sessionNumero()).isEqualTo("CAI-2026-0002");
        assertThat(d.caisseNom()).isEqualTo("Comptoir");
        assertThat(d.statut()).isEqualTo(DepensePdv.Statut.VALIDE);
    }

    @Test
    @DisplayName("Le mode caisse exige une session ouverte")
    void caisseSansSession() {
        assertThatThrownBy(() -> service.creer(new DepensePdvService.Requete(4L, "Loyer", null, null, BigDecimal.TEN,
                null, ModeSortie.CAISSE, null, null, null, null, null), gerant()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caisse");
    }

    @Test
    @DisplayName("Chaque mois non paye d'une depense recurrente est propose, les mois payes non")
    void moisAPayer() {
        YearMonth courant = YearMonth.now(ZoneId.of("Africa/Lome"));
        DepenseRecurrente modele = new DepenseRecurrente();
        modele.setId(11L);
        modele.setLibelle("Loyer boutique");
        modele.setCategorie(loyer);
        modele.setMontant(new BigDecimal("150000"));
        modele.setJourEcheance(5);
        modele.setDebut(courant.minusMonths(2).atDay(1));
        when(recurrentes.actives()).thenReturn(List.of(modele));
        when(repository.moisPayes()).thenReturn(List.<Object[]>of(new Object[]{11L, courant.minusMonths(1).toString()}));

        List<DepensePdvService.APayer> dus = service.aPayer();

        assertThat(dus).extracting(DepensePdvService.APayer::periode)
                .containsExactly(courant.minusMonths(2).toString(), courant.toString());
        assertThat(dus.get(0).enRetard()).isTrue();
        assertThat(dus.get(0).echeance()).isEqualTo(courant.minusMonths(2).atDay(5));
    }

    @Test
    @DisplayName("Un mois deja paye ne se paie pas deux fois")
    void moisDejaPaye() {
        DepenseRecurrente modele = new DepenseRecurrente();
        modele.setId(11L);
        modele.setLibelle("Loyer boutique");
        when(recurrentes.findById(11L)).thenReturn(Optional.of(modele));
        when(repository.moisDejaPaye(11L, "2026-10")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(new DepensePdvService.Requete(4L, "Loyer", null, null, BigDecimal.TEN,
                LocalDate.of(2026, 10, 30), ModeSortie.BANQUE, null, null, null, 11L, "2026-10"), gerant()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deja paye");
    }

    private static UtilisateurPrincipal gerant() {
        Utilisateur u = new Utilisateur();
        u.setId(9L);
        u.setEmail("g@test.tg");
        u.setNom("Gerant");
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(Role.GERANT));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Test", true);
    }
}
