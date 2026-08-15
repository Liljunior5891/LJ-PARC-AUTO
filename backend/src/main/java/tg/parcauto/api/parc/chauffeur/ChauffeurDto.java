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
            @NotBlank @Size(max = 40) String matricule,
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
            StatutChauffeur statut,
            String observations,
            Boolean actif) {
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
            StatutChauffeur statut,
            String statutLibelle,
            String observations,
            boolean actif) {

        public static Reponse de(Chauffeur c) {
            return new Reponse(
                    c.getId(), c.getMatricule(), c.getNom(), c.getPrenom(), c.nomComplet(), c.getPhotoUrl(),
                    c.getTelephone(), c.getTelephoneUrgence(), c.getEmail(), c.getAdresse(), c.getDateNaissance(),
                    c.getNumeroPermis(), c.getCategoriesPermis(), c.getPermisObtention(), c.getPermisExpiration(),
                    c.permisBientotExpire(30), c.getDateEmbauche(), c.getSalaireMensuel(), c.getStatut(),
                    c.getStatut().getLibelle(), c.getObservations(), c.isActif());
        }
    }
}
