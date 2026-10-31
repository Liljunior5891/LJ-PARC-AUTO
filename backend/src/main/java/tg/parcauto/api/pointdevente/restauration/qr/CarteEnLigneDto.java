package tg.parcauto.api.pointdevente.restauration.qr;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ce que voit et envoie le client depuis le code QR de sa table. Rien n'y
 * revele les couts, les stocks chiffres ni les autres tables.
 */
public final class CarteEnLigneDto {

    private CarteEnLigneDto() {
    }

    public record Categorie(Long id, String nom, String couleur, String icone) {
    }

    public record Article(Long id, String nom, Long categorieId, BigDecimal prix, String image, boolean disponible) {
    }

    public record LigneSuivi(String libelle, BigDecimal quantite, BigDecimal total, String note,
                             LigneCommande.Statut statut) {
    }

    /** La commande en cours de la table, telle que le client la suit. */
    public record Suivi(String numero, List<LigneSuivi> lignes, BigDecimal total, BigDecimal resteAPayer,
                        Commande.Appel appel, Instant appelLe) {
    }

    public record Carte(
            String etablissement, String logo, String messageAccueil, String table,
            boolean commandeOuverte, boolean validationServeur,
            List<Categorie> categories, List<Article> articles, Suivi suivi,
            /** Faux tant qu'aucune caisse n'est ouverte : ni commande ni appel depuis la table. */
            boolean caisseOuverte) {
    }

    public record LigneDemande(
            @NotNull Long articleId,
            @NotNull @Positive @DecimalMax("20") BigDecimal quantite,
            @Size(max = 120) String note) {
    }

    public record Demande(@NotEmpty @Size(max = 30) List<@Valid LigneDemande> lignes) {
    }

    public record Appel(@NotNull Commande.Appel motif) {
    }
}
