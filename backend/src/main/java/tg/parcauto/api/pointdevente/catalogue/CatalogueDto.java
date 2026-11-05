package tg.parcauto.api.pointdevente.catalogue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class CatalogueDto {

    /** Une photo reduite a 480 pixels pese moins de 100 Ko ; la limite laisse de la marge. */
    public static final int PHOTO_MAX = 400_000;

    private CatalogueDto() {
    }

    public record CategorieRequete(
            @NotBlank @Size(max = 80) String nom,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "couleur au format #rrggbb") String couleur,
            Integer ordre,
            Boolean actif,
            PostePreparation poste,
            @Size(max = 16) String icone) {
    }

    public record CategorieReponse(Long id, String nom, String couleur, int ordre, boolean actif,
                                   PostePreparation poste, String posteLibelle, String icone) {

        public static CategorieReponse de(Categorie c) {
            return new CategorieReponse(c.getId(), c.getNom(), c.getCouleur(), c.getOrdre(), c.isActif(),
                    c.getPoste(), c.getPoste().getLibelle(), c.getIcone());
        }
    }

    public record ArticleRequete(
            @Size(max = 40) String code,
            @Size(max = 60) String codeBarres,
            @NotBlank @Size(max = 150) String nom,
            Long categorieId,
            @NotNull @PositiveOrZero BigDecimal prixVente,
            @PositiveOrZero BigDecimal prixAchat,
            @Size(max = 20) String unite,
            Boolean suiviStock,
            /** Stock d'ouverture, a la creation seulement : il genere une entree de stock. */
            @PositiveOrZero BigDecimal stockInitial,
            @PositiveOrZero BigDecimal seuilAlerte,
            Boolean actif,
            /** Photo en data URL ; nulle ou vide, l'article n'a pas de photo. */
            @Size(max = PHOTO_MAX) @Pattern(regexp = "^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$",
                    message = "photo au format JPEG, PNG ou WebP") String image) {
    }

    public record ArticleReponse(
            Long id,
            String code,
            String codeBarres,
            String nom,
            Long categorieId,
            String categorieNom,
            String categorieCouleur,
            BigDecimal prixVente,
            BigDecimal prixAchat,
            String unite,
            boolean suiviStock,
            BigDecimal stockActuel,
            BigDecimal seuilAlerte,
            boolean stockBas,
            boolean actif,
            String image,
            String categorieIcone,
            /** Vendu mais pas encore remis au client ; le disponible en est deduit. */
            BigDecimal stockReserve,
            BigDecimal stockDisponible) {

        public static ArticleReponse de(Article a) {
            Categorie c = a.getCategorie();
            return new ArticleReponse(
                    a.getId(), a.getCode(), a.getCodeBarres(), a.getNom(),
                    c == null ? null : c.getId(), c == null ? null : c.getNom(), c == null ? null : c.getCouleur(),
                    a.getPrixVente(), a.getPrixAchat(), a.getUnite(), a.isSuiviStock(),
                    a.getStockActuel(), a.getSeuilAlerte(), a.stockBas(), a.isActif(),
                    a.getImage(), c == null ? null : c.getIcone(), a.getStockReserve(), a.stockDisponible());
        }
    }
}
