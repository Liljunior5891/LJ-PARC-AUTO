package tg.parcauto.api.pointdevente.lieu;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.stock.MouvementStock;
import tg.parcauto.api.pointdevente.stock.MouvementStockRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Le total de l'article reste la somme des lieux et de ce qui est en transit ;
 * sans entrepot, rien ne change.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockParLieuTest {

    @Mock ArticleRepository articles;
    @Mock MouvementStockRepository mouvements;
    @Mock LieuStockService lieux;
    @Mock TransfertRepository transferts;
    @Mock NumerotationService numerotation;
    @Mock UtilisateurPrincipal auteur;
    @Mock UtilisateurPrincipal magasinier;

    private final Map<Long, StockLieu> lignes = new HashMap<>();
    private final List<MouvementStock> traces = new ArrayList<>();
    private StockService stock;
    private TransfertService service;
    private Article ciment;
    private LieuStock agoe;
    private LieuStock depot;

    @BeforeEach
    void preparer() {
        stock = new StockService(articles, mouvements, lieux);
        service = new TransfertService(transferts, articles, lieux, stock, numerotation);
        agoe = lieu(1L, "Agoe", LieuStock.Type.MAGASIN);
        depot = lieu(2L, "Depot", LieuStock.Type.ENTREPOT);
        ciment = new Article();
        ciment.setId(10L);
        ciment.setNom("Ciment");
        ciment.setStockActuel(new BigDecimal("500"));
        place(agoe, "100");
        place(depot, "400");

        when(lieux.actif()).thenReturn(true);
        when(lieux.cible(any())).thenAnswer(i -> i.getArgument(0) == null ? agoe : i.getArgument(0));
        when(lieux.ligne(any(), any())).thenAnswer(i -> lignes.get(((LieuStock) i.getArgument(0)).getId()));
        when(lieux.demande(anyLong())).thenAnswer(i -> ((Long) i.getArgument(0)) == 1L ? agoe : depot);
        when(lieux.accede(any(), any())).thenReturn(true);
        when(lieux.disponibilites(any())).thenReturn(List.of(new LieuStockService.Disponibilite(10L, 2L, "Depot",
                LieuStock.Type.ENTREPOT, new BigDecimal("400"), BigDecimal.ZERO, new BigDecimal("400"))));
        when(mouvements.save(any())).thenAnswer(i -> {
            traces.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(articles.findById(10L)).thenReturn(Optional.of(ciment));
        when(numerotation.prochainNumero("TR")).thenReturn("TR-1");
        when(transferts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(auteur.getNomComplet()).thenReturn("Gerant");
        org.mockito.Mockito.doReturn(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_GERANT")))
                .when(auteur).getAuthorities();
        when(magasinier.getNomComplet()).thenReturn("Magasinier");
        org.mockito.Mockito.doReturn(List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_MAGASINIER")))
                .when(magasinier).getAuthorities();
    }

    private static LieuStock lieu(Long id, String nom, LieuStock.Type type) {
        LieuStock l = new LieuStock();
        l.setId(id);
        l.setNom(nom);
        l.setType(type);
        return l;
    }

    private void place(LieuStock lieu, String quantite) {
        StockLieu s = new StockLieu();
        s.setLieu(lieu);
        s.setArticle(ciment);
        s.setStock(new BigDecimal(quantite));
        lignes.put(lieu.getId(), s);
    }

    private StockLieu de(LieuStock lieu) {
        return lignes.get(lieu.getId());
    }

    private Transfert transfertValide(String quantite) {
        TransfertService.Reponse r = service.demander(new TransfertService.Demande(2L, 1L, null,
                List.of(new TransfertService.LigneDemande(10L, new BigDecimal(quantite)))), auteur);
        assertThat(r.statut()).isEqualTo(Transfert.Statut.DEMANDE);
        Transfert[] capture = new Transfert[1];
        org.mockito.Mockito.verify(transferts).save(org.mockito.ArgumentMatchers.argThat(t -> {
            capture[0] = t;
            return true;
        }));
        Transfert t = capture[0];
        t.setId(70L);
        long i = 700;
        for (LigneTransfert l : t.getLignes()) {
            l.setId(i++);
        }
        when(transferts.findById(70L)).thenReturn(Optional.of(t));
        service.valider(70L, null, auteur);
        return t;
    }

    @Test
    void uneVenteNeSortQueDuLieuEtLeTotalSuit() {
        stock.appliquer(ciment, agoe, TypeMouvementStock.VENTE, new BigDecimal("30"), null, "Vente", 1L);

        assertThat(de(agoe).getStock()).isEqualByComparingTo("70");
        assertThat(de(depot).getStock()).isEqualByComparingTo("400");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("470");
        assertThat(traces.get(0).getLieu()).isEqualTo(agoe);
        assertThat(traces.get(0).getStockApres()).isEqualByComparingTo("470");
        assertThat(traces.get(0).getStockLieuApres()).isEqualByComparingTo("70");
    }

    @Test
    void reserverAuDelaDuLieuEstRefuseEtIndiqueLesAutresLieux() {
        assertThatThrownBy(() -> stock.reserver(ciment, agoe, new BigDecimal("150")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("a Agoe")
                .hasMessageContaining("Depot : 400");
        assertThat(de(agoe).getReserve()).isEqualByComparingTo("0");
    }

    @Test
    void laRemiseSortDuLieuEtDiminueSaReserve() {
        stock.reserver(ciment, depot, new BigDecimal("100"));
        assertThat(de(depot).disponible()).isEqualByComparingTo("300");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("100");

        stock.livrer(ciment, depot, new BigDecimal("60"), "BL", 1L);

        assertThat(de(depot).getStock()).isEqualByComparingTo("340");
        assertThat(de(depot).getReserve()).isEqualByComparingTo("40");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("440");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("40");
    }

    @Test
    void changerDeLieuDeplaceLaReservationSiLeStockYEst() {
        stock.reserver(ciment, depot, new BigDecimal("90"));
        stock.deplacerReservation(ciment, depot, agoe, new BigDecimal("90"));

        assertThat(de(depot).getReserve()).isEqualByComparingTo("0");
        assertThat(de(agoe).getReserve()).isEqualByComparingTo("90");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("90");

        stock.reserver(ciment, depot, new BigDecimal("90"));
        assertThatThrownBy(() -> stock.deplacerReservation(ciment, depot, agoe, new BigDecimal("90")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void annulerUneVenteRemetChaqueQuantiteLaOuElleEstSortie() {
        stock.appliquer(ciment, agoe, TypeMouvementStock.VENTE, new BigDecimal("10"), null, "Vente", 5L);
        stock.livrer(ciment, depot, new BigDecimal("20"), "BL", 5L);
        when(mouvements.findByVenteId(5L)).thenReturn(new ArrayList<>(traces));

        stock.retournerVente(5L, "Annulation");

        assertThat(de(agoe).getStock()).isEqualByComparingTo("100");
        assertThat(de(depot).getStock()).isEqualByComparingTo("400");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("500");
    }

    @Test
    void uneRemiseAnnuleeRevientAuLieuDeSortieEtResteReserveAuLieuActuel() {
        stock.reserver(ciment, agoe, new BigDecimal("10"));
        stock.annulerLivraison(ciment, depot, agoe, new BigDecimal("5"), "Annulation", 1L);

        assertThat(de(depot).getStock()).isEqualByComparingTo("405");
        assertThat(de(agoe).getReserve()).isEqualByComparingTo("15");
        assertThat(de(depot).getReserve()).isEqualByComparingTo("0");
    }

    @Test
    void lInventaireCorrigeLeLieuEtLeTotalDeLEcart() {
        stock.appliquer(ciment, agoe, TypeMouvementStock.INVENTAIRE, new BigDecimal("97"), null, "Inventaire", null);

        assertThat(de(agoe).getStock()).isEqualByComparingTo("97");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("497");
        assertThat(traces.get(0).getQuantite()).isEqualByComparingTo("-3");
    }

    @Test
    void enTransitLaMarchandiseQuitteLeLieuMaisPasLeTotal() {
        Transfert t = transfertValide("200");
        service.expedier(70L, new TransfertService.Expedition("Kossi", null), auteur);

        assertThat(de(depot).getStock()).isEqualByComparingTo("200");
        assertThat(de(agoe).getStock()).isEqualByComparingTo("100");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("500");
        assertThat(t.getStatut()).isEqualTo(Transfert.Statut.EXPEDIE);

        service.receptionner(70L, null, auteur);

        assertThat(de(agoe).getStock()).isEqualByComparingTo("300");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("500");
        assertThat(t.getStatut()).isEqualTo(Transfert.Statut.RECU);
    }

    @Test
    void onNExpedieNiSansValidationNiLaMarchandiseReservee() {
        Transfert t = transfertValide("350");
        stock.reserver(ciment, depot, new BigDecimal("100"));
        assertThatThrownBy(() -> service.expedier(70L, null, auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("insuffisant");

        t.setStatut(Transfert.Statut.DEMANDE);
        assertThatThrownBy(() -> service.expedier(70L, null, auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("valide");
    }

    @Test
    void unEcartResteEnTransitJusquALaDecisionDuGerant() {
        Transfert t = transfertValide("200");
        Long ligne = t.getLignes().get(0).getId();
        service.expedier(70L, null, auteur);
        service.receptionner(70L, new TransfertService.Reception(List.of(
                new TransfertService.LigneSaisie(ligne, new BigDecimal("198")))), auteur);

        assertThat(t.getStatut()).isEqualTo(Transfert.Statut.ECART);
        assertThat(de(agoe).getStock()).isEqualByComparingTo("298");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("500");

        service.traiterEcart(70L, new TransfertService.DecisionEcart(LigneTransfert.DecisionEcart.PERTE, "Sacs perces"), auteur);

        assertThat(t.getStatut()).isEqualTo(Transfert.Statut.RECU);
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("498");
        assertThat(de(agoe).getStock()).isEqualByComparingTo("298");
        assertThat(de(depot).getStock()).isEqualByComparingTo("200");
    }

    @Test
    void unEcartPeutRevenirAuDepart() {
        Transfert t = transfertValide("200");
        Long ligne = t.getLignes().get(0).getId();
        service.expedier(70L, null, auteur);
        service.receptionner(70L, new TransfertService.Reception(List.of(
                new TransfertService.LigneSaisie(ligne, new BigDecimal("150")))), auteur);
        service.traiterEcart(70L, new TransfertService.DecisionEcart(LigneTransfert.DecisionEcart.RETOUR_SOURCE, null), auteur);

        assertThat(de(depot).getStock()).isEqualByComparingTo("250");
        assertThat(de(agoe).getStock()).isEqualByComparingTo("250");
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("500");
    }

    private Transfert demandeDuMagasinier() {
        service.demander(new TransfertService.Demande(2L, 1L, null,
                List.of(new TransfertService.LigneDemande(10L, new BigDecimal("50")))), magasinier);
        Transfert[] capture = new Transfert[1];
        org.mockito.Mockito.verify(transferts).save(org.mockito.ArgumentMatchers.argThat(t -> {
            capture[0] = t;
            return true;
        }));
        capture[0].setId(71L);
        when(transferts.findById(71L)).thenReturn(Optional.of(capture[0]));
        return capture[0];
    }

    @Test
    void unMagasinierNeChoisitPasLeLieuDeDepart() {
        Transfert t = demandeDuMagasinier();

        assertThat(t.getSource()).isNull();
        assertThat(t.getDestination()).isEqualTo(agoe);
        assertThat(t.getStatut()).isEqualTo(Transfert.Statut.DEMANDE);
    }

    @Test
    void unMagasinierNeDemandeQuePourSesLieux() {
        when(lieux.accede(magasinier, agoe)).thenReturn(false);

        assertThatThrownBy(() -> service.demander(new TransfertService.Demande(null, 1L, null,
                List.of(new TransfertService.LigneDemande(10L, BigDecimal.ONE))), magasinier))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pas affecte");
    }

    @Test
    void leGerantChoisitLeDepartEnValidant() {
        Transfert t = demandeDuMagasinier();

        assertThatThrownBy(() -> service.valider(71L, null, auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("lieu de depart");
        assertThatThrownBy(() -> service.valider(71L, 1L, auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("identiques");

        service.valider(71L, 2L, auteur);

        assertThat(t.getSource()).isEqualTo(depot);
        assertThat(t.getStatut()).isEqualTo(Transfert.Statut.VALIDE);
    }

    @Test
    void uneDemandeSansDepartNeSExpediePas() {
        demandeDuMagasinier();

        assertThatThrownBy(() -> service.expedier(71L, null, auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("valide");
    }

    @Test
    void sansEntrepotLeStockResteSurLArticleSeul() {
        when(lieux.cible(any())).thenReturn(null);
        lignes.clear();

        stock.appliquer(ciment, TypeMouvementStock.VENTE, new BigDecimal("30"), null, "Vente", 1L);
        stock.reserver(ciment, new BigDecimal("20"));

        assertThat(ciment.getStockActuel()).isEqualByComparingTo("470");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("20");
        assertThat(traces.get(0).getLieu()).isNull();
        assertThat(traces.get(0).getStockLieuApres()).isNull();
    }
}
