package tg.parcauto.api.pointdevente.caisse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.materiaux.ReglementClientRepository;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regles de controle de caisse : billettage, especes attendues avec les
 * mouvements, fermeture bloquee par une commande en cours ou un ecart non
 * justifie, sortie limitee aux especes du tiroir.
 */
class CaisseServiceTest {

    private CaisseService service;
    private SessionCaisseRepository sessions;
    private MouvementCaisseRepository mouvements;
    private VenteRepository ventes;
    private CommandeRepository commandes;
    private ReglementClientRepository reglementsClients;
    private SessionCaisse session;
    private final List<MouvementCaisse> mouvementsEnregistres = new ArrayList<>();
    private SortieAttenteRepository sortiesAttente;
    private tg.parcauto.api.pointdevente.reglage.ReglagePdvService reglages;
    private final List<SortieAttente> demandes = new ArrayList<>();

    @BeforeEach
    void preparer() {
        sessions = mock(SessionCaisseRepository.class);
        mouvements = mock(MouvementCaisseRepository.class);
        ventes = mock(VenteRepository.class);
        commandes = mock(CommandeRepository.class);
        reglementsClients = mock(ReglementClientRepository.class);
        sortiesAttente = mock(SortieAttenteRepository.class);
        reglages = mock(tg.parcauto.api.pointdevente.reglage.ReglagePdvService.class);
        when(sortiesAttente.save(any())).thenAnswer(i -> {
            demandes.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(sortiesAttente.findBySessionIdOrderByDemandeeLeAscIdAsc(anyLong())).thenReturn(demandes);
        service = new CaisseService(sessions, mouvements, ventes, reglementsClients, commandes, mock(NumerotationService.class),
                mock(GestionCaisseService.class), mock(SessionTravailService.class),
                mock(tg.parcauto.api.pointdevente.materiaux.SoldeResteRepository.class),
                mock(tg.parcauto.api.pointdevente.tresorerie.RemiseCaisseRepository.class),
                sortiesAttente, depensesEnAttente(), new CircuitDepensesCaisse(reglages, sessions));
        org.mockito.Mockito.lenient().when(sortiesAttente.enAttenteSession(org.mockito.ArgumentMatchers.any()))
                .thenReturn(BigDecimal.ZERO);

        Caisse caisse = new Caisse();
        caisse.setId(3L);
        caisse.setNom("Caisse bar");
        caisse.getUtilisateurIds().add(7L);
        session = new SessionCaisse();
        session.setId(10L);
        session.setNumero("CAI-2026-0001");
        session.setCaisse(caisse);
        session.setCaissierId(1L);
        session.setCaissierNom("Gerant");
        session.setOuverteLe(Instant.now());
        session.setFondInitial(BigDecimal.valueOf(5000));

        when(sessions.findById(10L)).thenReturn(Optional.of(session));
        when(sessions.countByStatut(SessionCaisse.Statut.OUVERTE)).thenReturn(1L);
        // 12 000 F encaisses en especes sur la session.
        List<Object[]> totaux = new ArrayList<>();
        totaux.add(new Object[]{ModePaiementPdv.ESPECES, BigDecimal.valueOf(12000), 3L});
        when(ventes.totauxSession(10L)).thenReturn(totaux);
        when(mouvements.findBySessionIdOrderByDateMouvementAscIdAsc(10L)).thenReturn(mouvementsEnregistres);
        when(mouvements.save(any())).thenAnswer(i -> {
            mouvementsEnregistres.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(commandes.enCoursPourSession(anyLong(), anyBoolean())).thenReturn(List.of());
    }

    @Test
    void avecLaValidationUneSortieDuCaissierAttendEtNeTouchePasAuTiroir() {
        when(reglages.validationDepensesCaisse()).thenReturn(true);

        CaisseService.Reponse r = service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.SORTIE,
                BigDecimal.valueOf(3000), "Achat de glace"), principal(Role.CAISSIER, 7L));

        assertThat(mouvementsEnregistres).isEmpty();
        assertThat(demandes).singleElement().satisfies(d -> {
            assertThat(d.getStatut()).isEqualTo(SortieAttente.Statut.ATTENTE_GERANT);
            assertThat(d.getMontant()).isEqualByComparingTo("3000");
        });
        assertThat(r.sortiesEnAttente()).hasSize(1);
        // 5 000 de fond + 12 000 de ventes : la sortie demandee ne compte pas encore.
        assertThat(r.especesAttendues()).isEqualByComparingTo("17000");
    }

    @Test
    void avecLaValidationUneSortieDuGerantAttendLAdministrateur() {
        when(reglages.validationDepensesCaisse()).thenReturn(true);

        service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.SORTIE, BigDecimal.valueOf(3000),
                "Achat de glace"), principal(Role.GERANT, 1L));

        assertThat(mouvementsEnregistres).isEmpty();
        assertThat(demandes).singleElement().satisfies(d -> {
            assertThat(d.getStatut()).isEqualTo(SortieAttente.Statut.ATTENTE_ADMIN);
            assertThat(d.getViseePar()).isNotBlank();
        });
    }

    @Test
    void lAdministrateurSortDirectementMemeAvecLaValidation() {
        when(reglages.validationDepensesCaisse()).thenReturn(true);

        service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.SORTIE, BigDecimal.valueOf(3000),
                "Achat de glace"), principal(Role.ADMIN, 2L));

        assertThat(mouvementsEnregistres).hasSize(1);
        assertThat(demandes).isEmpty();
    }

    @Test
    void uneSortieApprouveeApresFermetureVaSurLaSessionOuverteDeLaMemeCaisse() {
        SessionCaisse fermee = new SessionCaisse();
        fermee.setId(9L);
        fermee.setNumero("CAI-2026-0000");
        fermee.setCaisse(session.getCaisse());
        fermee.setStatut(SessionCaisse.Statut.FERMEE);
        when(sessions.findByCaisseIdAndStatut(3L, SessionCaisse.Statut.OUVERTE)).thenReturn(Optional.of(session));

        MouvementCaisse m = service.sortieApprouvee(fermee, BigDecimal.valueOf(3000), "Depense DEP-1", principal(Role.ADMIN, 2L));

        assertThat(m.getSession()).isSameAs(session);
        assertThat(mouvementsEnregistres).hasSize(1);
    }

    @Test
    void lesEspecesAttenduesTiennentCompteDesEntreesEtSorties() {
        service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.ENTREE, BigDecimal.valueOf(2000),
                "Apport de monnaie"), principal(Role.CAISSIER, 7L));
        service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.SORTIE, BigDecimal.valueOf(3000),
                "Achat de glace"), principal(Role.CAISSIER, 7L));

        // 5 000 de fond + 12 000 de ventes + 2 000 - 3 000.
        CaisseService.Controle controle = service.controler(10L, comptage(10000, 1, 5000, 1));
        assertThat(controle.especesAttendues()).isEqualByComparingTo("16000");
        assertThat(controle.montantCompte()).isEqualByComparingTo("15000");
        assertThat(controle.ecart()).isEqualByComparingTo("-1000");
    }

    @Test
    void lesReglementsDeClientsEnEspecesSontAttendusDansLeTiroir() {
        // Quincaillerie : un client a credit verse 4 000 F en especes et 1 000 F par mobile money.
        List<Object[]> reglements = new ArrayList<>();
        reglements.add(new Object[]{ModePaiementPdv.ESPECES, BigDecimal.valueOf(4000), 1L});
        reglements.add(new Object[]{ModePaiementPdv.MOBILE_MONEY, BigDecimal.valueOf(1000), 1L});
        when(reglementsClients.totauxSession(10L)).thenReturn(reglements);

        // 5 000 de fond + 12 000 de ventes + 4 000 de reglement en especes.
        CaisseService.Controle controle = service.controler(10L, comptage(10000, 2, 1000, 1));
        assertThat(controle.especesAttendues()).isEqualByComparingTo("21000");
        assertThat(controle.ecart()).isEqualByComparingTo("0");
    }

    @Test
    void uneSortieNeDepassePasLesEspecesDuTiroir() {
        assertThatThrownBy(() -> service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.SORTIE,
                BigDecimal.valueOf(20000), "Retrait"), principal(Role.GERANT, 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("17000");
    }

    @Test
    void unCaissierNonAffecteNeTouchePasALaCaisse() {
        assertThatThrownBy(() -> service.mouvement(10L, new CaisseService.Mouvement(MouvementCaisse.Type.ENTREE,
                BigDecimal.valueOf(1000), "Apport"), principal(Role.CAISSIER, 99L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ne vous est pas affectee");
    }

    @Test
    void laFermetureAttendLesCommandesEnCours() {
        Commande table = new Commande();
        table.setLibelle("T2");
        when(commandes.enCoursPourSession(10L, true)).thenReturn(List.of(table));

        assertThatThrownBy(() -> service.fermer(10L, cloture(null, 10000, 1, 5000, 1, 2000, 1), principal(Role.GERANT, 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("1 commande(s) en cours").hasMessageContaining("T2");
        assertThat(session.ouverte()).isTrue();
    }

    @Test
    void unEcartNonJustifieEmpecheLaFermeture() {
        assertThatThrownBy(() -> service.fermer(10L, cloture(" ", 10000, 1, 5000, 1), principal(Role.GERANT, 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ecart de -2000 F").hasMessageContaining("justifiez");

        CaisseService.Reponse fermee = service.fermer(10L, cloture("Erreur de rendu de monnaie", 10000, 1, 5000, 1),
                principal(Role.GERANT, 1L));
        assertThat(fermee.statut()).isEqualTo(SessionCaisse.Statut.FERMEE);
        assertThat(fermee.ecart()).isEqualByComparingTo("-2000");
        assertThat(fermee.justificationEcart()).isEqualTo("Erreur de rendu de monnaie");
        assertThat(fermee.billettageFermeture()).hasSize(2);
        assertThat(fermee.fermeeParNom()).isEqualTo("gerant");
    }

    @Test
    void uneCaisseJusteSeFermeSansJustification() {
        CaisseService.Reponse fermee = service.fermer(10L, cloture(null, 10000, 1, 5000, 1, 2000, 1),
                principal(Role.GERANT, 1L));
        assertThat(fermee.ecart()).isEqualByComparingTo("0");
        assertThat(fermee.montantCompte()).isEqualByComparingTo("17000");
        assertThat(fermee.justificationEcart()).isNull();
    }

    @Test
    void uneCoupureInconnueEstRefusee() {
        assertThatThrownBy(() -> service.controler(10L, comptage(3000, 2)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Coupure inconnue : 3000 F");
    }

    // ------------------------------------------------------------ outils

    /** Billettage par paires (valeur, quantite). */
    private static List<CaisseService.LigneBillettage> billettage(int... valeursQuantites) {
        List<CaisseService.LigneBillettage> lignes = new ArrayList<>();
        for (int i = 0; i < valeursQuantites.length; i += 2) {
            lignes.add(new CaisseService.LigneBillettage(BigDecimal.valueOf(valeursQuantites[i]), valeursQuantites[i + 1]));
        }
        return lignes;
    }

    private static CaisseService.Comptage comptage(int... valeursQuantites) {
        return new CaisseService.Comptage(billettage(valeursQuantites));
    }

    private static CaisseService.Cloture cloture(String justification, int... valeursQuantites) {
        return new CaisseService.Cloture(billettage(valeursQuantites), justification, null);
    }

    private static UtilisateurPrincipal principal(Role role, Long id) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setEmail(role.name().toLowerCase() + "@test.tg");
        u.setNom(role.name().toLowerCase());
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(role));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Maquis Test", true);
    }

    private static tg.parcauto.api.pointdevente.depense.DepensePdvRepository depensesEnAttente() {
        tg.parcauto.api.pointdevente.depense.DepensePdvRepository repo = mock(tg.parcauto.api.pointdevente.depense.DepensePdvRepository.class);
        org.mockito.Mockito.lenient().when(repo.enAttenteSession(org.mockito.ArgumentMatchers.any())).thenReturn(BigDecimal.ZERO);
        return repo;
    }
}
