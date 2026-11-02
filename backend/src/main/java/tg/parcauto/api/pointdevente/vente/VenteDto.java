package tg.parcauto.api.pointdevente.vente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class VenteDto {

    private VenteDto() {
    }

    public record LigneDemande(
            @NotNull Long articleId,
            @NotNull @Positive BigDecimal quantite,
            @PositiveOrZero BigDecimal remise) {
    }

    /** Ticket a encaisser : les prix viennent du catalogue, jamais du poste de caisse. */
    public record Encaissement(
            @NotEmpty List<@Valid LigneDemande> lignes,
            @PositiveOrZero BigDecimal remise,
            @NotNull ModePaiementPdv modePaiement,
            @PositiveOrZero BigDecimal montantRecu,
            @Size(max = 80) String referencePaiement,
            @Size(max = 150) String clientNom,
            /** Session de caisse choisie par le poste quand plusieurs sont ouvertes. */
            Long sessionId,
            /** Commande de la boutique en ligne reglee par ce ticket (caisse boutique). */
            Long commandeEnLigneId) {

        public Reglement reglement() {
            return new Reglement(remise, modePaiement, montantRecu, referencePaiement, clientNom, sessionId);
        }
    }

    /** Reglement d'un ticket : remise globale, moyen de paiement et client. */
    public record Reglement(
            @PositiveOrZero BigDecimal remise,
            @NotNull ModePaiementPdv modePaiement,
            @PositiveOrZero BigDecimal montantRecu,
            @Size(max = 80) String referencePaiement,
            @Size(max = 150) String clientNom,
            Long sessionId) {
    }

    public record Annulation(@Size(max = 255) String motif) {
    }

    public record Ligne(Long articleId, String libelle, BigDecimal prixUnitaire, BigDecimal quantite,
                        BigDecimal remise, BigDecimal total) {

        public static Ligne de(LigneVente l) {
            return new Ligne(l.getArticle().getId(), l.getLibelle(), l.getPrixUnitaire(), l.getQuantite(),
                    l.getRemise(), l.getTotal());
        }
    }

    public record Resume(
            Long id, String numero, Instant dateVente, String caissierNom, String clientNom,
            BigDecimal total, ModePaiementPdv modePaiement, String modePaiementLibelle,
            Vente.Statut statut, String sessionNumero, String tableNom,
            /** Vendue en ligne (Marketplace) plutot qu'au comptoir. */
            boolean enLigne) {

        public static Resume de(Vente v) {
            return new Resume(v.getId(), v.getNumero(), v.getDateVente(), v.getCaissierNom(), v.getClientNom(),
                    v.getTotal(), v.getModePaiement(), v.getModePaiement().getLibelle(), v.getStatut(),
                    v.getSession().getNumero(), v.getTableNom(), v.isEnLigne());
        }
    }

    /** Ticket complet, tel qu'il s'imprime. */
    public record Ticket(
            Long id, String numero, Instant dateVente, String caissierNom, String clientNom,
            String sessionNumero, List<Ligne> lignes,
            BigDecimal totalBrut, BigDecimal remise, BigDecimal total,
            ModePaiementPdv modePaiement, String modePaiementLibelle,
            BigDecimal montantRecu, BigDecimal monnaieRendue, String referencePaiement,
            Vente.Statut statut, Instant annuleeLe, String annuleePar, String motifAnnulation,
            Long commandeId, String tableNom) {

        public static Ticket de(Vente v) {
            return new Ticket(v.getId(), v.getNumero(), v.getDateVente(), v.getCaissierNom(), v.getClientNom(),
                    v.getSession().getNumero(), v.getLignes().stream().map(Ligne::de).toList(),
                    v.getTotalBrut(), v.getRemise(), v.getTotal(),
                    v.getModePaiement(), v.getModePaiement().getLibelle(),
                    v.getMontantRecu(), v.getMonnaieRendue(), v.getReferencePaiement(),
                    v.getStatut(), v.getAnnuleeLe(), v.getAnnuleePar(), v.getMotifAnnulation(),
                    v.getCommandeId(), v.getTableNom());
        }
    }
}
