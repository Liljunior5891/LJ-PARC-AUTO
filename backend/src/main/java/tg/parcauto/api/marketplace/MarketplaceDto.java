package tg.parcauto.api.marketplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Echanges du module Marketplace : espace de gestion, boutique publique et place commune. */
public final class MarketplaceDto {

    private MarketplaceDto() {
    }

    // ------------------------------------------------------------------ Boutique

    public record BoutiqueRequete(
            @NotBlank @Size(min = 3, max = 60)
            @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                    message = "minuscules, chiffres et tirets seulement (ex. : chez-ama)")
            String slug,
            @NotBlank @Size(max = 150) String nom,
            @Size(max = 200) String slogan,
            String description,
            @Size(max = 60) String categorie,
            String couverture,
            @Size(max = 40) String telephone,
            @Size(max = 40) String whatsapp,
            @Email @Size(max = 150) String email,
            @Size(max = 255) String adresse,
            @Size(max = 80) String ville,
            @Size(max = 255) String horaires,
            boolean publiee,
            boolean retraitActif,
            boolean livraisonActive,
            @PositiveOrZero BigDecimal fraisLivraison,
            @Size(max = 255) String zoneLivraison,
            boolean paiementRemise,
            boolean paiementMobileMoney,
            @Size(max = 500) String messageConfirmation) {
    }

    public record BoutiqueReponse(
            Long id, String slug, String nom, String slogan, String description, String categorie,
            String couverture, String logo, String telephone, String whatsapp, String email, String adresse,
            String ville, String horaires, boolean publiee, boolean retraitActif, boolean livraisonActive,
            BigDecimal fraisLivraison, String zoneLivraison, boolean paiementRemise, boolean paiementMobileMoney,
            String messageConfirmation,
            /** Faux tant qu'aucun fournisseur de paiement en ligne n'est branche sur la plateforme. */
            boolean mobileMoneyDisponible) {
    }

    // -------------------------------------------------------------------- Offres

    public record OffreRequete(
            @NotNull Offre.Type type,
            Long articleId,
            Long camionId,
            @NotBlank @Size(max = 150) String titre,
            String description,
            @Size(max = 80) String categorie,
            @PositiveOrZero BigDecimal prix,
            @Size(max = 20) String unitePrix,
            @PositiveOrZero BigDecimal caution,
            String image,
            @PositiveOrZero Integer stock,
            boolean publiee,
            boolean miseEnAvant,
            int ordre) {
    }

    /** Offre vue dans l'espace de gestion, avec ce qu'elle reprend de sa source. */
    public record OffreReponse(
            Long id, Offre.Type type, Long articleId, Long camionId, String titre, String description,
            String categorie,
            /** Prix propre a la boutique, vide pour suivre celui de l'article. */
            BigDecimal prix,
            /** Prix affiche au client (propre ou repris de l'article). */
            BigDecimal prixAffiche,
            String unitePrix, BigDecimal caution, String image, String imageAffichee, Integer stock,
            /** Quantite encore vendable ; vide = sans limite. */
            BigDecimal disponible,
            boolean publiee, boolean miseEnAvant, int ordre,
            /** Detail de la source : immatriculation, code article... */
            String source) {
    }

    /** Article du point de vente et sa publication en ligne, pour le catalogue de la caisse. */
    public record ArticleEnLigne(Long articleId, Long offreId, boolean publiee) {
    }

    /** Nombre maximal de photos supplementaires par offre. */
    public static final int PHOTOS_MAX = 8;

    /**
     * Publication d'un article depuis le catalogue. {@code photos} : galerie de
     * l'offre en ligne, remplacee en entier ; nulle = galerie inchangee.
     */
    public record Publication(boolean publiee,
                              @Size(max = PHOTOS_MAX) List<@Size(max = 2_000_000) @Pattern(
                                      regexp = "^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$",
                                      message = "photo au format JPEG, PNG ou WebP") String> photos) {
    }

    /** Galerie d'une offre : photo principale d'abord, puis les photos supplementaires. */
    public record Galerie(List<String> photos) {
    }

    /** Vehicule du parc et sa location en ligne, pour la fiche vehicule et la vue du parc. */
    public record VehiculeEnLigne(Long camionId, Long offreId, boolean publiee, BigDecimal tarifJour,
                                  BigDecimal caution) {
    }

    public record PublicationVehicule(boolean publiee, @PositiveOrZero BigDecimal tarifJour,
                                      @PositiveOrZero BigDecimal caution,
                                      /** Galerie remplacee en entier ; nulle = inchangee. */
                                      @Size(max = PHOTOS_MAX) List<@Size(max = 2_000_000) @Pattern(
                                              regexp = "^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$",
                                              message = "photo au format JPEG, PNG ou WebP") String> photos) {
    }

    /** Publie d'un coup plusieurs articles du point de vente. */
    public record PublicationArticles(@NotEmpty List<Long> articleIds) {
    }

    public record SourceArticle(Long id, String code, String nom, String categorie, BigDecimal prix, String image,
                                boolean suiviStock, BigDecimal stock, boolean publiee) {
    }

    public record SourceVehicule(Long id, String immatriculation, String libelle, String type, String photo,
                                 String statut, boolean publiee) {
    }

    public record Sources(boolean pointDeVente, boolean gestionParc, List<SourceArticle> articles,
                          List<SourceVehicule> vehicules) {
    }

    // ----------------------------------------------------------------- Commandes

    public record LigneDemande(@NotNull Long offreId, @Min(1) @Max(999) int quantite) {
    }

    public record DemandeCommande(
            @NotBlank @Size(max = 150) String nom,
            @NotBlank @Size(max = 40) String telephone,
            @Email @Size(max = 150) String email,
            @NotNull CommandeEnLigne.ModeRemise modeRemise,
            @Size(max = 255) String adresseLivraison,
            @NotNull CommandeEnLigne.ModePaiement modePaiement,
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 50) List<@Valid LigneDemande> lignes) {
    }

    public record LigneReponse(Long offreId, Long articleId, String libelle, BigDecimal prixUnitaire, int quantite,
                               BigDecimal total) {
    }

    public record CommandeReponse(
            Long id, String numero, String jeton, Long clientId, String clientNom, String clientTelephone,
            String clientEmail, CommandeEnLigne.ModeRemise modeRemise, String modeRemiseLibelle,
            String adresseLivraison, CommandeEnLigne.ModePaiement modePaiement, String modePaiementLibelle,
            CommandeEnLigne.StatutPaiement statutPaiement, CommandeEnLigne.Statut statut, String statutLibelle,
            BigDecimal sousTotal, BigDecimal fraisLivraison, BigDecimal total, String note, String motifAnnulation,
            Instant dateCommande, List<LigneReponse> lignes,
            /** Traitee par la caisse du point de vente (acceptation, preparation, encaissement). */
            boolean enCaisse,
            /** Restaurant : commande de salle qui la porte. */
            Long pdvCommandeId) {

        public static CommandeReponse de(CommandeEnLigne c) {
            return new CommandeReponse(c.getId(), c.getNumero(), c.getJeton(), c.getClientId(), c.getClientNom(),
                    c.getClientTelephone(), c.getClientEmail(), c.getModeRemise(), c.getModeRemise().getLibelle(),
                    c.getAdresseLivraison(), c.getModePaiement(), c.getModePaiement().getLibelle(),
                    c.getStatutPaiement(), c.getStatut(), c.getStatut().getLibelle(), c.getSousTotal(),
                    c.getFraisLivraison(), c.getTotal(), c.getNote(), c.getMotifAnnulation(), c.getDateCommande(),
                    c.getLignes().stream().map(l -> new LigneReponse(l.getOffreId(), l.getArticleId(), l.getLibelle(),
                            l.getPrixUnitaire(), l.getQuantite(), l.getTotal())).toList(),
                    c.isEnCaisse(), c.getPdvCommandeId());
        }
    }

    public record ChangementStatut(@NotNull CommandeEnLigne.Statut statut, @Size(max = 255) String motif) {
    }

    // --------------------------------------------------------- Demandes de location

    public record DemandeLocationPublique(
            @NotNull Long offreId,
            @NotBlank @Size(max = 150) String nom,
            @NotBlank @Size(max = 40) String telephone,
            @Email @Size(max = 150) String email,
            @NotNull LocalDate dateDebut,
            @NotNull LocalDate dateFin,
            @Size(max = 180) String lieuDepart,
            @Size(max = 180) String destination,
            @Size(max = 500) String message) {
    }

    public record DemandeLocationReponse(
            Long id, String numero, String jeton, Long offreId, Long camionId, String vehicule,
            Long clientId, String clientNom, String clientTelephone, String clientEmail, LocalDate dateDebut,
            LocalDate dateFin, long jours, BigDecimal tarifJour, BigDecimal montantEstime, String lieuDepart,
            String destination, String message, DemandeLocation.Statut statut, String statutLibelle,
            String motifRefus, Long locationId, String locationNumero, Instant dateDemande,
            /** Recette enregistree en agence qui conclut la demande. */
            Long recetteId, String recetteNumero,
            /** Caution demandee pour le vehicule (offre en ligne). */
            BigDecimal caution) {
    }

    /** Acceptation : le tarif peut etre ajuste et un chauffeur designe avant de creer le contrat. */
    public record Acceptation(@PositiveOrZero BigDecimal tarifJour, Long chauffeurId, @Size(max = 500) String observations) {
    }

    public record Refus(@Size(max = 255) String motif) {
    }

    /** Compteurs de l'espace de gestion : ce qui attend une reponse. */
    public record Resume(long commandesNouvelles, long commandesEnCours, long demandesNouvelles, long offresPubliees,
                         boolean boutiquePubliee, String slug) {
    }

    // -------------------------------------------------------- Vues publiques

    public record OffrePublique(Long id, Offre.Type type, String titre, String description, String categorie,
                                BigDecimal prix, String unitePrix, BigDecimal caution, String image,
                                boolean disponible,
                                /** Quantite maximale commandable ; vide = sans limite. */
                                Integer quantiteMax,
                                boolean miseEnAvant,
                                /** Photos supplementaires (galerie ouverte au clic sur la photo). */
                                int photos) {
    }

    public record Vitrine(String slug, String nom, String slogan, String description, String categorie,
                          String couverture, String logo, String telephone, String whatsapp, String adresse,
                          String ville, String horaires, boolean retraitActif, boolean livraisonActive,
                          BigDecimal fraisLivraison, String zoneLivraison, boolean paiementRemise,
                          boolean paiementMobileMoney, List<OffrePublique> offres,
                          /** Faux quand la boutique ne prend pas de commande (caisse du point de vente fermee). */
                          boolean ouverte, String messageFermeture) {
    }

    /** Suivi d'une commande ou d'une demande de location, ouvert par le lien a jeton. */
    public record Suivi(
            String nature,
            String boutique, String slug, String telephone, String whatsapp, String messageConfirmation,
            CommandeReponse commande,
            DemandeLocationReponse location,
            /** Adresse ou poursuivre le paiement en ligne, s'il est attendu. */
            String urlPaiement,
            boolean paiementSimule) {
    }

    public record BoutiqueMarche(String slug, String nom, String slogan, String categorie, String ville,
                                 String logo, String couverture, long offres) {
    }

    public record OffreMarche(Long id, String slug, String boutique, String ville, Offre.Type type, String titre,
                              String categorie, BigDecimal prix, String unitePrix, String image) {
    }

    public record Marche(List<BoutiqueMarche> boutiques, List<OffreMarche> offres, List<String> categories,
                         List<String> villes) {
    }
}
