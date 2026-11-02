package tg.parcauto.api.pointdevente.restauration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.VenteDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public final class RestaurationDto {

    private RestaurationDto() {
    }

    // ------------------------------------------------------------ salles

    public record SalleRequete(
            @NotBlank @Size(max = 80) String nom,
            Integer ordre,
            Boolean actif) {
    }

    public record SalleReponse(Long id, String nom, int ordre, boolean actif) {

        public static SalleReponse de(Salle s) {
            return new SalleReponse(s.getId(), s.getNom(), s.getOrdre(), s.isActif());
        }
    }

    public record TableRequete(
            @NotNull Long salleId,
            @NotBlank @Size(max = 40) String nom,
            @Min(1) @Max(100) Integer places,
            TableRestaurant.Forme forme,
            @Min(0) @Max(TableRestaurant.LARGEUR_PLAN) Integer posX,
            @Min(0) @Max(TableRestaurant.HAUTEUR_PLAN) Integer posY,
            @Min(4) @Max(40) Integer largeur,
            @Min(4) @Max(40) Integer hauteur,
            Boolean actif) {
    }

    public record TableReponse(
            Long id, Long salleId, String nom, int places, TableRestaurant.Forme forme,
            int posX, int posY, int largeur, int hauteur, boolean actif, String jetonQr) {

        public static TableReponse de(TableRestaurant t) {
            return new TableReponse(t.getId(), t.getSalle().getId(), t.getNom(), t.getPlaces(), t.getForme(),
                    t.getPosX(), t.getPosY(), t.getLargeur(), t.getHauteur(), t.isActif(), t.getJetonQr());
        }
    }

    /** Deplacement d'une table sur le plan, enregistre en lot apres l'edition. */
    public record PositionTable(
            @NotNull Long id,
            @NotNull @Min(0) @Max(TableRestaurant.LARGEUR_PLAN) Integer posX,
            @NotNull @Min(0) @Max(TableRestaurant.HAUTEUR_PLAN) Integer posY) {
    }

    /** Une table du plan et la commande qui l'occupe, s'il y en a une. */
    public record TablePlan(TableReponse table, CommandeResume commande) {
    }

    public record SallePlan(SalleReponse salle, List<TablePlan> tables) {
    }

    /** Plan de service : les salles et leurs tables, puis les commandes au comptoir. */
    public record Plan(List<SallePlan> salles, List<CommandeResume> sansTable) {
    }

    // ---------------------------------------------------------- commandes

    public record Ouverture(
            Long tableId,
            @Size(max = 80) String libelle,
            @Min(1) @Max(200) Integer couverts,
            @Size(max = 255) String note,
            /** Session de caisse du poste, a laquelle la commande se rattache. */
            Long sessionId) {
    }

    public record AjoutLigne(
            @NotNull Long articleId,
            @NotNull @Positive BigDecimal quantite,
            @Size(max = 255) String note) {
    }

    /** Modification d'une ligne pas encore envoyee. */
    public record ModificationLigne(
            @NotNull @Positive BigDecimal quantite,
            @Size(max = 255) String note) {
    }

    public record Informations(
            @Min(1) @Max(200) Integer couverts,
            @Size(max = 255) String note) {
    }

    public record Transfert(@NotNull Long tableId) {
    }

    public record Annulation(@Size(max = 255) String motif) {
    }

    public record LignePaiement(@NotNull Long ligneId, @NotNull @Positive BigDecimal quantite) {
    }

    /**
     * Reglement de tout ou partie de l'addition. Sans lignes, tout ce qui reste
     * a payer ; avec des lignes, le partage par convive (une quantite partielle
     * scinde la ligne).
     */
    public record Encaissement(
            List<@Valid LignePaiement> lignes,
            @PositiveOrZero BigDecimal remise,
            @NotNull ModePaiementPdv modePaiement,
            @PositiveOrZero BigDecimal montantRecu,
            @Size(max = 80) String referencePaiement,
            @Size(max = 150) String clientNom,
            /** Session de caisse choisie par le poste quand plusieurs sont ouvertes. */
            Long sessionId) {

        public VenteDto.Reglement reglement() {
            return new VenteDto.Reglement(remise, modePaiement, montantRecu, referencePaiement, clientNom, sessionId);
        }
    }

    public record LigneReponse(
            Long id, Long articleId, String libelle, BigDecimal prixUnitaire, BigDecimal quantite,
            BigDecimal total, String note, PostePreparation poste, LigneCommande.Statut statut,
            Integer envoi, Instant envoyeeLe, Instant preteLe, Instant servieLe,
            boolean payee, String annuleePar, LigneCommande.Origine origine) {

        public static LigneReponse de(LigneCommande l) {
            return new LigneReponse(l.getId(), l.getArticle().getId(), l.getLibelle(), l.getPrixUnitaire(),
                    l.getQuantite(), montant(l.total()), l.getNote(), l.getPoste(), l.getStatut(),
                    l.getEnvoi(), l.getEnvoyeeLe(), l.getPreteLe(), l.getServieLe(),
                    l.getVente() != null, l.getAnnuleePar(), l.getOrigine());
        }
    }

    public record CommandeResume(
            Long id, String numero, Long tableId, String repere, String serveurNom, Integer couverts,
            Commande.Statut statut, Instant ouverteLe, Instant additionLe,
            /** Somme des lignes envoyees, payees ou non. */
            BigDecimal total,
            BigDecimal resteAPayer,
            long aEnvoyer, long enPreparation, long pretes,
            /** Articles commandes par le client, en attente de validation. */
            long aValider,
            Commande.Appel appel, Instant appelLe,
            /** Passee dans la boutique en ligne. */
            boolean enLigne) {

        /**
         * Table tenue par un autre serveur : le serveur la voit occupee, sans
         * le detail ni le montant de la commande.
         */
        public static CommandeResume masquee(Commande c) {
            return new CommandeResume(null, null, c.getTable() == null ? null : c.getTable().getId(),
                    c.repere(), c.getServeurNom(), null, c.getStatut(), c.getOuverteLe(), null,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 0, null, null, c.isEnLigne());
        }

        public static CommandeResume de(Commande c) {
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal reste = BigDecimal.ZERO;
            long aEnvoyer = 0;
            long enPreparation = 0;
            long pretes = 0;
            long aValider = 0;
            for (LigneCommande l : c.getLignes()) {
                if (l.getStatut().facturable()) {
                    total = total.add(l.total());
                }
                if (l.aPayer()) {
                    reste = reste.add(l.total());
                }
                switch (l.getStatut()) {
                    case PROPOSEE -> aValider++;
                    case BROUILLON -> aEnvoyer++;
                    case A_PREPARER, EN_PREPARATION -> enPreparation++;
                    case PRETE -> pretes++;
                    default -> {
                    }
                }
            }
            return new CommandeResume(c.getId(), c.getNumero(), c.getTable() == null ? null : c.getTable().getId(),
                    c.repere(), c.getServeurNom(), c.getCouverts(), c.getStatut(), c.getOuverteLe(),
                    c.getAdditionLe(), montant(total), montant(reste), aEnvoyer, enPreparation, pretes,
                    aValider, c.getAppelMotif(), c.getAppelLe(), c.isEnLigne());
        }
    }

    public record CommandeDetail(CommandeResume resume, String note, Instant fermeeLe, List<LigneReponse> lignes) {

        public static CommandeDetail de(Commande c) {
            return new CommandeDetail(CommandeResume.de(c), c.getNote(), c.getFermeeLe(),
                    c.getLignes().stream().map(LigneReponse::de).toList());
        }
    }

    /** Resultat d'un encaissement : le ticket a imprimer et la commande a jour. */
    public record ResultatEncaissement(VenteDto.Ticket ticket, CommandeDetail commande) {
    }

    // ------------------------------------------------------- preparation

    public record LigneBon(Long id, String libelle, BigDecimal quantite, String note, LigneCommande.Statut statut) {

        public static LigneBon de(LigneCommande l) {
            return new LigneBon(l.getId(), l.getLibelle(), l.getQuantite(), l.getNote(), l.getStatut());
        }
    }

    /** Bon de preparation : les lignes d'un meme envoi pour un poste. */
    public record Bon(
            Long commandeId, String numero, String repere, String serveurNom, Integer couverts,
            int envoi, Instant envoyeLe, LigneCommande.Statut statut, List<LigneBon> lignes) {
    }

    /** Bon a faire avancer : celui d'un ecran de preparation, ou a defaut d'un poste (cuisine, bar). */
    public record AvancementBon(@NotNull Long commandeId, @NotNull Integer envoi, Long ecranId, PostePreparation poste) {
    }

    public record SelectionLignes(@NotEmpty List<Long> lignes) {
    }

    // ------------------------------------------------------------ reglages

    public record Reglages(boolean qrActif, boolean qrValidation, @Size(max = 255) String messageAccueil) {

        public static Reglages de(ReglageRestauration r) {
            return new Reglages(r.isQrActif(), r.isQrValidation(), r.getMessageAccueil());
        }
    }

    private static BigDecimal montant(BigDecimal valeur) {
        return valeur.setScale(2, RoundingMode.HALF_UP);
    }
}
