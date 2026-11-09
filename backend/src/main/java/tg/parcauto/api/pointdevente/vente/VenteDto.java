package tg.parcauto.api.pointdevente.vente;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class VenteDto {

    private VenteDto() {
    }

    public record LigneDemande(
            @NotNull Long articleId,
            @NotNull @Positive BigDecimal quantite,
            @PositiveOrZero BigDecimal remise,
            /** Conditionnement vendu (tonne, botte, carton) ; nul pour l'unite de base. */
            Long conditionnementId,
            /** Quincaillerie : ce que le client emporte maintenant ; nul : toute la ligne. */
            @PositiveOrZero BigDecimal quantiteLivree,
            /** Avec des entrepots : lieu ou retirer la ligne ; nul : le magasin de la caisse. */
            Long lieuId,
            /** Note pour la preparation (« sans oignon »), imprimee sur le bon de cuisine. */
            @jakarta.validation.constraints.Size(max = 160) String note) {

        public LigneDemande(Long articleId, BigDecimal quantite, BigDecimal remise, Long conditionnementId) {
            this(articleId, quantite, remise, conditionnementId, null, null, null);
        }

        public LigneDemande(Long articleId, BigDecimal quantite, BigDecimal remise, Long conditionnementId,
                            BigDecimal quantiteLivree) {
            this(articleId, quantite, remise, conditionnementId, quantiteLivree, null, null);
        }
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
            Long commandeEnLigneId,
            /** Compte client (quincaillerie et materiaux). */
            Long clientId,
            /** Vente a credit : seul l'acompte est paye maintenant. */
            Boolean credit,
            @PositiveOrZero BigDecimal acompte) {

        public Reglement reglement() {
            return new Reglement(remise, modePaiement, montantRecu, referencePaiement, clientNom, sessionId);
        }

        public Compte compte() {
            return new Compte(clientId, Boolean.TRUE.equals(credit), acompte);
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

    /** Compte client d'une vente de quincaillerie, et vente a credit avec son acompte. */
    public record Compte(Long clientId, boolean credit, BigDecimal acompte) {
    }

    public record Annulation(@Size(max = 255) String motif) {
    }

    public record Ligne(Long articleId, String libelle, BigDecimal prixUnitaire, BigDecimal quantite,
                        BigDecimal remise, BigDecimal total, Long conditionnementId,
                        /** Remis au client jusqu'ici ; le reste est a livrer. */
                        BigDecimal quantiteLivree) {

        public static Ligne de(LigneVente l) {
            return new Ligne(l.getArticle().getId(), l.getLibelle(), l.getPrixUnitaire(), l.getQuantite(),
                    l.getRemise(), l.getTotal(), l.getConditionnement() == null ? null : l.getConditionnement().getId(),
                    l.getQuantiteLivree() == null ? l.getQuantite() : l.getQuantiteLivree());
        }
    }

    public record Resume(
            Long id, String numero, Instant dateVente, String caissierNom, String clientNom,
            BigDecimal total, ModePaiementPdv modePaiement, String modePaiementLibelle,
            Vente.Statut statut, String sessionNumero, String tableNom,
            /** Vendue en ligne (Marketplace) plutot qu'au comptoir. */
            boolean enLigne,
            /** Reste du par le client sur une vente a credit. */
            BigDecimal resteDu, LocalDate echeance) {

        public static Resume de(Vente v) {
            return new Resume(v.getId(), v.getNumero(), v.getDateVente(), v.getCaissierNom(), v.getClientNom(),
                    v.getTotal(), v.getModePaiement(), v.getModePaiement().getLibelle(), v.getStatut(),
                    v.getSession().getNumero(), v.getTableNom(), v.isEnLigne(), v.getResteDu(), v.getEcheance());
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
            Long commandeId, String tableNom,
            /** Quincaillerie : compte client, paye a la vente, reste du et echeance. */
            Long clientId, BigDecimal montantPaye, BigDecimal resteDu, LocalDate echeance, Long devisId) {

        public static Ticket de(Vente v) {
            return new Ticket(v.getId(), v.getNumero(), v.getDateVente(), v.getCaissierNom(), v.getClientNom(),
                    v.getSession().getNumero(),
                    // Une ligne entierement soldee n'est plus vendue : elle ne s'imprime pas.
                    v.getLignes().stream().filter(l -> l.getQuantite().signum() > 0).map(Ligne::de).toList(),
                    v.getTotalBrut(), v.getRemise(), v.getTotal(),
                    v.getModePaiement(), v.getModePaiement().getLibelle(),
                    v.getMontantRecu(), v.getMonnaieRendue(), v.getReferencePaiement(),
                    v.getStatut(), v.getAnnuleeLe(), v.getAnnuleePar(), v.getMotifAnnulation(),
                    v.getCommandeId(), v.getTableNom(),
                    v.getClient() == null ? null : v.getClient().getId(), v.getMontantPaye(), v.getResteDu(),
                    v.getEcheance(), v.getDevisId());
        }
    }
}
