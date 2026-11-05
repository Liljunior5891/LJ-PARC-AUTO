package tg.parcauto.api.pointdevente.materiaux;

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
import tg.parcauto.api.pointdevente.stock.MouvementStockRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Le stock suit la remise au client : ce qui est vendu et pas remis reste en magasin, reserve. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LivraisonClientServiceTest {

    private static final BigDecimal CENT = new BigDecimal("100");

    @Mock BonLivraisonRepository bons;
    @Mock VenteRepository ventes;
    @Mock NumerotationService numerotation;
    @Mock ReglageMateriauxService reglages;
    @Mock tg.parcauto.api.pointdevente.catalogue.ArticleRepository articles;
    @Mock MouvementStockRepository mouvements;
    @Mock UtilisateurPrincipal auteur;
    @Mock SoldeResteRepository soldes;
    @Mock tg.parcauto.api.pointdevente.lieu.LieuStockService lieux;
    @Mock tg.parcauto.api.pointdevente.lieu.BonRetraitRepository retraits;
    @Mock tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;
    @Mock tg.parcauto.api.pointdevente.caisse.SessionCaisse session;

    private StockService stock;
    private LivraisonClientService service;
    private Article ciment;
    private Vente vente;
    private LigneVente ligne;

    @BeforeEach
    void preparer() {
        stock = new StockService(articles, mouvements, lieux);
        service = new LivraisonClientService(bons, ventes, stock, numerotation, reglages, soldes, sessionsTravail, lieux, retraits);
        when(numerotation.prochainNumero("BL")).thenReturn("BL-1");
        when(auteur.getNomComplet()).thenReturn("Magasinier");
        when(bons.save(any())).thenAnswer(i -> i.getArgument(0));
        when(numerotation.prochainNumero("AV")).thenReturn("AV-1");
        when(soldes.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sessionsTravail.exiger(any(), any())).thenReturn(session);
        when(session.ouverte()).thenReturn(true);
        when(session.getNumero()).thenReturn("S-1");
        when(mouvements.save(any())).thenAnswer(i -> i.getArgument(0));

        ciment = new Article();
        ciment.setNom("Ciment");
        ciment.setStockActuel(new BigDecimal("500"));

        // 100 sacs vendus, 40 emportes : le stock a perdu 40, les 60 autres sont reserves.
        ligne = new LigneVente();
        ligne.setId(7L);
        ligne.setArticle(ciment);
        ligne.setLibelle("Ciment");
        ligne.setQuantite(CENT);
        ligne.setQuantiteBase(CENT);
        ligne.setPrixUnitaire(new BigDecimal("1000"));
        ligne.setRemise(BigDecimal.ZERO);
        ligne.setTotal(new BigDecimal("100000"));
        ligne.fixerEmporte(new BigDecimal("40"));
        stock.appliquer(ciment, TypeMouvementStock.VENTE, new BigDecimal("40"), null, "Commande", 1L);
        stock.reserver(ciment, ligne.resteBaseALivrer());

        vente = new Vente();
        vente.setId(1L);
        vente.setNumero("COM-1");
        vente.setTotalBrut(new BigDecimal("100000"));
        vente.setRemise(BigDecimal.ZERO);
        vente.setTotal(new BigDecimal("100000"));
        vente.setMontantPaye(new BigDecimal("100000"));
        vente.setResteDu(BigDecimal.ZERO);
        vente.getLignes().add(ligne);
        when(ventes.detail(1L)).thenReturn(Optional.of(vente));
    }

    private MateriauxDto.RemiseRequete remise(String quantite) {
        return new MateriauxDto.RemiseRequete(List.of(new MateriauxDto.LigneRemise(7L, new BigDecimal(quantite))), "Kossi", null);
    }

    @Test
    void laVenteLaisseLeResteEnMagasinMaisPlusDisponible() {
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("460");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("60");
        assertThat(ciment.stockDisponible()).isEqualByComparingTo("400");
    }

    @Test
    void uneRemiseSortLeStockEtReduitLaReservation() {
        MateriauxDto.BonReponse bon = service.remettre(1L, remise("25"), auteur);

        assertThat(ciment.getStockActuel()).isEqualByComparingTo("435");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("35");
        assertThat(ciment.stockDisponible()).isEqualByComparingTo("400");
        assertThat(ligne.resteALivrer()).isEqualByComparingTo("35");
        assertThat(bon.lignes().get(0).resteApres()).isEqualByComparingTo("35");
        assertThat(bon.recuPar()).isEqualTo("Kossi");
    }

    @Test
    void onNePeutPasRemettrePlusQueLeReste() {
        assertThatThrownBy(() -> service.remettre(1L, remise("61"), auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Il ne reste que 60");
    }

    @Test
    void uneAutreVenteNePeutPasPrendreLeStockReserve() {
        assertThatThrownBy(() -> stock.appliquer(ciment, TypeMouvementStock.VENTE, new BigDecimal("401"), null, "Autre", 2L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("400 disponible");
        assertThatThrownBy(() -> stock.reserver(ciment, new BigDecimal("401")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void uneRemiseReussitMemeSiUnePerteAEntameLeDisponible() {
        // 450 perdus : il reste 10 en magasin pour 60 reserves. On remet ce qui existe, pas plus.
        stock.appliquer(ciment, TypeMouvementStock.PERTE, new BigDecimal("450"), null, "Casse", null);

        service.remettre(1L, remise("10"), auteur);
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("0");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("50");

        assertThatThrownBy(() -> service.remettre(1L, remise("1"), auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Stock insuffisant");
    }

    @Test
    void laDerniereRemiseDUnConditionnementNeLaissePasDeReliquat() {
        // 1 tonne de 3 unites vendue en tiers : les arrondis ne doivent rien laisser en reserve.
        LigneVente tonne = new LigneVente();
        tonne.setQuantite(new BigDecimal("3"));
        tonne.setQuantiteBase(new BigDecimal("10"));
        tonne.fixerEmporte(BigDecimal.ZERO);
        BigDecimal total = tonne.remettre(BigDecimal.ONE).add(tonne.remettre(BigDecimal.ONE)).add(tonne.remettre(BigDecimal.ONE));

        assertThat(total).isEqualByComparingTo("10");
        assertThat(tonne.resteBaseALivrer()).isEqualByComparingTo("0");
    }

    @Test
    void annulerUnBonRendLaMarchandiseEtLaRemetAReserver() {
        BonLivraison bon = new BonLivraison();
        bon.setNumero("BL-1");
        bon.setVente(vente);
        BigDecimal base = ligne.remettre(new BigDecimal("25"));
        stock.livrer(ciment, base, "Livraison", 1L);
        LigneBonLivraison l = new LigneBonLivraison();
        l.setLigneVente(ligne);
        l.setQuantite(new BigDecimal("25"));
        l.setQuantiteBase(base);
        l.setResteApres(ligne.resteALivrer());
        bon.ajouter(l);
        when(bons.findById(9L)).thenReturn(Optional.of(bon));

        service.annuler(9L, "Erreur", auteur);

        assertThat(ciment.getStockActuel()).isEqualByComparingTo("460");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("60");
        assertThat(ligne.resteALivrer()).isEqualByComparingTo("60");
        assertThat(bon.getStatut()).isEqualTo(BonLivraison.Statut.ANNULE);
        assertThatThrownBy(() -> service.annuler(9L, null, auteur)).isInstanceOf(BusinessException.class);
    }

    private MateriauxDto.SoldeRequete solde(String quantite) {
        return new MateriauxDto.SoldeRequete(List.of(new MateriauxDto.LigneRemise(7L, new BigDecimal(quantite))), "Chantier arrete",
                tg.parcauto.api.pointdevente.vente.ModePaiementPdv.ESPECES, null, 1L);
    }

    @Test
    void venteToutePayeeSoldeeRembourseLaValeurDuReste() {
        MateriauxDto.SoldeReponse s = service.solder(1L, solde("60"), auteur);

        assertThat(s.montant()).isEqualByComparingTo("60000");
        assertThat(s.montantDeduit()).isEqualByComparingTo("0");
        assertThat(s.montantRembourse()).isEqualByComparingTo("60000");
        // La vente ne porte plus que ce que le client a garde ; ce qu'il a paye a l'epoque ne change pas.
        assertThat(vente.getTotal()).isEqualByComparingTo("40000");
        assertThat(vente.getMontantPaye()).isEqualByComparingTo("100000");
        assertThat(ligne.getQuantite()).isEqualByComparingTo("40");
        assertThat(ligne.resteALivrer()).isEqualByComparingTo("0");
        // La marchandise redevient disponible, sans bouger du magasin.
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("460");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("0");
        assertThat(ciment.stockDisponible()).isEqualByComparingTo("460");
    }

    @Test
    void laValeurDiminueDAbordCeQueLeClientDoit() {
        // Vente a credit : 70 000 F encore dus sur 100 000 F. Renoncer a 60 000 F ne rend rien.
        vente.setResteDu(new BigDecimal("70000"));
        MateriauxDto.SoldeReponse s = service.solder(1L, solde("60"), auteur);

        assertThat(s.montantDeduit()).isEqualByComparingTo("60000");
        assertThat(s.montantRembourse()).isEqualByComparingTo("0");
        assertThat(vente.getResteDu()).isEqualByComparingTo("10000");
        assertThat(s.sessionNumero()).isNull();
    }

    @Test
    void seulLeSurplusEstRembourse() {
        vente.setResteDu(new BigDecimal("20000"));
        MateriauxDto.SoldeReponse s = service.solder(1L, solde("60"), auteur);

        assertThat(s.montantDeduit()).isEqualByComparingTo("20000");
        assertThat(s.montantRembourse()).isEqualByComparingTo("40000");
        assertThat(vente.getResteDu()).isEqualByComparingTo("0");
    }

    @Test
    void laRemiseGlobaleReduitCeQuiEstRembourse() {
        // Remise globale de 10 % : 60 sacs a 1 000 F ne valent que 54 000 F.
        vente.setRemise(new BigDecimal("10000"));
        vente.setTotal(new BigDecimal("90000"));
        MateriauxDto.SoldeReponse s = service.solder(1L, solde("60"), auteur);

        assertThat(s.montant()).isEqualByComparingTo("54000");
        assertThat(vente.getTotal()).isEqualByComparingTo("36000");
        assertThat(vente.getRemise()).isEqualByComparingTo("4000");
    }

    @Test
    void onNeSoldePasPlusQueLeReste() {
        assertThatThrownBy(() -> service.solder(1L, solde("61"), auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Il ne reste que 60");
    }

    @Test
    void annulerUnSoldeRemetToutEnPlace() {
        when(soldes.findById(5L)).thenAnswer(i -> {
            SoldeReste s = new SoldeReste();
            s.setVente(vente);
            s.setSession(session);
            s.setNumero("AV-1");
            return Optional.of(s);
        });
        vente.setResteDu(new BigDecimal("20000"));
        org.mockito.ArgumentCaptor<SoldeReste> capture = org.mockito.ArgumentCaptor.forClass(SoldeReste.class);
        service.solder(1L, solde("60"), auteur);
        org.mockito.Mockito.verify(soldes).save(capture.capture());
        when(soldes.findById(5L)).thenReturn(Optional.of(capture.getValue()));

        service.annulerSolde(5L, "Erreur", auteur);

        assertThat(vente.getTotal()).isEqualByComparingTo("100000");
        assertThat(vente.getResteDu()).isEqualByComparingTo("20000");
        assertThat(ligne.getQuantite()).isEqualByComparingTo("100");
        assertThat(ligne.resteALivrer()).isEqualByComparingTo("60");
        assertThat(ligne.getTotal()).isEqualByComparingTo("100000");
        assertThat(ciment.getStockReserve()).isEqualByComparingTo("60");
    }

    @Test
    void unSoldeSurSessionFermeeNeSAnnulePlus() {
        org.mockito.ArgumentCaptor<SoldeReste> capture = org.mockito.ArgumentCaptor.forClass(SoldeReste.class);
        service.solder(1L, solde("60"), auteur);
        org.mockito.Mockito.verify(soldes).save(capture.capture());
        when(soldes.findById(5L)).thenReturn(Optional.of(capture.getValue()));
        when(session.ouverte()).thenReturn(false);

        assertThatThrownBy(() -> service.annulerSolde(5L, null, auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("fermee");
    }

    @Test
    void laVenteSurArrivagePeutDepasserLeStock() {
        stock.reserverSansLimite(ciment, new BigDecimal("500"));

        assertThat(ciment.stockDisponible()).isEqualByComparingTo("-100");
        // Un autre client ne peut pas repartir avec : la marchandise en magasin est deja promise.
        assertThatThrownBy(() -> stock.appliquer(ciment, TypeMouvementStock.VENTE, BigDecimal.ONE, null, "Autre", 2L))
                .isInstanceOf(BusinessException.class);
        // Mais le client servi le premier peut recevoir ce qui existe.
        service.remettre(1L, remise("60"), auteur);
        assertThat(ciment.getStockActuel()).isEqualByComparingTo("400");
    }

    @Test
    void uneCommandeAnnuleeNeSeRemetPlus() {
        vente.setStatut(Vente.Statut.ANNULEE);
        assertThatThrownBy(() -> service.remettre(1L, remise("1"), auteur))
                .isInstanceOf(BusinessException.class).hasMessageContaining("annulee");
    }
}
