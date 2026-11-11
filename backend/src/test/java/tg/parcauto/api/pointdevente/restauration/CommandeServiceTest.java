package tg.parcauto.api.pointdevente.restauration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.Categorie;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.stock.MouvementStockRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandeServiceTest {

    private final AtomicLong identifiants = new AtomicLong(100);

    private CommandeService service;
    private CommandeRepository repository;
    private VenteService venteService;
    private Commande commande;
    private Article poulet;
    private Article biere;
    private Article eau;

    @BeforeEach
    void preparer() {
        repository = mock(CommandeRepository.class);
        MouvementStockRepository mouvements = mock(MouvementStockRepository.class);
        when(mouvements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        StockService stockService = new StockService(mock(ArticleRepository.class), mouvements, org.mockito.Mockito.mock(tg.parcauto.api.pointdevente.lieu.LieuStockService.class));
        venteService = mock(VenteService.class);
        ArticleRepository articles = mock(ArticleRepository.class);

        poulet = article(1L, "Poulet braise", "3500", PostePreparation.CUISINE, null);
        biere = article(2L, "Biere", "800", PostePreparation.BAR, "24");
        eau = article(3L, "Eau minerale", "500", PostePreparation.AUCUN, "10");
        when(articles.findById(1L)).thenReturn(Optional.of(poulet));
        when(articles.findById(2L)).thenReturn(Optional.of(biere));
        when(articles.findById(3L)).thenReturn(Optional.of(eau));

        commande = new Commande();
        commande.setId(10L);
        commande.setNumero("CMD-2026-0001");
        commande.setLibelle("Comptoir");
        commande.setServeurId(5L);
        commande.setServeurNom("Afi");
        when(repository.detail(10L)).thenReturn(Optional.of(commande));
        // Comme Hibernate au flush : chaque nouvelle ligne recoit un identifiant.
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> numeroter(invocation.getArgument(0)));
        when(repository.findById(10L)).thenReturn(Optional.of(commande));

        when(venteService.encaisserAddition(any(), any(), any(), anyLong(), any())).thenAnswer(invocation -> {
            Vente vente = new Vente();
            vente.setId(identifiants.incrementAndGet());
            vente.setNumero("TK-" + vente.getId());
            vente.setSession(new SessionCaisse());
            vente.setModePaiement(ModePaiementPdv.ESPECES);
            List<tg.parcauto.api.pointdevente.vente.LigneVente> lignes = invocation.getArgument(0);
            lignes.forEach(vente::ajouter);
            vente.setTotal(lignes.stream().map(l -> l.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add));
            return vente;
        });

        impression = mock(tg.parcauto.api.pointdevente.impression.ImpressionService.class);
        service = new CommandeService(repository, mock(LigneCommandeRepository.class),
                mock(TableRestaurantRepository.class), articles, stockService, venteService,
                mock(tg.parcauto.api.pointdevente.caisse.SessionTravailService.class), mock(NumerotationService.class),
                impression, mock(tg.parcauto.api.pointdevente.lieu.LieuStockService.class),
                mock(tg.parcauto.api.pointdevente.caisse.PerimetreCaisse.class));
    }

    private tg.parcauto.api.pointdevente.impression.ImpressionService impression;

    @Test
    @DisplayName("L'envoi forme un bon par poste, sort le stock et rend pret ce qui se sert tel quel")
    void envoi() {
        ajouter(1L, "2");
        ajouter(2L, "3");
        ajouter(3L, "1");

        RestaurationDto.CommandeDetail detail = service.envoyer(10L, caissier());

        assertThat(detail.lignes()).extracting(RestaurationDto.LigneReponse::statut).containsExactly(
                LigneCommande.Statut.A_PREPARER, LigneCommande.Statut.A_PREPARER, LigneCommande.Statut.PRETE);
        assertThat(detail.lignes()).allSatisfy(l -> assertThat(l.envoi()).isEqualTo(1));
        assertThat(biere.getStockActuel()).isEqualByComparingTo("21");
        assertThat(eau.getStockActuel()).isEqualByComparingTo("9");
        assertThat(detail.resume().total()).isEqualByComparingTo("9900");
    }

    @Test
    @DisplayName("Un second envoi porte le numero de bon suivant et ne renvoie pas les lignes deja parties")
    void secondEnvoi() {
        ajouter(1L, "1");
        service.envoyer(10L, caissier());
        ajouter(2L, "2");

        RestaurationDto.CommandeDetail detail = service.envoyer(10L, caissier());

        assertThat(detail.lignes()).extracting(RestaurationDto.LigneReponse::envoi).containsExactly(1, 2);
        assertThat(biere.getStockActuel()).isEqualByComparingTo("22");
    }

    @Test
    @DisplayName("Une rupture de stock bloque l'envoi")
    void rupture() {
        ajouter(3L, "11");

        assertThatThrownBy(() -> service.envoyer(10L, caissier()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuffisant");
    }

    @Test
    @DisplayName("Le partage par convive scinde une ligne et laisse la commande ouverte")
    void partage() {
        ajouter(2L, "3");
        service.envoyer(10L, caissier());
        toutServir();
        Long ligneBiere = commande.getLignes().get(0).getId();

        RestaurationDto.ResultatEncaissement resultat = service.encaisser(10L, encaissement(
                List.of(new RestaurationDto.LignePaiement(ligneBiere, new BigDecimal("1")))), caissier());

        assertThat(resultat.ticket().total()).isEqualByComparingTo("800");
        assertThat(resultat.commande().resume().statut()).isEqualTo(Commande.Statut.OUVERTE);
        assertThat(resultat.commande().resume().resteAPayer()).isEqualByComparingTo("1600");
        assertThat(commande.getLignes()).hasSize(2);
    }

    @Test
    @DisplayName("Regler tout le reste ferme la commande")
    void reglementComplet() {
        ajouter(1L, "1");
        ajouter(2L, "2");
        service.envoyer(10L, caissier());
        toutServir();

        RestaurationDto.ResultatEncaissement resultat = service.encaisser(10L, encaissement(null), caissier());

        assertThat(resultat.ticket().total()).isEqualByComparingTo("5100");
        assertThat(resultat.commande().resume().statut()).isEqualTo(Commande.Statut.ENCAISSEE);
        assertThat(commande.getFermeeLe()).isNotNull();
    }

    @Test
    @DisplayName("L'addition complete est refusee tant que des articles n'ont pas ete envoyes")
    void brouillonBloqueAddition() {
        ajouter(1L, "1");
        service.envoyer(10L, caissier());
        toutServir();
        ajouter(2L, "1");

        assertThatThrownBy(() -> service.encaisser(10L, encaissement(null), caissier()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pas ete envoyes");
    }

    @Test
    @DisplayName("Une commande en preparation, ou prete mais pas servie, ne s'encaisse pas")
    void encaissementApresService() {
        ajouter(1L, "1");
        ajouter(3L, "1");
        service.envoyer(10L, caissier());

        assertThatThrownBy(() -> service.encaisser(10L, encaissement(null), caissier()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 article(s) pas encore servi(s)");

        commande.getLignes().get(0).setStatut(LigneCommande.Statut.SERVIE);
        assertThatThrownBy(() -> service.encaisser(10L, encaissement(null), caissier()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 article(s) pas encore servi(s)");

        toutServir();
        assertThat(service.encaisser(10L, encaissement(null), caissier()).commande().resume().statut())
                .isEqualTo(Commande.Statut.ENCAISSEE);
    }

    @Test
    @DisplayName("Un serveur n'annule pas une commande partie en cuisine ; un gerant le peut et le stock revient")
    void annulation() {
        ajouter(2L, "2");
        service.envoyer(10L, caissier());

        assertThatThrownBy(() -> service.annulerCommande(10L, principal(Role.SERVEUR)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gerant");

        service.annulerCommande(10L, principal(Role.GERANT));
        assertThat(commande.getStatut()).isEqualTo(Commande.Statut.ANNULEE);
        assertThat(biere.getStockActuel()).isEqualByComparingTo("24");
    }

    @Test
    @DisplayName("Le meme article sans consigne s'additionne ; avec une consigne, il forme une ligne a part")
    void regroupement() {
        ajouter(1L, "1");
        ajouter(1L, "1");
        service.ajouter(10L, new RestaurationDto.AjoutLigne(1L, BigDecimal.ONE, "bien cuit"));

        assertThat(commande.getLignes()).hasSize(2);
        assertThat(commande.getLignes().get(0).getQuantite()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("Code QR avec validation : la demande du client attend un serveur, sans toucher au stock")
    void demandeClientAValider() {
        TableRestaurant table = table();
        when(repository.ouvertes()).thenReturn(List.of());

        Commande c = service.commandeClient(table, List.of(new RestaurationDto.AjoutLigne(2L, new BigDecimal("2"), null)), true);

        assertThat(c.getLignes()).allSatisfy(l -> {
            assertThat(l.getStatut()).isEqualTo(LigneCommande.Statut.PROPOSEE);
            assertThat(l.getOrigine()).isEqualTo(LigneCommande.Origine.CLIENT);
        });
        assertThat(c.getServeurId()).isNull();
        assertThat(biere.getStockActuel()).isEqualByComparingTo("24");
        assertThat(RestaurationDto.CommandeResume.de(c).aValider()).isEqualTo(1);
        assertThat(RestaurationDto.CommandeResume.de(c).total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Code QR sans validation : la demande part aussitot en preparation")
    void demandeClientDirecte() {
        when(repository.ouvertes()).thenReturn(List.of());

        Commande c = service.commandeClient(table(), List.of(new RestaurationDto.AjoutLigne(2L, BigDecimal.ONE, null)), false);

        assertThat(c.getLignes().get(0).getStatut()).isEqualTo(LigneCommande.Statut.A_PREPARER);
        assertThat(biere.getStockActuel()).isEqualByComparingTo("23");
    }

    @Test
    @DisplayName("Le serveur valide la demande du client : elle part en preparation et il prend la table")
    void validationParLeServeur() {
        commande.setServeurId(null);
        commande.setServeurNom(CommandeService.CLIENT_QR);
        when(repository.ouvertes()).thenReturn(List.of(commande));
        commande.setTable(table());
        service.commandeClient(commande.getTable(), List.of(new RestaurationDto.AjoutLigne(1L, BigDecimal.ONE, "bien cuit")), true);

        assertThatThrownBy(() -> service.encaisser(10L, encaissement(null), caissier()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("validez-les ou refusez-les");

        RestaurationDto.CommandeDetail detail = service.validerDemandes(10L, principal(Role.SERVEUR));
        assertThat(detail.lignes()).extracting(RestaurationDto.LigneReponse::statut)
                .containsExactly(LigneCommande.Statut.A_PREPARER);
        assertThat(commande.getServeurId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Le serveur refuse la demande du client : les articles proposes disparaissent")
    void refusParLeServeur() {
        when(repository.ouvertes()).thenReturn(List.of(commande));
        commande.setTable(table());
        service.commandeClient(commande.getTable(), List.of(new RestaurationDto.AjoutLigne(2L, new BigDecimal("5"), null)), true);

        service.refuserDemandes(10L, principal(Role.SERVEUR));

        assertThat(commande.getLignes()).isEmpty();
    }

    // ----------------------------------------------------------- outils

    private static TableRestaurant table() {
        TableRestaurant t = new TableRestaurant();
        t.setId(3L);
        t.setNom("T3");
        return t;
    }

    private void ajouter(Long articleId, String quantite) {
        service.ajouter(10L, new RestaurationDto.AjoutLigne(articleId, new BigDecimal(quantite), null));
    }

    private Commande numeroter(Commande c) {
        c.getLignes().stream().filter(l -> l.getId() == null).forEach(l -> l.setId(identifiants.incrementAndGet()));
        return c;
    }

    private static RestaurationDto.Encaissement encaissement(List<RestaurationDto.LignePaiement> lignes) {
        return new RestaurationDto.Encaissement(lignes, null, ModePaiementPdv.ESPECES, null, null, null, null);
    }

    private static Article article(Long id, String nom, String prix, PostePreparation poste, String stock) {
        Categorie categorie = new Categorie();
        categorie.setNom(poste.name());
        categorie.setPoste(poste);
        Article a = new Article();
        a.setId(id);
        a.setNom(nom);
        a.setPrixVente(new BigDecimal(prix));
        a.setCategorie(categorie);
        a.setSuiviStock(stock != null);
        a.setStockActuel(stock == null ? BigDecimal.ZERO : new BigDecimal(stock));
        return a;
    }

    /** La salle a servi tout ce qui est parti en preparation. */
    private void toutServir() {
        commande.getLignes().stream().filter(l -> l.getStatut().facturable())
                .forEach(l -> l.setStatut(LigneCommande.Statut.SERVIE));
    }

    @Test
    @DisplayName("L'envoi par la caisse cree les bons d'impression ; l'envoi direct d'un code QR n'imprime rien")
    void impressionSeulementParLaCaisse() {
        ajouter(1L, "2");
        service.envoyer(10L, caissier());
        org.mockito.Mockito.verify(impression).bonsEnvoi(org.mockito.ArgumentMatchers.eq(commande),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.anyString());

        org.mockito.Mockito.clearInvocations(impression);
        when(repository.ouvertes()).thenReturn(List.of());
        service.commandeClient(table(), List.of(new RestaurationDto.AjoutLigne(2L, BigDecimal.ONE, null)), false);
        org.mockito.Mockito.verify(impression, org.mockito.Mockito.never()).bonsEnvoi(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Une categorie imprimee sans ecran est prete des l'envoi")
    void imprimeeSansEcranPrete() {
        poulet.getCategorie().setId(99L);
        when(impression.categoriesImprimeesSansEcran()).thenReturn(java.util.Set.of(99L));
        ajouter(1L, "1");
        service.envoyer(10L, caissier());
        assertThat(commande.getLignes().get(0).getStatut()).isEqualTo(LigneCommande.Statut.PRETE);
    }

    private static UtilisateurPrincipal caissier() {
        return principal(Role.CAISSIER);
    }

    private static UtilisateurPrincipal principal(Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(7L);
        u.setEmail(role.name().toLowerCase() + "@test.tg");
        u.setNom(role.getLibelle());
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(role));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Maquis Test", true);
    }
}
