package tg.parcauto.api.pointdevente.materiaux;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regles du credit en quincaillerie : activation, droit du caissier, acompte
 * minimum, plafond, retard de paiement, et imputation des reglements.
 */
class CompteClientServiceTest {

    private CompteClientService service;
    private ClientPdvRepository clients;
    private VenteRepository ventes;
    private SessionTravailService sessions;
    private ReglageMateriaux reglage;
    private ClientPdv client;
    private final List<Object[]> encours = new ArrayList<>();

    @BeforeEach
    void preparer() {
        clients = mock(ClientPdvRepository.class);
        ventes = mock(VenteRepository.class);
        sessions = mock(SessionTravailService.class);
        ReglageMateriauxService reglages = mock(ReglageMateriauxService.class);
        NumerotationService numerotation = mock(NumerotationService.class);
        when(numerotation.prochainNumero("REG")).thenReturn("REG-2026-0001");
        service = new CompteClientService(clients, mock(ReglementClientRepository.class), ventes, reglages,
                sessions, numerotation, mock(SoldeResteRepository.class));

        reglage = new ReglageMateriaux();
        reglage.setPlafondDefaut(BigDecimal.valueOf(500_000));
        when(reglages.exiger()).thenReturn(reglage);
        when(reglages.courant()).thenReturn(reglage);

        client = new ClientPdv();
        client.setId(5L);
        client.setNom("BTP Kossi");
        when(clients.findById(5L)).thenReturn(Optional.of(client));
        when(clients.encours(anyList())).thenReturn(encours);
    }

    @Test
    @DisplayName("Sous le plafond, le caissier vend a credit ; l'echeance suit le delai des reglages")
    void creditSousLePlafond() {
        LocalDate echeance = service.autoriserCredit(client, montant(300_000), montant(50_000), principal(Role.CAISSIER));
        assertThat(echeance).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    @DisplayName("Le delai propre au client l'emporte sur celui des reglages")
    void delaiDuClient() {
        client.setDelaiPaiementJours(60);
        assertThat(service.autoriserCredit(client, montant(100_000), montant(0), principal(Role.CAISSIER)))
                .isEqualTo(LocalDate.now().plusDays(60));
    }

    @Test
    @DisplayName("Au-dela du plafond : refuse au caissier, accorde au gerant si les reglages le permettent")
    void plafondDepasse() {
        encours.add(new Object[]{5L, montant(400_000), montant(0)});

        assertThatThrownBy(() -> service.autoriserCredit(client, montant(200_000), montant(0), principal(Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Plafond")
                .hasMessageContaining("100000 F disponibles");
        assertThat(service.autoriserCredit(client, montant(200_000), montant(0), principal(Role.GERANT))).isNotNull();

        reglage.setDepassementGerant(false);
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(200_000), montant(0), principal(Role.GERANT)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un plafond a zero sur la fiche interdit tout credit, malgre le plafond par defaut")
    void plafondNulSurLaFiche() {
        client.setPlafondCredit(BigDecimal.ZERO);
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(10_000), montant(0), principal(Role.CAISSIER)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un client en retard de paiement n'obtient pas de nouveau credit")
    void clientEnRetard() {
        encours.add(new Object[]{5L, montant(20_000), montant(20_000)});
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(10_000), montant(0), principal(Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("retard");

        reglage.setBloquerSiRetard(false);
        assertThat(service.autoriserCredit(client, montant(10_000), montant(0), principal(Role.CAISSIER))).isNotNull();
    }

    @Test
    @DisplayName("Reglages : credit desactive, credit reserve au gerant, acompte minimum")
    void reglagesDuCredit() {
        reglage.setAcompteMinimum(30);
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(100_000), montant(20_000), principal(Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("30000 F");
        assertThat(service.autoriserCredit(client, montant(100_000), montant(30_000), principal(Role.CAISSIER))).isNotNull();

        reglage.setCreditCaissier(false);
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(100_000), montant(30_000), principal(Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gerant");

        reglage.setCreditActif(false);
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(100_000), montant(30_000), principal(Role.ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("desactivee");
    }

    @Test
    @DisplayName("Un acompte qui couvre le total n'est pas une vente a credit")
    void acompteTotal() {
        assertThatThrownBy(() -> service.autoriserCredit(client, montant(10_000), montant(10_000), principal(Role.GERANT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("comptant");
    }

    @Test
    @DisplayName("Un versement solde les ventes de la plus ancienne a la plus recente, sans depasser le du")
    void imputationDesReglements() {
        Vente ancienne = vente("COM-1", 30_000);
        Vente recente = vente("COM-2", 50_000);
        when(ventes.duesDuClient(5L)).thenReturn(List.of(ancienne, recente));
        SessionCaisse session = new SessionCaisse();
        session.setNumero("CAI-1");
        when(sessions.exiger(any(), any())).thenReturn(session);

        MateriauxDto.RecuReglement recu = service.regler(5L, new MateriauxDto.ReglementRequete(montant(40_000),
                ModePaiementPdv.ESPECES, montant(50_000), null, null, null), principal(Role.CAISSIER));

        assertThat(ancienne.getResteDu()).isEqualByComparingTo("0");
        assertThat(recente.getResteDu()).isEqualByComparingTo("40000");
        assertThat(recu.imputations()).hasSize(2);
        assertThat(recu.monnaieRendue()).isEqualByComparingTo("10000");
        assertThat(recu.soldeRestant()).isEqualByComparingTo("40000");

        assertThatThrownBy(() -> service.regler(5L, new MateriauxDto.ReglementRequete(montant(50_000),
                ModePaiementPdv.ESPECES, null, null, null, null), principal(Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("depasse");
    }

    // ------------------------------------------------------------ outils

    private static Vente vente(String numero, long reste) {
        Vente v = new Vente();
        v.setNumero(numero);
        v.setDateVente(Instant.now());
        v.setTotal(montant(reste));
        v.setResteDu(montant(reste));
        return v;
    }

    private static BigDecimal montant(long valeur) {
        return BigDecimal.valueOf(valeur).setScale(2);
    }

    private static UtilisateurPrincipal principal(Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail(role.name().toLowerCase() + "@test.tg");
        u.setNom(role.name().toLowerCase());
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(role));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Quincaillerie Test", true);
    }
}
