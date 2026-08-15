package tg.parcauto.api.parc.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DocumentDto {

    private DocumentDto() {
    }

    public record Requete(
            @NotNull ProprietaireDocument proprietaireType,
            @NotNull Long proprietaireId,
            @NotNull TypeDocument type,
            @NotBlank @Size(max = 150) String libelle,
            @Size(max = 80) String numero,
            @Size(max = 150) String emetteur,
            LocalDate dateEmission,
            LocalDate dateExpiration,
            @PositiveOrZero BigDecimal cout,
            String observations) {
    }

    public record Reponse(
            Long id,
            ProprietaireDocument proprietaireType,
            Long proprietaireId,
            String proprietaireLibelle,
            TypeDocument type,
            String typeLibelle,
            String libelle,
            String numero,
            String emetteur,
            LocalDate dateEmission,
            LocalDate dateExpiration,
            Long joursAvantExpiration,
            boolean expire,
            BigDecimal cout,
            String fichierNom,
            String fichierType,
            Long fichierTaille,
            boolean fichierDisponible,
            String observations) {

        public static Reponse de(Document d) {
            return de(d, null);
        }

        public static Reponse de(Document d, String proprietaireLibelle) {
            return new Reponse(
                    d.getId(), d.getProprietaireType(), d.getProprietaireId(), proprietaireLibelle,
                    d.getType(), d.getType().getLibelle(), d.getLibelle(), d.getNumero(), d.getEmetteur(),
                    d.getDateEmission(), d.getDateExpiration(), d.joursAvantExpiration(), d.expire(),
                    d.getCout(), d.getFichierNom(), d.getFichierType(), d.getFichierTaille(),
                    d.getFichierChemin() != null, d.getObservations());
        }
    }
}
