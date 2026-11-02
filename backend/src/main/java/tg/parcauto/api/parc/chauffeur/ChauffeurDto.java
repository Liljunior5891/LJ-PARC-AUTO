package tg.parcauto.api.parc.chauffeur;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ChauffeurDto {

    private ChauffeurDto() {
    }

    public record Requete(
            /** Facultatif : genere (CH-2026-0001) s'il est laisse vide. */
            @Size(max = 40) String matricule,
            @NotBlank @Size(max = 80) String nom,
            @NotBlank @Size(max = 80) String prenom,
            @Size(max = 500) String photoUrl,
            @NotBlank @Size(max = 30) String telephone,
            @Size(max = 30) String telephoneUrgence,
            @Email @Size(max = 150) String email,
            @Size(max = 255) String adresse,
            LocalDate dateNaissance,
            @Size(max = 60) String numeroPermis,
            @Size(max = 100) String categoriesPermis,
            LocalDate permisObtention,
            LocalDate permisExpiration,
            LocalDate dateEmbauche,
            @PositiveOrZero BigDecimal salaireMensuel,
            @Size(max = 150) String garantNom,
            @Size(max = 80) String garantLien,
            @Size(max = 30) String garantTelephone,
            @Size(max = 30) String garantTelephoneSecondaire,
            @Size(max = 255) String garantAdresse,
            @Size(max = 120) String garantProfession,
            @Size(max = 80) String garantPieceIdentite,
            StatutChauffeur statut,
            String observations,
            Boolean actif,
            TypeChauffeur typeChauffeur,
            @Size(max = 10) String typeContrat,
            LocalDate dateFinContrat,
            @Size(max = 40) String numeroCnss,
            @PositiveOrZero BigDecimal tarif,
            @Size(max = 10) String uniteTarif,
            @Size(max = 60) String identifiantFiscal,
            @Size(max = 5) String groupeSanguin) {
    }

    public record Reponse(
            Long id,
            String matricule,
            String nom,
            String prenom,
            String nomComplet,
            String photoUrl,
            String telephone,
            String telephoneUrgence,
            String email,
            String adresse,
            LocalDate dateNaissance,
            String numeroPermis,
            String categoriesPermis,
            LocalDate permisObtention,
            LocalDate permisExpiration,
            boolean permisBientotExpire,
            LocalDate dateEmbauche,
            BigDecimal salaireMensuel,
            String garantNom,
            String garantLien,
            String garantTelephone,
            String garantTelephoneSecondaire,
            String garantAdresse,
            String garantProfession,
            String garantPieceIdentite,
            boolean garantRenseigne,
            StatutChauffeur statut,
            String statutLibelle,
            String observations,
            boolean actif,
            TypeChauffeur typeChauffeur,
            String typeChauffeurLibelle,
            String typeContrat,
            LocalDate dateFinContrat,
            String numeroCnss,
            BigDecimal tarif,
            String uniteTarif,
            String identifiantFiscal,
            String groupeSanguin) {

        public static Reponse de(Chauffeur c) {
            return new Reponse(
                    c.getId(), c.getMatricule(), c.getNom(), c.getPrenom(), c.nomComplet(), c.getPhotoUrl(),
                    c.getTelephone(), c.getTelephoneUrgence(), c.getEmail(), c.getAdresse(), c.getDateNaissance(),
                    c.getNumeroPermis(), c.getCategoriesPermis(), c.getPermisObtention(), c.getPermisExpiration(),
                    c.permisBientotExpire(30), c.getDateEmbauche(), c.getSalaireMensuel(),
                    c.getGarantNom(), c.getGarantLien(), c.getGarantTelephone(),
                    c.getGarantTelephoneSecondaire(), c.getGarantAdresse(), c.getGarantProfession(),
                    c.getGarantPieceIdentite(), c.garantRenseigne(), c.getStatut(),
                    c.getStatut().getLibelle(), c.getObservations(), c.isActif(),
                    c.getTypeChauffeur(), c.getTypeChauffeur().getLibelle(), c.getTypeContrat(),
                    c.getDateFinContrat(), c.getNumeroCnss(), c.getTarif(), c.getUniteTarif(),
                    c.getIdentifiantFiscal(), c.getGroupeSanguin());
        }
    }
}
