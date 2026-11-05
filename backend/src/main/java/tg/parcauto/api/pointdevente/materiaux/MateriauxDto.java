package tg.parcauto.api.pointdevente.materiaux;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.pointdevente.catalogue.Conditionnement;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Echanges du point de vente « Quincaillerie & materiaux ». */
public final class MateriauxDto {

    private MateriauxDto() {
    }

    // ------------------------------------------------------------- reglages

    public record Reglages(
            boolean creditActif,
            boolean creditCaissier,
            boolean depassementGerant,
            boolean bloquerSiRetard,
            @PositiveOrZero BigDecimal plafondDefaut,
            @Min(0) @Max(365) int delaiPaiementJours,
            @Min(0) @Max(100) int acompteMinimum,
            boolean clientObligatoire,
            boolean devisActif,
            @Min(1) @Max(365) int devisValiditeJours,
            @Size(max = 500) String mentionsDevis,
            boolean factureA4,
            boolean tvaActive,
            boolean conditionnementsActifs,
            boolean livraisonDifferee,
            boolean venteSurArrivage) {

        public static Reglages de(ReglageMateriaux r) {
            return new Reglages(r.isCreditActif(), r.isCreditCaissier(), r.isDepassementGerant(),
                    r.isBloquerSiRetard(), r.getPlafondDefaut(), r.getDelaiPaiementJours(), r.getAcompteMinimum(),
                    r.isClientObligatoire(), r.isDevisActif(), r.getDevisValiditeJours(), r.getMentionsDevis(),
                    r.isFactureA4(), r.isTvaActive(), r.isConditionnementsActifs(), r.isLivraisonDifferee(), r.isVenteSurArrivage());
        }
    }

    // -------------------------------------------------------------- clients

    public record ClientRequete(
            @Size(max = 40) String code,
            ClientPdv.Type type,
            /** Raison sociale ; pour une personne, composee a partir de ses prenoms et de son nom. */
            @Size(max = 150) String nom,
            @Size(max = 40) String telephone,
            @Email @Size(max = 150) String email,
            @Size(max = 255) String adresse,
            @Size(max = 60) String nif,
            /** Nul : plafond par defaut des reglages ; zero : aucun credit. */
            @PositiveOrZero BigDecimal plafondCredit,
            @Min(0) @Max(365) Integer delaiPaiementJours,
            @Size(max = 500) String note,
            Boolean actif,
            @Size(max = 70) String nomFamille,
            @Size(max = 70) String prenoms,
            LocalDate dateNaissance,
            @Size(max = 100) String profession,
            @Size(max = 40) String formeJuridique,
            @Size(max = 60) String rccm,
            @Size(max = 100) String secteurActivite,
            @Size(max = 150) String organismeTutelle,
            @Size(max = 60) String numeroAgrement,
            @Size(max = 80) String ville,
            @Size(max = 80) String pays,
            @Size(max = 120) String personneContact,
            @Size(max = 100) String fonctionContact,
            @Size(max = 40) String telephoneContact,
            String conditionsCommerciales) {
    }

    /**
     * Client et sa situation : encours (tout ce qu'il doit), part echue (echeance
     * passee), plafond retenu (le sien ou celui des reglages, nul sans limite) et
     * credit encore disponible.
     */
    public record ClientReponse(
            Long id, String code, ClientPdv.Type type, String typeLibelle, String nom, String telephone,
            String email, String adresse, String nif, BigDecimal plafondCredit, Integer delaiPaiementJours,
            String note, boolean actif,
            BigDecimal encours, BigDecimal encoursEchu, BigDecimal plafondRetenu, BigDecimal creditDisponible,
            String nomFamille, String prenoms, LocalDate dateNaissance, String profession, String formeJuridique,
            String rccm, String secteurActivite, String organismeTutelle, String numeroAgrement, String ville,
            String pays, String personneContact, String fonctionContact, String telephoneContact,
            String conditionsCommerciales) {
    }

    /** Vente encore due par un client. */
    public record VenteDue(Long id, String numero, Instant dateVente, BigDecimal total, BigDecimal montantPaye,
                           BigDecimal resteDu, LocalDate echeance, boolean enRetard) {

        public static VenteDue de(Vente v) {
            return new VenteDue(v.getId(), v.getNumero(), v.getDateVente(), v.getTotal(),
                    v.getTotal().subtract(v.getResteDu()), v.getResteDu(), v.getEcheance(),
                    v.getEcheance() != null && v.getEcheance().isBefore(LocalDate.now()));
        }
    }

    public record ReglementLigne(Long id, String numero, Instant date, String venteNumero, BigDecimal montant,
                                 ModePaiementPdv modePaiement, String modePaiementLibelle, String referencePaiement,
                                 String caissierNom, String sessionNumero, ReglementClient.Statut statut,
                                 String motifAnnulation) {

        public static ReglementLigne de(ReglementClient r) {
            return new ReglementLigne(r.getId(), r.getNumero(), r.getDateReglement(), r.getVente().getNumero(),
                    r.getMontant(), r.getModePaiement(), r.getModePaiement().getLibelle(), r.getReferencePaiement(),
                    r.getCaissierNom(), r.getSession().getNumero(), r.getStatut(), r.getMotifAnnulation());
        }
    }

    /** Compte d'un client : sa fiche, ses ventes dues et l'historique de ses reglements. */
    public record Compte(ClientReponse client, List<VenteDue> dues, List<ReglementLigne> reglements,
                         /** Ventes dont il reste de la marchandise a remettre au client. */
                         List<VenteALivrer> aLivrer) {
    }

    // ------------------------------------------------------------- livraisons

    /** Ce qu'il reste a remettre d'une ligne de vente. */
    public record LigneALivrer(Long ligneId, Long articleId, String libelle, String unite, BigDecimal quantite,
                               BigDecimal quantiteLivree, BigDecimal reste,
                               /** Stock en magasin, dans l'unite de base du produit. */
                               BigDecimal enMagasin,
                               /** Valeur d'une unite pour un solde : apres remises de la ligne et de la vente. */
                               BigDecimal valeurUnitaire) {

        public static LigneALivrer de(tg.parcauto.api.pointdevente.vente.LigneVente l) {
            Vente v = l.getVente();
            BigDecimal valeur = l.getQuantite().signum() == 0 || v.getTotalBrut().signum() == 0 ? BigDecimal.ZERO
                    : l.getTotal().multiply(v.getTotal()).divide(v.getTotalBrut(), 6, java.math.RoundingMode.HALF_UP)
                            .divide(l.getQuantite(), 2, java.math.RoundingMode.HALF_UP);
            return new LigneALivrer(l.getId(), l.getArticle().getId(), l.getLibelle(), uniteDeLigne(l), l.getQuantite(),
                    l.getQuantiteLivree(), l.resteALivrer(), l.getArticle().getStockActuel(), valeur);
        }
    }

    /** Vente dont une partie n'a pas encore ete remise, et ce que le client doit encore payer. */
    public record VenteALivrer(Long venteId, String numero, Instant dateVente, Long clientId, String clientNom,
                               String clientTelephone, BigDecimal resteDu, List<LigneALivrer> lignes,
                               Vente.Statut statut, tg.parcauto.api.pointdevente.vente.ModePaiementPdv modePaiement) {

        public static VenteALivrer de(Vente v) {
            ClientPdv c = v.getClient();
            return new VenteALivrer(v.getId(), v.getNumero(), v.getDateVente(), c == null ? null : c.getId(),
                    v.getClientNom(), c == null ? null : c.getTelephone(), v.getResteDu(),
                    v.getLignes().stream().filter(l -> l.resteALivrer().signum() > 0).map(LigneALivrer::de).toList(),
                    v.getStatut(), v.getModePaiement());
        }
    }

    public record LigneRemise(@NotNull Long ligneId, @NotNull @Positive BigDecimal quantite) {
    }

    /** Remise au client d'une partie de sa commande. */
    public record RemiseRequete(
            @jakarta.validation.constraints.NotEmpty List<@Valid LigneRemise> lignes,
            @Size(max = 150) String recuPar,
            @Size(max = 255) String note) {
    }

    public record LigneBon(String libelle, String unite, BigDecimal quantite, BigDecimal resteApres) {

        public static LigneBon de(LigneBonLivraison l) {
            return new LigneBon(l.getLigneVente().getLibelle(), uniteDeLigne(l.getLigneVente()), l.getQuantite(), l.getResteApres());
        }
    }

    public record BonReponse(Long id, String numero, Long venteId, String venteNumero, Instant dateBon, String remisPar,
                             String recuPar, String note, BonLivraison.Statut statut, String motifAnnulation,
                             List<LigneBon> lignes) {

        public static BonReponse de(BonLivraison b) {
            return new BonReponse(b.getId(), b.getNumero(), b.getVente().getId(), b.getVente().getNumero(), b.getDateBon(),
                    b.getRemisPar(), b.getRecuPar(), b.getNote(), b.getStatut(), b.getMotifAnnulation(),
                    b.getLignes().stream().map(LigneBon::de).toList());
        }
    }

    public record AnnulationBon(@Size(max = 255) String motif) {
    }

    /** Le client renonce a tout ou partie de ce qui reste a lui remettre. */
    public record SoldeRequete(
            @jakarta.validation.constraints.NotEmpty List<@Valid LigneRemise> lignes,
            @jakarta.validation.constraints.NotBlank @Size(max = 255) String motif,
            /** Comment le surplus est rendu, s'il y en a un. */
            tg.parcauto.api.pointdevente.vente.ModePaiementPdv modeRemboursement,
            @Size(max = 80) String referencePaiement,
            Long sessionId) {
    }

    public record LigneSolde(String libelle, String unite, BigDecimal quantite, BigDecimal total) {

        public static LigneSolde de(LigneSoldeReste l) {
            return new LigneSolde(l.getLigneVente().getLibelle(), uniteDeLigne(l.getLigneVente()), l.getQuantite(), l.getTotal());
        }
    }

    public record SoldeReponse(Long id, String numero, Long venteId, String venteNumero, Instant dateSolde, String auteurNom,
                               String motif, BigDecimal montant, BigDecimal montantDeduit, BigDecimal montantRembourse,
                               String modeRemboursementLibelle, String sessionNumero, boolean sessionOuverte,
                               SoldeReste.Statut statut, String motifAnnulation, List<LigneSolde> lignes) {

        public static SoldeReponse de(SoldeReste s) {
            return new SoldeReponse(s.getId(), s.getNumero(), s.getVente().getId(), s.getVente().getNumero(), s.getDateSolde(),
                    s.getAuteurNom(), s.getMotif(), s.getMontant(), s.getMontantDeduit(), s.getMontantRembourse(),
                    s.getModeRemboursement() == null ? null : s.getModeRemboursement().getLibelle(),
                    s.getSession() == null ? null : s.getSession().getNumero(),
                    s.getSession() == null || s.getSession().ouverte(), s.getStatut(), s.getMotifAnnulation(),
                    s.getLignes().stream().map(LigneSolde::de).toList());
        }
    }

    /** Unite imprimee : le conditionnement vendu, sinon l'unite de base du produit. */
    private static String uniteDeLigne(tg.parcauto.api.pointdevente.vente.LigneVente l) {
        return l.getConditionnement() != null ? l.getConditionnement().getNom() : l.getArticle().getUnite();
    }

    /** Versement d'un client ; sans vente designee, il solde ses ventes de la plus ancienne a la plus recente. */
    public record ReglementRequete(
            @NotNull @Positive BigDecimal montant,
            @NotNull ModePaiementPdv modePaiement,
            /** Especes : billet remis par le client, pour la monnaie a rendre. */
            @PositiveOrZero BigDecimal montantRecu,
            @Size(max = 80) String referencePaiement,
            Long venteId,
            Long sessionId) {
    }

    /** Recu d'un versement, tel qu'il s'imprime. */
    public record RecuReglement(
            String numero, Instant date, Long clientId, String clientNom, String clientTelephone,
            BigDecimal montant, ModePaiementPdv modePaiement, String modePaiementLibelle,
            BigDecimal montantRecu, BigDecimal monnaieRendue, String referencePaiement,
            String caissierNom, String sessionNumero, List<Imputation> imputations,
            BigDecimal soldeRestant, ReglementClient.Statut statut) {
    }

    public record Imputation(String venteNumero, BigDecimal montant, BigDecimal resteApres) {
    }

    public record Annulation(@Size(max = 255) String motif) {
    }

    // ---------------------------------------------------------------- devis

    public record LigneDevisRequete(
            @NotNull Long articleId,
            Long conditionnementId,
            @NotNull @Positive BigDecimal quantite,
            @PositiveOrZero BigDecimal remise) {
    }

    public record DevisRequete(
            Long clientId,
            /** Client sans fiche. */
            @Size(max = 150) String clientNom,
            @NotEmpty List<@Valid LigneDevisRequete> lignes,
            @PositiveOrZero BigDecimal remise,
            /** Nulle : date du jour plus la validite des reglages. */
            LocalDate valableJusqu,
            @Size(max = 500) String note) {
    }

    public record LigneDevisReponse(Long id, Long articleId, Long conditionnementId, String libelle, String unite,
                                    BigDecimal prixUnitaire, BigDecimal quantite, BigDecimal remise,
                                    BigDecimal total) {

        public static LigneDevisReponse de(LigneDevis l) {
            Conditionnement c = l.getConditionnement();
            return new LigneDevisReponse(l.getId(), l.getArticle().getId(), c == null ? null : c.getId(),
                    l.getLibelle(), c == null ? l.getArticle().getUnite() : c.getNom(),
                    l.getPrixUnitaire(), l.getQuantite(), l.getRemise(), l.getTotal());
        }
    }

    /** Statut affiche : un devis en cours dont la validite est passee est « expire ». */
    public record DevisReponse(
            Long id, String numero, Long clientId, String clientNom, String clientTelephone,
            LocalDate dateDevis, LocalDate valableJusqu, Devis.Statut statut, String statutLibelle, boolean expire,
            BigDecimal totalBrut, BigDecimal remise, BigDecimal total, String note, String vendeurNom,
            Long venteId, List<LigneDevisReponse> lignes) {

        public static DevisReponse de(Devis d, boolean avecLignes) {
            ClientPdv c = d.getClient();
            String libelle = switch (d.getStatut()) {
                case EN_COURS -> d.expire() ? "Expire" : "En cours";
                case CONVERTI -> "Transforme en vente";
                case ANNULE -> "Annule";
            };
            return new DevisReponse(d.getId(), d.getNumero(), c == null ? null : c.getId(), d.getClientNom(),
                    c == null ? null : c.getTelephone(), d.getDateDevis(), d.getValableJusqu(), d.getStatut(),
                    libelle, d.expire(), d.getTotalBrut(), d.getRemise(), d.getTotal(), d.getNote(),
                    d.getVendeurNom(), d.getVenteId(),
                    avecLignes ? d.getLignes().stream().map(LigneDevisReponse::de).toList() : List.of());
        }
    }

    /** Encaissement d'un devis accepte : reglement, et vente a credit eventuelle. */
    public record EncaissementDevis(
            @NotNull ModePaiementPdv modePaiement,
            @PositiveOrZero BigDecimal montantRecu,
            @Size(max = 80) String referencePaiement,
            Long sessionId,
            Boolean credit,
            @PositiveOrZero BigDecimal acompte,
            /** Ce que le client emporte maintenant ; nul : tout. */
            List<@Valid Emporte> emportes) {
    }

    /** Quantite emportee d'un produit du devis ; le reste sera livre plus tard. */
    public record Emporte(@NotNull Long articleId, Long conditionnementId, @NotNull @PositiveOrZero BigDecimal quantite) {
    }

    // ----------------------------------------------------- conditionnements

    public record ConditionnementRequete(
            Long id,
            @NotBlank @Size(max = 40) String nom,
            @NotNull @Positive BigDecimal coefficient,
            @NotNull @PositiveOrZero BigDecimal prixVente,
            @Size(max = 60) String codeBarres,
            Boolean actif) {
    }

    public record ConditionnementReponse(Long id, Long articleId, String nom, BigDecimal coefficient,
                                         BigDecimal prixVente, String codeBarres, boolean actif) {

        public static ConditionnementReponse de(Conditionnement c) {
            return new ConditionnementReponse(c.getId(), c.getArticle().getId(), c.getNom(), c.getCoefficient(),
                    c.getPrixVente(), c.getCodeBarres(), c.isActif());
        }
    }
}
