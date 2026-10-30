package tg.parcauto.api.parametrage;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Echanges de l'ecran de parametrage. */
public final class SocieteDto {

    private SocieteDto() {
    }

    public record Reponse(
            String nom,
            String sigle,
            String formeJuridique,
            String adresse,
            String ville,
            String pays,
            String telephone,
            String email,
            String siteWeb,
            String nif,
            String rccm,
            String banque,
            String compteBancaire,
            String mentionsFacture,
            java.math.BigDecimal tauxTva,
            /** Logo pret a etre affiche : « data:image/png;base64,... ». */
            String logo) {
    }

    /** Ce que la page de connexion a besoin de connaitre, sans authentification. */
    public record Identite(String nom, String sigle, String logo) {
    }

    /**
     * Ce que le site vitrine publie a des visiteurs non authentifies : l'identite
     * et les coordonnees qui figurent deja sur les documents commerciaux. Les
     * references fiscales et bancaires en sont volontairement absentes.
     */
    public record Vitrine(
            String nom,
            String sigle,
            String formeJuridique,
            String adresse,
            String ville,
            String pays,
            String telephone,
            String email,
            String siteWeb,
            String logo) {
    }

    public record Requete(
            @NotBlank @Size(max = 150) String nom,
            @Size(max = 60) String sigle,
            @Size(max = 60) String formeJuridique,
            @Size(max = 255) String adresse,
            @Size(max = 100) String ville,
            @Size(max = 100) String pays,
            @Size(max = 40) String telephone,
            @Email @Size(max = 150) String email,
            @Size(max = 150) String siteWeb,
            @Size(max = 60) String nif,
            @Size(max = 60) String rccm,
            @Size(max = 120) String banque,
            @Size(max = 60) String compteBancaire,
            @Size(max = 500) String mentionsFacture,
            @NotNull @DecimalMin("0") @DecimalMax("100") java.math.BigDecimal tauxTva) {
    }
}
